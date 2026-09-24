package app.rainline;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import java.util.function.Consumer;

/** Three compact, keyboard- and screen-reader-accessible appearance choices. */
@android.annotation.SuppressLint("ViewConstructor") // Created only by the settings screen, never inflated from XML.
final class ThemePicker extends RadioGroup {
    ThemePicker(Context context, WidgetSettings current, Consumer<WidgetTheme> select) {
        super(context);
        setOrientation(HORIZONTAL);
        for (WidgetTheme theme : WidgetTheme.values()) {
            RadioButton button = new RadioButton(context) {
                @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
                    super.onSizeChanged(width, height, oldWidth, oldHeight);
                    if (width <= getPaddingLeft() + getPaddingRight() || width == oldWidth) return;
                    float density = getResources().getDisplayMetrics().density;
                    int chartWidth = width - getPaddingLeft() - getPaddingRight();
                    WidgetSettings sample = new WidgetSettings();
                    theme.applyTo(sample);
                    // Small swatches use tighter spacing; the main preview shows actual padding.
                    sample.paddingLeft = sample.paddingRight = 5;
                    sample.paddingTop = 4; sample.paddingBottom = 2;
                    sample.labelSize = 8;
                    sample.cornerRadius = Math.min(6, sample.cornerRadius);
                    long now = System.currentTimeMillis();
                    Bitmap bitmap = ChartRenderer.bitmap(Math.max(1, Math.round(chartWidth / density)),
                            46, density, sample, Forecast.example(now), now, ForecastState.DATA);
                    BitmapDrawable drawable = new BitmapDrawable(getResources(), bitmap);
                    drawable.setBounds(0, 0, chartWidth, dp(46));
                    setCompoundDrawables(null, drawable, null, null);
                }
            };
            button.setId(View.generateViewId());
            button.setText(name(theme));
            button.setTextSize(12);
            button.setTextColor(Color.WHITE);
            button.setAllCaps(false);
            button.setMaxLines(2);
            button.setButtonDrawable(null);
            button.setGravity(Gravity.CENTER);
            button.setPadding(dp(6), dp(8), dp(6), dp(8));
            button.setCompoundDrawablePadding(dp(5));
            button.setMinWidth(0);
            button.setMinimumWidth(0);
            button.setMinimumHeight(dp(86));
            button.setContentDescription(context.getString(description(theme)));
            boolean checked = theme.matches(current);
            GradientDrawable background = new GradientDrawable();
            background.setColor(0xff101318);
            background.setCornerRadius(dp(10));
            background.setStroke(dp(checked ? 2 : 1), checked ? 0xff82c8ff : 0xff59616b);
            button.setBackground(background);
            LayoutParams params = new LayoutParams(0, LayoutParams.MATCH_PARENT, 1);
            if (theme != WidgetTheme.LIGHT) params.leftMargin = dp(8);
            addView(button, params);
            button.setChecked(checked);
            // Also allows reapplying a preset after manual adjustments.
            button.setOnClickListener(view -> select.accept(theme));
        }
    }

    static int name(WidgetTheme theme) {
        return switch (theme) {
            case LIGHT -> R.string.theme_light;
            case DARK -> R.string.theme_dark;
            case MINIMAL -> R.string.theme_minimal;
        };
    }
    private static int description(WidgetTheme theme) {
        return switch (theme) {
            case LIGHT -> R.string.theme_light_description;
            case DARK -> R.string.theme_dark_description;
            case MINIMAL -> R.string.theme_minimal_description;
        };
    }
    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
