#!/usr/bin/env python3
"""Builds every Keswa icon and logo from one definition of the mark.

The shapes and colours below are the single source: change them here and the macOS, Windows and
Android icons, the logos and the README banner all move together. Hand-editing a generated file
would be undone by the next run.

Needs macOS (`iconutil`), Google Chrome (draws the SVG), Pillow, and a JDK only when the wordmark
text changes (see OutlineText.java).

    python3 design/brand/build.py                  # rewrites the icons and logos in this repo
    python3 design/brand/build.py --out /tmp/keswa # same tree, somewhere else, to look first
"""
from __future__ import annotations

import argparse
import json
import math
import os
import shutil
import struct
import subprocess
import tempfile
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

from PIL import Image

HERE = Path(__file__).resolve().parent
REPO = HERE.parents[1]
FONTS = REPO / "core/src/commonMain/composeResources/font"
CHROME = os.environ.get("CHROME", "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome")
JAVA = str(Path(os.environ["JAVA_HOME"]) / "bin/java") if "JAVA_HOME" in os.environ else "java"

# ---- palette ---------------------------------------------------------------------------------
NAVY_TOP, NAVY, NAVY_BOTTOM = "#27345F", "#1A2344", "#111833"
GOLD_LIGHT, GOLD, GOLD_DARK, GOLD_DEEP = "#E8CD8F", "#CAA55C", "#A5843F", "#7A5F2A"
IVORY = "#F7F3EA"

ON_DARK = dict(stem=GOLD_LIGHT, arm=GOLD, leg=GOLD_DARK, fold=GOLD_DEEP, seam="#B8944A")
ON_LIGHT = dict(stem=NAVY, arm="#C29A4A", leg="#9A7A33", fold="#6B5224", seam="#C29A4A")

# ---- the mark --------------------------------------------------------------------------------
# A K made of folded cloth. The origin is the centre of its 408 x 500 box. The arm and the leg are
# two 45-degree bands whose perpendicular width equals the stem's (92), so all three strokes read
# as one weight. Where the bands overlap, the crease is drawn darker.
STEM = [(-204, -250), (-112, -250), (-112, 250), (-204, 250)]
ARM = [(-112, -66), (72, -250), (204, -250), (-112, 66)]
LEG = [(-112, 66), (72, 250), (204, 250), (-112, -66)]
FOLD = [(-112, -66), (-46, 0), (-112, 66)]
SEAM_X = -158
SEAM = [(-210 + 64 * i, -174 + 64 * i) for i in range(7)]  # seven stitches down the stem
CORNER_RADIUS = 322.6  # centre to a corner of the K; sets the Android safe-zone scale


def poly(points, fill):
    return f'<path d="M{"L".join(f"{x} {y}" for x, y in points)}Z" fill="{fill}"/>'


def mark(palette, seam=True):
    parts = [poly(STEM, palette["stem"]), poly(ARM, palette["arm"]),
             poly(LEG, palette["leg"]), poly(FOLD, palette["fold"])]
    if seam:
        stitches = "".join(f"M{SEAM_X} {a}V{b}" for a, b in SEAM)
        parts.append(f'<path d="{stitches}" fill="none" stroke="{palette["seam"]}" '
                     f'stroke-width="10" stroke-linecap="round"/>')
    return "".join(parts)


def squircle(half, cx=512, cy=512, n=5.0, steps=360):
    """Apple's icon outline is a superellipse, not a rounded rectangle: n = 5 is a close fit."""
    points = []
    for i in range(steps):
        t = 2 * math.pi * i / steps
        c, s = math.cos(t), math.sin(t)
        points.append(f"{cx + half * math.copysign(abs(c) ** (2 / n), c):.1f} "
                      f"{cy + half * math.copysign(abs(s) ** (2 / n), s):.1f}")
    return "M" + "L".join(points) + "Z"


# ---- icon tiles ------------------------------------------------------------------------------
# mac:   Apple's template, body 824 of 1024, with the soft shadow macOS icons carry
# tight: body 960, no shadow, a larger K: small sizes, Windows, the favicon
# full:  body edge to edge, for logos and the banner, which place it themselves
# round: a circle, for the legacy round launcher icon
STYLES = {
    "mac": dict(shape="squircle", half=412, k=1.00, shadow=True),
    "tight": dict(shape="squircle", half=480, k=1.12, shadow=False),
    "full": dict(shape="squircle", half=512, k=1.15, shadow=False),
    "round": dict(shape="circle", half=480, k=1.05, shadow=False),
}


def tile_defs(uid, shadow):
    shadow_def = (f'<filter id="{uid}s" x="-15%" y="-15%" width="130%" height="140%">'
                  f'<feDropShadow dx="0" dy="16" stdDeviation="22" flood-color="#000" flood-opacity=".4"/>'
                  f'</filter>') if shadow else ""
    return (f'<defs>'
            f'<linearGradient id="{uid}t" x1="0" y1="0" x2="0" y2="1">'
            f'<stop offset="0" stop-color="{NAVY_TOP}"/><stop offset="1" stop-color="{NAVY_BOTTOM}"/></linearGradient>'
            f'<linearGradient id="{uid}r" x1="0" y1="0" x2="0" y2="1">'
            f'<stop offset="0" stop-color="{GOLD_LIGHT}" stop-opacity=".38"/>'
            f'<stop offset=".45" stop-color="{GOLD_LIGHT}" stop-opacity="0"/></linearGradient>'
            f'{shadow_def}</defs>')


def tile_group(style, uid="k", seam=True):
    """The finished icon on a 1024 canvas, as SVG elements without the <svg> wrapper.

    `uid` keeps gradient and filter ids unique when several icons share one document.
    """
    s = STYLES[style]
    if s["shape"] == "circle":
        def body(attrs):
            return f'<circle cx="512" cy="512" r="{s["half"]}" {attrs}/>'
    else:
        outline_d = squircle(s["half"])

        def body(attrs):
            return f'<path d="{outline_d}" {attrs}/>'
    filt = f' filter="url(#{uid}s)"' if s["shadow"] else ""
    return (tile_defs(uid, s["shadow"])
            + body(f'fill="url(#{uid}t)"{filt}')
            + body(f'fill="none" stroke="url(#{uid}r)" stroke-width="4"')
            + f'<g transform="translate(512 512) scale({s["k"]})">{mark(ON_DARK, seam)}</g>')


def svg_doc(inner, view="0 0 1024 1024", extra=""):
    return f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="{view}"{extra}>{inner}</svg>\n'


def icon_svg(style, seam=True, uid="k"):
    return svg_doc(tile_group(style, uid, seam))


# ---- text as outlines ------------------------------------------------------------------------
# GitHub shows an SVG as an image, which cannot load fonts, so the wordmark is drawn as paths.
# outlines.json holds them; OutlineText.java makes them from the app's own IBM Plex files.
OUTLINES = HERE / "outlines.json"


def outline(key, font, text):
    """Advance, bounds and SVG path data for `text` at size 1000. Cached in outlines.json."""
    cache = json.loads(OUTLINES.read_text()) if OUTLINES.exists() else {}
    if key not in cache:
        line = subprocess.run(
            [JAVA, "-Djava.awt.headless=true", str(HERE / "OutlineText.java"), str(FONTS / font), "1000", text],
            capture_output=True, text=True, check=True).stdout.strip()
        advance, x0, y0, x1, y1, d = line.split(" ", 5)
        cache[key] = dict(advance=float(advance), x0=float(x0), y0=float(y0), x1=float(x1), y1=float(y1), d=d)
        OUTLINES.write_text(json.dumps(cache, ensure_ascii=False, indent=2, sort_keys=True) + "\n")
    return cache[key]


def latin():
    return outline("keswa", "ibm_plex_sans_semibold.ttf", "Keswa")


def arabic():
    return outline("keswa-ar", "ibm_plex_sans_arabic_semibold.ttf", "كسوة")


def tagline():
    return outline("tagline", "ibm_plex_sans_regular.ttf", "Offline-first point of sale for clothing shops")


def place(text, ink_left, baseline, size, fill):
    """Position outlined text by its ink edge rather than its advance, so margins are honest."""
    scale = size / 1000
    return (f'<path transform="translate({ink_left - text["x0"] * scale:.1f} {baseline}) scale({scale})" '
            f'd="{text["d"]}" fill="{fill}"/>')


def ink_width(text, size):
    return (text["x1"] - text["x0"]) * size / 1000


# ---- logo lockup -----------------------------------------------------------------------------
def logo_svg(on_light=False):
    word, arabic_fill, rule = (NAVY, "#9A7A33", "#C29A4A") if on_light else (IVORY, GOLD, GOLD)
    icon, latin_size, arabic_size, baseline = 240, 172, 150, 180
    x = icon + 60
    parts = [f'<g transform="scale({icon / 1024})">{tile_group("full", "lg")}</g>',
             place(latin(), x, baseline, latin_size, word)]
    x += ink_width(latin(), latin_size) + 44
    parts.append(f'<path d="M{x:.1f} 72V168" stroke="{rule}" stroke-width="3" stroke-linecap="round" opacity=".8"/>')
    x += 44
    parts.append(place(arabic(), x, baseline, arabic_size, arabic_fill))
    total = x + ink_width(arabic(), arabic_size)
    return svg_doc("".join(parts), f"0 0 {total:.0f} {icon}", f' width="{total:.0f}" height="{icon}"')


# ---- banner and social preview ---------------------------------------------------------------
def stitch(x0, x1, y, dash=26, gap=16, width=5):
    segments, x = [], x0
    while x + dash <= x1:
        segments.append(f"M{x:.0f} {y}h{dash}")
        x += dash + gap
    return f'<path d="{"".join(segments)}" stroke="{GOLD}" stroke-width="{width}" stroke-linecap="round" opacity=".9"/>'


def backdrop(w, h):
    """A deep navy ground, darker than the icon tile so the tile stands off it, with a few pleats:
    the fold of the mark, repeated as cloth. Lighter navy bands, and one thin line of gold piping."""
    def pleat(a, band, fill, opacity):
        return (f'<path d="M{a} {h + 40}L{a + h + 40} 0H{a + h + 40 + band}L{a + band} {h + 40}Z" '
                f'fill="{fill}" opacity="{opacity}"/>')
    pleats = "".join([
        pleat(w - 450, 150, "#2B3868", .55), pleat(w - 262, 9, GOLD, .85),
        pleat(w - 240, 120, "#2B3868", .38), pleat(w - 80, 190, "#2B3868", .55)])
    return (f'<defs><linearGradient id="bg" x1="0" y1="0" x2="1" y2="1">'
            f'<stop offset="0" stop-color="#151D3D"/><stop offset="1" stop-color="#080C1D"/></linearGradient>'
            f'<clipPath id="cut"><rect width="{w}" height="{h}"/></clipPath></defs>'
            f'<rect width="{w}" height="{h}" fill="url(#bg)"/><g clip-path="url(#cut)">{pleats}</g>')


def hero(w, h, icon, icon_y, text_x, latin_size, arabic_size, baseline, rule_y, arabic_baseline, line=None):
    parts = [backdrop(w, h),
             f'<g transform="translate(96 {icon_y}) scale({icon / 1024})">{tile_group("full", "hero")}</g>',
             place(latin(), text_x, baseline, latin_size, IVORY),
             stitch(text_x, w - 96, rule_y),
             place(arabic(), text_x, arabic_baseline, arabic_size, GOLD)]
    if line:
        y, size = line
        parts.append(place(tagline(), 96, y, size, "#CFC9BA"))
    return svg_doc("".join(parts), f"0 0 {w} {h}", f' width="{w}" height="{h}"')


def banner_svg():
    return hero(1280, 420, icon=270, icon_y=75, text_x=430, latin_size=200, arabic_size=118,
                baseline=200, rule_y=238, arabic_baseline=346)


def social_svg():
    return hero(1280, 640, icon=300, icon_y=88, text_x=470, latin_size=224, arabic_size=132,
                baseline=262, rule_y=304, arabic_baseline=408, line=(552, 44))


# ---- drawing ---------------------------------------------------------------------------------
def render(svg_text, png_path, w, h=None, covers=0.75):
    """Draw SVG to a transparent PNG of exactly w x h with headless Chrome.

    Two things learned the hard way. Chrome will not open a window narrower than about 500 px, so a
    96 px render lands mostly off-screen: the SVG is drawn at its target size as an <img> inside a
    big window and cropped. And every call needs its own profile, or parallel instances hand work
    to each other and return half-drawn screenshots. A blank or cropped result raises instead of
    shipping.
    """
    h = h or w
    png_path.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory() as tmp:
        (Path(tmp) / "in.svg").write_text(svg_text)
        (Path(tmp) / "page.html").write_text(
            '<!doctype html><meta charset="utf-8"><style>html,body{margin:0;background:transparent}'
            f'img{{display:block}}</style><img src="in.svg" width="{w}" height="{h}">')
        shot = Path(tmp) / "shot.png"
        subprocess.run([CHROME, "--headless=new", "--disable-gpu", "--hide-scrollbars",
                        f"--user-data-dir={tmp}/profile", "--default-background-color=00000000",
                        f"--window-size={max(w, 1024)},{max(h, 1024)}", f"--screenshot={shot}",
                        f"file://{tmp}/page.html"],
                       stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, check=True)
        with Image.open(shot) as full:
            image = full.convert("RGBA").crop((0, 0, w, h))
    box = image.getchannel("A").getbbox()
    if box is None or (box[2] - box[0]) < covers * w or (box[3] - box[1]) < covers * h:
        raise RuntimeError(f"bad render {png_path.name}: content {box} in {w}x{h}")
    image.save(png_path, optimize=True)


def render_many(jobs):
    with ThreadPoolExecutor(max_workers=4) as pool:
        list(pool.map(lambda job: render(*job), jobs))


def write(path, text):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text)


# ---- outputs ---------------------------------------------------------------------------------
def brand_files(out):
    brand = out / "design/brand"
    write(brand / "keswa-icon.svg", icon_svg("tight", True, "ic"))
    pad = 44
    view = f"{-204 - pad} {-250 - pad} {408 + 2 * pad} {500 + 2 * pad}"
    for name, palette in [("keswa-mark.svg", ON_DARK), ("keswa-mark-on-light.svg", ON_LIGHT)]:
        write(brand / name, svg_doc(mark(palette), view, f' width="{408 + 2 * pad}" height="{500 + 2 * pad}"'))
    write(brand / "keswa-logo.svg", logo_svg(False))
    write(brand / "keswa-logo-on-light.svg", logo_svg(True))
    write(brand / "keswa-banner.svg", banner_svg())
    render(social_svg(), brand / "keswa-social-preview.png", 1280, 640)


def desktop(out):
    def with_seam(px):
        return px >= 128  # the stitches only read once the icon is big enough to show them

    with tempfile.TemporaryDirectory() as tmp:
        # macOS: iconutil wants exactly these names, and the @2x of one size is the 1x of the next.
        iconset = Path(tmp) / "Keswa.iconset"
        files = {
            16: ["icon_16x16"],
            32: ["icon_16x16@2x", "icon_32x32"],
            64: ["icon_32x32@2x"],
            128: ["icon_128x128"],
            256: ["icon_128x128@2x", "icon_256x256"],
            512: ["icon_256x256@2x", "icon_512x512"],
            1024: ["icon_512x512@2x"],
        }
        render_many([(icon_svg("mac", with_seam(px), "m"), iconset / f"{names[0]}.png", px)
                     for px, names in files.items()])
        for names in files.values():
            for alias in names[1:]:
                shutil.copy(iconset / f"{names[0]}.png", iconset / f"{alias}.png")
        (out / "composeApp").mkdir(parents=True, exist_ok=True)
        subprocess.run(["iconutil", "-c", "icns", str(iconset), "-o", str(out / "composeApp/icon.icns")], check=True)

        # The window and Dock icon the app sets for itself at runtime.
        window_icon = out / "composeApp/src/desktopMain/resources/icon.png"
        render(icon_svg("mac", True, "w"), window_icon, 512)
        # An IDE leaves a copy under bin/ and the repo tracks it, so it moves with the source.
        bin_icon = out / "composeApp/bin/desktopMain/icon.png"
        bin_icon.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy(window_icon, bin_icon)

        # Windows: PNG frames inside an .ico, drawn tight so the shape fills the taskbar slot.
        sizes = [16, 24, 32, 48, 64, 128, 256]
        frames = Path(tmp) / "ico"
        render_many([(icon_svg("tight", with_seam(px), "i"), frames / f"{px}.png", px) for px in sizes])
        write_ico(out / "composeApp/icon.ico", [(px, (frames / f"{px}.png").read_bytes()) for px in sizes])


def write_ico(path, frames):
    head = struct.pack("<HHH", 0, 1, len(frames))
    offset, entries, data = 6 + 16 * len(frames), b"", b""
    for px, png in frames:
        side = 0 if px >= 256 else px  # the format stores 256 as 0
        entries += struct.pack("<BBBBHHII", side, side, 0, 0, 1, 32, len(png), offset + len(data))
        data += png
    path.write_bytes(head + entries + data)


def android(out):
    res = out / "composeApp/src/androidMain/res"

    # Bitmaps for launchers that ignore adaptive icons. minSdk is 26, so none should need them, but
    # previews and store tooling still read these files.
    densities = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
    jobs = []
    for name, px in densities.items():
        jobs.append((icon_svg("tight", px >= 144, "a"), res / f"mipmap-{name}/ic_launcher.png", px))
        jobs.append((icon_svg("round", px >= 144, "b"), res / f"mipmap-{name}/ic_launcher_round.png", px))
    render_many(jobs)

    # The adaptive icon: a 108dp canvas of which a launcher shows a 72dp circle, squircle or square.
    # The K's corners sit 30dp from the centre, well inside the 66dp the platform promises to keep.
    scale = round(30 / CORNER_RADIUS, 4)

    def path(points, color):
        data = "M" + " L".join(f"{x},{y}" for x, y in points) + " Z"
        return f'        <path android:fillColor="{color}" android:pathData="{data}"/>\n'

    note = '<!-- Generated by design/brand/build.py. The mark is defined there; edit it there. -->\n'
    vector = ('<?xml version="1.0" encoding="utf-8"?>\n' + note +
              '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
              '    android:width="108dp"\n    android:height="108dp"\n'
              '    android:viewportWidth="108"\n    android:viewportHeight="108">\n')
    group = ('    <group\n        android:translateX="54"\n        android:translateY="54"\n'
             f'        android:scaleX="{scale}"\n        android:scaleY="{scale}">\n')
    stitches = " ".join(f"M{SEAM_X},{a} L{SEAM_X},{b}" for a, b in SEAM)
    foreground = (vector + group
                  + path(STEM, GOLD_LIGHT) + path(ARM, GOLD) + path(LEG, GOLD_DARK) + path(FOLD, GOLD_DEEP)
                  + f'        <path\n            android:pathData="{stitches}"\n'
                    f'            android:strokeColor="{ON_DARK["seam"]}"\n'
                    f'            android:strokeLineCap="round"\n            android:strokeWidth="10"/>\n'
                  + '    </group>\n</vector>\n')
    # Themed icons (Android 13+) recolour a single silhouette, so the tones and stitches drop out.
    # Three separate paths, not one: the arm and the leg wind opposite ways and would cancel where
    # they overlap under a non-zero fill.
    monochrome = (vector + group + path(STEM, "#FFFFFF") + path(ARM, "#FFFFFF") + path(LEG, "#FFFFFF")
                  + '    </group>\n</vector>\n')
    background = ('<?xml version="1.0" encoding="utf-8"?>\n' + note +
                  '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
                  '    xmlns:aapt="http://schemas.android.com/aapt"\n'
                  '    android:width="108dp"\n    android:height="108dp"\n'
                  '    android:viewportWidth="108"\n    android:viewportHeight="108">\n'
                  '    <path android:pathData="M0,0h108v108h-108z">\n'
                  '        <aapt:attr name="android:fillColor">\n'
                  '            <gradient\n                android:type="linear"\n'
                  '                android:startX="54"\n                android:startY="0"\n'
                  '                android:endX="54"\n                android:endY="108">\n'
                  f'                <item android:color="{NAVY_TOP}" android:offset="0.0"/>\n'
                  f'                <item android:color="{NAVY_BOTTOM}" android:offset="1.0"/>\n'
                  '            </gradient>\n        </aapt:attr>\n    </path>\n</vector>\n')
    adaptive = ('<?xml version="1.0" encoding="utf-8"?>\n'
                '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
                '    <background android:drawable="@drawable/ic_launcher_background"/>\n'
                '    <foreground android:drawable="@drawable/ic_launcher_foreground"/>\n'
                '    <monochrome android:drawable="@drawable/ic_launcher_monochrome"/>\n'
                '</adaptive-icon>\n')
    write(res / "drawable/ic_launcher_background.xml", background)
    write(res / "drawable/ic_launcher_foreground.xml", foreground)
    write(res / "drawable/ic_launcher_monochrome.xml", monochrome)
    write(res / "mipmap-anydpi-v26/ic_launcher.xml", adaptive)
    write(res / "mipmap-anydpi-v26/ic_launcher_round.xml", adaptive)


def main():
    parser = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    parser.add_argument("--out", type=Path, default=REPO, help="root to write into (default: this repo)")
    out = parser.parse_args().out.resolve()
    brand_files(out)
    android(out)
    desktop(out)
    print(f"Wrote the brand assets under {out}")


if __name__ == "__main__":
    main()
