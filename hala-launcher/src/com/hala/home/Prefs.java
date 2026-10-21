package com.hala.home;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;

/**
 * Settings and the home layout. The home screen is one list of slots, page after page
 * (`columns × rows` slots per page); a slot holds an app key or nothing. The dock is a list
 * of app keys.
 */
final class Prefs {
    static final int MIN_COLUMNS = 4;
    static final int MAX_COLUMNS = 9;
    static final int MIN_ROWS = 3;
    static final int MAX_ROWS = 8;
    static final int MIN_ICON = 44;
    static final int MAX_ICON = 96;
    static final int MAX_DOCK = 10;

    private final SharedPreferences sp;

    Prefs(Context c) {
        sp = c.getSharedPreferences("hala", Context.MODE_PRIVATE);
    }

    boolean firstRun() {
        return !sp.contains("home");
    }

    int columns(boolean landscape) {
        return sp.getInt(landscape ? "columnsLand" : "columns", landscape ? 7 : 5);
    }

    int rows(boolean landscape) {
        return sp.getInt(landscape ? "rowsLand" : "rows", landscape ? 4 : 6);
    }

    void setGrid(boolean landscape, int columns, int rows) {
        sp.edit()
            .putInt(landscape ? "columnsLand" : "columns", clamp(columns, MIN_COLUMNS, MAX_COLUMNS))
            .putInt(landscape ? "rowsLand" : "rows", clamp(rows, MIN_ROWS, MAX_ROWS))
            .apply();
    }

    int iconDp() {
        return clamp(sp.getInt("icon", 64), MIN_ICON, MAX_ICON);
    }

    void setIconDp(int dp) {
        sp.edit().putInt("icon", clamp(dp, MIN_ICON, MAX_ICON)).apply();
    }

    boolean labels() {
        return sp.getBoolean("labels", true);
    }

    void setLabels(boolean on) {
        sp.edit().putBoolean("labels", on).apply();
    }

    /** `auto`, `light` or `dark`. */
    String theme() {
        return sp.getString("theme", "auto");
    }

    void setTheme(String theme) {
        sp.edit().putString("theme", theme).apply();
    }

    /** Hala's One UI-style icons for the device's built-in apps. */
    boolean themedIcons() {
        return sp.getBoolean("themedIcons", true);
    }

    void setThemedIcons(boolean on) {
        sp.edit().putBoolean("themedIcons", on).apply();
    }

    /** The clock, weather and brief cards at the top of the home screen. */
    boolean widgets() {
        return sp.getBoolean("widgets", true);
    }

    void setWidgets(boolean on) {
        sp.edit().putBoolean("widgets", on).apply();
    }

    /** A swipe down on the right half opens Hala's quick panel (the left half, notifications). */
    boolean quickPanel() {
        return sp.getBoolean("quickPanel", true);
    }

    void setQuickPanel(boolean on) {
        sp.edit().putBoolean("quickPanel", on).apply();
    }

    boolean recents() {
        return sp.getBoolean("recents", true);
    }

    void setRecents(boolean on) {
        sp.edit().putBoolean("recents", on).apply();
    }

    /**
     * Home slots in order for one orientation (each has its own grid, so each keeps its own
     * arrangement); `null` is an empty slot. Landscape starts as a copy of portrait.
     */
    List<String> home(boolean landscape) {
        if (landscape && sp.contains("homeLand")) {
            return read("homeLand", true);
        }
        return read("home", true);
    }

    void setHome(boolean landscape, List<String> slots) {
        // Trailing empty slots are dropped; pages are rebuilt from the slot count.
        int end = slots.size();
        while (end > 0 && slots.get(end - 1) == null) {
            end--;
        }
        write(landscape ? "homeLand" : "home", slots.subList(0, end));
    }

    List<String> dock() {
        List<String> d = read("dock", false);
        return d.size() > MAX_DOCK ? new ArrayList<String>(d.subList(0, MAX_DOCK)) : d;
    }

    void setDock(List<String> keys) {
        write("dock", keys);
    }

    private List<String> read(String name, boolean keepEmpty) {
        List<String> out = new ArrayList<String>();
        String raw = sp.getString(name, "[]");
        try {
            JSONArray a = new JSONArray(raw);
            for (int i = 0; i < a.length(); i++) {
                if (a.isNull(i)) {
                    if (keepEmpty) {
                        out.add(null);
                    }
                } else {
                    out.add(a.getString(i));
                }
            }
        } catch (JSONException e) {
            // A damaged layout starts over empty rather than crashing the home screen.
        }
        return out;
    }

    private void write(String name, List<String> keys) {
        JSONArray a = new JSONArray();
        for (String k : keys) {
            a.put(k == null ? org.json.JSONObject.NULL : k);
        }
        sp.edit().putString(name, a.toString()).apply();
    }

    static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
