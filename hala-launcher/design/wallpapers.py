#!/usr/bin/env python3
"""Hala's wallpapers: soft, layered rounded shapes over a gradient (original artwork).

Writes design/wallpapers/<name>.svg. design/render.sh turns them into the WebP files the app
ships (res/drawable-nodpi/wall_<name>.webp).
"""
import math
import os

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "wallpapers")
SIZE = 2560


def rounded_polygon(cx, cy, r, sides, rotation, roundness):
    """A regular polygon with its corners rounded off (`roundness` 0…1 of each half side)."""
    pts = []
    for i in range(sides):
        a = math.radians(rotation + 360.0 * i / sides)
        pts.append((cx + r * math.cos(a), cy + r * math.sin(a)))
    d = []
    for i in range(sides):
        p0 = pts[i - 1]
        p1 = pts[i]
        p2 = pts[(i + 1) % sides]
        a = (p1[0] + (p0[0] - p1[0]) * roundness / 2, p1[1] + (p0[1] - p1[1]) * roundness / 2)
        b = (p1[0] + (p2[0] - p1[0]) * roundness / 2, p1[1] + (p2[1] - p1[1]) * roundness / 2)
        d.append(("M" if i == 0 else "L") + "%.1f,%.1f" % a)
        d.append("Q%.1f,%.1f %.1f,%.1f" % (p1[0], p1[1], b[0], b[1]))
    return " ".join(d) + " Z"


WALLPAPERS = {
    # Warm peach to coral.
    "peach": {"stops": ["#FFD9C7", "#F9A9B4", "#F0839A", "#EC6A62"], "shape": "#FFFFFF", "shadow": "#B8405A",
              "sides": 6, "alpha": 0.08},
    # Morning sky: light blue to lilac.
    "sky": {"stops": ["#CBE7FF", "#9EC3FF", "#8F9CFB", "#A07FEE"], "shape": "#FFFFFF", "shadow": "#3C4FB0",
            "sides": 5, "alpha": 0.08},
    # Night: deep navy to violet, for the dark theme.
    "night": {"stops": ["#1B2140", "#262B5C", "#3B2E6E", "#5B3A7E"], "shape": "#A9B4FF", "shadow": "#05060F",
              "sides": 6, "alpha": 0.06},
    # Mint: fresh green to teal.
    "mint": {"stops": ["#D4F7E8", "#9DE6CB", "#6FCDBC", "#3FAFB3"], "shape": "#FFFFFF", "shadow": "#1F7F7A",
             "sides": 7, "alpha": 0.08},
}


def svg(spec):
    s = SIZE
    stops = "".join('<stop offset="%.2f" stop-color="%s"/>' % (i / (len(spec["stops"]) - 1), c)
                    for i, c in enumerate(spec["stops"]))
    layers = []
    cx, cy = s * 0.60, s * 0.46
    for i, k in enumerate([1.05, 0.86, 0.68, 0.51, 0.35]):
        d = rounded_polygon(cx, cy, s * 0.62 * k, spec["sides"], -90 + i * 9, 0.85)
        layers.append('<path d="%s" fill="%s" fill-opacity="%.2f" filter="url(#lift)"/>'
                      % (d, spec["shape"], spec["alpha"] + i * 0.01))
    return """<svg xmlns="http://www.w3.org/2000/svg" width="{s}" height="{s}" viewBox="0 0 {s} {s}">
<defs>
  <linearGradient id="bg" x1="0" y1="0" x2="1" y2="1">{stops}</linearGradient>
  <radialGradient id="glow" cx="0.25" cy="0.15" r="0.7">
    <stop offset="0" stop-color="#FFFFFF" stop-opacity="0.28"/>
    <stop offset="1" stop-color="#FFFFFF" stop-opacity="0"/>
  </radialGradient>
  <filter id="lift" x="-20%" y="-20%" width="140%" height="140%">
    <feDropShadow dx="0" dy="{dy}" stdDeviation="{blur}" flood-color="{shadow}" flood-opacity="0.18"/>
  </filter>
</defs>
<rect width="{s}" height="{s}" fill="url(#bg)"/>
<rect width="{s}" height="{s}" fill="url(#glow)"/>
{layers}
</svg>
""".format(s=s, stops=stops, dy=s * 0.006, blur=s * 0.018, shadow=spec["shadow"], layers="\n".join(layers))


def main():
    os.makedirs(OUT, exist_ok=True)
    for name, spec in WALLPAPERS.items():
        with open(os.path.join(OUT, name + ".svg"), "w") as fh:
            fh.write(svg(spec))
    print("wrote %d wallpapers" % len(WALLPAPERS))


if __name__ == "__main__":
    main()
