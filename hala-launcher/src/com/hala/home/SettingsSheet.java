package com.hala.home;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

/**
 * Home screen settings, a sheet that rises from the bottom (within reach of the thumb, as One
 * UI's guidelines ask for dialogs). Each group of settings is a rounded focus block; every
 * change applies at once.
 */
final class SettingsSheet {
    interface Host {
        Prefs prefs();

        boolean landscape();

        /** A setting changed: lay the home screen out again. */
        void settingsChanged();

        /** Turn on recent apps (asks for Usage access when it isn't granted). */
        void requestRecents();

        void pickWallpaper();

        void chooseDefaultHome();

        void dismissOverlay();
    }

    private SettingsSheet() {}

    static void show(final FrameLayout overlay, final Host host) {
        final Context c = overlay.getContext();
        overlay.removeAllViews();
        View scrim = new View(c);
        scrim.setBackgroundColor(0x66000000);
        scrim.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                host.dismissOverlay();
            }
        });
        overlay.addView(scrim, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        LinearLayout sheet = new LinearLayout(c);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setBackground(Ui.topRounded(Ui.dark ? 0xFF111111 : 0xFFF2F2F2, Ui.RADIUS_BLOCK));
        sheet.setClickable(true);
        sheet.setPadding(Ui.dp(Ui.MARGIN), Ui.dp(10), Ui.dp(Ui.MARGIN), Ui.dp(Ui.MARGIN));

        View handle = new View(c);
        handle.setBackground(Ui.rounded(Ui.subText(), 2));
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(Ui.dp(36), Ui.dp(4));
        hp.gravity = Gravity.CENTER_HORIZONTAL;
        hp.bottomMargin = Ui.dp(16);
        sheet.addView(handle, hp);

        LinearLayout titleRow = new LinearLayout(c);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = Ui.text(c, c.getString(R.string.settings), 20, Ui.text(), true);
        titleRow.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView done = Ui.text(c, c.getString(R.string.done), 17, Ui.accentText(), true);
        done.setPadding(Ui.dp(12), Ui.dp(8), Ui.dp(12), Ui.dp(8));
        done.setBackground(AppMenu.pressable(c));
        done.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                host.dismissOverlay();
            }
        });
        titleRow.addView(done);
        sheet.addView(titleRow);

        ScrollView scroll = new ScrollView(c);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout body = new LinearLayout(c);
        body.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(body);
        sheet.addView(scroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        final Prefs prefs = host.prefs();
        final boolean land = host.landscape();

        // Layout: grid and icon size.
        LinearLayout layout = section(c, body, c.getString(R.string.section_layout));
        layout.addView(stepper(c, c.getString(R.string.grid_columns), prefs.columns(land), Prefs.MIN_COLUMNS, Prefs.MAX_COLUMNS, new Step() {
            @Override
            public void set(int v) {
                prefs.setGrid(land, v, prefs.rows(land));
                host.settingsChanged();
            }
        }));
        layout.addView(divider(c));
        layout.addView(stepper(c, c.getString(R.string.grid_rows), prefs.rows(land), Prefs.MIN_ROWS, Prefs.MAX_ROWS, new Step() {
            @Override
            public void set(int v) {
                prefs.setGrid(land, prefs.columns(land), v);
                host.settingsChanged();
            }
        }));
        layout.addView(divider(c));
        layout.addView(slider(c, c.getString(R.string.icon_size), prefs.iconDp(), Prefs.MIN_ICON, Prefs.MAX_ICON, new Step() {
            @Override
            public void set(int v) {
                prefs.setIconDp(v);
                host.settingsChanged();
            }
        }));
        layout.addView(divider(c));
        layout.addView(toggle(c, c.getString(R.string.show_labels), null, prefs.labels(), new Toggle() {
            @Override
            public void set(boolean on) {
                prefs.setLabels(on);
                host.settingsChanged();
            }
        }));

        // Appearance: theme.
        LinearLayout look = section(c, body, c.getString(R.string.section_look));
        look.addView(themeChoice(c, prefs, host));
        look.addView(divider(c));
        look.addView(toggle(c, c.getString(R.string.recent_apps), c.getString(R.string.recent_apps_hint), prefs.recents(), new Toggle() {
            @Override
            public void set(boolean on) {
                prefs.setRecents(on);
                if (on) {
                    host.requestRecents();
                }
                host.settingsChanged();
            }
        }));

        // System: wallpaper and the default home app (the way back to the device's own).
        LinearLayout system = section(c, body, c.getString(R.string.section_system));
        system.addView(action(c, c.getString(R.string.wallpaper), null, new Runnable() {
            @Override
            public void run() {
                host.pickWallpaper();
            }
        }));
        system.addView(divider(c));
        system.addView(action(c, c.getString(R.string.default_home), c.getString(R.string.default_home_hint), new Runnable() {
            @Override
            public void run() {
                host.chooseDefaultHome();
            }
        }));

        String version = "";
        try {
            version = c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName;
        } catch (Exception e) {
            // No version to show.
        }
        TextView about = Ui.text(c, c.getString(R.string.about, version), 13, Ui.subText(), false);
        about.setPadding(Ui.dp(8), Ui.dp(16), Ui.dp(8), 0);
        body.addView(about);

        // At most 640 dp wide and 85% of the height, centred at the bottom.
        int width = Math.min(overlay.getWidth(), Ui.dp(640));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(width, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        overlay.addView(sheet, lp);
        sheet.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int max = (int) (overlay.getHeight() * 0.85f);
        if (sheet.getMeasuredHeight() > max) {
            lp.height = max;
            sheet.setLayoutParams(lp);
        }
        overlay.setVisibility(View.VISIBLE);
        scrim.setAlpha(0f);
        scrim.animate().alpha(1f).setDuration(200).start();
        sheet.setTranslationY(Math.min(max, sheet.getMeasuredHeight()));
        sheet.animate().translationY(0).setDuration(320).setInterpolator(new android.view.animation.PathInterpolator(0.22f, 0.25f, 0f, 1f)).start();
    }

    // ---- Building blocks ----

    private interface Step {
        void set(int v);
    }

    private interface Toggle {
        void set(boolean on);
    }

    private static LinearLayout section(Context c, LinearLayout body, String title) {
        TextView head = Ui.text(c, title, 14, Ui.subText(), true);
        head.setPadding(Ui.dp(8), Ui.dp(20), Ui.dp(8), Ui.dp(8));
        body.addView(head);
        LinearLayout block = new LinearLayout(c);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setBackground(Ui.rounded(Ui.block(), Ui.RADIUS_BLOCK));
        block.setPadding(0, Ui.dp(6), 0, Ui.dp(6));
        body.addView(block, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return block;
    }

    private static View divider(Context c) {
        View d = new View(c);
        d.setBackgroundColor(Ui.divider());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, Ui.dp(0.5f)));
        lp.leftMargin = lp.rightMargin = Ui.dp(20);
        d.setLayoutParams(lp);
        return d;
    }

    private static LinearLayout row(Context c) {
        LinearLayout r = new LinearLayout(c);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setMinimumHeight(Ui.dp(56));
        r.setPadding(Ui.dp(20), Ui.dp(10), Ui.dp(20), Ui.dp(10));
        return r;
    }

    private static LinearLayout labels(Context c, String title, String hint) {
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(Ui.text(c, title, 18, Ui.text(), false));
        if (hint != null) {
            TextView h = Ui.text(c, hint, 13, Ui.subText(), false);
            h.setPadding(0, Ui.dp(2), 0, 0);
            col.addView(h);
        }
        return col;
    }

    private static View stepper(Context c, String title, int value, final int min, final int max, final Step step) {
        LinearLayout r = row(c);
        r.addView(labels(c, title, null), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        final int[] v = {value};
        final TextView shown = Ui.text(c, String.valueOf(value), 18, Ui.accentText(), true);
        shown.setGravity(Gravity.CENTER);
        final TextView minus = roundButton(c, "−");
        final TextView plus = roundButton(c, "+");
        minus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (v[0] > min) {
                    v[0]--;
                    shown.setText(String.valueOf(v[0]));
                    step.set(v[0]);
                }
            }
        });
        plus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (v[0] < max) {
                    v[0]++;
                    shown.setText(String.valueOf(v[0]));
                    step.set(v[0]);
                }
            }
        });
        r.addView(minus, new LinearLayout.LayoutParams(Ui.dp(40), Ui.dp(40)));
        r.addView(shown, new LinearLayout.LayoutParams(Ui.dp(48), LinearLayout.LayoutParams.WRAP_CONTENT));
        r.addView(plus, new LinearLayout.LayoutParams(Ui.dp(40), Ui.dp(40)));
        return r;
    }

    private static TextView roundButton(Context c, String glyph) {
        TextView b = Ui.text(c, glyph, 22, Ui.text(), false);
        b.setGravity(Gravity.CENTER);
        b.setBackground(Ui.rounded(Ui.divider(), 20));
        return b;
    }

    private static View slider(Context c, String title, int value, final int min, int max, final Step step) {
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(Ui.dp(20), Ui.dp(14), Ui.dp(20), Ui.dp(10));
        col.addView(Ui.text(c, title, 18, Ui.text(), false));
        SeekBar bar = new SeekBar(c);
        bar.setMax(max - min);
        bar.setProgress(value - min);
        ColorStateList accent = ColorStateList.valueOf(Ui.PRIMARY);
        bar.setProgressTintList(accent);
        bar.setThumbTintList(accent);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {}

            @Override
            public void onStartTrackingTouch(SeekBar s) {}

            @Override
            public void onStopTrackingTouch(SeekBar s) {
                step.set(min + s.getProgress());
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(8);
        col.addView(bar, lp);
        return col;
    }

    private static View toggle(Context c, String title, String hint, boolean on, final Toggle toggle) {
        LinearLayout r = row(c);
        r.addView(labels(c, title, hint), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        final Switch sw = new Switch(c);
        sw.setChecked(on);
        int[][] states = {{android.R.attr.state_checked}, {}};
        sw.setThumbTintList(new ColorStateList(states, new int[] {0xFFFFFFFF, 0xFFFFFFFF}));
        sw.setTrackTintList(new ColorStateList(states, new int[] {Ui.PRIMARY, Ui.dark ? 0xFF5E5E5E : 0xFFB8B8B8}));
        sw.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(android.widget.CompoundButton b, boolean checked) {
                toggle.set(checked);
            }
        });
        r.addView(sw);
        r.setBackground(AppMenu.pressable(c));
        r.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sw.toggle();
            }
        });
        return r;
    }

    private static View action(Context c, String title, String hint, final Runnable run) {
        LinearLayout r = row(c);
        r.addView(labels(c, title, hint), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        r.setBackground(AppMenu.pressable(c));
        r.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                run.run();
            }
        });
        return r;
    }

    /** Theme: three choices side by side, the current one in a filled pill. */
    private static View themeChoice(final Context c, final Prefs prefs, final Host host) {
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(Ui.dp(20), Ui.dp(14), Ui.dp(20), Ui.dp(14));
        col.addView(Ui.text(c, c.getString(R.string.theme), 18, Ui.text(), false));
        LinearLayout pills = new LinearLayout(c);
        pills.setBackground(Ui.rounded(Ui.divider(), 22));
        pills.setPadding(Ui.dp(4), Ui.dp(4), Ui.dp(4), Ui.dp(4));
        String[] values = {"auto", "light", "dark"};
        int[] names = {R.string.theme_auto, R.string.theme_light, R.string.theme_dark};
        String current = prefs.theme();
        for (int i = 0; i < values.length; i++) {
            final String value = values[i];
            boolean on = value.equals(current);
            TextView pill = Ui.text(c, c.getString(names[i]), 15, on ? 0xFFFFFFFF : Ui.text(), on);
            pill.setGravity(Gravity.CENTER);
            pill.setPadding(0, Ui.dp(10), 0, Ui.dp(10));
            if (on) {
                pill.setBackground(Ui.rounded(Ui.PRIMARY, 18));
            }
            pill.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    prefs.setTheme(value);
                    host.settingsChanged();
                }
            });
            pills.addView(pill, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(10);
        col.addView(pills, lp);
        return col;
    }
}
