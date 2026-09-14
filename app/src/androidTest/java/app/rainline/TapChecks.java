package app.rainline;

import android.Manifest;
import android.app.Instrumentation;
import android.app.job.JobScheduler;
import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.os.Build;
import android.os.SystemClock;
import android.util.AtomicFile;
import java.io.File;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/** Real PendingIntent -> receiver -> JobService delivery; only temporary emulator widgets. */
final class TapChecks {
    static void run(Instrumentation test, Context context) throws Exception {
        check(Build.MODEL.startsWith("sdk_gphone"), "Tap integration checks require an emulator");
        check(Updates.widgetIds(context).length == 0, "Tap checks require no existing Rainline widgets");
        check(Updates.deviceActive(context), "Unlock the test emulator first");
        AppWidgetHost host = new AppWidgetHost(context, 782317);
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        JobScheduler jobs = context.getSystemService(JobScheduler.class);
        SettingsStore store = new SettingsStore(context);
        ForecastCache cache = new ForecastCache(context);
        String key = ForecastWindow.coordinateKey(59.9139, 10.7522);
        ForecastCache.Entry previous = cache.read(key);
        int first = host.allocateAppWidgetId(), second = host.allocateAppWidgetId();
        try {
            long now = System.currentTimeMillis();
            WidgetSettings settings = new WidgetSettings();
            settings.follow = false; settings.automatic = false;
            settings.latitude = 59.9139; settings.longitude = 10.7522;
            store.put(first, settings); store.put(second, settings);
            ForecastCache.Entry entry = new ForecastCache.Entry();
            entry.body = "{\"properties\":{\"meta\":{\"updated_at\":\"" + Instant.ofEpochMilli(now)
                    + "\",\"radar_coverage\":\"ok\",\"units\":{\"precipitation_rate\":\"mm/h\"}},\"timeseries\":["
                    + point(now) + "," + point(now + 5 * Forecast.MINUTE) + "]}}";
            entry.checkedAt = now; entry.expiresAt = now + 30 * Forecast.MINUTE;
            cache.write(key, entry);
            test.getUiAutomation().adoptShellPermissionIdentity(Manifest.permission.BIND_APPWIDGET);
            try {
                for (int id : new int[]{first, second})
                    check(manager.bindAppWidgetIdIfAllowed(id, new ComponentName(context, RainWidgetProvider.class)),
                            "Could not bind temporary widget");
            } finally { test.getUiAutomation().dropShellPermissionIdentity(); }
            check(RainWidgetProvider.refreshIntent(context, first).isBroadcast(), "A tap must not launch an activity");
            long tappedAt = System.currentTimeMillis();
            RainWidgetProvider.refreshIntent(context, first).send();
            await(() -> store.attemptedAt(first) >= tappedAt, "Tap did not complete a refresh with automatic updates disabled");
            check(store.issue(first) == UpdateIssue.NONE, "Tap refresh failed");
            check(store.attemptedAt(second) == 0, "Tap refreshed a different widget");
            check(cache.read(key).checkedAt == now, "Manual tap bypassed MET's unexpired cache");
            await(() -> jobs.getPendingJob(Updates.manualJobId(first)) == null, "Manual job did not finish");
            check(!Updates.pending(context, first), "Completed tap must clear its loading state");
            check(UpdateDiagnostics.at(context, "widgetTap") >= tappedAt, "Widget tap was not recorded");
            long submittedAt = UpdateDiagnostics.at(context, "rendered." + first);
            check(submittedAt >= tappedAt, "Tap did not submit its graph to the host");
            check((manager.getAppWidgetInfo(first).widgetFeatures
                    & android.appwidget.AppWidgetProviderInfo.WIDGET_FEATURE_RECONFIGURABLE) != 0,
                    "Long-press reconfiguration metadata is missing");
            RainWidgetProvider.refreshIntent(context, 0).send();
            test.waitForIdleSync();
            check(jobs.getAllPendingJobs().stream().noneMatch(j -> j.getId() <= 0),
                    "Invalid widget tap queued a manual refresh");
        } finally {
            for (int id : new int[]{first, second}) Updates.cancelManualRefresh(context, id);
            Updates.IO.submit(() -> {}).get(5, TimeUnit.SECONDS);
            for (int id : new int[]{first, second}) {
                host.deleteAppWidgetId(id);
                store.delete(id);
                UpdateDiagnostics.deleteWidget(context, id);
            }
            if (previous == null) new AtomicFile(new File(context.getCacheDir(), "nowcast/" + key + ".json")).delete();
            else cache.write(key, previous);
            Updates.schedule(context);
        }
    }
    private static String point(long time) {
        return "{\"time\":\"" + Instant.ofEpochMilli(time)
                + "\",\"data\":{\"instant\":{\"details\":{\"precipitation_rate\":0}}}}";
    }
    private static void await(BooleanSupplier condition, String message) throws Exception {
        long deadline = SystemClock.elapsedRealtime() + 30_000;
        while (!condition.getAsBoolean() && SystemClock.elapsedRealtime() < deadline) Thread.sleep(50);
        check(condition.getAsBoolean(), message);
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
