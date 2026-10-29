package app.cleanyourtv.launcher;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.OvershootInterpolator;

/** The single glass focus highlight. Draws a rounded rect that springs between targets without any layout pass. */
public class Highlight extends View {
    static final int SELECTOR = 1, HOVER = 2, OUTLINE = 3;
    private final Paint px = new Paint();
    private int unit, style;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF(), from = new RectF(), to = new RectF();
    private float radius, fromRadius, toRadius;
    private ValueAnimator anim;

    public Highlight(Context c, AttributeSet a) {
        super(c, a);
        float d = getResources().getDisplayMetrics().density;
        fill.setColor(0x1FFFFFFF);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(2 * d);
        stroke.setColor(0x8CFFFFFF);
    }

    /** Block theme: square pixel frames that snap instead of springing; unit is one art pixel. */
    public void setPixelUnit(int u) { unit = u; }

    public void setStyle(int s) { style = s; }

    public void moveTo(RectF target, float r, boolean animate) {
        if (anim != null) anim.cancel();
        if (!animate || rect.isEmpty()) {
            rect.set(target);
            radius = r;
            invalidate();
            return;
        }
        from.set(rect);
        fromRadius = radius;
        to.set(target);
        toRadius = r;
        anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(340);
        anim.setInterpolator(new OvershootInterpolator(1.3f));
        anim.addUpdateListener(v -> {
            float t = (float) v.getAnimatedValue();
            rect.set(lerp(from.left, to.left, t), lerp(from.top, to.top, t),
                    lerp(from.right, to.right, t), lerp(from.bottom, to.bottom, t));
            radius = lerp(fromRadius, toRadius, t);
            invalidate();
        });
        anim.start();
    }

    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }

    @Override protected void onDraw(Canvas c) {
        if (rect.isEmpty()) return;
        if (unit > 0) {
            drawPixel(c);
            return;
        }
        c.drawRoundRect(rect, radius, radius, fill);
        c.drawRoundRect(rect, radius, radius, stroke);
    }

    /** SELECTOR: the hotbar's thick light frame. HOVER: the inventory's lightened slot. OUTLINE: buttons and menus. */
    private void drawPixel(Canvas c) {
        int l = Math.round(rect.left), t = Math.round(rect.top), r = Math.round(rect.right), b = Math.round(rect.bottom);
        if (style == HOVER) {
            px.setColor(0x80FFFFFF);
            c.drawRect(l, t, r, b, px);
        } else if (style == SELECTOR) {
            ring(c, l, t, r, b, unit, 0xFF141414);
            ring(c, l + unit, t + unit, r - unit, b - unit, 2 * unit, 0xFFEDEDED);
            ring(c, l + 3 * unit, t + 3 * unit, r - 3 * unit, b - 3 * unit, unit, 0xFF7E7E7E);
        } else {
            px.setColor(0x26FFFFFF);
            c.drawRect(l, t, r, b, px);
            ring(c, l, t, r, b, unit, 0xFFFFFFFF);
        }
    }

    private void ring(Canvas c, int l, int t, int r, int b, int w, int color) {
        px.setColor(color);
        c.drawRect(l, t, r, t + w, px);
        c.drawRect(l, b - w, r, b, px);
        c.drawRect(l, t + w, l + w, b - w, px);
        c.drawRect(r - w, t + w, r, b - w, px);
    }
}
