package app.rainline;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.app.job.JobWorkItem;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.os.Build;
import java.util.ArrayList;
import java.util.List;

/** Simulates quota rejection without spending the user's real expedited-job allowance. */
final class SchedulingChecks {
    static void run(Context context) {
        if (Build.VERSION.SDK_INT < 31) return;
        Context isolated = new ContextWrapper(context) {
            @Override public SharedPreferences getSharedPreferences(String name, int mode) {
                return super.getSharedPreferences("scheduling-checks-" + name, mode);
            }
        };
        try {
            FakeScheduler jobs = new FakeScheduler();
            jobs.rejectExpedited = true;
            Updates.scheduleRefresh(isolated, jobs, true);
            check(jobs.attempts.size() == 2, "Quota rejection must schedule a regular fallback");
            check(jobs.attempts.get(0).isExpedited(), "First wake request must be expedited");
            check(!jobs.current.isExpedited() && jobs.current.getMinLatencyMillis() == 0,
                    "Quota fallback must be regular without artificial delay");
            check(UpdateDiagnostics.outcome(isolated).equals("regular_after_expedited_quota"), "Quota fallback must be diagnosable");

            jobs.rejectExpedited = false;
            jobs.attempts.clear();
            jobs.current = Updates.refreshJob(isolated, false);
            Updates.scheduleRefresh(isolated, jobs, true);
            check(jobs.attempts.size() == 1 && jobs.current.isExpedited(), "Wake must promote a waiting ordinary job");
            Updates.scheduleRefresh(isolated, jobs, true);
            check(jobs.attempts.size() == 1, "Repeated wakes must not replace an already expedited job");
            jobs.current = null; jobs.attempts.clear(); jobs.rejectAll = true;
            Updates.scheduleRefresh(isolated, jobs, true);
            check(UpdateDiagnostics.outcome(isolated).equals("rejected"), "Scheduling failure must be visible in diagnostics");
        } finally { context.deleteSharedPreferences("scheduling-checks-update-diagnostics"); }
    }
    private static final class FakeScheduler extends JobScheduler {
        JobInfo current;
        boolean rejectExpedited, rejectAll;
        final List<JobInfo> attempts = new ArrayList<>();
        @Override public int schedule(JobInfo job) {
            attempts.add(job);
            if (rejectAll || (rejectExpedited && job.isExpedited())) return RESULT_FAILURE;
            current = job; return RESULT_SUCCESS;
        }
        @Override public JobInfo getPendingJob(int id) { return current; }
        @Override public List<JobInfo> getAllPendingJobs() { return current == null ? List.of() : List.of(current); }
        @Override public void cancel(int id) { current = null; }
        @Override public void cancelAll() { current = null; }
        @Override public int enqueue(JobInfo job, JobWorkItem item) { return schedule(job); }
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
