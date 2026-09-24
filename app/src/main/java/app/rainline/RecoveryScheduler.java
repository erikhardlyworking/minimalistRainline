package app.rainline;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.os.PersistableBundle;
import java.util.concurrent.ThreadLocalRandom;

/** One coalesced automatic follow-up, with bounds carried across process death in job extras. */
final class RecoveryScheduler {
    static final int JOB_ID = 1103;
    static final String ATTEMPT = "recoveryAttempt", DEADLINE = "recoveryDeadline";
    private RecoveryScheduler() { }
    static boolean expired(PersistableBundle extras, long now) {
        return extras.getInt(ATTEMPT, 0) > 0 && now >= extras.getLong(DEADLINE, 0);
    }
    static void schedule(Context context, JobScheduler jobs, PersistableBundle source, RefreshResult result) {
        long now = System.currentTimeMillis();
        int attempt = source.getInt(ATTEMPT, 0);
        long deadline = attempt > 0 ? source.getLong(DEADLINE, 0) : now + RecoveryPolicy.WINDOW;
        long at = RecoveryPolicy.nextAt(result, attempt, deadline, now, ThreadLocalRandom.current().nextLong(30_001));
        if (at == 0) {
            UpdateDiagnostics.recovery(context, result.needsRecovery() ? "limit_reached" : "not_needed", 0);
            return;
        }
        JobInfo pending = jobs.getPendingJob(JOB_ID);
        // Coalesce separate wake/periodic failures; don't reset an existing burst's deadline.
        boolean sameCompletedAttempt = pending != null && attempt > 0
                && pending.getExtras().getInt(ATTEMPT) == attempt
                && pending.getExtras().getLong(DEADLINE) == deadline;
        if (pending != null && !sameCompletedAttempt && !expired(pending.getExtras(), now)) {
            UpdateDiagnostics.recovery(context, "already_queued", pending.getExtras().getLong("recoveryAt", 0));
            return;
        }
        PersistableBundle extras = new PersistableBundle();
        extras.putInt(ATTEMPT, attempt + 1);
        extras.putLong(DEADLINE, deadline);
        extras.putLong("recoveryAt", at);
        JobInfo job = new JobInfo.Builder(JOB_ID, new ComponentName(context, RefreshJobService.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setMinimumLatency(at - now)
                .setBackoffCriteria(60_000, JobInfo.BACKOFF_POLICY_EXPONENTIAL).setExtras(extras).build();
        int scheduled = jobs.schedule(job);
        UpdateDiagnostics.recovery(context, scheduled == JobScheduler.RESULT_SUCCESS ? "queued" : "rejected",
                scheduled == JobScheduler.RESULT_SUCCESS ? at : 0);
    }
}
