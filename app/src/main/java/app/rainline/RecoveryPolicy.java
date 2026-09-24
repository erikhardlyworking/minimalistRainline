package app.rainline;

/** Short recovery bursts only; normal periodic/wake/tap requests remain independent. */
final class RecoveryPolicy {
    static final long WINDOW = 10 * 60_000L;
    static final int MAX_RETRIES = 2;
    private RecoveryPolicy() { }
    static long nextAt(RefreshResult result, int attempts, long deadline, long now, long jitter) {
        if (!result.needsRecovery() || attempts >= MAX_RETRIES || now >= deadline) return 0;
        // One catch-up after a job arrives during sleep/unlock, never a sleeping retry loop.
        if (result.kind == RefreshResult.Kind.ASLEEP_OR_LOCKED && attempts > 0) return 0;
        long delay = 60_000L * (attempts + 1);
        long next = Math.max(now + delay, result.retryAt) + Math.max(0, Math.min(30_000, jitter));
        return next < deadline ? next : 0;
    }
}
