package com.hala.home;

import android.content.Context;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** An app's squircle icon with its name under it. */
final class AppIconView extends LinearLayout {
    final AppInfo app;
    final ImageView icon;
    final TextView label;

    /**
     * @param onWallpaper the name is drawn over the wallpaper (white with a shadow) rather
     *                    than on a content screen
     */
    AppIconView(Context c, AppInfo app, int iconPx, boolean showLabel, boolean onWallpaper) {
        super(c);
        this.app = app;
        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER);
        setClipToPadding(false);
        setClipChildren(false);
        icon = new ImageView(c);
        icon.setImageBitmap(app.icon);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        addView(icon, new LayoutParams(iconPx, iconPx));
        label = Ui.text(c, app.label, 13, Ui.text(), false);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        label.setGravity(Gravity.CENTER);
        label.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        if (onWallpaper) {
            Ui.onWallpaper(label);
        }
        LayoutParams lp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(6);
        addView(label, lp);
        label.setVisibility(showLabel ? VISIBLE : GONE);
        setContentDescription(app.label);
        setFocusable(true);
        setClickable(true);
        setLongClickable(true);
        // Pressing shrinks the icon a little and lets go back: a tangible response under the finger.
        setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View v, android.view.MotionEvent e) {
                switch (e.getActionMasked()) {
                    case android.view.MotionEvent.ACTION_DOWN:
                        icon.animate().scaleX(0.92f).scaleY(0.92f).setDuration(120).start();
                        break;
                    case android.view.MotionEvent.ACTION_UP:
                    case android.view.MotionEvent.ACTION_CANCEL:
                        icon.animate().scaleX(1f).scaleY(1f).setDuration(160).start();
                        break;
                    default:
                        break;
                }
                return false;
            }
        });
    }
}
