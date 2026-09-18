package app.rainline;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.widget.RemoteViews;
import java.io.File;
import java.io.FileOutputStream;

/** Device tests with Android's own instrumentation; no test SDK in the app. */
public final class SmokeInstrumentation extends Instrumentation {
    private boolean live, wake, wakeOnly, tap, locales, store;
    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        live = arguments != null && "true".equals(arguments.getString("live"));
        wake = arguments != null && "true".equals(arguments.getString("wake"));
        wakeOnly = arguments != null && "true".equals(arguments.getString("wakeOnly"));
        tap = arguments != null && "true".equals(arguments.getString("tap"));
        locales = arguments != null && "true".equals(arguments.getString("locales"));
        store = arguments != null && "true".equals(arguments.getString("store"));
        start();
    }
    @Override public void onStart() {
        Bundle results = new Bundle();
        try {
            Context context = getTargetContext();
            if (store) {
                StoreScreenshotInstrumentation.run(this);
                results.putString("result", "20 native screenshots, 5 feature graphics and the app icon exported");
                finish(Activity.RESULT_OK, results);
                return;
            }
            if (locales) {
                LocalizationChecks.run(this, context);
                results.putString("result", "Five app languages, English fallback, formatting, decimal input and native layouts passed");
                finish(Activity.RESULT_OK, results);
                return;
            }
            if (wakeOnly) {
                WakeChecks.run(this, context);
                results.putString("result", "Unlock, cache refresh and widget host delivery passed");
                finish(Activity.RESULT_OK, results);
                return;
            }
            ClientChecks.run(context);
            ForecastChecks.run(context);
            SchedulingChecks.run(context);
            LocationChecks.run(context);
            RefreshChecks.run(context);
            if (tap) TapChecks.run(this, context);
            if (wake) WakeChecks.run(this, context);
            long now = System.currentTimeMillis();
            WidgetSettings settings = new WidgetSettings();
            settings.follow = false;
            for (int[] size : new int[][]{{130,64}, {240,110}, {400,100}, {180,240}}) {
                Bitmap bitmap = ChartRenderer.bitmap(size[0], size[1], 3f, settings, Forecast.example(now), now, ForecastState.DATA);
                check(bitmap.getWidth() > 0 && bitmap.getHeight() > 0, "Empty bitmap");
                check(bitmap.getAllocationByteCount() <= 1_800_000, "Bitmap exceeds widget budget");
                check(android.graphics.Color.alpha(bitmap.getPixel(0,0)) == 0, "Default background must be transparent");
                RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.rain_widget);
                views.setImageViewBitmap(R.id.chart, bitmap);
                runOnMainSync(() -> {
                    android.view.View inflated = views.apply(context, null);
                    check(inflated.findViewById(R.id.chart) != null, "RemoteViews inflation failed");
                });
            }
            Bitmap preview = ChartRenderer.bitmap(360, 146, 3f, settings, Forecast.example(now), now, ForecastState.DATA);
            try (FileOutputStream output = new FileOutputStream(new File(context.getFilesDir(), "tested-chart.png"))) {
                preview.compress(Bitmap.CompressFormat.PNG, 100, output);
            }
            WidgetSettings largeLabels = settings.copy();
            largeLabels.labelSize = 24;
            for (int[] size : new int[][]{{130,64}, {320,100}}) {
                Bitmap labels = ChartRenderer.bitmap(size[0], size[1], 3f, largeLabels, Forecast.example(now), now, ForecastState.DATA);
                check(labels.getAllocationByteCount() <= 1_800_000, "Large labels exceed widget bitmap budget");
                try (FileOutputStream output = new FileOutputStream(new File(context.getFilesDir(),
                        size[0] == 130 ? "tested-labels-compact.png" : "tested-labels-large.png"))) {
                    labels.compress(Bitmap.CompressFormat.PNG, 100, output);
                }
            }
            Bitmap missing = ChartRenderer.bitmap(240, 110, 2f, settings, null, now, ForecastState.LOADING);
            check(missing.getWidth() == 480, "Missing forecast should still render");
            checkRainfallRendering(now);
            WidgetSettings padded = settings.copy();
            padded.paddingLeft = padded.paddingTop = padded.paddingRight = padded.paddingBottom = 48;
            padded.backgroundColor = 0xff163047;
            Bitmap compact = ChartRenderer.bitmap(130, 64, 3f, padded, Forecast.example(now), now, ForecastState.DATA);
            check(compact.getPixel(0, 0) == padded.backgroundColor, "Custom background must cover padding");
            int whitePixels = 0;
            for (int y = 0; y < compact.getHeight(); y++)
                for (int x = 0; x < compact.getWidth(); x++)
                    if (compact.getPixel(x, y) == android.graphics.Color.WHITE) whitePixels++;
            check(whitePixels > 100, "Large padding must not erase a compact graph");
            padded.backgroundColor = 0;
            Bitmap transparent = ChartRenderer.bitmap(130, 64, 2f, padded, null, now, ForecastState.LOADING);
            check(android.graphics.Color.alpha(transparent.getPixel(0, 0)) == 0, "Transparent background was flattened");
            Activity activity = startActivitySync(new Intent(context, SettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            waitForIdleSync();
            check(activity != null, "Settings did not launch");
            runOnMainSync(() -> {
                android.view.View root = activity.findViewById(android.R.id.content);
                check(find(root, android.widget.Button.class, activity.getString(R.string.open_yr)) != null, "Open Yr button missing");
                check(find(root, android.widget.TextView.class, activity.getString(R.string.rain_interval)) != null, "Rain refresh interval setting missing");
                check(find(root, android.widget.TextView.class, activity.getString(R.string.dry_interval)) != null, "Dry refresh interval setting missing");
                check(find(root, android.widget.TextView.class, "30 · 60 · 90 minutes  /  small ticks every 5 minutes") == null,
                        "Old time-axis explanatory text must be removed");
            });
            runOnMainSync(() -> {
                android.view.View root = activity.findViewById(android.R.id.content);
                int width = context.getResources().getDisplayMetrics().widthPixels;
                int height = context.getResources().getDisplayMetrics().heightPixels;
                root.measure(android.view.View.MeasureSpec.makeMeasureSpec(width, android.view.View.MeasureSpec.EXACTLY),
                        android.view.View.MeasureSpec.makeMeasureSpec(height, android.view.View.MeasureSpec.EXACTLY));
                root.layout(0, 0, width, height);
                Bitmap screen = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                root.draw(new android.graphics.Canvas(screen));
                try (FileOutputStream output = new FileOutputStream(new File(context.getFilesDir(), "tested-settings.png"))) {
                    screen.compress(Bitmap.CompressFormat.PNG, 100, output);
                } catch (java.io.IOException e) { throw new AssertionError(e); }
            });
            checkAppearanceDialogs(activity, context, settings);
            checkRainfallDialogs(activity, context, settings);
            checkTimeAxisDialog(activity, context, settings);
            onMain(() -> {
                android.app.AlertDialog diagnostics = Diagnostics.show(activity, 0);
                capture(diagnostics, context, "tested-diagnostics.png");
                diagnostics.dismiss();
            });
            runOnMainSync(activity::finish);
            String network = wake ? " Emulator wake triggered a completed MET forecast fetch." : "";
            if (live) {
                MetClient client = new MetClient(context);
                ForecastCache.Entry response = client.fetch(59.9139, 10.7522);
                Forecast forecast = response.forecast();
                check(forecast.points.size() > 1, "Live forecast is empty");
                check(response.expiresAt > response.checkedAt, "Cache expiry must be respected");
                ForecastCache.Entry cached = client.fetch(59.9139, 10.7522);
                check(cached.checkedAt == response.checkedAt, "Second request should reuse unexpired cache");
                network = " Live MET HTTPS fetch, JSON parsing and expiry-cache reuse also passed.";
            }
            results.putString("stream", "\nPassed: four widget sizes, bitmap budget, RemoteViews inflation, missing-data rendering, settings launch and layout capture; padding bounds, custom/transparent backgrounds and colour/padding dialog edits."
                    + " Rain scale, threshold crossings, clipping, custom/disabled highlights, rainfall settings and time axis controls also passed. HTTP/cache integration, retained radar-outage data, aged graph/loading/error rendering, expedited quota fallback, queued-job promotion, disabled-background-location handling, settings navigation controls and private-data exclusion from diagnostics passed." + network + "\n");
            finish(Activity.RESULT_OK, results);
        } catch (Throwable e) {
            results.putString("stream", "\nFAILED: " + android.util.Log.getStackTraceString(e));
            finish(Activity.RESULT_CANCELED, results);
        }
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    private void checkTimeAxisDialog(Activity activity, Context context, WidgetSettings settings) {
        java.util.concurrent.atomic.AtomicReference<WidgetSettings> applied = new java.util.concurrent.atomic.AtomicReference<>();
        onMain(() -> {
            android.app.AlertDialog dialog = AppearanceDialogs.timeAxis(activity, settings, applied::set);
            android.view.View root = dialog.getWindow().getDecorView();
            android.widget.SeekBar size = find(root, android.widget.SeekBar.class);
            android.widget.CheckBox show = find(root, android.widget.CheckBox.class, activity.getString(R.string.show_time_labels));
            check(size != null && show != null, "Time label controls missing");
            size.setProgress(20);
            find(root, android.widget.CheckBox.class, activity.getString(R.string.show_time_axis)).setChecked(false);
            find(root, android.widget.CheckBox.class, activity.getString(R.string.show_time_ticks)).setChecked(false);
            show.setChecked(false);
            check(!size.isEnabled(), "Size slider should be disabled with hidden labels");
            show.setChecked(true);
            check(size.isEnabled(), "Size slider should be enabled with visible labels");
            capture(dialog, context, "tested-time-axis.png");
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick();
        });
        waitForIdleSync();
        check(applied.get() != null && applied.get().labelSize == 20 && applied.get().labels,
                "Apply must save label size and visibility");
        check(!applied.get().showAxis && !applied.get().showTicks, "Axis and ticks must be independently hideable");
        check(settings.labelSize == 11 && settings.labels, "Dialog must not mutate input settings");
        check(settings.showAxis && settings.showTicks, "Dialog must preserve the original axis settings");

        WidgetSettings curveOnly = applied.get().copy();
        curveOnly.labels = false;
        long now = System.currentTimeMillis();
        Bitmap bitmap = rainBitmap(curveOnly, now, 2, 2);
        int curvePixels = 0;
        for (int y = 0; y < bitmap.getHeight(); y++) {
            for (int x = 0; x < bitmap.getWidth(); x++) {
                if (y >= bitmap.getHeight() - 24)
                    check(android.graphics.Color.alpha(bitmap.getPixel(x, y)) == 0, "Hidden axis must not leave a line, ticks or numbers");
                if (bitmap.getPixel(x, y) == android.graphics.Color.WHITE) curvePixels++;
            }
        }
        check(curvePixels > 0, "Hiding the axis must preserve the rain curve");
        Bitmap dry = rainBitmap(curveOnly, now, 0, 0);
        boolean dryVisible = false;
        for (int y = dry.getHeight() - 8; y < dry.getHeight(); y++)
            for (int x = 0; x < dry.getWidth(); x++)
                dryVisible |= dry.getPixel(x, y) == android.graphics.Color.WHITE;
        check(dryVisible, "A dry forecast must remain a visible curve with the axis hidden");
        try (FileOutputStream output = new FileOutputStream(new File(context.getFilesDir(), "tested-curve-only.png"))) {
            ChartRenderer.bitmap(320, 100, 3f, curveOnly, Forecast.example(now), now, ForecastState.DATA)
                    .compress(Bitmap.CompressFormat.PNG, 100, output);
        } catch (java.io.IOException e) { throw new AssertionError(e); }
    }
    private static void checkRainfallRendering(long now) {
        WidgetSettings s = new WidgetSettings();
        s.follow = false;
        Bitmap rising = rainBitmap(s, now, 2, 4);
        rainPixel(rising, .5, 2.2, 3, android.graphics.Color.WHITE, "Rain below threshold must stay white");
        rainPixel(rising, 2, 2.8, 3, android.graphics.Color.RED, "Rising segment must switch colour at the threshold");
        rainPixel(rising, 4, 3, 3, android.graphics.Color.RED, "Rising segment must reach the ceiling at the correct time");
        Bitmap falling = rainBitmap(s, now, 4, 2);
        rainPixel(falling, 1, 3, 3, android.graphics.Color.RED, "Falling segment must stay capped until it crosses the ceiling");
        rainPixel(falling, 3, 2.8, 3, android.graphics.Color.RED, "Falling segment above threshold must stay red");
        rainPixel(falling, 4.5, 2.2, 3, android.graphics.Color.WHITE, "Falling segment must return to white below threshold");
        rainPixel(rainBitmap(s, now, 2.5, 2.5), 2.5, 2.5, 3, android.graphics.Color.RED,
                "Rain exactly at threshold must use the highlight colour");
        s.highlightRain = false;
        rainPixel(rainBitmap(s, now, 2.8, 2.8), 2.5, 2.8, 3, android.graphics.Color.WHITE,
                "Disabling highlights must keep strong rain white");
        s.highlightRain = true;
        s.highlightColor = android.graphics.Color.GREEN;
        rainPixel(rainBitmap(s, now, 2.8, 2.8), 2.5, 2.8, 3, android.graphics.Color.GREEN,
                "Custom highlight colour must be rendered");
        s.scaleMax = 6;
        rainPixel(rainBitmap(s, now, 3, 3), 2.5, 3, 6, android.graphics.Color.GREEN,
                "Custom ceiling must change the vertical scale");
        s.scaleMax = 3;
        s.highlightThreshold = 4;
        rainPixel(rainBitmap(s, now, 3.5, 3.5), 2.5, 3, 3, android.graphics.Color.WHITE,
                "Clipped rain below a higher threshold must stay white");
        rainPixel(rainBitmap(s, now, 4.5, 4.5), 2.5, 3, 3, android.graphics.Color.GREEN,
                "Highlight must use the actual rain rate even above the ceiling");
    }
    private static Bitmap rainBitmap(WidgetSettings settings, long now, double from, double to) {
        Forecast forecast = new Forecast(now, "ok", java.util.Arrays.asList(
                new Forecast.Point(now, from), new Forecast.Point(now + 5 * Forecast.MINUTE, to)));
        return ChartRenderer.bitmap(256, 132, 3f, settings, forecast, now, ForecastState.DATA);
    }
    private static void rainPixel(Bitmap bitmap, double minute, double rate, double ceiling, int colour, String message) {
        // This fixture has a 240 dp wide, 100 dp high plot with its baseline at y=109 dp.
        int x = (int) Math.round((8 + 2 * minute) * 3);
        int y = (int) Math.round((109 - 100 * rate / ceiling) * 3);
        for (int yy = y - 2; yy <= y + 2; yy++)
            for (int xx = x - 2; xx <= x + 2; xx++)
                if (bitmap.getPixel(xx, yy) == colour) return;
        throw new AssertionError(message);
    }

    private void checkRainfallDialogs(Activity activity, Context context, WidgetSettings settings) {
        java.util.concurrent.atomic.AtomicReference<WidgetSettings> applied = new java.util.concurrent.atomic.AtomicReference<>();
        onMain(() -> {
            android.app.AlertDialog dialog = AppearanceDialogs.rainfall(activity, settings, applied::set);
            android.view.View root = dialog.getWindow().getDecorView();
            android.widget.EditText ceiling = find(root, android.widget.EditText.class, activity.getString(R.string.graph_ceiling));
            android.widget.EditText threshold = find(root, android.widget.EditText.class, activity.getString(R.string.highlight_threshold));
            check(ceiling != null && threshold != null, "Rainfall numeric controls missing");
            ceiling.setText("0");
            check(!dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).isEnabled(), "Zero ceiling must be rejected");
            ceiling.setText("6");
            threshold.setText("101");
            check(!dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).isEnabled(), "Out-of-range threshold must be rejected");
            threshold.setText("1,5");
            check(dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).isEnabled(), "Decimal comma must be accepted");
            find(root, android.widget.CheckBox.class).setChecked(false);
            capture(dialog, context, "tested-rainfall.png");
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick();
        });
        waitForIdleSync();
        check(applied.get() != null && applied.get().scaleMax == 6 && applied.get().highlightThreshold == 1.5f
                && !applied.get().highlightRain, "Rainfall settings were not applied correctly");
        check(settings.scaleMax == 3 && settings.highlightThreshold == 2.5f && settings.highlightRain,
                "Rainfall dialog must not mutate original settings");
        applied.set(null);
        onMain(() -> {
            android.app.AlertDialog dialog = AppearanceDialogs.highlightColour(activity, settings, applied::set);
            android.widget.EditText hex = find(dialog.getWindow().getDecorView(), android.widget.EditText.class);
            hex.setText("#00FF00");
            capture(dialog, context, "tested-rain-colour.png");
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick();
        });
        waitForIdleSync();
        check(applied.get() != null && applied.get().highlightColor == android.graphics.Color.GREEN,
                "Highlight colour was not applied correctly");
        check(applied.get().backgroundColor == settings.backgroundColor && settings.highlightColor == android.graphics.Color.RED,
                "Highlight colour must not change the background or original settings");
    }
    private void checkAppearanceDialogs(Activity activity, Context context, WidgetSettings settings) {
        java.util.concurrent.atomic.AtomicReference<WidgetSettings> applied = new java.util.concurrent.atomic.AtomicReference<>();
        onMain(() -> {
            android.app.AlertDialog color = AppearanceDialogs.background(activity, settings, applied::set);
            android.widget.EditText hex = find(color.getWindow().getDecorView(), android.widget.EditText.class);
            check(hex != null, "Hex colour entry missing");
            hex.setText("#xx");
            check(!color.getButton(android.app.AlertDialog.BUTTON_POSITIVE).isEnabled(), "Invalid hex must not be applied");
            hex.setText("#803F244B");
            check(color.getButton(android.app.AlertDialog.BUTTON_POSITIVE).isEnabled(), "Valid ARGB hex must be accepted");
            capture(color, context, "tested-colour-picker.png");
            color.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick();
        });
        // AlertDialog posts button callbacks to the UI queue, rather than invoking them inline.
        waitForIdleSync();
        check(applied.get() != null && applied.get().backgroundColor == 0x803f244b, "Colour edit lost alpha or RGB");
        check(settings.backgroundColor == 0x00000000, "Dialog must not mutate input settings");
        applied.set(null);
        onMain(() -> {
            android.app.AlertDialog padding = AppearanceDialogs.padding(activity, settings, applied::set);
            android.widget.SeekBar left = find(padding.getWindow().getDecorView(), android.widget.SeekBar.class);
            check(left != null, "Padding slider missing");
            left.setProgress(32);
            capture(padding, context, "tested-padding.png");
            padding.getButton(android.app.AlertDialog.BUTTON_NEGATIVE).performClick();
        });
        waitForIdleSync();
        check(applied.get() == null && settings.paddingLeft == 8, "Cancel must keep existing padding");
    }
    private void onMain(Runnable action) {
        java.util.concurrent.atomic.AtomicReference<Throwable> failure = new java.util.concurrent.atomic.AtomicReference<>();
        runOnMainSync(() -> { try { action.run(); } catch (Throwable t) { failure.set(t); } });
        if (failure.get() != null) throw new AssertionError(failure.get());
    }
    private static void capture(android.app.AlertDialog dialog, Context context, String filename) {
        android.view.View root = dialog.getWindow().getDecorView();
        int width = Math.round(360 * context.getResources().getDisplayMetrics().density);
        int height = context.getResources().getDisplayMetrics().heightPixels;
        root.measure(android.view.View.MeasureSpec.makeMeasureSpec(width, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(height, android.view.View.MeasureSpec.AT_MOST));
        root.layout(0, 0, root.getMeasuredWidth(), root.getMeasuredHeight());
        Bitmap image = Bitmap.createBitmap(root.getWidth(), root.getHeight(), Bitmap.Config.ARGB_8888);
        root.draw(new android.graphics.Canvas(image));
        try (FileOutputStream output = new FileOutputStream(new File(context.getFilesDir(), filename))) {
            image.compress(Bitmap.CompressFormat.PNG, 100, output);
        } catch (java.io.IOException e) { throw new AssertionError(e); }
    }
    private static <T extends android.view.View> T find(android.view.View root, Class<T> type) {
        return find(root, type, null);
    }
    private static <T extends android.view.View> T find(android.view.View root, Class<T> type, String description) {
        if (type.isInstance(root) && (description == null || description.equals(root.getContentDescription())
                || (root instanceof android.widget.TextView && description.contentEquals(((android.widget.TextView) root).getText()))))
            return type.cast(root);
        if (root instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                T result = find(group.getChildAt(i), type, description);
                if (result != null) return result;
            }
        }
        return null;
    }
}
