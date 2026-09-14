package app.rainline;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/** Appearance edits stay local until Apply, so cancelling never changes a placed widget. */
public final class AppearanceDialogs {
    private AppearanceDialogs() {}

    public static AlertDialog timeAxis(Activity activity, WidgetSettings current, Consumer<WidgetSettings> apply) {
        WidgetSettings draft = current.copy();
        LinearLayout body = body(activity);
        label(body, activity.getString(R.string.preview_time_axis));
        View preview = preview(activity, draft);
        body.addView(preview, new LinearLayout.LayoutParams(-1, dp(activity, 120)));
        CheckBox axis = checkBox(body, R.string.show_time_axis, draft.showAxis);
        CheckBox ticks = checkBox(body, R.string.show_time_ticks, draft.showTicks);
        CheckBox show = checkBox(body, R.string.show_time_labels, draft.labels);
        axis.setOnCheckedChangeListener((button, checked) -> { draft.showAxis = checked; preview.invalidate(); });
        ticks.setOnCheckedChangeListener((button, checked) -> { draft.showTicks = checked; preview.invalidate(); });
        SeekBar size = slider(body, activity.getString(R.string.text_size), 24, draft.labelSize, "", value -> {
            draft.labelSize = value; preview.invalidate();
        });
        size.setMin(8);
        size.setEnabled(draft.labels);
        show.setOnCheckedChangeListener((button, checked) -> {
            draft.labels = checked; size.setEnabled(checked); preview.invalidate();
        });
        label(body, activity.getString(R.string.time_axis_help));
        AlertDialog dialog = new AlertDialog.Builder(activity).setTitle(activity.getString(R.string.time_axis))
                .setView(scroll(body)).setPositiveButton(activity.getString(R.string.apply), (d, which) -> apply.accept(draft))
                .setNegativeButton(activity.getString(R.string.cancel), null).setNeutralButton(activity.getString(R.string.reset), null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
            WidgetSettings defaults = new WidgetSettings();
            axis.setChecked(defaults.showAxis); ticks.setChecked(defaults.showTicks);
            show.setChecked(defaults.labels); size.setProgress(defaults.labelSize);
        }));
        dialog.show();
        return dialog;
    }

    private static CheckBox checkBox(LinearLayout parent, int label, boolean checked) {
        CheckBox box = new CheckBox(parent.getContext());
        box.setText(label);
        box.setContentDescription(parent.getContext().getString(label));
        box.setTextColor(Color.WHITE);
        box.setButtonTintList(ColorStateList.valueOf(Color.WHITE));
        box.setChecked(checked);
        parent.addView(box, new LinearLayout.LayoutParams(-1, -2));
        return box;
    }

    public static AlertDialog padding(Activity activity, WidgetSettings current, Consumer<WidgetSettings> apply) {
        WidgetSettings draft = current.copy();
        LinearLayout body = body(activity);
        label(body, activity.getString(R.string.preview_padding));
        View preview = preview(activity, draft);
        body.addView(preview, new LinearLayout.LayoutParams(-1, dp(activity, 120)));
        SeekBar left = slider(body, activity.getString(R.string.left), 48, draft.paddingLeft, "dp", value -> { draft.paddingLeft = value; preview.invalidate(); });
        SeekBar top = slider(body, activity.getString(R.string.top), 48, draft.paddingTop, "dp", value -> { draft.paddingTop = value; preview.invalidate(); });
        SeekBar right = slider(body, activity.getString(R.string.right), 48, draft.paddingRight, "dp", value -> { draft.paddingRight = value; preview.invalidate(); });
        SeekBar bottom = slider(body, activity.getString(R.string.bottom), 48, draft.paddingBottom, "dp", value -> { draft.paddingBottom = value; preview.invalidate(); });
        label(body, activity.getString(R.string.padding_help));
        AlertDialog dialog = new AlertDialog.Builder(activity).setTitle(activity.getString(R.string.content_padding))
                .setView(scroll(body)).setPositiveButton(activity.getString(R.string.apply), (d, which) -> apply.accept(draft))
                .setNegativeButton(activity.getString(R.string.cancel), null).setNeutralButton(activity.getString(R.string.reset), null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
            WidgetSettings defaults = new WidgetSettings();
            left.setProgress(defaults.paddingLeft); top.setProgress(defaults.paddingTop);
            right.setProgress(defaults.paddingRight); bottom.setProgress(defaults.paddingBottom);
        }));
        dialog.show();
        return dialog;
    }

    public static AlertDialog background(Activity activity, WidgetSettings current, Consumer<WidgetSettings> apply) {
        return colour(activity, current, false, apply);
    }

    public static AlertDialog highlightColour(Activity activity, WidgetSettings current, Consumer<WidgetSettings> apply) {
        return colour(activity, current, true, apply);
    }

    private static AlertDialog colour(Activity activity, WidgetSettings current, boolean highlight, Consumer<WidgetSettings> apply) {
        ColorEditor editor = new ColorEditor(activity, current.copy(), highlight);
        AlertDialog dialog = new AlertDialog.Builder(activity).setTitle(highlight ? activity.getString(R.string.highlight_colour) : activity.getString(R.string.background_colour))
                .setView(scroll(editor)).setPositiveButton(activity.getString(R.string.apply), (d, which) -> {
                    if (editor.valid) apply.accept(editor.draft);
                }).setNegativeButton(activity.getString(R.string.cancel), null).setNeutralButton(highlight ? activity.getString(R.string.red) : activity.getString(R.string.transparent), null).create();
        editor.dialog = dialog;
        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(editor.valid);
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v ->
                    editor.hex.setText(ColorValue.format(highlight ? Color.RED : Color.TRANSPARENT)));
        });
        dialog.show();
        return dialog;
    }

    public static AlertDialog rainfall(Activity activity, WidgetSettings current, Consumer<WidgetSettings> apply) {
        RainfallEditor editor = new RainfallEditor(activity, current.copy());
        AlertDialog dialog = new AlertDialog.Builder(activity).setTitle(activity.getString(R.string.rainfall_scale))
                .setView(scroll(editor)).setPositiveButton(activity.getString(R.string.apply), (d, which) -> {
                    if (editor.valid) apply.accept(editor.draft);
                }).setNegativeButton(activity.getString(R.string.cancel), null).setNeutralButton(activity.getString(R.string.reset), null).create();
        editor.dialog = dialog;
        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(editor.valid);
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                WidgetSettings defaults = new WidgetSettings();
                editor.ceiling.setText(UiText.rate(activity, defaults.scaleMax));
                editor.threshold.setText(UiText.rate(activity, defaults.highlightThreshold));
                editor.highlight.setChecked(defaults.highlightRain);
            });
        });
        dialog.show();
        return dialog;
    }

    private static final class RainfallEditor extends LinearLayout {
        final WidgetSettings draft;
        final View preview;
        final EditText ceiling, threshold;
        final CheckBox highlight;
        final TextView guides;
        AlertDialog dialog;
        boolean valid = true;

        RainfallEditor(Context context, WidgetSettings draft) {
            super(context);
            this.draft = draft;
            setOrientation(VERTICAL);
            setPadding(dp(context, 20), dp(context, 4), dp(context, 20), dp(context, 12));
            label(this, getContext().getString(R.string.preview_scale));
            preview = preview(context, draft);
            addView(preview, new LayoutParams(-1, dp(context, 120)));
            ceiling = rateEntry(getContext().getString(R.string.graph_ceiling), draft.scaleMax);
            guides = label(this, "");
            highlight = new CheckBox(context);
            highlight.setText(getContext().getString(R.string.highlight_enable));
            highlight.setTextColor(Color.WHITE);
            highlight.setButtonTintList(ColorStateList.valueOf(Color.WHITE));
            highlight.setChecked(draft.highlightRain);
            addView(highlight, new LayoutParams(-1, -2));
            threshold = rateEntry(getContext().getString(R.string.highlight_threshold), draft.highlightThreshold);
            label(this, getContext().getString(R.string.rainfall_help));
            TextWatcher watcher = new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) { update(); }
                @Override public void afterTextChanged(Editable s) { }
            };
            ceiling.addTextChangedListener(watcher);
            threshold.addTextChangedListener(watcher);
            highlight.setOnCheckedChangeListener((button, checked) -> update());
            update();
        }

        private EditText rateEntry(String title, float initial) {
            label(this, title);
            EditText entry = new EditText(getContext());
            entry.setSingleLine(true);
            entry.setSelectAllOnFocus(true);
            entry.setTextColor(Color.WHITE);
            entry.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            entry.setKeyListener(android.text.method.DigitsKeyListener.getInstance("0123456789.,"));
            entry.setRawInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            entry.setContentDescription(title);
            entry.setText(UiText.rate(getContext(), initial));
            addView(entry, new LayoutParams(-1, dp(getContext(), 52)));
            return entry;
        }

        private double rate(EditText entry) {
            double value;
            try { value = Double.parseDouble(entry.getText().toString().trim().replace(',', '.')); }
            catch (NumberFormatException e) { value = Double.NaN; }
            entry.setError(WidgetSettings.validRainRate(value) ? null : getContext().getString(R.string.rate_invalid));
            return value;
        }

        private void update() {
            double max = rate(ceiling), from = rate(threshold);
            valid = WidgetSettings.validRainRate(max) && WidgetSettings.validRainRate(from);
            if (valid) {
                draft.scaleMax = (float) max;
                draft.highlightThreshold = (float) from;
                draft.highlightRain = highlight.isChecked();
                guides.setText(getContext().getString(R.string.guides_summary, UiText.rate(getContext(), max / 3), UiText.rate(getContext(), max * 2 / 3)));
                preview.invalidate();
            }
            if (dialog != null && dialog.isShowing()) dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(valid);
        }
    }


    private static final class ColorEditor extends LinearLayout {
        final WidgetSettings draft;
        final boolean highlight;
        final EditText hex;
        final ColorPickerView plane;
        final SeekBar hue, opacity;
        final View preview;
        AlertDialog dialog;
        boolean syncing, valid = true;

        ColorEditor(Context context, WidgetSettings draft, boolean highlight) {
            super(context);
            this.draft = draft;
            this.highlight = highlight;
            setOrientation(VERTICAL);
            setPadding(dp(context, 20), dp(context, 4), dp(context, 20), dp(context, 12));
            label(this, highlight ? getContext().getString(R.string.preview_highlight) : getContext().getString(R.string.preview_background));
            preview = preview(context, draft, highlight);
            addView(preview, new LayoutParams(-1, dp(context, 100)));
            plane = new ColorPickerView(context);
            plane.setColor(colour());
            addView(plane, new LayoutParams(-1, dp(context, 158)));
            hue = slider(this, getContext().getString(R.string.hue), 360, Math.round(plane.hue()), "°", value -> {
                if (!syncing) plane.setHue(value);
            });
            GradientDrawable rainbow = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                    new int[]{Color.RED, Color.YELLOW, Color.GREEN, Color.CYAN, Color.BLUE, Color.MAGENTA, Color.RED});
            rainbow.setCornerRadius(dp(context, 4));
            hue.setProgressTintList(null);
            hue.setProgressDrawable(rainbow);
            hue.setSplitTrack(false);
            opacity = highlight ? null : slider(this, getContext().getString(R.string.opacity), 255, colour() >>> 24, "%", value -> {
                if (!syncing) {
                    setColour((value << 24) | (colour() & 0xffffff));
                    syncText();
                }
            });
            label(this, highlight ? getContext().getString(R.string.hex_opaque) : getContext().getString(R.string.hex_alpha));
            hex = new EditText(context);
            hex.setSingleLine(true);
            hex.setTextColor(Color.WHITE);
            hex.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            hex.setFilters(new InputFilter[]{new InputFilter.LengthFilter(highlight ? 7 : 9)});
            hex.setSelectAllOnFocus(true);
            hex.setContentDescription(highlight ? getContext().getString(R.string.hex_highlight_description)
                    : getContext().getString(R.string.hex_background_description));
            hex.setText(ColorValue.format(colour()));
            addView(hex, new LayoutParams(-1, dp(context, 52)));
            label(this, highlight ? getContext().getString(R.string.highlight_preview_help)
                    : getContext().getString(R.string.alpha_help));
            plane.setOnColorChanged(color -> {
                setColour((colour() & 0xff000000) | (color & 0xffffff));
                syncText();
            });
            hex.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    if (syncing) return;
                    try {
                        setColour(ColorValue.parse(s.toString()));
                        valid = true;
                        syncing = true;
                        plane.setColor(colour());
                        hue.setProgress(Math.round(plane.hue()));
                        if (opacity != null) opacity.setProgress(colour() >>> 24);
                        preview.invalidate();
                    } catch (IllegalArgumentException ignored) { valid = false; }
                    finally { syncing = false; }
                    if (dialog != null && dialog.isShowing())
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(valid);
                }
                @Override public void afterTextChanged(Editable s) { }
            });
        }
        private int colour() { return highlight ? draft.highlightColor : draft.backgroundColor; }
        private void setColour(int colour) {
            if (highlight) draft.highlightColor = colour | 0xff000000;
            else draft.backgroundColor = colour;
        }
        private void syncText() {
            syncing = true;
            hex.setText(ColorValue.format(colour()));
            hex.setSelection(hex.length());
            syncing = false;
            valid = true;
            if (dialog != null && dialog.isShowing()) dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
            preview.invalidate();
        }
    }

    private static LinearLayout body(Context context) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(context, 20), dp(context, 4), dp(context, 20), dp(context, 12));
        return layout;
    }
    private static ScrollView scroll(LinearLayout body) {
        ScrollView scroll = new ScrollView(body.getContext());
        scroll.addView(body);
        return scroll;
    }
    private static TextView label(LinearLayout parent, String title) {
        TextView view = new TextView(parent.getContext());
        view.setTextColor(Color.WHITE); view.setTextSize(12); view.setText(title);
        view.setPadding(0, dp(parent.getContext(), 8), 0, dp(parent.getContext(), 2));
        parent.addView(view, new LinearLayout.LayoutParams(-1, -2));
        return view;
    }
    private static SeekBar slider(LinearLayout parent, String title, int max, int initial, String unit, IntConsumer change) {
        TextView description = label(parent, sliderLabel(parent.getContext(), title, initial, unit));
        SeekBar bar = new SeekBar(parent.getContext());
        bar.setMax(max); bar.setProgress(initial);
        bar.setContentDescription(title);
        bar.setThumbTintList(ColorStateList.valueOf(Color.WHITE));
        bar.setProgressTintList(ColorStateList.valueOf(Color.WHITE));
        parent.addView(bar, new LinearLayout.LayoutParams(-1, dp(parent.getContext(), 40)));
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                description.setText(sliderLabel(parent.getContext(), title, progress, unit));
                change.accept(progress);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });
        return bar;
    }
    private static String sliderLabel(Context context, String title, int value, String unit) {
        return String.format(UiText.locale(context), "%s · %d %s", title,
                "%".equals(unit) ? Math.round(value * 100f / 255) : value, unit).trim();
    }
    private static View preview(Context context, WidgetSettings draft) {
        return preview(context, draft, false);
    }
    private static View preview(Context context, WidgetSettings draft, boolean highlightPreview) {
        return new View(context) {
            @Override protected void onDraw(Canvas canvas) {
                float density = getResources().getDisplayMetrics().density;
                if ((draft.backgroundColor >>> 24) < 255) {
                    Paint tiles = new Paint();
                    float cell = 10 * density;
                    for (int y = 0; y * cell < getHeight(); y++) {
                        for (int x = 0; x * cell < getWidth(); x++) {
                            tiles.setColor((x + y) % 2 == 0 ? 0xff222222 : 0xff444444);
                            canvas.drawRect(x * cell, y * cell, (x + 1) * cell, (y + 1) * cell, tiles);
                        }
                    }
                }
                long now = System.currentTimeMillis();
                WidgetSettings sample = draft.copy();
                sample.follow = false;
                if (highlightPreview) {
                    sample.highlightRain = true;
                    sample.scaleMax = 3f;
                    sample.highlightThreshold = 2.5f;
                }
                ChartRenderer.draw(canvas, getWidth(), getHeight(), density, sample, Forecast.example(now), now, ForecastState.DATA);
            }
        };
    }
    private static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
