package app.rainline;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.view.MotionEvent;
import android.view.View;
import java.util.function.IntConsumer;

/** A native saturation/brightness plane. The hue slider and hex field live beside it. */
public final class ColorPickerView extends View {
    private final float[] hsv = {0, 0, 0};
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float inset;
    private IntConsumer listener;

    public ColorPickerView(Context context) {
        super(context);
        inset = 9 * getResources().getDisplayMetrics().density;
        setContentDescription(context.getString(R.string.colour_picker_description));
        setClickable(true);
    }
    public void setColor(int color) { Color.colorToHSV(color, hsv); invalidate(); }
    public void setHue(float hue) {
        hsv[0] = hue;
        invalidate();
        if (listener != null) listener.accept(Color.HSVToColor(hsv));
    }
    public float hue() { return hsv[0]; }
    public void setOnColorChanged(IntConsumer callback) { listener = callback; }

    @Override protected void onDraw(Canvas canvas) {
        float left = inset, top = inset, right = getWidth() - inset, bottom = getHeight() - inset;
        if (right <= left || bottom <= top) return;
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(null);
        paint.setColor(Color.HSVToColor(new float[]{hsv[0], 1, 1}));
        canvas.drawRect(left, top, right, bottom, paint);
        paint.setShader(new LinearGradient(left, top, right, top, Color.WHITE, Color.TRANSPARENT, Shader.TileMode.CLAMP));
        canvas.drawRect(left, top, right, bottom, paint);
        paint.setShader(new LinearGradient(left, top, left, bottom, Color.TRANSPARENT, Color.BLACK, Shader.TileMode.CLAMP));
        canvas.drawRect(left, top, right, bottom, paint);
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        float dp = getResources().getDisplayMetrics().density;
        paint.setColor(Color.WHITE); paint.setStrokeWidth(.5f * dp);
        canvas.drawRect(left, top, right, bottom, paint);
        float x = left + hsv[1] * (right - left), y = top + (1 - hsv[2]) * (bottom - top);
        paint.setColor(Color.BLACK); paint.setStrokeWidth(3 * dp);
        canvas.drawCircle(x, y, 6 * dp, paint);
        paint.setColor(Color.WHITE); paint.setStrokeWidth(1.5f * dp);
        canvas.drawCircle(x, y, 6 * dp, paint);
    }
    @Override public boolean onTouchEvent(MotionEvent event) {
        if (getWidth() <= 2 * inset || getHeight() <= 2 * inset) return false;
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE || action == MotionEvent.ACTION_UP) {
            if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(action != MotionEvent.ACTION_UP);
            hsv[1] = Math.max(0, Math.min(1, (event.getX() - inset) / (getWidth() - 2 * inset)));
            hsv[2] = 1 - Math.max(0, Math.min(1, (event.getY() - inset) / (getHeight() - 2 * inset)));
            invalidate();
            if (listener != null) listener.accept(Color.HSVToColor(hsv));
            if (action == MotionEvent.ACTION_UP) performClick();
            return true;
        }
        if (action == MotionEvent.ACTION_CANCEL) {
            if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
            return true;
        }
        return super.onTouchEvent(event);
    }
    @Override public boolean performClick() { super.performClick(); return true; }
}
