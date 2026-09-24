package app.rainline;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import java.util.List;

public final class ChartRenderer {
    private ChartRenderer() {}

    public static Bitmap bitmap(int widthDp, int heightDp, float density,
                                WidgetSettings settings, Forecast forecast, long now, ForecastState state) {
        float scale = Math.min(density, Math.min(900f / widthDp, 500f / heightDp));
        int width = Math.max(1, Math.round(widthDp * scale));
        int height = Math.max(1, Math.round(heightDp * scale));
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        draw(new Canvas(bitmap), width, height, scale, settings, forecast, now, state);
        return bitmap;
    }

    public static void draw(Canvas canvas, int width, int height, float dp, WidgetSettings settings,
                            Forecast forecast, long now, ForecastState state) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(settings.backgroundColor);
        float cornerRadius = Math.min(settings.cornerRadius * dp, Math.min(width, height) / 2f);
        canvas.drawRoundRect(0, 0, width, height, cornerRadius, cornerRadius, p);
        p.setStrokeCap(Paint.Cap.ROUND);
        float left = settings.paddingLeft * dp, rightPadding = settings.paddingRight * dp;
        // Large user-selected padding must not erase a small widget or overlap its labels.
        float availableHorizontalPadding = Math.max(0, width - 72 * dp);
        float horizontalScale = left + rightPadding > availableHorizontalPadding
                ? availableHorizontalPadding / (left + rightPadding) : 1;
        left *= horizontalScale;
        float right = width - rightPadding * horizontalScale;
        p.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        p.setTextSize(settings.labelSize * dp);
        float labelWidth = Math.max(p.measureText("30"), Math.max(p.measureText("60"), p.measureText("90")));
        float labelSpace = Math.max(dp, (right - left) / 4 - 4 * dp);
        if (labelWidth > labelSpace) p.setTextSize(p.getTextSize() * labelSpace / labelWidth);
        float labelOffset = (settings.showTicks ? 8 : 2) * dp + p.getTextSize();
        float top = settings.paddingTop * dp, bottom = settings.paddingBottom * dp;
        float footer = settings.labels ? labelOffset + Math.max(3 * dp, p.descent()) : settings.showTicks ? 7 * dp : 0;
        float availableVerticalPadding = Math.max(0, height - footer - 16 * dp);
        float verticalScale = top + bottom > availableVerticalPadding
                ? availableVerticalPadding / (top + bottom) : 1;
        top *= verticalScale;
        float base = height - bottom * verticalScale - footer;
        if (right <= left || base <= top) return;
        float plotWidth = right - left, plotHeight = base - top;
        int subtle = settings.guides == 1 ? Color.WHITE : Color.rgb(115, 115, 115);

        if (settings.guides != 2) {
            p.setColor(settings.guides == 1 ? Color.WHITE : Color.rgb(160, 160, 160));
            p.setStrokeWidth(.35f * dp);
            for (int i = 1; i <= 2; i++) {
                float y = base - plotHeight * i / 3;
                canvas.drawLine(left, y, right, y, p);
            }
        }
        // Dotted baseline means no data. Known intervals are drawn solid over it.
        if (settings.showAxis) {
            p.setColor(subtle);
            p.setStrokeWidth(.55f * dp);
            for (float x = left; x < right; x += 4 * dp)
                canvas.drawLine(x, base, Math.min(right, x + 1.1f * dp), base, p);
        }

        List<ForecastWindow.Segment> segments = ForecastWindow.segments(state == ForecastState.DATA ? forecast : null, now);
        p.setColor(settings.foregroundColor);
        p.setStrokeWidth(.65f * dp);
        if (settings.showAxis) {
            for (ForecastWindow.Segment segment : segments)
                canvas.drawLine(left + plotWidth * (float) segment.startMinute / 120, base,
                        left + plotWidth * (float) segment.endMinute / 120, base, p);
        }

        if (settings.showTicks) {
            for (int minute = 0; minute <= 120; minute += 5) {
                boolean major = minute % 30 == 0;
                float x = left + plotWidth * minute / 120;
                p.setColor(settings.foregroundColor);
                p.setStrokeWidth(.65f * dp);
                canvas.drawLine(x, base + 2 * dp, x, base + (major ? 6 : 5) * dp, p);
            }
        }
        if (settings.labels) {
            p.setColor(settings.foregroundColor);
            p.setTextAlign(Paint.Align.CENTER);
            for (int minute = 30; minute <= 90; minute += 30)
                canvas.drawText(Integer.toString(minute), left + plotWidth * minute / 120, base + labelOffset, p);
        }
        p.setStrokeWidth((settings.normalLine ? 1.65f : 1.05f) * dp);
        double[] cuts = new double[4];
        for (ForecastWindow.Segment segment : segments) {
            float x1 = left + plotWidth * (float) segment.startMinute / 120;
            float x2 = left + plotWidth * (float) segment.endMinute / 120;
            // Split at actual rate crossings before capping the height. This preserves
            // both the timing of the colour change and the slope below the ceiling.
            cuts[0] = 0; cuts[1] = 1;
            int count = 2;
            double change = segment.endRate - segment.startRate;
            if (change != 0) {
                double ceiling = (settings.scaleMax - segment.startRate) / change;
                if (ceiling > 0 && ceiling < 1) cuts[count++] = ceiling;
                double highlight = (settings.highlightThreshold - segment.startRate) / change;
                if (settings.highlightRain && highlight > 0 && highlight < 1) cuts[count++] = highlight;
            }
            java.util.Arrays.sort(cuts, 0, count);
            for (int i = 1; i < count; i++) {
                double a = cuts[i - 1], b = cuts[i];
                if (b <= a) continue;
                double rateA = segment.startRate + change * a, rateB = segment.startRate + change * b;
                p.setColor(rainColor(settings, (rateA + rateB) / 2));
                canvas.drawLine(x1 + (x2 - x1) * (float) a,
                        base - plotHeight * (float) Math.min(rateA, settings.scaleMax) / settings.scaleMax,
                        x1 + (x2 - x1) * (float) b,
                        base - plotHeight * (float) Math.min(rateB, settings.scaleMax) / settings.scaleMax, p);
            }
            boolean startAbove = segment.startRate > settings.scaleMax, endAbove = segment.endRate > settings.scaleMax;
            if ((startAbove || endAbove) && (startAbove != endAbove || ((int) segment.startMinute / 5) % 3 == 0)) {
                // A tiny chevron communicates intensity above the selected range.
                float x = startAbove && endAbove ? (x1 + x2) / 2 : startAbove ? x1 : x2;
                p.setColor(rainColor(settings, startAbove && endAbove
                        ? (segment.startRate + segment.endRate) / 2 : Math.max(segment.startRate, segment.endRate)));
                canvas.drawLine(x - 2 * dp, top + 3 * dp, x, top, p);
                canvas.drawLine(x, top, x + 2 * dp, top + 3 * dp, p);
            }
        }
        if (state != ForecastState.DATA) {
            float x = (left + right) / 2;
            float y = (top + base) / 2;
            p.setColor(settings.foregroundColor);
            p.setStrokeWidth(.8f * dp);
            p.setStyle(Paint.Style.STROKE);
            if (state == ForecastState.UNAVAILABLE) {
                // Clockwise refresh arrow: a clear retry affordance, distinct from loading.
                float radius = 6 * dp;
                canvas.drawArc(x - radius, y - radius, x + radius, y + radius, 35, 285, false, p);
                double angle = Math.toRadians(320);
                float endX = x + radius * (float) Math.cos(angle);
                float endY = y + radius * (float) Math.sin(angle);
                float tangentX = -(float) Math.sin(angle), tangentY = (float) Math.cos(angle);
                float wing = 2.6f * dp;
                canvas.drawLine(endX, endY, endX - wing * tangentX + wing * tangentY,
                        endY - wing * tangentY - wing * tangentX, p);
                canvas.drawLine(endX, endY, endX - wing * tangentX - wing * tangentY,
                        endY - wing * tangentY + wing * tangentX, p);
            } else {
                // An open ring reads as loading without requiring an animation or extra wakeups.
                canvas.drawArc(x - 4 * dp, y - 4 * dp, x + 4 * dp, y + 4 * dp, -60, 280, false, p);
            }
        }
    }

    private static int rainColor(WidgetSettings settings, double rate) {
        return settings.highlightRain && rate >= settings.highlightThreshold ? settings.highlightColor : settings.rainColor;
    }
}
