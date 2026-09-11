package app.rainline;

import org.junit.Test;
import java.time.Instant;
import static org.junit.Assert.*;

public class HttpPolicyTest {
    private final long now = Instant.parse("2026-09-11T10:00:00Z").toEpochMilli();
    @Test public void acceptsRetryAfterSeconds() {
        assertEquals(now + 120_000, MetClient.retryAt("120", now));
    }
    @Test public void honoursServerRetryDelaysLongerThanADay() {
        assertEquals(now + 2 * 24 * 60 * Forecast.MINUTE, MetClient.retryAt("172800", now));
    }
    @Test public void acceptsHttpDate() {
        assertEquals(now + 10 * Forecast.MINUTE, MetClient.retryAt("Fri, 11 Sep 2026 10:10:00 GMT", now));
    }
    @Test public void malformedHeaderBacksOffInsteadOfLooping() {
        assertEquals(now + 5 * Forecast.MINUTE, MetClient.retryAt("broken", now));
        assertEquals(now + 5 * Forecast.MINUTE, MetClient.retryAt(null, now));
    }
    @Test public void pastOrNegativeRetryStillWaits() {
        assertEquals(now + Forecast.MINUTE, MetClient.retryAt("-10", now));
        assertEquals(now + Forecast.MINUTE, MetClient.retryAt("Fri, 11 Sep 2026 09:00:00 GMT", now));
    }
}
