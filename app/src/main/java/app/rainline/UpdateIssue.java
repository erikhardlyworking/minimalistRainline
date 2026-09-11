package app.rainline;

import java.io.IOException;

/** Fixed categories are safe to include in diagnostics; exception messages are not. */
public enum UpdateIssue {
    NONE, UNKNOWN_ERROR, LOCATION_UNAVAILABLE, LOCATION_MISSING, LOCATION_STALE,
    RETRY_DELAY, HTTP_FORBIDDEN, HTTP_THROTTLED, OUTSIDE_COVERAGE, HTTP_ERROR,
    INVALID_RESPONSE, REDIRECT_ERROR, IO_ERROR, TIMEOUT, DNS_ERROR, TLS_ERROR, API_DEPRECATED;

    public Failure failure(String message) { return new Failure(this, message); }
    public static UpdateIssue from(IOException error, UpdateIssue fallback) {
        if (error instanceof Failure) return ((Failure) error).issue;
        if (error instanceof java.net.SocketTimeoutException) return TIMEOUT;
        if (error instanceof java.net.UnknownHostException) return DNS_ERROR;
        if (error instanceof javax.net.ssl.SSLException) return TLS_ERROR;
        return fallback;
    }
    public static final class Failure extends IOException {
        public final UpdateIssue issue;
        private Failure(UpdateIssue issue, String message) { super(message); this.issue = issue; }
    }
}
