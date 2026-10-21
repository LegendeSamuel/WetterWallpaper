"""Zerlegt ein Foto per Tiefenschätzung in background.png + foreground.png fürs Handy.

python tools/split_layers.py BILD [--center 0.5] [--threshold 0.25] [--soft 0.04]
  --center     horizontale Bildmitte des Hochformat-Ausschnitts (0..1)
  --threshold  ab welcher relativen Nähe (0 = fern, 1 = nah) ein Pixel zum Vordergrund gehört
  --soft       Breite des weichen Übergangs an der Kante
Ergebnis in tools/out/ (inkl. depth.png zum Nachjustieren).
"""
import argparse
from pathlib import Path

import numpy as np
import onnxruntime as ort
from PIL import Image

TOOLS = Path(__file__).parent
MODEL = TOOLS / "models" / "depth_anything_v2_small.onnx"
OUT = TOOLS / "out"
SCREEN = (1080, 2340)  # S24 Ultra
MEAN = np.array([0.485, 0.456, 0.406], dtype=np.float32)
STD = np.array([0.229, 0.224, 0.225], dtype=np.float32)


def portrait_crop(img: Image.Image, center: float) -> Image.Image:
    target = SCREEN[0] / SCREEN[1]
    w, h = img.size
    if w / h > target:
        cw = round(h * target)
        left = min(max(round(w * center - cw / 2), 0), w - cw)
        img = img.crop((left, 0, left + cw, h))
    else:
        ch = round(w / target)
        top = (h - ch) // 2
        img = img.crop((0, top, w, top + ch))
    return img.resize(SCREEN, Image.LANCZOS)


def estimate_depth(img: Image.Image) -> np.ndarray:
    """Relative Nähe 0..1 (1 = nah) in Bildschirmauflösung."""
    # Modell will Kantenlängen als Vielfache von 14
    w = 518
    h = round(w * img.height / img.width / 14) * 14
    x = np.asarray(img.resize((w, h), Image.BICUBIC), dtype=np.float32) / 255.0
    x = ((x - MEAN) / STD).transpose(2, 0, 1)[None]
    session = ort.InferenceSession(str(MODEL), providers=["CPUExecutionProvider"])
    depth = session.run(None, {session.get_inputs()[0].name: x})[0][0]
    depth = (depth - depth.min()) / (depth.max() - depth.min() + 1e-6)
    # bikubisch schießt leicht über 0..1 hinaus
    return np.clip(np.asarray(Image.fromarray(depth.astype(np.float32)).resize(SCREEN, Image.BICUBIC)), 0, 1)


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("image", type=Path)
    ap.add_argument("--center", type=float, default=0.5)
    ap.add_argument("--threshold", type=float, default=0.25)
    ap.add_argument("--soft", type=float, default=0.04)
    args = ap.parse_args()

    OUT.mkdir(exist_ok=True)
    photo = portrait_crop(Image.open(args.image).convert("RGB"), args.center)
    near = estimate_depth(photo)
    alpha = np.clip((near - args.threshold) / max(args.soft, 1e-3) + 0.5, 0, 1)

    photo.save(OUT / "background.png")
    fg = photo.convert("RGBA")
    fg.putalpha(Image.fromarray((alpha * 255).astype(np.uint8)))
    fg.save(OUT / "foreground.png")
    # sqrt-kodiert: ferne Bereiche (Dunst, Himmelskante) brauchen die feinere Auflösung
    Image.fromarray((np.sqrt(near) * 255).round().astype(np.uint8)).save(OUT / "depth.png")
    print(f"ok: {OUT}  (Vordergrundanteil {alpha.mean():.0%})")


if __name__ == "__main__":
    main()
