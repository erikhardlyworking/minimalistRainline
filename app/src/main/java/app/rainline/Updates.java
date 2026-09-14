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
import android.os.PersistableBundle;
import android.os.Build;
import android.app.KeyguardManager;
import java.util.concurrent.ThreadLocalRandom;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class Updates {
    static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final AtomicBoolean refreshing = new AtomicBoolean();
    private static volatile int settingsRefreshId = -1;
    private static final Set<Integer> inFlight = ConcurrentHashMap.newKeySet();
    private static final int PERIODIC_JOB = 1101, IMMEDIATE_JOB = 1102;
    private static final String REQUESTED_AT = "requestedAt";
    static final String MANUAL_WIDGET = "manualWidget";
    private Updates() {}

    public static int[] widgetIds(Context context) {
        return AppWidgetManager.getInstance(context).getAppWidgetIds(new ComponentName(context, RainWidgetProvider.class));
    }
    static boolean isOurWidget(Context context, int id) {
        if (id <= 0) return false;
        android.appwidget.AppWidgetProviderInfo info = AppWidgetManager.getInstance(context).getAppWidgetInfo(id);
        return info != null && new ComponentName(context, RainWidgetProvider.class).equals(info.provider);
    }
    // Automatic jobs have positive IDs; each placed widget owns one negative manual job ID.
    static int manualJobId(int widgetId) { return -widgetId; }
    static void cancelManualRefresh(Context context, int id) {
        if (id > 0) context.getSystemService(JobScheduler.class).cancel(manualJobId(id));
    }
    static void enqueueManual(Context context, int id) {
        if (!isOurWidget(context, id) || !deviceActive(context)) return;
        scheduleRefresh(context, context.getSystemService(JobScheduler.class), true, id);
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
        if (!automatic) jobs.cancel(IMMEDIATE_JOB);
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
        enqueue(context, false);
    }
    public static void enqueue(Context context, boolean prompt) {
        if (!canFetchInBackground(context)) return;
        if (!forecastCheckDue(context)) return;
        JobScheduler jobs = context.getSystemService(JobScheduler.class);
        scheduleRefresh(context, jobs, prompt);
    }
    static void scheduleRefresh(Context context, JobScheduler jobs, boolean prompt) {
        scheduleRefresh(context, jobs, prompt, 0);
    }
    static void scheduleRefresh(Context context, JobScheduler jobs, boolean prompt, int manualWidgetId) {
        int jobId = manualWidgetId > 0 ? manualJobId(manualWidgetId) : IMMEDIATE_JOB;
        JobInfo existing = jobs.getPendingJob(jobId);
        if (existing != null && (!prompt || RefreshJobService.isRunning(jobId)
                || (Build.VERSION.SDK_INT >= 31 && existing.isExpedited()))) return;
        // Replace an ordinary queued job on wake. Leaving it in place would retain its delays.
        JobInfo request = refreshJob(context, prompt, prompt && Build.VERSION.SDK_INT >= 31, manualWidgetId);
        int result = jobs.schedule(request);
        String outcome = prompt && Build.VERSION.SDK_INT >= 31 ? "expedited" : "regular";
        if (result == JobScheduler.RESULT_FAILURE && prompt && Build.VERSION.SDK_INT >= 31) {
            result = jobs.schedule(refreshJob(context, true, false, manualWidgetId));
            outcome = "regular_after_expedited_quota";
        }
        UpdateDiagnostics.scheduled(context, result == JobScheduler.RESULT_SUCCESS ? outcome : "rejected");
    }
    static JobInfo refreshJob(Context context, boolean prompt) {
        return refreshJob(context, prompt, prompt && Build.VERSION.SDK_INT >= 31, 0);
    }
    private static JobInfo refreshJob(Context context, boolean prompt, boolean expedited, int manualWidgetId) {
        PersistableBundle extras = new PersistableBundle();
        extras.putLong(REQUESTED_AT, System.currentTimeMillis());
        extras.putInt(MANUAL_WIDGET, manualWidgetId);
        int jobId = manualWidgetId > 0 ? manualJobId(manualWidgetId) : IMMEDIATE_JOB;
        JobInfo.Builder job = new JobInfo.Builder(jobId, new ComponentName(context, RefreshJobService.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setExtras(extras)
                .setBackoffCriteria(60_000, JobInfo.BACKOFF_POLICY_EXPONENTIAL);
        if (expedited && Build.VERSION.SDK_INT >= 31) job.setExpedited(true);
        else if (!prompt) job.setMinimumLatency(ThreadLocalRandom.current().nextLong(5_000, 35_001));
        return job.build();
    }
    static boolean forecastCheckDue(Context context) {
        SettingsStore store = new SettingsStore(context);
        long now = System.currentTimeMillis();
        for (int id : widgetIds(context)) {
            WidgetSettings settings = store.get(id);
            if (settings.automatic && automaticRefreshDue(context, settings, now)) return true;
        }
        return false;
    }
    static boolean automaticRefreshDue(Context context, WidgetSettings settings, long now) {
        // A stale followed position must be resolved before deciding which location's cache to use.
        return !settings.hasLocation() || settings.locationExpired(now) || weatherCheckDue(context, settings, now);
    }
    static boolean weatherCheckDue(Context context, WidgetSettings settings, long now) {
        if (!settings.hasLocation()) return true;
        String key = ForecastWindow.coordinateKey(settings.latitude, settings.longitude);
        if (new MetClient(context).retryDeferred(key, now)) return false;
        ForecastCache.Entry entry = new ForecastCache(context).read(key);
        if (entry == null) return true;
        try {
            return now >= RefreshPolicy.nextCheckAt(settings, entry.forecast(), entry.checkedAt, entry.expiresAt, now);
        } catch (org.json.JSONException ignored) { return true; }
    }
    public static boolean pending(Context context, int id) {
        if (settingsRefreshId == id || inFlight.contains(id)) return true;
        SettingsStore store = new SettingsStore(context);
        if (!deviceActive(context)) return false;
        JobScheduler jobs = context.getSystemService(JobScheduler.class);
        JobInfo manual = id > 0 ? jobs.getPendingJob(manualJobId(id)) : null;
        if (manual != null && manual.getExtras().getLong(REQUESTED_AT) > store.attemptedAt(id)) return true;
        if (!store.get(id).automatic) return false;
        JobInfo queued = jobs.getPendingJob(IMMEDIATE_JOB);
        // A completed failure must be shown even while its JobService is finishing.
        return queued != null && queued.getExtras().getLong(REQUESTED_AT) > store.attemptedAt(id);
    }
    static Future<?> refreshWidget(Context context, int id, Runnable completed) {
        Context app = context.getApplicationContext();
        return IO.submit(() -> {
            try {
                // A tap is manual even with automatic updates disabled. Location access
                // remains background access because no activity has been opened.
                if (isOurWidget(app, id) && deviceActive(app)) refreshOne(app, id, false, true);
            } finally {
                RainWidgetProvider.render(app, id);
                new Handler(Looper.getMainLooper()).post(completed);
            }
        });
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
    public static void refreshFromSettings(Context context, int id, boolean manual, Runnable completed) {
        if (!manual && !automaticRefreshDue(context, new SettingsStore(context).get(id), System.currentTimeMillis())) return;
        if (!refreshing.compareAndSet(false, true)) return;
        settingsRefreshId = id;
        Context app = context.getApplicationContext();
        IO.execute(() -> {
            try { refreshOne(app, id, true, manual); }
            finally {
                settingsRefreshId = -1;
                refreshing.set(false);
                RainWidgetProvider.render(app, id);
                new Handler(Looper.getMainLooper()).post(completed);
            }
        });
        RainWidgetProvider.render(app, id);
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

    private static void refreshOne(Context context, int id, boolean foreground, boolean manual) {
        SettingsStore store = new SettingsStore(context);
        WidgetSettings settings = store.get(id);
        long now = System.currentTimeMillis();
        // Recheck on the worker: another widget or foreground refresh may have filled this cache.
        if (!manual && !automaticRefreshDue(context, settings, now)) return;
        UpdateIssue stage = UpdateIssue.LOCATION_UNAVAILABLE;
        inFlight.add(id);
        RainWidgetProvider.render(context, id);
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
            // Location resolution may have changed the coordinate key. Each place has its own cadence.
            if (!manual && !weatherCheckDue(context, settings, System.currentTimeMillis())) return;
            stage = UpdateIssue.IO_ERROR;
            ForecastCache.Entry entry = new MetClient(context).fetch(settings.latitude, settings.longitude);
            Forecast result;
            try { result = entry.forecast(); }
            catch (org.json.JSONException e) { throw UpdateIssue.INVALID_RESPONSE.failure("The stored forecast is unreadable."); }
            if (!ForecastWindow.hasUpcomingData(result, System.currentTimeMillis()))
                throw UpdateIssue.FORECAST_UNAVAILABLE.failure("The weather service has no precipitation data for the next 90 minutes at this location.");
            if (settings.sameLocation(store.get(id))) store.status(id,
                    entry.deprecated ? "MET is retiring this API version. Check for a Rainline update." : "",
                    entry.deprecated ? UpdateIssue.API_DEPRECATED : UpdateIssue.NONE, System.currentTimeMillis());
        } catch (IOException e) {
            if (settings.sameLocation(store.get(id)) || !store.get(id).hasLocation())
                store.status(id, e.getMessage() == null ? "Couldn't update the forecast." : e.getMessage(), UpdateIssue.from(e, stage), System.currentTimeMillis());
        } finally {
            inFlight.remove(id);
            RainWidgetProvider.render(context, id);
        }
    }
}
