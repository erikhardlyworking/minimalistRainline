package app.rainline;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Instrumentation;
import android.app.LocaleManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Build;
import android.os.LocaleList;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Real per-app language changes, restored afterwards; emulator only, without weather requests. */
final class LocalizationChecks {
    static void run(Instrumentation test, Context context) throws Exception {
        check(Build.VERSION.SDK_INT >= 33 && Build.MODEL.startsWith("sdk_gphone"), "Use an Android 13+ emulator");
        check(Updates.widgetIds(context).length == 0 && !new SettingsStore(context).get(0).hasLocation(),
                "Use an emulator without widgets or a configured location");
        LocaleManager manager = context.getSystemService(LocaleManager.class);
        LocaleList previous = manager.getApplicationLocales();
        Activity[] current = {null};
        try {
            for (String tag : new String[]{"en", "nb", "sv", "fi", "da"}) {
                test.runOnMainSync(() -> manager.setApplicationLocales(LocaleList.forLanguageTags(tag)));
                test.waitForIdleSync();
                Activity activity = test.startActivitySync(new Intent(context, SettingsActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                current[0] = activity;
                test.waitForIdleSync();
                check(UiText.locale(activity).getLanguage().equals(tag), "Activity did not switch to " + tag);
                validateResources(activity);
                if (!tag.equals("en")) {
                    check(UiText.rate(activity, 2.5).equals("2,5"), "Decimal comma missing for " + tag);
                    String coordinates = UiText.coordinates(activity, 59.9139, 10.7522);
                    double[] parsed = ForecastWindow.parseCoordinates(coordinates);
                    check(parsed != null && parsed[0] == 59.9139 && parsed[1] == 10.7522,
                            "Displayed coordinates do not round-trip for " + tag);
                }
                WidgetSettings saved = new WidgetSettings();
                saved.place = "Current location";
                check(UiText.place(activity, saved).equals(activity.getString(R.string.current_location)),
                        "Stored English location label leaked after language change");
                saved.follow = false; saved.place = "Espoo";
                check(UiText.place(activity, saved).equals("Espoo"), "A saved place name was translated");
                check(!UiText.issue(activity, UpdateIssue.LOCATION_STALE).isEmpty(), "Stored error not localized");
                long now = System.currentTimeMillis();
                check(UiText.ago(activity, now - 5 * Forecast.MINUTE, now)
                        .equals(activity.getString(R.string.time_minutes_ago, 5)), "Elapsed time not localized");

                test.runOnMainSync(() -> activity.getWindow().setLayout(dp(activity, 360), ViewGroup.LayoutParams.MATCH_PARENT));
                Thread.sleep(100); // Let the resized window complete its first frame.
                test.waitForIdleSync();
                test.runOnMainSync(() -> {
                    View view = activity.findViewById(android.R.id.content);
                    check(find(view, activity.getString(R.string.widget_settings)) != null, "Settings title missing");
                    check(find(view, activity.getString(R.string.open_yr)) != null, "Yr action missing");
                    check(find(view, activity.getString(R.string.language)) != null, "Language setting missing");
                    checkTextFits(view);
                    capture(activity, view, "tested-locale-" + tag + ".png");
                });
                AlertDialog[] currentDialog = {null};
                test.runOnMainSync(() -> {
                    AlertDialog dialog = AppearanceDialogs.rainfall(activity, new WidgetSettings(), draft -> {
                        check(draft.scaleMax == 3.5f && draft.highlightThreshold == 1.25f,
                                "Decimal-comma edits did not apply correctly");
                    });
                    currentDialog[0] = dialog;
                    dialog.getWindow().setLayout(dp(activity, 360), ViewGroup.LayoutParams.WRAP_CONTENT);
                    EditText ceiling = find(dialog.getWindow().getDecorView(), activity.getString(R.string.graph_ceiling), EditText.class);
                    EditText threshold = find(dialog.getWindow().getDecorView(), activity.getString(R.string.highlight_threshold), EditText.class);
                    check(ceiling != null && threshold != null, "Localized rainfall entries are missing");
                    ceiling.setText("3,5"); threshold.setText("1,25");
                    check(dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled(), "Decimal commas were rejected");
                });
                Thread.sleep(100);
                test.waitForIdleSync();
                test.runOnMainSync(() -> {
                    AlertDialog dialog = currentDialog[0];
                    capture(activity, dialog.getWindow().getDecorView(), "tested-locale-" + tag + "-rainfall.png");
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
                });
                test.runOnMainSync(activity::finish);
                test.waitForIdleSync();
                current[0] = null;
            }
            Configuration fallback = new Configuration(context.getResources().getConfiguration());
            fallback.setLocales(LocaleList.forLanguageTags("fr"));
            check(context.createConfigurationContext(fallback).getString(R.string.open_yr).equals("Open Yr"),
                    "Unsupported languages must fall back to English");
        } finally {
            test.runOnMainSync(() -> {
                if (current[0] != null) current[0].finish();
                manager.setApplicationLocales(previous);
            });
            test.waitForIdleSync();
        }
    }
    private static void validateResources(Context context) throws Exception {
        Pattern pattern = Pattern.compile("%(\\d+)\\$[.\\d]*([sdf])");
        for (Field field : R.string.class.getFields()) {
            int id = field.getInt(null);
            String value = context.getString(id);
            Matcher matcher = pattern.matcher(value);
            Object[] args = new Object[4];
            while (matcher.find()) {
                int index = Integer.parseInt(matcher.group(1)) - 1;
                args[index] = switch (matcher.group(2)) {
                    case "d" -> 5;
                    case "f" -> 2.5;
                    default -> "example";
                };
            }
            check(!context.getString(id, args).isEmpty(), "Empty localized string: " + field.getName());
        }
    }
    private static View find(View view, String text) {
        return find(view, text, View.class);
    }
    private static <T extends View> T find(View view, String text, Class<T> type) {
        if (type.isInstance(view) && ((view instanceof TextView && text.contentEquals(((TextView) view).getText()))
                || text.contentEquals(view.getContentDescription() == null ? "" : view.getContentDescription()))) return type.cast(view);
        if (view instanceof ViewGroup group) for (int i = 0; i < group.getChildCount(); i++) {
            T found = find(group.getChildAt(i), text, type);
            if (found != null) return found;
        }
        return null;
    }
    private static void checkTextFits(View view) {
        if (view instanceof TextView text && text.getLayout() != null) {
            for (int line = 0; line < text.getLineCount(); line++)
                check(text.getLayout().getEllipsisCount(line) == 0, "Truncated label: " + text.getText());
            check(text.getLayout().getHeight() <= text.getHeight() - text.getCompoundPaddingTop() - text.getCompoundPaddingBottom(),
                    "Clipped label: " + text.getText());
        }
        if (view instanceof ViewGroup group) for (int i = 0; i < group.getChildCount(); i++) checkTextFits(group.getChildAt(i));
    }
    private static void capture(Context context, View view, String name) {
        check(view.getWidth() > 0 && view.getHeight() > 0, "Cannot capture an unlaid-out view");
        Bitmap bitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(bitmap));
        try (FileOutputStream output = new FileOutputStream(new File(context.getFilesDir(), name))) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output);
        } catch (java.io.IOException e) { throw new AssertionError(e); }
        finally { bitmap.recycle(); }
    }
    private static int dp(Context context, int value) { return Math.round(value * context.getResources().getDisplayMetrics().density); }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
