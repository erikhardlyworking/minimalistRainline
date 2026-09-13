package app.rainline;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class ForecastTimes {
    private ForecastTimes() {}
    public static String describe(long timestamp, long now) {
        if (timestamp <= 0) return "never";
        return DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.getDefault())
                .withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(timestamp))
                + " (" + ago(timestamp, now) + ")";
    }
    static String ago(long timestamp, long now) {
        if (timestamp <= 0) return "never";
        if (timestamp > now) return "clock ahead";
        long minutes = (now - timestamp) / Forecast.MINUTE;
        if (minutes == 0) return "just now";
        if (minutes < 60) return minutes + " min ago";
        if (minutes < 24 * 60) return minutes / 60 + " h " + minutes % 60 + " min ago";
        return minutes / (24 * 60) + " d " + minutes / 60 % 24 + " h ago";
    }
}
