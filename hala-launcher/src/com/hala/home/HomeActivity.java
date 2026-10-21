package com.hala.home;

import android.app.Activity;
import android.app.ActivityOptions;
import android.app.AppOpsManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.provider.Settings;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * The home screen: a clock, pages of app icons, page dots and the dock over the wallpaper.
 * Swipe up for all apps, down for notifications; long-press an icon for its menu or to move
 * it, long-press empty space for settings.
 */
public final class HomeActivity extends Activity
        implements AppRepository.Listener, CellLayout.Host, DrawerView.Host, SettingsSheet.Host, HomePager.Listener {

    /** One UI's standard easing: a quick start that settles gently. */
    private static final PathInterpolator EASE = new PathInterpolator(0.22f, 0.25f, 0f, 1f);
    private static final int DRAWER_MS = 320;
    private static final int MAX_RECENTS = 3;

    private final Handler main = new Handler(Looper.getMainLooper());
    private Prefs prefs;
    private AppRepository repo;
    private String themeApplied;
    private int iconDpApplied;

    private HomeRoot root;
    private LinearLayout content;
    private HomePager pager;
    private LinearLayout dots;
    private FrameLayout dockArea;
    private LinearLayout dock;
    private DrawerView drawer;
    private FrameLayout overlay;
    private final Rect insets = new Rect();

    private boolean drawerOpen;
    private DragPayload drag;
    private int edgeSide;
    private final Runnable edgeFlip = new Runnable() {
        @Override
        public void run() {
            if (drag != null && edgeSide != 0) {
                pager.snapTo(pager.current() + edgeSide, true);
                main.postDelayed(this, 700);
            }
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = new Prefs(this);
        themeApplied = prefs.theme();
        iconDpApplied = prefs.iconDp();
        Ui.init(this, themeApplied);
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        repo = new AppRepository(this, this);
        buildUi();
        repo.load(iconPx());
    }

    @Override
    protected void onDestroy() {
        main.removeCallbacksAndMessages(null);
        repo.close();
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Recent apps change every time another app is used.
        buildDock();
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Coming back home shows the home screen, not the drawer left open behind an app.
        if (drawerOpen) {
            setDrawer(false, false);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // The Home button while already home: close what is open, else go to the first page.
        if (overlay.getVisibility() == View.VISIBLE) {
            dismissOverlay();
        } else if (drawerOpen) {
            setDrawer(false, true);
        } else if (pager != null) {
            pager.snapTo(0, true);
        }
    }

    @Override
    public void onBackPressed() {
        if (overlay.getVisibility() == View.VISIBLE) {
            dismissOverlay();
        } else if (drawerOpen) {
            setDrawer(false, true);
        }
        // A home screen has nothing behind it: Back does nothing else.
    }

    @Override
    public void onConfigurationChanged(Configuration c) {
        super.onConfigurationChanged(c);
        Ui.init(this, prefs.theme());
        boolean settingsOpen = overlay.getVisibility() == View.VISIBLE && overlay.getTag() == SETTINGS_TAG;
        buildUi();
        if (settingsOpen) {
            showSettings();
        }
    }

    // ---- Building the screen ----

    private int iconPx() {
        return Ui.dp(prefs.iconDp());
    }

    @Override
    public boolean landscape() {
        return getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
    }

    private void buildUi() {
        root = new HomeRoot(this);
        root.setOnDragListener(new View.OnDragListener() {
            @Override
            public boolean onDrag(View v, DragEvent e) {
                if (e.getAction() == DragEvent.ACTION_DRAG_ENDED) {
                    dragEnded();
                }
                return e.getAction() == DragEvent.ACTION_DRAG_STARTED;
            }
        });

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        root.addView(content, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        // Clock and date, the way One UI's home and lock screens lead with the time.
        TextClock clock = new TextClock(this);
        clock.setFormat12Hour("h:mm");
        clock.setFormat24Hour("HH:mm");
        clock.setTextSize(landscape() ? 52 : 64);
        clock.setTypeface(android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL));
        Ui.onWallpaper(clock);
        clock.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cp.leftMargin = cp.rightMargin = Ui.dp(Ui.MARGIN + 8);
        content.addView(clock, cp);
        TextClock date = new TextClock(this);
        date.setFormat12Hour("EEEE، d MMMM");
        date.setFormat24Hour("EEEE، d MMMM");
        if (!isArabic()) {
            date.setFormat12Hour("EEEE, d MMMM");
            date.setFormat24Hour("EEEE, d MMMM");
        }
        date.setTextSize(16);
        Ui.onWallpaper(date);
        date.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        dp.leftMargin = dp.rightMargin = Ui.dp(Ui.MARGIN + 10);
        dp.bottomMargin = Ui.dp(12);
        content.addView(date, dp);

        pager = new HomePager(this);
        pager.setListener(this);
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        pp.leftMargin = pp.rightMargin = Ui.dp(Ui.MARGIN - 8);
        content.addView(pager, pp);

        dots = new LinearLayout(this);
        dots.setGravity(Gravity.CENTER);
        content.addView(dots, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(24)));

        // The dock: a rounded glass bar holding pinned apps, a divider and recent apps.
        dockArea = new FrameLayout(this);
        dockArea.setPadding(Ui.dp(Ui.MARGIN), Ui.dp(6), Ui.dp(Ui.MARGIN), Ui.dp(14));
        dock = new LinearLayout(this);
        dock.setGravity(Gravity.CENTER_VERTICAL);
        dock.setBackground(Ui.rounded(Ui.glass(), Ui.RADIUS_BLOCK));
        dock.setPadding(Ui.dp(14), Ui.dp(10), Ui.dp(14), Ui.dp(10));
        dock.setMinimumHeight(iconPx() + Ui.dp(20));
        dock.setMinimumWidth(Ui.dp(200));
        dockArea.addView(dock, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        dockArea.setOnDragListener(new View.OnDragListener() {
            @Override
            public boolean onDrag(View v, DragEvent e) {
                return dockDrag(e);
            }
        });
        content.addView(dockArea, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        drawer = new DrawerView(this, this);
        drawer.setVisibility(View.GONE);
        root.addView(drawer, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        overlay = new FrameLayout(this);
        // Hidden but still laid out: the menu and the sheet size themselves from it.
        overlay.setVisibility(View.INVISIBLE);
        root.addView(overlay, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public WindowInsets onApplyWindowInsets(View v, WindowInsets in) {
                insets.set(in.getSystemWindowInsetLeft(), in.getSystemWindowInsetTop(), in.getSystemWindowInsetRight(), in.getSystemWindowInsetBottom());
                content.setPadding(insets.left, insets.top + Ui.dp(20), insets.right, insets.bottom);
                drawer.setPadding(insets.left, insets.top, insets.right, insets.bottom);
                return in;
            }
        });

        drawerOpen = false;
        drag = null;
        setContentView(root);
        root.requestApplyInsets();
        updateBars();
        rebuildHome();
        buildDock();
        drawer.setApps(repo.all(), iconPx(), prefs.labels());
    }

    private boolean isArabic() {
        return "ar".equals(getResources().getConfiguration().getLocales().get(0).getLanguage());
    }

    @Override
    public void onAppsChanged() {
        if (prefs.firstRun() && !repo.all().isEmpty()) {
            seedDefaults();
        }
        rebuildHome();
        buildDock();
        drawer.setApps(repo.all(), iconPx(), prefs.labels());
    }

    /** Lay out the home pages from the saved slots. Apps that were uninstalled leave a gap. */
    private void rebuildHome() {
        if (pager == null) {
            return;
        }
        boolean land = landscape();
        int cols = prefs.columns(land);
        int rows = prefs.rows(land);
        int perPage = cols * rows;
        List<String> slots = prefs.home();
        int pages = Math.max(1, (slots.size() + perPage - 1) / perPage);
        // Drop pages that are entirely empty past the first.
        while (pages > 1 && pageEmpty(slots, pages - 1, perPage)) {
            pages--;
        }
        int keep = pager.current();
        pager.removeAllViews();
        int px = iconPx();
        boolean labels = prefs.labels();
        for (int p = 0; p < pages; p++) {
            CellLayout page = new CellLayout(this, p, cols, rows, this);
            for (int cell = 0; cell < perPage; cell++) {
                int slot = p * perPage + cell;
                if (slot >= slots.size()) {
                    break;
                }
                AppInfo app = repo.get(slots.get(slot));
                if (app != null) {
                    page.addAt(iconFor(app, DragPayload.HOME, slot, px, labels), cell);
                }
            }
            pager.addView(page);
        }
        pager.snapTo(Math.min(keep, pages - 1), false);
    }

    private static boolean pageEmpty(List<String> slots, int page, int perPage) {
        for (int i = page * perPage; i < Math.min(slots.size(), (page + 1) * perPage); i++) {
            if (slots.get(i) != null) {
                return false;
            }
        }
        return true;
    }

    private AppIconView iconFor(final AppInfo app, final int from, final int index, int px, boolean labels) {
        final AppIconView v = new AppIconView(this, app, px, labels, true);
        v.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                launch(app, v.icon);
            }
        });
        v.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                int[] at = new int[2];
                v.getLocationOnScreen(at);
                longPress(v, from, index, at[0] + v.getWidth() / 2f, at[1] + v.getHeight() / 2f);
                return true;
            }
        });
        return v;
    }

    @Override
    public void onPageChanged(int page, int count) {
        dots.removeAllViews();
        if (count < 2) {
            return;
        }
        for (int i = 0; i < count; i++) {
            View d = new View(this);
            d.setBackground(Ui.rounded(0xFFFFFFFF, 4));
            d.setAlpha(i == page ? 1f : 0.45f);
            int size = Ui.dp(i == page ? 8 : 6);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.leftMargin = lp.rightMargin = Ui.dp(4);
            dots.addView(d, lp);
        }
    }

    /** Pinned apps, then (when there are any) a divider and the most recently used apps. */
    private void buildDock() {
        if (dock == null) {
            return;
        }
        dock.removeAllViews();
        int px = iconPx();
        List<String> keys = prefs.dock();
        List<String> pinned = new ArrayList<String>();
        for (int i = 0; i < keys.size(); i++) {
            AppInfo app = repo.get(keys.get(i));
            if (app == null) {
                continue;
            }
            pinned.add(app.key);
            AppIconView v = iconFor(app, DragPayload.DOCK, i, px, false);
            v.setTag(DOCK_TAG);
            dock.addView(v, dockParams(px));
        }
        List<AppInfo> recent = recentApps(pinned);
        if (!recent.isEmpty()) {
            View line = new View(this);
            line.setBackgroundColor(Ui.dark ? 0x40FFFFFF : 0x30000000);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(1), px - Ui.dp(12));
            lp.leftMargin = lp.rightMargin = Ui.dp(10);
            dock.addView(line, lp);
            for (AppInfo app : recent) {
                // Recent apps can be dragged from the dock like drawer apps, but aren't pinned.
                dock.addView(iconFor(app, DragPayload.DRAWER, -1, px, false), dockParams(px));
            }
        }
    }

    private static final Object DOCK_TAG = new Object();
    private static final Object SETTINGS_TAG = new Object();

    private LinearLayout.LayoutParams dockParams(int px) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(px + Ui.dp(16), LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = lp.rightMargin = Ui.dp(2);
        return lp;
    }

    // ---- Recent apps (needs Usage access) ----

    private boolean usageAccess() {
        AppOpsManager ops = (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
        if (ops == null) {
            return false;
        }
        int mode = ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), getPackageName());
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    private List<AppInfo> recentApps(List<String> skip) {
        List<AppInfo> out = new ArrayList<AppInfo>();
        if (!prefs.recents() || !usageAccess()) {
            return out;
        }
        UsageStatsManager usm = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
        if (usm == null) {
            return out;
        }
        long now = System.currentTimeMillis();
        List<UsageStats> stats;
        try {
            stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 3L * 24 * 3600 * 1000, now);
        } catch (RuntimeException e) {
            return out;
        }
        if (stats == null) {
            return out;
        }
        List<UsageStats> sorted = new ArrayList<UsageStats>(stats);
        Collections.sort(sorted, new Comparator<UsageStats>() {
            @Override
            public int compare(UsageStats a, UsageStats b) {
                return Long.compare(b.getLastTimeUsed(), a.getLastTimeUsed());
            }
        });
        String home = defaultHomePackage();
        List<String> seen = new ArrayList<String>();
        for (UsageStats s : sorted) {
            String pkg = s.getPackageName();
            if (s.getLastTimeUsed() <= 0 || seen.contains(pkg) || pkg.equals(getPackageName()) || pkg.equals(home)) {
                continue;
            }
            seen.add(pkg);
            AppInfo app = repo.forPackage(pkg);
            if (app == null || skip.contains(app.key)) {
                continue;
            }
            out.add(app);
            if (out.size() == MAX_RECENTS) {
                break;
            }
        }
        return out;
    }

    private String defaultHomePackage() {
        Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        ResolveInfo ri = getPackageManager().resolveActivity(i, 0);
        return ri == null || ri.activityInfo == null ? "" : ri.activityInfo.packageName;
    }

    // ---- First run: a familiar starting layout from the device's own default apps ----

    private void seedDefaults() {
        List<String> dockKeys = new ArrayList<String>();
        addDefault(dockKeys, Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_BROWSER));
        addDefault(dockKeys, Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MESSAGING));
        addDefault(dockKeys, Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_GALLERY));
        addDefault(dockKeys, new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA));
        addDefault(dockKeys, Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MARKET));
        addDefault(dockKeys, new Intent(Settings.ACTION_SETTINGS));
        List<String> homeKeys = new ArrayList<String>();
        String[] cats = {
            Intent.CATEGORY_APP_CALENDAR, Intent.CATEGORY_APP_EMAIL, Intent.CATEGORY_APP_CONTACTS,
            Intent.CATEGORY_APP_MUSIC, Intent.CATEGORY_APP_CALCULATOR, Intent.CATEGORY_APP_MAPS,
        };
        for (String cat : cats) {
            addDefault(homeKeys, Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, cat));
        }
        homeKeys.removeAll(dockKeys);
        // Saving (even an empty layout) marks the first run as done.
        prefs.setDock(dockKeys);
        prefs.setHome(homeKeys);
    }

    private void addDefault(List<String> into, Intent intent) {
        ResolveInfo ri;
        try {
            ri = getPackageManager().resolveActivity(intent, 0);
        } catch (RuntimeException e) {
            return;
        }
        if (ri == null || ri.activityInfo == null) {
            return;
        }
        // "android" is the chooser: no default is set for that kind of app.
        String pkg = ri.activityInfo.packageName;
        if ("android".equals(pkg)) {
            return;
        }
        AppInfo app = repo.forPackage(pkg);
        if (app != null && !into.contains(app.key)) {
            into.add(app.key);
        }
    }

    // ---- Opening apps ----

    @Override
    public void launch(AppInfo app, View from) {
        Rect bounds = new Rect();
        Bundle opts = null;
        if (from != null && from.isAttachedToWindow()) {
            int[] at = new int[2];
            from.getLocationOnScreen(at);
            bounds.set(at[0], at[1], at[0] + from.getWidth(), at[1] + from.getHeight());
            opts = ActivityOptions.makeClipRevealAnimation(from, 0, 0, from.getWidth(), from.getHeight()).toBundle();
        }
        try {
            repo.launcherApps().startMainActivity(app.component, app.user, bounds, opts);
        } catch (RuntimeException e) {
            Toast.makeText(this, R.string.cant_open, Toast.LENGTH_SHORT).show();
        }
    }

    private void appInfo(AppInfo app) {
        try {
            repo.launcherApps().startAppDetailsActivity(app.component, app.user, null, null);
        } catch (RuntimeException e) {
            Toast.makeText(this, R.string.cant_open, Toast.LENGTH_SHORT).show();
        }
    }

    private void uninstall(AppInfo app) {
        Intent i = new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + app.component.getPackageName()));
        i.putExtra(Intent.EXTRA_USER, app.user);
        start(i);
    }

    private void start(Intent i) {
        try {
            startActivity(i);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.cant_open, Toast.LENGTH_SHORT).show();
        } catch (SecurityException e) {
            Toast.makeText(this, R.string.cant_open, Toast.LENGTH_SHORT).show();
        }
    }

    // ---- Long press: the app menu, and dragging ----

    @Override
    public void longPress(AppIconView v, final int from, final int index, float x, float y) {
        final AppInfo app = v.app;
        List<AppMenu.Item> items = new ArrayList<AppMenu.Item>();
        if (from == DragPayload.HOME) {
            items.add(new AppMenu.Item(getString(R.string.remove), new Runnable() {
                @Override
                public void run() {
                    List<String> home = prefs.home();
                    if (index < home.size()) {
                        home.set(index, null);
                        prefs.setHome(home);
                    }
                    rebuildHome();
                }
            }));
        } else if (from == DragPayload.DOCK) {
            items.add(new AppMenu.Item(getString(R.string.remove_from_dock), new Runnable() {
                @Override
                public void run() {
                    List<String> d = prefs.dock();
                    if (index < d.size()) {
                        d.remove(index);
                        prefs.setDock(d);
                    }
                    buildDock();
                }
            }));
        } else {
            items.add(new AppMenu.Item(getString(R.string.add_to_home), new Runnable() {
                @Override
                public void run() {
                    addToHome(app);
                }
            }));
            items.add(new AppMenu.Item(getString(R.string.add_to_dock), new Runnable() {
                @Override
                public void run() {
                    DragPayload add = new DragPayload(app, DragPayload.DRAWER, -1, 0, 0);
                    add.moved = true;
                    dropOnDock(prefs.dock().size(), add);
                }
            }));
        }
        items.add(new AppMenu.Item(getString(R.string.app_info), new Runnable() {
            @Override
            public void run() {
                appInfo(app);
            }
        }));
        if (!app.system) {
            items.add(new AppMenu.Item(getString(R.string.uninstall), new Runnable() {
                @Override
                public void run() {
                    uninstall(app);
                }
            }));
        }
        int[] at = new int[2];
        v.icon.getLocationOnScreen(at);
        int[] anchor = {at[0], at[1], at[0] + v.icon.getWidth(), at[1] + v.icon.getHeight()};
        overlay.setTag(null);
        AppMenu.show(overlay, anchor, app.label, items, new Runnable() {
            @Override
            public void run() {
                dismissOverlay();
            }
        });

        // The same press can carry on into a drag: moving the finger closes the menu.
        drag = new DragPayload(app, from, index, x, y);
        addSparePage();
        try {
            v.startDragAndDrop(ClipData.newPlainText(app.label, app.key), new View.DragShadowBuilder(v.icon), drag, 0);
        } catch (RuntimeException e) {
            drag = null;
        }
    }

    /** While dragging, an empty page waits after the last one so an app can go to a new page. */
    private void addSparePage() {
        int count = pager.count();
        if (count == 0) {
            return;
        }
        CellLayout last = (CellLayout) pager.getChildAt(count - 1);
        if (last.getChildCount() == 0) {
            return;
        }
        boolean land = landscape();
        pager.addView(new CellLayout(this, count, prefs.columns(land), prefs.rows(land), this));
        onPageChanged(pager.current(), pager.count());
    }

    @Override
    public void dragMoved(float x, float y) {
        DragPayload p = drag;
        if (p == null || p.moved) {
            return;
        }
        if (Math.hypot(x - p.startX, y - p.startY) > Ui.dp(24)) {
            p.moved = true;
            dismissOverlay();
            if (drawerOpen) {
                setDrawer(false, true);
            }
        }
    }

    @Override
    public void dragAtEdge(int side) {
        if (side == edgeSide) {
            return;
        }
        edgeSide = side;
        main.removeCallbacks(edgeFlip);
        if (side != 0 && drag != null && drag.moved) {
            main.postDelayed(edgeFlip, 600);
        }
    }

    private void dragEnded() {
        if (drag == null) {
            return;
        }
        drag = null;
        edgeSide = 0;
        main.removeCallbacks(edgeFlip);
        dock.setAlpha(1f);
        rebuildHome();
        buildDock();
    }

    @Override
    public void dropOnHome(int slot, DragPayload p) {
        if (!p.moved) {
            return;
        }
        List<String> home = prefs.home();
        pad(home, Math.max(slot, p.index) + 1);
        String key = p.app.key;
        String there = home.get(slot);
        if (p.from == DragPayload.HOME) {
            if (slot == p.index) {
                return;
            }
            // Dropping on another app swaps the two.
            home.set(p.index, there);
            home.set(slot, key);
        } else if (p.from == DragPayload.DOCK) {
            List<String> d = prefs.dock();
            if (p.index < d.size()) {
                if (there != null) {
                    d.set(p.index, there);
                } else {
                    d.remove(p.index);
                }
                prefs.setDock(d);
            }
            home.set(slot, key);
        } else {
            // From the drawer: one icon per app on the home screen, so an existing one moves.
            int old = home.indexOf(key);
            if (old >= 0) {
                home.set(old, null);
                there = home.get(slot);
            }
            if (there == null) {
                home.set(slot, key);
            } else {
                home.set(freeSlotFrom(home, slot), key);
            }
        }
        prefs.setHome(home);
    }

    private void addToHome(AppInfo app) {
        List<String> home = prefs.home();
        if (home.contains(app.key)) {
            return;
        }
        int slot = freeSlotFrom(home, 0);
        pad(home, slot + 1);
        home.set(slot, app.key);
        prefs.setHome(home);
        rebuildHome();
        boolean land = landscape();
        pager.snapTo(slot / (prefs.columns(land) * prefs.rows(land)), true);
    }

    /** The first empty slot at or after `from` (a new page when every page is full). */
    private static int freeSlotFrom(List<String> home, int from) {
        for (int i = from; i < home.size(); i++) {
            if (home.get(i) == null) {
                return i;
            }
        }
        for (int i = 0; i < Math.min(from, home.size()); i++) {
            if (home.get(i) == null) {
                return i;
            }
        }
        return home.size();
    }

    private static void pad(List<String> list, int size) {
        while (list.size() < size) {
            list.add(null);
        }
    }

    private boolean dockDrag(DragEvent e) {
        if (!(e.getLocalState() instanceof DragPayload)) {
            return false;
        }
        DragPayload p = (DragPayload) e.getLocalState();
        switch (e.getAction()) {
            case DragEvent.ACTION_DRAG_STARTED:
                return true;
            case DragEvent.ACTION_DRAG_ENTERED:
                dock.setAlpha(0.75f);
                return true;
            case DragEvent.ACTION_DRAG_LOCATION: {
                int[] at = new int[2];
                dockArea.getLocationOnScreen(at);
                dragMoved(at[0] + e.getX(), at[1] + e.getY());
                return true;
            }
            case DragEvent.ACTION_DRAG_EXITED:
                dock.setAlpha(1f);
                return true;
            case DragEvent.ACTION_DROP: {
                dock.setAlpha(1f);
                int[] at = new int[2];
                dockArea.getLocationOnScreen(at);
                dropOnDock(dockPosition(at[0] + e.getX()), p);
                return true;
            }
            default:
                return true;
        }
    }

    /** Where in the pinned apps a drop at screen x goes, in reading order. */
    private int dockPosition(float x) {
        boolean rtl = root.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
        int pos = 0;
        for (int i = 0; i < dock.getChildCount(); i++) {
            View c = dock.getChildAt(i);
            if (c.getTag() != DOCK_TAG) {
                continue;
            }
            int[] at = new int[2];
            c.getLocationOnScreen(at);
            float centre = at[0] + c.getWidth() / 2f;
            if (rtl ? centre > x : centre < x) {
                pos++;
            }
        }
        return pos;
    }

    private void dropOnDock(int pos, DragPayload p) {
        // A long press released in place only opens the menu; it doesn't drop anything.
        if (!p.moved) {
            return;
        }
        List<String> d = prefs.dock();
        String key = p.app.key;
        if (p.from == DragPayload.DOCK) {
            if (p.index >= d.size()) {
                return;
            }
            d.remove(p.index);
            if (pos > p.index) {
                pos--;
            }
        } else {
            int old = d.indexOf(key);
            if (old >= 0) {
                d.remove(old);
                if (pos > old) {
                    pos--;
                }
            } else if (d.size() >= Prefs.MAX_DOCK) {
                Toast.makeText(this, R.string.dock_full, Toast.LENGTH_SHORT).show();
                return;
            }
            if (p.from == DragPayload.HOME) {
                List<String> home = prefs.home();
                if (p.index < home.size()) {
                    home.set(p.index, null);
                    prefs.setHome(home);
                }
            }
        }
        d.add(Math.max(0, Math.min(d.size(), pos)), key);
        prefs.setDock(d);
        if (drag == null) {
            buildDock();
            rebuildHome();
        }
    }

    // ---- The app drawer ----

    private void setDrawer(final boolean open, boolean animate) {
        drawerOpen = open;
        int h = root.getHeight();
        drawer.animate().cancel();
        if (open) {
            drawer.setApps(repo.all(), iconPx(), prefs.labels());
            drawer.setVisibility(View.VISIBLE);
        } else {
            drawer.hideKeyboard();
        }
        updateBars();
        if (!animate || h == 0) {
            drawer.setTranslationY(open ? 0 : h);
            followDrawer();
            if (!open) {
                drawer.setVisibility(View.GONE);
                drawer.reset();
            }
            return;
        }
        drawer.animate()
            .translationY(open ? 0 : h)
            .setDuration(DRAWER_MS)
            .setInterpolator(EASE)
            .setUpdateListener(new android.animation.ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(android.animation.ValueAnimator a) {
                    followDrawer();
                }
            })
            .withEndAction(new Runnable() {
                @Override
                public void run() {
                    if (!drawerOpen) {
                        drawer.setVisibility(View.GONE);
                        drawer.reset();
                    }
                }
            })
            .start();
    }

    /** The home screen fades and recedes as the drawer rises over it. */
    private void followDrawer() {
        int h = Math.max(1, root.getHeight());
        float progress = drawer.getVisibility() == View.VISIBLE ? 1f - Math.min(1f, drawer.getTranslationY() / h) : 0f;
        content.setAlpha(1f - progress);
        float scale = 1f - 0.06f * progress;
        content.setScaleX(scale);
        content.setScaleY(scale);
    }

    @Override
    public void drawerDragged(float dy, boolean released, float velocityY) {
        int h = Math.max(1, root.getHeight());
        if (!released) {
            drawer.animate().cancel();
            drawer.setTranslationY(Math.min(h, dy));
            followDrawer();
            return;
        }
        boolean close = velocityY > Ui.dp(600) || (velocityY > -Ui.dp(600) && dy > h / 4f);
        setDrawer(!close, true);
    }

    private void updateBars() {
        // Over the wallpaper the system bars stay light; on the light drawer they turn dark.
        int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
        boolean lightContent = (drawerOpen || overlay.getVisibility() == View.VISIBLE && overlay.getTag() == SETTINGS_TAG) && !Ui.dark;
        if (lightContent) {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        getWindow().getDecorView().setSystemUiVisibility(flags);
    }

    private void expandNotifications() {
        try {
            Object sb = getSystemService("statusbar");
            Class.forName("android.app.StatusBarManager").getMethod("expandNotificationsPanel").invoke(sb);
        } catch (Exception e) {
            // Not offered on this device: the gesture simply does nothing.
        }
    }

    // ---- Settings ----

    private void showSettings() {
        if (overlay.getWidth() == 0) {
            // Just rebuilt (a theme change or rotation): open once the screen has its size.
            overlay.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
                @Override
                public void onLayoutChange(View v, int l, int t, int r, int b, int ol, int ot, int or, int ob) {
                    v.removeOnLayoutChangeListener(this);
                    v.post(new Runnable() {
                        @Override
                        public void run() {
                            showSettings();
                        }
                    });
                }
            });
            return;
        }
        SettingsSheet.show(overlay, this);
        overlay.setTag(SETTINGS_TAG);
        updateBars();
    }

    @Override
    public Prefs prefs() {
        return prefs;
    }

    @Override
    public void settingsChanged() {
        String theme = prefs.theme();
        int icon = prefs.iconDp();
        if (!theme.equals(themeApplied)) {
            // A new theme recolours everything, the settings sheet included.
            themeApplied = theme;
            Ui.init(this, theme);
            buildUi();
            showSettings();
            return;
        }
        if (icon != iconDpApplied) {
            iconDpApplied = icon;
            dock.setMinimumHeight(iconPx() + Ui.dp(20));
            // Icons are drawn at their size: load them again (the layout follows when they arrive).
            repo.load(iconPx());
        }
        rebuildHome();
        buildDock();
        drawer.setApps(repo.all(), iconPx(), prefs.labels());
    }

    @Override
    public void requestRecents() {
        if (usageAccess()) {
            return;
        }
        Intent i = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.parse("package:" + getPackageName()));
        try {
            startActivity(i);
        } catch (ActivityNotFoundException e) {
            start(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
        }
    }

    @Override
    public void pickWallpaper() {
        start(Intent.createChooser(new Intent(Intent.ACTION_SET_WALLPAPER), getString(R.string.wallpaper)));
    }

    @Override
    public void chooseDefaultHome() {
        try {
            startActivity(new Intent(Settings.ACTION_HOME_SETTINGS));
        } catch (ActivityNotFoundException e) {
            start(new Intent(Settings.ACTION_SETTINGS));
        }
    }

    @Override
    public void dismissOverlay() {
        overlay.removeAllViews();
        overlay.setVisibility(View.INVISIBLE);
        overlay.setTag(null);
        updateBars();
    }

    // ---- Gestures on the home screen itself ----

    /**
     * The root view watches every touch on the home screen: a vertical swipe opens the drawer
     * (up) or the notification shade (down), and a long press on empty space opens settings.
     * Horizontal swipes belong to the pager, which claims them first.
     */
    private final class HomeRoot extends FrameLayout {
        private final int slop;
        private float downX;
        private float downY;
        private boolean opening;
        private boolean handled;
        private VelocityTracker velocity;
        private final Runnable longPress = new Runnable() {
            @Override
            public void run() {
                handled = true;
                performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                showSettings();
            }
        };

        HomeRoot(Context c) {
            super(c);
            slop = ViewConfiguration.get(c).getScaledTouchSlop();
        }

        private boolean idle() {
            return !drawerOpen && overlay.getVisibility() != View.VISIBLE && drag == null;
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent e) {
            if (!idle()) {
                removeCallbacks(longPress);
                return false;
            }
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = e.getX();
                    downY = e.getY();
                    opening = false;
                    handled = false;
                    if (!overIcon(e.getRawX(), e.getRawY())) {
                        postDelayed(longPress, ViewConfiguration.getLongPressTimeout());
                    }
                    break;
                case MotionEvent.ACTION_MOVE:
                    return swipe(e);
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    removeCallbacks(longPress);
                    break;
                default:
                    break;
            }
            return false;
        }

        /** Follow a moving finger; true once it is a vertical swipe this view acts on. */
        private boolean swipe(MotionEvent e) {
            float dx = e.getX() - downX;
            float dy = e.getY() - downY;
            if (Math.abs(dx) > slop || Math.abs(dy) > slop) {
                removeCallbacks(longPress);
            }
            if (handled || opening || Math.abs(dy) <= slop * 2 || Math.abs(dy) <= Math.abs(dx) * 1.5f) {
                return false;
            }
            if (dy < 0) {
                opening = true;
                drawer.setTranslationY(getHeight());
                drawer.setApps(repo.all(), iconPx(), prefs.labels());
                drawer.setVisibility(View.VISIBLE);
            } else {
                handled = true;
                expandNotifications();
            }
            return true;
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            if (velocity == null) {
                velocity = VelocityTracker.obtain();
            }
            velocity.addMovement(e);
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    // Reached only when no child took the touch; onInterceptTouchEvent has
                    // already recorded where it began and started the long-press timer.
                    return true;
                case MotionEvent.ACTION_MOVE:
                    // A touch that began on empty space (the clock, the dock's edges) comes
                    // straight here rather than through onInterceptTouchEvent.
                    if (!opening && idle()) {
                        swipe(e);
                    }
                    if (opening) {
                        drawer.setTranslationY(Math.max(0, Math.min(getHeight(), getHeight() + e.getY() - downY)));
                        followDrawer();
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL: {
                    removeCallbacks(longPress);
                    if (opening) {
                        velocity.computeCurrentVelocity(1000);
                        float vy = velocity.getYVelocity();
                        float pulled = downY - e.getY();
                        boolean open = vy < -Ui.dp(600) || (vy < Ui.dp(600) && pulled > getHeight() / 5f);
                        setDrawer(open, true);
                        opening = false;
                    }
                    velocity.recycle();
                    velocity = null;
                    return true;
                }
                default:
                    return true;
            }
        }

        /** Whether screen point (x, y) is on an app icon (its own long press wins). */
        private boolean overIcon(float x, float y) {
            View page = pager.getChildAt(pager.current());
            return page instanceof ViewGroup && hits((ViewGroup) page, x, y) || hits(dock, x, y);
        }

        private boolean hits(ViewGroup group, float x, float y) {
            int[] at = new int[2];
            for (int i = 0; i < group.getChildCount(); i++) {
                View c = group.getChildAt(i);
                if (!(c instanceof AppIconView)) {
                    continue;
                }
                c.getLocationOnScreen(at);
                if (x >= at[0] && x < at[0] + c.getWidth() && y >= at[1] && y < at[1] + c.getHeight()) {
                    return true;
                }
            }
            return false;
        }
    }
}
