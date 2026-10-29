package app.cleanyourtv.launcher;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

/** The block theme's pixel boxes and app sprites. All drawn here; no game assets. */
final class BlockArt {
    private BlockArt() {}

    /** A box with an optional one-unit outline and a one-unit bevel: light on top and left, dark on bottom and right. */
    static Drawable box(int unit, int outline, int fill, int light, int dark) {
        return new Drawable() {
            private final Paint p = new Paint();

            @Override public void draw(Canvas c) {
                Rect b = getBounds();
                int l = b.left, t = b.top, r = b.right, bt = b.bottom;
                if (outline != 0) {
                    rect(c, l, t, r, bt, outline);
                    l += unit; t += unit; r -= unit; bt -= unit;
                }
                rect(c, l, t, r, bt, fill);
                if (light != 0) { rect(c, l, t, r, t + unit, light); rect(c, l, t, l + unit, bt, light); }
                if (dark != 0) { rect(c, l, bt - unit, r, bt, dark); rect(c, r - unit, t, r, bt, dark); }
            }

            private void rect(Canvas c, int l, int t, int r, int b, int color) {
                p.setColor(color);
                c.drawRect(l, t, r, b, p);
            }

            @Override public void setAlpha(int a) {}
            @Override public void setColorFilter(ColorFilter f) {}
            @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
        };
    }

    /** An app icon cut down to a 16 x 16 sprite: hard alpha edges, 16 levels per channel, drawn without smoothing. */
    static BitmapDrawable sprite(Resources res, Drawable icon) {
        Bitmap big = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888);
        Rect old = icon.copyBounds();
        icon.setBounds(0, 0, 64, 64);
        icon.draw(new Canvas(big));
        icon.setBounds(old);
        Bitmap small = Bitmap.createScaledBitmap(big, 16, 16, true);
        big.recycle();
        int[] px = new int[256];
        small.getPixels(px, 0, 16, 0, 0, 16, 16);
        small.recycle();
        for (int i = 0; i < px.length; i++)
            px[i] = Color.alpha(px[i]) < 110 ? 0 : 0xFF000000 | (px[i] & 0xF0F0F0) | 0x080808;
        return pixelated(res, px);
    }

    /** For icons that are a long word: one big letter or two (Netflix's N) in two colours, readable at 16 x 16. */
    static BitmapDrawable letters(Resources res, Typeface font, String text, int bg, int fg) {
        Bitmap big = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(big);
        c.drawColor(bg);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setTypeface(font);
        p.setFakeBoldText(true);
        p.setColor(fg);
        Rect b = new Rect();
        p.setTextSize(64);
        p.getTextBounds(text, 0, text.length(), b);
        p.setTextSize(64 * Math.min(54f / b.width(), 46f / b.height()));
        p.getTextBounds(text, 0, text.length(), b);
        c.drawText(text, 32 - b.exactCenterX(), 32 - b.exactCenterY(), p);
        Bitmap small = Bitmap.createScaledBitmap(big, 16, 16, true);
        big.recycle();
        int[] px = new int[256];
        small.getPixels(px, 0, 16, 0, 0, 16, 16);
        small.recycle();
        for (int i = 0; i < px.length; i++) px[i] = distance(px[i], fg) < distance(px[i], bg) ? fg : bg;  // two hard colours
        return pixelated(res, px);
    }

    private static int distance(int a, int b) {
        int r = Color.red(a) - Color.red(b), g = Color.green(a) - Color.green(b), bl = Color.blue(a) - Color.blue(b);
        return r * r + g * g + bl * bl;
    }

    private static BitmapDrawable pixelated(Resources res, int[] px) {
        BitmapDrawable d = new BitmapDrawable(res, Bitmap.createBitmap(px, 16, 16, Bitmap.Config.ARGB_8888));
        d.setFilterBitmap(false);
        return d;
    }
}
