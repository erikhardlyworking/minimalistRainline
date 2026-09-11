package app.rainline;

import org.json.JSONException;
import org.json.JSONObject;

public final class WidgetSettings {
    public boolean follow = true;
    public double latitude = Double.NaN, longitude = Double.NaN;
    public String place = "";
    public long locationAt;
    public float accuracy;
    public int guides = 2; // 0 light grey, 1 white, 2 hidden
    public boolean normalLine, labels = true, automatic = true;
    public int labelSize = 11;
    public boolean showAxis = true, showTicks = true;
    // Defaults retain the original chart placement, including space below its labels.
    public int paddingLeft = 8, paddingTop = 9, paddingRight = 8, paddingBottom = 1;
    public int backgroundColor = 0x00000000;
    public float scaleMax = 3f, highlightThreshold = 2.5f;
    public boolean highlightRain = true;
    public int highlightColor = 0xffff0000;

    public static boolean validRainRate(double rate) {
        return Double.isFinite(rate) && rate >= .1 && rate <= 100;
    }

    public boolean hasLocation() { return ForecastWindow.isCoordinate(latitude, longitude); }

    public boolean sameLocation(WidgetSettings other) {
        return hasLocation() && other.hasLocation()
                && ForecastWindow.coordinateKey(latitude, longitude)
                .equals(ForecastWindow.coordinateKey(other.latitude, other.longitude));
    }

    public boolean locationExpired(long now) {
        return follow && (locationAt <= 0 || now - locationAt > 2 * 60 * Forecast.MINUTE
                || locationAt - now > 5 * Forecast.MINUTE);
    }

    public WidgetSettings copy() { return fromJson(toJson()); }

    public String toJson() {
        try {
            JSONObject o = new JSONObject();
            o.put("follow", follow);
            if (hasLocation()) { o.put("lat", latitude); o.put("lon", longitude); }
            o.put("place", place).put("locationAt", locationAt).put("accuracy", accuracy);
            o.put("guides", guides).put("normalLine", normalLine).put("labels", labels);
            o.put("labelSize", labelSize);
            o.put("showAxis", showAxis).put("showTicks", showTicks);
            o.put("automatic", automatic);
            o.put("paddingLeft", paddingLeft).put("paddingTop", paddingTop);
            o.put("paddingRight", paddingRight).put("paddingBottom", paddingBottom);
            o.put("backgroundColor", ColorValue.format(backgroundColor));
            o.put("scaleMax", scaleMax).put("highlightThreshold", highlightThreshold);
            o.put("highlightRain", highlightRain).put("highlightColor", ColorValue.format(highlightColor));
            return o.toString();
        } catch (JSONException e) { throw new IllegalStateException(e); }
    }

    public static WidgetSettings fromJson(String json) {
        WidgetSettings s = new WidgetSettings();
        try {
            JSONObject o = new JSONObject(json);
            s.follow = o.optBoolean("follow", true);
            s.latitude = o.optDouble("lat", Double.NaN);
            s.longitude = o.optDouble("lon", Double.NaN);
            s.place = o.optString("place", "");
            s.locationAt = o.optLong("locationAt");
            s.accuracy = (float) o.optDouble("accuracy", 0);
            s.guides = Math.max(0, Math.min(2, o.optInt("guides", 2)));
            s.normalLine = o.optBoolean("normalLine");
            s.labels = o.optBoolean("labels", true);
            s.labelSize = Math.max(8, Math.min(24, o.optInt("labelSize", 11)));
            s.showAxis = o.optBoolean("showAxis", true);
            s.showTicks = o.optBoolean("showTicks", true);
            s.automatic = o.optBoolean("automatic", true);
            s.paddingLeft = padding(o.optInt("paddingLeft", 8));
            s.paddingTop = padding(o.optInt("paddingTop", 9));
            s.paddingRight = padding(o.optInt("paddingRight", 8));
            s.paddingBottom = padding(o.optInt("paddingBottom", s.labels ? 1 : 5));
            try { s.backgroundColor = ColorValue.parse(o.optString("backgroundColor", "#00000000")); }
            catch (IllegalArgumentException ignoredColor) { /* Keep the transparent default. */ }
            double scale = o.optDouble("scaleMax", s.scaleMax);
            if (validRainRate(scale)) s.scaleMax = (float) scale;
            double threshold = o.optDouble("highlightThreshold", s.highlightThreshold);
            if (validRainRate(threshold)) s.highlightThreshold = (float) threshold;
            s.highlightRain = o.optBoolean("highlightRain", true);
            try { s.highlightColor = 0xff000000 | ColorValue.parse(o.optString("highlightColor", "#FF0000")); }
            catch (IllegalArgumentException ignoredColor) { /* Keep the red default. */ }
        } catch (JSONException ignored) { /* New or damaged preferences need setup. */ }
        return s;
    }
    private static int padding(int value) { return Math.max(0, Math.min(48, value)); }
}
