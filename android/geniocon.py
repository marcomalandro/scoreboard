#!/usr/bin/env python3
"""Generate launcher icons for the Scoreboard app.

Concept: rounded-square icon split blue/red like the app, with a big
white "0-0" score centered over a gold scoreboard divider. Legible at
every launcher density.
"""

from PIL import Image, ImageDraw, ImageFont, ImageFilter
import os

BLUE = (30, 64, 175, 255)      # #1e40af
RED = (185, 28, 28, 255)       # #b91c1c
GOLD = (251, 191, 36, 255)     # #fbbf24
WHITE = (241, 245, 249, 255)   # #f1f5f9
NAVY = (15, 23, 42, 255)       # #0f172a

# Density → pixel size for square launcher icon
DENSITIES = {
    "mdpi":    48,
    "hdpi":    72,
    "xhdpi":   96,
    "xxhdpi":  144,
    "xxxhdpi": 192,
}
# Adaptive icon foreground / background (108dp; xxxhdpi = 432px, safe zone = 66dp = 264px)
ADAPTIVE_SIZE = 432


def find_font(size, bold=True):
    candidates = [
        "/system/fonts/Roboto-Black.ttf",
        "/system/fonts/Roboto-Bold.ttf",
        "/system/fonts/Roboto-Regular.ttf",
        "/data/data/com.termux/files/usr/share/fonts/TTF/DejaVuSans-Bold.ttf",
        "/data/data/com.termux/files/usr/share/fonts/TTF/DejaVuSans.ttf",
    ]
    for p in candidates:
        if os.path.exists(p):
            try:
                return ImageFont.truetype(p, size)
            except Exception:
                pass
    return ImageFont.load_default()


def _draw_digit(d, digit, cx, cy, font, shadow_offset):
    bbox = d.textbbox((0, 0), digit, font=font)
    tw = bbox[2] - bbox[0]
    th = bbox[3] - bbox[1]
    tx = cx - tw // 2 - bbox[0]
    ty = cy - th // 2 - bbox[1]
    d.text((tx + shadow_offset, ty + shadow_offset), digit, font=font, fill=(0, 0, 0, 170))
    d.text((tx, ty), digit, font=font, fill=WHITE)


def draw_icon(size, rounded=True, corner_ratio=0.22):
    """Scoreboard icon: blue/red split, gold divider, big white 0-0."""
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)

    if rounded:
        radius = int(size * corner_ratio)
        mask = Image.new("L", (size, size), 0)
        ImageDraw.Draw(mask).rounded_rectangle([0, 0, size - 1, size - 1], radius=radius, fill=255)
    else:
        mask = Image.new("L", (size, size), 255)

    half = size // 2
    d.rectangle([0, 0, half, size], fill=BLUE)
    d.rectangle([half, 0, size, size], fill=RED)

    # Gold vertical divider
    bar_w = max(2, int(size * 0.035))
    d.rectangle([half - bar_w // 2, 0, half + bar_w // 2, size], fill=GOLD)

    # Dark scoreboard housing bands, top + bottom
    band = max(1, int(size * 0.07))
    darken = Image.new("RGBA", (size, band), (0, 0, 0, 110))
    img.paste(darken, (0, 0), darken)
    img.paste(darken, (0, size - band), darken)

    # Two big score digits, one per half
    font_size = int(size * 0.68)
    font = find_font(font_size, bold=True)
    shadow_offset = max(1, int(size * 0.015))
    cy = size // 2 + int(size * 0.02)
    _draw_digit(d, "0", size // 4, cy, font, shadow_offset)
    _draw_digit(d, "0", size * 3 // 4, cy, font, shadow_offset)

    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    out.paste(img, (0, 0), mask)
    return out


def draw_round_icon(size):
    """Circular version for launchers that prefer round icons."""
    img = draw_icon(size, rounded=False)
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).ellipse([0, 0, size - 1, size - 1], fill=255)
    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    out.paste(img, (0, 0), mask)
    return out


def draw_adaptive_foreground(size=ADAPTIVE_SIZE):
    """Adaptive foreground: gold divider + big 0 – 0 digits, inside the safe zone."""
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    safe = int(size * 264 / 432)
    off = (size - safe) // 2

    bar_w = max(3, int(size * 0.025))
    cx = size // 2
    d.rectangle([cx - bar_w // 2, off, cx + bar_w // 2, off + safe], fill=GOLD)

    font_size = int(safe * 0.72)
    font = find_font(font_size, bold=True)
    shadow_offset = max(1, int(size * 0.012))
    cy = size // 2
    _draw_digit(d, "0", off + safe // 4, cy, font, shadow_offset)
    _draw_digit(d, "0", off + safe * 3 // 4, cy, font, shadow_offset)
    return img


def draw_adaptive_background(size=ADAPTIVE_SIZE):
    """Adaptive background: full blue/red split; system applies the mask."""
    img = Image.new("RGBA", (size, size), NAVY)
    d = ImageDraw.Draw(img)
    half = size // 2
    d.rectangle([0, 0, half, size], fill=BLUE)
    d.rectangle([half, 0, size, size], fill=RED)
    return img


def main():
    base = "res"
    os.makedirs(base, exist_ok=True)

    # Legacy per-density launcher icons
    for density, px in DENSITIES.items():
        mip = os.path.join(base, f"mipmap-{density}")
        os.makedirs(mip, exist_ok=True)
        draw_icon(px).save(os.path.join(mip, "ic_launcher.png"), "PNG", optimize=True)
        draw_round_icon(px).save(os.path.join(mip, "ic_launcher_round.png"), "PNG", optimize=True)
        print(f"wrote {mip}/ic_launcher.png ({px}px)")

    # Adaptive icon (API 26+)
    anydpi = os.path.join(base, "mipmap-anydpi-v26")
    os.makedirs(anydpi, exist_ok=True)
    adaptive_xml = (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
        '  <background android:drawable="@mipmap/ic_launcher_background"/>\n'
        '  <foreground android:drawable="@mipmap/ic_launcher_foreground"/>\n'
        '</adaptive-icon>\n'
    )
    with open(os.path.join(anydpi, "ic_launcher.xml"), "w") as f:
        f.write(adaptive_xml)
    with open(os.path.join(anydpi, "ic_launcher_round.xml"), "w") as f:
        f.write(adaptive_xml)

    # Foreground/background at xxxhdpi
    fx = os.path.join(base, "mipmap-xxxhdpi")
    draw_adaptive_foreground().save(os.path.join(fx, "ic_launcher_foreground.png"), "PNG", optimize=True)
    draw_adaptive_background().save(os.path.join(fx, "ic_launcher_background.png"), "PNG", optimize=True)
    print("wrote adaptive foreground/background at xxxhdpi")

    # Also drop a 512px promo icon for reference
    draw_icon(512).save("ic_launcher_512.png", "PNG", optimize=True)
    print("wrote promo ic_launcher_512.png")


if __name__ == "__main__":
    main()
