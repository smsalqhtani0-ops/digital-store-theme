package com.hala.home;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** The menu that opens beside an app icon on a long press: a small rounded block of actions. */
final class AppMenu {
    private AppMenu() {}

    static final class Item {
        final String label;
        final Runnable action;

        Item(String label, Runnable action) {
            this.label = label;
            this.action = action;
        }
    }

    /**
     * Show `items` in `overlay` next to `anchor` (screen bounds of the pressed icon). A tap
     * outside closes it; `dismiss` empties the overlay.
     */
    static View show(final FrameLayout overlay, int[] anchor, String title, List<Item> items, final Runnable dismiss) {
        Context c = overlay.getContext();
        overlay.removeAllViews();
        View scrim = new View(c);
        scrim.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss.run();
            }
        });
        overlay.addView(scrim, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        LinearLayout card = new LinearLayout(c);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Ui.rounded(Ui.block(), Ui.RADIUS_TILE));
        card.setElevation(Ui.dp(8));
        card.setPadding(0, Ui.dp(8), 0, Ui.dp(8));
        TextView head = Ui.text(c, title, 14, Ui.subText(), true);
        head.setPadding(Ui.dp(20), Ui.dp(8), Ui.dp(20), Ui.dp(6));
        head.setSingleLine(true);
        card.addView(head);
        List<View> rows = new ArrayList<View>();
        for (final Item it : items) {
            TextView row = Ui.text(c, it.label, 16, Ui.text(), false);
            row.setPadding(Ui.dp(20), Ui.dp(12), Ui.dp(20), Ui.dp(12));
            row.setBackground(pressable(c));
            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dismiss.run();
                    it.action.run();
                }
            });
            card.addView(row, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            rows.add(row);
        }
        int width = Ui.dp(260);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(width, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT);
        overlay.addView(card, lp);

        // Place it above the icon when there is room, else below; centred on the icon and kept
        // inside the screen's margins.
        int[] origin = new int[2];
        overlay.getLocationOnScreen(origin);
        card.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int h = card.getMeasuredHeight();
        int margin = Ui.dp(16);
        int left = anchor[0] - origin[0] + (anchor[2] - anchor[0]) / 2 - width / 2;
        left = Math.max(margin, Math.min(overlay.getWidth() - width - margin, left));
        int top = anchor[1] - origin[1] - h - Ui.dp(8);
        if (top < margin) {
            top = anchor[3] - origin[1] + Ui.dp(8);
        }
        top = Math.max(margin, Math.min(overlay.getHeight() - h - margin, top));
        lp.leftMargin = left;
        lp.topMargin = top;
        card.setLayoutParams(lp);

        overlay.setVisibility(View.VISIBLE);
        card.setAlpha(0f);
        card.setScaleX(0.9f);
        card.setScaleY(0.9f);
        card.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(180).start();
        return card;
    }

    static android.graphics.drawable.Drawable pressable(Context c) {
        android.util.TypedValue tv = new android.util.TypedValue();
        c.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true);
        return c.getDrawable(tv.resourceId);
    }
}
