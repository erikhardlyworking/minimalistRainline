package app.rainline;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import java.io.File;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Cadence gates and a manual cached refresh, isolated from user settings and network. */
final class RefreshChecks {
    static void run(Context context) throws Exception {
        File directory = new File(context.getCacheDir(), "refresh-checks");
        Context isolated = new ContextWrapper(context) {
            @Override public Context getApplicationContext() { return this; }
            @Override public File getCacheDir() { return directory; }
            @Override public SharedPreferences getSharedPreferences(String name, int mode) {
                return super.getSharedPreferences("refresh-checks-" + name, mode);
            }
        };
        try {
            // A prior interrupted run can leave this test's private preferences behind.
            isolated.getSharedPreferences("widgets", 0).edit().clear().commit();
            isolated.getSharedPreferences("http-backoff", 0).edit().clear().commit();
            long now = System.currentTimeMillis();
            WidgetSettings dry = new WidgetSettings();
            dry.follow = false; dry.latitude = 59.9139; dry.longitude = 10.7522;
            SettingsStore store = new SettingsStore(isolated);
            store.put(0, dry);
            ForecastCache.Entry entry = new ForecastCache.Entry();
            StringBuilder points = new StringBuilder();
            for (int minute = 0; minute <= 120; minute += 5) {
                if (minute > 0) points.append(',');
                points.append("{\"time\":\"").append(Instant.ofEpochMilli(now + minute * Forecast.MINUTE))
                        .append("\",\"data\":{\"instant\":{\"details\":{\"precipitation_rate\":0}}}}");
            }
            entry.body = "{\"properties\":{\"meta\":{\"updated_at\":\"" + Instant.ofEpochMilli(now)
                    + "\",\"radar_coverage\":\"ok\",\"units\":{\"precipitation_rate\":\"mm/h\"}},\"timeseries\":[" + points + "]}}";
            entry.checkedAt = now - 5 * Forecast.MINUTE; entry.expiresAt = now - 1;
            ForecastCache cache = new ForecastCache(isolated);
            String key = ForecastWindow.coordinateKey(dry.latitude, dry.longitude);
            cache.write(key, entry);
            check(!Updates.weatherCheckDue(isolated, dry, now), "Expired HTTP cache bypassed the dry interval");
            Updates.refreshFromSettings(isolated, 0, false, () -> { throw new AssertionError("Automatic refresh ran too soon"); });
            check(store.attemptedAt(0) == 0, "Opening settings bypassed the dry interval");
            WidgetSettings faster = dry.copy(); faster.dryRefreshMinutes = 5;
            check(Updates.weatherCheckDue(isolated, faster, now), "Widgets must honour their own intervals");
            check(Updates.weatherCheckDue(isolated, dry, now + 10 * Forecast.MINUTE), "Dry cache did not become due at 15 minutes");
            isolated.getSharedPreferences("http-backoff", 0).edit().putLong("retry.global", now + Forecast.MINUTE).apply();
            check(!Updates.weatherCheckDue(isolated, faster, now), "A shorter interval bypassed HTTP retry delay");
            isolated.getSharedPreferences("http-backoff", 0).edit().clear().apply();

            // Manual refresh runs now, but an unexpired server cache still prevents any HTTP.
            entry.expiresAt = now + 10 * Forecast.MINUTE; cache.write(key, entry);
            CountDownLatch completed = new CountDownLatch(1);
            Updates.refreshFromSettings(isolated, 0, true, completed::countDown);
            check(completed.await(10, TimeUnit.SECONDS), "Manual cached refresh did not finish");
            check(store.attemptedAt(0) >= now, "Manual refresh was suppressed by the dry interval");
            check(cache.read(key).checkedAt == entry.checkedAt, "Manual refresh ignored server cache expiry");
            check(store.issue(0) == UpdateIssue.NONE, "Manual cached refresh failed");
        } finally {
            for (String name : new String[]{"widgets", "http-backoff"}) {
                isolated.getSharedPreferences(name, 0).edit().clear().commit();
                context.deleteSharedPreferences("refresh-checks-" + name);
            }
            remove(directory);
        }
    }
    private static void remove(File file) {
        File[] children = file.listFiles();
        if (children != null) for (File child : children) remove(child);
        if (file.exists() && !file.delete()) throw new AssertionError("Could not delete isolated refresh test data");
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
