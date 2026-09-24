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
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/** Actual JobService completion/replacement on an emulator, with no external HTTP. */
final class RecoveryJobChecks {
    static void run(Instrumentation test, Context context) throws Exception {
        check(Build.MODEL.startsWith("sdk_gphone"), "Recovery lifecycle tests require an emulator");
        check(Updates.widgetIds(context).length == 0, "Recovery lifecycle tests require no existing widgets");
        check(context.getSharedPreferences("http-backoff", 0).getAll().isEmpty(), "Recovery tests require no existing HTTP backoff");
        UiAutomation automation = test.getUiAutomation();
        AppWidgetHost host = new AppWidgetHost(context, 782317);
        JobScheduler jobs = context.getSystemService(JobScheduler.class);
        SettingsStore store = new SettingsStore(context);
        ForecastCache cache = new ForecastCache(context);
        String key = ForecastWindow.coordinateKey(60, 10);
        ForecastCache.Entry previous = cache.read(key);
        int id = host.allocateAppWidgetId();
        try {
            WidgetSettings settings = new WidgetSettings();
            settings.follow = false; settings.latitude = 60; settings.longitude = 10;
            settings.automatic = false; store.put(id, settings);
            automation.adoptShellPermissionIdentity(Manifest.permission.BIND_APPWIDGET);
            try { check(AppWidgetManager.getInstance(context).bindAppWidgetIdIfAllowed(id,
                    new ComponentName(context, RainWidgetProvider.class)), "Could not bind recovery test widget"); }
            finally { automation.dropShellPermissionIdentity(); }
            test.waitForIdleSync();
            shell(automation, "input keyevent 223");
            await(() -> !Updates.deviceActive(context), "Emulator did not sleep");
            jobs.cancelAll();
            Updates.IO.submit(() -> {}).get(5, TimeUnit.SECONDS);
            settings.automatic = true; store.put(id, settings);
            new AtomicFile(new File(context.getCacheDir(), "nowcast/" + key + ".json")).delete();
            long before = store.attemptedAt(id);
            Updates.scheduleRefresh(context, jobs, false);
            shell(automation, "cmd jobscheduler run -f app.rainline 1102");
            await(() -> attempt(jobs) == 1, "Sleeping job lost its bounded follow-up");
            check(UpdateDiagnostics.completion(context).equals("asleep_or_locked"), "Sleep skip not diagnosed");
            check(store.attemptedAt(id) == before && cache.read(key) == null, "Sleeping job attempted weather HTTP");
            long deadline = jobs.getPendingJob(RecoveryScheduler.JOB_ID).getExtras().getLong(RecoveryScheduler.DEADLINE);

            // Wake with a service backoff: cannot issue HTTP, but must preserve the retry.
            context.getSharedPreferences("http-backoff", 0).edit()
                    .putLong("retry.global", System.currentTimeMillis() + 3 * Forecast.MINUTE).commit();
            shell(automation, "input keyevent 224");
            shell(automation, "wm dismiss-keyguard");
            await(() -> Updates.deviceActive(context), "Emulator did not unlock");
            shell(automation, "cmd jobscheduler run -f app.rainline 1103");
            await(() -> attempt(jobs) == 2, "Finishing a recovery job lost its replacement");
            check(jobs.getPendingJob(RecoveryScheduler.JOB_ID).getExtras().getLong(RecoveryScheduler.DEADLINE) == deadline,
                    "Actual JobService reset recovery deadline");
            check(UpdateDiagnostics.completion(context).equals("http_backoff"), "Server backoff not diagnosed");
            check(store.attemptedAt(id) == before, "Forced-early recovery bypassed service backoff");

            // A cache filled by another refresh makes the follow-up a harmless no-op.
            ForecastCache.Entry fresh = new ForecastCache.Entry();
            fresh.body = ClientChecks.forecastJson(System.currentTimeMillis());
            fresh.checkedAt = System.currentTimeMillis(); fresh.expiresAt = fresh.checkedAt + 5 * Forecast.MINUTE;
            cache.write(key, fresh);
            context.getSharedPreferences("http-backoff", 0).edit().clear().commit();
            shell(automation, "cmd jobscheduler run -f app.rainline 1103");
            await(() -> jobs.getPendingJob(RecoveryScheduler.JOB_ID) == null, "Successful recovery left another retry queued");
            check(UpdateDiagnostics.completion(context).equals("cache_not_due"), "Recovery did not reuse fresh cache");
            check(store.attemptedAt(id) == before, "Fresh cache triggered HTTP");

            // A recovery delayed for hours by Android must expire without touching HTTP.
            android.os.PersistableBundle expired = new android.os.PersistableBundle();
            expired.putInt(RecoveryScheduler.ATTEMPT, 1);
            expired.putLong(RecoveryScheduler.DEADLINE, System.currentTimeMillis() - 1);
            jobs.schedule(new JobInfo.Builder(RecoveryScheduler.JOB_ID, new ComponentName(context, RefreshJobService.class))
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setExtras(expired).build());
            shell(automation, "cmd jobscheduler run -f app.rainline 1103");
            await(() -> jobs.getPendingJob(RecoveryScheduler.JOB_ID) == null
                    && UpdateDiagnostics.completion(context).equals("recovery_expired"), "Old recovery did not expire on start");
            check(store.attemptedAt(id) == before, "Expired recovery touched forecast status");
            RecoveryScheduler.schedule(context, jobs, new android.os.PersistableBundle(),
                    new RefreshResult(RefreshResult.Kind.RETRYABLE_FAILURE));
            check(attempt(jobs) == 1, "Could not prepare disabled-update cancellation check");
            settings.automatic = false; store.put(id, settings);
            Updates.schedule(context);
            check(jobs.getPendingJob(RecoveryScheduler.JOB_ID) == null, "Disabling automatic updates left recovery queued");
        } finally {
            WidgetSettings disabled = store.get(id); disabled.automatic = false; store.put(id, disabled);
            jobs.cancelAll();
            Updates.IO.submit(() -> {}).get(5, TimeUnit.SECONDS);
            host.deleteAppWidgetId(id);
            store.delete(id); UpdateDiagnostics.deleteWidget(context, id);
            context.getSharedPreferences("http-backoff", 0).edit().clear().commit();
            if (previous == null) new AtomicFile(new File(context.getCacheDir(), "nowcast/" + key + ".json")).delete();
            else cache.write(key, previous);
            Updates.schedule(context);
            shell(automation, "input keyevent 224");
            shell(automation, "wm dismiss-keyguard");
        }
    }
    private static int attempt(JobScheduler jobs) {
        JobInfo job = jobs.getPendingJob(RecoveryScheduler.JOB_ID);
        return job == null ? 0 : job.getExtras().getInt(RecoveryScheduler.ATTEMPT);
    }
    private static String shell(UiAutomation automation, String command) throws Exception {
        try (ParcelFileDescriptor fd = automation.executeShellCommand(command);
             FileInputStream input = new FileInputStream(fd.getFileDescriptor())) {
            return new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }
    private static void await(BooleanSupplier condition, String message) throws Exception {
        long deadline = SystemClock.elapsedRealtime() + 20_000;
        while (!condition.getAsBoolean() && SystemClock.elapsedRealtime() < deadline) Thread.sleep(50);
        check(condition.getAsBoolean(), message);
    }
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
}
