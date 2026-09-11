package app.rainline;

import android.app.job.JobParameters;
import android.app.job.JobService;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Future;

public final class RefreshJobService extends JobService {
    private final Map<Integer, Future<?>> running = new HashMap<>();
    @Override public boolean onStartJob(JobParameters params) {
        Future<?> future = Updates.refreshWidgets(this, () -> {
            if (running.remove(params.getJobId()) != null) jobFinished(params, false);
        });
        running.put(params.getJobId(), future);
        return true;
    }
    @Override public boolean onStopJob(JobParameters params) {
        Future<?> future = running.remove(params.getJobId());
        if (future != null) future.cancel(true);
        return true;
    }
}
