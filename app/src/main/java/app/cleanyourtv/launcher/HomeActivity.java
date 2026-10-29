package app.cleanyourtv.launcher;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class HomeActivity extends Activity {
    private static final int TOP = 0, DOCK = 1, GRID = 2, MENU = 3;
    private static final float FOCUS_SCALE = 1.08f;

    static final class App {
        final String pkg, label;
        final ComponentName component;
        final Drawable art, icon;
        final boolean banner;
        Integer glow;
        BitmapDrawable sprite;

        App(ResolveInfo ri, PackageManager pm) {
            pkg = ri.activityInfo.packageName;
            label = ri.loadLabel(pm).toString();
            component = new ComponentName(pkg, ri.activityInfo.name);
            Drawable b = ri.activityInfo.loadBanner(pm);
            banner = b != null;
            icon = ri.loadIcon(pm);
            art = banner ? b : icon;
        }
    }

    static final class Tile extends RecyclerView.ViewHolder {
        final FrameLayout card;
        final ImageView art;
        final TextView label;

        Tile(View root, FrameLayout card, ImageView art, TextView label) {
            super(root);
            this.card = card;
            this.art = art;
            this.label = label;
        }
    }

    private final List<App> dock = new ArrayList<>(), grid = new ArrayList<>();
    private final List<Tile> dockTiles = new ArrayList<>();
    private final List<TextView> pills = new ArrayList<>(), menuItems = new ArrayList<>();
    private final List<Runnable> pillActions = new ArrayList<>();
    private final List<Runnable> menuActions = new ArrayList<>();

    private ScrollView scroll;
    private FrameLayout page;
    private Highlight highlight;
    private LinearLayout dockRow, pillRow, menu;
    private RecyclerView gridView;
    private TextView dockName, greeting, netPill, guardPill;
    private ImageView wallpaper;
    private View glow;
    private final GradientDrawable glowDrawable = new GradientDrawable();
    private ValueAnimator glowAnim;
    private int glowColor = Color.TRANSPARENT;
    private long wallpaperStamp = -1;

    private Typeface outfit, pixelFont;
    private boolean blocky;
    private int u;  // block theme: one art pixel, in screen pixels
    private TextView tooltip;
    private BlockRoom room;
    private final Map<String, String[]> blockLetters = new java.util.HashMap<>();
    private final Runnable fadeName = () -> dockName.animate().alpha(0f).setDuration(400).start();
    private SharedPreferences prefs;
    private int columns, cellW, tileW, cardH, screenW, screenH;
    private int zone = DOCK, topIdx, dockIdx, gridIdx, menuIdx;
    private View focusedCard;
    private boolean hidden, selectDown;
    private ConnectivityManager.NetworkCallback netCallback;

    private final RecyclerView.Adapter<Tile> adapter = new RecyclerView.Adapter<Tile>() {
        @Override public Tile onCreateViewHolder(ViewGroup parent, int type) { return newTile(parent, true); }
        @Override public void onBindViewHolder(Tile t, int i) { bind(t, grid.get(i)); }
        @Override public int getItemCount() { return grid.size(); }
    };

    private final BroadcastReceiver packages = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { reload(false); }
    };

    private final BroadcastReceiver tick = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { updateGreeting(); }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_home);
        prefs = getPreferences(MODE_PRIVATE);
        outfit = getResources().getFont(R.font.outfit);
        blocky = "blocky".equals(prefs.getString("theme", null));

        scroll = findViewById(R.id.scroll);
        page = findViewById(R.id.page);
        highlight = findViewById(R.id.highlight);
        dockRow = findViewById(R.id.dock);
        pillRow = findViewById(R.id.pills);
        menu = findViewById(R.id.menu);
        gridView = findViewById(R.id.grid);
        dockName = findViewById(R.id.dock_name);
        greeting = findViewById(R.id.greeting);
        wallpaper = findViewById(R.id.wallpaper);
        glow = findViewById(R.id.glow);
        TextClock clock = findViewById(R.id.clock), date = findViewById(R.id.date);

        DisplayMetrics dm = getResources().getDisplayMetrics();
        screenW = dm.widthPixels;
        screenH = dm.heightPixels;
        if (blocky) {
            u = Math.max(1, screenW / BlockRoom.W);
            pixelFont = getResources().getFont(R.font.monocraft);
            columns = 9;
            cellW = tileW = cardH = 20 * u;
            findViewById(R.id.top).getLayoutParams().height = screenH - 28 * u;
            styleBlocky();
        } else {
            columns = Math.max(1, getResources().getInteger(R.integer.columns));
            cellW = (screenW - 2 * dp(64)) / columns;
            tileW = cellW - dp(24);
            cardH = tileW * 9 / 16;
            findViewById(R.id.top).getLayoutParams().height = screenH - (cardH + 2 * dp(22)) - dp(36);

            dockRow.setBackground(glass(0xFF111113, dp(30), 0x14FFFFFF));
            menu.setBackground(glass(0xFF1C1C1E, dp(18), 0x1FFFFFFF));
            glowDrawable.setGradientType(GradientDrawable.RADIAL_GRADIENT);
            glowDrawable.setGradientRadius(dp(300));
            glowDrawable.setGradientCenter(0.3f, 0.5f);
            glowDrawable.setColors(new int[] {Color.TRANSPARENT, Color.TRANSPARENT});
            glow.setBackground(glowDrawable);
        }

        font(greeting, 300);
        font(clock, 120);
        font(date, 300);
        font(dockName, 300);
        String datePattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEEMMMMd");
        date.setFormat12Hour(datePattern);
        date.setFormat24Hour(datePattern);
        if (blocky) placeClockOnPainting(clock, date);

        // shortcut: decided once per launch; re-enabling the Telekom app needs a launcher restart to bring Inputs back.
        if (blocky) {
            settingsGear();
        } else {
            if (hasLiveTv()) pill("Inputs", this::openInputsMenu);
            netPill = pill("Network", () -> { if (!open(new Intent(Settings.ACTION_WIFI_SETTINGS))) open(new Intent(Settings.ACTION_SETTINGS)); });
            pill("Settings", this::openSettingsMenu);
        }

        guardPill = warningPill();

        gridView.setLayoutManager(new GridLayoutManager(this, columns));
        gridView.setItemAnimator(null);
        gridView.setNestedScrollingEnabled(false);
        gridView.setAdapter(adapter);

        reload(true);

        // First start after boot: fade the home screen up out of the boot animation's black.
        if (SystemClock.elapsedRealtime() < 3 * 60_000) {
            page.setAlpha(0f);
            page.setTranslationY(dp(24));
            page.animate().alpha(1f).translationY(0).setStartDelay(200).setDuration(1200)
                    .setInterpolator(new DecelerateInterpolator(2f)).start();
        }

        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_CHANGED);
        f.addDataScheme("package");
        registerReceiver(packages, f);
    }

    @Override protected void onStart() {
        super.onStart();
        if (!blocky) loadWallpaper();
        ConnectivityManager cm = getSystemService(ConnectivityManager.class);
        netCallback = new ConnectivityManager.NetworkCallback() {
            @Override public void onAvailable(Network n) { runOnUiThread(HomeActivity.this::updateNetwork); }
            @Override public void onLost(Network n) { runOnUiThread(HomeActivity.this::updateNetwork); }
            @Override public void onCapabilitiesChanged(Network n, NetworkCapabilities c) { runOnUiThread(HomeActivity.this::updateNetwork); }
        };
        cm.registerDefaultNetworkCallback(netCallback);
        updateNetwork();
    }

    /** Read by the screensaver: it zooms out of the painting only when Home was what was on screen. */
    static volatile boolean inFront;
    static volatile long leftAt;

    @Override protected void onResume() {
        super.onResume();
        inFront = true;
        hidden = false;
        updateGreeting();
        updateGuard();
        registerReceiver(tick, new IntentFilter(Intent.ACTION_TIME_TICK));
    }

    @Override protected void onPause() {
        super.onPause();
        inFront = false;
        leftAt = SystemClock.uptimeMillis();
        unregisterReceiver(tick);
        selectDown = false;
    }

    @Override protected void onStop() {
        super.onStop();
        hidden = true;
        getSystemService(ConnectivityManager.class).unregisterNetworkCallback(netCallback);
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(packages);
    }

    /** Home pressed while already home: back to the dock. Coming back from an app keeps the last tile. */
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (hidden) return;
        menu.setVisibility(View.GONE);
        zone = DOCK;
        applyFocus(true);
    }

    // ---- apps -------------------------------------------------------------------------------

    private void reload(boolean restore) {
        PackageManager pm = getPackageManager();
        Set<String> hiddenApps = new HashSet<>(Arrays.asList(getResources().getStringArray(R.array.hidden_apps)));
        Map<String, App> apps = new LinkedHashMap<>();
        Intent q = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        for (ResolveInfo ri : pm.queryIntentActivities(q, 0)) {
            String p = ri.activityInfo.packageName;
            if (!p.equals(getPackageName()) && !hiddenApps.contains(p) && !apps.containsKey(p)) apps.put(p, new App(ri, pm));
        }
        dock.clear();
        grid.clear();
        for (String p : getResources().getStringArray(R.array.dock_apps)) {
            App a = apps.remove(p);
            if (a != null && dock.size() < columns) dock.add(a);
        }
        grid.addAll(apps.values());
        Collator c = Collator.getInstance();
        grid.sort((a, b) -> c.compare(a.label, b.label));
        if (blocky) while (dock.size() < columns && !grid.isEmpty()) dock.add(grid.remove(0));  // a full hotbar

        dockRow.removeAllViews();
        dockTiles.clear();
        for (App a : dock) {
            Tile t = newTile(dockRow, false);
            bind(t, a);
            dockRow.addView(t.itemView);
            dockTiles.add(t);
        }
        adapter.notifyDataSetChanged();
        focusedCard = null;

        if (restore) {
            String pkg = prefs.getString("pkg", null);
            zone = DOCK;
            for (int i = 0; i < dock.size(); i++) if (dock.get(i).pkg.equals(pkg)) dockIdx = i;
            for (int i = 0; i < grid.size(); i++) if (grid.get(i).pkg.equals(pkg)) { zone = GRID; gridIdx = i; }
        }
        afterLayout(() -> applyFocus(false));
    }

    private Tile newTile(ViewGroup parent, boolean withLabel) {
        boolean inGrid = parent instanceof RecyclerView;
        int w = blocky && inGrid ? 18 * u : tileW, h = blocky ? w : cardH;
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER_HORIZONTAL);
        cell.setLayoutParams(inGrid
                ? new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                : new LinearLayout.LayoutParams(cellW, ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout card = new FrameLayout(this);
        if (!blocky) card.setBackground(glass(0xFF1C1C1E, dp(14), 0));
        else if (inGrid) card.setBackground(BlockArt.box(u, 0, 0xFF8B8B8B, 0xFF373737, 0xFFFFFFFF));
        else card.setBackground(BlockArt.box(u, 0xFF6B6B6B, 0x60000000, 0, 0));  // with the bar's own edge: 2 px grey on every side
        card.setClipToOutline(!blocky);
        ImageView art = new ImageView(this);
        card.addView(art, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        cell.addView(card, new LinearLayout.LayoutParams(w, h));

        TextView label = null;
        if (withLabel && !blocky) {
            label = new TextView(this);
            font(label, 400);
            label.setTextSize(17);
            label.setTextColor(0xE6FFFFFF);
            label.setSingleLine();
            label.setEllipsize(TextUtils.TruncateAt.END);
            label.setGravity(Gravity.CENTER);
            label.setAlpha(0f);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(tileW, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = dp(14);
            lp.bottomMargin = dp(18);
            cell.addView(label, lp);
        }
        return new Tile(cell, card, art, label);
    }

    private void bind(Tile t, App a) {
        if (blocky) {
            t.art.setImageDrawable(sprite(a));
            int pad = (t.card.getLayoutParams().width - 16 * u) / 2;  // a 16-pixel sprite in an 18- or 20-pixel slot
            t.art.setPadding(pad, pad, pad, pad);
            t.art.setScaleType(ImageView.ScaleType.FIT_XY);
            return;
        }
        t.art.setImageDrawable(a.art);
        int pad = a.banner ? 0 : cardH / 4;
        t.art.setPadding(pad, pad, pad, pad);
        t.art.setScaleType(a.banner ? ImageView.ScaleType.CENTER_CROP : ImageView.ScaleType.FIT_CENTER);
        t.card.setScaleX(1f);
        t.card.setScaleY(1f);
        if (t.label != null) {
            t.label.setText(a.label);
            t.label.setAlpha(0f);
        }
    }

    private void launch(App a) {
        open(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER).setComponent(a.component)
                .addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));
    }

    /** A visible TV input or the Telekom app (absent when disabled) means Inputs has something to offer. */
    private boolean hasLiveTv() {
        TvInputManager tim = (TvInputManager) getSystemService(TV_INPUT_SERVICE);
        if (tim != null) for (TvInputInfo in : tim.getTvInputList())
            if (in.getParentId() == null && !in.isHidden(this)) return true;
        return getPackageManager().resolveActivity(new Intent(Intent.ACTION_MAIN)
                .setComponent(ComponentName.unflattenFromString(getString(R.string.live_tv_component))), 0) != null;
    }

    private void openLiveTv() {
        if (!open(new Intent(Intent.ACTION_MAIN).setComponent(ComponentName.unflattenFromString(getString(R.string.live_tv_component)))))
            Toast.makeText(this, "Live TV app not found", Toast.LENGTH_SHORT).show();
    }

    private boolean open(Intent i) {
        try {
            startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    // ---- focus ------------------------------------------------------------------------------

    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        int k = e.getKeyCode();
        boolean select = k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER || k == KeyEvent.KEYCODE_NUMPAD_ENTER;
        boolean nav = k == KeyEvent.KEYCODE_DPAD_UP || k == KeyEvent.KEYCODE_DPAD_DOWN
                || k == KeyEvent.KEYCODE_DPAD_LEFT || k == KeyEvent.KEYCODE_DPAD_RIGHT || k == KeyEvent.KEYCODE_BACK;
        if (k == KeyEvent.KEYCODE_TV) {
            if (e.getAction() == KeyEvent.ACTION_UP) openLiveTv();
            return true;
        }
        if (!select && !nav) return super.dispatchKeyEvent(e);
        if (select) {
            // Act on release, and only for a press that started here (not one carried over from the app we left).
            if (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) selectDown = true;
            else if (e.getAction() == KeyEvent.ACTION_UP && selectDown) { selectDown = false; activate(); }
            return true;
        }
        if (e.getAction() == KeyEvent.ACTION_DOWN && (k == KeyEvent.KEYCODE_BACK ? back() : move(k))) applyFocus(true);
        return true;
    }

    private boolean back() {
        if (zone == MENU) { closeMenu(); return true; }
        if (zone == DOCK || dock.isEmpty()) return false;
        zone = DOCK;
        return true;
    }

    /** Deterministic D-pad: one row per press, ends of each row are walls. Returns whether focus moved. */
    private boolean move(int k) {
        boolean left = k == KeyEvent.KEYCODE_DPAD_LEFT, right = k == KeyEvent.KEYCODE_DPAD_RIGHT;
        boolean up = k == KeyEvent.KEYCODE_DPAD_UP, down = k == KeyEvent.KEYCODE_DPAD_DOWN;
        switch (zone) {
            case TOP:
                if (left && topIdx > 0) { topIdx--; return true; }
                if (right && topIdx < pills.size() - 1) { topIdx++; return true; }
                if (down && (!dock.isEmpty() || !grid.isEmpty())) { zone = dock.isEmpty() ? GRID : DOCK; return true; }
                return false;
            case MENU:
                if (up && menuIdx > 0) { menuIdx--; return true; }
                if (down && menuIdx < menuItems.size() - 1) { menuIdx++; return true; }
                if (left || right) { closeMenu(); return true; }
                return false;
            case DOCK:
                if (left && dockIdx > 0) { dockIdx--; return true; }
                if (right && dockIdx < dock.size() - 1) { dockIdx++; return true; }
                if (up) { zone = TOP; return true; }
                if (down && !grid.isEmpty()) { zone = GRID; gridIdx = Math.min(dockIdx, Math.min(columns, grid.size()) - 1); return true; }
                return false;
            default:
                int col = gridIdx % columns;
                if (left && col > 0) { gridIdx--; return true; }
                if (right && col < columns - 1 && gridIdx + 1 < grid.size()) { gridIdx++; return true; }
                if (up && gridIdx >= columns) { gridIdx -= columns; return true; }
                if (up) {
                    if (dock.isEmpty()) zone = TOP;
                    else { zone = DOCK; dockIdx = Math.min(col, dock.size() - 1); }
                    return true;
                }
                if (down && gridIdx / columns < (grid.size() - 1) / columns) {
                    gridIdx = Math.min(gridIdx + columns, grid.size() - 1);
                    return true;
                }
                return false;
        }
    }

    private void activate() {
        switch (zone) {
            case TOP:
                pillActions.get(topIdx).run();
                break;
            case MENU:
                Runnable r = menuActions.get(menuIdx);
                closeMenu();
                applyFocus(false);
                r.run();
                break;
            case DOCK:
                launch(dock.get(dockIdx));
                break;
            default:
                launch(grid.get(gridIdx));
        }
    }

    private void applyFocus(boolean animate) {
        if (zone == DOCK && dock.isEmpty()) zone = grid.isEmpty() ? TOP : GRID;
        if (zone == GRID && grid.isEmpty()) zone = dock.isEmpty() ? TOP : DOCK;
        dockIdx = Math.max(0, Math.min(dockIdx, dock.size() - 1));
        gridIdx = Math.max(0, Math.min(gridIdx, grid.size() - 1));

        View target;
        float radius;
        View card = null;
        if (zone == TOP) {
            target = pills.get(topIdx);
            radius = target.getHeight() / 2f;
        } else if (zone == MENU) {
            target = menuItems.get(menuIdx);
            radius = dp(10);
        } else if (zone == DOCK) {
            card = target = dockTiles.get(dockIdx).card;
            radius = dp(14) * FOCUS_SCALE + dp(7);
        } else {
            Tile t = (Tile) gridView.findViewHolderForAdapterPosition(gridIdx);
            if (t == null) return;
            card = target = t.card;
            radius = dp(14) * FOCUS_SCALE + dp(7);
        }

        if (focusedCard != card) {
            if (focusedCard != null) setTileFocus(focusedCard, false);
            if (card != null) setTileFocus(card, true);
            focusedCard = card;
        }
        if (blocky) moveBlockHighlight(target);
        else highlight.moveTo(rectInPage(target, card != null ? FOCUS_SCALE : 1f, card != null ? dp(7) : 0), radius, animate);
        updateDockName(animate);
        scrollFor(animate);

        if (zone == DOCK) prefs.edit().putString("pkg", dock.get(dockIdx).pkg).apply();
        if (zone == GRID) prefs.edit().putString("pkg", grid.get(gridIdx).pkg).apply();
    }

    private void setTileFocus(View card, boolean on) {
        if (blocky) return;  // slots don't grow; the selector frame shows focus
        card.animate().scaleX(on ? FOCUS_SCALE : 1f).scaleY(on ? FOCUS_SCALE : 1f).setDuration(200).start();
        ViewGroup cell = (ViewGroup) card.getParent();
        if (cell != null && cell.getChildCount() > 1) cell.getChildAt(1).animate().alpha(on ? 1f : 0f).setDuration(200).start();
    }

    private RectF rectInPage(View v, float scale, float outset) {
        Rect r = new Rect(0, 0, v.getWidth(), v.getHeight());
        page.offsetDescendantRectToMyCoords(v, r);
        float dx = r.width() * (scale - 1) / 2 + outset, dy = r.height() * (scale - 1) / 2 + outset;
        return new RectF(r.left - dx, r.top - dy, r.right + dx, r.bottom + dy);
    }

    private void scrollFor(boolean animate) {
        int y = 0;
        if (zone == GRID) {
            View row = gridView.getLayoutManager().findViewByPosition(gridIdx);
            if (row != null) y = Math.max(0, (int) rectInPage(row, 1f, 0).top - screenH / 2 + dp(40));
        }
        if (animate) scroll.smoothScrollTo(0, y);
        else scroll.scrollTo(0, y);
    }

    private void updateDockName(boolean animate) {
        boolean show = zone == DOCK;
        if (blocky) {  // the name pops up over the hotbar and fades after a moment
            dockName.removeCallbacks(fadeName);
            dockName.animate().cancel();
            dockName.setAlpha(show ? 1f : 0f);
            if (show) {
                dockName.setText(dock.get(dockIdx).label);
                dockName.postDelayed(fadeName, 2500);
            }
            return;
        }
        if (show) {
            App a = dock.get(dockIdx);
            dockName.setText(a.label);
            setGlow(glowOf(a), animate);
        }
        long d = animate ? 220 : 0;
        dockName.animate().alpha(show ? 1f : 0f).setDuration(d).start();
        glow.animate().alpha(show ? 1f : 0.3f).setDuration(d).start();
    }

    // ---- top bar ----------------------------------------------------------------------------

    private TextView pill(String text, Runnable action) {
        TextView t = new TextView(this);
        t.setTextSize(17);
        font(t, 500);
        t.setTextColor(0xE6FFFFFF);
        t.setText(text);
        t.setPadding(dp(22), dp(10), dp(22), dp(10));
        t.setBackground(blocky ? BlockArt.box(u, 0xFF000000, 0xFF6F6F6F, 0xFFA8A8A8, 0xFF3A3A3A)
                : glass(0x26FFFFFF, dp(999), 0x22FFFFFF));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMarginStart(dp(12));
        pillRow.addView(t, lp);
        pills.add(t);
        pillActions.add(action);
        return t;
    }

    /** A non-focusable red alert in the top row; updateGuard() shows it when a guarded telemetry app is back on. */
    private TextView warningPill() {
        TextView t = new TextView(this);
        t.setTextSize(17);
        font(t, 500);
        t.setTextColor(0xFFFFFFFF);
        t.setSingleLine(true);
        t.setText("⚠ Telekom telemetry on — open Settings");
        t.setPadding(dp(22), dp(10), dp(22), dp(10));
        t.setBackground(blocky ? BlockArt.box(u, 0xFF7A0E0E, 0xFFC63A3A, 0xFFE06A6A, 0xFF4A0808)
                : glass(0x66E53935, dp(999), 0x55FF6B6B));
        t.setVisibility(View.GONE);
        // Placed on the page, not the theme's pill row, so it reads as one line in both themes.
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        lp.topMargin = dp(18);
        page.addView(t, lp);
        return t;
    }

    private void updateGuard() {
        if (guardPill != null) guardPill.setVisibility(enabledGuardPackages().isEmpty() ? View.GONE : View.VISIBLE);
    }

    /** Guarded Telekom telemetry packages we disabled; returns those a firmware/app update turned back on. */
    private List<String> enabledGuardPackages() {
        List<String> back = new ArrayList<>();
        PackageManager pm = getPackageManager();
        for (String p : getResources().getStringArray(R.array.guard_telemetry)) {
            try {
                if (pm.getApplicationInfo(p, 0).enabled) back.add(p);
            } catch (PackageManager.NameNotFoundException ignored) { }  // still disabled, or not on this box
        }
        return back;
    }

    /** Panic button: no reset activity is standard across boxes, so try the known ones, then fall back to Settings. */
    private void factoryReset() {
        Toast.makeText(this, "Choose Factory data reset / Reset to wipe back to stock", Toast.LENGTH_LONG).show();
        if (open(new Intent("android.settings.FACTORY_RESET"))) return;
        if (open(new Intent().setClassName("com.android.tv.settings", "com.android.tv.settings.device.StorageResetActivity"))) return;
        open(new Intent(Settings.ACTION_SETTINGS));
    }

    private void openInputsMenu() {
        clearMenu();
        TvInputManager tim = (TvInputManager) getSystemService(TV_INPUT_SERVICE);
        if (tim != null) for (TvInputInfo in : tim.getTvInputList()) {
            if (in.getParentId() != null || in.isHidden(this)) continue;
            menuItem(in.getType() == TvInputInfo.TYPE_COMPOSITE ? "AV" : in.loadLabel(this), () -> openInput(in));
        }
        menuItem("Live TV", this::openLiveTv);
        showMenu();
    }

    /** Our own look for the top of Settings; each entry opens that page of the stock TV Settings. */
    private void openSettingsMenu() {
        clearMenu();
        menuItem("Theme: " + (blocky ? "Blocky" : "Clean"), () -> {
            prefs.edit().putString("theme", blocky ? "clean" : "blocky").apply();
            recreate();
        });
        settingsItem("Network & internet", Settings.ACTION_WIFI_SETTINGS, null);
        settingsItem("Display & sound", Settings.ACTION_SOUND_SETTINGS, ".device.displaysound.DisplaySoundActivity");
        settingsItem("Apps", Settings.ACTION_APPLICATION_SETTINGS, ".device.apps.AppsActivity");
        settingsItem("Accounts", Settings.ACTION_SYNC_SETTINGS, null);
        settingsItem("Date & time", Settings.ACTION_DATE_SETTINGS, null);
        settingsItem("About", Settings.ACTION_DEVICE_INFO_SETTINGS, null);
        settingsItem("All settings", Settings.ACTION_SETTINGS, null);
        PackageManager pm = getPackageManager();
        for (String p : enabledGuardPackages()) {
            CharSequence label;
            try { label = pm.getApplicationLabel(pm.getApplicationInfo(p, 0)); }
            catch (PackageManager.NameNotFoundException e) { label = p; }
            menuItem("Disable " + label, () -> {
                if (!open(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + p))))
                    open(new Intent(Settings.ACTION_APPLICATION_SETTINGS));
            });
        }
        menuItem("Restore to stock (factory reset)", this::factoryReset);
        showMenu();
    }

    /** cls pins the TV Settings screen where the action alone would also match a vendor (Amlogic) screen. */
    private void settingsItem(String label, String action, String cls) {
        Intent i = new Intent(action);
        if (cls != null) i.setClassName("com.android.tv.settings", "com.android.tv.settings" + cls);
        ResolveInfo ri = getPackageManager().resolveActivity(i, 0);
        if (ri == null || !ri.activityInfo.exported) return;  // unexported screens resolve but refuse to start
        menuItem(label, () -> { if (!open(i)) open(new Intent(Settings.ACTION_SETTINGS)); });
    }

    private void clearMenu() {
        menu.removeAllViews();
        menuItems.clear();
        menuActions.clear();
    }

    /** Drops the menu under the focused pill, kept on screen. */
    private void showMenu() {
        View p = pills.get(topIdx);
        Rect r = new Rect(0, 0, p.getWidth(), p.getHeight());
        page.offsetDescendantRectToMyCoords(p, r);
        menu.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) menu.getLayoutParams();
        lp.leftMargin = Math.min(r.left, screenW - dp(64) - menu.getMeasuredWidth());
        lp.topMargin = Math.max(dp(24), Math.min(r.bottom + dp(12), screenH - dp(24) - menu.getMeasuredHeight()));
        menu.setLayoutParams(lp);
        menu.setVisibility(View.VISIBLE);
        zone = MENU;
        menuIdx = 0;
        afterLayout(() -> applyFocus(true));
    }

    private void menuItem(CharSequence label, Runnable action) {
        TextView t = new TextView(this);
        t.setTextSize(18);
        font(t, 400);
        t.setTextColor(0xF2FFFFFF);
        t.setText(label);
        t.setPadding(dp(18), dp(12), dp(18), dp(12));
        menu.addView(t, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        menuItems.add(t);
        menuActions.add(action);
    }

    private void closeMenu() {
        menu.setVisibility(View.GONE);
        zone = TOP;
    }

    private void openInput(TvInputInfo in) {
        Uri u = in.isPassthroughInput() ? TvContract.buildChannelUriForPassthroughInput(in.getId()) : TvContract.buildChannelsUriForInput(in.getId());
        if (!open(new Intent(Intent.ACTION_VIEW, u))) Toast.makeText(this, "Can't switch input", Toast.LENGTH_SHORT).show();
    }

    @SuppressWarnings("deprecation") // getConnectionInfo is still the only way to read the SSID on Android 8-12
    private void updateNetwork() {
        if (netPill == null) return;  // the block theme has no network pill
        ConnectivityManager cm = getSystemService(ConnectivityManager.class);
        NetworkCapabilities nc = cm.getNetworkCapabilities(cm.getActiveNetwork());
        String text;
        if (nc == null) text = "Offline";
        else if (nc.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) text = "Ethernet";
        else if (nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            WifiInfo wi = getApplicationContext().getSystemService(WifiManager.class).getConnectionInfo();
            String ssid = wi == null ? null : wi.getSSID();
            text = ssid == null || ssid.contains("unknown ssid") ? "Wi-Fi" : ssid.replace("\"", "");
        } else text = "Online";
        if (text.contentEquals(netPill.getText())) return;
        netPill.setText(text);
        if (zone == TOP || zone == MENU) afterLayout(() -> applyFocus(false));
    }

    private void updateGreeting() {
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        greeting.setText(h < 5 ? "Good night" : h < 12 ? "Good morning" : h < 18 ? "Good afternoon" : h < 22 ? "Good evening" : "Good night");
    }

    // ---- look -------------------------------------------------------------------------------

    /** Pushed with: adb push wallpaper.jpg /sdcard/Android/data/app.cleanyourtv.launcher/files/ */
    private void loadWallpaper() {
        File dir = getExternalFilesDir(null);
        File f = null;
        if (dir != null) for (String n : new String[] {"wallpaper.jpg", "wallpaper.png"}) if (new File(dir, n).isFile()) { f = new File(dir, n); break; }
        long stamp = f == null ? 0 : f.lastModified();
        if (stamp == wallpaperStamp) return;
        wallpaperStamp = stamp;
        Bitmap b = null;
        if (f != null) {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getPath(), o);
            o.inSampleSize = Math.max(1, o.outWidth / screenW);
            o.inJustDecodeBounds = false;
            b = BitmapFactory.decodeFile(f.getPath(), o);
        }
        wallpaper.setImageBitmap(b);
        wallpaper.setColorFilter(0x80000000, PorterDuff.Mode.SRC_ATOP);
        wallpaper.setVisibility(b == null ? View.GONE : View.VISIBLE);
    }

    /** Dominant vivid colour of an app's banner, weighted towards saturated pixels. */
    private int glowOf(App a) {
        if (a.glow != null) return a.glow;
        Bitmap b = Bitmap.createBitmap(32, 18, Bitmap.Config.ARGB_8888);
        Rect old = a.art.copyBounds();
        a.art.setBounds(0, 0, 32, 18);
        a.art.draw(new Canvas(b));
        a.art.setBounds(old);
        float[] hsv = new float[3];
        double r = 0, g = 0, bl = 0, w = 0;
        for (int y = 0; y < 18; y++) for (int x = 0; x < 32; x++) {
            int p = b.getPixel(x, y);
            if (Color.alpha(p) < 128) continue;
            Color.colorToHSV(p, hsv);
            double wt = hsv[1] * hsv[2] + 0.03;
            r += Color.red(p) * wt;
            g += Color.green(p) * wt;
            bl += Color.blue(p) * wt;
            w += wt;
        }
        b.recycle();
        int c = w == 0 ? Color.GRAY : Color.rgb((int) (r / w), (int) (g / w), (int) (bl / w));
        Color.colorToHSV(c, hsv);
        if (hsv[1] > 0.12f) { hsv[1] = Math.max(hsv[1], 0.65f); hsv[2] = 1f; } else hsv[2] = 0.6f;
        a.glow = Color.HSVToColor(hsv);
        return a.glow;
    }

    private void setGlow(int c, boolean animate) {
        if (glowAnim != null) glowAnim.cancel();
        glowAnim = ValueAnimator.ofArgb(glowColor, c);
        glowAnim.setDuration(animate ? 400 : 0);
        glowAnim.addUpdateListener(v -> {
            glowColor = (int) v.getAnimatedValue();
            glowDrawable.setColors(new int[] {(glowColor & 0x00FFFFFF) | 0x66000000, glowColor & 0x00FFFFFF});
        });
        glowAnim.start();
    }

    private GradientDrawable glass(int fill, float radius, int stroke) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(radius);
        if (stroke != 0) d.setStroke(Math.max(1, dp(1)), stroke);
        return d;
    }

    private void font(TextView t, int weight) {
        if (blocky) {  // pixel text with the hard drop shadow block games use; set the size first
            t.setTypeface(pixelFont);
            float off = Math.max(2, t.getTextSize() / 9);
            t.setShadowLayer(0.01f, off, off, 0xFF2A2A2A);
            return;
        }
        t.setTypeface(outfit);
        t.setFontVariationSettings("'wght' " + weight);
    }

    /** The block theme: a cabin behind everything, a 9-slot hotbar, an inventory panel and pixel text. */
    private void styleBlocky() {
        room = new BlockRoom(this);
        ((ViewGroup) scroll.getParent()).addView(room, 0);
        for (String item : getResources().getStringArray(R.array.block_letters)) {
            String[] f = item.split("\\|");
            blockLetters.put(f[0], f);
        }
        glow.setVisibility(View.GONE);
        highlight.setPixelUnit(u);

        LinearLayout.LayoutParams hotbar = new LinearLayout.LayoutParams(182 * u, 22 * u);
        hotbar.gravity = Gravity.CENTER_HORIZONTAL;
        dockRow.setLayoutParams(hotbar);
        dockRow.setPadding(u, u, u, u);
        dockRow.setBackground(BlockArt.box(u, 0xFF6B6B6B, 0xB0262626, 0, 0));

        LinearLayout.LayoutParams inventory = new LinearLayout.LayoutParams(176 * u, ViewGroup.LayoutParams.WRAP_CONTENT);
        inventory.gravity = Gravity.CENTER_HORIZONTAL;
        inventory.topMargin = 12 * u;
        inventory.bottomMargin = 12 * u;
        gridView.setLayoutParams(inventory);
        gridView.setPadding(7 * u, 7 * u, 7 * u, 7 * u);
        gridView.setBackground(BlockArt.box(u, 0xFF000000, 0xFFC6C6C6, 0xFFFFFFFF, 0xFF555555));
        menu.setBackground(BlockArt.box(u, 0xFF2D1B5E, 0xF0100A1C, 0, 0));

        FrameLayout.LayoutParams name = (FrameLayout.LayoutParams) dockName.getLayoutParams();
        name.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        name.setMarginStart(0);
        name.bottomMargin = 4 * u;
        dockName.setTextSize(TypedValue.COMPLEX_UNIT_PX, 10 * u);
        dockName.setTextColor(Color.WHITE);

        tooltip = new TextView(this);
        tooltip.setTextSize(TypedValue.COMPLEX_UNIT_PX, 8 * u);
        font(tooltip, 400);
        tooltip.setTextColor(Color.WHITE);
        tooltip.setPadding(3 * u, 2 * u, 3 * u, 2 * u);
        tooltip.setBackground(BlockArt.box(u, 0xFF2D1B5E, 0xF0100A1C, 0, 0));
        tooltip.setVisibility(View.GONE);
        page.addView(tooltip, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    /** Block theme: the only top-bar target is the item frame on the wall; its gear opens the Settings menu. */
    private void settingsGear() {
        TextView gear = pill("", this::openSettingsMenu);
        gear.setBackground(null);
        gear.setPadding(0, 0, 0, 0);
        gear.setLayoutParams(new LinearLayout.LayoutParams(BlockRoom.FRAME_W * u, BlockRoom.FRAME_W * u));
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) pillRow.getLayoutParams();
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.setMarginStart(BlockRoom.FRAME_X * u);
        lp.setMarginEnd(0);
        lp.topMargin = BlockRoom.FRAME_Y * u;
        pillRow.setLayoutParams(lp);
    }

    /** Block theme: the clock and a short date sit centred on the painting on the left wall; the greeting goes. */
    private void placeClockOnPainting(TextClock clock, TextClock date) {
        greeting.setVisibility(View.GONE);
        LinearLayout box = (LinearLayout) clock.getParent();
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) box.getLayoutParams();
        clock.setTextSize(TypedValue.COMPLEX_UNIT_PX, 13 * u);
        date.setTextSize(TypedValue.COMPLEX_UNIT_PX, 5 * u);
        font(clock, 120);
        font(date, 300);
        String pattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEdMMM");
        date.setFormat12Hour(pattern);
        date.setFormat24Hour(pattern);
        lp.width = BlockRoom.PAINT_W * u;
        lp.setMarginStart(BlockRoom.PAINT_X * u);
        box.measure(View.MeasureSpec.makeMeasureSpec(lp.width, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED);
        lp.topMargin = BlockRoom.PAINT_Y * u + (BlockRoom.PAINT_H * u - box.getMeasuredHeight()) / 2;
        box.setLayoutParams(lp);
    }

    /** The app's 16 x 16 sprite: its own icon pixelated, or big letters where block_letters names it. */
    private BitmapDrawable sprite(App a) {
        if (a.sprite == null) {
            String[] f = blockLetters.get(a.pkg);
            a.sprite = f == null ? BlockArt.sprite(getResources(), a.icon)
                    : BlockArt.letters(getResources(), pixelFont, f[1], Color.parseColor(f[2]), Color.parseColor(f[3]));
        }
        return a.sprite;
    }

    /** Hotbar: selector frame around the slot. Inventory: lightened slot plus a name tooltip. Elsewhere: an outline. */
    private void moveBlockHighlight(View target) {
        int style = zone == DOCK ? Highlight.SELECTOR : zone == GRID ? Highlight.HOVER : Highlight.OUTLINE;
        RectF r = rectInPage(target, 1f, zone == DOCK ? 2 * u : zone == GRID ? -u : 0);
        highlight.setStyle(style);
        highlight.moveTo(r, 0, false);
        room.setSpinning(zone == TOP || zone == MENU);
        if (zone != GRID) {
            tooltip.setVisibility(View.GONE);
            return;
        }
        tooltip.setText(grid.get(gridIdx).label);
        tooltip.setVisibility(View.VISIBLE);
        tooltip.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        float x = r.right + 3 * u;
        if (x + tooltip.getMeasuredWidth() > screenW) x = r.left - 3 * u - tooltip.getMeasuredWidth();
        tooltip.setX(x);
        tooltip.setY(r.top - 4 * u);
    }

    private void afterLayout(Runnable r) {
        page.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override public void onGlobalLayout() {
                page.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                r.run();
            }
        });
        page.requestLayout();
    }

    private int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
