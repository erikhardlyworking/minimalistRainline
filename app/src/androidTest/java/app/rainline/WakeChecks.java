package app.rainline;

import android.Manifest;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.util.AtomicFile;
import java.io.File;
import java.io.FileInputStream;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/** Opt-in emulator-only test: real screen events and jobs, with an isolated test widget and cached data. */
final class WakeChecks {
    static void run(Instrumentation test, Context context) throws Exception {
        check(Build.MODEL.startsWith("sdk_gphone"), "Screen-control tests are restricted to the Android emulator");
        check(Updates.widgetIds(context).length == 0, "Wake tests require an emulator with no existing Rainline widgets");
        UiAutomation automation = test.getUiAutomation();
        AppWidgetHost host = new AppWidgetHost(context, 782316);
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        JobScheduler jobs = context.getSystemService(JobScheduler.class);
        SettingsStore store = new SettingsStore(context);
        ForecastCache cache = new ForecastCache(context);
        String key = ForecastWindow.coordinateKey(0, 179.1234);
        ForecastCache.Entry previous = cache.read(key);
        int id = host.allocateAppWidgetId();
        try {
            long now = System.currentTimeMillis();
            WidgetSettings settings = new WidgetSettings();
            settings.follow = false; settings.latitude = 0; settings.longitude = 179.1234;
            store.put(id, settings);
            ForecastCache.Entry entry = new ForecastCache.Entry();
            entry.body = "{\"properties\":{\"meta\":{\"updated_at\":\"" + Instant.ofEpochMilli(now)
                    + "\",\"radar_coverage\":\"ok\",\"units\":{\"precipitation_rate\":\"mm/h\"}},\"timeseries\":["
                    + point(now) + "," + point(now + 5 * Forecast.MINUTE) + "]}}";
            entry.checkedAt = now; entry.expiresAt = now + 30 * Forecast.MINUTE;
            cache.write(key, entry);
            automation.adoptShellPermissionIdentity(Manifest.permission.BIND_APPWIDGET);
            try { check(manager.bindAppWidgetIdIfAllowed(id, new ComponentName(context, RainWidgetProvider.class)), "Could not bind test widget"); }
            finally { automation.dropShellPermissionIdentity(); }
            test.waitForIdleSync();

            shell(automation, "input keyevent 223");
            await(() -> !Updates.deviceActive(context), "Emulator did not sleep");
            jobs.cancelAll();
            Updates.IO.submit(() -> {}).get(5, TimeUnit.SECONDS);
            long before = store.attemptedAt(id);
            Updates.enqueue(context, true);
            check(jobs.getPendingJob(1102) == null, "Sleeping device queued a weather request");
            check(store.attemptedAt(id) == before, "Sleeping device attempted to update");

            long wakeAt = System.currentTimeMillis();
            shell(automation, "input keyevent 224");
            shell(automation, "wm dismiss-keyguard");
            await(() -> Updates.deviceActive(context), "Emulator did not become interactive and unlocked");
            await(() -> jobs.getPendingJob(1102) != null || store.attemptedAt(id) >= wakeAt,
                    "Real wake/unlock broadcasts did not queue a refresh");
            JobInfo queued = jobs.getPendingJob(1102);
            if (queued != null) check(queued.getMinLatencyMillis() == 0, "Wake refresh added a random delay");
            check(cache.read(key).checkedAt == now, "Wake request bypassed a valid HTTP cache");
            check(ForecastState.of(settings, entry.displayForecast(System.currentTimeMillis()), System.currentTimeMillis(),
                    store.issue(id), Updates.pending(context, id)) == ForecastState.DATA, "Wake discarded usable cached data");
        } finally {
            WidgetSettings disabled = store.get(id); disabled.automatic = false; store.put(id, disabled);
            jobs.cancelAll();
            Updates.IO.submit(() -> {}).get(5, TimeUnit.SECONDS);
            host.deleteAppWidgetId(id);
            store.delete(id);
            if (previous == null) new AtomicFile(new File(context.getCacheDir(), "nowcast/" + key + ".json")).delete();
            else cache.write(key, previous);
            Updates.schedule(context);
            shell(automation, "input keyevent 224");
            shell(automation, "wm dismiss-keyguard");
        }
    }
    private static String point(long time) {
        return "{\"time\":\"" + Instant.ofEpochMilli(time)
                + "\",\"data\":{\"instant\":{\"details\":{\"precipitation_rate\":0.4}}}}";
    }
    private static void shell(UiAutomation automation, String command) throws Exception {
        try (ParcelFileDescriptor fd = automation.executeShellCommand(command);
             FileInputStream input = new FileInputStream(fd.getFileDescriptor())) { input.readAllBytes(); }
    }
    private static void await(BooleanSupplier condition, String message) throws Exception {
        long deadline = SystemClock.elapsedRealtime() + 10_000;
        while (!condition.getAsBoolean() && SystemClock.elapsedRealtime() < deadline) Thread.sleep(50);
        check(condition.getAsBoolean(), message);
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
