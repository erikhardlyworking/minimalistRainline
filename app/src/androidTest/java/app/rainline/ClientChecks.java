package app.rainline;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

/** HTTP/cache integration uses isolated storage and fake responses, never the user's forecasts. */
final class ClientChecks {
    static void run(Context context) throws Exception {
        File directory = new File(context.getCacheDir(), "client-checks");
        Context isolated = new ContextWrapper(context) {
            @Override public File getCacheDir() { return directory; }
            @Override public SharedPreferences getSharedPreferences(String name, int mode) {
                return super.getSharedPreferences("client-checks-" + name, mode);
            }
        };
        context.deleteSharedPreferences("client-checks-http-backoff");
        context.deleteSharedPreferences("client-checks-widgets");
        remove(directory);
        try {
            long now = System.currentTimeMillis();
            String json = forecastJson(now);
            String modified = DateTimeFormatter.RFC_1123_DATE_TIME.format(Instant.ofEpochMilli(now).atZone(ZoneOffset.UTC));
            AtomicInteger calls = new AtomicInteger();
            MetClient client = new MetClient(isolated, url -> {
                int request = calls.getAndIncrement();
                int code = request == 0 ? 203 : request == 1 ? 304 : request == 2 ? 200 : 429;
                FakeConnection connection = new FakeConnection(url, code, json);
                connection.headers.put("Expires", DateTimeFormatter.RFC_1123_DATE_TIME.format(
                        Instant.ofEpochMilli(now + 10 * Forecast.MINUTE).atZone(ZoneOffset.UTC)));
                connection.headers.put("Last-Modified", modified);
                connection.headers.put("ETag", "\"forecast-v1\"");
                connection.headers.put("Retry-After", "172800");
                if (request == 1) connection.expectedModified = modified;
                return connection;
            });
            ForecastCache cache = new ForecastCache(isolated);
            String key = ForecastWindow.coordinateKey(60, 10);
            ForecastCache.Entry entry = client.fetch(60, 10);
            check(entry.deprecated && cache.read(key).deprecated, "203 warning must survive a cache round trip");
            client.fetch(60, 10);
            check(calls.get() == 1, "An unexpired response must suppress the next request");
            entry.expiresAt = now - 1;
            cache.write(key, entry);
            entry = client.fetch(60, 10);
            check(entry.deprecated && entry.body.equals(json), "304 must preserve the forecast and deprecation warning");
            check(calls.get() == 2 && entry.expiresAt > now, "304 must refresh cache expiry");
            entry.expiresAt = now - 1;
            cache.write(key, entry);
            entry = client.fetch(60, 10);
            check(!entry.deprecated, "A normal replacement response must clear deprecation");
            try { client.fetch(61, 11); throw new AssertionError("429 was accepted"); }
            catch (UpdateIssue.Failure e) { check(e.issue == UpdateIssue.HTTP_THROTTLED, "429 classification lost"); }
            try { client.fetch(62, 12); throw new AssertionError("Global backoff was bypassed"); }
            catch (UpdateIssue.Failure e) { check(e.issue == UpdateIssue.RETRY_DELAY, "Global retry wait not classified"); }
            check(calls.get() == 4, "Changing locations must not bypass service-wide throttling");

            // Keep the last useful forecast when a later successful response reports a radar outage.
            context.deleteSharedPreferences("client-checks-http-backoff");
            AtomicInteger outageCalls = new AtomicInteger();
            MetClient outageClient = new MetClient(isolated, url -> {
                FakeConnection response = new FakeConnection(url, outageCalls.getAndIncrement() == 0 ? 200 : 304,
                        "{\"properties\":{\"meta\":{\"updated_at\":\"" + Instant.ofEpochMilli(now)
                                + "\",\"radar_coverage\":\"temporarily unavailable\"},\"timeseries\":[]}}");
                response.headers.put("Last-Modified", "outage-validator");
                return response;
            });
            entry.expiresAt = now - 1;
            cache.write(key, entry);
            ForecastCache.Entry outage = outageClient.fetch(60, 10);
            check(!outage.forecast().hasRadar(), "Cache validators must still describe the outage response");
            check(ForecastWindow.hasUpcomingData(cache.read(key).displayForecast(now + Forecast.MINUTE), now + Forecast.MINUTE),
                    "A radar outage erased useful cached samples");
            check(!ForecastWindow.hasUpcomingData(outage.displayForecast(now + 6 * Forecast.MINUTE), now + 6 * Forecast.MINUTE),
                    "Retained data must expire by its sample timestamps");
            outage.expiresAt = now - 1;
            cache.write(key, outage);
            outage = outageClient.fetch(60, 10);
            check(outage.retainedBody.equals(json), "304 must retain the last useful forecast");
            check(outage.lastModified.equals("outage-validator"), "Retained weather must not replace HTTP validators");

            MetClient recovered = new MetClient(isolated, url -> new FakeConnection(url, 200, json));
            outage.expiresAt = now - 1;
            cache.write(key, outage);
            entry = recovered.fetch(60, 10);
            check(entry.retainedBody.isEmpty(), "Recovery must replace the temporary fallback");

            WidgetSettings settings = new WidgetSettings();
            settings.follow = false;
            settings.latitude = 59.1234; settings.longitude = 10.9876;
            settings.place = "PRIVATE PLACE SENTINEL";
            SettingsStore store = new SettingsStore(isolated);
            store.put(0, settings);
            store.status(0, "RAW ERROR SENTINEL https://api.met.no/request?lat=59.1234&lon=10.9876", UpdateIssue.HTTP_ERROR, now);
            cache.write(ForecastWindow.coordinateKey(settings.latitude, settings.longitude), entry);
            String report = Diagnostics.summary(isolated, 0);
            check(report.contains("http_error") && report.contains("Rainline/"), "Useful diagnostic context missing");
            for (String secret : new String[]{"59.1234", "10.9876", "PRIVATE PLACE", "RAW ERROR", "lat=", "https://api.met.no/request"})
                check(!report.contains(secret), "Diagnostics exposed private state: " + secret);
            check(store.get(0).place.equals(settings.place) && store.error(0).startsWith("RAW ERROR SENTINEL"),
                    "Generating diagnostics must not alter saved settings or errors");
        } finally {
            context.deleteSharedPreferences("client-checks-http-backoff");
            context.deleteSharedPreferences("client-checks-widgets");
            remove(directory);
        }
    }
    static String forecastJson(long now) {
        return "{\"properties\":{\"meta\":{\"updated_at\":\"" + Instant.ofEpochMilli(now)
                + "\",\"radar_coverage\":\"ok\",\"units\":{\"precipitation_rate\":\"mm/h\"}},\"timeseries\":["
                + point(now) + "," + point(now + 5 * Forecast.MINUTE) + "]}}";
    }
    private static String point(long now) {
        return "{\"time\":\"" + Instant.ofEpochMilli(now) + "\",\"data\":{\"instant\":{\"details\":{\"precipitation_rate\":1.2}}}}";
    }
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    private static void remove(File file) {
        File[] children = file.listFiles();
        if (children != null) for (File child : children) remove(child);
        if (file.exists() && !file.delete()) throw new AssertionError("Could not remove isolated test data");
    }
    static final class FakeConnection extends HttpURLConnection {
        final Map<String, String> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        final byte[] body;
        String expectedModified;
        FakeConnection(URL url, int code, String body) { super(url); responseCode = code; this.body = body.getBytes(StandardCharsets.UTF_8); }
        @Override public int getResponseCode() {
            if (expectedModified != null) {
                check(expectedModified.equals(getRequestProperty("If-Modified-Since")), "Server validator must be returned unchanged");
                check("\"forecast-v1\"".equals(getRequestProperty("If-None-Match")), "ETag validator missing");
            }
            return responseCode;
        }
        @Override public String getHeaderField(String name) { return headers.get(name); }
        @Override public InputStream getInputStream() { return new ByteArrayInputStream(body); }
        @Override public void connect() { }
        @Override public void disconnect() { }
        @Override public boolean usingProxy() { return false; }
    }
}
