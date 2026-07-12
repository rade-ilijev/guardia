# Optional gender model for the appearance rules

Guardia's appearance rules can optionally relax locking based on an **estimated** sex. That estimate
comes from an on-device TFLite model at `app/src/main/assets/gender.tflite`. **No model ships with
the app** — the feature stays inert (and the settings UI says so) until you add one. This folder is
the supported way to produce a shippable one.

## Read this first — licensing (it matters)

Almost every pre-trained gender model you'll find online was trained on **UTKFace**, **IMDB-Wiki**,
or **Adience**. Those datasets are licensed for **non-commercial academic research only**. A model's
own "Apache-2.0/MIT" tag does **not** override the dataset license — shipping such weights in a paid
Play Store app is copyright infringement. Do not bundle those.

**The clean route: [FairFace](https://github.com/joojs/fairface).** The FairFace dataset is
**CC BY 4.0** — commercial use is allowed *with attribution*. Train (or obtain) a small classifier on
FairFace, then convert it here. If you bundle a FairFace-derived model, add the attribution line from
[`ATTRIBUTION.md`](ATTRIBUTION.md) to the app's Third-Party Notices.

Also weigh the product side: Google removed gender classification from ML Kit/Cloud Vision over
fairness concerns, and any classifier *will* misclassify some people. In Guardia it can only ever
**relax** locking (never cause a lock), and the block list + multi-face detection always still lock —
but keep it off if you want maximum protection.

## The tensor contract `GenderClassifier` expects

The Kotlin side (`core/ml/GenderClassifier.kt`) reads the input size from the model, so any square
input works. Match this and the file just works:

| | |
|---|---|
| **Input** | `[1, H, W, 3]` float32, RGB, normalized to **[0, 1]** |
| **Output** | `[1, 2]` = `[P(female), P(male)]` (argmax), **or** `[1, 1]` = `P(male)` (≥ 0.5 → male) |
| Reported | winning class only when its probability ≥ 0.70, else `UNKNOWN` |

## Convert your trained model → `gender.tflite`

Requires Python 3.10+ and TensorFlow 2.x (`pip install -r requirements.txt`).

```bash
# From a Keras SavedModel dir or .h5 that outputs [female, male] probabilities:
python export_gender_tflite.py --model path/to/your_model --out ../../app/src/main/assets/gender.tflite

# If your model outputs [male, female] instead, remap it:
python export_gender_tflite.py --model path/to/your_model --male-index 0 --out ../../app/src/main/assets/gender.tflite

# Sanity-check an existing gender.tflite (prints I/O shapes, runs one dummy inference):
python export_gender_tflite.py --verify ../../app/src/main/assets/gender.tflite
```

The script applies dynamic-range quantization to keep the file small (typically < 2 MB) and prints
the final input/output shapes so you can confirm the contract before shipping.

## After adding the model

1. Put the file at `app/src/main/assets/gender.tflite`.
2. Rebuild. In **Settings → Detection → Appearance rules**, the **Male / Female** chips appear
   automatically (they're hidden while no model is present).
3. Add the FairFace attribution to the app's notices if the model is FairFace-derived.
