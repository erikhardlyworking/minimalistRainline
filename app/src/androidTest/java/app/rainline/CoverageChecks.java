package app.rainline;

import android.Manifest;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import java.io.File;
import java.util.concurrent.atomic.AtomicInteger;

/** Local exclusion and return-from-travel tests; never send these test locations to MET. */
final class CoverageChecks {
    static void run(Context context) throws Exception {
        File directory = new File(context.getCacheDir(), "coverage-checks");
        Context isolated = new ContextWrapper(context) {
            @Override public File getCacheDir() { return directory; }
            @Override public SharedPreferences getSharedPreferences(String name, int mode) {
                return super.getSharedPreferences("coverage-checks-" + name, mode);
            }
            @Override public int checkSelfPermission(String permission) {
                // Deterministic followed-location fixture: reuse the recent stored fix.
                return Manifest.permission.ACCESS_BACKGROUND_LOCATION.equals(permission)
                        ? PackageManager.PERMISSION_DENIED : PackageManager.PERMISSION_GRANTED;
            }
        };
        try {
            for (String name : new String[]{"widgets", "http-backoff"}) isolated.getSharedPreferences(name, 0).edit().clear().commit();
            remove(directory);
            long now = System.currentTimeMillis();
            AtomicInteger calls = new AtomicInteger();
            MetClient client = new MetClient(isolated, url -> {
                calls.incrementAndGet();
                return new ClientChecks.FakeConnection(url, 200, ClientChecks.forecastJson(System.currentTimeMillis()));
            });
            WidgetSettings paris = new WidgetSettings();
            paris.follow = false; paris.latitude = 48.8566; paris.longitude = 2.3522;
            SettingsStore store = new SettingsStore(isolated); store.put(0, paris);
            check(!Updates.weatherCheckDue(isolated, paris, now), "Outside forecast was due for network checking");
            check(!Updates.automaticRefreshDue(isolated, paris, now), "Fixed outside place consumed wake-job quota");
            try { client.fetch(paris.latitude, paris.longitude); throw new AssertionError("Outside client fetch was allowed"); }
            catch (UpdateIssue.Failure e) { check(e.issue == UpdateIssue.OUTSIDE_COVERAGE, "Outside failure category lost"); }
            ForecastCache.Entry old = new ForecastCache.Entry();
            old.body = ClientChecks.forecastJson(now); old.checkedAt = now; old.expiresAt = now + Forecast.MINUTE;
            new ForecastCache(isolated).write(ForecastWindow.coordinateKey(paris.latitude, paris.longitude), old);
            try { client.fetch(paris.latitude, paris.longitude); throw new AssertionError("Old cache bypassed local coverage rule"); }
            catch (UpdateIssue.Failure e) { check(e.issue == UpdateIssue.OUTSIDE_COVERAGE, "Cached outside category lost"); }
            RefreshResult manual = Updates.refreshOne(isolated, 0, true, true, client);
            check(manual.kind == RefreshResult.Kind.PERMANENT_FAILURE && store.issue(0) == UpdateIssue.OUTSIDE_COVERAGE,
                    "Manual outside refresh must explain coverage without a retry burst");
            check(calls.get() == 0, "Manual/outside cache paths attempted HTTP");
            check(isolated.getSharedPreferences("http-backoff", 0).getAll().isEmpty(), "Local rejection created a server backoff");
            check(Diagnostics.summary(isolated, 0).contains("Inside approximate forecast region: false"), "Local exclusion not diagnosable");

            if (android.os.Build.VERSION.SDK_INT >= 29) {
                paris.follow = true; paris.locationAt = now; store.put(0, paris);
                check(Updates.automaticRefreshDue(isolated, paris, now), "Following an outside place prevented location rechecks");
                store.updateFollowedLocation(paris.latitude, paris.longitude, now, 100);
                RefreshResult outside = Updates.refreshOne(isolated, 0, false, false, client);
                check(outside.kind == RefreshResult.Kind.PERMANENT_FAILURE && calls.get() == 0,
                        "Followed outside position attempted forecast HTTP");
                store.updateFollowedLocation(60, 10, now, 100);
                RefreshResult returned = Updates.refreshOne(isolated, 0, false, false, client);
                check(returned.kind == RefreshResult.Kind.SUCCESS && calls.get() == 1,
                        "Returning from abroad did not resume forecasts");
                check(store.get(0).latitude == 60 && store.issue(0) == UpdateIssue.NONE, "Old outside status/location survived return");
                check(!Updates.weatherCheckDue(isolated, store.get(0), System.currentTimeMillis()), "Return ignored fresh cache");
            }

            // The rectangle admits uncertain nearby locations; preserve MET's final verdict.
            for (int code : new int[]{404, 422}) {
                isolated.getSharedPreferences("http-backoff", 0).edit().clear().commit();
                MetClient edge = new MetClient(isolated, url -> new ClientChecks.FakeConnection(url, code, ""));
                try { edge.fetch(59.437, 24.7536); throw new AssertionError("Server coverage error accepted"); }
                catch (UpdateIssue.Failure e) { check(e.issue == UpdateIssue.OUTSIDE_COVERAGE, "Server coverage category lost"); }
            }
        } finally {
            for (String name : new String[]{"widgets", "http-backoff"}) context.deleteSharedPreferences("coverage-checks-" + name);
            remove(directory);
        }
    }
    private static void remove(File file) {
        File[] children = file.listFiles();
        if (children != null) for (File child : children) remove(child);
        if (file.exists() && !file.delete()) throw new AssertionError("Could not remove coverage test data");
    }
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
}
