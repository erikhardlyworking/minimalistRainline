package app.rainline;

import java.util.ArrayList;
import java.util.List;

/** Clips actual timestamped samples to "now .. now + two hours". No extrapolation. */
public final class ForecastWindow {
    /** Decimal commas use a semicolon between coordinates; place names return null for geocoding. */
    public static double[] parseCoordinates(String query) {
        String[] parts = query.trim().split(query.contains(";") ? ";" : ",", -1);
        if (parts.length != 2) return null;
        double lat, lon;
        try {
            lat = Double.parseDouble(parts[0].trim().replace(',', '.'));
            lon = Double.parseDouble(parts[1].trim().replace(',', '.'));
        } catch (NumberFormatException notCoordinates) { return null; }
        if (!isCoordinate(lat, lon)) throw new IllegalArgumentException("Coordinates out of range");
        return new double[]{lat, lon};
    }
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
        // Age alone does not invalidate timestamped future samples in the cache.
        if (forecast == null || !forecast.hasRadar() || forecast.clockAhead(now)) return result;
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

    /** Any real interval in the next 90 minutes is useful, including a dry interval. */
    public static boolean hasUpcomingData(Forecast forecast, long now) {
        for (Segment segment : segments(forecast, now))
            if (segment.startMinute < Forecast.UPCOMING / (double) Forecast.MINUTE) return true;
        return false;
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
