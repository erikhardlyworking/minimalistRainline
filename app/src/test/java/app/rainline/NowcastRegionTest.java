package app.rainline;

import org.junit.Test;
import static org.junit.Assert.*;

public final class NowcastRegionTest {
    @Test public void includesTheFourCountriesAndCoastalExtremes() {
        for (double[] place : new double[][]{
                {59.9139,10.7522}, {59.3293,18.0686}, {55.6761,12.5683}, {60.1699,24.9384},
                {71.17,25.78}, {60.8,4.5}, {54.56,11.97}, {62.9,31.58},
                {55.1,14.7}, {60.1,19.9}, {57.7,11.6}, {69.75,30.9}})
            assertTrue(place[0] + "," + place[1], NowcastRegion.mayHaveCoverage(place[0], place[1]));
    }
    @Test public void excludesDistantTravelDestinations() {
        for (double[] place : new double[][]{
                {48.8566,2.3522}, {51.5074,-0.1278}, {52.52,13.405}, {40.42,-3.7},
                {40.71,-74.01}, {35.68,139.65}, {-33.87,151.21}, {0,0}})
            assertFalse(NowcastRegion.mayHaveCoverage(place[0], place[1]));
    }
    @Test public void excludesNordicTerritoriesOutsideThisForecastProduct() {
        for (double[] place : new double[][]{
                {64.15,-21.94}, {62.01,-6.77}, {64.18,-51.72}, {78.22,15.65}, {70.98,-8.4}})
            assertFalse(NowcastRegion.mayHaveCoverage(place[0], place[1]));
    }
    @Test public void boundaryIsInclusiveAndGenerousNotAnExactCountryOrRadarMap() {
        assertTrue(NowcastRegion.mayHaveCoverage(54,4));
        assertTrue(NowcastRegion.mayHaveCoverage(72,32));
        assertFalse(NowcastRegion.mayHaveCoverage(53.9999,12));
        assertFalse(NowcastRegion.mayHaveCoverage(72.0001,12));
        assertFalse(NowcastRegion.mayHaveCoverage(60,3.9999));
        assertFalse(NowcastRegion.mayHaveCoverage(60,32.0001));
        assertTrue(NowcastRegion.mayHaveCoverage(59.437,24.7536)); // Tallinn: leave nearby coverage to MET.
    }
    @Test public void rejectsInvalidCoordinatesWithoutTreatingUnconfiguredWidgetAsOutside() {
        assertFalse(NowcastRegion.mayHaveCoverage(Double.NaN,12));
        assertFalse(NowcastRegion.mayHaveCoverage(60,Double.POSITIVE_INFINITY));
        assertFalse(NowcastRegion.mayHaveCoverage(91,12));
        assertFalse(NowcastRegion.outside(new WidgetSettings()));
    }
    @Test public void outsideShowsUnavailableImmediatelyThenResumesAtValidLocation() {
        WidgetSettings settings = new WidgetSettings(); settings.follow = false;
        settings.latitude = 48.8566; settings.longitude = 2.3522;
        long now = 1_800_000_000_000L;
        assertEquals(ForecastState.UNAVAILABLE, ForecastState.of(settings, null, now, UpdateIssue.NONE, false));
        assertEquals(ForecastState.UNAVAILABLE, ForecastState.of(settings, null, now, UpdateIssue.NONE, true));
        settings.latitude = 60; settings.longitude = 10;
        assertEquals(ForecastState.DATA, ForecastState.of(settings, Forecast.example(now), now, UpdateIssue.OUTSIDE_COVERAGE, false));
    }
}
