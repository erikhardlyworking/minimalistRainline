package app.rainline;

import org.json.JSONException;
import org.junit.Test;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

public class ForecastTest {
    private final long now = Instant.parse("2026-09-11T10:00:00Z").toEpochMilli();
    private String json(String coverage, String units, String rows) {
        return "{\"properties\":{\"meta\":{\"updated_at\":\"2026-09-11T10:00:00Z\","
                + "\"radar_coverage\":\"" + coverage + "\",\"units\":{" + units + "}},\"timeseries\":[" + rows + "]}}";
    }
    private String row(String time, String details) {
        return "{\"time\":\"2026-09-11T" + time + ":00Z\",\"data\":{\"instant\":{\"details\":{" + details + "}}}}";
    }
    @Test public void parsesLiveApiShapeAndInstantaneousUnits() throws Exception {
        Forecast forecast = Forecast.parse(json("ok", "\"precipitation_rate\":\"mm/h\"",
                row("10:00", "\"precipitation_rate\":1.7") + "," + row("10:05", "\"precipitation_rate\":0")));
        assertTrue(forecast.hasRadar());
        assertEquals(now, forecast.updatedAt);
        assertEquals(1.7, forecast.points.get(0).rate, .0001);
        assertEquals(0, forecast.points.get(1).rate, 0);
    }
    @Test public void missingNegativeAndNullRatesRemainUnknown() throws Exception {
        Forecast f = Forecast.parse(json("ok", "\"precipitation_rate\":\"mm/h\"",
                row("10:00", "") + "," + row("10:05", "\"precipitation_rate\":null")
                        + "," + row("10:10", "\"precipitation_rate\":-1")));
        assertFalse(f.hasRadar());
        for (Forecast.Point p : f.points) assertFalse(p.valid());
    }
    @Test public void coverageFlagOverridesUnexpectedRates() throws Exception {
        Forecast f = Forecast.parse(json("no coverage", "", row("10:00", "\"precipitation_rate\":2")));
        assertFalse(f.hasRadar());
        assertTrue(ForecastWindow.segments(f, now).isEmpty());
    }
    @Test public void temporarilyUnavailableNeedsNoPrecipitationUnit() throws Exception {
        Forecast f = Forecast.parse(json("temporarily unavailable", "", row("10:00", "\"air_temperature\":10")));
        assertFalse(f.hasRadar());
    }
    @Test(expected = JSONException.class) public void rejectsUnexpectedUnits() throws Exception {
        Forecast.parse(json("ok", "\"precipitation_rate\":\"mm\"", row("10:00", "\"precipitation_rate\":1")));
    }
    @Test(expected = JSONException.class) public void rejectsOutOfOrderTimes() throws Exception {
        Forecast.parse(json("ok", "\"precipitation_rate\":\"mm/h\"",
                row("10:05", "\"precipitation_rate\":1") + "," + row("10:00", "\"precipitation_rate\":2")));
    }
    @Test(expected = JSONException.class) public void rejectsDuplicateTimes() throws Exception {
        Forecast.parse(json("ok", "\"precipitation_rate\":\"mm/h\"",
                row("10:00", "\"precipitation_rate\":1") + "," + row("10:00", "\"precipitation_rate\":2")));
    }
    @Test(expected = JSONException.class) public void rejectsBadTimestamp() throws Exception {
        Forecast.parse(json("ok", "\"precipitation_rate\":\"mm/h\"", row("not-a-time", "\"precipitation_rate\":1")));
    }
    @Test public void interpolatesAtNowWithoutShiftingFutureTimes() {
        Forecast f = new Forecast(now, "ok", Arrays.asList(
                new Forecast.Point(now - 2 * Forecast.MINUTE, 0),
                new Forecast.Point(now + 3 * Forecast.MINUTE, 5)));
        List<ForecastWindow.Segment> result = ForecastWindow.segments(f, now);
        assertEquals(1, result.size());
        assertEquals(0, result.get(0).startMinute, 0);
        assertEquals(2, result.get(0).startRate, .0001);
        assertEquals(3, result.get(0).endMinute, 0);
    }
    @Test public void neverExtendsShortForecastToFillTwoHours() {
        Forecast f = new Forecast(now, "ok", Arrays.asList(
                new Forecast.Point(now, 2), new Forecast.Point(now + 5 * Forecast.MINUTE, 1)));
        List<ForecastWindow.Segment> result = ForecastWindow.segments(f, now);
        assertEquals(1, result.size());
        assertEquals(5, result.get(0).endMinute, 0);
    }
    @Test public void clipsAtTwoHoursAndInterpolatesBoundary() {
        Forecast f = new Forecast(now, "ok", Arrays.asList(
                new Forecast.Point(now + 118 * Forecast.MINUTE, 2),
                new Forecast.Point(now + 123 * Forecast.MINUTE, 7)));
        ForecastWindow.Segment s = ForecastWindow.segments(f, now).get(0);
        assertEquals(120, s.endMinute, 0);
        assertEquals(4, s.endRate, .0001);
    }
    @Test public void doesNotBridgeMissingSamplesOrLargerTimeGaps() {
        Forecast f = new Forecast(now, "ok", Arrays.asList(
                new Forecast.Point(now, 2),
                new Forecast.Point(now + 5 * Forecast.MINUTE, Double.NaN),
                new Forecast.Point(now + 10 * Forecast.MINUTE, 1),
                new Forecast.Point(now + 20 * Forecast.MINUTE, 0)));
        assertTrue(ForecastWindow.segments(f, now).isEmpty());
    }
    @Test public void oldForecastRetainsFutureSamplesAtTheirCorrectTimes() {
        Forecast f = Forecast.example(now);
        assertFalse(f.isStale(now + 20 * Forecast.MINUTE));
        assertTrue(f.isStale(now + 20 * Forecast.MINUTE + 1));
        List<ForecastWindow.Segment> segments = ForecastWindow.segments(f, now + 31 * Forecast.MINUTE);
        assertEquals(0, segments.get(0).startMinute, 0);
        assertEquals(4, segments.get(0).endMinute, 0);
        assertEquals(1.18, segments.get(0).startRate, .0001);
        assertEquals(89, segments.get(segments.size() - 1).endMinute, 0);
        assertTrue(ForecastWindow.hasUpcomingData(f, now + 100 * Forecast.MINUTE));
        assertFalse(ForecastWindow.hasUpcomingData(f, now + 120 * Forecast.MINUTE));
    }
    @Test public void clockFarBehindStillRejectsFutureIssuedForecast() {
        Forecast f = Forecast.example(now);
        assertTrue(ForecastWindow.segments(f, now - 6 * Forecast.MINUTE).isEmpty());
    }
    @Test public void upcomingWindowRequiresSomeDataBeforeNinetyMinutes() {
        Forecast later = new Forecast(now, "ok", Arrays.asList(
                new Forecast.Point(now + 90 * Forecast.MINUTE, 0),
                new Forecast.Point(now + 95 * Forecast.MINUTE, 0)));
        assertFalse(ForecastWindow.hasUpcomingData(later, now));
        // Even a partly overlapping dry interval is useful; no full 90-minute coverage is required.
        assertTrue(ForecastWindow.hasUpcomingData(later, now + Forecast.MINUTE));
    }
    @Test public void samplesBeforeNowAreExcluded() {
        Forecast f = new Forecast(now, "ok", Arrays.asList(
                new Forecast.Point(now - 10 * Forecast.MINUTE, 2),
                new Forecast.Point(now - 5 * Forecast.MINUTE, 0)));
        assertTrue(ForecastWindow.segments(f, now).isEmpty());
    }
    @Test public void validatesAndRoundsCoordinatesIndependentlyOfLocale() {
        LocaleGuard.runGerman(() -> assertEquals("59.9139,10.7522", ForecastWindow.coordinateKey(59.91394, 10.75224)));
        assertFalse(ForecastWindow.isCoordinate(Double.NaN, 10));
        assertFalse(ForecastWindow.isCoordinate(60, Double.POSITIVE_INFINITY));
        assertFalse(ForecastWindow.isCoordinate(90.01, 10));
        assertTrue(ForecastWindow.isCoordinate(-90, -180));
    }
    @Test public void settingsRoundTripRetainsLocationAndIndependentAppearance() {
        WidgetSettings s = new WidgetSettings();
        s.follow = false; s.latitude = 60.1; s.longitude = 11.2; s.place = "A \"place\"";
        s.guides = 2; s.normalLine = true; s.labels = false; s.automatic = false;
        s.labelSize = 20;
        s.showAxis = false; s.showTicks = false;
        s.paddingLeft = 0; s.paddingTop = 24; s.paddingRight = 12; s.paddingBottom = 48;
        s.backgroundColor = ColorValue.parse("#802A3B4C");
        s.scaleMax = 4.5f; s.highlightThreshold = 1.2f; s.highlightRain = false;
        s.highlightColor = 0xff34dd56;
        WidgetSettings copy = s.copy();
        assertTrue(s.sameLocation(copy));
        assertEquals(s.place, copy.place);
        assertFalse(copy.follow);
        assertEquals(2, copy.guides);
        assertTrue(copy.normalLine);
        assertFalse(copy.labels);
        assertEquals(20, copy.labelSize);
        assertFalse(copy.showAxis);
        assertFalse(copy.showTicks);
        assertFalse(copy.automatic);
        assertEquals(0, copy.paddingLeft);
        assertEquals(24, copy.paddingTop);
        assertEquals(12, copy.paddingRight);
        assertEquals(48, copy.paddingBottom);
        assertEquals("#802A3B4C", ColorValue.format(copy.backgroundColor));
        assertEquals(4.5f, copy.scaleMax, 0);
        assertEquals(1.2f, copy.highlightThreshold, 0);
        assertFalse(copy.highlightRain);
        assertEquals(0xff34dd56, copy.highlightColor);
        copy.guides = 0;
        assertEquals(2, s.guides);
    }
    @Test public void freshDefaultCannotMasqueradeAsALocation() {
        WidgetSettings s = WidgetSettings.fromJson("{}");
        assertTrue(s.follow);
        assertFalse(s.hasLocation());
        assertTrue(s.locationExpired(now));
        assertFalse(s.copy().hasLocation());
        assertEquals(8, s.paddingLeft);
        assertEquals(9, s.paddingTop);
        assertEquals(8, s.paddingRight);
        assertEquals(1, s.paddingBottom);
        assertEquals(0x00000000, s.backgroundColor);
        assertEquals(2, s.guides);
        assertEquals(0, WidgetSettings.fromJson("{\"guides\":0}").guides);
        assertEquals(11, s.labelSize);
        assertTrue(s.showAxis);
        assertTrue(s.showTicks);
        assertEquals(3f, s.scaleMax, 0);
        assertEquals(2.5f, s.highlightThreshold, 0);
        assertTrue(s.highlightRain);
        assertEquals(0xffff0000, s.highlightColor);
    }
    @Test public void invalidRainfallSettingsFallBackWithoutLosingSavedAppearance() {
        WidgetSettings s = WidgetSettings.fromJson("{\"scaleMax\":0,\"highlightThreshold\":-1,\"highlightColor\":\"invalid\",\"backgroundColor\":\"#123456\",\"paddingTop\":12}");
        assertEquals(3f, s.scaleMax, 0);
        assertEquals(2.5f, s.highlightThreshold, 0);
        assertEquals(0xffff0000, s.highlightColor);
        assertEquals(0xff123456, s.backgroundColor);
        assertEquals(12, s.paddingTop);
        assertFalse(WidgetSettings.validRainRate(Double.NaN));
        assertFalse(WidgetSettings.validRainRate(Double.POSITIVE_INFINITY));
        assertFalse(WidgetSettings.validRainRate(.09));
        assertFalse(WidgetSettings.validRainRate(101));
        assertTrue(WidgetSettings.validRainRate(.1));
        assertTrue(WidgetSettings.validRainRate(100));
    }
    @Test public void fixedPlaceDoesNotAgeButFollowedLocationDoes() {
        WidgetSettings s = new WidgetSettings();
        s.locationAt = now;
        assertFalse(s.locationExpired(now + 60 * Forecast.MINUTE));
        assertTrue(s.locationExpired(now + 121 * Forecast.MINUTE));
        s.follow = false;
        assertFalse(s.locationExpired(now + 121 * Forecast.MINUTE));
    }
    private static final class LocaleGuard {
        static void runGerman(Runnable action) {
            java.util.Locale original = java.util.Locale.getDefault();
            try { java.util.Locale.setDefault(java.util.Locale.GERMANY); action.run(); }
            finally { java.util.Locale.setDefault(original); }
        }
    }
}
