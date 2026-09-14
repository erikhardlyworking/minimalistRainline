package app.rainline;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class RefreshReceiver extends BroadcastReceiver {
    static final String REFRESH_WIDGET = "app.rainline.REFRESH_WIDGET";
    @Override public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (REFRESH_WIDGET.equals(action)) {
            int id = intent.getIntExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, 0);
            if (!Updates.isOurWidget(context, id)) return;
            UpdateDiagnostics.event(context, "widgetTap");
            Updates.enqueueManual(context, id);
            RainWidgetProvider.render(context, id);
            Updates.schedule(context);
            return;
        }
        if (!"app.rainline.TICK".equals(action) && !Intent.ACTION_BOOT_COMPLETED.equals(action)
                && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(action) && !Intent.ACTION_TIME_CHANGED.equals(action)
                && !Intent.ACTION_TIMEZONE_CHANGED.equals(action)) return;
        UpdateDiagnostics.event(context, "alarmOrSystemEvent");
        // Catch-up work after sleep can be urgent; unexpired cache and backoff suppress empty jobs.
        Updates.enqueue(context, true);
        RainWidgetProvider.renderAll(context);
        Updates.schedule(context);
    }
}
