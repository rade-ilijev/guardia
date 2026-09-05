#!/usr/bin/env python3
"""Guardia face-recognition benchmark.

Measures the bundled (or a candidate) TFLite face embedder against a labeled dataset so
model and threshold changes are decided by numbers, not vibes.

Dataset layout (aligned face crops, one folder per identity):

    dataset/
      alice/  img1.jpg img2.jpg ...
      bob/    img1.jpg ...

Crops should approximate the app's canonical alignment (FaceAligner): eyes level, the
eye-line at 42% of image height, face width ~2.5x the inter-ocular distance, square.
LFW-style aligned sets are close enough for relative comparisons between models.

Outputs: genuine/impostor similarity stats, EER, and FAR/FRR across Guardia's threshold
curve  t(sensitivity) = 0.35 + sensitivity * 0.45  (see FaceRecognizer.thresholdFor).

Usage:
    python bench.py --model ../../app/src/main/assets/mobilefacenet.tflite --data ./dataset
"""

import argparse
import itertools
import sys
from pathlib import Path

import numpy as np

try:
    from tflite_runtime.interpreter import Interpreter  # lightweight, preferred
except ImportError:
    try:
        from tensorflow.lite.python.interpreter import Interpreter
    except ImportError:
        sys.exit("Install tflite-runtime or tensorflow to run the benchmark.")

try:
    from PIL import Image
except ImportError:
    sys.exit("Install pillow (pip install pillow).")


def load_embedder(model_path: str):
    interp = Interpreter(model_path=str(model_path))
    interp.allocate_tensors()
    inp = interp.get_input_details()[0]
    out = interp.get_output_details()[0]
    _, h, w, _ = inp["shape"]

    def embed(img: Image.Image) -> np.ndarray:
        x = np.asarray(img.convert("RGB").resize((w, h)), dtype=np.float32)
        # Match the app's preprocessing: normalize to [-1, 1].
        x = (x - 127.5) / 127.5
        interp.set_tensor(inp["index"], x[None, ...])
        interp.invoke()
        e = interp.get_tensor(out["index"])[0].astype(np.float32)
        n = np.linalg.norm(e)
        return e / n if n > 0 else e

    return embed


def cosine(a: np.ndarray, b: np.ndarray) -> float:
    return float(np.dot(a, b))


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--model", required=True, help="Path to the .tflite embedder")
    ap.add_argument("--data", required=True, help="Dataset root (folder per identity)")
    ap.add_argument("--max-per-person", type=int, default=10)
    ap.add_argument("--max-impostor-pairs", type=int, default=20000)
    args = ap.parse_args()

    embed = load_embedder(args.model)
    root = Path(args.data)
    people = {}
    for person_dir in sorted(p for p in root.iterdir() if p.is_dir()):
        imgs = sorted(
            f for f in person_dir.iterdir()
            if f.suffix.lower() in {".jpg", ".jpeg", ".png", ".webp"}
        )[: args.max_per_person]
        if len(imgs) >= 2:
            people[person_dir.name] = [embed(Image.open(f)) for f in imgs]
    if len(people) < 2:
        sys.exit("Need at least two identities with 2+ images each.")

    genuine = [
        cosine(a, b)
        for embs in people.values()
        for a, b in itertools.combinations(embs, 2)
    ]
    rng = np.random.default_rng(7)
    names = list(people)
    impostor = []
    while len(impostor) < args.max_impostor_pairs:
        i, j = rng.choice(len(names), 2, replace=False)
        a = people[names[i]][rng.integers(len(people[names[i]]))]
        b = people[names[j]][rng.integers(len(people[names[j]]))]
        impostor.append(cosine(a, b))
        if len(impostor) >= len(genuine) * 10:
            break

    g = np.array(genuine)
    im = np.array(impostor)
    print(f"identities={len(people)}  genuine_pairs={len(g)}  impostor_pairs={len(im)}")
    print(f"genuine  : mean={g.mean():.3f}  p5={np.percentile(g, 5):.3f}  min={g.min():.3f}")
    print(f"impostor : mean={im.mean():.3f}  p95={np.percentile(im, 95):.3f}  max={im.max():.3f}")

    # EER: threshold where false-accept rate == false-reject rate.
    ts = np.linspace(0.0, 1.0, 501)
    far = np.array([(im >= t).mean() for t in ts])
    frr = np.array([(g < t).mean() for t in ts])
    eer_i = int(np.argmin(np.abs(far - frr)))
    print(f"EER      : {((far[eer_i] + frr[eer_i]) / 2) * 100:.2f}%  at threshold {ts[eer_i]:.3f}")

    print("\nGuardia threshold curve  t = 0.35 + sensitivity * 0.45")
    print(f"{'sens':>5} {'thresh':>7} {'FAR%':>7} {'FRR%':>7}")
    for sens in (0.0, 0.25, 0.5, 0.65, 0.8, 1.0):
        t = 0.35 + sens * 0.45
        print(f"{sens:>5.2f} {t:>7.3f} {(im >= t).mean() * 100:>7.3f} {(g < t).mean() * 100:>7.2f}")


if __name__ == "__main__":
    main()
