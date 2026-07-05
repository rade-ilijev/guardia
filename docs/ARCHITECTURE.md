# Guardia — Application Architecture & Analysis

> A reverse-engineered, code-accurate explanation of the Guardia Android app, produced by reading the actual source under `android/app/src/main/java/com/guardia/app/`. Where the implementation differs from older planning notes, this document follows the **code**.

---

## 1. What Guardia Is

Guardia is a **privacy-first Android security app** that uses **on-device AI** to continuously verify *who is physically using the phone* and defends the device when an unauthorized person is detected.

Core promise: **no faces, photos, voiceprints, or embeddings ever leave the device.** Networking is used only for optional user-configured alerts (SMTP email / SMS), find-my-phone, and Play subscription billing.

Because Android exposes **no face-recognition API** and **no blind background camera capture**, Guardia builds its own recognition stack (ML Kit detection + a TFLite/LiteRT embedder + cosine matching) and works within the platform by combining:

- a persistent **foreground service** (camera + microphone + special-use),
- an **Accessibility service** (to know the foreground app and capture screens),
- a **Device Admin** receiver (to lock the device and react to wrong unlocks),
- **sensor/battery gating** so the camera only fires while the device is actively in use.

| Property | Value |
|---|---|
| Application ID | `com.guardia.app` |
| `minSdk` / `targetSdk` / `compileSdk` | 26 / 35 / 35 |
| Language / UI | Kotlin, Jetpack Compose (Material 3) |
| Architecture | MVVM + repositories, Hilt DI |
| Persistence | Room (v9) + Preferences DataStore |
| On-device ML | ML Kit Face Detection + LiteRT (TensorFlow Lite) embedder |
| Version | `1.0` (versionCode 1) |

---

## 2. Tech Stack

| Area | Library | Notes |
|---|---|---|
| UI | Jetpack Compose (BOM 2024.12.01), Material 3, Navigation Compose | Single-activity, Compose NavHost |
| DI | Hilt 2.52 + KSP | `@HiltAndroidApp`, `@AndroidEntryPoint`, `@HiltViewModel` |
| Async | Kotlin Coroutines 1.9 + Flow/StateFlow | |
| Camera | CameraX 1.5.3 (`core`, `camera2`, `lifecycle`, `view`) | Front camera, `ImageAnalysis` |
| Face detection | ML Kit `face-detection:16.1.7` | BlazeFace-based detector + landmarks/classification |
| Face embedding | LiteRT 1.4.2 (`com.google.ai.edge.litert`) | Keeps the `org.tensorflow.lite.Interpreter` API |
| DB | Room 2.6.1 | Plain SQLite (no SQLCipher) |
| Prefs | DataStore Preferences 1.1.1 | |
| Crypto | AndroidX Security Crypto 1.1.0-alpha06 | `MasterKey` + `EncryptedFile`; Keystore AES/GCM |
| Voice | Picovoice Porcupine 3.0.3 | Wake-word safeword (Eagle speaker-ID planned) |
| Images | Coil 2.7.0 | |
| Alerts | JavaMail (`com.sun.mail` 1.6.7), Play Services Location 21.3.0 | SMTP email + SMS + location |
| Billing | Play Billing 7.1.1 | Single monthly subscription |

Feature flags (`BuildConfig`): `FEATURE_DECOY`, `FEATURE_PANIC_WIPE`, `FEATURE_HIDDEN_ICON`, and `PICOVOICE_ACCESS_KEY` (from `local.properties`; empty disables voice).

---

## 3. High-Level Architecture

```
┌──────────────────────────────────────────────────────────────────────┐
│                            UI (Jetpack Compose)                        │
│  GuardiaRoot gate → Onboarding / Lock / Decoy / MainScreen (NavHost)   │
│  ViewModels (@HiltViewModel) expose StateFlow                          │
└───────────────┬──────────────────────────────────────────────────────┘
                │ inject
┌───────────────▼──────────────────────────────────────────────────────┐
│                       Domain / Service layer                          │
│  GuardController · GuardService · RulesEngine · Responder             │
│  AppTriggerManager · AppLockManager · FaceCheck · TamperResponder     │
│  LocationZoneManager · VoiceService · AlertsManager · Billing         │
└───────────────┬──────────────────────────────────────────────────────┘
                │ uses
┌───────────────▼───────────────────────┐   ┌──────────────────────────┐
│            On-device ML                │   │       Data layer         │
│  FacePipeline → FaceQualityAnalyzer    │   │  Room (PeopleRepository, │
│  → FaceAligner → FaceEmbedder          │   │  IntruderRepository,     │
│  → FaceRecognizer (cosine match)       │   │  EventsRepository,       │
│  EmbeddingMath (VERSION=2)             │   │  SafeZoneRepository)     │
└────────────────────────────────────────┘   │  AppPreferences (DataStore)│
                                              │  CryptoManager · PinManager│
                                              └──────────────────────────┘
```

Package map (`com.guardia.app`):

| Package | Responsibility |
|---|---|
| `core/ml` | Face detection, alignment, embedding, matching |
| `core/guard` | `GuardController`, `GuardService`, `CaptureGate`, `RulesEngine`, `Responder`, `AppTriggerManager` |
| `core/system` | Accessibility, Device Admin, intruder capture, tamper, boot, QS tile, camera/mic monitor |
| `core/appcheck` | Per-app face-check overlay (`FaceCheckActivity`/VM) |
| `core/applock` | PIN-gated app lock (`AppLockManager`/`AppLockActivity`) |
| `core/voice` | Picovoice safeword (`VoiceService`/`VoiceController`) |
| `core/location` | Safe zones / geo-aware schedule (`LocationZoneManager`) |
| `core/alerts` | `AlertsManager` (email/SMS), `SmsReceiver` (find-my-phone) |
| `core/security` | `CryptoManager`, `PinManager` |
| `core/billing` | `BillingManager`, `EntitlementManager` |
| `data`, `data/db` | Repositories, Room entities/DAOs, DataStore |
| `domain/model` | Plain domain types |
| `di` | `DatabaseModule`, `MlModule` |
| `ui` | Compose screens, components, theme, navigation |

---

## 4. On-Device Face Recognition Pipeline

Guardia implements a custom recognition stack because Android has no face-recognition API. There is **no liveness/anti-spoof model** in the current code (it is planned only).

### 4.1 Flow

```
Camera frame (Bitmap + rotationDegrees)
  → BitmapUtils.rotate()
  → FaceQualityAnalyzer.detect()        (ML Kit)
       0 faces → NO_FACE   |  >1 faces → MULTIPLE_FACES
  → crop + averageLuminance gate (<45 luma → INCONCLUSIVE LOW_LIGHT)
  → per enrolled model-version:
       v2 → FaceAligner.align() → FaceEmbedder.embed()
       v0 → crop → FaceEmbedder.embedLegacy()
  → FaceRecognizer.identify(probesByVersion, sensitivity, thresholdBoost)
  → FacePipeline.Analysis(outcome, similarity, personName, personId, reason?)
```

`FacePipeline` (bound to `FacePipelineImpl` via `MlModule`) is the single entry point:

```kotlin
enum class Outcome { MATCH, NO_MATCH, BLOCKED, NO_FACE, MULTIPLE_FACES, INCONCLUSIVE }
enum class InconclusiveReason { LOW_LIGHT, NO_ENROLLMENT, FACE_UNCLEAR }
suspend fun analyze(bitmap, rotationDegrees, sensitivity): Analysis
```

### 4.2 Components

| Component | File | Role |
|---|---|---|
| `FaceQualityAnalyzer` | `core/ml/FaceQualityAnalyzer.kt` | ML Kit detection (ACCURATE mode, landmarks + classification, minFaceSize 0.15); head-pose & enrollment quality gating (size, luma ≥60, yaw ≤22°, roll ≤18°, eyes-open ≥0.4) |
| `FaceAligner` | `core/ml/FaceAligner.kt` | Eye-landmark-based alignment → 160×160 crop (falls back to padded bbox crop) |
| `FaceEmbedder` | `core/ml/FaceEmbedder.kt` | LiteRT `Interpreter`; loads `mobilefacenet.tflite` then `facenet.tflite`; input/embedding dims read from tensor (default 112-in / **192-D** out); brightness + flip augmentation; L2-normalized output. Falls back to a 1024-D grayscale descriptor if no model asset is present |
| `FaceRecognizer` | `core/ml/FaceRecognizer.kt` | Cosine nearest-neighbor matching; block-list precedence; negative-embedding rejection |
| `EmbeddingMath` | `core/ml/EmbeddingMath.kt` | `VERSION=2`, `cosine`, `l2Normalize`, `centroid`, byte (de)serialization |

### 4.3 Matching logic (`FaceRecognizer`)

- Threshold from sensitivity: `0.35 + sensitivity*0.45` → range **[0.35, 0.80]**; default sensitivity `0.65` ≈ cosine **0.64**. Dim light adds up to `+0.08`.
- Per `(personId, modelVersion)` group score = `0.5 * bestSampleCosine + 0.5 * centroidCosine`; person score = max across versions.
- **Block-list precedence:** a blocked person at/above threshold (within `0.05` of best authorized) → `BLOCKED`.
- **Negative rejection:** if a “not me” (declined gallery) embedding is closer than the matched owner, the match is rejected.
- **`noEnrolledOwners`:** when no enabled authorized samples exist, callers treat results as “can’t decide” and never lock the owner out.

> Operational note: ship a real `mobilefacenet.tflite` (or `facenet.tflite`) in `android/app/src/main/assets/`. Without it, the embedder degrades to the low-accuracy grayscale fallback.

---

## 5. Continuous Guarding

### 5.1 Lifecycle and the single switch

`GuardController` is a process-wide object that is the **single on/off switch** for guarding (callable without Hilt). It is invoked by the dashboard, the QS tile, the voice safeword, and the boot receiver, and it starts/stops `GuardService`.

`GuardService` (`core/guard/GuardService.kt`) is a foreground service (`camera|microphone|location|specialUse`, notification channel `guardia_guarding`, ID 1001, `START_STICKY`). It does **not** hold the camera open continuously.

### 5.2 Polling + gating

- A coroutine polls every **350 ms**. When `CaptureGate.shouldCapture()` is true (and no per-app check is running), it calls `captureOnce()`: binds front-camera `ImageAnalysis`, skips warmup frames, grabs one bitmap, then **releases the camera immediately** (so the OS privacy indicator turns off between checks).
- `CaptureGate` (`core/guard/CaptureGate.kt`) hard-gates checks to when the device is **interactive and unlocked** (`PowerManager.isInteractive`, `!KeyguardManager.isKeyguardLocked`). It schedules:
  - **Responsiveness** cadence: Saver 5 min / Balanced 2.5 min / Max 1 min.
  - **Premium** extras: custom interval, first-check-on-unlock, check ramp (offsets from unlock), shake-to-check (accelerometer jolt > 6 m/s²).
- `applyAll()` merges user prefs + premium entitlements + `LocationZoneManager.policy` into the effective schedule.

### 5.3 Decision & response

`RulesEngine.onAnalysis()` (`core/guard/RulesEngine.kt`) converts outcomes → `OK | IGNORE | LOCK` with grace windows:

| Outcome | Trigger pref | Locks after |
|---|---|---|
| `MATCH` | — | never (resets counters) |
| `NO_MATCH` / `BLOCKED` / `MULTIPLE_FACES` | unknown/blocked/multi | **2 consecutive** bad frames |
| `NO_FACE` | `lockOnNoFace` | **3 consecutive** |
| `INCONCLUSIVE` | — | always IGNORE (won’t lock in the dark / when no enrollment) |

On `LOCK`, `Responder.onIntruder()`:
1. saves the JPEG to `IntruderRepository` (if capture enabled),
2. logs a `GuardEvent` (`INTRUDER_LOCK` / `UNKNOWN_FACE`),
3. in **test mode** logs only; otherwise calls `DeviceAdminManager.lockNow()` and `AlertsManager.onSecurityEvent()`.

---

## 6. Per-App Protection

Two independent paths both driven by `GuardAccessibilityService` detecting `TYPE_WINDOW_STATE_CHANGED`:

### 6.1 Per-app face check (`AppTriggerManager` → `FaceCheckActivity`)
- When a foreground app is in `prefs.triggerApps` and guarding is active, it launches `FaceCheckActivity` (an overlay shown over the guarded app, even over the lock screen).
- Check styles (`appCheckStyle`): **0 Loading** (opaque cover), **1 Blur**, **2 Freeze** (accessibility screenshot of the app behind the gate).
- **Once-per-visit:** after a pass (`markPassed`), no re-check until the user switches to a different real app or the screen turns off.
- `FaceCheckViewModel`: 6 s deadline, MATCH → pass; BLOCKED → fail+intruder; NO_MATCH/MULTIPLE → fast-fail after grace; failure **closes the app** (sends home) and only locks the whole device if an unauthorized face was actually seen and `appLockOnFail` is set.

### 6.2 Per-app PIN lock (`AppLockManager` → `AppLockActivity`)
- Apps in `prefs.lockedApps` require the **real PIN** (no face, no device admin). Unlock persists only while that app stays foreground.

---

## 7. System Integration

| Component | File | Role |
|---|---|---|
| `GuardAccessibilityService` | `core/system/GuardAccessibilityService.kt` | Foreground-app detection; screenshot provider (blur/freeze); `goHome()`; **tamper hook** on `onUnbind()` |
| `GuardDeviceAdminReceiver` + `DeviceAdminManager` | `core/system/` | `lockNow()`; `onPasswordFailed()` → wrong-unlock selfie; `onDisabled()` → tamper. Policy XML declares `force-lock`, `wipe-data`, `watch-login`, but **no `wipeData()` call exists** in code |
| `IntruderCaptureService` | `core/system/IntruderCaptureService.kt` | One-shot front-camera capture (wrong unlock, tamper) → encrypted JPEG + `WRONG_UNLOCK` event |
| `TamperResponder` | `core/system/TamperResponder.kt` | On accessibility-off or admin-removed: log + intruder capture + alert + high-priority notification |
| `BootReceiver` | `core/system/BootReceiver.kt` | Re-arms guarding after reboot if it was enabled |
| `GuardTileService` | `core/system/GuardTileService.kt` | Quick Settings toggle |
| `CameraMicMonitor` / `GuardiaCameraMic` | `core/system/` | Privacy dashboard: detects when *other* apps use camera/mic (separate from the intruder loop) |
| `VoiceService` / `VoiceController` | `core/voice/` | Picovoice safeword to start/stop guarding; modes Off / Always / Face-fallback (after 3 `NO_FACE`) |
| `LocationZoneManager` | `core/location/` | Fused-location safe zones → geo-aware schedule (premium); public-area fallback |
| `SmsReceiver` + `AlertsManager` | `core/alerts/` | Find-my-phone keyword SMS → lock + reply with maps link; SMTP email + SMS intruder alerts |

> Two lock philosophies: **continuous guarding** locks the whole device; **per-app checks** close the app and only lock the device when an unauthorized face is confirmed.

---

## 8. Data & Persistence

### 8.1 Room (`GuardiaDatabase`, version 9, `guardia.db`)

Plain SQLite (no SQLCipher). `fallbackToDestructiveMigration()` is enabled alongside explicit 1→9 migrations. Relationships are logical (string `personId`), no FK constraints.

| Table / Entity | Key fields |
|---|---|
| `people` / `PersonEntity` | `id`, `name`, `photoPath?`, `createdAt`, `lastSeenAt?`, `recognitionCount`, `confidenceSum`, `enabled`, `blocked` |
| `face_samples` / `FaceSampleEntity` | `id`, `personId` (indexed), `embedding: ByteArray` (L2-normalized LE float32), `quality`, `photoPath?`, `modelVersion` |
| `negative_faces` / `NegativeFaceEntity` | `id`, `personId?`, `embedding`, `photoPath?`, `modelVersion` (“not me” / blacklist) |
| `events` / `EventEntity` | `id`, `type`, `message`, `timestamp`, `photoPath?` (max 300 kept) |
| `intruder_captures` / `IntruderCaptureEntity` | `id`, `photoPath` (`.enc`), `source`, `timestamp` |
| `safe_zones` / `SafeZoneEntity` | location + per-zone schedule (radius, responsiveness, ramp, shake, lockOnNoFace, …) |

DAOs: `PersonDao`, `EventDao`, `IntruderDao`, `SafeZoneDao` (Flow-based reads, suspend writes). `PersonWithCount` is a projection (person + `sampleCount` subquery).

### 8.2 Preferences (DataStore `guardia`)

`AppPreferences` exposes the full settings surface as `Flow`s with setters: onboarding/guarding state, interval/responsiveness, triggers (first-check-on-unlock, ramp, shake), app-check style & lock-on-fail, location/public-area schedule, trigger/locked app sets, recognition sensitivity (`0.65`), lock rules (unknown/blocked/multi/no-face), capture intruders, PIN state, voice mode, test mode, profiles, and alert config (SMTP, SMS, find-my-phone keyword).

### 8.3 Repositories

`PeopleRepository` (people/samples/negatives + in-memory recognizer cache + `needsReenroll` flow), `IntruderRepository` (encrypted captures via `CryptoManager`), `EventsRepository` (activity log), `SafeZoneRepository` (zones), plus `BackupManager` (password-derived AES-256-GCM export/import).

---

## 9. Security Model

| Concern | Mechanism |
|---|---|
| Intruder photos | `CryptoManager.saveEncrypted()` — Jetpack Security `EncryptedFile` (AES256_GCM_HKDF_4KB) under `filesDir/intruders/*.enc` |
| Secrets (SMTP password) | Keystore AES/GCM via `CryptoManager.encryptString()` (alias `guardia_pref_key`) |
| PINs | `PinManager` PBKDF2-HMAC-SHA256 (120k iterations, salted), constant-time verify; legacy salted SHA-256 still verified |
| PIN lockout | 5 attempts → exponential backoff (`30s * 2^(n-5)`, capped 15 min) |
| Multi-PIN | **Real** (unlock), **Decoy** (Snake game), **Panic** (dev-safe: routed to decoy, never wipes) |

> **Honest caveats (code vs. marketing copy):** face **embeddings and face-crop JPEGs are stored unencrypted** in SQLite / `filesDir/faces/` — only intruder photos and the SMTP password use Keystore encryption. `IntruderRepository.clear()` removes DB rows but may orphan `.enc` files. The README/older plans mention SQLCipher/ObjectBox/MiniFASNet liveness — **none are in the build.**

---

## 10. UI Layer

Two navigation tiers:

1. **App gate** (`GuardiaRoot` + `AppViewModel`): `LOADING → ONBOARDING / LOCKED / UNLOCKED / DECOY`.
2. **In-app NavHost** (`MainScreen`): four bottom tabs + stack routes.

**Bottom tabs:** `home` (Dashboard), `people`, `activity`, `settings`.
**Stack routes:** `add_person?personId=`, `person/{id}`, `blocked_people`, `add_blocked`, `gallery_import/{id}`, `intruders`, `stats`, `settings_detail/{key}`, `paywall`.

Key flows:
- **Onboarding** — 10-step wizard: PINs → permissions → first face enrollment (`AddPersonScreen`) → device admin → accessibility → location → done.
- **Dashboard** — `StatusOrb` hero, start/stop guarding, setup checklist, battery impact, test mode, lock app.
- **People/Enrollment** — guided 5-pose × 2-sample capture via `AnalysisCamera`; gallery import with swipe triage (confirm/decline/blacklist); blocked-person management; per-person authorization toggles.
- **Intruders** — encrypted selfie grid (decrypted only for display); assign to person / create blocked person.
- **Settings** — searchable categorized index → `settings_detail/{key}` (guarding, detection, appcheck, response, applock, voice, location, profiles, alerts, scanner, pins, privacy, system, account). Premium sections gated by `EntitlementManager` → `paywall`.

**MVVM:** every screen pairs with a `@HiltViewModel` exposing `StateFlow`; screens are mostly stateless with navigation callbacks; nav args via `SavedStateHandle`.

**Design system** (`ui/theme`, `ui/components`): teal/cyan-on-dark “security console” aesthetic; reusable `StatusOrb`, `PinPad`, `GuardiaCard`, `GuardiaScaffold`, settings rows, `BarChart`/`ScoreRing`.

---

## 11. Monetization

- `BillingManager` wraps Play Billing for a single monthly subscription `guardia_premium_monthly`; queries product/price/trial, handles purchase/restore/acknowledge, degrades gracefully when unavailable.
- `EntitlementManager` is the single source of truth for `premium`. **Debug builds bypass billing** (`premium = BuildConfig.DEBUG`).
- Premium-gated features: App Lock, Profiles, Alerts, Location mode, custom interval / ramp / shake / first-check-on-unlock.

---

## 12. Dependency Injection

`@HiltAndroidApp class GuardiaApp`; `MainActivity` is the single `@AndroidEntryPoint`. Only **two** hand-written modules:

- `DatabaseModule` — provides `GuardiaDatabase` + DAOs (with the 1→9 migrations).
- `MlModule` — `@Binds FacePipelineImpl → FacePipeline`.

Everything else (repositories, `CryptoManager`, ML components, managers) is constructor-injected `@Singleton`. Services/receivers use `@AndroidEntryPoint` + `@Inject lateinit var`.

---

## 13. Permissions (AndroidManifest)

Camera, record audio, foreground-service (camera/microphone/special-use/location), boot-completed, post-notifications, system-alert-window, vibrate, internet, network-state, send/receive SMS, fine/coarse/background location.

Registered components: `MainActivity`, `AppLockActivity`, `FaceCheckActivity` (overlay), `GuardService`, `IntruderCaptureService`, `VoiceService`, `GuardAccessibilityService`, `GuardDeviceAdminReceiver`, `SmsReceiver`, `BootReceiver`, `GuardTileService`.

---

## 14. Testing

JVM unit tests under `android/app/src/test/` cover the trust-critical pure logic: `FaceRecognizerTest`, `EmbeddingMathTest`, `RulesEngineTest`, `PinManagerTest` (plus `ExampleUnitTest`). UI/instrumented tests use AndroidX JUnit + Espresso + Compose BOM.

---

## 15. Notable Gaps & Discrepancies (code reality)

1. **No TFLite model shipped** in `assets/` — recognition falls back to a weak grayscale descriptor until one is added.
2. **No liveness / anti-spoof** model (MiniFASNet is planned only). Mitigations: low-light refusal, multi-face rejection, negative embeddings, consecutive-frame grace.
3. **No data wipe** despite the declared `wipe-data` admin policy; panic PIN routes to the decoy game.
4. **Embeddings & face crops stored unencrypted**; only intruder photos + SMTP password are encrypted.
5. **Plain Room** (not SQLCipher/ObjectBox as older docs state); `fallbackToDestructiveMigration()` can wipe on schema mismatch.
6. **`IntruderRepository.clear()`** may orphan `.enc` files on disk.
7. **QS tile** toggles guarding without a PIN check (future work).
8. **Boot re-arm** is best-effort — camera FGS often cannot start until the device is unlocked/interactive.

---

## 16. File Map (quick reference)

| Area | Path |
|---|---|
| App / DI | `GuardiaApp.kt`, `MainActivity.kt`, `di/DatabaseModule.kt`, `di/MlModule.kt` |
| ML | `core/ml/FacePipeline(Impl).kt`, `FaceQualityAnalyzer.kt`, `FaceAligner.kt`, `FaceEmbedder.kt`, `FaceRecognizer.kt`, `EmbeddingMath.kt` |
| Guarding | `core/guard/GuardController.kt`, `GuardService.kt`, `CaptureGate.kt`, `RulesEngine.kt`, `Responder.kt`, `AppTriggerManager.kt` |
| System | `core/system/GuardAccessibilityService.kt`, `DeviceAdminManager.kt`, `GuardDeviceAdminReceiver.kt`, `IntruderCaptureService.kt`, `TamperResponder.kt`, `BootReceiver.kt`, `GuardTileService.kt`, `CameraMicMonitor.kt`, `GuardiaCameraMic.kt` |
| App check / lock | `core/appcheck/FaceCheckActivity.kt`, `FaceCheckViewModel.kt`, `core/applock/AppLockActivity.kt`, `AppLockManager.kt` |
| Voice / Location / Alerts | `core/voice/VoiceService.kt`, `core/location/LocationZoneManager.kt`, `core/alerts/AlertsManager.kt`, `SmsReceiver.kt` |
| Security / Billing | `core/security/CryptoManager.kt`, `PinManager.kt`, `core/billing/BillingManager.kt`, `EntitlementManager.kt` |
| Data | `data/db/Entities.kt`, `Daos.kt`, `GuardiaDatabase.kt`, `data/PeopleRepository.kt`, `IntruderRepository.kt`, `EventsRepository.kt`, `SafeZoneRepository.kt`, `AppPreferences.kt` |
| UI | `ui/MainActivity`/`GuardiaRoot`, `ui/main/MainScreen.kt`, `ui/screens/**`, `ui/components/**`, `ui/theme/**` |
