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
        String description = description(context, settings, forecast, now, state);
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
    static String description(Context context, WidgetSettings settings, Forecast forecast, long now, ForecastState state) {
        if (state == ForecastState.LOADING) return context.getString(R.string.widget_waiting);
        if (!settings.hasLocation()) return context.getString(R.string.widget_location_needed);
        if (settings.locationExpired(now)) return context.getString(R.string.widget_location_stale);
        if (state == ForecastState.UNAVAILABLE) return context.getString(R.string.widget_unavailable);
        java.util.List<ForecastWindow.Segment> segments = ForecastWindow.segments(forecast, now);
        if (segments.isEmpty()) return context.getString(R.string.widget_empty);
        double peak = 0;
        for (ForecastWindow.Segment s : segments) peak = Math.max(peak, Math.max(s.startRate, s.endRate));
        String axis = settings.showTicks ? " " + context.getString(R.string.widget_ticks) : "";
        if (settings.labels) axis += " " + context.getString(R.string.widget_labels);
        axis += " " + context.getString(settings.showAxis ? R.string.widget_dotted : R.string.widget_gaps);
        return context.getString(R.string.widget_forecast, peak, axis,
                forecast.isStale(now) ? " " + context.getString(R.string.widget_stored) : "");
    }
}
