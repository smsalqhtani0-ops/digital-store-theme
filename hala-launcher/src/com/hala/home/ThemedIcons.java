package com.hala.home;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.provider.AlarmClock;
import android.provider.MediaStore;
import android.provider.Settings;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Hala's own icons (res/drawable/icon_*.xml, drawn by design/icons.py) for the device's built-in
 * apps: camera, gallery, settings, phone and the rest. An app is matched by the role the system
 * gives it (the default camera, the default gallery…), else by its name. Only apps that came
 * with the device are restyled; Google's apps and anything installed later keep their icons.
 */
final class ThemedIcons {
    private final Context context;
    /** Package → icon name, from the system's default app for each role. */
    private final Map<String, String> roles = new HashMap<String, String>();

    ThemedIcons(Context context) {
        this.context = context;
        role(new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA), "camera");
        role(new Intent(Settings.ACTION_SETTINGS), "settings");
        role(new Intent(Intent.ACTION_DIAL), "phone");
        role(new Intent(AlarmClock.ACTION_SHOW_ALARMS), "clock");
        category(Intent.CATEGORY_APP_GALLERY, "gallery");
        category(Intent.CATEGORY_APP_MESSAGING, "messages");
        category(Intent.CATEGORY_APP_CONTACTS, "contacts");
        category(Intent.CATEGORY_APP_CALENDAR, "calendar");
        category(Intent.CATEGORY_APP_CALCULATOR, "calculator");
        category(Intent.CATEGORY_APP_FILES, "files");
        category(Intent.CATEGORY_APP_EMAIL, "email");
        category(Intent.CATEGORY_APP_BROWSER, "browser");
        category(Intent.CATEGORY_APP_MUSIC, "music");
        category(Intent.CATEGORY_APP_WEATHER, "weather");
        category(Intent.CATEGORY_APP_MAPS, "maps");
        category(Intent.CATEGORY_APP_FITNESS, "health");
    }

    private void category(String cat, String name) {
        role(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, cat), name);
    }

    private void role(Intent intent, String name) {
        try {
            ResolveInfo ri = context.getPackageManager().resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY);
            if (ri != null && ri.activityInfo != null && !"android".equals(ri.activityInfo.packageName)
                    && !roles.containsKey(ri.activityInfo.packageName)) {
                roles.put(ri.activityInfo.packageName, name);
            }
        } catch (RuntimeException e) {
            // No default for this role.
        }
    }

    /**
     * The icon name for `info`, or null to keep the app's own icon. `shared` is true when its
     * package has more than one launcher entry (dialer and contacts often share one).
     */
    String nameFor(LauncherActivityInfo info, boolean shared) {
        ApplicationInfo ai = info.getApplicationInfo();
        String pkg = ai.packageName;
        boolean builtIn = (ai.flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0;
        if (!builtIn || pkg.startsWith("com.google.") || pkg.equals("com.android.chrome") || pkg.equals("com.android.vending")) {
            return null;
        }
        if (shared) {
            String byActivity = byWords(info.getComponentName().getClassName().toLowerCase(Locale.ROOT), true);
            if (byActivity != null) {
                return byActivity;
            }
        }
        String byRole = roles.get(pkg);
        return byRole != null ? byRole : byWords(pkg.toLowerCase(Locale.ROOT), false);
    }

    private static String byWords(String s, boolean activity) {
        if (s.contains("dial") || s.contains("calllog") || activity && s.contains("phone")) {
            return "phone";
        }
        if (s.contains("contact") || s.contains("people")) {
            return "contacts";
        }
        if (s.contains("mms") || s.contains("messag") || s.contains("conversation")) {
            return "messages";
        }
        if (activity) {
            return null;
        }
        String[][] words = {
            {"notepad", "notes"}, {"note", "notes"}, {"recorder", "recorder"}, {"thememanager", "themes"},
            {"theme", "themes"}, {"health", "health"}, {"systemmanager", "care"}, {"phonemanager", "care"},
            {"optimiz", "care"}, {"video", "video"}, {"filemanager", "files"}, {"deskclock", "clock"},
            {"clock", "clock"}, {"calculator", "calculator"}, {"calendar", "calendar"}, {"camera", "camera"},
            {"gallery", "gallery"}, {"photos", "gallery"}, {"weather", "weather"}, {"email", "email"},
            {"browser", "browser"}, {"music", "music"}, {"download", "downloads"},
        };
        for (String[] w : words) {
            if (s.contains(w[0])) {
                return w[1];
            }
        }
        return null;
    }

    /** Draw icon `name` at `size` pixels, in the squircle. */
    Bitmap render(String name, int size) {
        if ("calendar".equals(name)) {
            return calendar(size);
        }
        int id = context.getResources().getIdentifier("icon_" + name, "drawable", context.getPackageName());
        if (id == 0) {
            return null;
        }
        Drawable d = context.getDrawable(id);
        return d == null ? null : IconShapes.renderFull(d, size);
    }

    /** The calendar shows today: the weekday in red over the day's number. */
    private Bitmap calendar(int size) {
        Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Path shape = IconShapes.squircle(size);
        Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        bg.setShader(new LinearGradient(0, 0, size, size, 0xFFFFFFFF, 0xFFEDEDED, Shader.TileMode.CLAMP));
        c.drawPath(shape, bg);
        Date now = new Date();
        Locale locale = context.getResources().getConfiguration().getLocales().get(0);
        Paint day = new Paint(Paint.ANTI_ALIAS_FLAG);
        day.setColor(0xFFF2435A);
        day.setTextAlign(Paint.Align.CENTER);
        day.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        day.setTextSize(size * 0.17f);
        String weekday = new SimpleDateFormat("EEE", locale).format(now);
        c.drawText(weekday, size / 2f, size * 0.32f, day);
        Paint num = new Paint(Paint.ANTI_ALIAS_FLAG);
        num.setColor(0xFF1F1F1F);
        num.setTextAlign(Paint.Align.CENTER);
        num.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        num.setTextSize(size * 0.46f);
        String date = String.format(locale, "%d", Calendar.getInstance().get(Calendar.DAY_OF_MONTH));
        c.drawText(date, size / 2f, size * 0.78f, num);
        return b;
    }
}
