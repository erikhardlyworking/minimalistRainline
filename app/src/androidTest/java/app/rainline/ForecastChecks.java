package app.rainline;

import android.app.job.JobInfo;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import java.io.File;
import java.io.FileOutputStream;

final class ForecastChecks {
    static void run(Context context) throws Exception {
        long now = System.currentTimeMillis();
        WidgetSettings settings = new WidgetSettings();
        WidgetTheme.MINIMAL.applyTo(settings);
        settings.follow = false;
        settings.latitude = 60; settings.longitude = 10;
        settings.showAxis = settings.showTicks = settings.labels = false;
        settings.paddingLeft = settings.paddingTop = settings.paddingRight = settings.paddingBottom = 0;
        Forecast old = Forecast.example(now - 31 * Forecast.MINUTE);
        ForecastState state = ForecastState.of(settings, old, now, UpdateIssue.IO_ERROR, false);
        Bitmap graph = ChartRenderer.bitmap(240, 96, 2, settings, old, now, state);
        check(state == ForecastState.DATA, "Useful old weather must survive a failed refresh");
        check(hasInk(graph, 0, 354), "Old future samples must still draw a curve");
        check(!hasInk(graph, 360, 480), "Missing final forecast intervals must remain blank");
        Bitmap loading = ChartRenderer.bitmap(240, 96, 2, settings, null, now, ForecastState.LOADING);
        Bitmap error = ChartRenderer.bitmap(240, 96, 2, settings, null, now, ForecastState.UNAVAILABLE);
        check(Color.alpha(loading.getPixel(240, 96)) == 0, "Loading ring must have an empty centre");
        check(Color.alpha(error.getPixel(240, 96)) == 0, "Refresh arrow must have an empty centre");
        check(hasInk(error, 224, 256), "Refresh arrow must remain visible without axes");
        check(hasInk(loading, 230, 250), "Loading indicator must be visible without axes");
        check(!loading.sameAs(error), "Waiting and failed states must look different");
        check(RainWidgetProvider.description(context, settings, old, now, state).contains(context.getString(R.string.widget_stored)),
                "Accessibility must describe retained data");
        check(RainWidgetProvider.description(context, settings, null, now, ForecastState.LOADING).equals(context.getString(R.string.widget_waiting)),
                "Loading accessibility description must not say unavailable");
        check(RainWidgetProvider.description(context, settings, null, now, ForecastState.UNAVAILABLE).equals(context.getString(R.string.widget_unavailable)),
                "Refresh arrow must describe its action");

        JobInfo wake = Updates.refreshJob(context, true);
        JobInfo routine = Updates.refreshJob(context, false);
        check(wake.getMinLatencyMillis() == 0, "Wake requests must not add the routine random delay");
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            check(wake.isExpedited(), "Removing latency alone does not request prompt execution");
            check(!routine.isExpedited(), "Routine jobs must not consume expedited quota");
        }
        check(routine.getMinLatencyMillis() >= 5_000 && routine.getMinLatencyMillis() <= 35_000,
                "Routine scheduling must retain jitter");
        check(wake.getNetworkType() == JobInfo.NETWORK_TYPE_ANY, "Prompt refresh must still require network");
        check(wake.getExtras().getLong("requestedAt") >= now, "Queued refresh needs a timestamp for loading state");

        // A labelled contact sheet made with the same Canvas renderer as the actual widget.
        Bitmap sheet = Bitmap.createBitmap(960, 1080, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(sheet);
        canvas.drawColor(Color.BLACK);
        Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        text.setColor(Color.WHITE); text.setTextSize(28);
        settings.showAxis = settings.showTicks = settings.labels = true;
        settings.paddingLeft = settings.paddingRight = 8; settings.paddingTop = 9; settings.paddingBottom = 1;
        String[] labels = {"Stored forecast, 31 minutes old", "Waiting for data", "Data unavailable: tap to refresh"};
        ForecastState[] states = {ForecastState.DATA, ForecastState.LOADING, ForecastState.UNAVAILABLE};
        for (int i = 0; i < states.length; i++) {
            canvas.drawText(labels[i], 24, 36 + i * 360, text);
            Bitmap widget = ChartRenderer.bitmap(320, 100, 3, settings, i == 0 ? old : null, now, states[i]);
            canvas.drawBitmap(widget, 30, 50 + i * 360, null);
            widget.recycle();
        }
        try (FileOutputStream output = new FileOutputStream(new File(context.getFilesDir(), "tested-refresh-states.png"))) {
            sheet.compress(Bitmap.CompressFormat.PNG, 100, output);
        }
    }
    private static boolean hasInk(Bitmap bitmap, int fromX, int toX) {
        for (int x = fromX; x < toX; x++) for (int y = 0; y < bitmap.getHeight(); y++)
            if (Color.alpha(bitmap.getPixel(x, y)) > 0) return true;
        return false;
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
