package app.rainline;

import android.content.Context;
import org.json.JSONException;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/** Only this class talks to MET; every caller shares its expiry and conditional cache. */
public final class MetClient {
    private final ForecastCache cache;
    private final android.content.SharedPreferences backoff;
    private final MetHttp.ConnectionFactory connections;
    public MetClient(Context context) {
        this(context, url -> (HttpURLConnection) url.openConnection());
    }
    MetClient(Context context, MetHttp.ConnectionFactory connections) {
        cache = new ForecastCache(context);
        backoff = context.getSharedPreferences("http-backoff", Context.MODE_PRIVATE);
        this.connections = connections;
    }

    boolean retryDeferred(String key, long now) {
        return now < retryNotBefore(key);
    }
    long retryNotBefore(String key) {
        return Math.max(backoff.getLong("retry." + key, 0), backoff.getLong("retry.global", 0));
    }

    public ForecastCache.Entry fetch(double lat, double lon) throws IOException {
        String key = ForecastWindow.coordinateKey(lat, lon);
        long now = System.currentTimeMillis();
        ForecastCache.Entry old = cache.read(key);
        if (old != null && now < old.expiresAt) return old; // Also applies to Refresh now.
        if (retryDeferred(key, now))
            throw UpdateIssue.RETRY_DELAY.failure("Waiting before retrying the weather service.");
        String[] coordinates = key.split(",");
        URL url = new URL("https://api.met.no/weatherapi/nowcast/2.0/complete?lat="
                + coordinates[0] + "&lon=" + coordinates[1]);
        HttpURLConnection connection = null;
        try {
            connection = MetHttp.open(url, old == null ? "" : old.lastModified, old == null ? "" : old.etag, connections);
            int code = connection.getResponseCode();
            if (code == 304 && old != null) {
                old.checkedAt = now;
                old.expiresAt = expiry(connection, now);
                cache.write(key, old);
                backoff.edit().remove("retry." + key).apply();
                return old;
            }
            if (code != 200 && code != 203) {
                long retryAt = retryAt(connection.getHeaderField("Retry-After"), now);
                if (code == 403) retryAt = Math.max(retryAt, now + 60 * Forecast.MINUTE);
                android.content.SharedPreferences.Editor retry = backoff.edit().putLong("retry." + key, retryAt);
                if (code == 403 || code == 429) retry.putLong("retry.global", retryAt);
                retry.apply();
                if (code == 404 || code == 422) throw UpdateIssue.OUTSIDE_COVERAGE.failure("This location is outside the forecast area.");
                if (code == 429) throw UpdateIssue.HTTP_THROTTLED.failure("The weather service is busy. Rainline will retry later.");
                if (code == 403) throw UpdateIssue.HTTP_FORBIDDEN.failure("The weather service refused this app's request (403).");
                throw UpdateIssue.HTTP_ERROR.failure("Weather service unavailable (" + code + ").");
            }
            ForecastCache.Entry entry = new ForecastCache.Entry();
            entry.body = MetHttp.body(connection);
            entry.deprecated = code == 203;
            try {
                Forecast received = entry.forecast();
                if (!ForecastWindow.hasUpcomingData(received, now) && old != null
                        && ForecastWindow.hasUpcomingData(old.displayForecast(now), now))
                    entry.retainedBody = old.displayBody(now);
            }
            catch (JSONException e) { throw UpdateIssue.INVALID_RESPONSE.failure("The weather service returned an unreadable forecast."); }
            entry.expiresAt = expiry(connection, now);
            entry.checkedAt = now;
            entry.lastModified = header(connection, "Last-Modified");
            entry.etag = header(connection, "ETag");
            if (Thread.currentThread().isInterrupted()) throw new IOException("Refresh cancelled");
            cache.write(key, entry);
            backoff.edit().remove("retry." + key).apply();
            return entry;
        } catch (IOException e) {
            if (Thread.currentThread().isInterrupted()) throw e;
            if (backoff.getLong("retry." + key, 0) <= now)
                backoff.edit().putLong("retry." + key, now + Forecast.MINUTE).apply();
            if (e instanceof UpdateIssue.Failure) throw e;
            throw UpdateIssue.from(e, UpdateIssue.IO_ERROR).failure("Could not download or save the weather forecast.");
        } finally { if (connection != null) connection.disconnect(); }
    }
    private static String header(HttpURLConnection connection, String key) {
        String value = connection.getHeaderField(key);
        return value == null ? "" : value;
    }
    private static long expiry(HttpURLConnection connection, long now) {
        return Math.max(now + 30_000, connection.getHeaderFieldDate("Expires", now + 5 * Forecast.MINUTE));
    }
    static long retryAt(String value, long now) {
        if (value != null) {
            try { return now + Math.max(60, Math.min((Long.MAX_VALUE - now) / 1000, Long.parseLong(value))) * 1000; }
            catch (NumberFormatException ignored) {
                try { return Math.max(now + 60_000, ZonedDateTime.parse(value,
                        DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()); }
                catch (java.time.DateTimeException ignoredDate) { }
            }
        }
        return now + 5 * Forecast.MINUTE;
    }
}
