# Guardia — Privacy Policy

**Effective date:** July 5, 2026
**Last updated:** July 5, 2026
**App:** Guardia (Android package `com.guardia.app`)
**Developer / Data controller:** Rade Ilijev
**Contact:** radeilijev1@gmail.com

> **Where to read this:** This Privacy Policy is built into the app — open
> **Settings → Privacy & Data → Privacy Policy** to read the full text on your device at any time,
> even offline. We do not currently operate a website; if we publish one later, the same text will be
> posted there and linked from the Google Play listing.

---

## 0. Our promise, in one paragraph

Guardia is a privacy-first phone-security app. Everything sensitive — face detection, face
recognition, intruder detection, and screen-locking — runs **on your device**. We have **no servers,
no account system, and no analytics, ads, or tracking**. We (the developer) never receive your face,
your face data, your photos, your messages, your contacts, or your location. The only time
information leaves your phone is through features **you** switch on and configure yourself (for
example, emailing yourself an intruder photo, or sending a "find my phone" text), and a purchase
receipt handled by Google Play if you choose to subscribe. **We have nothing to hide**, and this
policy explains exactly what the app touches, where it stays, and the controls you have over all of
it.

---

## 1. Who this applies to

This policy applies to anyone who installs and uses Guardia. Guardia is **not directed to children**
and is intended for people old enough to own and control their own device under local law (at least
13, or your country's minimum age of digital consent, whichever is higher).

---

## 2. The core principle: on-device processing

- **Face detection & recognition** run locally (Google ML Kit detection + an on-device TensorFlow
  Lite / LiteRT model). Camera frames are analyzed in memory and are not sent anywhere.
- **Face data** (mathematical "embeddings" derived from an enrolled face, plus optional face-crop
  thumbnails) is stored only in Guardia's private storage on your device.
- **Intruder photos** are stored **encrypted** in Guardia's private storage on your device.

There is no Guardia cloud and no Guardia login. We cannot see any of this data.

---

## 3. What Guardia accesses, why, and where it goes

| Data / sensor | Why Guardia uses it | Where it goes | Your control |
|---|---|---|---|
| **Camera (face)** | Detect/recognize who is using the device; capture an intruder photo | Processed on-device; enrollments + encrypted intruder photos stored locally | Deny/revoke Camera permission; delete people and photos in-app |
| **Microphone (voice safeword)** *(optional)* | Offline wake-word to start/stop guarding hands-free | Processed live on-device; **never recorded, stored, or sent** | Off by default; turn off Voice; revoke Microphone |
| **Location** *(optional, Premium)* | Adjust guarding by place ("safe zones"); attach a map link to alerts you set up | Used on-device; only sent inside an alert **you** configured, to the recipient **you** chose | Off by default; revoke Location anytime |
| **SMS — send/receive** *(optional)* | "Find my phone": detect your secret keyword text, lock, and reply with location; send a security text to your trusted number | Sent via your own carrier to the recipient **you** set | Receiver is **disabled** until you enable Find-my-phone; revoke SMS anytime |
| **Email alerts** *(optional)* | Email you a security event (optionally with photo/location) using **your** SMTP details | Sent directly from your device to the email server **you** entered | Off by default; your SMTP password is encrypted on-device |
| **Installed apps / foreground app** *(optional)* | Let you pick which apps to protect; know which app is open for per-app checks/App Lock | Stays on-device; app **content is never read or sent** | Don't enable App Lock / per-app checks; revoke Accessibility |
| **Security data created by the app** | PINs (stored only as salted hashes), event log, settings, battery/usage stats | Stored locally on-device | Change/clear in-app; uninstall removes all |
| **Purchases (subscription)** | Provide and restore Guardia Premium | Handled by **Google Play Billing** (Google's terms apply) | Manage/cancel in Google Play |

---

## 4. The Accessibility Service — exactly what it does

Guardia registers an Android **Accessibility Service** as a security tool
(`android:isAccessibilityTool="true"`), used **only** to:

1. Detect which app is in the foreground, so per-app face checks and App Lock can trigger;
2. Draw the blur/freeze overlay and "go home" action used to close a protected app on a failed check;
3. Detect tampering with Guardia's protection.

It does **not** read, log, collect, or transmit your screen content, your keystrokes, your messages,
or the contents of other apps. It performs no remote data collection. You can turn it off anytime in
Android **Settings → Accessibility**, and the related features simply stop.

---

## 5. Device Admin — exactly what it does

Guardia uses **Device Admin** for a single purpose: to **lock the screen** when an unauthorized
person is detected (and to capture an intruder selfie on a wrong device-unlock attempt). Guardia
**does not, and cannot, erase or wipe your device** — it declares no "wipe data" capability, and the
"panic" PIN is intentionally non-destructive (it just opens a harmless decoy screen). Remove Device
Admin anytime in Android **Settings → Security**.

---

## 6. How we use information

We use the data above **only** to run the features on your device: verifying who is using the phone,
locking it, saving optional evidence, applying per-app protection, and delivering the alerts you
configure. We do **not** sell or rent your data, use it for advertising, share it with ad networks or
data brokers, use third-party analytics/tracking SDKs, or build profiles about you.

---

## 7. Sharing and disclosure

Guardia shares your personal information with no one, except:

- **At your direction:** alerts you set up are sent to recipients **you** choose (your email/SMTP,
  your trusted SMS number).
- **Google Play Billing:** to process a subscription you buy (handled by Google).
- **Legal requirements:** because your sensitive data lives on your device and not on our servers, we
  normally have nothing to disclose. If you email us for support, we may disclose that limited
  information if strictly required by law.

---

## 8. Your control and your rights (full control)

You are in control of everything:

- **Permissions:** Grant or revoke any permission anytime in Android Settings, or from
  **Settings → Privacy & Data → Permissions & control** in the app, which links straight to the
  system screens. Denying a permission only disables the matching feature.
- **Delete your data:** In-app you can delete enrolled people and their face data, delete individual
  or all intruder photos, and clear the activity log (**Settings → Privacy & Data**).
- **Export/your copy:** You can export a password-protected backup of your enrollment data.
- **Erase everything:** Uninstalling Guardia removes all of its private storage — face embeddings,
  thumbnails, intruder photos, PIN hashes, logs, and settings. We keep no copy, so we cannot restore
  it (and never have it).
- **Subscriptions:** Manage or cancel anytime in Google Play.

If you are in a region with data-protection laws (e.g. GDPR/UK GDPR, CCPA/CPRA), you have rights to
access, correct, delete, port, or restrict your personal data. Because we do not hold your on-device
data, you exercise those rights directly with the in-app controls and by uninstalling. For anything
involving information you sent us (such as a support email), contact radeilijev1@gmail.com.

---

## 9. Data retention

- **On your device:** kept until you delete it or uninstall. Intruder photos are capped and old
  events are pruned automatically; you can also clear them manually anytime.
- **With us:** nothing, because nothing is uploaded to us. Support emails you send are kept only as
  long as needed to help you, then deleted.

---

## 10. Security

- Intruder photos are encrypted at rest (Android Jetpack Security `EncryptedFile`, AES-256-GCM).
- Your SMTP password is encrypted at rest with the Android Keystore.
- PINs are stored only as salted PBKDF2-HMAC-SHA256 hashes, with brute-force lockout/backoff.
- Sensitive components (e.g. the SMS receiver) stay disabled until you opt in.

No system is perfectly secure, but because the most sensitive data never leaves your device, the
exposure surface is minimized. Keep your OS updated and your screen lock on for best protection.

---

## 11. Permissions reference

| Permission | Purpose | Required? |
|---|---|---|
| Camera | On-device face detection/recognition and intruder photos | Core |
| Microphone (Record audio) | Optional on-device voice "safeword" | Optional |
| Foreground service (camera/microphone/location/special use) | Run guarding reliably with a visible notification | Core |
| Post notifications | Show guarding status and alerts | Core |
| Display over other apps (overlay) | Show the face-check / app-lock screen over a protected app | Per-app |
| Receive boot completed | Re-arm guarding after restart (if enabled) | Core |
| Vibrate | Haptic feedback | Minor |
| Internet / Network state | Send the email/SMS alerts you configure; Play Billing | Optional / billing |
| Send SMS | Send the security/locate text you configure | Optional |
| Receive SMS | Find-my-phone: detect your secret keyword | Optional (off by default) |
| Fine / Coarse / Background location | Location-based protection and location in alerts | Optional (Premium) |
| Accessibility service | Detect foreground app for per-app checks / App Lock; tamper protection | Per-app |
| Device Admin | Lock the screen on an unauthorized user (never wipes) | Core |
| Query installed apps | Let you pick which apps to protect with App Lock | Optional |

---

## 12. International users

Guardia processes your data on your own device, wherever you are. If you email us, your message and
the address you use may be processed in the European Union. We do not otherwise transfer your
personal data internationally because we do not collect it on servers.

---

## 13. Changes to this policy

We may update this policy as the app or the law changes. The current version is always available in
the app (**Settings → Privacy & Data → Privacy Policy**) with an updated date. Material changes will
be highlighted in the app where feasible.

---

## 14. Contact

**Rade Ilijev**
Email: radeilijev1@gmail.com
