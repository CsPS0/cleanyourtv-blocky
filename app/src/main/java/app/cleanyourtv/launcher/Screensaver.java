package app.cleanyourtv.launcher;

import android.animation.ValueAnimator;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.SystemClock;
import android.provider.Settings;
import android.service.dreams.DreamService;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextClock;

import java.util.Locale;
import java.util.Random;

/**
 * The screensaver follows the launcher's theme. Clean: the clock and colour glow on black. Blocky: the clock in pixel
 * type over the home screen's painting, blown up and full of life; when Home was on screen it zooms out of the
 * painting on the wall. Either way the clock drifts slowly so it never burns in.
 */
public class Screensaver extends DreamService {
    private static final long STEP_MS = 30_000;

    private final Random rnd = new Random();
    private final GradientDrawable glowDrawable = new GradientDrawable();
    private final Runnable step = this::step;
    private FrameLayout root;
    private View glow;  // clean theme only
    private LinearLayout content;
    private ValueAnimator hueAnim;
    private float hue = rnd.nextFloat() * 360;
    private long glideMs;

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);  // any key wakes the box
        setFullscreen(true);
        // Animators get scaled by the developer "animator duration scale"; undo that so the glide fills each step.
        float scale = Settings.Global.getFloat(getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1f);
        glideMs = scale > 0 ? (long) (STEP_MS / scale) : STEP_MS;

        boolean blocky = "blocky".equals(getSharedPreferences("HomeActivity", MODE_PRIVATE).getString("theme", null));
        Typeface outfit = getResources().getFont(blocky ? R.font.monocraft : R.font.outfit);
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        BlockVista vista = null;
        if (blocky) {
            vista = new BlockVista(this);
            root.addView(vista, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        } else {
            glow = new View(this);
            glowDrawable.setGradientType(GradientDrawable.RADIAL_GRADIENT);
            glowDrawable.setGradientRadius(dp(420));
            setGlow(color(hue));
            glow.setBackground(glowDrawable);
            root.addView(glow, new FrameLayout.LayoutParams(dp(900), dp(900)));
        }

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        TextClock clock = new TextClock(this);
        clock.setFormat12Hour("h:mm");
        clock.setFormat24Hour("H:mm");
        clock.setTextSize(160);
        clock.setTextColor(0xF2FFFFFF);
        clock.setIncludeFontPadding(false);
        clock.setTypeface(outfit);
        clock.setFontVariationSettings("'wght' 120");
        TextClock date = new TextClock(this);
        String datePattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEEMMMMd");
        date.setFormat12Hour(datePattern);
        date.setFormat24Hour(datePattern);
        date.setTextSize(26);
        date.setTextColor(0x99FFFFFF);
        date.setTypeface(outfit);
        date.setFontVariationSettings("'wght' 300");
        if (blocky) {  // hard drop shadows, as block games draw text
            clock.setTextSize(110);
            clock.setTextColor(Color.WHITE);
            date.setTextColor(0xFFE0E0E0);
            for (TextClock t : new TextClock[] {clock, date}) {
                float off = Math.max(2, t.getTextSize() / 18);
                t.setShadowLayer(0.01f, off, off, 0xFF2A2A2A);
            }
        }
        content.addView(clock);
        content.addView(date);
        // Text is drawn once into a GPU texture; drifting it then costs no re-rendering.
        content.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        root.addView(content, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT));

        boolean zoom = vista != null && (HomeActivity.inFront || SystemClock.uptimeMillis() - HomeActivity.leftAt < 3000);
        View fadeIn = zoom ? content : root;
        fadeIn.setAlpha(0f);
        setContentView(root);
        if (zoom) zoomOutOfPainting(vista);
        root.post(() -> {
            moveTo((root.getWidth() - content.getWidth()) / 2f, (root.getHeight() - content.getHeight()) / 2f, 0);
            fadeIn.animate().alpha(1f).setStartDelay(zoom ? glideMs * 2200 / STEP_MS : 0).setDuration(1500).start();
            root.postDelayed(step, STEP_MS / 3);
        });
    }

    /** Starts on the room with the scene sitting in the painting's canvas, then grows it to the whole screen. */
    private void zoomOutOfPainting(BlockVista vista) {
        BlockRoom room = new BlockRoom(this);
        root.addView(room, 0, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        int sw = getResources().getDisplayMetrics().widthPixels, sh = getResources().getDisplayMetrics().heightPixels;
        int u = Math.max(1, sw / BlockRoom.W);
        vista.setPivotX(0);
        vista.setPivotY(0);
        vista.setTranslationX((BlockRoom.PAINT_X + 3) * u);
        vista.setTranslationY((BlockRoom.PAINT_Y + 3) * u);
        vista.setScaleX((BlockRoom.PAINT_W - 6) * u / (float) sw);
        vista.setScaleY((BlockRoom.PAINT_H - 6) * u / (float) sh);
        vista.animate().translationX(0).translationY(0).scaleX(1).scaleY(1)
                .setStartDelay(glideMs * 500 / STEP_MS).setDuration(glideMs * 1600 / STEP_MS)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .withEndAction(() -> root.removeView(room)).start();
    }

    @Override
    public void onDetachedFromWindow() {
        root.removeCallbacks(step);
        if (hueAnim != null) hueAnim.cancel();
        content.animate().cancel();
        if (glow != null) glow.animate().cancel();
        super.onDetachedFromWindow();
    }

    /** Glide to a random spot over one step while the glow turns to a neighbouring hue. */
    private void step() {
        int m = dp(80);
        float x = m + rnd.nextFloat() * Math.max(0, root.getWidth() - content.getWidth() - 2 * m);
        float y = m + rnd.nextFloat() * Math.max(0, root.getHeight() - content.getHeight() - 2 * m);
        moveTo(x, y, glideMs);
        root.postDelayed(step, STEP_MS);
        if (glow == null) return;

        float to = (hue + 40 + rnd.nextFloat() * 50) % 360;
        if (hueAnim != null) hueAnim.cancel();
        hueAnim = ValueAnimator.ofArgb(color(hue), color(to));
        hueAnim.setDuration(glideMs);
        hueAnim.addUpdateListener(a -> setGlow((int) a.getAnimatedValue()));
        hueAnim.start();
        hue = to;
    }

    private void moveTo(float x, float y, long ms) {
        AccelerateDecelerateInterpolator ease = new AccelerateDecelerateInterpolator();
        content.animate().translationX(x).translationY(y).setDuration(ms).setInterpolator(ease).start();
        if (glow == null) return;
        float gx = x + content.getWidth() / 2f - glow.getLayoutParams().width / 2f;
        float gy = y + content.getHeight() / 2f - glow.getLayoutParams().height / 2f;
        glow.animate().translationX(gx).translationY(gy).setDuration(ms).setInterpolator(ease).start();
    }

    private void setGlow(int c) {
        glowDrawable.setColors(new int[] {(c & 0x00FFFFFF) | 0x59000000, c & 0x00FFFFFF});
    }

    private static int color(float hue) {
        return Color.HSVToColor(new float[] {hue, 0.75f, 0.9f});
    }

    private int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
