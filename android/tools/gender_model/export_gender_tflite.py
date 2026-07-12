#!/usr/bin/env python3
"""Convert a trained gender classifier to the gender.tflite Guardia expects, or verify one.

See README.md for the tensor contract. Guardia reads the input size from the model, so any square
input works; the important part is the output layout ([P(female), P(male)] by default) and [0,1] RGB
input normalization, which your training preprocessing must match.

Usage:
    python export_gender_tflite.py --model path/to/model --out ../../app/src/main/assets/gender.tflite
    python export_gender_tflite.py --model path/to/model --male-index 0 --out .../gender.tflite
    python export_gender_tflite.py --verify path/to/gender.tflite
"""
import argparse
import sys


def _tf():
    try:
        import tensorflow as tf  # noqa: WPS433 (import inside fn keeps --verify fast)
        return tf
    except ImportError:
        sys.exit("TensorFlow is required: pip install -r requirements.txt")


def export(model_path: str, out_path: str, male_index: int) -> None:
    tf = _tf()
    model = tf.keras.models.load_model(model_path)

    # If the source model outputs [male, female], swap columns so the exported model is
    # [female, male] as the Kotlin GenderClassifier expects for the size-2 case.
    if male_index == 0:
        inp = model.input
        out = model.output
        swapped = tf.keras.layers.Lambda(lambda t: tf.reverse(t, axis=[-1]))(out)
        model = tf.keras.Model(inp, swapped)

    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    # Dynamic-range quantization: ~4x smaller, no representative dataset needed, negligible accuracy hit.
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    tflite = converter.convert()

    with open(out_path, "wb") as f:
        f.write(tflite)
    print(f"Wrote {out_path} ({len(tflite) / 1024:.0f} KB)")
    verify(out_path)


def verify(tflite_path: str) -> None:
    tf = _tf()
    interp = tf.lite.Interpreter(model_path=tflite_path)
    interp.allocate_tensors()
    inp = interp.get_input_details()[0]
    out = interp.get_output_details()[0]
    print(f"input : shape={list(inp['shape'])} dtype={inp['dtype'].__name__}")
    print(f"output: shape={list(out['shape'])} dtype={out['dtype'].__name__}")

    h, w = int(inp["shape"][1]), int(inp["shape"][2])
    import numpy as np
    dummy = np.random.rand(1, h, w, 3).astype(inp["dtype"])
    interp.set_tensor(inp["index"], dummy)
    interp.invoke()
    result = interp.get_tensor(out["index"])[0]
    print(f"dummy inference output: {result}")

    n = int(out["shape"][-1])
    if n == 2:
        print("OK: 2-class output — Guardia reads [P(female), P(male)].")
    elif n == 1:
        print("OK: 1-class output — Guardia reads P(male) (>= 0.5 -> male).")
    else:
        print(f"WARNING: output size {n} is not 1 or 2 — Guardia will not read this correctly.")


def main() -> None:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--model", help="Keras SavedModel dir or .h5 to convert")
    p.add_argument("--out", default="../../app/src/main/assets/gender.tflite", help="output .tflite path")
    p.add_argument("--male-index", type=int, default=1, choices=(0, 1),
                   help="column index of 'male' in your model's output (default 1 = [female, male])")
    p.add_argument("--verify", metavar="TFLITE", help="verify an existing gender.tflite instead of converting")
    args = p.parse_args()

    if args.verify:
        verify(args.verify)
    elif args.model:
        export(args.model, args.out, args.male_index)
    else:
        p.error("provide --model to convert, or --verify to check an existing file")


if __name__ == "__main__":
    main()
