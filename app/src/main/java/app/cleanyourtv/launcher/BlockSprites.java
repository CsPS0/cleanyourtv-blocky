package app.cleanyourtv.launcher;

import android.graphics.Bitmap;
import android.graphics.Matrix;

/** Hand-drawn pixel sprites for the block theme, all facing right. Original art, written as rows of palette letters. */
final class BlockSprites {
    private BlockSprites() {}

    private static final String DOG_PAL = " W:e8e4dc w:c2bbaf g:a59e92 d:5e574e k:1e1e1e r:c8302a";
    private static final String CAT_PAL = " o:e09a3a s:a86418 W:f2e6d0 e:6ed04a p:e07a8a";

    /** Wolf-style dog with a red collar, 24 x 15: walk A, walk B, sit with tail up, sit with tail down. */
    static Bitmap[] dog() {
        return new Bitmap[] {
                art(DOG_PAL, new String[] {
                        "................d..d....",
                        "...............gwggwg...",
                        ".ww............WWWWWW...",
                        ".ww............WWWWkW...",
                        "..WW...........WWWWWWWWk",
                        "..WW...........WWWWWWWWW",
                        "...WgggggggggggWWWWWWwww",
                        "...WWWWWWWWWWWWrr.......",
                        "....WWWWWWWWWWWrr.......",
                        "....WWWWWWWWWWWrr.......",
                        "....WWWWWWWWWWWWW.......",
                        "....WwwwwwwwwwwwW.......",
                        ".....ww.WW..ww.WW.......",
                        ".....ww.WW..ww.WW.......",
                        ".....ww.WW..ww.WW......."}),
                art(DOG_PAL, new String[] {
                        "................d..d....",
                        "...............gwggwg...",
                        "...............WWWWWW...",
                        "...............WWWWkW...",
                        "...............WWWWWWWWk",
                        "...............WWWWWWWWW",
                        "....gggggggggggWWWWWWwww",
                        ".wwwWWWWWWWWWWWrr.......",
                        ".wwwWWWWWWWWWWWrr.......",
                        "....WWWWWWWWWWWrr.......",
                        "....WWWWWWWWWWWWW.......",
                        "....WwwwwwwwwwwwW.......",
                        "......WW.ww..Www........",
                        "......WW.ww..Www........",
                        "......WW.ww..Www........"}),
                art(DOG_PAL, new String[] {
                        "................d..d....",
                        "...............gwggwg...",
                        "...............WWWWWW...",
                        "...............WWWWkW...",
                        "...............WWWWWWWWk",
                        "...............WWWWWWWWW",
                        "........gggggggWWWWWWwww",
                        "........WWWWWWWrr.......",
                        "..ww....WWWWWWWrr.......",
                        "..ww....WWWWWWWrr.......",
                        "...wwwgggggggggWW.......",
                        "...wwwWWWWWWWWWWww......",
                        "......WWWWWWWWWWww......",
                        "......WWWWWWWWWWww......",
                        "......WWWWWWWWWWww......"}),
                art(DOG_PAL, new String[] {
                        "................d..d....",
                        "...............gwggwg...",
                        "...............WWWWWW...",
                        "...............WWWWkW...",
                        "...............WWWWWWWWk",
                        "...............WWWWWWWWW",
                        "........gggggggWWWWWWwww",
                        "........WWWWWWWrr.......",
                        "........WWWWWWWrr.......",
                        "........WWWWWWWrr.......",
                        "......gggggggggWW.......",
                        "......WWWWWWWWWWww......",
                        "......WWWWWWWWWWww......",
                        "..wwwwWWWWWWWWWWww......",
                        "..wwwwWWWWWWWWWWww......"}),
        };
    }

    /** Tabby cat sitting, 12 x 10: tail left, tail down, paw swiping right (at the dog). */
    static Bitmap[] cat() {
        String[] head = {"......o..o..", "......oooo..", "......oeop..", "......ooWW..", "....oooWW..."};
        return new Bitmap[] {
                art(CAT_PAL, head, "....sssWW...", "s...oooWW...", "s...sssWW...", ".sssooooo...", "....ooWWW..."),
                art(CAT_PAL, head, "....sssWW...", "....oooWW...", "....sssWW...", "s...ooooo...", ".sssooWWW..."),
                art(CAT_PAL, head, "....sssWWooo", "s...oooWW..W", "s...sssWW...", ".sssooooo...", "....ooWWW..."),
        };
    }

    static Bitmap[] sheep() {
        String pal = " W:e9e9e9 w:c4c4c4 f:d8b493 k:2a2a2a l:b89474";
        String[] body = {"...wwww...", ".wWWWWWWff", "wWWWWWWWfk", "wWWWWWWWff", ".wwWWWWw.."};
        return walk(pal, body, new String[] {".l.l..l.l.", ".l.l..l.l."}, new String[] {"..ll...ll.", "..ll...ll."});
    }

    static Bitmap[] cow() {
        String pal = " b:5b3a24 W:ececec p:e8a0a8 k:1a1a1a h:d8d0b8";
        String[] body = {"........h.", ".bbbbbbbbb", "bWWbbbbbkb", "bWWbbWWbpp", ".bbbbbbbb."};
        return walk(pal, body, new String[] {".b.b..b.b.", ".b.b..b.b."}, new String[] {"..bb...bb.", "..bb...bb."});
    }

    static Bitmap[] pig() {
        String pal = " p:f0a6a6 q:d07a7a s:e98a96 k:1a1a1a";
        String[] body = {"..pppppp..", ".pppppppkp", "qpppppppss", ".pppppppp."};
        return walk(pal, body, new String[] {".q.q..q.q."}, new String[] {"..q.q..q.q"});
    }

    static Bitmap[] chicken() {
        String pal = " W:f2f2f2 y:e8a030 r:d02828 k:1a1a1a";
        String[] body = {"..WW.", "..Wky", "WWWWr", "WWWW."};
        return walk(pal, body, new String[] {".y.y."}, new String[] {"..yy."});
    }

    static Bitmap[] zombie() {
        String pal = " g:4e8a3a G:3c6e2c c:2e8a8a u:3a3a8a k:1a1a1a";
        String[] body = {"ggg...", "ggk...", "GGG...", "ccgggg", "cc....", "cc....", "uu...."};
        return walk(pal, body, new String[] {"u.u...", "u.u..."}, new String[] {".uu...", ".uu..."});
    }

    static Bitmap[] skeleton() {
        String pal = " B:d6d6d6 d:6a6a6a o:7a5230";
        String[] body = {"BBB...", "BBd...", "BBB...", ".B.o..", ".BBBo.", ".B..o.", ".B...."};
        return walk(pal, body, new String[] {"B.B...", "B.B..."}, new String[] {".BB...", ".BB..."});
    }

    static Bitmap[] spider() {
        String pal = " d:2e2622 D:1c1614 R:ff3030";
        String[] body = {"..dddd....", ".dddddDDRR", "dddddddddd"};
        return walk(pal, body, new String[] {"d.d.d.d.d.", "d...d...d."}, new String[] {".d.d.d.d.d", ".d...d...d"});
    }

    private static final String PLAYER_PAL = " h:4a2e1a s:e0a878 e:2a2a6a t:3a7a3a a:2e602e b:5a3a1a p:4a4a6a o:3a2a1a";
    private static final String[] PLAYER_HEAD = {".hhhh...", ".hhsss..", ".hssse..", ".hssss.."};
    private static final String[] LEGS_APART = {".pppp...", ".pppp...", ".p..p...", ".p..p...", ".o..o..."};
    private static final String[] LEGS_TOGETHER = {".pppp...", ".pppp...", "..pp....", "..pp....", "..oo...."};

    /** An original blocky farmer-adventurer, 8 x 14: walk A, walk B, arm raised, arm forward (tools drawn on top). */
    static Bitmap[] player() {
        String[] arms = {".tttt...", ".ttat...", ".ttat...", ".ttat...", ".bbsb..."};
        String[] up = {".tttta..", ".tttt...", ".tttt...", ".tttt...", ".bbbb..."};
        String[] forward = {".tttt...", ".tttt...", ".ttttaas", ".tttt...", ".bbbb..."};
        String[] raisedHead = {".hhhh...", ".hhsss..", ".hssse..", ".hssssss"};
        return new Bitmap[] {
                art(PLAYER_PAL, concat(PLAYER_HEAD, arms), LEGS_APART),
                art(PLAYER_PAL, concat(PLAYER_HEAD, arms), LEGS_TOGETHER),
                art(PLAYER_PAL, concat(raisedHead, up), LEGS_TOGETHER),
                art(PLAYER_PAL, concat(PLAYER_HEAD, forward), LEGS_APART),
        };
    }

    private static final int SHIRT = 0xFF3A7A3A, BELT = 0xFF5A3A1A, BUCKLE = 0xFFC8A040, PANTS = 0xFF4A4A6A, BOOTS = 0xFF3A2A1A;
    private static final int HAIR = 0xFF4A2E1A, SKIN = 0xFFE0A878, EYE = 0xFF2A2A6A;

    /**
     * The same farmer at the cabin's scale: side view, 26 x 41, facing right. Each limb turns about its joint;
     * 0 degrees hangs straight down and positive swings forward. The far arm and leg are shaded darker.
     */
    static Bitmap figure(float nearLeg, float farLeg, float nearArm, float farArm) {
        int w = 26, h = 41;
        int[] p = new int[w * h];
        limb(p, w, 10, 15, farArm, 12, 4, BlockRoom.shade(SHIRT, -30), BlockRoom.shade(SKIN, -30), 4);
        limb(p, w, 10, 25, farLeg, 15, 4, BlockRoom.shade(PANTS, -25), BlockRoom.shade(BOOTS, -20), 12);
        limb(p, w, 10, 25, nearLeg, 15, 4, PANTS, BOOTS, 12);
        fill(p, w, 8, 13, 5, 11, SHIRT);
        fill(p, w, 8, 13, 1, 11, BlockRoom.shade(SHIRT, -18));  // its back, turned from the light
        fill(p, w, 8, 24, 5, 1, BELT);
        fill(p, w, 11, 24, 2, 1, BUCKLE);
        head(p, w, 5, 3, false);
        limb(p, w, 10, 15, nearArm, 12, 4, SHIRT, SKIN, 4);
        return Bitmap.createBitmap(p, w, h, Bitmap.Config.ARGB_8888);
    }

    /** The farmer asleep: the head lying face up with its crown to the headboard (10 x 10), and the blanket over the body (32 x 6). */
    static Bitmap[] sleeper() {
        int[] p = new int[100];
        head(p, 10, 0, 0, true);
        Matrix m = new Matrix();
        m.postRotate(-90);
        Bitmap head = Bitmap.createBitmap(Bitmap.createBitmap(p, 10, 10, Bitmap.Config.ARGB_8888), 0, 0, 10, 10, m, false);
        int w = 32, h = 6;
        int[] b = new int[w * h];
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int top = x < 2 ? 1 : x < 14 ? 0 : x < 22 ? 1 : 2 + (x - 22) / 4;  // shoulders, then lower over the legs
                if (y >= top) b[y * w + x] = y == top ? 0xFFC84A40 : (x + y) % 6 < 3 ? 0xFFB0302A : 0xFF9C2A24;
            }
        return new Bitmap[] {head, Bitmap.createBitmap(b, w, h, Bitmap.Config.ARGB_8888)};
    }

    /** A chest's lid swung open, its inside face above the chest, over the dark inside with loot glinting. 26 x 17. */
    static Bitmap chestOpen() {
        return art(" k:2a1a0e L:7a4e26 l:5e3c1c d:1a100a y:f0c040 g:40d070", new String[] {
                "kkkkkkkkkkkkkkkkkkkkkkkkkk",
                "kLLLLLLLLLLLLLLLLLLLLLLLLk",
                "kLllllllllllllllllllllllLk",
                "kLllllllllllllllllllllllLk",
                "kLllllllllllllllllllllllLk",
                "kLllllllllllllllllllllllLk",
                "kLLLLLLLLLLLLLLLLLLLLLLLLk",
                "kkkkkkkkkkkkkkkkkkkkkkkkkk",
                "kddddddddddddddddddddddddk",
                "kddddddddddddddddddddddddk",
                "kdddyyddddddddgdddddyydddk",
                "kddyyyydddddddggddddyyyddk",
                "kddddddddddddddddddddddddk",
                "kddddddddddddddddddddddddk",
                "kddddddddddddddddddddddddk",
                "kddddddddddddddddddddddddk",
                "kkkkkkkkkkkkkkkkkkkkkkkkkk"});
    }

    /** A 10 x 10 head facing right: hair over the top and back, one eye (shut when asleep), a mouth. */
    private static void head(int[] p, int w, int x0, int y0, boolean asleep) {
        fill(p, w, x0, y0, 10, 10, SKIN);
        fill(p, w, x0, y0, 10, 3, HAIR);
        fill(p, w, x0, y0, 3, 7, HAIR);
        fill(p, w, x0 + 9, y0 + 3, 1, 1, HAIR);  // fringe
        if (asleep) {
            fill(p, w, x0 + 7, y0 + 5, 2, 1, 0xFF8A5A3A);
        } else {
            fill(p, w, x0 + 7, y0 + 4, 1, 2, 0xFFF4F4F4);
            fill(p, w, x0 + 8, y0 + 4, 1, 2, EYE);
        }
        fill(p, w, x0 + 8, y0 + 8, 2, 1, BlockRoom.shade(SKIN, -40));
    }

    /** A limb width pixels wide, len long, turned deg about (px, py); its first split pixels are upper (sleeve, trouser), the rest lower. */
    private static void limb(int[] p, int w, float px, float py, float deg, int len, float width, int upper, int lower, int split) {
        double a = Math.toRadians(deg), dx = Math.sin(a), dy = Math.cos(a);
        for (int i = 0; i < p.length; i++) {
            double rx = i % w + 0.5 - px, ry = i / w + 0.5 - py;
            double along = rx * dx + ry * dy, across = rx * dy - ry * dx;
            if (along >= -width / 2 && along < len && Math.abs(across) < width / 2) p[i] = along < split ? upper : lower;
        }
    }

    private static void fill(int[] p, int w, int x0, int y0, int fw, int fh, int c) {
        for (int y = y0; y < y0 + fh; y++)
            for (int x = x0; x < x0 + fw; x++) p[y * w + x] = c;
    }

    private static String[] concat(String[] a, String[] b) {
        String[] r = new String[a.length + b.length];
        System.arraycopy(a, 0, r, 0, a.length);
        System.arraycopy(b, 0, r, a.length, b.length);
        return r;
    }

    /** Settings gear, 16 x 16, lit from the top left; it sits in the item frame. */
    static Bitmap gear() {
        return art(" l:e2e2e2 g:a8a8a8 d:5e5e5e", new String[] {
                "................",
                ".......ll.......",
                "...l...ll...g...",
                "..lllllllggggg..",
                "...lllllggggg...",
                "...llllddgggg...",
                "...llld..dggg...",
                ".lllld....dgddd.",
                ".lllgd....ddddd.",
                "...gggd..dddd...",
                "...ggggdddddd...",
                "...gggggddddd...",
                "..gggggddddddd..",
                "...g...dd...d...",
                ".......dd.......",
                "................"});
    }

    private static Bitmap[] walk(String pal, String[] body, String[] legsA, String[] legsB) {
        return new Bitmap[] {art(pal, body, legsA), art(pal, body, legsB)};
    }

    private static Bitmap art(String pal, String[] body, String... legs) {
        String[] rows = new String[body.length + legs.length];
        System.arraycopy(body, 0, rows, 0, body.length);
        System.arraycopy(legs, 0, rows, body.length, legs.length);
        int w = 0;
        for (String r : rows) w = Math.max(w, r.length());
        int[] px = new int[w * rows.length];  // short rows end in transparent pixels
        for (int y = 0; y < rows.length; y++)
            for (int x = 0; x < rows[y].length(); x++) {
                char ch = rows[y].charAt(x);
                if (ch == '.') continue;
                int i = pal.indexOf(" " + ch + ":");
                if (i < 0) throw new IllegalArgumentException("no colour for '" + ch + "'");
                px[y * w + x] = 0xFF000000 | Integer.parseInt(pal.substring(i + 3, i + 9), 16);
            }
        return Bitmap.createBitmap(px, w, rows.length, Bitmap.Config.ARGB_8888);
    }
}
