#!/usr/bin/env python3
"""Hala's own icons for the device's built-in apps, in the flat, colourful style of One UI.

Every icon is drawn here from simple shapes on a 100-unit square (the launcher cuts it to the
squircle). The script writes each one twice: an SVG to preview in a browser and an Android
VectorDrawable for the app. Glyphs marked "Material" use paths from Google's Material Icons
(Apache License 2.0); the rest are original.

Usage: python3 design/icons.py   (from the hala-launcher directory)
"""
import math
import os

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
SVG_DIR = os.path.join(HERE, "icons")
RES_DIR = os.path.join(ROOT, "res", "drawable")

WHITE = "#FFFFFF"


# ---- Path helpers (all in the 100-unit square) ----

def f(v):
    return ("%.2f" % v).rstrip("0").rstrip(".")


def circle(cx, cy, r):
    return "M%s,%s a%s,%s 0 1,0 %s,0 a%s,%s 0 1,0 %s,0 Z" % (
        f(cx - r), f(cy), f(r), f(r), f(2 * r), f(r), f(r), f(-2 * r))


def ellipse(cx, cy, rx, ry):
    return "M%s,%s a%s,%s 0 1,0 %s,0 a%s,%s 0 1,0 %s,0 Z" % (
        f(cx - rx), f(cy), f(rx), f(ry), f(2 * rx), f(rx), f(ry), f(-2 * rx))


def rrect(x, y, w, h, r):
    r = min(r, w / 2, h / 2)
    return ("M%s,%s H%s a%s,%s 0 0,1 %s,%s V%s a%s,%s 0 0,1 %s,%s H%s a%s,%s 0 0,1 %s,%s V%s "
            "a%s,%s 0 0,1 %s,%s Z") % (
        f(x + r), f(y), f(x + w - r), f(r), f(r), f(r), f(r), f(y + h - r), f(r), f(r), f(-r), f(r),
        f(x + r), f(r), f(r), f(-r), f(-r), f(y + r), f(r), f(r), f(r), f(-r))


def polygon(points):
    return "M" + " L".join("%s,%s" % (f(x), f(y)) for x, y in points) + " Z"


def polar(cx, cy, r, deg):
    a = math.radians(deg)
    return cx + r * math.cos(a), cy + r * math.sin(a)


def gear(cx, cy, outer, root, teeth):
    pts = []
    step = 360.0 / teeth
    for i in range(teeth):
        a = i * step - 90
        pts += [polar(cx, cy, root, a - step * 0.30), polar(cx, cy, outer, a - step * 0.17),
                polar(cx, cy, outer, a + step * 0.17), polar(cx, cy, root, a + step * 0.30)]
    return polygon(pts)


# ---- Shapes ----

def fill(d, color=WHITE, alpha=1.0, evenodd=False, transform=None):
    return {"d": d, "fill": color, "alpha": alpha, "evenodd": evenodd, "transform": transform}


def stroke(d, color=WHITE, width=5.0, transform=None):
    return {"d": d, "stroke": color, "width": width, "transform": transform}


def material(d, color=WHITE, scale=2.2, dx=0.0, dy=0.0, evenodd=False):
    """A 24-unit Material glyph, scaled and centred in the square."""
    off = 50 - 12 * scale
    return fill(d, color, evenodd=evenodd, transform=("scale", scale, off + dx, off + dy))


def icon(top, bottom, shapes):
    return {"top": top, "bottom": bottom, "shapes": shapes}


# Material Icons paths (Apache License 2.0).
M_CALL = ("M20.01 15.38c-1.23 0-2.42-.2-3.53-.56-.35-.12-.74-.03-1.01.24l-1.57 1.97c-2.83-1.35-5.48-3.9"
          "-6.89-6.83l1.95-1.66c.27-.28.35-.67.24-1.02-.37-1.11-.56-2.3-.56-3.53 0-.54-.45-.99-.99-.99H4.19"
          "C3.65 3 3 3.24 3 3.99 3 13.28 10.73 21 20.01 21c.71 0 .99-.63.99-1.18v-3.45c0-.54-.45-.99-.99-.99z")
M_PERSON = ("M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 0-8 1.34-8 4v2h16v-2"
            "c0-2.66-5.33-4-8-4z")
M_FOLDER = "M10 4H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2h-8l-2-2z"
M_MAIL = ("M20 4H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V6c0-1.1-.9-2-2-2zm0 4l-8 5-8-5V6"
          "l8 5 8-5v2z")
M_NOTE = "M12 3v10.55c-.59-.34-1.27-.55-2-.55-2.21 0-4 1.79-4 4s1.79 4 4 4 4-1.79 4-4V7h4V3h-6z"
M_MIC = ("M12 14c1.66 0 2.99-1.34 2.99-3L15 5c0-1.66-1.34-3-3-3S9 3.34 9 5v6c0 1.66 1.34 3 3 3zm5.3-3"
         "c0 3-2.54 5.1-5.3 5.1S6.7 14 6.7 11H5c0 3.41 2.72 6.23 6 6.72V21h2v-3.28c3.28-.48 6-3.3 6-6.72h-1.7z")
M_HEART = ("M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09"
           "C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z")
M_SHIELD = "M12 1L3 5v6c0 5.55 3.84 10.74 9 12 5.16-1.26 9-6.45 9-12V5l-9-4z"


def cloud(x, y, s, color=WHITE):
    """A cloud whose base is centred at (x, y), `s` = 1 for a 48-unit-wide cloud."""
    return [
        fill(circle(x - 10 * s, y - 8 * s, 10 * s), color),
        fill(circle(x + 3 * s, y - 13 * s, 13 * s), color),
        fill(circle(x + 14 * s, y - 6 * s, 8 * s), color),
        fill(rrect(x - 22 * s, y - 10 * s, 44 * s, 12 * s, 6 * s), color),
    ]


ICONS = {
    # A camera body with a lens.
    "camera": icon("#FF6A7D", "#E93A55", [
        fill(rrect(22, 32, 56, 40, 12) + " " + rrect(38, 25, 24, 14, 5)),
        fill(circle(50, 52, 13.5), "#EF4D63"),
        fill(circle(50, 52, 7.5)),
        fill(circle(68, 40, 3), "#EF4D63"),
    ]),
    # Four overlapping petals.
    "gallery": icon("#FF6FA0", "#E0418A", [
        fill(" ".join(ellipse(50, 33, 10, 15) for _ in range(1)), transform=("rotate", a, 50, 50))
        for a in (0, 72, 144, 216, 288)
    ] + [fill(circle(50, 50, 7.5), "#EE5594")]),
    "settings": icon("#8A93A6", "#5F687B", [
        fill(gear(50, 50, 27, 21, 8) + " " + circle(50, 50, 9.5), evenodd=True),
    ]),
    "phone": icon("#45DB8A", "#1FAE62", [material(M_CALL, scale=2.55)]),
    # A speech bubble with three dots.
    "messages": icon("#5AA6FF", "#2F6CF0", [
        fill(rrect(23, 27, 54, 40, 18) + " " + polygon([(33, 60), (29, 76), (47, 64)])),
        fill(circle(38, 47, 3.6), "#3F86F6"),
        fill(circle(50, 47, 3.6), "#3F86F6"),
        fill(circle(62, 47, 3.6), "#3F86F6"),
    ]),
    "contacts": icon("#FFA24A", "#F2751E", [material(M_PERSON, scale=2.75, dy=1)]),
    "clock": icon("#3A4152", "#20242E", [
        fill(circle(50, 50, 27)),
        stroke("M50,50 L50,33", "#20242E", 5.2),
        stroke("M50,50 L62,57", "#20242E", 4.2),
        fill(circle(50, 50, 4), "#FF7A45"),
    ]),
    "calculator": icon("#4FD58A", "#22A65D", [
        stroke("M36,28 V44 M28,36 H44", width=5.2),
        stroke("M56,36 H72", width=5.2),
        stroke("M30.5,58.5 L41.5,69.5 M41.5,58.5 L30.5,69.5", width=5.2),
        stroke("M56,60 H72 M56,68 H72", width=5.2),
    ]),
    "files": icon("#FFD25A", "#F5A81C", [material(M_FOLDER, scale=2.5, dy=1)]),
    "email": icon("#FF7C66", "#EB4A3A", [material(M_MAIL, scale=2.5)]),
    # A planet with a ring.
    "browser": icon("#A27BFF", "#6E46EE", [
        fill(circle(50, 50, 17)),
        stroke(ellipse(50, 50, 28, 9.5), width=4.2, transform=("rotate", -22, 50, 50)),
    ]),
    "music": icon("#3FD0E6", "#169FC2", [material(M_NOTE, scale=2.75, dx=-1)]),
    # A note pad with ruled lines.
    "notes": icon("#FFFFFF", "#EFEFEF", [
        fill(rrect(25, 20, 50, 60, 9), "#FFB23F"),
        fill(rrect(29, 30, 42, 46, 5)),
        stroke("M36,42 H64 M36,52 H64 M36,62 H54", "#FFB23F", 4.5),
    ]),
    "recorder": icon("#FF6F6F", "#E43F5A", [material(M_MIC, scale=2.6)]),
    "weather": icon("#59B7FF", "#2E7BEF", [fill(circle(60, 40, 14), "#FFD54A")] + cloud(47, 70, 1.2)),
    # A palette.
    "themes": icon("#C077FF", "#8B45F0", [
        fill(circle(50, 50, 26) + " " + circle(61, 62, 6.5), evenodd=True),
        fill(circle(39, 42, 4.5), "#FF6A7D"),
        fill(circle(50, 35, 4.5), "#FFC23D"),
        fill(circle(62, 41, 4.5), "#45DB8A"),
        fill(circle(38, 56, 4.5), "#5AA6FF"),
    ]),
    "health": icon("#FFFFFF", "#EFEFEF", [
        material(M_HEART, "#22C79A", scale=2.6, dy=1),
        stroke("M29,51 H41 L45.5,42 L52,60 L56.5,51 H71", WHITE, 4),
    ]),
    "care": icon("#3CC9D6", "#1592C7", [
        material(M_SHIELD, scale=2.45, dy=1),
        stroke("M40,51 L47.5,58.5 L61,44.5", "#1EA6CC", 5.5),
    ]),
    "video": icon("#6E7BFF", "#4450E6", [
        fill(rrect(22, 30, 56, 40, 12) + " " + polygon([(45, 40), (45, 60), (61, 50)]), evenodd=True),
    ]),
    "maps": icon("#3FD08F", "#16A974", [
        fill("M50,22 C38.4,22 30,30.6 30,41.6 C30,56 50,78 50,78 C50,78 70,56 70,41.6 C70,30.6 61.6,22 50,22 Z "
             + circle(50, 41, 8), evenodd=True),
    ]),
    "downloads": icon("#5AA6FF", "#2F6CF0", [
        stroke("M50,26 V60 M36,47 L50,61 L64,47", width=6),
        stroke("M30,72 H70", width=6),
    ]),
}


# ---- Glyphs for the quick panel and widgets: one colour, tinted in the app, 24-unit grid ----

def g_fill(d, evenodd=False):
    return {"d": d, "fill": WHITE, "alpha": 1.0, "evenodd": evenodd, "transform": None}


def g_stroke(d, width=2.0):
    return {"d": d, "stroke": WHITE, "width": width, "transform": None}


def g_circle(cx, cy, r):
    return circle(cx, cy, r)


def sun_rays(cx, cy, r1, r2, n=8):
    parts = []
    for i in range(n):
        x1, y1 = polar(cx, cy, r1, i * 360.0 / n)
        x2, y2 = polar(cx, cy, r2, i * 360.0 / n)
        parts.append("M%s,%s L%s,%s" % (f(x1), f(y1), f(x2), f(y2)))
    return " ".join(parts)


GLYPHS = {
    # Material Icons (Apache License 2.0).
    "wifi": [g_fill("M1 9l2 2c4.97-4.97 13.03-4.97 18 0l2-2C16.93 2.93 7.08 2.93 1 9zm8 8l3 3 3-3c-1.65-1.66"
                    "-4.34-1.66-6 0zm-4-4l2 2c2.76-2.76 7.24-2.76 10 0l2-2C15.14 9.14 8.87 9.14 5 13z")],
    "bluetooth": [g_fill("M17.71 7.71L12 2h-1v7.59L6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 11 14.41V22h1l5.71"
                         "-5.71-4.3-4.29 4.3-4.29zM13 5.83l1.88 1.88L13 9.59V5.83zm1.88 10.46L13 18.17v-3.76"
                         "l1.88 1.88z")],
    "flashlight": [g_fill("M6 2h12v3H6zM6 7v1l2 3v11h8V11l2-3V7H6zm6 8.5c-.83 0-1.5-.67-1.5-1.5s.67-1.5 1.5"
                          "-1.5 1.5.67 1.5 1.5-.67 1.5-1.5 1.5z")],
    "dnd": [g_fill("M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm5 11H7v-2h10v2z")],
    "location": [g_fill("M12 2C8.13 2 5 5.13 5 9c0 5.25 7 13 7 13s7-7.75 7-13c0-3.87-3.13-7-7-7zm0 9.5c-1.38"
                        " 0-2.5-1.12-2.5-2.5s1.12-2.5 2.5-2.5 2.5 1.12 2.5 2.5-1.12 2.5-2.5 2.5z")],
    "airplane": [g_fill("M21 16v-2l-8-5V3.5c0-.83-.67-1.5-1.5-1.5S10 2.67 10 3.5V9l-8 5v2l8-2.5V19l-2 1.5V22"
                        "l3.5-1 3.5 1v-1.5L13 19v-5.5l8 2.5z")],
    "volume": [g_fill("M3 9v6h4l5 5V4L7 9H3zm13.5 3c0-1.77-1.02-3.29-2.5-4.03v8.05c1.48-.73 2.5-2.25 2.5-4.02z"
                      "M14 3.23v2.06c2.89.86 5 3.54 5 6.71s-2.11 5.85-5 6.71v2.06c4.01-.91 7-4.49 7-8.77s-2.99"
                      "-7.86-7-8.77z")],
    "bell": [g_fill("M12 22c1.1 0 2-.9 2-2h-4c0 1.1.89 2 2 2zm6-6v-5c0-3.07-1.64-5.64-4.5-6.32V4c0-.83-.67-1.5"
                    "-1.5-1.5s-1.5.67-1.5 1.5v.68C7.63 5.36 6 7.92 6 11v5l-2 2v1h16v-1l-2-2z")],
    "moon": [g_fill("M12 3c-4.97 0-9 4.03-9 9s4.03 9 9 9 9-4.03 9-9c0-.46-.04-.92-.1-1.36-.98 1.37-2.58 2.26"
                    "-4.4 2.26-2.98 0-5.4-2.42-5.4-5.4 0-1.81.89-3.42 2.26-4.4-.44-.06-.9-.1-1.36-.1z")],
    "home": [g_fill("M10 20v-6h4v6h5v-8h3L12 3 2 12h3v8z")],
    "wallpaper": [g_fill("M21 19V5c0-1.1-.9-2-2-2H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2z"
                         "M8.5 13.5l2.5 3.01L14.5 12l4.5 6H5l3.5-4.5z")],
    # Original.
    "gear": [g_fill(gear(12, 12, 10, 7.8, 8) + " " + circle(12, 12, 3.4), evenodd=True)],
    "brightness": [g_fill(circle(12, 12, 4.6)), g_stroke(sun_rays(12, 12, 7.6, 10.2), 2.0)],
    "rotate": [g_stroke("M5.5,8.5 A7.5,7.5 0 0,1 18.8,9.2", 2.0), g_fill(polygon([(20.8, 5.6), (20.4, 11.4), (15.4, 9.6)])),
               g_stroke("M18.5,15.5 A7.5,7.5 0 0,1 5.2,14.8", 2.0), g_fill(polygon([(3.2, 18.4), (3.6, 12.6), (8.6, 14.4)]))],
}


def glyph_svg(shapes):
    out = ['<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="24" height="24">']
    for sh in shapes:
        if "stroke" in sh:
            out.append('<path d="%s" fill="none" stroke="#222" stroke-width="%s" stroke-linecap="round"/>'
                       % (sh["d"], f(sh["width"])))
        else:
            rule = ' fill-rule="evenodd"' if sh["evenodd"] else ""
            out.append('<path d="%s" fill="#222"%s/>' % (sh["d"], rule))
    out.append("</svg>")
    return "\n".join(out) + "\n"


def glyph_vector(shapes):
    out = ['<?xml version="1.0" encoding="utf-8"?>',
           '<!-- Generated by design/icons.py: edit that script, not this file. -->',
           '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
           '    android:width="24dp" android:height="24dp"',
           '    android:viewportWidth="24" android:viewportHeight="24">']
    for sh in shapes:
        if "stroke" in sh:
            out.append('    <path android:pathData="%s" android:strokeColor="#FFFFFFFF" android:strokeWidth="%s"'
                       ' android:strokeLineCap="round" />' % (sh["d"], f(sh["width"])))
        else:
            extra = ' android:fillType="evenOdd"' if sh["evenodd"] else ""
            out.append('    <path android:pathData="%s" android:fillColor="#FFFFFFFF"%s />' % (sh["d"], extra))
    out.append("</vector>")
    return "\n".join(out) + "\n"


# ---- Writers ----

def svg_transform(t):
    if t is None:
        return ""
    if t[0] == "scale":
        return ' transform="translate(%s %s) scale(%s)"' % (f(t[2]), f(t[3]), f(t[1]))
    return ' transform="rotate(%s %s %s)"' % (f(t[1]), f(t[2]), f(t[3]))


def to_svg(name, ic):
    out = ['<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100" width="100" height="100">',
           '<defs><linearGradient id="bg" x1="0" y1="0" x2="1" y2="1">'
           '<stop offset="0" stop-color="%s"/><stop offset="1" stop-color="%s"/></linearGradient></defs>'
           % (ic["top"], ic["bottom"]),
           '<rect width="100" height="100" fill="url(#bg)"/>']
    for s in ic["shapes"]:
        tr = svg_transform(s.get("transform"))
        if "stroke" in s:
            out.append('<path d="%s" fill="none" stroke="%s" stroke-width="%s" stroke-linecap="round" '
                       'stroke-linejoin="round"%s/>' % (s["d"], s["stroke"], f(s["width"]), tr))
        else:
            rule = ' fill-rule="evenodd"' if s["evenodd"] else ""
            alpha = ' fill-opacity="%s"' % f(s["alpha"]) if s["alpha"] < 1 else ""
            out.append('<path d="%s" fill="%s"%s%s%s/>' % (s["d"], s["fill"], alpha, rule, tr))
    out.append("</svg>")
    return "\n".join(out) + "\n"


def vd_color(c):
    return "#FF" + c[1:].upper()


def to_vector(name, ic):
    out = ['<?xml version="1.0" encoding="utf-8"?>',
           '<!-- Generated by design/icons.py: edit that script, not this file. -->',
           '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
           '    xmlns:aapt="http://schemas.android.com/aapt"',
           '    android:width="100dp" android:height="100dp"',
           '    android:viewportWidth="100" android:viewportHeight="100">',
           '    <path android:pathData="M0,0 H100 V100 H0 Z">',
           '        <aapt:attr name="android:fillColor">',
           '            <gradient android:type="linear" android:startX="0" android:startY="0"',
           '                android:endX="100" android:endY="100"',
           '                android:startColor="%s" android:endColor="%s" />' % (vd_color(ic["top"]), vd_color(ic["bottom"])),
           '        </aapt:attr>',
           '    </path>']
    for s in ic["shapes"]:
        t = s.get("transform")
        indent = "    "
        if t is not None:
            if t[0] == "scale":
                out.append('    <group android:scaleX="%s" android:scaleY="%s" android:translateX="%s" android:translateY="%s">'
                           % (f(t[1]), f(t[1]), f(t[2]), f(t[3])))
            else:
                out.append('    <group android:rotation="%s" android:pivotX="%s" android:pivotY="%s">'
                           % (f(t[1]), f(t[2]), f(t[3])))
            indent = "        "
        if "stroke" in s:
            out.append('%s<path android:pathData="%s" android:strokeColor="%s" android:strokeWidth="%s"\n'
                       '%s    android:strokeLineCap="round" android:strokeLineJoin="round" />'
                       % (indent, s["d"], vd_color(s["stroke"]), f(s["width"]), indent))
        else:
            extra = ""
            if s["evenodd"]:
                extra += ' android:fillType="evenOdd"'
            if s["alpha"] < 1:
                extra += ' android:fillAlpha="%s"' % f(s["alpha"])
            out.append('%s<path android:pathData="%s" android:fillColor="%s"%s />'
                       % (indent, s["d"], vd_color(s["fill"]), extra))
        if t is not None:
            out.append("    </group>")
    out.append("</vector>")
    return "\n".join(out) + "\n"


def main():
    os.makedirs(SVG_DIR, exist_ok=True)
    os.makedirs(RES_DIR, exist_ok=True)
    for name, ic in ICONS.items():
        with open(os.path.join(SVG_DIR, name + ".svg"), "w") as fh:
            fh.write(to_svg(name, ic))
        with open(os.path.join(RES_DIR, "icon_" + name + ".xml"), "w") as fh:
            fh.write(to_vector(name, ic))
    glyph_dir = os.path.join(HERE, "glyphs")
    os.makedirs(glyph_dir, exist_ok=True)
    for name, shapes in GLYPHS.items():
        with open(os.path.join(glyph_dir, name + ".svg"), "w") as fh:
            fh.write(glyph_svg(shapes))
        with open(os.path.join(RES_DIR, "glyph_" + name + ".xml"), "w") as fh:
            fh.write(glyph_vector(shapes))
    print("wrote %d icons and %d glyphs" % (len(ICONS), len(GLYPHS)))


if __name__ == "__main__":
    main()
