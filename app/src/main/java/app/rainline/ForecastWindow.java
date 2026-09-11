package app.rainline;

import java.util.ArrayList;
import java.util.List;

/** Clips actual timestamped samples to "now .. now + two hours". No extrapolation. */
public final class ForecastWindow {
    private ForecastWindow() {}
    public static final class Segment {
        public final double startMinute, endMinute, startRate, endRate;
        Segment(double startMinute, double endMinute, double startRate, double endRate) {
            this.startMinute = startMinute;
            this.endMinute = endMinute;
            this.startRate = startRate;
            this.endRate = endRate;
        }
    }

    public static List<Segment> segments(Forecast forecast, long now) {
        List<Segment> result = new ArrayList<>();
        if (forecast == null || !forecast.hasRadar() || forecast.isStale(now)) return result;
        for (int i = 1; i < forecast.points.size(); i++) {
            Forecast.Point a = forecast.points.get(i - 1), b = forecast.points.get(i);
            // A missing sample is a real gap, not a dry interval or an invented straight line.
            if (!a.valid() || !b.valid() || b.time - a.time > 5 * Forecast.MINUTE
                    || b.time <= a.time || b.time <= now || a.time >= now + Forecast.HORIZON) continue;
            long from = Math.max(a.time, now), to = Math.min(b.time, now + Forecast.HORIZON);
            double span = b.time - a.time;
            result.add(new Segment((from - now) / (double) Forecast.MINUTE,
                    (to - now) / (double) Forecast.MINUTE,
                    a.rate + (b.rate - a.rate) * (from - a.time) / span,
                    a.rate + (b.rate - a.rate) * (to - a.time) / span));
        }
        return result;
    }

    public static boolean isCoordinate(double lat, double lon) {
        return Double.isFinite(lat) && Double.isFinite(lon)
                && lat >= -90 && lat <= 90 && lon >= -180 && lon <= 180;
    }

    public static String coordinateKey(double lat, double lon) {
        if (!isCoordinate(lat, lon)) throw new IllegalArgumentException("Invalid coordinates");
        // MET asks for at most four decimals so nearby requests can share its cache.
        return String.format(java.util.Locale.ROOT, "%.4f,%.4f", lat == 0 ? 0 : lat, lon == 0 ? 0 : lon);
    }
}
