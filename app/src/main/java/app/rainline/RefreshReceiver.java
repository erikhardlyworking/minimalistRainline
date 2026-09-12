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
        // The alarm already has random jitter. It also recovers missed wake broadcasts.
        Updates.enqueue(context, true);
        RainWidgetProvider.renderAll(context);
        Updates.schedule(context);
    }
}
