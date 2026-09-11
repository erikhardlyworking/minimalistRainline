package app.rainline;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.PowerManager;
import android.app.KeyguardManager;
import java.util.concurrent.ThreadLocalRandom;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

public final class Updates {
    static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final AtomicBoolean refreshing = new AtomicBoolean();
    private static final int PERIODIC_JOB = 1101, IMMEDIATE_JOB = 1102;
    private Updates() {}

    public static int[] widgetIds(Context context) {
        return AppWidgetManager.getInstance(context).getAppWidgetIds(new ComponentName(context, RainWidgetProvider.class));
    }
    public static void schedule(Context context) {
        int[] ids = widgetIds(context);
        boolean automatic = false;
        SettingsStore store = new SettingsStore(context);
        for (int id : ids) automatic |= store.get(id).automatic;
        JobScheduler jobs = context.getSystemService(JobScheduler.class);
        if (automatic && jobs.getPendingJob(PERIODIC_JOB) == null) {
            jobs.schedule(new JobInfo.Builder(PERIODIC_JOB, new ComponentName(context, RefreshJobService.class))
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setPeriodic(15 * Forecast.MINUTE, 5 * Forecast.MINUTE)
                    .setPersisted(true).setBackoffCriteria(60_000, JobInfo.BACKOFF_POLICY_EXPONENTIAL).build());
        } else if (!automatic) jobs.cancel(PERIODIC_JOB);
        if (ids.length == 0) jobs.cancel(IMMEDIATE_JOB);
        AlarmManager alarms = context.getSystemService(AlarmManager.class);
        PendingIntent pending = PendingIntent.getBroadcast(context, 0,
                new Intent(context, RefreshReceiver.class).setAction("app.rainline.TICK"),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        if (ids.length > 0) {
            // Non-wakeup, inexact: refresh the axis when Android allows without waking a sleeping phone.
            alarms.set(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + 5 * Forecast.MINUTE
                    + ThreadLocalRandom.current().nextLong(60_001), pending);
        } else alarms.cancel(pending);
    }
    public static void enqueue(Context context) {
        if (!canFetchInBackground(context)) return;
        JobScheduler jobs = context.getSystemService(JobScheduler.class);
        if (jobs.getPendingJob(IMMEDIATE_JOB) == null) {
            jobs.schedule(new JobInfo.Builder(IMMEDIATE_JOB, new ComponentName(context, RefreshJobService.class))
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setMinimumLatency(ThreadLocalRandom.current().nextLong(5_000, 35_001))
                    .setBackoffCriteria(60_000, JobInfo.BACKOFF_POLICY_EXPONENTIAL).build());
        }
    }
    public static Future<?> refreshWidgets(Context context, Runnable completed) {
        Context app = context.getApplicationContext();
        return IO.submit(() -> {
            try {
                if (!canFetchInBackground(app)) return;
                SettingsStore store = new SettingsStore(app);
                for (int id : widgetIds(app)) {
                    if (Thread.currentThread().isInterrupted() || !deviceActive(app)) break;
                    if (store.get(id).automatic) refreshOne(app, id, false, false);
                }
            } finally {
                RainWidgetProvider.renderAll(app);
                new Handler(Looper.getMainLooper()).post(completed);
            }
        });
    }
    public static void refreshFromSettings(Context context, int id, boolean locate, Runnable completed) {
        if (!refreshing.compareAndSet(false, true)) return;
        Context app = context.getApplicationContext();
        IO.execute(() -> {
            try { refreshOne(app, id, true, locate); }
            finally {
                refreshing.set(false);
                new Handler(Looper.getMainLooper()).post(completed);
            }
        });
    }
    public static boolean busy() { return refreshing.get(); }

    static boolean deviceActive(Context context) {
        PowerManager power = context.getSystemService(PowerManager.class);
        KeyguardManager keyguard = context.getSystemService(KeyguardManager.class);
        return power != null && power.isInteractive() && (keyguard == null || !keyguard.isKeyguardLocked());
    }
    static boolean canFetchInBackground(Context context) {
        if (!deviceActive(context)) return false;
        SettingsStore store = new SettingsStore(context);
        for (int id : widgetIds(context)) if (store.get(id).automatic) return true;
        return false;
    }

    private static void refreshOne(Context context, int id, boolean foreground, boolean locate) {
        SettingsStore store = new SettingsStore(context);
        WidgetSettings settings = store.get(id);
        long now = System.currentTimeMillis();
        UpdateIssue stage = UpdateIssue.LOCATION_UNAVAILABLE;
        try {
            if (settings.follow) {
                WidgetSettings location = LocationAccess.resolve(context, foreground);
                // User may have switched to a fixed place while the location request was running.
                settings = store.get(id);
                if (settings.follow) {
                    settings.latitude = location.latitude;
                    settings.longitude = location.longitude;
                    settings.locationAt = location.locationAt;
                    settings.accuracy = location.accuracy;
                    settings.place = "Current location";
                    store.put(id, settings);
                }
            }
            if (!settings.hasLocation()) throw UpdateIssue.LOCATION_MISSING.failure("Choose a location to get your forecast.");
            if (settings.locationExpired(now)) throw UpdateIssue.LOCATION_STALE.failure("Open Rainline to update your location.");
            // The display may have turned off during a location request. Keep the cached forecast then.
            if (!foreground && !deviceActive(context)) return;
            stage = UpdateIssue.IO_ERROR;
            ForecastCache.Entry entry = new MetClient(context).fetch(settings.latitude, settings.longitude);
            if (settings.sameLocation(store.get(id))) store.status(id,
                    entry.deprecated ? "MET is retiring this API version. Check for a Rainline update." : "",
                    entry.deprecated ? UpdateIssue.API_DEPRECATED : UpdateIssue.NONE, now);
        } catch (IOException e) {
            if (settings.sameLocation(store.get(id)) || !store.get(id).hasLocation())
                store.status(id, e.getMessage() == null ? "Couldn't update the forecast." : e.getMessage(), UpdateIssue.from(e, stage), now);
        }
        RainWidgetProvider.render(context, id);
    }
}
