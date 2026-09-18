package app.rainline;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Instrumentation;
import android.app.LocaleManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.LocaleList;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Locale;

/** Emulator-only store asset export. Uses actual app windows and their labelled sample previews.
 * No weather requests, location permissions, seeded forecasts or production screenshot mode. */
public final class StoreScreenshotInstrumentation {
    private final Instrumentation test;
    private StoreScreenshotInstrumentation(Instrumentation test) { this.test = test; }
    static void run(Instrumentation test) throws Exception {
        new StoreScreenshotInstrumentation(test).export();
    }

    private void export() throws Exception {
        Context context = test.getTargetContext();
        require(Build.VERSION.SDK_INT >= 33 && Build.MODEL.startsWith("sdk_gphone"), "Use an Android 13+ emulator");
        require(Updates.widgetIds(context).length == 0 && !new SettingsStore(context).get(0).hasLocation(),
                "Use an emulator without Rainline widgets or a selected location");
        LocaleManager manager = context.getSystemService(LocaleManager.class);
        LocaleList previousLocale = manager.getApplicationLocales();
        SettingsStore store = new SettingsStore(context);
        boolean hadDefaults = store.contains(0);
        WidgetSettings previousDefaults = store.get(0);
        Activity[] activity = {null};
        AlertDialog[] dialog = {null};
        String[][] languages = {
            {"en", "en-GB", "A minimal rain forecast"},
            {"nb", "no-NO", "Et minimalistisk regnvarsel"},
            {"sv", "sv-SE", "En minimalistisk regnprognos"},
            {"fi", "fi-FI", "Minimalistinen sade-ennuste"},
            {"da", "da-DK", "En minimalistisk regnprognose"}
        };
        try {
            test.runOnMainSync(() -> store.put(0, new WidgetSettings()));
            for (String[] language : languages) {
                test.runOnMainSync(() -> manager.setApplicationLocales(LocaleList.forLanguageTags(language[0])));
                test.waitForIdleSync();
                Activity current = test.startActivitySync(new Intent(context, SettingsActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                activity[0] = current;
                settle(); // Wait for window focus before requesting immersive mode.
                test.runOnMainSync(() -> {
                    immersive(current.getWindow());
                    findScroll(current.findViewById(android.R.id.content)).setVerticalScrollBarEnabled(false);
                });
                settle();
                captureWindow(context, current, language[1] + "/01-forecast.png");
                test.runOnMainSync(() -> {
                    View root = current.findViewById(android.R.id.content);
                    TextView heading = findText(root, current.getString(R.string.appearance).toUpperCase(Locale.ROOT));
                    require(heading != null, "Missing appearance section");
                    ScrollView scroll = findScroll(root);
                    scroll.scrollTo(0, heading.getTop());
                });
                settle();
                captureWindow(context, current, language[1] + "/02-appearance.png");
                test.runOnMainSync(() -> {
                    dialog[0] = AppearanceDialogs.rainfall(current, store.get(0), ignored -> {});
                    immersive(dialog[0].getWindow());
                });
                settle();
                capture(context, language[1] + "/03-rainfall.png");
                test.runOnMainSync(() -> {
                    dialog[0].dismiss();
                    dialog[0] = AppearanceDialogs.timeAxis(current, store.get(0), ignored -> {});
                    immersive(dialog[0].getWindow());
                });
                settle();
                capture(context, language[1] + "/04-time-axis.png");
                test.runOnMainSync(() -> {
                    dialog[0].dismiss(); dialog[0] = null;
                    feature(context, language[1], language[2]);
                    current.finish();
                });
                activity[0] = null;
                test.waitForIdleSync();
            }
            test.runOnMainSync(() -> {
                Bitmap icon = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(icon);
                canvas.drawColor(Color.BLACK);
                Drawable mark = context.getDrawable(R.drawable.ic_rainline);
                mark.setBounds(0, 0, 512, 512); mark.draw(canvas);
                save(context, icon, "icon.png", true);
                icon.recycle();
            });
        } finally {
            test.runOnMainSync(() -> {
                if (dialog[0] != null) dialog[0].dismiss();
                if (activity[0] != null) activity[0].finish();
                if (hadDefaults) store.put(0, previousDefaults); else store.delete(0);
                manager.setApplicationLocales(previousLocale);
            });
            test.waitForIdleSync();
        }
    }

    private void settle() throws InterruptedException {
        test.waitForIdleSync();
        Thread.sleep(400); // Complete native window animations before capturing pixels.
        test.waitForIdleSync();
    }
    private static void immersive(Window window) {
        window.getInsetsController().hide(WindowInsets.Type.systemBars());
    }
    private void captureWindow(Context context, Activity activity, String name) {
        test.runOnMainSync(() -> {
            // Capture the actual laid-out app window, excluding System UI's notification icons.
            View view = activity.getWindow().getDecorView();
            require(view.getWidth() == 1080 && view.getHeight() == 1920, "Unexpected window size");
            Bitmap bitmap = Bitmap.createBitmap(1080, 1920, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            canvas.drawColor(Color.BLACK);
            view.draw(canvas);
            save(context, bitmap, name, false);
            bitmap.recycle();
        });
    }
    private void capture(Context context, String name) {
        Bitmap bitmap = test.getUiAutomation().takeScreenshot();
        require(bitmap != null && bitmap.getWidth() == 1080 && bitmap.getHeight() == 1920,
                "Set the emulator display to portrait 1080x1920 first");
        save(context, bitmap, name, false);
        bitmap.recycle();
    }
    private static void feature(Context context, String language, String subtitle) {
        Bitmap bitmap = Bitmap.createBitmap(1024, 500, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(Color.BLACK);
        Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        text.setColor(Color.WHITE); text.setTextAlign(Paint.Align.CENTER);
        text.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        text.setTextSize(68); canvas.drawText("Rainline", 512, 128, text);
        text.setTextSize(26); canvas.drawText(subtitle, 512, 180, text);
        WidgetSettings settings = new WidgetSettings();
        settings.highlightRain = false;
        long now = System.currentTimeMillis();
        canvas.save(); canvas.translate(160, 230);
        ChartRenderer.draw(canvas, 704, 180, 2f, settings, Forecast.example(now), now, ForecastState.DATA);
        canvas.restore();
        save(context, bitmap, language + "/feature-graphic.png", false);
        bitmap.recycle();
    }
    private static void save(Context context, Bitmap bitmap, String name, boolean alpha) {
        File target = new File(context.getFilesDir(), "store-captures/" + name);
        require(target.getParentFile().isDirectory() || target.getParentFile().mkdirs(), "Cannot create asset directory");
        bitmap.setHasAlpha(alpha);
        try (FileOutputStream out = new FileOutputStream(target)) {
            require(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out), "PNG export failed");
        } catch (java.io.IOException error) { throw new IllegalStateException(error); }
    }
    private static TextView findText(View view, String text) {
        if (view instanceof TextView label && text.contentEquals(label.getText())) return label;
        if (view instanceof ViewGroup group) for (int i = 0; i < group.getChildCount(); i++) {
            TextView found = findText(group.getChildAt(i), text);
            if (found != null) return found;
        }
        return null;
    }
    private static ScrollView findScroll(View view) {
        if (view instanceof ScrollView scroll) return scroll;
        if (view instanceof ViewGroup group) for (int i = 0; i < group.getChildCount(); i++) {
            ScrollView found = findScroll(group.getChildAt(i));
            if (found != null) return found;
        }
        return null;
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
