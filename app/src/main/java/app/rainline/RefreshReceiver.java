package app.rainline;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class RefreshReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
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
