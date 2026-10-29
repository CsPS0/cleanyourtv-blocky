package app.cleanyourtv.launcher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LightingColorFilter;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

import java.util.Random;

/**
 * The block theme's screensaver scene: the painting from the home screen's wall (dusk sky, mountain ridge, grass)
 * blown up to the whole screen, at 160 x 90 art pixels. Animals graze by day while a player farms a wheat field;
 * at night monsters come and the player fights them with a sword. The sky, sun or moon and the light follow the
 * real time of day for the TV's time zone.
 *
 * Nothing stays put, so nothing can burn in: the world loops seamlessly every 160 pixels and pans sideways without
 * end, and it also sways slowly up and down so even the sky's horizontal colour bands keep shifting.
 */
public class BlockVista extends View {
    static final int W = 160, H = 90;                       // the screen in art pixels; the world loops every W
    private static final int SWAY = 8, WH = H + 2 * SWAY;     // the world is taller by the sway, above and below
    private static final int GROUND = 74 + SWAY, FIELD_X = 62, CROPS = 8;
    private static final long PAN_MS = 2000, SWAY_MS = 600_000;  // one art pixel sideways every 2 s; a sway every 10 min
    private static final long STEP_MS = 400;
    private static final int[] WHEAT = {0xFF5E9E3A, 0xFF7AB040, 0xFFA8B848, 0xFFD8B848};

    private final Bitmap backdrop, frame = Bitmap.createBitmap(W, WH, Bitmap.Config.ARGB_8888);
    private final Canvas fc = new Canvas(frame);
    private final Paint pixel = new Paint(), lit = new Paint(), hurt = new Paint(), fill = new Paint();
    private final RectF rf = new RectF();
    private final int[] stars = new int[50], crop = new int[CROPS];
    private final double lat, lon;
    private final Random rnd = new Random();
    private final Runnable tick = this::tick;
    private final BlockRoom.Mob[] tame, foes;
    private final Bitmap[] player = BlockSprites.player();
    private float px = 40;
    private int pdir = 1, pframe, swing;  // swing: 0 none, 1 raised, 2 striking
    private float poofX;
    private long poofAt, lastStep;
    private boolean running;
    private int litLevel = -1;

    public BlockVista(Context c) {
        super(c);
        double[] place = BlockRoom.place(c);
        lat = place[0];
        lon = place[1];
        Random r = new Random(5);
        backdrop = Bitmap.createBitmap(paintBackdrop(r), W, WH, Bitmap.Config.ARGB_8888);
        for (int i = 0; i < stars.length; i++) stars[i] = r.nextInt(W) << 16 | r.nextInt(36 + SWAY);
        for (int i = 0; i < CROPS; i++) crop[i] = r.nextInt(4);
        tame = new BlockRoom.Mob[] {
                mob(BlockSprites.sheep(), 12, 0.5f), mob(BlockSprites.sheep(), 118, 0.5f), mob(BlockSprites.cow(), 30, 0.5f),
                mob(BlockSprites.pig(), 136, 0.7f), mob(BlockSprites.chicken(), 108, 1f)};
        foes = new BlockRoom.Mob[] {
                mob(BlockSprites.zombie(), 8, 0.5f), mob(BlockSprites.zombie(), 140, 0.5f),
                mob(BlockSprites.skeleton(), 120, 0.5f), mob(BlockSprites.spider(), 20, 0.8f)};
        for (Paint p : new Paint[] {pixel, lit, hurt}) p.setFilterBitmap(false);
        hurt.setColorFilter(new LightingColorFilter(0xFFFF6060, 0x00400000));  // red flash when hit
    }

    private static BlockRoom.Mob mob(Bitmap[] walk, float x, float speed) { return new BlockRoom.Mob(walk, null, x, speed, 2, W - 2); }

    @Override protected void onWindowVisibilityChanged(int v) {
        super.onWindowVisibilityChanged(v);
        running = v == VISIBLE;
        removeCallbacks(tick);
        if (running) post(tick);
    }

    @Override protected void onDetachedFromWindow() {
        running = false;
        removeCallbacks(tick);
        super.onDetachedFromWindow();
    }

    private void tick() {
        invalidate();
        if (running) postDelayed(tick, STEP_MS);
    }

    @Override protected void onDraw(Canvas canvas) {
        long now = System.currentTimeMillis();
        double[] sun = Daylight.sun(lat, lon, now);
        float day = Daylight.light(sun[0]);
        boolean night = day < 0.5f;
        int level = Math.round(day * 32);
        if (level != litLevel) {  // the painting's colours by day, darker and bluer by night
            litLevel = level;
            int v = (int) (255 * (0.5f + 0.5f * day));
            lit.setColorFilter(new LightingColorFilter(Color.rgb(v, v, Math.min(255, v + 40)), 0));
        }
        if (now - lastStep > 10 * STEP_MS) lastStep = now - STEP_MS;
        while (now - lastStep >= STEP_MS) {
            lastStep += STEP_MS;
            if (night) fight(now); else farm(now);
        }

        fc.drawBitmap(backdrop, 0, 0, lit);
        if (day < 0.6f) {
            int a = (int) (255 * (1 - day / 0.6f));
            for (int i = 0; i < stars.length; i++) {
                int x = stars[i] >>> 16, y = stars[i] & 0xFFFF;
                fill.setColor(Color.argb((now / 700 + i * 31) % 9 == 0 ? a / 3 : a, 255, 255, 230));
                fc.drawRect(x, y, x + 1, y + 1, fill);
            }
        }
        fill.setColor(night ? 0xFFE8E2C8 : 0xFFFFE070);  // the painting's moon by night, a sun by day
        fc.drawRect(W - 26, 8 + SWAY, W - 18, 16 + SWAY, fill);

        for (int i = 0; i < CROPS; i++) {
            int x = FIELD_X + 2 + i * 5, h = 1 + crop[i] * 2;
            fill.setColor(WHEAT[crop[i]]);
            fc.drawRect(x, GROUND - h, x + 2, GROUND, fill);
            if (crop[i] == 3) {
                fill.setColor(0xFFF0D060);
                fc.drawRect(x, GROUND - h - 1, x + 2, GROUND - h + 2, fill);
            }
        }
        for (BlockRoom.Mob m : night ? foes : tame) {
            if (now < m.deadUntil) continue;
            Bitmap b = m.bitmap(now);
            sprite(b, m.x, GROUND - b.getHeight(), m.dir < 0, now < m.hurtUntil ? hurt : lit);
        }
        drawPlayer(night);
        if (now - poofAt < 1000) {  // a monster going up in smoke
            float t = (now - poofAt) / 1000f;
            fill.setColor(Color.argb((int) (220 * (1 - t)), 200, 200, 200));
            for (int k = 0; k < 6; k++) {
                float a = k * 1.047f, d = 2 + t * 8;
                float x = poofX + (float) Math.cos(a) * d, y = GROUND - 6 + (float) Math.sin(a) * d - t * 6;
                fc.drawRect(x, y, x + 2, y + 2, fill);
            }
        }
        // Camera: sideways along the loop, plus the slow sway; drawn twice so the loop's seam is always covered.
        float camX = now % (PAN_MS * W) / (float) PAN_MS;
        float camY = SWAY + SWAY * (float) Math.sin(2 * Math.PI * (now % SWAY_MS) / SWAY_MS);
        float scale = getWidth() / (float) W;
        canvas.save();
        canvas.scale(scale, getHeight() / (float) H);
        canvas.translate(-camX, -camY);
        canvas.drawBitmap(frame, 0, 0, pixel);
        canvas.drawBitmap(frame, W, 0, pixel);
        canvas.restore();
    }

    /** By day: crops ripen on their own; the player walks to a ripe one and harvests it, which replants it. */
    private void farm(long now) {
        for (int i = 0; i < CROPS; i++) if (crop[i] < 3 && rnd.nextInt(70) == 0) crop[i]++;
        for (BlockRoom.Mob m : tame) m.step(now, rnd, 25);
        int target = -1;
        for (int i = 0; i < CROPS; i++)
            if (crop[i] == 3 && (target < 0 || Math.abs(cropX(i) - px) < Math.abs(cropX(target) - px))) target = i;
        if (target < 0) {  // nothing ripe: tend the field now and then
            swing = rnd.nextInt(8) == 0 ? 1 : 0;
            return;
        }
        if (walkTo(cropX(target) - 9)) return;
        pdir = 1;
        swing = swing == 1 ? 2 : 1;
        if (swing == 2) crop[target] = 0;
    }

    /** By night: monsters close in; the player walks up to the nearest and strikes it until it goes up in smoke. */
    private void fight(long now) {
        BlockRoom.Mob foe = null;
        for (BlockRoom.Mob m : foes) {
            if (now < m.deadUntil) continue;
            boolean keepsAway = m.walk == foes[2].walk;  // the skeleton hangs back
            float gap = px - m.x, want = keepsAway ? 22 : 6;
            if (Math.abs(gap) > want) {
                m.dir = gap > 0 ? 1 : -1;
                m.x += m.dir * m.speed;
            }
            m.frame ^= 1;
            if (foe == null || Math.abs(m.x - px) < Math.abs(foe.x - px)) foe = m;
        }
        if (foe == null) {
            swing = 0;
            return;
        }
        if (walkTo(foe.x + (foe.x > px ? -9 : 9))) return;
        pdir = foe.x > px ? 1 : -1;
        swing = swing == 1 ? 2 : 1;
        if (swing != 2) return;
        foe.hurtUntil = now + 350;
        foe.x = Math.max(2, Math.min(W - 12, foe.x + pdir * 5));  // knocked back
        if (--foe.hp > 0) return;
        poofX = foe.x + 4;
        poofAt = now;
        foe.hp = 3;
        foe.deadUntil = now + 6000 + rnd.nextInt(6000);
        foe.x = rnd.nextBoolean() ? 2 : W - 12;  // a new one comes from the edge later
    }

    private static float cropX(int i) { return FIELD_X + 2 + i * 5; }

    /** One step towards x; returns whether the player is still walking. */
    private boolean walkTo(float x) {
        if (Math.abs(x - px) <= 1) return false;
        pdir = x > px ? 1 : -1;
        px += pdir;
        pframe ^= 1;
        swing = 0;
        return true;
    }

    /** The player with a hoe by day or a sword by night, mirrored as one when facing left. */
    private void drawPlayer(boolean night) {
        Bitmap b = player[swing == 1 ? 2 : swing == 2 ? 3 : pframe];
        float x = px, y = GROUND - b.getHeight();
        fc.save();
        if (pdir < 0) fc.scale(-1, 1, x + 4, 0);
        fc.drawBitmap(b, x, y, lit);
        if (swing == 1) {  // tool held up, hand at (6, 3)
            fill.setColor(night ? 0xFFCFD8DC : 0xFF7A5230);
            fc.drawRect(x + 6, y - 4, x + 7, y + 3, fill);
            fill.setColor(night ? 0xFF5A5A5A : 0xFF9A9A9A);
            fc.drawRect(night ? x + 5 : x + 7, night ? y + 2 : y - 4, x + (night ? 8 : 9), night ? y + 3 : y - 3, fill);
        } else if (swing == 2) {  // tool struck forward, hand at (7, 6)
            fill.setColor(night ? 0xFFCFD8DC : 0xFF7A5230);
            fc.drawRect(x + 8, y + 6, x + 14, y + 7, fill);
            fill.setColor(night ? 0xFF5A5A5A : 0xFF9A9A9A);
            if (night) fc.drawRect(x + 8, y + 5, x + 9, y + 8, fill);
            else fc.drawRect(x + 13, y + 6, x + 14, y + 9, fill);
        }
        fc.restore();
    }

    private void sprite(Bitmap b, float x, float y, boolean flip, Paint p) {
        rf.set(x, y, x + b.getWidth(), y + b.getHeight());
        fc.save();
        if (flip) fc.scale(-1, 1, rf.centerX(), 0);
        fc.drawBitmap(b, null, rf, p);
        fc.restore();
    }

    /**
     * The painting's scene, W wide and WH tall: dusk gradient, a ridge like the painting's, grass and dirt, a tilled
     * field. The ridge uses whole sine periods over W so the world joins up seamlessly when it loops.
     */
    private static int[] paintBackdrop(Random r) {
        int[] p = new int[W * WH];
        for (int y = 0; y < WH; y++)
            for (int x = 0; x < W; x++)
                p[y * W + x] = BlockRoom.shade(BlockRoom.mix(0xFF231E46, 0xFF7A4256, Math.min(1, Math.max(0, (y - SWAY) / (float) (GROUND - SWAY)))), r.nextInt(7) - 3);
        for (int x = 0; x < W; x++) {
            double n = 2 * Math.PI * x / W;
            int ridge = GROUND - 2 - (int) (2.2 * (4 + 5 * Math.abs(Math.sin(2.5 * n)) + 1.5 * (1 + Math.sin(5 * n))));
            for (int y = ridge; y < GROUND; y++) p[y * W + x] = BlockRoom.shade(0xFF33304A, r.nextInt(7) - 3);
            for (int y = GROUND; y < WH; y++)
                p[y * W + x] = BlockRoom.shade(y < GROUND + 2 ? 0xFF2A3E26 : 0xFF3E2C1E, r.nextInt(9) - 4);
        }
        for (int x = FIELD_X; x < FIELD_X + CROPS * 5 + 2; x++)  // tilled rows under the wheat
            for (int y = GROUND; y < GROUND + 3; y++) p[y * W + x] = BlockRoom.shade(x % 2 == 0 ? 0xFF4A2E1C : 0xFF5A3A24, r.nextInt(7) - 3);
        return p;
    }
}
