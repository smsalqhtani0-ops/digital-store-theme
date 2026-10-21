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
    private int generation;

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
    void load(int iconPx) {
        this.iconPx = iconPx;
        reload();
    }

    private void reload() {
        final int gen;
        synchronized (this) {
            gen = ++generation;
        }
        final int px = iconPx;
        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<AppInfo> list = query(px);
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

    private List<AppInfo> query(int px) {
        List<AppInfo> out = new ArrayList<AppInfo>();
        String self = context.getPackageName();
        for (UserHandle user : launcherApps.getProfiles()) {
            long serial = userManager.getSerialNumberForUser(user);
            List<LauncherActivityInfo> infos;
            try {
                infos = launcherApps.getActivityList(null, user);
            } catch (RuntimeException e) {
                continue;
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
                    // Work-profile apps carry the system's badge.
                    Drawable d = info.getBadgedIcon(0);
                    app.icon = IconShapes.render(d, Math.max(px, 1));
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
