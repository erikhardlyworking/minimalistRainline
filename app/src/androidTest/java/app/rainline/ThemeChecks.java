package app.rainline;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioButton;
import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.atomic.AtomicReference;

final class ThemeChecks {
    static void run(Instrumentation runner, Context context, Activity activity) throws Exception {
        long now = System.currentTimeMillis();
        Bitmap sheet = Bitmap.createBitmap(1080, 930, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(sheet);
        canvas.drawColor(0xff465266);
        Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        text.setColor(Color.WHITE); text.setTextSize(30);
        int index = 0;
        for (WidgetTheme theme : WidgetTheme.values()) {
            WidgetSettings settings = new WidgetSettings();
            theme.applyTo(settings);
            for (int[] size : new int[][]{{130, 64}, {240, 110}, {400, 100}, {180, 240}}) {
                for (ForecastState state : ForecastState.values()) {
                    Bitmap chart = ChartRenderer.bitmap(size[0], size[1], 3, settings, Forecast.example(now), now, state);
                    check(chart.getAllocationByteCount() <= 1_800_000, "Theme exceeds bitmap budget");
                    check(Color.alpha(chart.getPixel(0, 0)) == 0, "Rounded corner/transparent padding must remain clear");
                    check(count(chart, settings.foregroundColor) > 5, "Theme axes or status icon disappeared");
                    if (state == ForecastState.DATA) check(count(chart, settings.rainColor) > 5, "Theme rain line disappeared");
                    chart.recycle();
                }
            }
            canvas.drawText(context.getString(ThemePicker.name(theme)), 36, index * 310 + 48, text);
            Bitmap chart = ChartRenderer.bitmap(336, 76, 3, settings, Forecast.example(now), now, ForecastState.DATA);
            canvas.drawBitmap(chart, 36, index * 310 + 65, null);
            chart.recycle();
            index++;
        }
        write(sheet, context, "tested-themes.png");
        sheet.recycle();

        SettingsStore store = new SettingsStore(context);
        int[] ids = Updates.widgetIds(context);
        int id = ids.length == 0 ? 0 : ids[0];
        WidgetSettings before = store.get(id);
        try {
            for (WidgetTheme theme : WidgetTheme.values()) {
                main(runner, () -> {
                    View root = activity.findViewById(android.R.id.content);
                    RadioButton button = find(root, RadioButton.class, activity.getString(ThemePicker.name(theme)));
                    check(button != null, "Missing theme choice");
                    button.performClick();
                });
                runner.waitForIdleSync();
                check(theme.matches(store.get(id)), "Theme choice did not persist");
                main(runner, () -> {
                    View root = activity.findViewById(android.R.id.content);
                    int top = -1, selected = 0;
                    for (WidgetTheme other : WidgetTheme.values()) {
                        RadioButton button = find(root, RadioButton.class, activity.getString(ThemePicker.name(other)));
                        check(button.getWidth() >= 48 * context.getResources().getDisplayMetrics().density,
                                "Theme touch target is too small");
                        if (top < 0) top = button.getTop();
                        check(top == button.getTop(), "Themes must stay in one row");
                        check(button.getCompoundDrawables()[1] != null, "Theme thumbnail is missing");
                        if (button.isChecked()) selected++;
                        check(button.isChecked() == (other == theme), "Selected theme is incorrect");
                    }
                    check(selected == 1, "Exactly one matching theme must be selected");
                });
                if (theme == WidgetTheme.LIGHT) {
                    main(runner, () -> {
                        View root = activity.findViewById(android.R.id.content);
                        Bitmap screenshot = Bitmap.createBitmap(root.getWidth(), root.getHeight(), Bitmap.Config.ARGB_8888);
                        root.draw(new Canvas(screenshot));
                        try { write(screenshot, context, "tested-theme-settings.png"); }
                        catch (Exception e) { throw new AssertionError(e); }
                        screenshot.recycle();
                    });
                }
            }
            for (boolean rain : new boolean[]{true, false}) {
                AtomicReference<WidgetSettings> applied = new AtomicReference<>();
                main(runner, () -> {
                    android.app.AlertDialog dialog = rain ? AppearanceDialogs.rainColour(activity, before, applied::set)
                            : AppearanceDialogs.axisColour(activity, before, applied::set);
                    find(dialog.getWindow().getDecorView(), android.widget.EditText.class, null).setText("#123456");
                    dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick();
                });
                runner.waitForIdleSync();
                check(applied.get() != null, "Colour dialog did not apply");
                check((rain ? applied.get().rainColor : applied.get().foregroundColor) == 0xff123456, "Wrong colour saved");
                check(applied.get().backgroundColor == before.backgroundColor, "Colour edit changed the background");
            }
        } finally {
            main(runner, () -> { store.put(id, before); RainWidgetProvider.renderAll(context); });
        }
    }
    private static int count(Bitmap bitmap, int colour) {
        int count = 0;
        for (int y = 0; y < bitmap.getHeight(); y++) for (int x = 0; x < bitmap.getWidth(); x++)
            if (bitmap.getPixel(x, y) == colour) count++;
        return count;
    }
    private static void write(Bitmap bitmap, Context context, String name) throws Exception {
        try (FileOutputStream out = new FileOutputStream(new File(context.getFilesDir(), name))) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
    }
    private static void main(Instrumentation runner, Runnable action) {
        AtomicReference<Throwable> error = new AtomicReference<>();
        runner.runOnMainSync(() -> { try { action.run(); } catch (Throwable t) { error.set(t); } });
        if (error.get() != null) throw new AssertionError(error.get());
    }
    private static <T extends View> T find(View root, Class<T> type, String label) {
        if (type.isInstance(root) && (label == null || root instanceof android.widget.TextView
                && label.contentEquals(((android.widget.TextView) root).getText()))) return type.cast(root);
        if (root instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) {
                T match = find(group.getChildAt(i), type, label);
                if (match != null) return match;
            }
        }
        return null;
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
