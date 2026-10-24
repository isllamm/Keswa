# Keswa brand kit

The mark is a **K made of folded cloth**: a stem with a stitched seam, and two bands that fold
out of it. The two bands overlap at the crease, which is drawn darker.

## Files

| File | Use |
|---|---|
| `keswa-icon.svg` | The app icon as a flat square tile |
| `keswa-mark.svg` | The K alone, for navy or dark backgrounds |
| `keswa-mark-on-light.svg` | The K alone, for white or light backgrounds |
| `keswa-logo.svg` | Icon, **Keswa** and **كسوة**, for dark backgrounds |
| `keswa-logo-on-light.svg` | The same, for light backgrounds |
| `keswa-banner.svg` | The README banner |
| `keswa-social-preview.png` | 1280 x 640. Upload it in the repository's *Settings → Social preview* |

The app's own icons are generated from the same shapes: `composeApp/icon.icns` (macOS),
`composeApp/icon.ico` (Windows), `composeApp/src/desktopMain/resources/icon.png` (window and Dock),
and the Android launcher icons under `composeApp/src/androidMain/res` (adaptive icon with a
themed monochrome layer, plus legacy bitmaps).

## Colours

| Name | Hex | Use |
|---|---|---|
| Navy | `#1A2344` | The tile, text on light backgrounds |
| Navy top / bottom | `#27345F` / `#111833` | The tile gradient |
| Gold | `#CAA55C` | Accent, the Arabic name |
| Gold light / dark / deep | `#E8CD8F` / `#A5843F` / `#7A5F2A` | The K's stem, leg and crease |
| Ivory | `#F7F3EA` | Text on navy |

The app screens use their own palette (`core/designsystem/KeswaPalette.kt`). The brand kit does not
change it.

## Type

**Keswa** is IBM Plex Sans SemiBold and **كسوة** is IBM Plex Sans Arabic SemiBold: the fonts the app
already ships. In the SVGs they are drawn as outlines, because GitHub shows an SVG as an image and an
image cannot load fonts.

## Using the logo

- Keep clear space of at least the width of the K's stem on every side.
- Do not recolour the K, stretch it, or put the on-dark logo on a light background.
- Below 32 px use the icon, not the lockup. The stitches are left out under 128 px.

## Regenerating

Every icon and logo is generated from one definition of the mark in `build.py`. Change the shapes or
colours there and run:

```bash
python3 design/brand/build.py
```

It needs macOS, Google Chrome and Pillow. A JDK is only needed if you change the wordmark text;
`OutlineText.java` turns a font into outlines, and `outlines.json` caches the result.
