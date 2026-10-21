package com.hala.home;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.View;
import android.widget.TextView;

/**
 * Design tokens and small view helpers. The values follow the published One UI design
 * guidelines: primary blue, rounded "focus blocks" (26 dp radius for large cards, 20 dp for
 * grid tiles, 12 dp for small thumbnails), 24 dp side margins and the type scale (40 sp light
 * for extended titles down to 13 sp for the smallest text).
 */
final class Ui {
    private Ui() {}

    static final int PRIMARY = 0xFF0381FE;
    static final int PRIMARY_DARK = 0xFF0072DE;
    static final int CONTROL_ACTIVE = 0xFF3E91FF;

    static final float RADIUS_BLOCK = 26f;
    static final float RADIUS_TILE = 20f;
    static final float RADIUS_SMALL = 12f;
    static final float MARGIN = 24f;

    static float density = 1f;
    /** The theme in use: set from the preference and the system's night mode. */
    static boolean dark;

    static void init(Context c, String themePref) {
        density = c.getResources().getDisplayMetrics().density;
        if ("light".equals(themePref)) {
            dark = false;
        } else if ("dark".equals(themePref)) {
            dark = true;
        } else {
            int night = c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
            dark = night == Configuration.UI_MODE_NIGHT_YES;
        }
    }

    static int dp(float v) {
        return Math.round(v * density);
    }

    /** Background behind content screens (app drawer, settings). */
    static int screen() {
        return dark ? 0xF2000000 : 0xF2F6F6F6;
    }

    /** A focus block: the card that holds a group of content. */
    static int block() {
        return dark ? 0xFF252525 : 0xFFFCFCFC;
    }

    /** The dock and other bars that float over the wallpaper. */
    static int glass() {
        return dark ? 0xB3262626 : 0xB3F7F7F7;
    }

    static int text() {
        return dark ? 0xFFFAFAFA : 0xFF000000;
    }

    static int subText() {
        return dark ? 0xFF999999 : 0xFF6E6E6E;
    }

    static int divider() {
        return dark ? 0x1FFFFFFF : 0x1F000000;
    }

    /** Accent text (text buttons, values): darker blue on light, lighter blue on dark. */
    static int accentText() {
        return dark ? CONTROL_ACTIVE : PRIMARY_DARK;
    }

    static GradientDrawable rounded(int color, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radiusDp));
        return d;
    }

    /** Rounded only on the top corners (sheets that rise from the bottom). */
    static GradientDrawable topRounded(int color, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        float r = dp(radiusDp);
        d.setCornerRadii(new float[] {r, r, r, r, 0, 0, 0, 0});
        return d;
    }

    static TextView text(Context c, CharSequence s, float sp, int color, boolean medium) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setTypeface(Typeface.create(medium ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
        t.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        return t;
    }

    /** Text over the wallpaper: white with a soft shadow, readable on any picture. */
    static void onWallpaper(TextView t) {
        t.setTextColor(Color.WHITE);
        t.setShadowLayer(dp(3), 0, dp(1), 0x66000000);
    }
}
