package app.rainline;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Only the latest fixed event categories and timestamps, never a location or event history. */
public final class UpdateDiagnostics {
    private UpdateDiagnostics() {}
    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences("update-diagnostics", Context.MODE_PRIVATE);
    }
    static void event(Context context, String key) {
        prefs(context).edit().putLong(key, System.currentTimeMillis()).apply();
    }
    static void scheduled(Context context, String outcome) {
        prefs(context).edit().putLong("scheduled", System.currentTimeMillis()).putString("scheduleOutcome", outcome).apply();
    }
    static void stopped(Context context, int reason) {
        prefs(context).edit().putLong("stopped", System.currentTimeMillis()).putInt("stopReason", reason).apply();
    }
    static long at(Context context, String key) { return prefs(context).getLong(key, 0); }
    static String outcome(Context context) { return prefs(context).getString("scheduleOutcome", "none"); }
    static String lastStop(Context context) {
        if (Build.VERSION.SDK_INT < 31) return "not available on this Android version";
        return name(JobParameters.class, "STOP_REASON_", prefs(context).getInt("stopReason", 0));
    }
    @SuppressWarnings("deprecation")
    static String pendingReason(JobScheduler jobs, JobInfo job) {
        if (Build.VERSION.SDK_INT < 34) return "not available on this Android version";
        int[] reasons = Build.VERSION.SDK_INT >= 36 ? jobs.getPendingJobReasons(job.getId())
                : new int[]{jobs.getPendingJobReason(job.getId())};
        List<String> labels = new ArrayList<>();
        for (int reason : reasons) labels.add(name(JobScheduler.class, "PENDING_JOB_REASON_", reason));
        return String.join(", ", labels);
    }
    private static String name(Class<?> type, String prefix, int value) {
        for (Field field : type.getFields()) {
            if (field.getType() != int.class || !field.getName().startsWith(prefix)) continue;
            try {
                if (field.getInt(null) == value) return field.getName().substring(prefix.length()).toLowerCase(Locale.ROOT);
            } catch (IllegalAccessException ignored) { }
        }
        return "unknown (" + value + ")";
    }
}
