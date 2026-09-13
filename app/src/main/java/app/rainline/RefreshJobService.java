package app.rainline;

import android.app.job.JobParameters;
import android.app.job.JobService;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import android.os.Build;

public final class RefreshJobService extends JobService {
    private static final class Run { Future<?> future; }
    private final Map<Integer, Run> running = new HashMap<>();
    private static final Set<Integer> activeJobs = ConcurrentHashMap.newKeySet();
    static boolean isRunning(int id) { return activeJobs.contains(id); }
    @Override public boolean onStartJob(JobParameters params) {
        Run run = new Run();
        running.put(params.getJobId(), run);
        activeJobs.add(params.getJobId());
        UpdateDiagnostics.event(this, "started");
        run.future = Updates.refreshWidgets(this, () -> {
            // A cancelled job may finish after its replacement has already started.
            if (running.get(params.getJobId()) != run) return;
            running.remove(params.getJobId());
            activeJobs.remove(params.getJobId());
            UpdateDiagnostics.event(this, "completed");
            jobFinished(params, false);
        });
        return true;
    }
    @Override public boolean onStopJob(JobParameters params) {
        activeJobs.remove(params.getJobId());
        UpdateDiagnostics.stopped(this, Build.VERSION.SDK_INT >= 31 ? params.getStopReason() : 0);
        Run run = running.remove(params.getJobId());
        if (run != null) run.future.cancel(true);
        return true;
    }
    @Override public void onDestroy() {
        for (Map.Entry<Integer, Run> entry : running.entrySet()) {
            activeJobs.remove(entry.getKey());
            entry.getValue().future.cancel(true);
        }
        running.clear();
        super.onDestroy();
    }
}
