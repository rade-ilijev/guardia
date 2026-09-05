# face_bench — measurable recognition accuracy

Runs Guardia's TFLite face embedder over a labeled dataset and reports genuine/impostor
similarity distributions, EER, and FAR/FRR along the app's actual threshold curve
(`FaceRecognizer.thresholdFor`: `0.35 + sensitivity * 0.45`). Use it to compare candidate
models (task: embedder upgrade) and to sanity-check threshold changes before shipping them.

## Setup

    pip install pillow numpy tflite-runtime   # or tensorflow

## Dataset

One folder per identity containing aligned face crops (2+ images each):

    dataset/alice/*.jpg
    dataset/bob/*.jpg

Alignment should approximate the app's `FaceAligner` canonical crop — eyes level, eye-line
at 42% height, face width ≈ 2.5× inter-ocular distance, square (160px works). Public
aligned sets (LFW-funneled etc.) are fine for *relative* model comparisons; for absolute
numbers, build a small set through the app's own aligner. Mind dataset licenses — nothing
here gets bundled or uploaded.

## Run

    python bench.py --model ../../app/src/main/assets/mobilefacenet.tflite --data ./dataset

Interpreting: FAR at a threshold = share of strangers who would be accepted (security),
FRR = share of owner-pairs rejected (false locks / annoyance). A model upgrade must beat
the current one on both curves before bumping `EmbeddingMath.VERSION`.
