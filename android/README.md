# Guardia (Android)

On-device AI security app that continuously verifies who is using the device (face + voice) and locks/defends it when an unauthorized person is detected. No biometric data leaves the device.

See the full plans in [`../docs`](../docs) and the master plan at `../guardia_android_security_plan_45e3547a.plan.md`.

## Stack
- Kotlin, Jetpack Compose (Material 3), Hilt, Coroutines, DataStore, Navigation-Compose.
- Build flavors: `full` (sideload, all features) and `play` (policy-safe lite).
- minSdk 26, targetSdk 35, applicationId `com.guardia.app` (`com.guardia.app.play` for the play flavor).

## Build
```
./gradlew :app:assembleFullDebug      # full (sideload) flavor
./gradlew :app:assemblePlayDebug      # play (lite) flavor
```

## Current state (scaffold)
Architecture skeleton in place and building:
- `core/guard` — `GuardController` (state) + `GuardService` (foreground service) + engine stubs (`RulesEngine`, `CaptureGate`, `Responder`).
- `core/ml` — `FacePipeline` / `VoicePipeline` interfaces (our own recognition AI; Android has no face-recognition API).
- `core/system` — `GuardAccessibilityService`, `GuardDeviceAdminReceiver`, `BootReceiver`, `GuardTileService`.
- `core/security` — `CryptoManager` stub.
- `data/settings` — `SettingsRepository` (DataStore).
- `ui` — Compose theme, navigation, Dashboard (start/stop) + Settings (13 categories).

## Next (Phase 0 spike)
Wire real behavior on top of the scaffold and validate on 3-4 OEM devices:
1. CameraX capture in `GuardService` (camera FGS, while-in-use rules).
2. ML pipeline: ML Kit detect -> MobileFaceNet -> MiniFASNet liveness -> ObjectBox match.
3. Device Admin `lockNow()` on failed check.
4. Wrong-unlock capture via `onPasswordFailed` + overlay (measure per-OEM reliability).
5. Mic listen-window voice fallback.
6. Battery baseline (naive vs sensor-gated).
