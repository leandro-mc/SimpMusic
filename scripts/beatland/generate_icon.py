#!/usr/bin/env python3
"""
Beatland launcher icon generator (requires Pillow + numpy).

    python3 scripts/beatland/generate_icon.py            # regenerate every asset
    python3 scripts/beatland/generate_icon.py --preview  # also write /tmp/beatland_icon_preview.png

Concept "Beamed": two beamed eighth notes whose beam turns into a sound wave that decays off to the
right ("beat" + "land": the rhythm travelling across the horizon). White notes with a soft cool
gradient, cyan-to-lime wave, on a deep navy background with a teal glow. No purple anywhere
(docs/beatland/RULES.md). Decision D-020.

Everything is built from ONE geometry (normalised 0..1 "design tile" coordinates below):
  * raster tile  (legacy launcher PNGs, desktop PNG/ICO/ICNS, in-app drawable): the tile fills the image.
  * adaptive icon (Android 8+ vectors): the design tile is mapped onto the central ~79x79 of the 108x108
    viewport (see ADAPTIVE_BOOST); the background layer fills the whole 108x108 and the
    wave keeps running into the masked-out area, so it simply "leaves" the icon.

Writes:
  androidApp/src/beatland/res/drawable-v24/ic_launcher_{background,foreground,monochrome}.xml
  androidApp/src/beatland/res/mipmap-{m,h,xh,xxh,xxxh}dpi/ic_launcher{,_round}.png   (legacy < API 26)
  composeApp/icon/beatland_app_icon.{png,ico,icns}                                   (desktop packaging)
  composeApp/src/commonMain/composeResources/drawable/beatland_app_icon.png          (in-app / window icon)
"""
from __future__ import annotations

import argparse
import math
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parents[2]
RES = ROOT / "androidApp/src/beatland/res"

# ============================================================================ design (0..1 tile) ============
BG_TOP, BG_BOTTOM = "#0E2347", "#050A18"
GLOW_COLOR, GLOW_CENTER, GLOW_RADIUS, GLOW_ALPHA = "#19C6D8", (0.60, 0.35), 0.60, 0.26
NOTE_TOP, NOTE_BOTTOM = "#FFFFFF", "#CFE7FF"
WAVE_STOPS = [(0.0, "#FFFFFF"), (0.45, "#7FF3FF"), (1.0, "#B8FF5E")]
SHADOW = "#000814"

HEADS = [  # centre x, centre y, rx, ry, rotation (deg)
    (0.285, 0.730, 0.098, 0.072, -22.0),
    (0.545, 0.665, 0.098, 0.072, -22.0),
]
STEMS = [  # x0, y0, x1, y1 (pills)
    (0.355, 0.310, 0.402, 0.735),
    (0.615, 0.245, 0.662, 0.670),
]
BEAM_X0, BEAM_KNEE, BEAM_X1 = 0.378, 0.640, 1.10
BEAM_Y0, BEAM_Y_KNEE = 0.325, 0.268
WAVE_AMP, WAVE_DECAY, WAVE_PERIOD = 0.07, 0.36, 0.34
BEAM_W0, BEAM_W1 = 0.062, 0.014
WAVE_X_GRAD = (0.35, 1.05)  # horizontal extent of the wave gradient


def beam_centerline(n: int = 520):
    xs = np.linspace(BEAM_X0, BEAM_X1, n)
    base = np.interp(xs, [BEAM_X0, BEAM_KNEE], [BEAM_Y0, BEAM_Y_KNEE])
    after = xs > BEAM_KNEE
    amp = np.where(after, WAVE_AMP * np.exp(-(xs - BEAM_KNEE) / WAVE_DECAY), 0.0)
    ys = base + amp * np.sin(2 * math.pi * (xs - BEAM_KNEE) / WAVE_PERIOD) * after
    ws = np.linspace(BEAM_W0, BEAM_W1, n)
    return xs, ys, ws


# ============================================================================ raster toolkit ================
S = 1024  # master canvas
SS = 3  # supersampling for shape masks


def hexc(h: str) -> np.ndarray:
    h = h.lstrip("#")
    return np.array([int(h[i : i + 2], 16) for i in (0, 2, 4)], np.float32)


YY, XX = np.mgrid[0:S, 0:S].astype(np.float32)
XX /= S
YY /= S


def mk() -> Image.Image:
    return Image.new("L", (S * SS, S * SS), 0)


def fin(m: Image.Image) -> np.ndarray:
    return np.asarray(m.resize((S, S), Image.LANCZOS), dtype=np.float32) / 255.0


def U(v: float) -> float:
    return v * S * SS


def m_pill(x0, y0, x1, y1) -> np.ndarray:
    m = mk()
    ImageDraw.Draw(m).rounded_rectangle((U(x0), U(y0), U(x1), U(y1)), radius=U(min(x1 - x0, y1 - y0) / 2), fill=255)
    return fin(m)


def m_ellipse_rot(cx, cy, rx, ry, deg) -> np.ndarray:
    c, s = math.cos(math.radians(deg)), math.sin(math.radians(deg))
    pts = []
    for a in np.linspace(0, 2 * math.pi, 240):
        x, y = rx * math.cos(a), ry * math.sin(a)
        pts.append((U(cx + x * c - y * s), U(cy + x * s + y * c)))
    m = mk()
    ImageDraw.Draw(m).polygon(pts, fill=255)
    return fin(m)


def m_taper(xs, ys, ws) -> np.ndarray:
    m = mk()
    d = ImageDraw.Draw(m)
    for x, y, w in zip(xs, ys, ws):
        r = U(w / 2)
        d.ellipse((U(x) - r, U(y) - r, U(x) + r, U(y) + r), fill=255)
    return fin(m)


def blur(a: np.ndarray, r: float) -> np.ndarray:
    im = Image.fromarray((np.clip(a, 0, 1) * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(r * S))
    return np.asarray(im, dtype=np.float32) / 255.0


def lin(stops, p0, p1) -> np.ndarray:
    dx, dy = p1[0] - p0[0], p1[1] - p0[1]
    t = np.clip(((XX - p0[0]) * dx + (YY - p0[1]) * dy) / (dx * dx + dy * dy + 1e-9), 0, 1)
    offs = [s[0] for s in stops]
    out = np.zeros((S, S, 3), np.float32)
    for ch in range(3):
        out[..., ch] = np.interp(t, offs, [hexc(s[1])[ch] for s in stops])
    return out


def glow(c, r, power=2.0) -> np.ndarray:
    return np.clip(1 - np.sqrt((XX - c[0]) ** 2 + (YY - c[1]) ** 2) / r, 0, 1) ** power


def paint(cv, mask, col, a=1.0) -> np.ndarray:
    col = np.asarray(col, np.float32)
    m = (mask * a)[..., None]
    if col.ndim == 1:
        col = col[None, None, :]
    return cv * (1 - m) + col * m


def drop_shadow(cv, mask, off=0.012, bl=0.014, a=0.5) -> np.ndarray:
    return paint(cv, blur(np.roll(mask, int(off * S), axis=0), bl), hexc(SHADOW), a)


def render_master() -> np.ndarray:
    """Full-bleed (unmasked) S x S x 3 float canvas of the icon."""
    cv = lin([(0, BG_TOP), (1, BG_BOTTOM)], (0, 0), (1, 1)).copy()
    cv = paint(cv, glow(GLOW_CENTER, GLOW_RADIUS, 2.0), hexc(GLOW_COLOR), GLOW_ALPHA)
    xs, ys, ws = beam_centerline()
    beam = m_taper(xs, ys, ws)
    stems = [m_pill(*s) for s in STEMS]
    heads = [m_ellipse_rot(*h) for h in HEADS]
    for m in (beam, *stems, *heads):
        cv = drop_shadow(cv, m)
    note_col = lin([(0, NOTE_TOP), (1, NOTE_BOTTOM)], (0, 0.22), (0, 0.80))
    wave_col = lin(WAVE_STOPS, (WAVE_X_GRAD[0], 0), (WAVE_X_GRAD[1], 0))
    for m in (*stems, *heads):
        cv = paint(cv, m, note_col)
    return paint(cv, beam, wave_col)


def tile_alpha(shape: str, n: float = 5.0) -> np.ndarray:
    N = S * 2
    ys, xs = np.mgrid[0:N, 0:N].astype(np.float32)
    x, y = (xs + 0.5) / N * 2 - 1, (ys + 0.5) / N * 2 - 1
    m = (x * x + y * y <= 1) if shape == "round" else (np.abs(x) ** n + np.abs(y) ** n <= 1)
    return m.astype(np.float32).reshape(S, 2, S, 2).mean(axis=(1, 3))


def masked(cv: np.ndarray, shape: str) -> Image.Image:
    rgb = np.clip(cv, 0, 255).astype(np.uint8)
    return Image.fromarray(np.dstack([rgb, (tile_alpha(shape) * 255).astype(np.uint8)]), "RGBA")


def write_rasters(cv: np.ndarray) -> None:
    square, round_ = masked(cv, "squircle"), masked(cv, "round")
    for name, px in {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}.items():
        d = RES / f"mipmap-{name}"
        d.mkdir(parents=True, exist_ok=True)
        square.resize((px, px), Image.LANCZOS).save(d / "ic_launcher.png", optimize=True)
        round_.resize((px, px), Image.LANCZOS).save(d / "ic_launcher_round.png", optimize=True)
    icon_dir = ROOT / "composeApp/icon"
    icon_dir.mkdir(parents=True, exist_ok=True)
    square.resize((512, 512), Image.LANCZOS).save(icon_dir / "beatland_app_icon.png", optimize=True)
    square.save(
        icon_dir / "beatland_app_icon.ico",
        sizes=[(16, 16), (24, 24), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)],
    )
    square.save(icon_dir / "beatland_app_icon.icns")
    res_dir = ROOT / "composeApp/src/commonMain/composeResources/drawable"
    square.resize((432, 432), Image.LANCZOS).save(res_dir / "beatland_app_icon.png", optimize=True)


# ============================================================================ Android vectors ===============
# The design tile is mapped around the viewport centre. 72 units is the part every launcher mask shows;
# the extra 10 % (ADAPTIVE_BOOST) uses the free margin inside the 66-unit safe zone (checked below).
ADAPTIVE_BOOST = 1.10
VSCALE = 72.0 * ADAPTIVE_BOOST


def vx(x: float) -> float:
    return 54.0 + (x - 0.5) * VSCALE


def vy(y: float) -> float:
    return 54.0 + (y - 0.5) * VSCALE


def vl(v: float) -> float:
    return v * VSCALE


def f(v: float) -> str:
    return f"{v:.2f}".rstrip("0").rstrip(".")


def pill_path(x0, y0, x1, y1) -> str:
    X0, Y0, X1, Y1 = vx(x0), vy(y0), vx(x1), vy(y1)
    r = (X1 - X0) / 2
    return (
        f"M{f(X0 + r)},{f(Y0)} A{f(r)},{f(r)} 0 0 1 {f(X1)},{f(Y0 + r)} V{f(Y1 - r)} "
        f"A{f(r)},{f(r)} 0 0 1 {f(X0 + r)},{f(Y1)} A{f(r)},{f(r)} 0 0 1 {f(X0)},{f(Y1 - r)} "
        f"V{f(Y0 + r)} A{f(r)},{f(r)} 0 0 1 {f(X0 + r)},{f(Y0)} Z"
    )


def ellipse_path(cx, cy, rx, ry) -> str:
    CX, CY, RX, RY = vx(cx), vy(cy), vl(rx), vl(ry)
    return (
        f"M{f(CX - RX)},{f(CY)} a{f(RX)},{f(RY)} 0 1,0 {f(2 * RX)},0 "
        f"a{f(RX)},{f(RY)} 0 1,0 {f(-2 * RX)},0 Z"
    )


def beam_outline(step: int = 4):
    xs, ys, ws = beam_centerline()
    idx = list(range(0, len(xs), step))
    if idx[-1] != len(xs) - 1:
        idx.append(len(xs) - 1)
    left, right = [], []
    for k, i in enumerate(idx):
        j0, j1 = max(0, i - 1), min(len(xs) - 1, i + 1)
        tx, ty = xs[j1] - xs[j0], ys[j1] - ys[j0]
        norm = math.hypot(tx, ty) or 1.0
        nx, ny = -ty / norm, tx / norm
        left.append((vx(xs[i] + nx * ws[i] / 2), vy(ys[i] + ny * ws[i] / 2)))
        right.append((vx(xs[i] - nx * ws[i] / 2), vy(ys[i] - ny * ws[i] / 2)))
    pts = left + right[::-1]
    d = "M" + " L".join(f"{f(x)},{f(y)}" for x, y in pts) + " Z"
    # round start cap
    r0 = vl(BEAM_W0) / 2
    cx, cy = vx(xs[0]), vy(ys[0])
    cap = f"M{f(cx - r0)},{f(cy)} a{f(r0)},{f(r0)} 0 1,0 {f(2 * r0)},0 a{f(r0)},{f(r0)} 0 1,0 {f(-2 * r0)},0 Z"
    return d + " " + cap, pts


def gradient_xml(kind: str, **kw) -> str:
    if kind == "vertical":
        y0, y1 = kw["y"]
        stops = kw["stops"]
        items = "\n".join(
            f'                <item android:color="{c}" android:offset="{f(o)}" />' for o, c in stops
        )
        return f'''        <aapt:attr name="android:fillColor">
            <gradient
                android:startX="0"
                android:startY="{f(y0)}"
                android:endX="0"
                android:endY="{f(y1)}"
                android:type="linear">
{items}
            </gradient>
        </aapt:attr>'''
    x0, x1 = kw["x"]
    items = "\n".join(
        f'                <item android:color="{c}" android:offset="{f(o)}" />' for o, c in kw["stops"]
    )
    return f'''        <aapt:attr name="android:fillColor">
            <gradient
                android:startX="{f(x0)}"
                android:startY="0"
                android:endX="{f(x1)}"
                android:endY="0"
                android:type="linear">
{items}
            </gradient>
        </aapt:attr>'''


HEADER = '''<?xml version="1.0" encoding="utf-8"?>
<!-- {title}. Generated by scripts/beatland/generate_icon.py (do not edit by hand). -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
'''


def write_vectors() -> None:
    d = RES / "drawable-v24"
    d.mkdir(parents=True, exist_ok=True)

    # ---- background: navy diagonal gradient + soft teal glow
    gx, gy, gr = vx(GLOW_CENTER[0]), vy(GLOW_CENTER[1]), vl(GLOW_RADIUS)
    (d / "ic_launcher_background.xml").write_text(
        HEADER.format(title="Beatland launcher icon background")
        + f'''    <path android:pathData="M0,0h108v108h-108z">
        <aapt:attr name="android:fillColor">
            <gradient
                android:startX="0"
                android:startY="0"
                android:endX="108"
                android:endY="108"
                android:type="linear">
                <item android:color="{BG_TOP}" android:offset="0" />
                <item android:color="{BG_BOTTOM}" android:offset="1" />
            </gradient>
        </aapt:attr>
    </path>
    <path android:pathData="M0,0h108v108h-108z">
        <aapt:attr name="android:fillColor">
            <gradient
                android:centerX="{f(gx)}"
                android:centerY="{f(gy)}"
                android:gradientRadius="{f(gr)}"
                android:type="radial">
                <item android:color="#{int(GLOW_ALPHA * 255):02X}{GLOW_COLOR.lstrip('#')}" android:offset="0" />
                <item android:color="#00{GLOW_COLOR.lstrip('#')}" android:offset="1" />
            </gradient>
        </aapt:attr>
    </path>
</vector>
''',
        encoding="utf-8",
        newline="\n",
    )

    # ---- foreground: notes (vertical white gradient) + beam/wave (horizontal gradient)
    note_grad = gradient_xml(
        "vertical", y=(vy(0.22), vy(0.80)), stops=[(0, NOTE_TOP), (1, NOTE_BOTTOM)]
    )
    wave_grad = gradient_xml(
        "horizontal", x=(vx(WAVE_X_GRAD[0]), vx(WAVE_X_GRAD[1])), stops=WAVE_STOPS
    )
    beam_d, _ = beam_outline()
    parts = []
    for s in STEMS:
        parts.append(f'    <path android:pathData="{pill_path(*s)}">\n{note_grad}\n    </path>\n')
    for cx, cy, rx, ry, deg in HEADS:
        parts.append(
            f'    <group android:pivotX="{f(vx(cx))}" android:pivotY="{f(vy(cy))}" android:rotation="{f(deg)}">\n'
            f'        <path android:pathData="{ellipse_path(cx, cy, rx, ry)}">\n{note_grad}\n        </path>\n'
            f"    </group>\n"
        )
    parts.append(f'    <path android:pathData="{beam_d}">\n{wave_grad}\n    </path>\n')
    (d / "ic_launcher_foreground.xml").write_text(
        HEADER.format(title="Beatland launcher icon foreground: beamed notes with a sound-wave beam")
        + "".join(parts)
        + "</vector>\n",
        encoding="utf-8",
        newline="\n",
    )

    # ---- monochrome (themed icons): same shapes, single colour
    mono = []
    for s in STEMS:
        mono.append(f'    <path\n        android:fillColor="#FFFFFF"\n        android:pathData="{pill_path(*s)}" />\n')
    for cx, cy, rx, ry, deg in HEADS:
        mono.append(
            f'    <group android:pivotX="{f(vx(cx))}" android:pivotY="{f(vy(cy))}" android:rotation="{f(deg)}">\n'
            f'        <path\n            android:fillColor="#FFFFFF"\n            android:pathData="{ellipse_path(cx, cy, rx, ry)}" />\n'
            f"    </group>\n"
        )
    mono.append(f'    <path\n        android:fillColor="#FFFFFF"\n        android:pathData="{beam_d}" />\n')
    (d / "ic_launcher_monochrome.xml").write_text(
        HEADER.format(title="Beatland themed (monochrome) launcher icon")
        .replace('    xmlns:aapt="http://schemas.android.com/aapt"\n', "")
        + "".join(mono)
        + "</vector>\n",
        encoding="utf-8",
        newline="\n",
    )


# ============================================================================ verification preview ==========
def write_preview(cv: np.ndarray) -> None:
    """Contact sheet: squircle / circle masks, small sizes, and the adaptive-icon safe-zone check."""
    sq, ci = masked(cv, "squircle"), masked(cv, "round")
    sheet = Image.new("RGBA", (980, 330), (30, 32, 38, 255))
    sheet.paste(sq.resize((300, 300), Image.LANCZOS), (15, 15), sq.resize((300, 300), Image.LANCZOS))
    x = 335
    for px in (120, 80, 56, 40, 28):
        t = ci.resize((px, px), Image.LANCZOS)
        sheet.paste(t, (x, 15), t)
        x += px + 12
    x = 335
    for px in (120, 80, 56, 40, 28):
        t = sq.resize((px, px), Image.LANCZOS)
        sheet.paste(t, (x, 170), t)
        x += px + 12
    sheet.save("/tmp/beatland_icon_preview.png")


def check_safe_zone() -> None:
    """Report the farthest solid-note point from the adaptive-icon centre (safe zone radius = 33)."""
    worst = 0.0
    for cx, cy, rx, ry, deg in HEADS:
        for a in np.linspace(0, 2 * math.pi, 360):
            c, s = math.cos(math.radians(deg)), math.sin(math.radians(deg))
            x = cx + rx * math.cos(a) * c - ry * math.sin(a) * s
            y = cy + rx * math.cos(a) * s + ry * math.sin(a) * c
            worst = max(worst, math.hypot(vx(x) - 54, vy(y) - 54))
    for x0, y0, x1, y1 in STEMS:
        for x, y in ((x0, y0), (x1, y0), (x0, y1), (x1, y1)):
            worst = max(worst, math.hypot(vx(x) - 54, vy(y) - 54))
    print(f"notes: farthest point {worst:.1f} from centre (safe zone radius 33, circle mask radius 36)")


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--preview", action="store_true")
    args = ap.parse_args()
    cv = render_master()
    write_vectors()
    write_rasters(cv)
    check_safe_zone()
    if args.preview:
        write_preview(cv)
        print("preview: /tmp/beatland_icon_preview.png")
    print("icon assets written")


if __name__ == "__main__":
    main()
