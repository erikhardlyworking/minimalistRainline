package app.rainline;

import org.junit.Test;
import static org.junit.Assert.*;

public final class ForecastTimesTest {
    private final long now = 1_800_000_000_000L;
    @Test public void agesRemainExplicitAcrossMinutesHoursAndDays() {
        assertEquals("just now", ForecastTimes.ago(now - 59_999, now));
        assertEquals("1 min ago", ForecastTimes.ago(now - Forecast.MINUTE, now));
        assertEquals("59 min ago", ForecastTimes.ago(now - 59 * Forecast.MINUTE, now));
        assertEquals("1 h 0 min ago", ForecastTimes.ago(now - 60 * Forecast.MINUTE, now));
        assertEquals("2 h 5 min ago", ForecastTimes.ago(now - 125 * Forecast.MINUTE, now));
        assertEquals("1 d 1 h ago", ForecastTimes.ago(now - 25 * 60 * Forecast.MINUTE, now));
    }
    @Test public void absentOrFutureTimestampIsNotReportedAsFreshData() {
        assertEquals("never", ForecastTimes.describe(0, now));
        assertEquals("clock ahead", ForecastTimes.ago(now + 1, now));
        assertTrue(ForecastTimes.describe(now - 5 * Forecast.MINUTE, now).endsWith("(5 min ago)"));
    }
}
