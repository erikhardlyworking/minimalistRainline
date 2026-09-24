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

            jobs.current = null; jobs.attempts.clear(); jobs.rejectAll = false; jobs.rejectExpedited = true;
            Updates.scheduleRefresh(isolated, jobs, true, 42);
            check(jobs.attempts.size() == 2, "A widget tap needs the same quota fallback");
            check(jobs.current.getId() == Updates.manualJobId(42)
                    && jobs.current.getExtras().getInt(Updates.MANUAL_WIDGET) == 42,
                    "Quota fallback lost the tapped widget or manual refresh intent");
            jobs.rejectExpedited = false; jobs.attempts.clear();
            Updates.scheduleRefresh(isolated, jobs, true, 42);
            Updates.scheduleRefresh(isolated, jobs, true, 42);
            check(jobs.attempts.size() == 1 && jobs.current.isExpedited(), "Repeated taps must coalesce");
            Updates.scheduleRefresh(isolated, jobs, true, 43);
            check(jobs.attempts.size() == 2 && jobs.current.getExtras().getInt(Updates.MANUAL_WIDGET) == 43,
                    "Tapping another widget must not reuse the first widget's request");
            jobs.current = null; jobs.attempts.clear();
            android.os.PersistableBundle initial = new android.os.PersistableBundle();
            RefreshResult failed = new RefreshResult(RefreshResult.Kind.RETRYABLE_FAILURE);
            RecoveryScheduler.schedule(isolated, jobs, initial, failed);
            JobInfo first = jobs.current;
            check(first.getId() == RecoveryScheduler.JOB_ID && !first.isExpedited(), "Recovery must use an ordinary separate job");
            check(first.getNetworkType() == JobInfo.NETWORK_TYPE_ANY, "Recovery lost network constraint");
            check(first.getMinLatencyMillis() >= 60_000 && first.getMinLatencyMillis() <= 90_000, "First retry delay not bounded");
            check(first.getExtras().getInt(RecoveryScheduler.ATTEMPT) == 1, "Missing persistent attempt count");
            RecoveryScheduler.schedule(isolated, jobs, initial, failed);
            check(jobs.attempts.size() == 1, "Repeated events replaced a queued recovery");
            RecoveryScheduler.schedule(isolated, jobs, first.getExtras(), failed);
            check(jobs.current.getExtras().getInt(RecoveryScheduler.ATTEMPT) == 2, "Second retry lost its attempt count");
            check(jobs.current.getExtras().getLong(RecoveryScheduler.DEADLINE) == first.getExtras().getLong(RecoveryScheduler.DEADLINE),
                    "Retry restarted its lifetime");
            JobInfo second = jobs.current;
            RecoveryScheduler.schedule(isolated, jobs, second.getExtras(), failed);
            check(jobs.attempts.size() == 2, "Retries continued beyond their limit");
            check(RecoveryScheduler.expired(second.getExtras(), second.getExtras().getLong(RecoveryScheduler.DEADLINE)),
                    "Deferred recovery did not expire");
            check(!RecoveryScheduler.expired(initial, Long.MAX_VALUE), "Normal jobs must not expire as recoveries");
            jobs.current = null; jobs.attempts.clear();
            RefreshResult asleep = new RefreshResult(RefreshResult.Kind.ASLEEP_OR_LOCKED);
            RecoveryScheduler.schedule(isolated, jobs, initial, asleep);
            first = jobs.current;
            RecoveryScheduler.schedule(isolated, jobs, first.getExtras(), asleep);
            check(jobs.attempts.size() == 1, "Sleeping catch-up kept polling");
            jobs.current = null; jobs.rejectAll = true;
            RecoveryScheduler.schedule(isolated, jobs, initial, failed);
            check(UpdateDiagnostics.recoveryOutcome(isolated).equals("rejected"), "Recovery rejection not recorded");
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
        @Override public JobInfo getPendingJob(int id) { return current != null && current.getId() == id ? current : null; }
        @Override public List<JobInfo> getAllPendingJobs() { return current == null ? List.of() : List.of(current); }
        @Override public void cancel(int id) { current = null; }
        @Override public void cancelAll() { current = null; }
        @Override public int enqueue(JobInfo job, JobWorkItem item) { return schedule(job); }
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
