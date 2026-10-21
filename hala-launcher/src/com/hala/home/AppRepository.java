package com.hala.home;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.os.UserManager;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The launchable apps of every profile on the device, from LauncherApps (the API meant for home
 * screens), with their squircle icons. Loads on a background thread and reloads when apps are
 * installed, updated or removed.
 */
final class AppRepository {
    interface Listener {
        void onAppsChanged();
    }

    private final Context context;
    private final LauncherApps launcherApps;
    private final UserManager userManager;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Listener listener;
    private volatile List<AppInfo> apps = new ArrayList<AppInfo>();
    private volatile Map<String, AppInfo> byKey = new HashMap<String, AppInfo>();
    private int iconPx;
    private boolean themed;
    private int generation;
    /** The day the icons were drawn (the calendar icon shows the date). */
    private int loadedDay = -1;

    private final LauncherApps.Callback callback = new LauncherApps.Callback() {
        @Override
        public void onPackageRemoved(String packageName, UserHandle user) {
            reload();
        }

        @Override
        public void onPackageAdded(String packageName, UserHandle user) {
            reload();
        }

        @Override
        public void onPackageChanged(String packageName, UserHandle user) {
            reload();
        }

        @Override
        public void onPackagesAvailable(String[] packageNames, UserHandle user, boolean replacing) {
            reload();
        }

        @Override
        public void onPackagesUnavailable(String[] packageNames, UserHandle user, boolean replacing) {
            reload();
        }
    };

    AppRepository(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.launcherApps = (LauncherApps) context.getSystemService(Context.LAUNCHER_APPS_SERVICE);
        this.userManager = (UserManager) context.getSystemService(Context.USER_SERVICE);
        this.listener = listener;
        launcherApps.registerCallback(callback, main);
    }

    void close() {
        launcherApps.unregisterCallback(callback);
    }

    List<AppInfo> all() {
        return apps;
    }

    AppInfo get(String key) {
        return key == null ? null : byKey.get(key);
    }

    /** The first launchable activity of `packageName` in the main profile. */
    AppInfo forPackage(String packageName) {
        for (AppInfo a : apps) {
            if (a.component.getPackageName().equals(packageName) && a.user.equals(android.os.Process.myUserHandle())) {
                return a;
            }
        }
        return null;
    }

    LauncherApps launcherApps() {
        return launcherApps;
    }

    /** Load again at `iconPx` pixels per icon (icon size changes re-render every icon). */
    void load(int iconPx, boolean themed) {
        this.iconPx = iconPx;
        this.themed = themed;
        reload();
    }

    /** Draw the icons again if the date changed since they were drawn. */
    void refreshIfNewDay() {
        if (loadedDay != java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_YEAR)) {
            reload();
        }
    }

    private void reload() {
        final int gen;
        synchronized (this) {
            gen = ++generation;
        }
        final int px = iconPx;
        final boolean withThemes = themed;
        loadedDay = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_YEAR);
        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<AppInfo> list = query(px, withThemes);
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        synchronized (AppRepository.this) {
                            if (gen != generation) {
                                return;
                            }
                        }
                        Map<String, AppInfo> map = new HashMap<String, AppInfo>();
                        for (AppInfo a : list) {
                            map.put(a.key, a);
                        }
                        apps = list;
                        byKey = map;
                        listener.onAppsChanged();
                    }
                });
            }
        }, "hala-apps").start();
    }

    private List<AppInfo> query(int px, boolean withThemes) {
        List<AppInfo> out = new ArrayList<AppInfo>();
        String self = context.getPackageName();
        ThemedIcons themes = withThemes ? new ThemedIcons(context) : null;
        for (UserHandle user : launcherApps.getProfiles()) {
            long serial = userManager.getSerialNumberForUser(user);
            List<LauncherActivityInfo> infos;
            try {
                infos = launcherApps.getActivityList(null, user);
            } catch (RuntimeException e) {
                continue;
            }
            Map<String, Integer> perPackage = new HashMap<String, Integer>();
            for (LauncherActivityInfo info : infos) {
                String pkg = info.getComponentName().getPackageName();
                Integer n = perPackage.get(pkg);
                perPackage.put(pkg, n == null ? 1 : n + 1);
            }
            for (LauncherActivityInfo info : infos) {
                ComponentName cn = info.getComponentName();
                if (cn.getPackageName().equals(self)) {
                    continue;
                }
                ApplicationInfo ai = info.getApplicationInfo();
                boolean system = (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                String label = String.valueOf(info.getLabel());
                AppInfo app = new AppInfo(cn, user, serial, label, system);
                try {
                    // Built-in apps of the main profile can take Hala's own icon; work-profile
                    // apps keep theirs, which carries the system's badge.
                    String name = null;
                    if (themes != null && user.equals(android.os.Process.myUserHandle())) {
                        name = themes.nameFor(info, perPackage.get(cn.getPackageName()) > 1);
                    }
                    app.icon = name == null ? null : themes.render(name, Math.max(px, 1));
                    if (app.icon == null) {
                        Drawable d = info.getBadgedIcon(0);
                        app.icon = IconShapes.render(d, Math.max(px, 1));
                    }
                } catch (RuntimeException e) {
                    app.icon = null;
                }
                out.add(app);
            }
        }
        final Collator collator = Collator.getInstance();
        Collections.sort(out, new Comparator<AppInfo>() {
            @Override
            public int compare(AppInfo a, AppInfo b) {
                return collator.compare(a.label, b.label);
            }
        });
        return out;
    }
}
