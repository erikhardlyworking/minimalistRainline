package app.rainline;

/** A fixed, local outcome; carries no location, URL or exception text. */
final class RefreshResult {
    enum Kind {
        CACHE_NOT_DUE, SUCCESS, PERMANENT_FAILURE, NO_WIDGETS, ASLEEP_OR_LOCKED,
        HTTP_BACKOFF, RETRYABLE_FAILURE, CANCELLED, RECOVERY_EXPIRED
    }
    final Kind kind;
    final long retryAt;
    RefreshResult(Kind kind) { this(kind, 0); }
    RefreshResult(Kind kind, long retryAt) { this.kind = kind; this.retryAt = retryAt; }
    boolean needsRecovery() {
        return kind == Kind.RETRYABLE_FAILURE || kind == Kind.HTTP_BACKOFF || kind == Kind.ASLEEP_OR_LOCKED;
    }
    RefreshResult merge(RefreshResult other) {
        // A failure in one widget must survive success/cache reuse in another.
        if (other.needsRecovery() && !needsRecovery()) return other;
        if (needsRecovery() && !other.needsRecovery()) return this;
        Kind combined = kind.ordinal() >= other.kind.ordinal() ? kind : other.kind;
        return new RefreshResult(combined, Math.max(retryAt, other.retryAt));
    }
    static boolean transientIssue(UpdateIssue issue) {
        return issue == UpdateIssue.IO_ERROR || issue == UpdateIssue.DNS_ERROR || issue == UpdateIssue.TIMEOUT
                || issue == UpdateIssue.HTTP_ERROR || issue == UpdateIssue.HTTP_THROTTLED || issue == UpdateIssue.RETRY_DELAY;
    }
}
