package app.rainline;

import android.content.Context;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Resolve display text at read time so saved settings and errors survive language changes. */
final class UiText {
    private UiText() {}
    static Locale locale(Context context) {
        return context.getResources().getConfiguration().getLocales().get(0);
    }
    static String rate(Context context, double value) {
        return new DecimalFormat("0.##", DecimalFormatSymbols.getInstance(locale(context))).format(value);
    }
    static String coordinates(Context context, double lat, double lon) {
        String separator = DecimalFormatSymbols.getInstance(locale(context)).getDecimalSeparator() == ',' ? "; " : ", ";
        return String.format(locale(context), "%.4f%s%.4f", lat, separator, lon);
    }
    static String place(Context context, WidgetSettings settings) {
        if (settings.follow) return context.getString(R.string.current_location);
        if (settings.place.isEmpty() || "Fixed location".equals(settings.place)) return context.getString(R.string.fixed_place);
        return settings.place;
    }
    static String time(Context context, long timestamp) {
        if (timestamp <= 0) return context.getString(R.string.time_never);
        return DateTimeFormatter.ofPattern("d MMM, HH:mm", locale(context))
                .withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(timestamp));
    }
    static String describe(Context context, long timestamp, long now) {
        if (timestamp <= 0) return context.getString(R.string.time_never);
        return context.getString(R.string.time_and_age, time(context, timestamp), ago(context, timestamp, now));
    }
    static String ago(Context context, long timestamp, long now) {
        if (timestamp <= 0) return context.getString(R.string.time_never);
        if (timestamp > now) return context.getString(R.string.time_clock_ahead);
        long minutes = (now - timestamp) / Forecast.MINUTE;
        if (minutes == 0) return context.getString(R.string.time_just_now);
        if (minutes < 60) return context.getString(R.string.time_minutes_ago, minutes);
        if (minutes < 24 * 60) return context.getString(R.string.time_hours_ago, minutes / 60, minutes % 60);
        return context.getString(R.string.time_days_ago, minutes / (24 * 60), minutes / 60 % 24);
    }
    static String issue(Context context, UpdateIssue issue) {
        if (issue == UpdateIssue.NONE) return "";
        int label = switch (issue) {
            case LOCATION_MISSING -> R.string.choose_location;
            case LOCATION_UNAVAILABLE -> R.string.error_location;
            case LOCATION_STALE -> R.string.error_location_stale;
            case RETRY_DELAY -> R.string.error_retry;
            case HTTP_FORBIDDEN -> R.string.error_forbidden;
            case HTTP_THROTTLED -> R.string.error_throttled;
            case OUTSIDE_COVERAGE -> R.string.error_coverage;
            case HTTP_ERROR -> R.string.error_http;
            case INVALID_RESPONSE -> R.string.error_response;
            case REDIRECT_ERROR -> R.string.error_redirect;
            case TIMEOUT -> R.string.error_timeout;
            case DNS_ERROR, IO_ERROR -> R.string.error_network;
            case TLS_ERROR -> R.string.error_tls;
            case API_DEPRECATED -> R.string.error_api_deprecated;
            case FORECAST_UNAVAILABLE -> R.string.error_forecast;
            default -> R.string.error_unknown;
        };
        return context.getString(label);
    }
}
