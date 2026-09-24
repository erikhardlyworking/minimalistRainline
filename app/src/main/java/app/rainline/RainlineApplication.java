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
        // USER_PRESENT comes from System UI, which has its own UID on e.g. Samsung.
        // NOT_EXPORTED accepts SCREEN_ON from system_server but drops that unlock event.
        // Every action in this filter is a protected Android broadcast: ordinary apps
        // cannot send one, even though this receiver must accept external senders.
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(screenEvents, filter, Context.RECEIVER_EXPORTED);
        else registerReceiver(screenEvents, filter);
    }

    static void handleScreenEvent(Context context, String action) {
        boolean wake = Intent.ACTION_SCREEN_ON.equals(action) || Intent.ACTION_USER_PRESENT.equals(action);
        if (!wake && !Intent.ACTION_TIME_TICK.equals(action)) return;
        if (wake) UpdateDiagnostics.event(context, Intent.ACTION_USER_PRESENT.equals(action) ? "unlock" : "screenOn");
        if (Updates.widgetIds(context).length == 0) return;
        if (!Updates.deviceActive(context)) {
            // SCREEN_ON can precede unlock. Preserve one catch-up even if USER_PRESENT
            // is later deferred; the follow-up itself still refuses HTTP while locked.
            if (wake && Updates.hasAutomaticWidgets(context) && Updates.forecastCheckDue(context))
                RecoveryScheduler.schedule(context, context.getSystemService(android.app.job.JobScheduler.class),
                        new android.os.PersistableBundle(), new RefreshResult(RefreshResult.Kind.ASLEEP_OR_LOCKED));
            return;
        }
        // Queue the fetch first, then immediately realign the cached graph without waiting for HTTP.
        if (wake) Updates.enqueue(context, true);
        RainWidgetProvider.renderAll(context);
        if (wake) Updates.schedule(context);
    }
}
