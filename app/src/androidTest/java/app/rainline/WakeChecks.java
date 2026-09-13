package app.rainline;

import android.Manifest;
import android.app.Instrumentation;
import android.app.KeyguardManager;
import android.app.UiAutomation;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetProviderInfo;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.util.AtomicFile;
import android.widget.RemoteViews;
import java.io.File;
import java.io.FileInputStream;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.concurrent.atomic.AtomicInteger;

/** Opt-in emulator-only test: real screen events and a completed MET fetch at a public Oslo test location. */
final class WakeChecks {
    static void run(Instrumentation test, Context context) throws Exception {
        check(Build.MODEL.startsWith("sdk_gphone"), "Screen-control tests are restricted to the Android emulator");
        check(Updates.widgetIds(context).length == 0, "Wake tests require an emulator with no existing Rainline widgets");
        UiAutomation automation = test.getUiAutomation();
        check(!context.getSystemService(KeyguardManager.class).isKeyguardSecure(),
                "Wake tests require an emulator without a PIN or password");
        String lockDisabled = shell(automation, "locksettings get-disabled").trim();
        check("true".equals(lockDisabled) || "false".equals(lockDisabled), "Could not read emulator lock setting");
        AtomicInteger rejectedReceiverEvents = new AtomicInteger();
        BroadcastReceiver oldRegistration = new BroadcastReceiver() {
            @Override public void onReceive(Context ctx, Intent intent) { rejectedReceiverEvents.incrementAndGet(); }
        };
        if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(oldRegistration,
                new IntentFilter(Intent.ACTION_USER_PRESENT), Context.RECEIVER_NOT_EXPORTED);
        AtomicInteger delivered = new AtomicInteger();
        AppWidgetHost host = new AppWidgetHost(context, 782316) {
            @Override protected AppWidgetHostView onCreateView(Context ctx, int widgetId, AppWidgetProviderInfo info) {
                return new AppWidgetHostView(ctx) {
                    @Override public void updateAppWidget(RemoteViews views) {
                        super.updateAppWidget(views);
                        if (views != null) delivered.incrementAndGet();
                    }
                };
            }
        };
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        JobScheduler jobs = context.getSystemService(JobScheduler.class);
        SettingsStore store = new SettingsStore(context);
        ForecastCache cache = new ForecastCache(context);
        String key = ForecastWindow.coordinateKey(59.9139, 10.7522);
        ForecastCache.Entry previous = cache.read(key);
        int id = host.allocateAppWidgetId();
        try {
            shell(automation, "locksettings set-disabled false");
            long now = System.currentTimeMillis();
            WidgetSettings settings = new WidgetSettings();
            settings.follow = false; settings.latitude = 59.9139; settings.longitude = 10.7522;
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
            test.runOnMainSync(() -> {
                host.createView(context, id, manager.getAppWidgetInfo(id));
                host.startListening();
            });
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
            await(() -> context.getSystemService(KeyguardManager.class).isKeyguardLocked(), "Emulator lock screen was not shown");
            shell(automation, "wm dismiss-keyguard");
            await(() -> Updates.deviceActive(context), "Emulator did not become interactive and unlocked");
            await(() -> UpdateDiagnostics.at(context, "unlock") >= wakeAt,
                    "System UI unlock broadcast was not delivered (screen-on alone is insufficient)");
            if (Build.VERSION.SDK_INT >= 33) check(rejectedReceiverEvents.get() == 0,
                    "Emulator did not reproduce rejection of the old NOT_EXPORTED receiver");
            await(() -> UpdateDiagnostics.at(context, "rendered." + id) >= wakeAt,
                    "Unlock did not submit an aligned graph to the launcher");
            check(jobs.getPendingJob(1102) == null, "Fresh cache should not consume wake-job quota");
            check(cache.read(key).checkedAt == now, "Wake request bypassed a valid HTTP cache");
            check(ForecastState.of(settings, entry.displayForecast(System.currentTimeMillis()), System.currentTimeMillis(),
                    store.issue(id), Updates.pending(context, id)) == ForecastState.DATA, "Wake discarded usable cached data");

            // Exercise the user's failure mode: an expired cache must actually be refreshed after wake.
            shell(automation, "input keyevent 223");
            await(() -> !Updates.deviceActive(context), "Emulator did not sleep a second time");
            jobs.cancelAll();
            Updates.IO.submit(() -> {}).get(5, TimeUnit.SECONDS);
            entry.expiresAt = System.currentTimeMillis() - 1;
            cache.write(key, entry);
            long fetchWakeAt = System.currentTimeMillis();
            int deliveredBefore = delivered.get();
            shell(automation, "input keyevent 224");
            await(() -> context.getSystemService(KeyguardManager.class).isKeyguardLocked(), "Second wake bypassed the lock screen");
            check(store.attemptedAt(id) == before, "A locked screen triggered a weather request");
            shell(automation, "wm dismiss-keyguard");
            await(() -> {
                ForecastCache.Entry refreshed = cache.read(key);
                return refreshed != null && refreshed.checkedAt >= fetchWakeAt && store.attemptedAt(id) >= fetchWakeAt;
            }, "Wake queued work but did not complete a forecast fetch: " + UpdateDiagnostics.outcome(context));
            check(UpdateDiagnostics.at(context, "started") >= fetchWakeAt, "Wake did not start a job");
            check(store.issue(id) == UpdateIssue.NONE || store.issue(id) == UpdateIssue.API_DEPRECATED,
                    "Wake fetch completed with " + store.issue(id));
            check(cache.read(key).forecast().points.size() > 1, "Wake did not download a real forecast");
            long fetchedForecastAt = cache.read(key).forecast().updatedAt;
            await(() -> UpdateDiagnostics.at(context, "forecastAt." + id) == fetchedForecastAt,
                    "Downloaded forecast was not submitted to the launcher");
            await(() -> delivered.get() > deliveredBefore, "Widget host never received the new RemoteViews");
        } catch (Throwable failure) {
            throw new AssertionError(failure.getMessage() + "\n" + Diagnostics.summary(context, id), failure);
        } finally {
            if (Build.VERSION.SDK_INT >= 33) context.unregisterReceiver(oldRegistration);
            WidgetSettings disabled = store.get(id); disabled.automatic = false; store.put(id, disabled);
            jobs.cancelAll();
            Updates.IO.submit(() -> {}).get(5, TimeUnit.SECONDS);
            host.deleteAppWidgetId(id);
            host.stopListening();
            store.delete(id);
            UpdateDiagnostics.deleteWidget(context, id);
            if (previous == null) new AtomicFile(new File(context.getCacheDir(), "nowcast/" + key + ".json")).delete();
            else cache.write(key, previous);
            Updates.schedule(context);
            shell(automation, "input keyevent 224");
            shell(automation, "wm dismiss-keyguard");
            shell(automation, "locksettings set-disabled " + lockDisabled);
        }
    }
    private static String point(long time) {
        return "{\"time\":\"" + Instant.ofEpochMilli(time)
                + "\",\"data\":{\"instant\":{\"details\":{\"precipitation_rate\":0.4}}}}";
    }
    private static String shell(UiAutomation automation, String command) throws Exception {
        try (ParcelFileDescriptor fd = automation.executeShellCommand(command);
             FileInputStream input = new FileInputStream(fd.getFileDescriptor())) {
            return new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }
    private static void await(BooleanSupplier condition, String message) throws Exception {
        long deadline = SystemClock.elapsedRealtime() + 30_000;
        while (!condition.getAsBoolean() && SystemClock.elapsedRealtime() < deadline) Thread.sleep(50);
        check(condition.getAsBoolean(), message);
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
