package app.cleanyourtv.launcher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LightingColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Random;
import java.util.TimeZone;

/**
 * The block theme's background: a plank cabin with a window onto a sky that follows the real sun for the TV's
 * time zone, a dog and a cat, animals outside by day and monsters by night, a painting that carries the clock and
 * an item frame whose gear is the Settings button. A farmer lives here: by day they open the chest, craft at the
 * workbench, cook at the furnace and pet the dog and cat; at night they sleep in the bed.
 * Painted at 320 x 180 art pixels and scaled up unsmoothed. Lighting is computed per art pixel ("shader" style:
 * daylight from the window, sun patch, torches and the furnace as soft lights, ambient occlusion) into a cached
 * image that is repainted only when the light changes. Each tick redraws the sky, the creatures, the flames, the
 * gear and full-resolution glows (sun rays, torch and furnace bloom, vignette).
 */
public class BlockRoom extends View {
    static final int W = 320, H = 180;
    private static final int WX = 104, WY = 24, WW = 112, WH = 80;  // window opening
    private static final int[] TORCH_X = {88, 230};  // either side of the window
    private static final int TORCH_Y = 50;
    static final int FRAME_X = 258, FRAME_Y = 54, FRAME_W = 24;               // item frame with the Settings gear
    static final int PAINT_X = 16, PAINT_Y = 22, PAINT_W = 68, PAINT_H = 48;  // painting the clock is shown on
    private static final int FLOOR = 148;
    private static final int FX = 234, FY = 116;  // furnace (32 x 32), its fire opening below
    private static final int CAT_X = 36;           // the cat sits on the bookshelf
    private static final int CHEST_X = 204, CHEST_Y = 124;  // chest (26 x 24) under the window's right side
    private static final int BED_X = 78;           // where the farmer stands to climb into bed
    private static final int SLEEP = 0, WANDER = 1, CHEST = 2, CRAFT = 3, COOK = 4, PET_DOG = 5, PET_CAT = 6;
    private static final float WALK = 4;           // art pixels per step
    private static final String[] ZED = {"####", "..#.", ".#..", "####"};
    private static final String[] HEART = {"#.#", "###", ".#."};
    private static final long STEP_MS = 400, FAST_MS = 80;
    private static final int[] BOOKS = {0xFF8E2B2B, 0xFF2E4C8C, 0xFF3E7A3A, 0xFF6B4A2A, 0xFF5A3A7A, 0xFFB08A3A};
    private static final int[] FLAME = {0xFFFFF4B0, 0xFFFFD060, 0xFFFF9A30};

    private final int[] albedo, lit = new int[W * H], ground = new int[WW / 4 + 1];
    private final float[] ao = new float[W * H];
    private final Bitmap still = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
    private final Bitmap sky = Bitmap.createBitmap(WW, WH, Bitmap.Config.ARGB_8888);
    private final Canvas fc = new Canvas(sky);  // draws in room coordinates
    private final Bitmap hills, gear = BlockSprites.gear();
    private final Paint pixel = new Paint(), land = new Paint(), fill = new Paint(), mobPaint = new Paint(), dogPaint = new Paint();
    private final Paint rayPaint = new Paint(), glow = new Paint(), vignette = new Paint();
    private final Rect dst = new Rect();
    private final RectF rf = new RectF();
    private final Path rays = new Path();
    private final int[] stars = new int[60];
    private final double lat, lon;
    private final Random rnd = new Random();
    private final Runnable tick = this::tick;
    private final Mob[] dayMobs, nightMobs;
    private final Mob dog;
    private final Bitmap[] cat = BlockSprites.cat();
    private long teaseUntil;  // the dog goes to sit under the cat until then
    private final Bitmap[] walkFig = {BlockSprites.figure(25, -25, -25, 25), BlockSprites.figure(-25, 25, 25, -25)};
    private final Bitmap[][] workFig = new Bitmap[7][];  // two frames per task
    private final Bitmap[] sleeper = BlockSprites.sleeper();
    private final Bitmap chestOpen = BlockSprites.chestOpen();
    private int task = -1, pdir = 1, face = 1, pframe;
    private float px, goal;
    private boolean there;       // arrived and doing the task
    private long doneAt, dogHeldUntil;
    private boolean running;
    private int stillKey;
    private long lastStep;

    private boolean spinning;

    /** A creature that wanders left and right, resting now and then. Sprites face right; dir < 0 mirrors them. */
    static final class Mob {
        final Bitmap[] walk, rest;
        final float speed;
        final int min, max;
        float x;
        int dir = 1, frame;
        long restUntil;
        int hp = 3;                 // the screensaver's fights
        long hurtUntil, deadUntil;

        Mob(Bitmap[] walk, Bitmap[] rest, float x, float speed, int min, int max) {
            this.walk = walk;
            this.rest = rest;
            this.x = x;
            this.speed = speed;
            this.min = min;
            this.max = max - walk[0].getWidth();
        }

        void step(long now, Random r, int restOdds) {
            frame ^= 1;
            if (now < restUntil) return;
            if (r.nextInt(restOdds) == 0) { restUntil = now + 2000 + r.nextInt(8000); return; }
            if (r.nextInt(30) == 0) dir = -dir;
            x += dir * speed;
            if (x < min) { x = min; dir = 1; }
            if (x > max) { x = max; dir = -1; }
        }

        Bitmap bitmap(long now) {
            if (now < restUntil) return rest != null ? rest[frame] : walk[0];
            return walk[frame];
        }
    }

    public BlockRoom(Context c) {
        super(c);
        double[] place = place(c);
        lat = place[0];
        lon = place[1];
        Random r = new Random(1);
        albedo = paintRoom(r);
        shadeAmbientOcclusion();
        for (int i = 0, h = 2; i < ground.length; i++) {
            h = Math.max(0, Math.min(3, h + r.nextInt(3) - 1));
            ground[i] = WH - 12 - 4 * h;
        }
        hills = Bitmap.createBitmap(paintHills(r), WW, WH, Bitmap.Config.ARGB_8888);
        for (int i = 0; i < stars.length; i++) stars[i] = (WX + r.nextInt(WW)) << 16 | (WY + r.nextInt(WH * 2 / 3));

        Bitmap[] dogArt = BlockSprites.dog();
        dog = new Mob(new Bitmap[] {dogArt[0], dogArt[1]}, new Bitmap[] {dogArt[2], dogArt[3]}, 120, 1.5f, 52, 232);
        dayMobs = new Mob[] {
                mob(BlockSprites.sheep(), 26, 0.5f), mob(BlockSprites.sheep(), 58, 0.5f), mob(BlockSprites.cow(), 86, 0.5f),
                mob(BlockSprites.pig(), 40, 0.7f), mob(BlockSprites.chicken(), 70, 1f)};
        nightMobs = new Mob[] {
                mob(BlockSprites.zombie(), 20, 0.5f), mob(BlockSprites.zombie(), 76, 0.5f),
                mob(BlockSprites.skeleton(), 50, 0.7f), mob(BlockSprites.spider(), 92, 1f)};
        Bitmap stand = BlockSprites.figure(0, 0, 4, -4);
        workFig[WANDER] = new Bitmap[] {stand, stand};
        workFig[CHEST] = new Bitmap[] {BlockSprites.figure(0, 0, 70, -5), BlockSprites.figure(0, 0, 100, -5)};  // rummaging
        workFig[CRAFT] = new Bitmap[] {BlockSprites.figure(0, 0, 125, -5), BlockSprites.figure(0, 0, 85, -5)};  // hammering
        workFig[COOK] = new Bitmap[] {BlockSprites.figure(0, 0, 45, -5), BlockSprites.figure(0, 0, 62, -5)};    // feeding the fire
        workFig[PET_DOG] = new Bitmap[] {BlockSprites.figure(8, -4, 35, -5), BlockSprites.figure(8, -4, 50, -5)};
        workFig[PET_CAT] = new Bitmap[] {BlockSprites.figure(0, 0, 132, -5), BlockSprites.figure(0, 0, 150, -5)};

        fc.translate(-WX, -WY);
        for (Paint p : new Paint[] {pixel, land, mobPaint, dogPaint}) p.setFilterBitmap(false);  // hard pixel edges
        rayPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SCREEN));
        glow.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SCREEN));
    }

    private static Mob mob(Bitmap[] walk, float x, float speed) { return new Mob(walk, null, x, speed, 1, WW - 1); }

    /** The TV's time zone mapped to its main city; zones zone.tab lacks (UTC, Etc/*) get a mid-latitude spot on their meridian. */
    static double[] place(Context c) {
        TimeZone tz = TimeZone.getDefault();
        try (InputStream in = c.getAssets().open("zone.tab")) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
            double[] ll = Daylight.coords(new String(out.toByteArray(), StandardCharsets.UTF_8), tz.getID());
            if (ll != null) return ll;
        } catch (IOException ignored) {
            // fall through to the meridian guess
        }
        return new double[] {45, tz.getRawOffset() / 3_600_000.0 * 15};
    }

    /** The Settings gear in the item frame spins while it has focus. */
    public void setSpinning(boolean on) {
        if (on == spinning) return;
        spinning = on;
        removeCallbacks(tick);
        if (running) post(tick);
    }

    // ---- animation --------------------------------------------------------------------------

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

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        float sx = w / (float) W;
        glow.setShader(new RadialGradient(0, 0, 24 * sx, 0xFFFFB060, 0x00FFB060, Shader.TileMode.CLAMP));
        vignette.setShader(new RadialGradient(w / 2f, h / 2f, (float) Math.hypot(w, h) * 0.55f,
                new int[] {0, 0, 0xFF000000}, new float[] {0, 0.6f, 1}, Shader.TileMode.CLAMP));
        stillKey = 0;
    }

    private void tick() {
        invalidate();
        if (running) postDelayed(tick, spinning ? FAST_MS : STEP_MS);
    }

    @Override protected void onDraw(Canvas canvas) {
        long now = System.currentTimeMillis();
        double[] sun = Daylight.sun(lat, lon, now);
        float day = Daylight.light(sun[0]), night = 1 - day;
        float dusk = (float) Math.max(0, 1 - Math.abs(sun[0]) / 10);
        float sunK = sun[0] > 1 && Math.abs(sun[1]) < 95 ? (float) (day * Math.min(1, sun[0] / 12)) : 0;
        float patchX = (float) (WX + WW / 2.0 - sun[1] / 90 * 80);  // where the sun through the window lands on the floor
        float sx = getWidth() / (float) W, sy = getHeight() / (float) H;

        int key = Objects.hash(Math.round(day * 32), Math.round(sunK * 8), Math.round(patchX / 4), getWidth());
        if (key != stillKey) {
            stillKey = key;
            paintStill(day, dusk, sunK, patchX, sx, sy);
        }
        if (now - lastStep > 10 * STEP_MS) lastStep = now - STEP_MS;
        while (now - lastStep >= STEP_MS) {
            lastStep += STEP_MS;
            stepDog(now, day);
            stepPlayer(now, day);
            for (Mob m : day >= 0.5f ? dayMobs : nightMobs) m.step(now, rnd, 25);
        }
        boolean asleep = task == SLEEP && there, busy = there && now < doneAt;

        drawSky(now, sun, day);
        dst.set(0, 0, getWidth(), getHeight());
        canvas.drawBitmap(still, null, dst, pixel);
        dst.set(Math.round(WX * sx), Math.round(WY * sy), Math.round((WX + WW) * sx), Math.round((WY + WH) * sy));
        canvas.drawBitmap(sky, null, dst, pixel);
        if (sunK > 0) canvas.drawPath(rays, rayPaint);
        if (task == CHEST && busy) sprite(canvas, chestOpen, CHEST_X, CHEST_Y - 8, sx, sy, false, dogPaint);

        boolean teased = now < teaseUntil && dog.x <= dog.min;
        Bitmap kitty = teased ? cat[(int) (now / 300 % 2) * 2] : cat[(int) (now / 1500 % 2)];
        sprite(canvas, kitty, CAT_X, 116 - kitty.getHeight(), sx, sy, false, dogPaint);
        drawFire(canvas, sx, sy);

        Bitmap d = dog.bitmap(now);
        fill.setColor(0x50000000);  // contact shadow
        canvas.drawRect((dog.x + 2) * sx, (FLOOR - 1) * sy, (dog.x + d.getWidth() - 1) * sx, (FLOOR + 1) * sy, fill);
        sprite(canvas, d, dog.x, FLOOR - d.getHeight(), sx, sy, dog.dir < 0, dogPaint);

        if (asleep) {
            sprite(canvas, sleeper[1], 71, 130, sx, sy, false, dogPaint);
            sprite(canvas, sleeper[0], 61, 124, sx, sy, false, dogPaint);
        } else {
            Bitmap f = there ? workFig[task][pframe] : walkFig[pframe];
            float bx = px + (pdir < 0 ? 15.5f : 10.5f);  // the body's middle, for the contact shadow
            fill.setColor(0x50000000);
            canvas.drawRect((bx - 5) * sx, (FLOOR - 1) * sy, (bx + 5) * sx, (FLOOR + 1) * sy, fill);
            sprite(canvas, f, px, FLOOR - f.getHeight(), sx, sy, pdir < 0, dogPaint);
        }

        drawFlames(canvas, sx, sy);
        drawGear(canvas, now, sx, sy);

        glow.setAlpha((int) ((0.15f + 0.85f * night) * (170 + rnd.nextInt(60))));  // flickering torch bloom
        for (int tx : TORCH_X) {
            canvas.save();
            canvas.translate((tx + 1) * sx, (TORCH_Y + 1) * sy);
            canvas.drawCircle(0, 0, 24 * sx, glow);
            canvas.restore();
        }
        glow.setAlpha((int) ((0.2f + 0.8f * night) * (150 + rnd.nextInt(80))));  // furnace
        canvas.save();
        canvas.translate((FX + 16) * sx, (FY + 22) * sy);
        canvas.drawCircle(0, 0, 24 * sx, glow);
        canvas.restore();
        vignette.setAlpha((int) (90 + 110 * night));
        canvas.drawRect(0, 0, getWidth(), getHeight(), vignette);

        if (asleep) drawZzz(canvas, now, sx, sy);
        if (busy && task == PET_CAT) drawHeart(canvas, now, CAT_X + 7, 104, sx, sy);
        if (busy && task == PET_DOG) drawHeart(canvas, now, dog.x + (dog.dir > 0 ? 18 : 6), 131, sx, sy);
    }

    /** Wanders; now and then trots to the bookshelf and sits looking up at the cat, who swipes at it. */
    private void stepDog(long now, float day) {
        if (now < dogHeldUntil) {  // sitting for the farmer, wagging
            dog.frame ^= 1;
            return;
        }
        if (now >= teaseUntil) {
            if (rnd.nextInt(90) == 0) teaseUntil = now + 16000;
            dog.step(now, rnd, day < 0.3f ? 12 : 30);  // sleepier at night
            return;
        }
        dog.dir = -1;
        dog.frame ^= 1;
        if (dog.x > dog.min) {
            dog.x = Math.max(dog.min, dog.x - dog.speed * 2);
            dog.restUntil = 0;
        } else {
            dog.restUntil = teaseUntil;
        }
    }

    /** The farmer's day: walk to a spot, do something there for a few seconds, pick the next. At night, bed. */
    private void stepPlayer(long now, float day) {
        boolean night = day < 0.5f;
        if (task < 0) {  // first frame: already in bed at night, standing about by day
            task = night ? SLEEP : WANDER;
            px = goal = night ? BED_X : 150;
            there = true;
        }
        if (night && task != SLEEP) {
            finishTask(now);
            task = SLEEP;
            goal = BED_X;
            there = false;
        } else if (!night && task == SLEEP) {  // morning: up and standing beside the bed
            task = WANDER;
            px = goal = BED_X + 10;
            there = true;
            doneAt = now + 3000;
        }
        pframe ^= 1;
        if (!there) {
            float d = goal - px;
            if (Math.abs(d) > WALK) {
                pdir = d > 0 ? 1 : -1;
                px += pdir * WALK;
                return;
            }
            px = goal;
            pdir = face;
            there = true;
            doneAt = now + (task == CRAFT || task == COOK ? 9000 : 6000 + rnd.nextInt(3000));
            return;
        }
        if (task == SLEEP || now < doneAt) return;
        finishTask(now);
        int next;
        do next = 1 + rnd.nextInt(6); while (next == task);  // WANDER .. PET_CAT
        startTask(next);
    }

    /** Sets where the farmer walks for the task and which way they then face; hand positions are worked out from figure()'s joints. */
    private void startTask(int t) {
        task = t;
        there = false;
        switch (t) {
            case CHEST: goal = CHEST_X - 18; face = 1; break;   // hand on the chest's front
            case CRAFT: goal = 265; face = 1; break;             // in front of the workbench, hands on its top
            case COOK: goal = 225; face = 1; break;              // at the furnace's opening
            case PET_CAT: goal = CAT_X; face = -1; break;        // beside the bookshelf, reaching up
            case PET_DOG:
                teaseUntil = 0;
                dogHeldUntil = dog.restUntil = Long.MAX_VALUE;   // the dog sits and waits
                dog.dir = px < dog.x ? -1 : 1;                   // turns to the farmer
                goal = dog.dir < 0 ? dog.x - 12 : dog.x + 9.5f;  // hand over its ears
                face = -dog.dir;
                break;
            default:
                goal = 30 + rnd.nextInt(230);
                face = goal > px ? 1 : -1;
        }
    }

    private void finishTask(long now) {
        if (task == PET_DOG) dogHeldUntil = dog.restUntil = now + 1500;  // a moment's more sitting, then off it goes
    }

    /** Three Zs rising and growing from the sleeper's head, each fading in and out. */
    private void drawZzz(Canvas c, long now, float sx, float sy) {
        for (int i = 0; i < 3; i++) {
            float t = (now + i * 1000) % 3000 / 3000f;
            fill.setColor(Color.argb((int) (230 * Math.sin(Math.PI * t)), 255, 255, 255));
            pattern(c, ZED, 70 + 14 * t, 118 - 26 * t, 1 + t, sx, sy);
        }
    }

    /** A heart floating up from a petted animal. */
    private void drawHeart(Canvas c, long now, float x, float y, float sx, float sy) {
        float t = now % 1200 / 1200f;
        fill.setColor(Color.argb((int) (255 * Math.sin(Math.PI * t)), 0xE8, 0x30, 0x3C));
        pattern(c, HEART, x - 1.5f, y - 4 - 8 * t, 1, sx, sy);
    }

    /** Rows of '#' drawn in fill's colour, each pixel k art pixels square. */
    private void pattern(Canvas c, String[] rows, float x0, float y0, float k, float sx, float sy) {
        for (int y = 0; y < rows.length; y++)
            for (int x = 0; x < rows[y].length(); x++)
                if (rows[y].charAt(x) == '#')
                    c.drawRect((x0 + x * k) * sx, (y0 + y * k) * sy, (x0 + (x + 1) * k) * sx, (y0 + (y + 1) * k) * sy, fill);
    }

    /**
     * Lights every art pixel: albedo x (sky ambient x occlusion + window spill + sun patch + torches + furnace).
     * A few thousand multiplies per light, done only when one of them changes.
     */
    private void paintStill(float day, float dusk, float sunK, float patchX, float sx, float sy) {
        float night = 1 - day;
        float ar = lerp(0.16f, 1.0f, day) + 0.25f * dusk, ag = lerp(0.19f, 0.97f, day) + 0.08f * dusk;
        float ab = lerp(0.34f, 0.92f, day) - 0.06f * dusk;
        float torch = 0.12f + 1.1f * night;
        float spill = 0.35f * day, wcx = WX + WW / 2f, wcy = WY + WH / 2f;
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++) {
                int i = y * W + x, c = albedo[i];
                if (c == 0) { lit[i] = 0; continue; }
                float o = ao[i], r = ar * o, g = ag * o, b = ab * o;
                for (int tx : TORCH_X) {
                    float dx = x - tx - 1, dy = y - TORCH_Y - 1, f = torch / (1 + (dx * dx + dy * dy) / 400f);
                    r += f; g += f * 0.62f; b += f * 0.3f;
                }
                float dx = x - FX - 16, dy = y - FY - 22, f = torch * 0.8f / (1 + (dx * dx + dy * dy) / 500f);
                r += f; g += f * 0.5f; b += f * 0.2f;  // furnace fire
                dx = x - wcx; dy = y - wcy;
                f = spill / (1 + (dx * dx + dy * dy) / 2500f);
                r += f; g += f; b += f * 1.05f;
                if (y >= FLOOR && sunK > 0) {
                    float edge = 1 - Math.abs(x - patchX) / (WW * 0.6f);
                    if (edge > 0) {
                        f = sunK * Math.min(1, edge * 3) * 0.9f * (1 - (y - FLOOR) / 48f);
                        r += f; g += f * 0.9f; b += f * 0.7f;
                    }
                }
                lit[i] = Color.rgb(clamp((int) (Color.red(c) * r)), clamp((int) (Color.green(c) * g)), clamp((int) (Color.blue(c) * b)));
            }
        still.setPixels(lit, 0, W, 0, 0, W, H);

        int dogMul = Color.rgb(clamp((int) (255 * (ar + 0.35f * night))), clamp((int) (255 * (ag + 0.2f * night))), clamp((int) (255 * ab)));
        dogPaint.setColorFilter(new LightingColorFilter(dogMul, 0));
        mobPaint.setColorFilter(new LightingColorFilter(Color.rgb(clamp((int) (255 * (0.45f + 0.55f * day))),
                clamp((int) (255 * (0.45f + 0.55f * day))), clamp((int) (255 * (0.6f + 0.4f * day)))), 0));
        land.setColorFilter(new LightingColorFilter(gray(0.22f + 0.78f * day), 0));

        // Light shafts from the window to the sun patch.
        float half = WW * 0.55f, top = WY + WH + 3;  // from the sill down, so no edge crosses the sky
        rays.rewind();
        rays.moveTo((WX - 4) * sx, top * sy);
        rays.lineTo((WX + WW + 4) * sx, top * sy);
        rays.lineTo((patchX + half) * sx, H * sy);
        rays.lineTo((patchX - half) * sx, H * sy);
        rays.close();
        int a = (int) (90 * sunK);
        rayPaint.setShader(new LinearGradient(0, top * sy, 0, H * sy, Color.argb(a, 255, 230, 170), 0x00FFE6AA, Shader.TileMode.CLAMP));
    }

    private void drawSky(long now, double[] sun, float day) {
        fc.save();
        fc.clipRect(WX, WY, WX + WW, WY + WH);
        float dusk = (float) Math.max(0, 1 - Math.abs(sun[0]) / 10);  // orange near the horizon at sunrise and sunset
        int top = mix(0xFF0A0F2A, 0xFF5F8FEF, day);
        int low = mix(mix(0xFF1A2550, 0xFF9CC2FF, day), 0xFFF0905A, dusk * 0.8f);
        for (int y = 0; y < WH; y += 4) {
            fill.setColor(mix(top, low, y / (float) WH));
            fc.drawRect(WX, WY + y, WX + WW, WY + y + 4, fill);
        }
        if (day < 0.6f) {
            int a = (int) (255 * (1 - day / 0.6f));
            for (int i = 0; i < stars.length; i++) {
                int x = stars[i] >>> 16, y = stars[i] & 0xFFFF;
                boolean dim = (now / 700 + i * 31) % 9 == 0;
                fill.setColor(Color.argb(dim ? a / 3 : a, 255, 255, 230));
                fc.drawRect(x, y, x + 1, y + 1, fill);
            }
        }
        body(sun[1], sun[0], 12, 0xFFFFD35A, 0xFFFFF2B0, false);
        body(sun[1] + 180, -sun[0], 10, 0xFFB8BCC8, 0xFFE8EAF0, true);  // the moon, kept opposite the sun
        clouds(now, day);
        fc.drawBitmap(hills, WX, WY, land);
        for (Mob m : day >= 0.5f ? dayMobs : nightMobs) {
            Bitmap b = m.bitmap(now);
            int foot = ground[Math.min(ground.length - 1, ((int) m.x + b.getWidth() / 2) / 4)];
            sprite(fc, b, WX + m.x, WY + foot - b.getHeight(), 1, 1, m.dir < 0, mobPaint);
        }
        fill.setColor(0x14FFFFFF);
        fc.drawRect(WX, WY, WX + WW, WY + WH, fill);
        fill.setColor(0x30FFFFFF);
        for (int px = WX + 8; px < WX + WW; px += WW / 2)
            for (int i = 0; i < 7; i++) fc.drawRect(px + i, WY + 14 - i, px + i + 1, WY + 15 - i, fill);
        fc.restore();
    }

    /** Sun or moon: across the window with the hour angle, up with the altitude. */
    private void body(double hourAngle, double alt, int size, int rim, int core, boolean craters) {
        double ha = ((hourAngle % 360) + 540) % 360 - 180;
        if (alt < -8) return;
        float horizon = WY + WH - 14;
        int x = Math.round(WX + WW / 2f + (float) (ha / 100) * (WW / 2f + 10) - size / 2f);
        int y = Math.round(horizon - (float) (Math.min(alt, 60) / 60) * (horizon - WY - 4) - size / 2f);
        fill.setColor(rim);
        fc.drawRect(x, y, x + size, y + size, fill);
        fill.setColor(core);
        fc.drawRect(x + 2, y + 2, x + size - 2, y + size - 2, fill);
        if (craters) {
            fill.setColor(rim);
            fc.drawRect(x + 3, y + 4, x + 5, y + 6, fill);
            fc.drawRect(x + 6, y + 6, x + 7, y + 7, fill);
        }
    }

    private void clouds(long now, float day) {
        fill.setColor(mix(0xFF39405A, 0xFFF4F6FA, day) & 0x00FFFFFF | 0xE0000000);
        int span = WW + 60;
        for (int i = 0; i < 4; i++) {
            int x = WX - 30 + (int) ((now / 1000 * (2 + i % 2) / 2 + i * 47) % span);
            int y = WY + 6 + i * 9 % 30, w = 18 + i * 13 % 16;
            fc.drawRect(x, y, x + w, y + 4, fill);
            fc.drawRect(x + 4, y - 3, x + w - 6, y, fill);
        }
    }

    /** Straight onto the screen, so the cached room never needs repainting for a flicker. */
    private void drawFlames(Canvas c, float sx, float sy) {
        for (int tx : TORCH_X) {
            for (int i = 0; i < 6; i++) {
                int x = tx + i % 2, y = TORCH_Y + i / 2;
                if (i < 2 && rnd.nextInt(4) == 0) continue;  // the tip comes and goes
                fill.setColor(FLAME[Math.min(2, i / 2 + rnd.nextInt(2))]);
                c.drawRect(Math.round(x * sx), Math.round(y * sy), Math.round((x + 1) * sx), Math.round((y + 1) * sy), fill);
            }
        }
    }

    /** Live flames in the furnace's opening. */
    private void drawFire(Canvas c, float sx, float sy) {
        for (int x = FX + 9; x < FX + 23; x++) {
            int h = 1 + rnd.nextInt(task == COOK && there ? 8 : 4);  // roars while the farmer cooks
            for (int y = FY + 26 - h; y < FY + 26; y++) {
                fill.setColor(FLAME[Math.min(2, (FY + 26 - y) / 2 + rnd.nextInt(2))]);
                c.drawRect(x * sx, y * sy, (x + 1) * sx, (y + 1) * sy, fill);
            }
        }
    }

    /** The gear in the item frame, kept bright so the Settings button reads at night; it spins while focused. */
    private void drawGear(Canvas c, long now, float sx, float sy) {
        float cx = (FRAME_X + FRAME_W / 2f) * sx, cy = (FRAME_Y + FRAME_W / 2f) * sy;
        c.save();
        if (spinning) c.rotate(now % 1200 * 0.3f, cx, cy);
        rf.set(cx - 8 * sx, cy - 8 * sy, cx + 8 * sx, cy + 8 * sy);
        c.drawBitmap(gear, null, rf, pixel);
        c.restore();
    }

    /** A bitmap scaled by (sx, sy) art pixels. */
    private void sprite(Canvas c, Bitmap b, float x, float y, float sx, float sy, boolean flip, Paint p) {
        float w = b.getWidth(), h = b.getHeight();
        rf.set(x * sx, y * sy, (x + w) * sx, (y + h) * sy);
        c.save();
        if (flip) c.scale(-1, 1, rf.centerX(), 0);
        c.drawBitmap(b, null, rf, p);
        c.restore();
    }

    // ---- painting (once) ----------------------------------------------------------------------

    private static int[] paintRoom(Random r) {
        int[] p = new int[W * H];
        planks(p, 0, 8, W, 140, 0xFF9A7448, r);
        cobble(p, 0, 128, W, FLOOR - 128, r);                                     // stone lower wall
        planks(p, 0, 125, W, 3, 0xFF6E5030, r);                                   // trim rail
        log(p, 0, 0, W, 8, true, r);
        log(p, 0, 8, 10, 140, false, r);
        log(p, W - 10, 8, 10, 140, false, r);
        planks(p, 0, FLOOR, W, 32, 0xFF7A5A36, r);
        for (int x = 0; x < W; x++) p[FLOOR * W + x] = shade(p[FLOOR * W + x], 24);

        // window: log frame, sill, a cross of glazing bars, an open hole for the sky
        log(p, WX - 3, WY - 3, WW + 6, WH + 6, true, r);
        rect(p, WX, WY, WW, WH, 0);
        rect(p, WX + WW / 2 - 1, WY, 2, WH, 0xFF5B4128);
        rect(p, WX, WY + WH / 2 - 1, WW, 2, 0xFF5B4128);
        planks(p, WX - 6, WY + WH + 3, WW + 12, 3, 0xFFB08A58, r);

        // curtains on a rod, folded fabric hanging over both sides of the window
        rect(p, WX - 14, WY - 9, WW + 28, 2, 0xFF3A2A1C);
        for (int side = 0; side < 2; side++) {
            int x0 = side == 0 ? WX - 10 : WX + WW - 8;
            for (int y = WY - 7; y < WY + WH + 7; y++)
                for (int x = x0; x < x0 + 18; x++) {
                    int fold = (x - x0) % 4;
                    int c = shade(0xFF8A2A2A, fold == 0 ? -26 : fold == 2 ? 14 : 0);
                    set(p, x, y, shade(c, r.nextInt(7) - 3));
                }
            rect(p, x0, WY + WH / 2 + 8, 18, 2, 0xFFC8A040);  // tie-back
        }

        // bookshelf, darker than the wall and outlined so it reads as its own block
        planks(p, 18, 116, 32, 32, 0xFF7E5C38, r);
        for (int shelf = 0; shelf < 2; shelf++) {
            int bottom = 131 + shelf * 15;
            rect(p, 20, bottom - 12, 28, 12, 0xFF3A2A18);
            for (int x = 20; x < 48; ) {
                int w = 2 + r.nextInt(2), h = 8 + r.nextInt(4), c = BOOKS[r.nextInt(BOOKS.length)];
                w = Math.min(w, 48 - x);
                rect(p, x, bottom - h, w, h, c);
                rect(p, x, bottom - h + 2, w, 1, shade(c, 40));
                x += w;
            }
        }
        outline(p, 18, 116, 32, 32);

        // bed: headboard, white pillow, red blanket, wooden frame on short legs
        rect(p, 56, 126, 4, 22, 0xFF6E4E2E);
        outline(p, 56, 126, 4, 22);
        rect(p, 60, 136, 46, 7, 0xFFE8E4DA);
        rect(p, 61, 133, 10, 5, 0xFFF4F2EC);
        rect(p, 61, 137, 10, 1, 0xFFC8C2B4);
        for (int y = 135; y < 143; y++)
            for (int x = 72; x < 106; x++)
                set(p, x, y, shade((x + y) % 6 < 3 ? 0xFFB0302A : 0xFF9C2A24, r.nextInt(7) - 3));
        rect(p, 72, 135, 34, 1, 0xFFC84A40);
        planks(p, 58, 143, 48, 3, 0xFF7A5A36, r);
        rect(p, 58, 146, 3, 2, 0xFF4E3220);
        rect(p, 103, 146, 3, 2, 0xFF4E3220);

        // furnace: stone block with a darker top, an iron-framed fire opening
        cobble(p, FX, FY, 32, 32, r);
        rect(p, FX, FY, 32, 3, 0xFF5E5E5E);
        rect(p, FX + 7, FY + 15, 18, 13, 0xFF2E2E2E);
        rect(p, FX + 9, FY + 17, 14, 9, 0xFF140C08);
        for (int x = FX + 9; x < FX + 23; x += 3) rect(p, x, FY + 17, 1, 9, 0xFF3A3A3A);
        rect(p, FX + 8, FY + 6, 16, 2, 0xFF4A4A4A);
        outline(p, FX, FY, 32, 32);

        // workbench: gridded top, planked body with corner posts, a saw and a hammer on the front
        planks(p, 270, 120, 32, 28, 0xFF7A4E2E, r);
        rect(p, 270, 120, 3, 28, 0xFF4E3220);
        rect(p, 299, 120, 3, 28, 0xFF4E3220);
        rect(p, 270, 116, 32, 4, 0xFF6E5030);
        for (int x = 270; x < 302; x += 4) rect(p, x, 116, 1, 4, 0xFF4E3820);
        rect(p, 270, 118, 32, 1, 0xFF4E3820);
        rect(p, 274, 128, 10, 3, 0xFFB9B9B9);
        for (int x = 274; x < 284; x += 2) rect(p, x, 131, 1, 1, 0xFF8A8A8A);
        rect(p, 284, 128, 3, 3, 0xFF5B4128);
        rect(p, 292, 126, 1, 9, 0xFF5B4128);
        rect(p, 290, 125, 5, 2, 0xFF7E7E7E);
        outline(p, 270, 116, 32, 32);

        // item frame: oak border with corner pegs round a leather back (the gear is drawn live)
        rect(p, FRAME_X, FRAME_Y, FRAME_W, FRAME_W, 0xFFB08A58);
        for (int y = FRAME_Y + 2; y < FRAME_Y + FRAME_W - 2; y++)
            for (int x = FRAME_X + 2; x < FRAME_X + FRAME_W - 2; x++) set(p, x, y, shade(0xFF7A4E2E, r.nextInt(11) - 5));
        outline(p, FRAME_X, FRAME_Y, FRAME_W, FRAME_W);
        for (int k = 0; k < 4; k++) rect(p, FRAME_X + k % 2 * (FRAME_W - 3), FRAME_Y + k / 2 * (FRAME_W - 3), 3, 3, 0xFF6E5030);

        // painting: a dusk mountain scene in a dark wooden frame; the clock is shown on it
        int px0 = PAINT_X + 3, py0 = PAINT_Y + 3, pw = PAINT_W - 6, ph = PAINT_H - 6;
        rect(p, PAINT_X, PAINT_Y, PAINT_W, PAINT_H, 0xFF4A3220);
        for (int y = 0; y < ph; y++)
            for (int x = 0; x < pw; x++) set(p, px0 + x, py0 + y, shade(mix(0xFF231E46, 0xFF7A4256, y / (float) ph), r.nextInt(7) - 3));
        for (int x = 0; x < pw; x++) {
            int ridge = ph - 9 - (int) (5 * Math.abs(Math.sin(x / 8.0))) - x / 4 % 3;
            for (int y = ridge; y < ph; y++) set(p, px0 + x, py0 + y, shade(y >= ph - 3 ? 0xFF2A3E26 : 0xFF33304A, r.nextInt(7) - 3));
        }
        rect(p, px0 + 3, py0 + 3, 4, 4, 0xFFE8E2C8);  // moon, in the corner clear of the clock
        outline(p, PAINT_X, PAINT_Y, PAINT_W, PAINT_H);

        // torch sticks and their iron brackets (flames are drawn live)
        for (int tx : TORCH_X) {
            for (int y = TORCH_Y + 3; y < TORCH_Y + 12; y++) {
                p[y * W + tx] = 0xFF6B4A28;
                p[y * W + tx + 1] = 0xFF4E361D;
            }
            rect(p, tx - 1, TORCH_Y + 10, 4, 1, 0xFF5A5A5A);
        }

        // chest: a plank box with darker corner bands, the lid's seam and an iron latch (opened live)
        Random cr = new Random(7);  // its own noise, so the rest of the room and the view keep their texture
        planks(p, CHEST_X, CHEST_Y, 26, 24, 0xFF8E5E2C, cr);
        rect(p, CHEST_X, CHEST_Y, 2, 24, 0xFF6A4420);
        rect(p, CHEST_X + 24, CHEST_Y, 2, 24, 0xFF6A4420);
        rect(p, CHEST_X, CHEST_Y + 8, 26, 1, 0xFF3A2614);
        rect(p, CHEST_X + 11, CHEST_Y + 6, 4, 5, 0xFF5A5A5A);
        rect(p, CHEST_X + 12, CHEST_Y + 7, 2, 3, 0xFFB8B8B8);
        outline(p, CHEST_X, CHEST_Y, 26, 24);
        return p;
    }

    /** Soft shadow where surfaces meet: corners, under furniture, behind what hangs on the wall. Multiplied into every light. */
    private void shadeAmbientOcclusion() {
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++) {
                float o = 1;
                if (y >= 8 && y < 22) o *= 0.62f + 0.38f * (y - 8) / 14f;                // under the ceiling beam
                if (y >= 128 && y < FLOOR) o *= 1 - 0.28f * (y - 128) / 20f;              // wall into floor
                if (y >= FLOOR) o *= 0.8f + 0.2f * (y - FLOOR) / 32f;                     // floor, brighter towards us
                if (x >= 10 && x < 20) o *= 0.7f + 0.3f * (x - 10) / 10f;                  // beside the corner posts
                if (x >= W - 20 && x < W - 10) o *= 0.7f + 0.3f * (W - 10 - x) / 10f;
                boolean onFloor = y >= FLOOR && y < FLOOR + 5;
                if (onFloor && (x >= 15 && x < 53 || x >= 54 && x < 106 || x >= CHEST_X - 2 && x < CHEST_X + 28 || x >= FX - 2 && x < 305))
                    o *= 0.55f + 0.09f * (y - FLOOR);
                if (y >= 124 && y < 128) o *= 0.85f;  // under the trim rail
                if (y >= 116 && y < FLOOR && (x >= 50 && x < 55 || x >= 265 && x < 270)) o *= 0.8f;
                if (dropShadow(x, y, FRAME_X, FRAME_Y, FRAME_W, FRAME_W) || dropShadow(x, y, PAINT_X, PAINT_Y, PAINT_W, PAINT_H)) o *= 0.6f;
                if (y >= WY + WH + 6 && y < WY + WH + 11 && x >= WX - 6 && x < WX + WW + 6) o *= 0.8f;  // under the sill
                ao[y * W + x] = o;
            }
    }

    /** The 2-pixel shadow to the lower right of something hung on the wall. */
    private static boolean dropShadow(int x, int y, int bx, int by, int bw, int bh) {
        return x >= bx + 2 && x < bx + bw + 2 && y >= by + 2 && y < by + bh + 2 && (x >= bx + bw || y >= by + bh);
    }

    /** Hills and two trees seen through the window; transparent where there is sky. */
    private int[] paintHills(Random r) {
        int[] p = new int[WW * WH];
        for (int x = 0; x < WW; x++) {
            int top = ground[x / 4];
            for (int y = top; y < WH; y++)
                p[y * WW + x] = shade(y - top < 2 ? 0xFF5E9E3A : 0xFF7A5634, r.nextInt(13) - 6);
        }
        for (int tx : new int[] {18, 82}) {
            int foot = ground[tx / 4];
            for (int y = foot - 9; y < foot; y++) { p[y * WW + tx] = 0xFF5B4128; p[y * WW + tx + 1] = 0xFF4A3420; }
            for (int y = foot - 17; y < foot - 7; y++)
                for (int x = tx - 4; x < tx + 6; x++)
                    if (r.nextInt(7) != 0) p[y * WW + x] = shade(0xFF3E7A2E, r.nextInt(17) - 8);
        }
        return p;
    }

    /** Boards 4 high and 16 long with staggered ends; each board its own shade, with grain, knots and nails. */
    private static void planks(int[] p, int x0, int y0, int w, int h, int base, Random r) {
        for (int y = y0; y < y0 + h; y++) {
            int row = (y - y0) / 4, inRow = (y - y0) % 4;
            for (int x = x0; x < x0 + w; x++) {
                int along = x - x0 + row * 7, board = along / 16;
                int b = shade(base, (row * 31 + board * 17) % 13 - 6);   // one shade per board
                int c = shade(b, r.nextInt(9) - 4);
                if ((along * 3 + inRow * 7 + row) % 11 == 0) c = shade(c, -12);  // grain streaks
                if (r.nextInt(150) == 0 && inRow == 1) c = shade(b, -40);        // knot
                if (inRow == 3) c = shade(base, -40);                            // gap under each plank
                else if (along % 16 == 0) c = shade(base, -32);                  // board ends
                else if (along % 16 == 1 && inRow == 1 && board % 2 == 0) c = 0xFF9A9A92;  // nail
                else if (inRow == 0) c = shade(c, 12);
                set(p, x, y, c);
            }
        }
    }

    /** Rough stones in staggered rows with dark mortar, each stone its own grey. */
    private static void cobble(int[] p, int x0, int y0, int w, int h, Random r) {
        for (int y = y0; y < y0 + h; y++) {
            int row = (y - y0) / 5, inRow = (y - y0) % 5, off = row % 2 * 3;
            for (int x = x0; x < x0 + w; x++) {
                int col = (x - x0 + off) / 6, inCol = (x - x0 + off) % 6;
                int stone = shade(0xFF7C7C7C, (row * 53 + col * 29) % 31 - 15);
                int c = inRow == 0 || inCol == 0 ? 0xFF464646 : shade(stone, r.nextInt(11) - 5);
                if (inRow == 1 && inCol > 0) c = shade(c, 14);  // lit top edge of each stone
                set(p, x, y, c);
            }
        }
    }

    private static void log(int[] p, int x0, int y0, int w, int h, boolean horizontal, Random r) {
        for (int y = y0; y < y0 + h; y++)
            for (int x = x0; x < x0 + w; x++) {
                int along = horizontal ? x : y, across = horizontal ? y : x;
                int c = shade(0xFF5B4128, r.nextInt(9) - 4);
                if ((across + along / 6) % 3 == 0) c = shade(c, -16);
                set(p, x, y, c);
            }
    }

    private static void outline(int[] p, int x, int y, int w, int h) {
        int c = 0xFF2E2010;
        rect(p, x, y, w, 1, c);
        rect(p, x, y + h - 1, w, 1, c);
        rect(p, x, y, 1, h, c);
        rect(p, x + w - 1, y, 1, h, c);
    }

    private static void rect(int[] p, int x0, int y0, int w, int h, int c) {
        for (int y = y0; y < y0 + h; y++) for (int x = x0; x < x0 + w; x++) set(p, x, y, c);
    }

    private static void set(int[] p, int x, int y, int c) {
        if (x >= 0 && x < W && y >= 0 && y < H) p[y * W + x] = c;
    }

    static int shade(int c, int d) {
        return Color.argb(Color.alpha(c), clamp(Color.red(c) + d), clamp(Color.green(c) + d), clamp(Color.blue(c) + d));
    }

    private static int clamp(int v) { return Math.max(0, Math.min(255, v)); }

    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }

    private static int gray(float f) {
        int v = (int) (255 * f);
        return Color.rgb(v, v, v);
    }

    static int mix(int a, int b, float t) {
        return Color.argb(
                (int) (Color.alpha(a) + (Color.alpha(b) - Color.alpha(a)) * t),
                (int) (Color.red(a) + (Color.red(b) - Color.red(a)) * t),
                (int) (Color.green(a) + (Color.green(b) - Color.green(a)) * t),
                (int) (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t));
    }
}
