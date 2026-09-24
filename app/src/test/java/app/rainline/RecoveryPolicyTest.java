package app.rainline;

import org.junit.Test;
import static org.junit.Assert.*;

public class RecoveryPolicyTest {
    private static final long NOW = 1_000_000;
    private final RefreshResult failed = new RefreshResult(RefreshResult.Kind.RETRYABLE_FAILURE);
    @Test public void delaysIncreaseAndIncludeBoundedJitter() {
        assertEquals(NOW + 60_000, next(failed, 0, -10));
        assertEquals(NOW + 90_000, next(failed, 0, 90_000));
        assertEquals(NOW + 125_000, next(failed, 1, 5_000));
    }
    @Test public void onlyTwoRetriesPerBurst() {
        assertEquals(0, next(failed, 2, 0));
        assertEquals(0, next(failed, 10, 0));
    }
    @Test public void sleepGetsOneCatchUpButNeverLoops() {
        RefreshResult asleep = new RefreshResult(RefreshResult.Kind.ASLEEP_OR_LOCKED);
        assertEquals(NOW + 60_000, next(asleep, 0, 0));
        assertEquals(0, next(asleep, 1, 0));
    }
    @Test public void serverRetryAfterIsNeverShortened() {
        assertEquals(NOW + 300_000, next(new RefreshResult(RefreshResult.Kind.HTTP_BACKOFF, NOW + 300_000), 0, 0));
        assertEquals(NOW + 305_000, next(new RefreshResult(RefreshResult.Kind.HTTP_BACKOFF, NOW + 300_000), 0, 5_000));
        assertEquals(0, next(new RefreshResult(RefreshResult.Kind.HTTP_BACKOFF, NOW + 3_600_000), 0, 0));
    }
    @Test public void expiredAndTooLateFollowUpsStop() {
        assertEquals(0, RecoveryPolicy.nextAt(failed, 1, NOW, NOW, 0));
        assertEquals(0, RecoveryPolicy.nextAt(failed, 1, NOW + 120_000, NOW, 0));
    }
    @Test public void successfulPermanentAndCancelledJobsDoNotRetry() {
        for (RefreshResult.Kind kind : new RefreshResult.Kind[]{RefreshResult.Kind.SUCCESS,
                RefreshResult.Kind.CACHE_NOT_DUE, RefreshResult.Kind.NO_WIDGETS,
                RefreshResult.Kind.PERMANENT_FAILURE, RefreshResult.Kind.CANCELLED, RefreshResult.Kind.RECOVERY_EXPIRED})
            assertEquals(kind.name(), 0, next(new RefreshResult(kind), 0, 0));
    }
    @Test public void otherWidgetsCannotHideFailureOrShortenBackoff() {
        RefreshResult success = new RefreshResult(RefreshResult.Kind.SUCCESS);
        assertTrue(success.merge(failed).needsRecovery());
        assertTrue(failed.merge(success).needsRecovery());
        assertEquals(NOW + 300_000, failed.merge(new RefreshResult(RefreshResult.Kind.HTTP_BACKOFF, NOW + 300_000)).retryAt);
    }
    @Test public void classifyOnlyPlausibleTransientFailures() {
        for (UpdateIssue issue : new UpdateIssue[]{UpdateIssue.IO_ERROR, UpdateIssue.DNS_ERROR, UpdateIssue.TIMEOUT,
                UpdateIssue.HTTP_ERROR, UpdateIssue.HTTP_THROTTLED, UpdateIssue.RETRY_DELAY})
            assertTrue(issue.name(), RefreshResult.transientIssue(issue));
        for (UpdateIssue issue : new UpdateIssue[]{UpdateIssue.LOCATION_MISSING, UpdateIssue.LOCATION_STALE,
                UpdateIssue.LOCATION_UNAVAILABLE, UpdateIssue.OUTSIDE_COVERAGE, UpdateIssue.HTTP_FORBIDDEN,
                UpdateIssue.INVALID_RESPONSE, UpdateIssue.REDIRECT_ERROR, UpdateIssue.TLS_ERROR, UpdateIssue.FORECAST_UNAVAILABLE})
            assertFalse(issue.name(), RefreshResult.transientIssue(issue));
    }
    private long next(RefreshResult result, int attempt, long jitter) {
        return RecoveryPolicy.nextAt(result, attempt, NOW + RecoveryPolicy.WINDOW, NOW, jitter);
    }
}
