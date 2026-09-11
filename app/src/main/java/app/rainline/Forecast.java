package app.rainline;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** MET's instantaneous precipitation rate, in mm/h; missing is never zero. */
public final class Forecast {
    public static final long MINUTE = 60_000L;
    public static final long HORIZON = 120 * MINUTE;
    public static final long STALE_AFTER = 20 * MINUTE;
    public final long updatedAt;
    public final String coverage;
    public final List<Point> points;

    public static final class Point {
        public final long time;
        public final double rate;
        public Point(long time, double rate) { this.time = time; this.rate = rate; }
        public boolean valid() { return Double.isFinite(rate) && rate >= 0; }
    }

    public Forecast(long updatedAt, String coverage, List<Point> points) {
        this.updatedAt = updatedAt;
        this.coverage = coverage;
        this.points = Collections.unmodifiableList(new ArrayList<>(points));
    }

    public static Forecast parse(String json) throws JSONException {
        try {
            JSONObject properties = new JSONObject(json).getJSONObject("properties");
            JSONObject meta = properties.getJSONObject("meta");
            long updated = Instant.parse(meta.getString("updated_at")).toEpochMilli();
            String coverage = meta.optString("radar_coverage", "unknown");
            JSONObject units = meta.optJSONObject("units");
            if ("ok".equals(coverage) && (units == null
                    || !"mm/h".equals(units.optString("precipitation_rate")))) {
                throw new JSONException("Missing or unsupported precipitation rate unit");
            }
            JSONArray series = properties.getJSONArray("timeseries");
            if (series.length() > 1000) throw new JSONException("Forecast is too large");
            List<Point> points = new ArrayList<>();
            long previous = Long.MIN_VALUE;
            for (int i = 0; i < series.length(); i++) {
                JSONObject entry = series.getJSONObject(i);
                long time = Instant.parse(entry.getString("time")).toEpochMilli();
                if (time <= previous) throw new JSONException("Forecast times must increase");
                previous = time;
                JSONObject data = entry.optJSONObject("data");
                JSONObject instant = data == null ? null : data.optJSONObject("instant");
                JSONObject details = instant == null ? null : instant.optJSONObject("details");
                double rate = details == null ? Double.NaN
                        : details.optDouble("precipitation_rate", Double.NaN);
                if (!Double.isFinite(rate) || rate < 0) rate = Double.NaN;
                points.add(new Point(time, rate));
            }
            return new Forecast(updated, coverage, points);
        } catch (java.time.DateTimeException e) {
            throw new JSONException("Invalid forecast timestamp");
        }
    }

    public boolean hasRadar() {
        if (!"ok".equals(coverage)) return false;
        for (Point point : points) if (point.valid()) return true;
        return false;
    }

    public boolean isStale(long now) {
        return now - updatedAt > STALE_AFTER || updatedAt - now > 5 * MINUTE;
    }

    /** Example data is used exclusively in the labelled settings/picker preview. */
    public static Forecast example(long now) {
        double[] rates = {0, 0, .1, .3, .15, .25, 1.0, 1.9, 2.9, 2.6, 1.8,
                1.9, 1.3, 1.0, .5, .2, 0, 0, 0, 0, 0, 0, 0, 0, 0};
        List<Point> points = new ArrayList<>();
        for (int i = 0; i < rates.length; i++) points.add(new Point(now + i * 5 * MINUTE, rates[i]));
        return new Forecast(now, "ok", points);
    }
}
