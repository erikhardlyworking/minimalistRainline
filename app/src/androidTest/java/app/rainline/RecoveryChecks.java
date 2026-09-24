package app.rainline;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** Fault injection without a MET request, isolated from real preferences and forecasts. */
final class RecoveryChecks {
    static void run(Context context) throws Exception {
        File directory = new File(context.getCacheDir(), "recovery-checks");
        AtomicBoolean asleep = new AtomicBoolean();
        Context isolated = new ContextWrapper(context) {
            @Override public File getCacheDir() { return directory; }
            @Override public SharedPreferences getSharedPreferences(String name, int mode) {
                return super.getSharedPreferences("recovery-checks-" + name, mode);
            }
            @Override public Object getSystemService(String name) {
                return asleep.get() && Context.POWER_SERVICE.equals(name) ? null : super.getSystemService(name);
            }
        };
        try {
            for (String name : new String[]{"widgets", "http-backoff", "update-diagnostics"})
                isolated.getSharedPreferences(name, 0).edit().clear().commit();
            remove(directory);
            long now = System.currentTimeMillis();
            WidgetSettings settings = new WidgetSettings();
            settings.follow = false; settings.latitude = 60; settings.longitude = 10;
            SettingsStore store = new SettingsStore(isolated);
            store.put(0, settings);
            String key = ForecastWindow.coordinateKey(60, 10);
            AtomicInteger calls = new AtomicInteger();
            MetClient client = new MetClient(isolated, url -> {
                if (calls.getAndIncrement() == 0) throw new UnknownHostException("PRIVATE HOST SENTINEL");
                return new ClientChecks.FakeConnection(url, 200, ClientChecks.forecastJson(System.currentTimeMillis()));
            });
            asleep.set(true);
            RefreshResult result = Updates.refreshOne(isolated, 0, false, false, client);
            check(result.kind == RefreshResult.Kind.ASLEEP_OR_LOCKED && calls.get() == 0, "Locked/sleeping worker made HTTP request");
            check(store.attemptedAt(0) == 0, "Sleep skip replaced forecast status");
            asleep.set(false);
            result = Updates.refreshOne(isolated, 0, true, false, client);
            check(result.kind == RefreshResult.Kind.RETRYABLE_FAILURE && store.issue(0) == UpdateIssue.DNS_ERROR,
                    "DNS failure was swallowed instead of returned to scheduler");
            check(result.retryAt >= now + 60_000, "Worker lost transport backoff");
            result = Updates.refreshOne(isolated, 0, true, false, client);
            check(result.kind == RefreshResult.Kind.HTTP_BACKOFF && calls.get() == 1, "Early retry bypassed MET backoff");
            // Simulate reaching the retry time; no real sleep/network required.
            isolated.getSharedPreferences("http-backoff", 0).edit().clear().commit();
            result = Updates.refreshOne(isolated, 0, true, false, client);
            check(result.kind == RefreshResult.Kind.SUCCESS && calls.get() == 2 && store.issue(0) == UpdateIssue.NONE,
                    "Retry after network recovery did not clear failure and cache forecast");
            result = Updates.refreshOne(isolated, 0, true, false, client);
            check(result.kind == RefreshResult.Kind.CACHE_NOT_DUE && calls.get() == 2, "Successful recovery kept fetching");
            long attempted = store.attemptedAt(0);
            Thread.currentThread().interrupt();
            try {
                result = Updates.refreshOne(isolated, 0, true, true, client);
                check(result.kind == RefreshResult.Kind.CANCELLED && calls.get() == 2 && store.attemptedAt(0) == attempted,
                        "Cancelled job touched network or forecast status");
            } finally { Thread.interrupted(); }
            settings.latitude = 61; store.put(0, settings);
            MetClient denied = new MetClient(isolated, url -> new ClientChecks.FakeConnection(url, 403, ""));
            result = Updates.refreshOne(isolated, 0, true, false, denied);
            check(result.kind == RefreshResult.Kind.PERMANENT_FAILURE, "Forbidden request got a rapid retry");
            check(RecoveryPolicy.nextAt(result, 0, now + RecoveryPolicy.WINDOW, now, 0) == 0, "403 retry scheduled");
            UpdateDiagnostics.completed(isolated, result);
            String summary = Diagnostics.summary(isolated, 0);
            check(summary.contains("permanent_failure") && !summary.contains("PRIVATE HOST"), "Completion diagnostics missing or leak exception text");

            JobNetwork network = new JobNetwork(true, null);
            URL url = new URL("https://api.met.no/");
            try { network.open(url); throw new AssertionError("Missing job network silently used default network"); }
            catch (IOException expected) { }
            Network active = context.getSystemService(ConnectivityManager.class).getActiveNetwork();
            check(active != null, "Network assignment check requires connected emulator");
            network.changed(active);
            network.open(url).disconnect(); // Construction only; no request to MET.
            network.changed(null);
            try { network.open(url); throw new AssertionError("Lost network assignment was ignored"); }
            catch (IOException expected) { }
            new JobNetwork(false, null).open(url).disconnect(); // API 26/27 fallback.
        } finally {
            for (String name : new String[]{"widgets", "http-backoff", "update-diagnostics"})
                context.deleteSharedPreferences("recovery-checks-" + name);
            remove(directory);
        }
    }
    private static void remove(File file) {
        File[] children = file.listFiles();
        if (children != null) for (File child : children) remove(child);
        if (file.exists() && !file.delete()) throw new AssertionError("Could not remove isolated test data");
    }
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
}
