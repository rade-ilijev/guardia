# Guardia — Google Play Submission & Compliance Pack

This document is your end-to-end guide for submitting Guardia to Google Play and maximizing the
chance of approval. It contains: an honest risk assessment, the exact permission declarations
reviewers will ask for, Data Safety form answers, prominent-disclosure copy to paste into the app
and listing, and a pre-submission checklist.

> **Important, honest framing:** Guardia uses several of Google Play's most heavily-scrutinized
> capabilities at once — **Accessibility API**, **Device Admin**, **SMS permissions**, **background
> camera**, **background location**, and **overlay (SYSTEM_ALERT_WINDOW)**. No one can *guarantee*
> Play approval for an app like this; approval requires correct policy declarations, in-app
> disclosures, a clear privacy policy, and often a written justification and possibly an appeal. This
> pack is written to give you the strongest, truthful submission. Do not overstate capabilities in
> the listing — reviewers test the app and read the policy.

---

## 0. App facts (from the code)

- Package: `com.guardia.app`
- versionName `1.0.1`, versionCode `2`, minSdk 26, target/compile SDK 35
- Single monthly subscription via Google Play Billing (`guardia_premium_monthly`)
- All face recognition is on-device; no analytics/ads/tracking SDKs; no Guardia server/account

---

## 1. Risk assessment by feature (read first)

| Capability | Play policy area | Risk | What makes it defensible |
|---|---|---|---|
| Accessibility Service | Accessibility API / Permissions | **High** | Declared as `isAccessibilityTool="true"`; used only for foreground-app detection + per-app lock + tamper, with clear in-app disclosure. Must be justified in the Play "Accessibility" declaration and **must not** read/transmit screen content. |
| Device Admin (force-lock) | Device & Network Abuse | **Medium** | Used only to lock; **wipe-data removed**; clearly disclosed. Some featured/anti-theft use is allowed. |
| SEND_SMS / RECEIVE_SMS | SMS/Call Log Permissions policy | **High** | Anti-theft "find my phone" is one of Google's listed eligible use cases. Requires the **Permissions Declaration Form**; receiver disabled until user opts in. Consider shipping a build **without** SMS if you want the smoothest review (see §6). |
| Background camera | Foreground Service + Photo/Video | **Medium-High** | Runs in a user-started foreground service with a persistent notification; camera released between checks. Requires `FOREGROUND_SERVICE_CAMERA` justification + prominent disclosure. |
| Background location | Location Permissions policy | **High** | Requires the **Location Permissions Declaration** + a video showing the in-app prominent disclosure and the feature. Premium/optional; consider gating or removing for v1. |
| SYSTEM_ALERT_WINDOW | Overlay | **Medium** | Used for the lock/check overlay; allowed for security overlays with disclosure. |
| QUERY (visible apps) | Package visibility | **Low** | Uses scoped `<queries>` (launcher intent), not `QUERY_ALL_PACKAGES`. Good. |
| Subscriptions | Payments / Subscriptions | **Low-Med** | Use Play Billing only (already done); listing must show price, renewal, and how to cancel. |

**Recommendation:** For your very first submission, strongly consider the **"Play-safe" build** in
§6 (no SMS, no background location) to reduce the number of restricted declarations to review at
once. You can add those features back in a later, separately-justified update.

---

## 2. Required Play Console declarations (App content)

Complete each of these in **Play Console → App content**:

### 2.1 Permissions Declaration Form — SMS (only if you ship SMS)
- Core feature: **Anti-theft ("find my phone")** — receiving a user-defined keyword SMS to lock and
  reply with location; sending a security/locate SMS to a user-defined trusted number.
- State that the SMS receiver is **disabled by default** and only enabled when the user turns on
  "Find my phone."
- Provide a **demo video** showing the user enabling the feature and the SMS flow.
- If you cannot meet the SMS policy bar, **remove SMS** (see §6) — email alerts still cover alerting.

### 2.2 Location Permissions Declaration (only if you ship background location)
- Feature: location-based protection (safe zones) and attaching location to security alerts the user
  configured.
- Explain why **background** access is needed (guarding behavior must adapt to location while the app
  is not in the foreground).
- Provide a **demo video** showing: the prominent in-app disclosure, the runtime prompt, and the
  feature in use.

### 2.3 Foreground Service declaration
- Declare `camera`, `microphone`, `location`, and `specialUse` foreground service types.
- `specialUse` subtitle (already in the manifest): *"Continuous on-device identity guarding
  (face/voice) to lock the device when an unauthorized user is detected."*
- Justify: guarding must run while the user is using the phone, with a persistent notification.

### 2.4 Accessibility usage declaration
- Purpose: detect the foreground app to trigger per-app face checks / App Lock, render the
  protective overlay, and detect tampering.
- Explicitly state the service **does not** collect or transmit screen content, keystrokes, or
  personal data, and is flagged `isAccessibilityTool="true"`.
- Note: if the App Lock / per-app feature is not essential for v1, removing the Accessibility service
  greatly de-risks review.

### 2.5 Device Admin
- Purpose: lock the screen on detection of an unauthorized user.
- Confirm: no wipe-data capability is declared or used.

---

## 3. Data Safety form answers (Play Console → App content → Data safety)

Use these answers; adjust if you change features. These reflect the current code (on-device, no
upload to the developer).

**Does your app collect or share any of the required user data types?**
- **Collected (processed on-device, not sent to the developer):** Yes, with the clarifications below.
- **Shared with third parties:** No third-party sharing for advertising/analytics. User-initiated
  alerts go to recipients the user chooses (their own email/SMS), which Play treats as user-directed
  transfer, not developer sharing.

**Is all user data encrypted in transit?** Yes — user-configured email uses SMTP over TLS; Play
Billing uses Google's secure channels. (Intruder photos and SMTP password are also encrypted at
rest on-device.)

**Do you provide a way for users to request data deletion?** Yes — in-app deletion controls and
uninstalling the app removes all on-device data. Provide the support email.

Data types to mark (collected = processed; **mark "Data is not shared" and "processed on device"
/ not sent off device** where the form allows; do not mark "collected" in the sense of sent to your
servers, because nothing is sent to your servers — read each toggle carefully and use the
"Processed ephemerally / on-device" options):

| Data type | Collected? | Sent off device? | Purpose |
|---|---|---|---|
| Photos (camera/face, intruder images) | On-device only | No (unless user attaches to their own email alert) | App functionality / security |
| Approximate & precise location | On-device only | Only to the user's own alert recipient if enabled | App functionality (security) |
| Audio (microphone) | On-device only, ephemeral | No (never recorded/stored) | App functionality (voice safeword) |
| Installed apps / app activity (foreground app) | On-device only | No | App functionality (per-app lock) |
| SMS messages | On-device only (keyword match) | Outgoing locate SMS sent by user config | App functionality (anti-theft) |
| App info & performance (battery/usage stats) | On-device only | No | App functionality |
| Purchases | Via Google Play | Handled by Google | Billing |

> Be precise and conservative on this form. Reviewers cross-check it against the privacy policy and
> the app's behavior. The key truthful message: **Guardia does not transmit personal data to the
> developer; sensitive processing is on-device; outbound data only occurs through features the user
> explicitly configures and to recipients the user chooses.**

---

## 4. Prominent disclosure copy (use in-app, before requesting access)

Google requires a **prominent in-app disclosure** (separate from the privacy policy) before
collecting sensitive data in the background. Show a dialog like this before enabling the relevant
feature and before the runtime permission prompt:

**Background camera (guarding):**
> "Guardia uses your front camera in the background to check whether the person using this device is
> you. Checks run only while the device is in use, a notification stays visible while guarding is on,
> and images are processed on your device and never uploaded. Enable guarding?"

**Background location (if shipped):**
> "Guardia can use your location in the background to adjust protection based on where your device is
> and to include a location link in security alerts you set up. Your location is used on your device
> and is only sent in alerts you configure. Allow background location?"

**SMS / Find my phone (if shipped):**
> "Turning on Find my phone lets Guardia send a security SMS to your trusted number and watch for a
> secret keyword text to lock and locate this device. Messages are sent from your phone via your
> carrier. Carrier rates may apply. Enable Find my phone?"

**Accessibility:**
> "Guardia's accessibility service detects which app is open so it can protect the apps you choose and
> guard against tampering. It does not read your screen content, your typing, or other apps' data,
> and nothing is uploaded. Enable the Guardia service?"

---

## 5. Store listing guidance

- **Title/short description:** describe it as a personal, on-device anti-theft / phone-security app.
  Avoid spyware/stalkerware framing ("catch your partner", "secretly monitor", "track someone").
  Stalkerware-style positioning is an automatic rejection.
- **Full description:** emphasize *your own device*, *on-device privacy*, *lock when an unauthorized
  user is detected*, *intruder snapshot for your own records*, *anti-theft find-my-phone*.
- **Required:** the listing needs a **public Privacy Policy URL**. You don't have a website, and the
  full policy already ships **inside the app** (Settings → Privacy & Data → Privacy Policy), but Play
  still requires a publicly reachable URL on the store listing. Use any free static host, e.g.:
  - A **GitHub repo + GitHub Pages** (or even a public Gist) containing `legal/PRIVACY_POLICY.md`.
  - A free **Google Sites** page, Notion public page, or similar.
  Paste the same text from `legal/PRIVACY_POLICY.md`. Keep the in-app copy and the hosted copy in sync.
- **Screenshots:** show the dashboard, settings, and the consent/disclosure screens.
- **Subscriptions:** clearly state price, billing period, free trial terms, and that users cancel in
  Google Play (already reflected in the in-app paywall text).
- Add a note that the app is intended for use on a device the user owns/controls and that monitoring
  others may require their consent under local law.

---

## 6. Optional "Play-safe" build (recommended for first approval)

To minimize restricted-permission review on your first release, build a variant that removes the
most-scrutinized features. Reintroduce them later via separate, well-justified updates.

Remove / disable for the Play-safe variant:
- `SEND_SMS`, `RECEIVE_SMS`, and the `SmsReceiver` (drop Find-my-phone-by-SMS; keep email alerts).
- `ACCESS_BACKGROUND_LOCATION` (keep foreground-only location, or drop location entirely).
- Optionally the Accessibility service (drop per-app face checks / App Lock for v1).

The codebase already isolates these (e.g. `SmsReceiver` is disabled by default; the build file notes
that `full`/`play` product flavors are planned). If you want, I can wire up a proper `play` product
flavor that strips these permissions/components at build time so a single `:app:assemblePlayRelease`
produces the policy-light AAB while your `full` build keeps everything.

---

## 7. Pre-submission technical checklist

- [ ] Set final `versionCode`/`versionName`.
- [ ] Create an upload key and configure `signing.properties` (see `android/` build config); build a
      signed **AAB** (`:app:bundleRelease`).
- [ ] Provide a real on-device face model in `android/app/src/main/assets/` (`mobilefacenet.tflite`
      or `facenet.tflite`) so recognition is accurate; otherwise it falls back to a weak descriptor.
- [ ] Set the Picovoice access key in `local.properties` if shipping voice, or the voice feature
      stays disabled (acceptable).
- [ ] Host the Privacy Policy at a public URL and add it to the listing + in-app.
- [ ] Fill the Data Safety form (§3) and all declarations (§2).
- [ ] Add prominent disclosures (§4) in the app before each sensitive permission request.
- [ ] Complete the content rating questionnaire and target-audience (not for children).
- [ ] Test on a fresh device: onboarding, enroll face, enable Device Admin, start guarding, trigger a
      lock with an unrecognized face (capture ON **and** OFF), and confirm it locks in both cases.
- [ ] Record demo videos for SMS/location declarations if those features ship.

---

## 8. What we changed in the code for compliance/quality

- **Lock reliability:** the device-lock is now the **first, isolated action** on intruder detection
  and is fully decoupled from the "Capture intruders" setting and from photo/DB/alert I/O, so a
  capture/encode/log failure can never prevent locking. (Files: `core/guard/Responder.kt`,
  `core/guard/GuardService.kt`.)
- **Removed unused `wipe-data` Device Admin policy** and corrected the device-admin description so the
  app accurately claims it only locks and never wipes. (Files: `res/xml/device_admin_policies.xml`,
  `res/values/strings.xml`.)

---

## 9. Honest bottom line

Guardia is a legitimate, privacy-respecting security tool, and this pack gives it the strongest
truthful submission. But because it combines multiple restricted capabilities, expect that the SMS,
background-location, and Accessibility declarations are the parts most likely to draw questions or a
rejection that needs an appeal. The single biggest lever to get approved quickly is to **submit the
Play-safe variant first** (§6), then add restricted features back one at a time with their own
declarations and demo videos.
