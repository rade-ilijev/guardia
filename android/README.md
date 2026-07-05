# Guardia (Android)

On-device AI security app that continuously verifies who is using the device (face + optional voice
safeword) and locks/defends it when an unauthorized person is detected. No biometric data ever
leaves the device — networking is used only for features the user explicitly configures (SMTP email
/ SMS alerts, find-my-phone) and Google Play subscription billing.

See the architecture write-up in [`../docs/ARCHITECTURE.md`](../docs/ARCHITECTURE.md), the Play
submission/compliance pack in [`../legal/PLAY_STORE_SUBMISSION.md`](../legal/PLAY_STORE_SUBMISSION.md),
and the legal texts in [`../legal`](../legal).

## Stack
- Kotlin, Jetpack Compose (Material 3), Hilt, Coroutines, DataStore, Room, Navigation-Compose.
- On-device ML: ML Kit face detection + a MobileFaceNet TFLite/LiteRT embedder + cosine matching.
- minSdk 26, targetSdk 35, compileSdk 35.

## Product flavors
Two distribution variants (dimension `distribution`):

| Flavor | applicationId | Purpose |
|---|---|---|
| `play` | `com.guardia.app` | Google Play build. Drops SMS + background location and ships a minimal Accessibility config (no screen capture) to minimise restricted-permission review. |
| `full` | `com.guardia.app.full` | Sideload build with every capability (SMS find-my-phone, background location, screenshot-based per-app check styles). |

`BuildConfig.PLAY_BUILD` gates the feature differences at runtime; the `play` source set
(`app/src/play/`) overlays a trimmed manifest and accessibility config.

## Build
```
./gradlew :app:assemblePlayDebug      # Play (store) build
./gradlew :app:assembleFullDebug      # full (sideload) build
./gradlew :app:bundlePlayRelease      # signed AAB for the Play Console
./gradlew testPlayDebugUnitTest       # JVM unit tests
```
Release builds are R8-shrunk and resource-shrunk (keep rules in `app/proguard-rules.pro`) and are
signed from `signing.properties` (see `signing.properties.example`).

## Configuration
- Ship a real on-device face model at `app/src/main/assets/mobilefacenet.tflite` (already present) so
  recognition is accurate; without it the embedder degrades to a weak grayscale fallback.
- Set `picovoice.accessKey` in `local.properties` to enable the voice safeword; empty disables it.
- Fill the three legal display values in `res/values/strings.xml` (`legal_developer_name`,
  `legal_contact_email`, `legal_updated_date`) — they are substituted into the in-app Privacy Policy
  and Terms and should match the hosted copies in [`../legal`](../legal).

## Key modules (`com.guardia.app`)
- `core/ml` — face detection, alignment, embedding, cosine matching.
- `core/guard` — `GuardController`/`GuardService` (foreground guard loop), `CaptureGate`,
  `RulesEngine`, `Responder`, `StopGuardActivity` (PIN gate for the QS tile).
- `core/system` — Accessibility, Device Admin, intruder capture, tamper, boot, QS tile, crash logger.
- `core/appcheck` / `core/applock` — per-app face check overlay and PIN-gated App Lock.
- `core/voice` / `core/location` / `core/alerts` — safeword, safe zones, email/SMS alerts.
- `core/security` / `core/billing` — Keystore crypto + PINs, Play Billing + cached entitlement.
- `data`, `data/db`, `di`, `domain/model`, `ui` — repositories/Room/DataStore, Hilt, models, Compose.
