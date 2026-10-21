package com.hala.home;

import android.app.NotificationManager;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.location.LocationManager;
import android.media.AudioManager;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;

/**
 * A control panel in the manner of One UI's quick panel: a rounded glass card at the top of the
 * screen with large Wi-Fi and Bluetooth buttons, a row of round toggles and brightness and
 * volume sliders. It opens with a swipe down on the right-hand half of the home screen.
 *
 * Android lets an ordinary app switch only some of these itself (flashlight, rotation, Do not
 * disturb, brightness, volume, the launcher's own dark mode); for Wi-Fi, Bluetooth, location
 * and airplane mode the button opens the system's own control.
 */
final class QuickPanel {
    interface Host {
        void dismissOverlay();

        void expandNotifications();

        /** Hala's own theme: true for dark. */
        boolean darkTheme();

        void setDarkTheme(boolean dark);

        void openHomeSettings();

        void start(Intent i);
    }

    private QuickPanel() {}

    private static String torchId;
    private static boolean torchOn;
    private static CameraManager.TorchCallback torchCallback;

    static void show(final FrameLayout overlay, final Host host, int topInset) {
        final Context c = overlay.getContext();
        overlay.removeAllViews();
        View scrim = new View(c);
        scrim.setBackgroundColor(0x33000000);
        scrim.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                host.dismissOverlay();
            }
        });
        overlay.addView(scrim, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        final LinearLayout panel = new LinearLayout(c);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackground(Ui.rounded(Ui.dark ? 0xEB1E1F23 : 0xEBF6F6F8, Ui.RADIUS_BLOCK));
        panel.setElevation(Ui.dp(12));
        panel.setPadding(Ui.dp(16), Ui.dp(14), Ui.dp(16), Ui.dp(16));
        panel.setClickable(true);
        // Swiping the panel up puts it away.
        panel.setOnTouchListener(new View.OnTouchListener() {
            float downY;

            @Override
            public boolean onTouch(View v, MotionEvent e) {
                if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    downY = e.getRawY();
                } else if (e.getActionMasked() == MotionEvent.ACTION_UP && downY - e.getRawY() > Ui.dp(60)) {
                    host.dismissOverlay();
                    return true;
                }
                return false;
            }
        });

        // Header: date, then settings and notifications.
        LinearLayout head = new LinearLayout(c);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextClock date = new TextClock(c);
        date.setFormat12Hour("h:mm  ·  EEE d MMM");
        date.setFormat24Hour("HH:mm  ·  EEE d MMM");
        date.setTextSize(15);
        date.setTextColor(Ui.text());
        date.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        head.addView(date, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        head.addView(iconButton(c, R.drawable.glyph_bell, new Runnable() {
            @Override
            public void run() {
                host.dismissOverlay();
                host.expandNotifications();
            }
        }));
        head.addView(iconButton(c, R.drawable.glyph_gear, new Runnable() {
            @Override
            public void run() {
                host.start(new Intent(Settings.ACTION_SETTINGS));
            }
        }));
        panel.addView(head);

        // Wi-Fi and Bluetooth: large buttons with their state.
        LinearLayout big = new LinearLayout(c);
        big.setPadding(0, Ui.dp(12), 0, 0);
        WifiManager wifi = (WifiManager) c.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        boolean wifiOn = wifi != null && wifi.isWifiEnabled();
        big.addView(bigTile(c, R.drawable.glyph_wifi, c.getString(R.string.qp_wifi), wifiOn, new Runnable() {
            @Override
            public void run() {
                host.start(new Intent(Settings.Panel.ACTION_WIFI));
            }
        }), weighted(false));
        boolean btOn = false;
        try {
            BluetoothAdapter bt = BluetoothAdapter.getDefaultAdapter();
            btOn = bt != null && bt.isEnabled();
        } catch (RuntimeException e) {
            btOn = false;
        }
        big.addView(bigTile(c, R.drawable.glyph_bluetooth, c.getString(R.string.qp_bluetooth), btOn, new Runnable() {
            @Override
            public void run() {
                host.start(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));
            }
        }), weighted(true));
        panel.addView(big);

        // Round toggles.
        LinearLayout round = new LinearLayout(c);
        round.setPadding(0, Ui.dp(14), 0, Ui.dp(4));
        round.addView(flashlight(c), roundParams());
        round.addView(rotation(c, host), roundParams());
        round.addView(dnd(c, host), roundParams());
        LocationManager lm = (LocationManager) c.getSystemService(Context.LOCATION_SERVICE);
        boolean locOn = lm != null && lm.isLocationEnabled();
        round.addView(roundTile(c, R.drawable.glyph_location, c.getString(R.string.qp_location), locOn, new Toggle() {
            @Override
            public boolean toggle(boolean on) {
                host.start(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                return on;
            }
        }), roundParams());
        boolean airplane = Settings.Global.getInt(c.getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, 0) == 1;
        round.addView(roundTile(c, R.drawable.glyph_airplane, c.getString(R.string.qp_airplane), airplane, new Toggle() {
            @Override
            public boolean toggle(boolean on) {
                host.start(new Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS));
                return on;
            }
        }), roundParams());
        panel.addView(round);

        // Sliders.
        panel.addView(brightness(c, host), sliderParams());
        panel.addView(volume(c), sliderParams());

        // Hala's own dark mode and home settings.
        LinearLayout bottom = new LinearLayout(c);
        bottom.setPadding(0, Ui.dp(12), 0, 0);
        bottom.addView(bigTile(c, R.drawable.glyph_moon, c.getString(R.string.qp_dark), host.darkTheme(), new Runnable() {
            @Override
            public void run() {
                host.setDarkTheme(!host.darkTheme());
            }
        }), weighted(false));
        bottom.addView(bigTile(c, R.drawable.glyph_home, c.getString(R.string.qp_home_settings), false, new Runnable() {
            @Override
            public void run() {
                host.openHomeSettings();
            }
        }), weighted(true));
        panel.addView(bottom);

        int width = Math.min(Ui.dp(400), overlay.getWidth() - Ui.dp(32));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(width, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.END);
        lp.topMargin = topInset + Ui.dp(10);
        lp.setMarginEnd(Ui.dp(16));
        overlay.addView(panel, lp);

        overlay.setVisibility(View.VISIBLE);
        scrim.setAlpha(0f);
        scrim.animate().alpha(1f).setDuration(200).start();
        panel.setAlpha(0f);
        panel.setTranslationY(-Ui.dp(40));
        panel.animate().alpha(1f).translationY(0).setDuration(260)
            .setInterpolator(new android.view.animation.PathInterpolator(0.22f, 0.25f, 0f, 1f)).start();
    }

    // ---- Building blocks ----

    private interface Toggle {
        /** Act on a tap; return the new state. */
        boolean toggle(boolean on);
    }

    private static LinearLayout.LayoutParams weighted(boolean gap) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.dp(60), 1f);
        if (gap) {
            lp.setMarginStart(Ui.dp(10));
        }
        return lp;
    }

    private static LinearLayout.LayoutParams roundParams() {
        return new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
    }

    private static LinearLayout.LayoutParams sliderParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(48));
        lp.topMargin = Ui.dp(10);
        return lp;
    }

    private static int tileColor() {
        return Ui.dark ? 0x1FFFFFFF : 0xFFFFFFFF;
    }

    private static Drawable glyph(Context c, int id, int color) {
        Drawable d = c.getDrawable(id).mutate();
        d.setTint(color);
        return d;
    }

    private static View iconButton(Context c, int glyph, final Runnable action) {
        ImageView b = new ImageView(c);
        b.setImageDrawable(glyph(c, glyph, Ui.text()));
        b.setPadding(Ui.dp(10), Ui.dp(10), Ui.dp(10), Ui.dp(10));
        b.setBackground(AppMenu.pressable(c));
        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                action.run();
            }
        });
        b.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(44), Ui.dp(44)));
        return b;
    }

    private static View bigTile(Context c, int glyph, String label, boolean on, final Runnable action) {
        LinearLayout t = new LinearLayout(c);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setBackground(Ui.rounded(tileColor(), 30));
        t.setPadding(Ui.dp(8), 0, Ui.dp(12), 0);
        ImageView icon = new ImageView(c);
        icon.setImageDrawable(glyph(c, glyph, on ? 0xFFFFFFFF : Ui.text()));
        icon.setBackground(Ui.rounded(on ? Ui.PRIMARY : Ui.divider(), 22));
        icon.setPadding(Ui.dp(11), Ui.dp(11), Ui.dp(11), Ui.dp(11));
        t.addView(icon, new LinearLayout.LayoutParams(Ui.dp(44), Ui.dp(44)));
        LinearLayout labels = new LinearLayout(c);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setPadding(Ui.dp(10), 0, 0, 0);
        TextView name = Ui.text(c, label, 14, Ui.text(), true);
        name.setSingleLine(true);
        labels.addView(name);
        labels.addView(Ui.text(c, c.getString(on ? R.string.qp_on : R.string.qp_off), 12, Ui.subText(), false));
        t.addView(labels, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        t.setForeground(AppMenu.pressable(c));
        t.setClipToOutline(true);
        t.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                action.run();
            }
        });
        return t;
    }

    private static View roundTile(final Context c, final int glyph, String label, boolean on, final Toggle toggle) {
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        final ImageView icon = new ImageView(c);
        icon.setPadding(Ui.dp(14), Ui.dp(14), Ui.dp(14), Ui.dp(14));
        final boolean[] state = {on};
        paintRound(c, icon, glyph, on);
        col.addView(icon, new LinearLayout.LayoutParams(Ui.dp(52), Ui.dp(52)));
        TextView name = Ui.text(c, label, 11, Ui.subText(), false);
        name.setSingleLine(true);
        name.setGravity(Gravity.CENTER);
        name.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        name.setPadding(0, Ui.dp(6), 0, 0);
        col.addView(name, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        icon.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                state[0] = toggle.toggle(state[0]);
                paintRound(c, icon, glyph, state[0]);
            }
        });
        return col;
    }

    private static void paintRound(Context c, ImageView icon, int glyph, boolean on) {
        icon.setImageDrawable(glyph(c, glyph, on ? 0xFFFFFFFF : Ui.text()));
        icon.setBackground(Ui.rounded(on ? Ui.PRIMARY : tileColor(), 26));
    }

    // ---- Toggles Android lets the launcher switch ----

    private static View flashlight(final Context c) {
        final CameraManager cm = (CameraManager) c.getSystemService(Context.CAMERA_SERVICE);
        if (torchId == null && cm != null) {
            try {
                for (String id : cm.getCameraIdList()) {
                    Boolean flash = cm.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                    if (flash != null && flash) {
                        torchId = id;
                        break;
                    }
                }
            } catch (Exception e) {
                torchId = null;
            }
            if (torchCallback == null) {
                torchCallback = new CameraManager.TorchCallback() {
                    @Override
                    public void onTorchModeChanged(String cameraId, boolean enabled) {
                        if (cameraId.equals(torchId)) {
                            torchOn = enabled;
                        }
                    }
                };
                cm.registerTorchCallback(torchCallback, new Handler(Looper.getMainLooper()));
            }
        }
        return roundTile(c, R.drawable.glyph_flashlight, c.getString(R.string.qp_flashlight), torchOn, new Toggle() {
            @Override
            public boolean toggle(boolean on) {
                if (cm == null || torchId == null) {
                    return false;
                }
                try {
                    cm.setTorchMode(torchId, !on);
                    return !on;
                } catch (Exception e) {
                    return on;
                }
            }
        });
    }

    private static boolean canWriteSettings(Context c, Host host) {
        if (Settings.System.canWrite(c)) {
            return true;
        }
        host.start(new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + c.getPackageName())));
        return false;
    }

    private static View rotation(final Context c, final Host host) {
        boolean on = Settings.System.getInt(c.getContentResolver(), Settings.System.ACCELEROMETER_ROTATION, 0) == 1;
        return roundTile(c, R.drawable.glyph_rotate, c.getString(R.string.qp_rotate), on, new Toggle() {
            @Override
            public boolean toggle(boolean on) {
                if (!canWriteSettings(c, host)) {
                    return on;
                }
                Settings.System.putInt(c.getContentResolver(), Settings.System.ACCELEROMETER_ROTATION, on ? 0 : 1);
                return !on;
            }
        });
    }

    private static View dnd(final Context c, final Host host) {
        final NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        boolean on = nm != null && nm.getCurrentInterruptionFilter() > NotificationManager.INTERRUPTION_FILTER_ALL;
        return roundTile(c, R.drawable.glyph_dnd, c.getString(R.string.qp_dnd), on, new Toggle() {
            @Override
            public boolean toggle(boolean on) {
                if (nm == null) {
                    return on;
                }
                if (!nm.isNotificationPolicyAccessGranted()) {
                    host.start(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS));
                    return on;
                }
                nm.setInterruptionFilter(on ? NotificationManager.INTERRUPTION_FILTER_ALL : NotificationManager.INTERRUPTION_FILTER_PRIORITY);
                return !on;
            }
        });
    }

    private static View brightness(final Context c, final Host host) {
        int now = Settings.System.getInt(c.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, 128);
        // The slider runs on a perceptual scale: the setting is roughly the square of it.
        float fraction = (float) Math.sqrt(Math.max(0, Math.min(255, now)) / 255f);
        return new PillSlider(c, R.drawable.glyph_brightness, fraction, new PillSlider.Listener() {
            boolean asked;

            @Override
            public void onChange(float f, boolean done) {
                if (!Settings.System.canWrite(c)) {
                    if (done && !asked) {
                        asked = true;
                        canWriteSettings(c, host);
                    }
                    return;
                }
                int v = Math.max(1, Math.round(255 * f * f));
                Settings.System.putInt(c.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
                Settings.System.putInt(c.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, v);
            }
        });
    }

    private static View volume(Context c) {
        final AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
        final int max = am == null ? 1 : Math.max(1, am.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
        float fraction = am == null ? 0 : am.getStreamVolume(AudioManager.STREAM_MUSIC) / (float) max;
        return new PillSlider(c, R.drawable.glyph_volume, fraction, new PillSlider.Listener() {
            @Override
            public void onChange(float f, boolean done) {
                if (am == null) {
                    return;
                }
                try {
                    am.setStreamVolume(AudioManager.STREAM_MUSIC, Math.round(f * max), 0);
                } catch (SecurityException e) {
                    // Do not disturb can forbid changing the volume.
                }
            }
        });
    }

    /** A thick rounded slider with its icon inside, filled from the start side. */
    static final class PillSlider extends View {
        interface Listener {
            void onChange(float fraction, boolean done);
        }

        private final Drawable icon;
        private final Listener listener;
        private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private float fraction;

        PillSlider(Context c, int glyph, float fraction, Listener listener) {
            super(c);
            this.fraction = Math.max(0, Math.min(1, fraction));
            this.listener = listener;
            icon = glyph(c, glyph, Ui.dark ? 0xFFFFFFFF : 0xFF3A3A3A);
            track.setColor(tileColor());
            fill.setColor(Ui.dark ? 0xFF5A5D66 : 0xFFDCE6F7);
        }

        private boolean rtl() {
            return getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth();
            float h = getHeight();
            float r = h / 2f;
            rect.set(0, 0, w, h);
            canvas.drawRoundRect(rect, r, r, track);
            float filled = h + (w - h) * fraction;
            if (rtl()) {
                rect.set(w - filled, 0, w, h);
            } else {
                rect.set(0, 0, filled, h);
            }
            canvas.drawRoundRect(rect, r, r, fill);
            int s = Math.round(h * 0.5f);
            int x = rtl() ? Math.round(w - h / 2f - s / 2f) : Math.round(h / 2f - s / 2f);
            int y = Math.round(h / 2f - s / 2f);
            icon.setBounds(x, y, x + s, y + s);
            icon.draw(canvas);
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    getParent().requestDisallowInterceptTouchEvent(true);
                    // Fall through: a tap sets the value too.
                case MotionEvent.ACTION_MOVE:
                case MotionEvent.ACTION_UP: {
                    float w = getWidth();
                    float h = getHeight();
                    float x = rtl() ? w - e.getX() : e.getX();
                    fraction = Math.max(0, Math.min(1, (x - h / 2f) / Math.max(1f, w - h)));
                    invalidate();
                    listener.onChange(fraction, e.getActionMasked() == MotionEvent.ACTION_UP);
                    return true;
                }
                default:
                    return true;
            }
        }
    }
}
