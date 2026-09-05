# Guardia brand assets — Sentinel mark

The mark: a shield drawn as one continuous stroke, broken top-right into a **scan notch**, with a
single dot at center — the eye of the guard. Shield + face-scan in one silhouette; it survives
24dp and pure-white monochrome.

- Signal cyan `#2CF5D8` on obsidian `#040608`
- Wordmark: `G U A R D I A` — Space Grotesk, semibold, wide tracking (+0.18em), uppercase

## Source of truth

- In-app / launcher vectors (already wired):
  - `android/app/src/main/res/drawable/ic_launcher_foreground.xml`
  - `android/app/src/main/res/drawable/ic_launcher_background.xml`
  - `android/app/src/main/res/drawable/ic_launcher_monochrome.xml` (dedicated themed-icon layer)
  - `android/app/src/main/res/drawable/ic_stat_guardia.xml` (notification, white silhouette)
  - `android/app/src/main/res/drawable/ic_guardia_logo.xml` (in-app hero mark)
- `guardia_mark.svg` — 512×512 master for everything outside the app.

## Exports (need Inkscape or ImageMagick — not installed on the dev machine)

Play Store icon (512 png, no rounded corners applied by you — Play masks it):

    inkscape guardia_mark.svg -w 512 -h 512 -o playstore_icon_512.png

Legacy mipmap webps (only used by non-adaptive launcher surfaces; minSdk 26 devices normally
render the adaptive XML). Regenerate at 48/72/96/144/192 px from the SVG, then:

    magick playstore_icon_512.png -resize 192x192 ic_launcher.webp   # xxxhdpi, repeat per density

Densities: mdpi 48, hdpi 72, xhdpi 96, xxhdpi 144, xxxhdpi 192 into the matching
`android/app/src/main/res/mipmap-*` folders (plus `_round` variants — same art).
