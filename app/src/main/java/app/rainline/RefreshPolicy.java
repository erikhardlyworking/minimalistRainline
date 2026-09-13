package app.rainline;

/** Request cadence is independent of repainting the graph and the forecast's issue time. */
public final class RefreshPolicy {
    private RefreshPolicy() {}

    public static boolean isDry(Forecast forecast, long now) {
        if (forecast == null || !forecast.hasRadar() || forecast.clockAhead(now)) return false;
        // A known rain sample counts even if a missing neighbour prevents drawing its segment.
        for (Forecast.Point point : forecast.points)
            if (point.valid() && point.rate > 0 && point.time >= now && point.time < now + Forecast.HORIZON)
                return false;
        double coveredMinutes = 0;
        for (ForecastWindow.Segment segment : ForecastWindow.segments(forecast, now)) {
            if (segment.startRate > 0 || segment.endRate > 0) return false;
            if (segment.startMinute <= coveredMinutes) coveredMinutes = Math.max(coveredMinutes, segment.endMinute);
        }
        // The cached two-hour forecast naturally loses its far end as time passes.
        // Require a continuous dry near-term window, without demanding an extrapolated tail.
        return coveredMinutes >= Forecast.UPCOMING / (double) Forecast.MINUTE;
    }

    public static int intervalMinutes(WidgetSettings settings, Forecast forecast, long now) {
        return isDry(forecast, now) ? settings.dryRefreshMinutes : settings.rainRefreshMinutes;
    }

    public static long nextCheckAt(WidgetSettings settings, Forecast forecast, long checkedAt, long expiresAt, long now) {
        long intervalDue = checkedAt <= 0 || checkedAt > now ? now
                : checkedAt + intervalMinutes(settings, forecast, now) * Forecast.MINUTE;
        return Math.max(intervalDue, expiresAt);
    }
}
