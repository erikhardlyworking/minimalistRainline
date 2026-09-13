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
        if (prompt && !forecastCheckDue(context)) return;
        JobScheduler jobs = context.getSystemService(JobScheduler.class);
        scheduleRefresh(context, jobs, prompt);
    }
    static void scheduleRefresh(Context context, JobScheduler jobs, boolean prompt) {
        JobInfo existing = jobs.getPendingJob(IMMEDIATE_JOB);
        if (existing != null && (!prompt || RefreshJobService.isRunning(IMMEDIATE_JOB)
                || (Build.VERSION.SDK_INT >= 31 && existing.isExpedited()))) return;
        // Replace an ordinary queued job on wake. Leaving it in place would retain its delays.
        JobInfo request = refreshJob(context, prompt);
        int result = jobs.schedule(request);
        String outcome = prompt && Build.VERSION.SDK_INT >= 31 ? "expedited" : "regular";
        if (result == JobScheduler.RESULT_FAILURE && prompt && Build.VERSION.SDK_INT >= 31) {
            result = jobs.schedule(refreshJob(context, true, false));
            outcome = "regular_after_expedited_quota";
        }
        UpdateDiagnostics.scheduled(context, result == JobScheduler.RESULT_SUCCESS ? outcome : "rejected");
    }
    static JobInfo refreshJob(Context context, boolean prompt) {
        return refreshJob(context, prompt, prompt && Build.VERSION.SDK_INT >= 31);
    }
    private static JobInfo refreshJob(Context context, boolean prompt, boolean expedited) {
        PersistableBundle extras = new PersistableBundle();
        extras.putLong(REQUESTED_AT, System.currentTimeMillis());
        JobInfo.Builder job = new JobInfo.Builder(IMMEDIATE_JOB, new ComponentName(context, RefreshJobService.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setExtras(extras)
                .setBackoffCriteria(60_000, JobInfo.BACKOFF_POLICY_EXPONENTIAL);
        if (expedited && Build.VERSION.SDK_INT >= 31) job.setExpedited(true);
        else if (!prompt) job.setMinimumLatency(ThreadLocalRandom.current().nextLong(5_000, 35_001));
        return job.build();
    }
    static boolean forecastCheckDue(Context context) {
        SettingsStore store = new SettingsStore(context);
        ForecastCache cache = new ForecastCache(context);
        MetClient client = new MetClient(context);
        long now = System.currentTimeMillis();
        for (int id : widgetIds(context)) {
            WidgetSettings settings = store.get(id);
            if (!settings.automatic) continue;
            if (!settings.hasLocation() || settings.locationExpired(now)) return true;
            String key = ForecastWindow.coordinateKey(settings.latitude, settings.longitude);
            ForecastCache.Entry entry = cache.read(key);
            if ((entry == null || now >= entry.expiresAt) && !client.retryDeferred(key, now)) return true;
        }
        return false;
    }
    public static boolean pending(Context context, int id) {
        if (settingsRefreshId == id || inFlight.contains(id)) return true;
        SettingsStore store = new SettingsStore(context);
        if (!store.get(id).automatic || !deviceActive(context)) return false;
        JobInfo queued = context.getSystemService(JobScheduler.class).getPendingJob(IMMEDIATE_JOB);
        // A completed failure must be shown even while its JobService is finishing.
        return queued != null && queued.getExtras().getLong(REQUESTED_AT) > store.attemptedAt(id);
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
        settingsRefreshId = id;
        Context app = context.getApplicationContext();
        IO.execute(() -> {
            try { refreshOne(app, id, true, locate); }
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

    private static void refreshOne(Context context, int id, boolean foreground, boolean locate) {
        SettingsStore store = new SettingsStore(context);
        WidgetSettings settings = store.get(id);
        long now = System.currentTimeMillis();
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
