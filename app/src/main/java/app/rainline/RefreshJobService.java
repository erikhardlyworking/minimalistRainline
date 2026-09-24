package app.rainline;

import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.os.Build;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class RefreshJobService extends JobService {
    private static final class Run {
        final JobNetwork network;
        Future<?> future;
        Run(JobParameters params) {
            network = new JobNetwork(Build.VERSION.SDK_INT >= 28, Build.VERSION.SDK_INT >= 28 ? params.getNetwork() : null);
        }
    }
    private final Map<Integer, Run> running = new HashMap<>();
    private static final Set<Integer> activeJobs = ConcurrentHashMap.newKeySet();
    static boolean isRunning(int id) { return activeJobs.contains(id); }
    @Override public boolean onStartJob(JobParameters params) {
        UpdateDiagnostics.event(this, "started");
        if (RecoveryScheduler.expired(params.getExtras(), System.currentTimeMillis())) {
            UpdateDiagnostics.completed(this, new RefreshResult(RefreshResult.Kind.RECOVERY_EXPIRED));
            return false;
        }
        Run run = new Run(params);
        running.put(params.getJobId(), run);
        activeJobs.add(params.getJobId());
        int manualWidget = params.getExtras().getInt(Updates.MANUAL_WIDGET, 0);
        run.future = Updates.refreshJob(this, manualWidget, new MetClient(this, run.network), result -> {
            // A stopped job can finish after its replacement has already started.
            if (running.get(params.getJobId()) != run) return;
            running.remove(params.getJobId());
            UpdateDiagnostics.completed(this, result);
            // Keep periodic timing intact. Bounded automatic retries use a separate job.
            jobFinished(params, false);
            activeJobs.remove(params.getJobId());
            // jobFinished posts to JobService's main-thread handler. Queue replacement
            // after that message, so finishing the old recovery cannot remove the new one.
            new android.os.Handler(getMainLooper()).post(() -> {
                if (manualWidget == 0 && Updates.hasAutomaticWidgets(this))
                    RecoveryScheduler.schedule(this, getSystemService(JobScheduler.class), params.getExtras(), result);
            });
        });
        return true;
    }
    @Override public void onNetworkChanged(JobParameters params) {
        Run run = running.get(params.getJobId());
        if (run != null && Build.VERSION.SDK_INT >= 28) {
            run.network.changed(params.getNetwork());
            UpdateDiagnostics.event(this, "networkChanged");
        }
    }
    @Override public boolean onStopJob(JobParameters params) {
        Run run = running.get(params.getJobId());
        if (run == null) return false;
        running.remove(params.getJobId());
        activeJobs.remove(params.getJobId());
        UpdateDiagnostics.stopped(this, Build.VERSION.SDK_INT >= 31 ? params.getStopReason() : 0);
        if (run.future != null) run.future.cancel(true);
        // Android preserves a lost network constraint; recovery jobs still expire on start.
        return !RecoveryScheduler.expired(params.getExtras(), System.currentTimeMillis());
    }
    @Override public void onDestroy() {
        for (Map.Entry<Integer, Run> entry : running.entrySet()) {
            activeJobs.remove(entry.getKey());
            if (entry.getValue().future != null) entry.getValue().future.cancel(true);
        }
        running.clear();
        super.onDestroy();
    }
}
