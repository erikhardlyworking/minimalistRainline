package app.rainline;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.KeyguardManager;
import android.app.job.JobScheduler;
import android.app.job.JobInfo;
import android.app.ActivityManager;
import android.app.usage.UsageStatsManager;
import android.appwidget.AppWidgetManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.Locale;

/** A local report built from explicit fields, never a preferences dump or raw log. */
public final class Diagnostics {
    private Diagnostics() {}

    public static AlertDialog show(Activity activity, int widgetId) {
        return show(activity, widgetId, "");
    }
    public static AlertDialog show(Activity activity, int widgetId, String beforeRefresh) {
        String report = summary(activity, widgetId) + (beforeRefresh.isEmpty() ? ""
                : "\nWidget before this settings refresh (ages at that time)\n" + beforeRefresh + "\n");
        TextView text = new TextView(activity);
        text.setText(report);
        text.setTextColor(Color.WHITE);
        text.setTextSize(12);
        text.setTypeface(Typeface.MONOSPACE);
        text.setTextIsSelectable(true);
        int padding = Math.round(20 * activity.getResources().getDisplayMetrics().density);
        text.setPadding(padding, padding / 2, padding, padding);
        ScrollView scroll = new ScrollView(activity);
        scroll.addView(text);
        return new AlertDialog.Builder(activity).setTitle("Local diagnostic summary")
                .setMessage("Generated on this device. No coordinates, place names, request URLs, device IDs or raw logs are included. Copy it only if you want to share it.")
                .setView(scroll).setPositiveButton("Copy", (dialog, which) -> {
                    ClipboardManager clipboard = activity.getSystemService(ClipboardManager.class);
                    if (clipboard != null) {
                        clipboard.setPrimaryClip(ClipData.newPlainText("Rainline diagnostics", report));
                        if (Build.VERSION.SDK_INT < 33) Toast.makeText(activity, "Diagnostics copied", Toast.LENGTH_SHORT).show();
                    }
                }).setNegativeButton("Close", null).show();
    }

    public static String summary(Context context, int widgetId) {
        SettingsStore store = new SettingsStore(context);
        WidgetSettings settings = store.get(widgetId);
        ForecastCache.Entry entry = settings.hasLocation() ? new ForecastCache(context).read(
                ForecastWindow.coordinateKey(settings.latitude, settings.longitude)) : null;
        return summary(context, widgetId, settings, entry, store.issue(widgetId), store.attemptedAt(widgetId));
    }

    static String summary(Context context, int widgetId, WidgetSettings settings, ForecastCache.Entry entry,
                          UpdateIssue issue, long attemptedAt) {
        long now = System.currentTimeMillis();
        StringBuilder out = new StringBuilder("Rainline diagnostics\n");
        line(out, "App version", BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")");
        line(out, "Build", BuildConfig.DEBUG ? "debug" : "release");
        line(out, "User-Agent", MetHttp.userAgent());
        line(out, "Android API", Build.VERSION.SDK_INT);
        line(out, "Device model", Build.MANUFACTURER + " " + Build.MODEL);
        line(out, "Placed widgets", Updates.widgetIds(context).length);
        line(out, "Selection", widgetId > 0 ? "placed widget" : "defaults");
        if (widgetId > 0) {
            out.append(UpdateDiagnostics.widgetSnapshot(context, widgetId)).append('\n');
            Bundle options = AppWidgetManager.getInstance(context).getAppWidgetOptions(widgetId);
            line(out, "Widget size bounds (dp)", options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH) + "–"
                    + options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH) + " wide, "
                    + options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT) + "–"
                    + options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT) + " high");
        }
        line(out, "Foreground location", LocationAccess.foregroundAllowed(context));
        line(out, "Precise location", context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED);
        line(out, "Background location", LocationAccess.backgroundAllowed(context));
        PowerManager power = context.getSystemService(PowerManager.class);
        KeyguardManager keyguard = context.getSystemService(KeyguardManager.class);
        line(out, "Screen interactive", power != null && power.isInteractive());
        line(out, "Device locked", keyguard != null && keyguard.isKeyguardLocked());
        line(out, "Battery saver", power != null && power.isPowerSaveMode());
        line(out, "Battery optimisation exempt", power != null && power.isIgnoringBatteryOptimizations(context.getPackageName()));
        ConnectivityManager network = context.getSystemService(ConnectivityManager.class);
        NetworkCapabilities capabilities = network == null ? null : network.getNetworkCapabilities(network.getActiveNetwork());
        line(out, "Internet validated", capabilities != null && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED));
        JobScheduler jobs = context.getSystemService(JobScheduler.class);
        line(out, "Pending update jobs", jobs == null ? 0 : jobs.getAllPendingJobs().size());
        if (Build.VERSION.SDK_INT >= 28) {
            line(out, "Android background restricted", context.getSystemService(ActivityManager.class).isBackgroundRestricted());
            line(out, "App standby bucket", context.getSystemService(UsageStatsManager.class).getAppStandbyBucket());
        }
        if (jobs != null) for (JobInfo job : jobs.getAllPendingJobs()) {
            String label = job.isPeriodic() ? "Periodic job" : "Refresh job";
            if (Build.VERSION.SDK_INT >= 31) line(out, label + " expedited", job.isExpedited());
            line(out, label + " waiting reason", UpdateDiagnostics.pendingReason(jobs, job));
        }
        line(out, "Last screen-on received", age(UpdateDiagnostics.at(context, "screenOn"), now));
        line(out, "Last unlock received", age(UpdateDiagnostics.at(context, "unlock"), now));
        line(out, "Last alarm/system event", age(UpdateDiagnostics.at(context, "alarmOrSystemEvent"), now));
        line(out, "Last job scheduled", age(UpdateDiagnostics.at(context, "scheduled"), now));
        line(out, "Scheduling result", UpdateDiagnostics.outcome(context));
        line(out, "Last job started", age(UpdateDiagnostics.at(context, "started"), now));
        line(out, "Last job completed", age(UpdateDiagnostics.at(context, "completed"), now));
        line(out, "Last job stopped", age(UpdateDiagnostics.at(context, "stopped"), now));
        line(out, "Last stop reason", UpdateDiagnostics.lastStop(context));
        line(out, "Location mode", settings.follow ? "follow" : "fixed");
        line(out, "Location configured", settings.hasLocation());
        line(out, "Location stale", settings.follow && settings.locationExpired(now));
        if (settings.follow) line(out, "Location age", age(settings.locationAt, now));
        line(out, "Automatic updates", settings.automatic);
        line(out, "Refresh pending", Updates.pending(context, widgetId));
        line(out, "Last update attempt", age(attemptedAt, now));
        line(out, "Last update category", issue.name().toLowerCase(Locale.ROOT));
        line(out, "Cached forecast", entry != null);
        if (entry != null) {
            line(out, "Last server check", age(entry.checkedAt, now));
            line(out, "Cache still valid", now < entry.expiresAt);
            line(out, "API deprecated", entry.deprecated);
            try {
                Forecast forecast = entry.forecast();
                line(out, "Forecast age", age(forecast.updatedAt, now));
                line(out, "Forecast older than 20 min", now - forecast.updatedAt > Forecast.STALE_AFTER);
                line(out, "Radar data available", forecast.hasRadar());
                Forecast display = entry.displayForecast(now);
                line(out, "Displayed forecast age", age(display.updatedAt, now));
                line(out, "Data within next 90 min", ForecastWindow.hasUpcomingData(display, now));
                line(out, "Usable forecast intervals", ForecastWindow.segments(display, now).size());
                line(out, "Widget state", ForecastState.of(settings, display, now, issue, Updates.pending(context, widgetId)));
            } catch (org.json.JSONException e) { line(out, "Forecast state", "unreadable"); }
        }
        line(out, "Axis / ticks / numbers", settings.showAxis + " / " + settings.showTicks + " / " + settings.labels);
        line(out, "Number size", settings.labelSize);
        line(out, "Guides", settings.guides == 2 ? "hidden" : settings.guides == 1 ? "white" : "grey");
        line(out, "Padding L/T/R/B", settings.paddingLeft + "/" + settings.paddingTop + "/" + settings.paddingRight + "/" + settings.paddingBottom);
        line(out, "Rain ceiling (mm/h)", settings.scaleMax);
        line(out, "Rain highlight enabled", settings.highlightRain);
        return out.toString();
    }

    private static String age(long timestamp, long now) {
        if (timestamp <= 0) return "unknown";
        if (timestamp > now) return "clock mismatch";
        return (now - timestamp) / Forecast.MINUTE + " min ago";
    }
    private static void line(StringBuilder out, String name, Object value) {
        out.append(name).append(": ").append(String.valueOf(value).replace('\n', ' ').replace('\r', ' ')).append('\n');
    }
}
