package app.rainline;

import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public final class ForecastStateTest {
    private final long now = 1_800_000_000_000L;
    private WidgetSettings location() {
        WidgetSettings settings = new WidgetSettings();
        settings.follow = false;
        settings.latitude = 60; settings.longitude = 10;
        return settings;
    }
    @Test public void usableCacheWinsOverEveryRefreshErrorAndLoading() {
        Forecast stored = Forecast.example(now - 100 * Forecast.MINUTE);
        for (UpdateIssue issue : UpdateIssue.values()) {
            assertEquals(ForecastState.DATA, ForecastState.of(location(), stored, now, issue, false));
            assertEquals(ForecastState.DATA, ForecastState.of(location(), stored, now, issue, true));
        }
    }
    @Test public void exhaustedOrAbsentCacheLoadsUntilAnAttemptFails() {
        for (Forecast f : new Forecast[]{null, Forecast.example(now - Forecast.HORIZON)}) {
            assertEquals(ForecastState.LOADING, ForecastState.of(location(), f, now, UpdateIssue.NONE, false));
            assertEquals(ForecastState.UNAVAILABLE, ForecastState.of(location(), f, now, UpdateIssue.IO_ERROR, false));
            assertEquals(ForecastState.LOADING, ForecastState.of(location(), f, now, UpdateIssue.IO_ERROR, true));
        }
    }
    @Test public void failedCoverageAndNoFutureDataAreUnavailableAfterCompletion() {
        Forecast noCoverage = new Forecast(now, "temporarily unavailable", List.of());
        assertEquals(ForecastState.UNAVAILABLE, ForecastState.of(location(), noCoverage, now, UpdateIssue.NONE, false));
        assertEquals(ForecastState.LOADING, ForecastState.of(location(), noCoverage, now, UpdateIssue.NONE, true));
        assertEquals(ForecastState.UNAVAILABLE, ForecastState.of(location(), Forecast.example(now - Forecast.HORIZON),
                now, UpdateIssue.FORECAST_UNAVAILABLE, false));
    }
    @Test public void missingLocationCanLoadWhileLocationIsBeingResolved() {
        WidgetSettings unconfigured = new WidgetSettings();
        assertEquals(ForecastState.LOADING, ForecastState.of(unconfigured, null, now, UpdateIssue.NONE, true));
        assertEquals(ForecastState.UNAVAILABLE, ForecastState.of(unconfigured, null, now, UpdateIssue.NONE, false));
    }
    @Test public void partialDryForecastIsDataButLaterOnlyDataDoesNotHideLoading() {
        Forecast dry = new Forecast(now - 30 * Forecast.MINUTE, "ok", List.of(
                new Forecast.Point(now + 10 * Forecast.MINUTE, 0), new Forecast.Point(now + 15 * Forecast.MINUTE, 0)));
        assertEquals(ForecastState.DATA, ForecastState.of(location(), dry, now, UpdateIssue.NONE, true));
        Forecast late = new Forecast(now, "ok", List.of(
                new Forecast.Point(now + 90 * Forecast.MINUTE, 0), new Forecast.Point(now + 95 * Forecast.MINUTE, 0)));
        assertEquals(ForecastState.LOADING, ForecastState.of(location(), late, now, UpdateIssue.NONE, false));
    }
}
