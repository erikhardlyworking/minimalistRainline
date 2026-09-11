package app.rainline;

import android.appwidget.AppWidgetManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class PinReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        int id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) return;
        android.appwidget.AppWidgetProviderInfo info = AppWidgetManager.getInstance(context).getAppWidgetInfo(id);
        if (info == null || !new android.content.ComponentName(context, RainWidgetProvider.class).equals(info.provider)) return;
        String json = intent.getStringExtra("settings");
        if (json != null) new SettingsStore(context).put(id, WidgetSettings.fromJson(json));
        RainWidgetProvider.render(context, id);
        Updates.schedule(context);
        Updates.enqueue(context);
    }
}
