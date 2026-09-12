package app.rainline;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;

/** Screen events require runtime registration; Android can defer them in a cached process. */
public final class RainlineApplication extends Application {
    private final BroadcastReceiver screenEvents = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            handleScreenEvent(context, intent.getAction());
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        filter.addAction(Intent.ACTION_TIME_TICK);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(screenEvents, filter, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(screenEvents, filter);
    }

    static void handleScreenEvent(Context context, String action) {
        boolean wake = Intent.ACTION_SCREEN_ON.equals(action) || Intent.ACTION_USER_PRESENT.equals(action);
        if (!wake && !Intent.ACTION_TIME_TICK.equals(action)) return;
        if (!Updates.deviceActive(context) || Updates.widgetIds(context).length == 0) return;
        // Queue the fetch first, then immediately realign the cached graph without waiting for HTTP.
        if (wake) Updates.enqueue(context, true);
        RainWidgetProvider.renderAll(context);
        if (wake) Updates.schedule(context);
    }
}
