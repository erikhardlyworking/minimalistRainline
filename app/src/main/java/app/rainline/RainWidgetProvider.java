package app.rainline;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.widget.RemoteViews;
import org.json.JSONException;

public final class RainWidgetProvider extends AppWidgetProvider {
    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        Updates.enqueue(context);
        for (int id : ids) render(context, id);
        Updates.schedule(context);
    }
    @Override public void onAppWidgetOptionsChanged(Context context, AppWidgetManager manager, int id, Bundle options) {
        render(context, id);
    }
    @Override public void onDeleted(Context context, int[] ids) {
        SettingsStore store = new SettingsStore(context);
        for (int id : ids) {
            Updates.cancelManualRefresh(context, id);
            store.delete(id);
            UpdateDiagnostics.deleteWidget(context, id);
        }
        Updates.schedule(context);
    }
    @Override public void onDisabled(Context context) { Updates.schedule(context); }
    @Override public void onRestored(Context context, int[] oldIds, int[] newIds) {
        SettingsStore store = new SettingsStore(context);
        for (int i = 0; i < Math.min(oldIds.length, newIds.length); i++) {
            Updates.cancelManualRefresh(context, oldIds[i]);
            store.put(newIds[i], store.get(oldIds[i]));
            store.delete(oldIds[i]);
            UpdateDiagnostics.deleteWidget(context, oldIds[i]);
        }
        onUpdate(context, AppWidgetManager.getInstance(context), newIds);
    }
    public static void renderAll(Context context) {
        for (int id : Updates.widgetIds(context)) render(context, id);
    }
    public static Forecast forecast(Context context, WidgetSettings settings) {
        if (!settings.hasLocation()) return null;
        ForecastCache.Entry entry = new ForecastCache(context).read(
                ForecastWindow.coordinateKey(settings.latitude, settings.longitude));
        if (entry == null) return null;
        try { return entry.displayForecast(System.currentTimeMillis()); } catch (JSONException e) { return null; }
    }
    // Receivers/settings and the fetch worker may render concurrently. Serialize
    // snapshot + submission so an older bitmap cannot overwrite a newer result.
    public static synchronized void render(Context context, int id) {
        if (id <= 0) return;
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        if (manager.getAppWidgetInfo(id) == null) return;
        SettingsStore store = new SettingsStore(context);
        WidgetSettings settings = store.get(id);
        Forecast forecast = forecast(context, settings);
        long now = System.currentTimeMillis();
        Bundle options = manager.getAppWidgetOptions(id);
        int minWidth = Math.max(130, options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 240));
        int maxWidth = Math.max(minWidth, options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, minWidth));
        int minHeight = Math.max(64, options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110));
        int maxHeight = Math.max(minHeight, options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, minHeight));
        ForecastState state = ForecastState.of(settings, forecast, now, store.issue(id), Updates.pending(context, id));
        RemoteViews portrait = views(context, id, minWidth, maxHeight, settings, forecast, now, state);
        RemoteViews landscape = views(context, id, maxWidth, minHeight, settings, forecast, now, state);
        manager.updateAppWidget(id, new RemoteViews(landscape, portrait));
        UpdateDiagnostics.rendered(context, id, now, forecast == null ? 0 : forecast.updatedAt, state);
    }
    private static RemoteViews views(Context context, int id, int width, int height,
                                     WidgetSettings settings, Forecast forecast, long now, ForecastState state) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.rain_widget);
        Bitmap bitmap = ChartRenderer.bitmap(width, height, context.getResources().getDisplayMetrics().density,
                settings, forecast, now, state);
        views.setImageViewBitmap(R.id.chart, bitmap);
        String description = description(settings, forecast, now, state);
        views.setContentDescription(R.id.chart, description);
        views.setOnClickPendingIntent(R.id.chart, refreshIntent(context, id));
        return views;
    }
    static PendingIntent refreshIntent(Context context, int id) {
        Intent click = new Intent(context, RefreshReceiver.class)
                .setAction(RefreshReceiver.REFRESH_WIDGET)
                .addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .setData(android.net.Uri.parse("rainline://refresh/" + id));
        return PendingIntent.getBroadcast(context, id, click,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
    static String description(WidgetSettings settings, Forecast forecast, long now, ForecastState state) {
        if (state == ForecastState.LOADING) return "Waiting for precipitation forecast. Tap to refresh.";
        if (!settings.hasLocation()) return "Location needed. Open Rainline to choose a location. Tap to retry.";
        if (settings.locationExpired(now)) return "Location needs updating. Tap to refresh. Open Rainline if location access is needed.";
        if (state == ForecastState.UNAVAILABLE) return "Precipitation forecast unavailable. Tap to refresh.";
        java.util.List<ForecastWindow.Segment> segments = ForecastWindow.segments(forecast, now);
        if (segments.isEmpty()) return "No current precipitation data. Tap to refresh.";
        double peak = 0;
        for (ForecastWindow.Segment s : segments) peak = Math.max(peak, Math.max(s.startRate, s.endRate));
        String axis = settings.showTicks ? " Five-minute ticks." : "";
        if (settings.labels) axis += " Labels at 30, 60 and 90 minutes.";
        axis += settings.showAxis ? " Dotted axis intervals have no data." : " Gaps have no data.";
        return String.format(java.util.Locale.getDefault(),
                "Two-hour precipitation forecast. Peak %.1f millimetres per hour.%s%s Tap to refresh.",
                peak, axis, forecast.isStale(now) ? " Using stored forecast data." : "");
    }
}
