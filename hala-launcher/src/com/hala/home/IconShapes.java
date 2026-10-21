package com.hala.home;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.Drawable;

/**
 * App icons in one shape: a squircle (a superellipse, |x|^n + |y|^n = 1), the soft rounded
 * square One UI's guidelines describe. Adaptive icons fill the shape; older icons sit on a
 * light squircle tile so every icon on the screen has the same outline.
 */
final class IconShapes {
    private IconShapes() {}

    private static final float EXPONENT = 4f;

    /** The squircle filling a `size` × `size` square. */
    static Path squircle(float size) {
        Path p = new Path();
        float r = size / 2f;
        int steps = 96;
        for (int i = 0; i <= steps; i++) {
            double t = 2 * Math.PI * i / steps;
            double c = Math.cos(t);
            double s = Math.sin(t);
            float x = (float) (r + r * Math.signum(c) * Math.pow(Math.abs(c), 2 / EXPONENT));
            float y = (float) (r + r * Math.signum(s) * Math.pow(Math.abs(s), 2 / EXPONENT));
            if (i == 0) {
                p.moveTo(x, y);
            } else {
                p.lineTo(x, y);
            }
        }
        p.close();
        return p;
    }

    /** A full-bleed square drawing (Hala's own icons) cut to the squircle. */
    static Bitmap renderFull(Drawable icon, int size) {
        Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        c.clipPath(squircle(size));
        icon.setBounds(0, 0, size, size);
        icon.draw(c);
        return b;
    }

    /** `icon` drawn in the squircle, `size` pixels square. */
    static Bitmap render(Drawable icon, int size) {
        Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Path shape = squircle(size);
        c.save();
        c.clipPath(shape);
        if (icon instanceof AdaptiveIconDrawable) {
            AdaptiveIconDrawable a = (AdaptiveIconDrawable) icon;
            // Adaptive layers are 108 units with the middle 72 visible: draw them 1.5× the
            // shape, centred, and the shape cuts away the margin.
            int extra = size / 4;
            Drawable bg = a.getBackground();
            Drawable fg = a.getForeground();
            if (bg != null) {
                bg.setBounds(-extra, -extra, size + extra, size + extra);
                bg.draw(c);
            } else {
                c.drawColor(0xFFFFFFFF);
            }
            if (fg != null) {
                fg.setBounds(-extra, -extra, size + extra, size + extra);
                fg.draw(c);
            }
        } else {
            Paint tile = new Paint(Paint.ANTI_ALIAS_FLAG);
            tile.setColor(0xFFF4F4F4);
            c.drawPath(shape, tile);
            int inset = Math.round(size * 0.16f);
            icon.setBounds(inset, inset, size - inset, size - inset);
            icon.draw(c);
        }
        c.restore();
        return b;
    }
}
