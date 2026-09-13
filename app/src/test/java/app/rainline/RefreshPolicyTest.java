package app.rainline;

import org.junit.Test;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

public class RefreshPolicyTest {
    private final long now = Instant.parse("2026-09-14T10:00:00Z").toEpochMilli();
    private final WidgetSettings settings = new WidgetSettings();
    private Forecast dry() {
        List<Forecast.Point> points = new ArrayList<>();
        for (int minute = -20; minute <= 120; minute += 5)
            points.add(new Forecast.Point(now + minute * Forecast.MINUTE, 0));
        return new Forecast(now - 20 * Forecast.MINUTE, "ok", points);
    }
    private Forecast withRate(int minute, double rate) {
        List<Forecast.Point> points = new ArrayList<>(dry().points);
        for (int i = 0; i < points.size(); i++)
            if (points.get(i).time == now + minute * Forecast.MINUTE)
                points.set(i, new Forecast.Point(points.get(i).time, rate));
        return new Forecast(dry().updatedAt, "ok", points);
    }
    @Test public void dryDefaultWaitsFifteenMinutesFromServerCheckEvenAfterHttpExpiry() {
        long checked = now - 5 * Forecast.MINUTE;
        assertTrue(RefreshPolicy.isDry(dry(), now));
        assertEquals(now + 10 * Forecast.MINUTE, RefreshPolicy.nextCheckAt(settings, dry(), checked, now - 1, now));
    }
    @Test public void anyRainInTwoHourWindowUsesFiveMinutes() {
        for (int minute : new int[]{0, 5, 90, 115}) {
            assertEquals(5, RefreshPolicy.intervalMinutes(settings, withRate(minute, .001), now));
            assertEquals(now, RefreshPolicy.nextCheckAt(settings, withRate(minute, .001),
                    now - 5 * Forecast.MINUTE, now - 1, now));
        }
    }
    @Test public void rainThatHasAlreadyEndedDoesNotKeepTheFastCadence() {
        assertEquals(15, RefreshPolicy.intervalMinutes(settings, withRate(-5, 1), now));
    }
    @Test public void rainBeyondTwoHoursDoesNotSelectTheFastCadence() {
        List<Forecast.Point> points = new ArrayList<>(dry().points);
        points.add(new Forecast.Point(now + 125 * Forecast.MINUTE, 1));
        assertTrue(RefreshPolicy.isDry(new Forecast(now, "ok", points), now));
    }
    @Test public void interpolationAtNowStillCountsOngoingRain() {
        Forecast f = withRate(-5, 1);
        assertFalse(RefreshPolicy.isDry(f, now - Forecast.MINUTE));
    }
    @Test public void gapsAndMissingRadarAreNotDryForecasts() {
        assertFalse(RefreshPolicy.isDry(null, now));
        assertFalse(RefreshPolicy.isDry(new Forecast(now, "temporarily unavailable", dry().points), now));
        assertFalse(RefreshPolicy.isDry(withRate(30, Double.NaN), now));
        assertFalse(RefreshPolicy.isDry(new Forecast(now, "ok", List.of(
                new Forecast.Point(now, 0), new Forecast.Point(now + 5 * Forecast.MINUTE, 0))), now));
    }
    @Test public void shortenedDryTailIsAllowedButLessThanNinetyMinutesIsUnknown() {
        assertTrue(RefreshPolicy.isDry(dry(), now + 30 * Forecast.MINUTE));
        assertFalse(RefreshPolicy.isDry(dry(), now + 30 * Forecast.MINUTE + 1));
    }
    @Test public void isolatedRainAfterNinetyMinutesStillSelectsRainInterval() {
        List<Forecast.Point> points = new ArrayList<>(dry().points.subList(0, 23)); // through +90 min
        points.add(new Forecast.Point(now + 110 * Forecast.MINUTE, 1));
        assertFalse(RefreshPolicy.isDry(new Forecast(now, "ok", points), now));
    }
    @Test public void customIntervalsAreIndependentAndPersistAcrossRestart() {
        settings.rainRefreshMinutes = 10; settings.dryRefreshMinutes = 30;
        WidgetSettings restored = WidgetSettings.fromJson(settings.toJson());
        assertEquals(10, RefreshPolicy.intervalMinutes(restored, Forecast.example(now), now));
        assertEquals(30, RefreshPolicy.intervalMinutes(restored, dry(), now));
        assertEquals(10, RefreshPolicy.intervalMinutes(restored, null, now));
    }
    @Test public void legacyAndInvalidSettingsUseSafeDefaults() {
        for (String json : new String[]{"{}", "{\"rainRefreshMinutes\":0,\"dryRefreshMinutes\":999999}"}) {
            WidgetSettings restored = WidgetSettings.fromJson(json);
            assertEquals(5, restored.rainRefreshMinutes);
            assertEquals(15, restored.dryRefreshMinutes);
        }
    }
    @Test public void serverExpiryCanPostponeButNeverShortenSelectedInterval() {
        assertEquals(now + 20 * Forecast.MINUTE, RefreshPolicy.nextCheckAt(settings, Forecast.example(now),
                now - 5 * Forecast.MINUTE, now + 20 * Forecast.MINUTE, now));
    }
    @Test public void missingOrFutureCheckTimestampDoesNotAddAnInterval() {
        assertEquals(now, RefreshPolicy.nextCheckAt(settings, dry(), 0, 0, now));
        assertEquals(now, RefreshPolicy.nextCheckAt(settings, dry(), now + Forecast.MINUTE, now - 1, now));
    }
}
