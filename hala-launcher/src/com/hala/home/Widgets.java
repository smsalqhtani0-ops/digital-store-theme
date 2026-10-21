package com.hala.home;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.BatteryManager;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;

import java.util.Calendar;

/**
 * The cards at the top of the home screen, in the shape of One UI widgets (2 × 2 rounded
 * blocks): a clock, the weather and a short brief of the day.
 */
final class Widgets {
    interface Host {
        /** The weather card was tapped (asks for location the first time). */
        void weatherTapped();

        void openClock();

        void openCalendar();
    }

    private Widgets() {}

    static final float CARD_DP = 164;

    static LinearLayout row(Context c, final Host host, Weather weather) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(clockCard(c, host), cardParams(false));
        row.addView(weatherCard(c, host, weather), cardParams(true));
        row.addView(briefCard(c, host), cardParams(true));
        return row;
    }

    private static LinearLayout.LayoutParams cardParams(boolean gap) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(CARD_DP), Ui.dp(CARD_DP));
        if (gap) {
            lp.setMarginStart(Ui.dp(16));
        }
        return lp;
    }

    private static LinearLayout card(Context c) {
        LinearLayout card = new LinearLayout(c);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(Ui.dp(18), Ui.dp(16), Ui.dp(18), Ui.dp(16));
        card.setElevation(Ui.dp(3));
        card.setClickable(true);
        card.setForeground(AppMenu.pressable(c));
        card.setClipToOutline(true);
        return card;
    }

    private static TextView line(Context c, CharSequence s, float sp, int color, boolean medium) {
        TextView t = Ui.text(c, s, sp, color, medium);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        return t;
    }

    // ---- Clock ----

    private static View clockCard(Context c, final Host host) {
        LinearLayout card = card(c);
        card.setBackground(Ui.rounded(Ui.dark ? 0xD9202226 : 0xE6FFFFFF, Ui.RADIUS_BLOCK));
        card.setGravity(Gravity.CENTER_VERTICAL);
        TextClock time = new TextClock(c);
        time.setFormat12Hour("h:mm");
        time.setFormat24Hour("HH:mm");
        time.setTextSize(44);
        time.setTextColor(Ui.text());
        time.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        time.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        card.addView(time);
        TextClock date = new TextClock(c);
        date.setFormat12Hour("EEEE\nd MMMM");
        date.setFormat24Hour("EEEE\nd MMMM");
        date.setTextSize(15);
        date.setTextColor(Ui.subText());
        date.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        card.addView(date);
        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                host.openClock();
            }
        });
        return card;
    }

    // ---- Weather ----

    private static View weatherCard(Context c, final Host host, Weather w) {
        LinearLayout card = card(c);
        boolean night = w.known && !w.day;
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
            night ? new int[] {0xFF3A4A8C, 0xFF1D2550} : new int[] {0xFF5DB8FF, 0xFF2E78EE});
        bg.setCornerRadius(Ui.dp(Ui.RADIUS_BLOCK));
        card.setBackground(bg);

        LinearLayout top = new LinearLayout(c);
        top.setGravity(Gravity.TOP);
        TextView temp = line(c, w.known ? String.format(c.getResources().getConfiguration().getLocales().get(0), "%d°", w.temp) : "--°", 36, 0xFFFFFFFF, false);
        temp.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        top.addView(temp, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        WeatherGlyph glyph = new WeatherGlyph(c, w.known ? w.condition : Weather.PARTLY, !w.known || w.day);
        top.addView(glyph, new LinearLayout.LayoutParams(Ui.dp(46), Ui.dp(46)));
        card.addView(top, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        if (!w.permitted()) {
            card.addView(line(c, c.getString(R.string.wx_tap), 14, 0xFFFFFFFF, true));
            TextView hint = Ui.text(c, c.getString(R.string.wx_tap_hint), 12, 0xD9FFFFFF, false);
            hint.setMaxLines(2);
            card.addView(hint);
        } else if (!w.known && w.failed) {
            card.addView(line(c, c.getString(R.string.wx_tap), 14, 0xFFFFFFFF, true));
            TextView hint = Ui.text(c, c.getString(R.string.wx_failed), 12, 0xD9FFFFFF, false);
            hint.setMaxLines(2);
            card.addView(hint);
        } else if (!w.known) {
            card.addView(line(c, c.getString(R.string.wx_loading), 14, 0xFFFFFFFF, true));
        } else {
            card.addView(line(c, c.getString(Weather.label(w.condition)), 14, 0xFFFFFFFF, true));
            card.addView(line(c, c.getString(R.string.wx_high_low, w.high, w.low), 12, 0xD9FFFFFF, false));
            if (!w.city.isEmpty()) {
                card.addView(line(c, w.city, 12, 0xD9FFFFFF, false));
            }
        }
        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                host.weatherTapped();
            }
        });
        return card;
    }

    // ---- Brief ----

    private static View briefCard(Context c, final Host host) {
        LinearLayout card = card(c);
        card.setBackground(Ui.rounded(Ui.dark ? 0xD9202226 : 0xE6FFFFFF, Ui.RADIUS_BLOCK));
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        int title;
        int body;
        int[] colors;
        if (hour >= 5 && hour < 11) {
            title = R.string.brief_morning;
            body = R.string.brief_morning_body;
            colors = new int[] {0xFFFFD86B, 0xFFFF9F43};
        } else if (hour >= 11 && hour < 17) {
            title = R.string.brief_midday;
            body = R.string.brief_midday_body;
            colors = new int[] {0xFF7BE0C3, 0xFF2BB5A0};
        } else if (hour >= 17 && hour < 22) {
            title = R.string.brief_evening;
            body = R.string.brief_evening_body;
            colors = new int[] {0xFFFFA36B, 0xFFE0567A};
        } else {
            title = R.string.brief_night;
            body = R.string.brief_night_body;
            colors = new int[] {0xFF8C9BFF, 0xFF4C4FC9};
        }
        View dot = new View(c);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, colors);
        g.setShape(GradientDrawable.OVAL);
        dot.setBackground(g);
        card.addView(dot, new LinearLayout.LayoutParams(Ui.dp(34), Ui.dp(34)));
        FrameLayout spacer = new FrameLayout(c);
        card.addView(spacer, new LinearLayout.LayoutParams(1, 0, 1f));
        card.addView(line(c, c.getString(title), 16, Ui.text(), true));
        TextView text = Ui.text(c, c.getString(body), 13, Ui.subText(), false);
        text.setMaxLines(2);
        card.addView(text);
        String battery = batteryLine(c);
        if (battery != null) {
            card.addView(line(c, battery, 12, Ui.accentText(), true));
        }
        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                host.openCalendar();
            }
        });
        return card;
    }

    private static String batteryLine(Context c) {
        Intent b = c.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (b == null) {
            return null;
        }
        int level = b.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = b.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        if (level < 0 || scale <= 0) {
            return null;
        }
        int pct = Math.round(100f * level / scale);
        int status = b.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        boolean charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL;
        return c.getString(charging ? R.string.battery_charging : R.string.battery, pct);
    }

    /** A small drawing of the conditions: sun, moon, clouds, rain, snow or a storm. */
    static final class WeatherGlyph extends View {
        private final int condition;
        private final boolean day;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        WeatherGlyph(Context c, int condition, boolean day) {
            super(c);
            this.condition = condition;
            this.day = day;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float s = Math.min(getWidth(), getHeight());
            boolean clouds = condition != Weather.CLEAR;
            // Sun or moon, up and to the right when clouds share the picture.
            float cx = clouds ? s * 0.64f : s * 0.5f;
            float cy = clouds ? s * 0.36f : s * 0.5f;
            float r = clouds ? s * 0.22f : s * 0.32f;
            if (condition == Weather.CLEAR || condition == Weather.PARTLY) {
                if (day) {
                    paint.setShader(new LinearGradient(cx - r, cy - r, cx + r, cy + r, 0xFFFFE27A, 0xFFFFB627, Shader.TileMode.CLAMP));
                    canvas.drawCircle(cx, cy, r, paint);
                    paint.setShader(null);
                } else {
                    Path moon = new Path();
                    moon.addCircle(cx, cy, r, Path.Direction.CW);
                    Path bite = new Path();
                    bite.addCircle(cx + r * 0.55f, cy - r * 0.35f, r * 0.85f, Path.Direction.CW);
                    moon.op(bite, Path.Op.DIFFERENCE);
                    paint.setColor(0xFFFFF2B8);
                    canvas.drawPath(moon, paint);
                }
            }
            if (!clouds) {
                return;
            }
            // A cloud along the bottom.
            paint.setColor(condition == Weather.STORM || condition == Weather.RAIN ? 0xFFE6ECF5 : 0xFFFFFFFF);
            float base = condition == Weather.CLOUDY || condition == Weather.PARTLY || condition == Weather.FOG ? s * 0.78f : s * 0.62f;
            canvas.drawCircle(s * 0.34f, base - s * 0.14f, s * 0.15f, paint);
            canvas.drawCircle(s * 0.52f, base - s * 0.2f, s * 0.2f, paint);
            canvas.drawCircle(s * 0.70f, base - s * 0.1f, s * 0.12f, paint);
            canvas.drawRoundRect(new RectF(s * 0.14f, base - s * 0.16f, s * 0.86f, base), s * 0.08f, s * 0.08f, paint);
            paint.setStrokeCap(Paint.Cap.ROUND);
            if (condition == Weather.RAIN || condition == Weather.DRIZZLE) {
                paint.setColor(0xFFBFE3FF);
                paint.setStrokeWidth(s * 0.06f);
                for (int i = 0; i < 3; i++) {
                    float x = s * (0.32f + 0.18f * i);
                    canvas.drawLine(x, base + s * 0.08f, x - s * 0.05f, base + s * 0.2f, paint);
                }
            } else if (condition == Weather.SNOW) {
                paint.setColor(0xFFFFFFFF);
                for (int i = 0; i < 3; i++) {
                    canvas.drawCircle(s * (0.32f + 0.18f * i), base + s * 0.13f, s * 0.045f, paint);
                }
            } else if (condition == Weather.STORM) {
                Path bolt = new Path();
                bolt.moveTo(s * 0.52f, base + s * 0.02f);
                bolt.lineTo(s * 0.40f, base + s * 0.18f);
                bolt.lineTo(s * 0.50f, base + s * 0.18f);
                bolt.lineTo(s * 0.44f, base + s * 0.32f);
                bolt.lineTo(s * 0.62f, base + s * 0.12f);
                bolt.lineTo(s * 0.52f, base + s * 0.12f);
                bolt.close();
                paint.setColor(0xFFFFD54A);
                canvas.drawPath(bolt, paint);
            } else if (condition == Weather.FOG) {
                paint.setColor(0xCCFFFFFF);
                paint.setStrokeWidth(s * 0.05f);
                canvas.drawLine(s * 0.2f, base + s * 0.1f, s * 0.8f, base + s * 0.1f, paint);
                canvas.drawLine(s * 0.3f, base + s * 0.2f, s * 0.7f, base + s * 0.2f, paint);
            }
        }
    }
}
