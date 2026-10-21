package com.hala.home;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Locale;

/**
 * Current weather for the weather card, from Open-Meteo (a free forecast service that needs no
 * account): the device's approximate location goes out, the temperature and conditions come
 * back. The last result is kept, so the card shows something at once and refreshes at most
 * every half hour.
 */
final class Weather {
    interface Listener {
        void onWeather(Weather w);
    }

    /** Conditions, grouped from the WMO weather codes the service reports. */
    static final int CLEAR = 0;
    static final int PARTLY = 1;
    static final int CLOUDY = 2;
    static final int FOG = 3;
    static final int DRIZZLE = 4;
    static final int RAIN = 5;
    static final int SNOW = 6;
    static final int STORM = 7;

    private static final long FRESH_MS = 30 * 60 * 1000L;

    boolean known;
    /** The last attempt failed (location off, no fix or no network). */
    boolean failed;
    private long lastAttempt;
    int temp;
    int high;
    int low;
    int condition;
    boolean day = true;
    String city = "";
    long time;

    private final Context context;
    private final SharedPreferences sp;
    private final Handler main = new Handler(Looper.getMainLooper());
    private boolean loading;

    Weather(Context c) {
        context = c.getApplicationContext();
        sp = context.getSharedPreferences("weather", Context.MODE_PRIVATE);
        known = sp.contains("time");
        temp = sp.getInt("temp", 0);
        high = sp.getInt("high", 0);
        low = sp.getInt("low", 0);
        condition = sp.getInt("condition", CLEAR);
        day = sp.getBoolean("day", true);
        city = sp.getString("city", "");
        time = sp.getLong("time", 0);
    }

    boolean permitted() {
        return context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    /** Fetch again if the last result is older than half an hour (or `force`). */
    void refresh(boolean force, final Listener listener) {
        long now = System.currentTimeMillis();
        if (loading || !permitted() || (!force && (now - time < FRESH_MS || now - lastAttempt < 5 * 60 * 1000L))) {
            return;
        }
        loading = true;
        lastAttempt = now;
        new Thread(new Runnable() {
            @Override
            public void run() {
                final boolean ok = fetch();
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        loading = false;
                        failed = !ok;
                        listener.onWeather(Weather.this);
                    }
                });
            }
        }, "hala-weather").start();
    }

    private Location location() {
        LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        if (lm == null) {
            return null;
        }
        Location best = null;
        String[] providers = {"fused", LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER, LocationManager.GPS_PROVIDER};
        for (String p : providers) {
            try {
                Location l = lm.getLastKnownLocation(p);
                if (l != null && (best == null || l.getTime() > best.getTime())) {
                    best = l;
                }
            } catch (RuntimeException e) {
                // That provider isn't on this device, or isn't allowed.
            }
        }
        if (best == null && Build.VERSION.SDK_INT >= 30) {
            // Nothing cached: ask the network provider once and wait briefly.
            final Location[] got = new Location[1];
            final Object lock = new Object();
            try {
                lm.getCurrentLocation(LocationManager.NETWORK_PROVIDER, null, context.getMainExecutor(),
                    new java.util.function.Consumer<Location>() {
                        @Override
                        public void accept(Location l) {
                            synchronized (lock) {
                                got[0] = l;
                                lock.notifyAll();
                            }
                        }
                    });
                synchronized (lock) {
                    if (got[0] == null) {
                        lock.wait(15000);
                    }
                }
                best = got[0];
            } catch (Exception e) {
                best = null;
            }
        }
        return best;
    }

    private boolean fetch() {
        Location loc;
        try {
            loc = location();
        } catch (SecurityException e) {
            return false;
        }
        if (loc == null) {
            return false;
        }
        double lat = loc.getLatitude();
        double lon = loc.getLongitude();
        try {
            String url = String.format(Locale.ROOT,
                "https://api.open-meteo.com/v1/forecast?latitude=%.3f&longitude=%.3f"
                    + "&current=temperature_2m,weather_code,is_day&daily=temperature_2m_max,temperature_2m_min"
                    + "&timezone=auto&forecast_days=1", lat, lon);
            JSONObject o = new JSONObject(get(url));
            JSONObject cur = o.getJSONObject("current");
            JSONObject daily = o.getJSONObject("daily");
            temp = (int) Math.round(cur.getDouble("temperature_2m"));
            condition = group(cur.getInt("weather_code"));
            day = cur.optInt("is_day", 1) == 1;
            high = (int) Math.round(daily.getJSONArray("temperature_2m_max").getDouble(0));
            low = (int) Math.round(daily.getJSONArray("temperature_2m_min").getDouble(0));
        } catch (Exception e) {
            return false;
        }
        city = placeName(lat, lon);
        time = System.currentTimeMillis();
        known = true;
        sp.edit().putInt("temp", temp).putInt("high", high).putInt("low", low).putInt("condition", condition)
            .putBoolean("day", day).putString("city", city).putLong("time", time).apply();
        return true;
    }

    private String placeName(double lat, double lon) {
        try {
            if (!Geocoder.isPresent()) {
                return "";
            }
            Locale locale = context.getResources().getConfiguration().getLocales().get(0);
            List<Address> found = new Geocoder(context, locale).getFromLocation(lat, lon, 1);
            if (found == null || found.isEmpty()) {
                return "";
            }
            Address a = found.get(0);
            String name = a.getLocality();
            if (name == null) {
                name = a.getSubAdminArea();
            }
            if (name == null) {
                name = a.getAdminArea();
            }
            return name == null ? "" : name;
        } catch (Exception e) {
            return "";
        }
    }

    private static String get(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(10000);
        try {
            InputStream in = c.getInputStream();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            return out.toString("UTF-8");
        } finally {
            c.disconnect();
        }
    }

    static int group(int wmo) {
        if (wmo == 0) {
            return CLEAR;
        }
        if (wmo <= 2) {
            return PARTLY;
        }
        if (wmo == 3) {
            return CLOUDY;
        }
        if (wmo == 45 || wmo == 48) {
            return FOG;
        }
        if (wmo >= 51 && wmo <= 57) {
            return DRIZZLE;
        }
        if (wmo >= 61 && wmo <= 67 || wmo >= 80 && wmo <= 82) {
            return RAIN;
        }
        if (wmo >= 71 && wmo <= 77 || wmo == 85 || wmo == 86) {
            return SNOW;
        }
        if (wmo >= 95) {
            return STORM;
        }
        return CLOUDY;
    }

    static int label(int condition) {
        switch (condition) {
            case CLEAR:
                return R.string.wx_clear;
            case PARTLY:
                return R.string.wx_partly;
            case FOG:
                return R.string.wx_fog;
            case DRIZZLE:
                return R.string.wx_drizzle;
            case RAIN:
                return R.string.wx_rain;
            case SNOW:
                return R.string.wx_snow;
            case STORM:
                return R.string.wx_storm;
            default:
                return R.string.wx_cloudy;
        }
    }
}
