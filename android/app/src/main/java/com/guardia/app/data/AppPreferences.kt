package com.guardia.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.guardia.app.core.security.PinManager
import com.guardia.app.core.security.PinType
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * App-level preferences: onboarding, the three PIN hashes, and core guarding settings.
 *
 * Every flow below ends in `distinctUntilChanged()`, which is not cosmetic. DataStore emits the
 * *whole* preferences snapshot whenever *any* key is written, so without it a single toggle wakes
 * all ~65 of these flows, each re-running its mapping and pushing an identical value downstream —
 * through the `combine`/`stateIn` chains in the view models and into recomposition. Deduplicating
 * at the source means a write only propagates to the settings that actually changed.
 */
@Singleton
class AppPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
    private val crypto: com.guardia.app.core.security.CryptoManager,
) {
    private val ds = context.dataStore

    val onboarded: Flow<Boolean> = ds.data.map { it[KEY_ONBOARDED] ?: false }.distinctUntilChanged()
    val guardingEnabled: Flow<Boolean> = ds.data.map { it[KEY_GUARDING_ENABLED] ?: false }.distinctUntilChanged()
    /** Responsiveness profile: 0 = Battery saver, 1 = Balanced, 2 = Max security. */
    val responsiveness: Flow<Int> = ds.data.map { it[KEY_RESPONSIVENESS] ?: 1 }.distinctUntilChanged()
    /** Whether periodic background face checks run at all (vs. only on app-open triggers). */
    val intervalCheckEnabled: Flow<Boolean> = ds.data.map { it[KEY_INTERVAL_ENABLED] ?: true }.distinctUntilChanged()
    /** Premium custom interval in seconds; 0 means use the responsiveness profile cadence. */
    val customIntervalSeconds: Flow<Int> = ds.data.map { it[KEY_CUSTOM_INTERVAL] ?: 0 }.distinctUntilChanged()
    /** Premium: run a face check the instant the device is unlocked. */
    val firstCheckOnUnlock: Flow<Boolean> = ds.data.map { it[KEY_FIRST_CHECK] ?: false }.distinctUntilChanged()
    /**
     * Premium: comma-separated gaps (seconds) between the early checks after unlock, e.g. "3,5,10".
     * The first check happens on unlock; each value is the delay until the next check. After the
     * ramp is exhausted, the steady interval (custom or responsiveness) takes over.
     */
    val checkRamp: Flow<String> = ds.data.map { it[KEY_CHECK_RAMP] ?: "" }.distinctUntilChanged()
    /** Premium: trigger an extra face check when the device is shaken. */
    val shakeToCheck: Flow<Boolean> = ds.data.map { it[KEY_SHAKE_CHECK] ?: false }.distinctUntilChanged()
    /** Per-app check screen style: 0 = Loading spinner, 1 = Blur, 2 = Freeze. */
    val appCheckStyle: Flow<Int> = ds.data.map { it[KEY_APP_CHECK_STYLE] ?: 0 }.distinctUntilChanged()
    /**
     * When a guarded app's face check fails and an unauthorized person is actually seen, also lock
     * the whole device (not just close the app). If the owner simply wasn't verified (no face / too
     * dark), the app is only closed regardless of this setting.
     */
    val appLockOnFail: Flow<Boolean> = ds.data.map { it[KEY_APP_LOCK_ON_FAIL] ?: true }.distinctUntilChanged()
    /** Premium: drive guarding from the device's location vs. user-defined safe zones. */
    val locationModeEnabled: Flow<Boolean> = ds.data.map { it[KEY_LOCATION_MODE] ?: false }.distinctUntilChanged()
    /** Whether guarding runs when outside every safe zone (a "public" area). */
    val publicGuardEnabled: Flow<Boolean> = ds.data.map { it[KEY_PUBLIC_GUARD] ?: true }.distinctUntilChanged()
    /** When true, public areas use the global Guarding & Triggers schedule instead of the fields below. */
    val publicUseDefault: Flow<Boolean> = ds.data.map { it[KEY_PUBLIC_USE_DEFAULT] ?: true }.distinctUntilChanged()
    /** Check cadence in public areas: 0 = saver, 1 = balanced, 2 = max. */
    val publicResponsiveness: Flow<Int> = ds.data.map { it[KEY_PUBLIC_RESP] ?: 2 }.distinctUntilChanged()
    /** Public custom interval (seconds); 0 = use the responsiveness profile. */
    val publicCustomIntervalSeconds: Flow<Int> = ds.data.map { it[KEY_PUBLIC_CUSTOM_INTERVAL] ?: 0 }.distinctUntilChanged()
    /** Public: run a check the instant the device is unlocked. */
    val publicFirstCheckOnUnlock: Flow<Boolean> = ds.data.map { it[KEY_PUBLIC_FIRST_CHECK] ?: false }.distinctUntilChanged()
    /** Public: comma-separated ramp gaps (seconds). */
    val publicCheckRamp: Flow<String> = ds.data.map { it[KEY_PUBLIC_RAMP] ?: "" }.distinctUntilChanged()
    /** Public: trigger a check on shake. */
    val publicShakeToCheck: Flow<Boolean> = ds.data.map { it[KEY_PUBLIC_SHAKE] ?: false }.distinctUntilChanged()
    /** Lock when no face is visible while in a public area. */
    val publicLockOnNoFace: Flow<Boolean> = ds.data.map { it[KEY_PUBLIC_NO_FACE] ?: false }.distinctUntilChanged()
    /** Packages that trigger an immediate face check when they come to the foreground. */
    val triggerApps: Flow<Set<String>> = ds.data.map { it[KEY_TRIGGER_APPS] ?: emptySet() }.distinctUntilChanged()
    /** Whether the user dismissed the first-run setup checklist on the dashboard. */
    val setupDismissed: Flow<Boolean> = ds.data.map { it[KEY_SETUP_DISMISSED] ?: false }.distinctUntilChanged()
    val sensitivity: Flow<Float> = ds.data.map { it[KEY_SENSITIVITY] ?: 0.65f }.distinctUntilChanged()
    /** Lock when an unrecognized face is seen (the core protection). */
    val lockOnUnknownFace: Flow<Boolean> = ds.data.map { it[KEY_LOCK_UNKNOWN] ?: true }.distinctUntilChanged()
    /** Lock when a block-listed (look-alike) person is recognized. */
    val lockOnBlockedPerson: Flow<Boolean> = ds.data.map { it[KEY_LOCK_BLOCKED] ?: true }.distinctUntilChanged()
    val lockOnMultipleFaces: Flow<Boolean> = ds.data.map { it[KEY_MULTI_FACE] ?: true }.distinctUntilChanged()
    /** Lock the device when no face is visible for a short streak (e.g. camera turned away/covered). */
    val lockOnNoFace: Flow<Boolean> = ds.data.map { it[KEY_NO_FACE] ?: true }.distinctUntilChanged()
    val captureIntruders: Flow<Boolean> = ds.data.map { it[KEY_CAPTURE] ?: true }.distinctUntilChanged()
    /**
     * What to do during background guarding when it's too dark to recognize a face safely:
     * 0 = Ignore (never act in low light — safest for false alarms),
     * 1 = Brighten the screen and try again (front "flash" to light the face, then re-check),
     * 2 = Lock (treat persistent unverifiable darkness as a threat and lock).
     * Default is Brighten & retry so darkness no longer silently disables protection.
     */
    val lowLightAction: Flow<Int> = ds.data.map { it[KEY_LOW_LIGHT_ACTION] ?: 1 }.distinctUntilChanged()
    val pinIsSet: Flow<Boolean> = ds.data.map { it[KEY_PIN_REAL] != null }.distinctUntilChanged()
    /**
     * Seconds Guardia may sit in the background before it asks for the PIN again. 0 means the
     * moment the app leaves the screen. The value is a grace period, not a timer: nothing counts
     * down while the app is in front.
     */
    val relockAfterSeconds: Flow<Int> = ds.data.map { it[KEY_RELOCK_AFTER] ?: 0 }.distinctUntilChanged()
    /**
     * Days to keep intruder captures before deleting them automatically. 0 = keep forever.
     * Evidence you never look at is a liability, not an asset, so this exists — but it defaults to
     * off, because silently destroying the only record of a break-in would be worse.
     */
    val evidenceRetentionDays: Flow<Int> = ds.data.map { it[KEY_EVIDENCE_RETENTION] ?: 0 }.distinctUntilChanged()

    /** Appearance: 0 = follow the system, 1 = always dark, 2 = always light. */
    val themeMode: Flow<Int> = ds.data.map { it[KEY_THEME_MODE] ?: THEME_SYSTEM }.distinctUntilChanged()
    /** Ambient animation: 0 = follow the system animator setting, 1 = always on, 2 = off. */
    val animationsMode: Flow<Int> = ds.data.map { it[KEY_ANIMATIONS_MODE] ?: MOTION_SYSTEM }.distinctUntilChanged()
    /**
     * Raises every surface boundary to the 3:1 WCAG asks of a UI component edge.
     *
     * Off by default: the hairline border is a deliberate part of the design, and this trades that
     * for an edge someone with low vision can actually find.
     */
    val highContrast: Flow<Boolean> = ds.data.map { it[KEY_HIGH_CONTRAST] ?: false }.distinctUntilChanged()

    /**
     * Whether the main UI may be screenshotted and screen-recorded.
     *
     * Off by default, and deliberately not remembered as "on" being harmless: with it on, anything
     * else that can capture the screen can also capture Guardia's intruder photos. It exists
     * because the user sometimes needs a picture of their own screen and no app should make that
     * impossible without saying so. The PIN gates are never covered by it.
     */
    val allowScreenCapture: Flow<Boolean> =
        ds.data.map { it[KEY_ALLOW_SCREEN_CAPTURE] ?: false }.distinctUntilChanged()
    /** Whether the lock screen offers fingerprint / face unlock as an alternative to the real PIN. */
    val decoyPinSet: Flow<Boolean> = ds.data.map { it[KEY_PIN_DECOY] != null }.distinctUntilChanged()
    /** Whether a recovery code exists — the only way back in if the real PIN is forgotten. */
    val recoveryCodeSet: Flow<Boolean> = ds.data.map { it[KEY_RECOVERY_CODE] != null }.distinctUntilChanged()
    val panicPinSet: Flow<Boolean> = ds.data.map { it[KEY_PIN_PANIC] != null }.distinctUntilChanged()
    /** Number of consecutive wrong PIN entries since the last success (drives the lockout backoff). */
    val pinFailedAttempts: Flow<Int> = ds.data.map { it[KEY_PIN_ATTEMPTS] ?: 0 }.distinctUntilChanged()
    /** Epoch-ms until which PIN entry is locked out (0 = not locked). */
    val pinLockedUntil: Flow<Long> = ds.data.map { it[KEY_PIN_LOCKED_UNTIL] ?: 0L }.distinctUntilChanged()
    /** 0 = Off, 1 = Always, 2 = Fallback only. */
    val voiceListeningMode: Flow<Int> = ds.data.map { it[KEY_VOICE_MODE] ?: 0 }.distinctUntilChanged()
    /** Test mode: show feedback notifications instead of locking the device. */
    val testMode: Flow<Boolean> = ds.data.map { it[KEY_TEST_MODE] ?: false }.distinctUntilChanged()
    /** Package names that require a PIN/face unlock when opened (App Lock). */
    val lockedApps: Flow<Set<String>> = ds.data.map { it[KEY_LOCKED_APPS] ?: emptySet() }.distinctUntilChanged()
    /** Active guarding profile name (preset applied to the guarding settings above). */
    val activeProfile: Flow<String> = ds.data.map { it[KEY_ACTIVE_PROFILE] ?: "Default" }.distinctUntilChanged()
    /** JSON-encoded rolling 24h log of the app's own face-check activity (for battery stats). */
    val guardActivityLog: Flow<String> = ds.data.map { it[KEY_GUARD_ACTIVITY] ?: "" }.distinctUntilChanged()

    // --- Alerts & Recovery ---
    val emailAlertsEnabled: Flow<Boolean> = ds.data.map { it[KEY_EMAIL_ENABLED] ?: false }.distinctUntilChanged()
    val smtpHost: Flow<String> = ds.data.map { it[KEY_SMTP_HOST] ?: "smtp.gmail.com" }.distinctUntilChanged()
    val smtpPort: Flow<Int> = ds.data.map { it[KEY_SMTP_PORT] ?: 587 }.distinctUntilChanged()
    val smtpUser: Flow<String> = ds.data.map { it[KEY_SMTP_USER] ?: "" }.distinctUntilChanged()
    /**
     * SMTP password, encrypted at rest with the Android Keystore. Values saved before encryption was
     * added (plaintext) still read back via the decrypt fallback, and are re-encrypted on next save.
     */
    val smtpPassword: Flow<String> = ds.data.map { prefs ->
        val stored = prefs[KEY_SMTP_PASS] ?: return@map ""
        if (stored.isBlank()) "" else crypto.decryptString(stored) ?: stored
    }.distinctUntilChanged()
    val alertRecipient: Flow<String> = ds.data.map { it[KEY_ALERT_RECIPIENT] ?: "" }.distinctUntilChanged()
    val smsAlertsEnabled: Flow<Boolean> = ds.data.map { it[KEY_SMS_ENABLED] ?: false }.distinctUntilChanged()
    val trustedNumber: Flow<String> = ds.data.map { it[KEY_TRUSTED_NUMBER] ?: "" }.distinctUntilChanged()
    val findMyPhoneEnabled: Flow<Boolean> = ds.data.map { it[KEY_FIND_ENABLED] ?: false }.distinctUntilChanged()
    val findKeyword: Flow<String> = ds.data.map { it[KEY_FIND_KEYWORD] ?: DEFAULT_FIND_KEYWORD }.distinctUntilChanged()
    /** Secret keyword that remotely arms guarding by text. Empty disables the arm command. */
    val armKeyword: Flow<String> = ds.data.map { it[KEY_ARM_KEYWORD] ?: DEFAULT_ARM_KEYWORD }.distinctUntilChanged()
    /**
     * When true (default), find-my-phone only reacts to the trusted number, so a leaked keyword
     * alone can't be used by a stranger to locate the device.
     */
    val findTrustedOnly: Flow<Boolean> = ds.data.map { it[KEY_FIND_TRUSTED_ONLY] ?: true }.distinctUntilChanged()

    /** Last-known premium entitlement so paying users keep features while offline. */
    val premiumCached: Flow<Boolean> = ds.data.map { it[KEY_PREMIUM_CACHED] ?: false }.distinctUntilChanged()
    /** Whether the prominent background-camera disclosure was accepted (required before guarding). */
    val guardDisclosureAccepted: Flow<Boolean> = ds.data.map { it[KEY_GUARD_DISCLOSURE] ?: false }.distinctUntilChanged()
    /** Failed device unlocks before an intruder selfie is captured (1 = every failure). */
    val wrongUnlockThreshold: Flow<Int> = ds.data.map { it[KEY_WRONG_UNLOCK_THRESHOLD] ?: 1 }.distinctUntilChanged()
    /** Opt-in local crash log (written to app-private storage only, never uploaded). */
    val crashLogEnabled: Flow<Boolean> = ds.data.map { it[KEY_CRASH_LOG] ?: false }.distinctUntilChanged()

    // --- Appearance rules (experimental): don't lock for a stranger whose estimated look matches. ---
    val appearanceRulesEnabled: Flow<Boolean> = ds.data.map { it[KEY_APPEARANCE_RULES] ?: false }.distinctUntilChanged()
    /** Hair-colour buckets (AppearanceAnalyzer.HairColor names) the user chose NOT to lock for. */
    val ignoreHairColors: Flow<Set<String>> = ds.data.map { it[KEY_IGNORE_HAIR] ?: emptySet() }.distinctUntilChanged()
    /** Eye-tone buckets (AppearanceAnalyzer.EyeTone names) the user chose NOT to lock for. */
    val ignoreEyeTones: Flow<Set<String>> = ds.data.map { it[KEY_IGNORE_EYES] ?: emptySet() }.distinctUntilChanged()
    /** Sex buckets (AppearanceAnalyzer.Sex names) the user chose NOT to lock for (needs gender model). */
    val ignoreSexes: Flow<Set<String>> = ds.data.map { it[KEY_IGNORE_SEX] ?: emptySet() }.distinctUntilChanged()

    /** Require a liveness signal (a blink) before a per-app face check passes — anti-spoofing. */
    val requireLiveness: Flow<Boolean> = ds.data.map { it[KEY_REQUIRE_LIVENESS] ?: true }.distinctUntilChanged()

    /** Weekly protection summary notification (on by default; it's the app's trust receipt). */
    val weeklyDigestEnabled: Flow<Boolean> = ds.data.map { it[KEY_WEEKLY_DIGEST] ?: true }.distinctUntilChanged()
    /** Epoch-ms of the last weekly digest we posted (0 = never). */
    val lastDigestAt: Flow<Long> = ds.data.map { it[KEY_LAST_DIGEST_AT] ?: 0L }.distinctUntilChanged()
    /** Epoch-ms heartbeat written by the running guard service (0 = never ran). */
    val guardHeartbeatAt: Flow<Long> = ds.data.map { it[KEY_GUARD_HEARTBEAT] ?: 0L }.distinctUntilChanged()
    /** True when the guard service last stopped through onDestroy (vs. being killed). */
    val guardStoppedCleanly: Flow<Boolean> = ds.data.map { it[KEY_GUARD_CLEAN_STOP] ?: true }.distinctUntilChanged()

    /** Pause periodic checks while connected to a Wi-Fi network the user trusts (e.g. home). */
    val trustedWifiEnabled: Flow<Boolean> = ds.data.map { it[KEY_TRUSTED_WIFI_ENABLED] ?: false }.distinctUntilChanged()
    /** SSIDs the user marked as trusted. */
    val trustedSsids: Flow<Set<String>> = ds.data.map { it[KEY_TRUSTED_SSIDS] ?: emptySet() }.distinctUntilChanged()

    suspend fun setTrustedWifiEnabled(value: Boolean) = ds.edit { it[KEY_TRUSTED_WIFI_ENABLED] = value }
    suspend fun toggleTrustedSsid(ssid: String) = ds.edit {
        val current = it[KEY_TRUSTED_SSIDS] ?: emptySet()
        it[KEY_TRUSTED_SSIDS] = if (ssid in current) current - ssid else current + ssid
    }

    /** Epoch-ms of the last unknown-face lock eligible for "was that you?" learning (0 = none). */
    val pendingFalseLockAt: Flow<Long> = ds.data.map { it[KEY_PENDING_FALSE_LOCK_AT] ?: 0L }.distinctUntilChanged()
    /** Encrypted capture path backing the pending false-lock question. */
    val pendingFalseLockPhoto: Flow<String?> = ds.data.map { it[KEY_PENDING_FALSE_LOCK_PHOTO] }.distinctUntilChanged()

    suspend fun setPendingFalseLock(at: Long, photoPath: String) = ds.edit {
        it[KEY_PENDING_FALSE_LOCK_AT] = at
        it[KEY_PENDING_FALSE_LOCK_PHOTO] = photoPath
    }

    suspend fun clearPendingFalseLock() = ds.edit {
        it.remove(KEY_PENDING_FALSE_LOCK_AT)
        it.remove(KEY_PENDING_FALSE_LOCK_PHOTO)
    }

    /**
     * Epoch-ms of the newest intruder event the owner has already been shown on the home screen.
     *
     * The alert used to be derived from the newest intruder event and nothing else, so once
     * something had been caught the banner stayed up until the activity log was wiped — the only
     * way to make the event stop being the newest one. Acknowledging is a separate fact from the
     * event happening, so it gets its own stored value rather than being inferred from the log.
     */
    val intruderAlertSeenAt: Flow<Long> = ds.data.map { it[KEY_INTRUDER_SEEN_AT] ?: 0L }.distinctUntilChanged()

    /** Marks every intruder event up to [at] as seen. Monotonic, so an older ack can't reopen it. */
    suspend fun setIntruderAlertSeenAt(at: Long) = ds.edit { prefs ->
        prefs[KEY_INTRUDER_SEEN_AT] = maxOf(prefs[KEY_INTRUDER_SEEN_AT] ?: 0L, at)
    }

    suspend fun setWeeklyDigestEnabled(value: Boolean) = ds.edit { it[KEY_WEEKLY_DIGEST] = value }
    suspend fun setLastDigestAt(value: Long) = ds.edit { it[KEY_LAST_DIGEST_AT] = value }
    suspend fun setGuardHeartbeatAt(value: Long) = ds.edit { it[KEY_GUARD_HEARTBEAT] = value }
    suspend fun setGuardStoppedCleanly(value: Boolean) = ds.edit { it[KEY_GUARD_CLEAN_STOP] = value }

    suspend fun setOnboarded(value: Boolean) = ds.edit { it[KEY_ONBOARDED] = value }
    suspend fun setRelockAfterSeconds(value: Int) = ds.edit { it[KEY_RELOCK_AFTER] = value.coerceAtLeast(0) }
    suspend fun setEvidenceRetentionDays(value: Int) = ds.edit { it[KEY_EVIDENCE_RETENTION] = value.coerceAtLeast(0) }
    suspend fun setThemeMode(value: Int) = ds.edit { it[KEY_THEME_MODE] = value.coerceIn(0, 2) }
    suspend fun setAnimationsMode(value: Int) = ds.edit { it[KEY_ANIMATIONS_MODE] = value.coerceIn(0, 2) }
    suspend fun setHighContrast(value: Boolean) = ds.edit { it[KEY_HIGH_CONTRAST] = value }
    suspend fun setAllowScreenCapture(value: Boolean) = ds.edit { it[KEY_ALLOW_SCREEN_CAPTURE] = value }
    suspend fun setGuardingEnabled(value: Boolean) = ds.edit { it[KEY_GUARDING_ENABLED] = value }
    suspend fun setResponsiveness(level: Int) = ds.edit { it[KEY_RESPONSIVENESS] = level }
    suspend fun setIntervalCheckEnabled(value: Boolean) = ds.edit { it[KEY_INTERVAL_ENABLED] = value }
    suspend fun setCustomIntervalSeconds(seconds: Int) = ds.edit { it[KEY_CUSTOM_INTERVAL] = seconds }
    suspend fun setFirstCheckOnUnlock(value: Boolean) = ds.edit { it[KEY_FIRST_CHECK] = value }
    suspend fun setCheckRamp(value: String) = ds.edit { it[KEY_CHECK_RAMP] = value }
    suspend fun setShakeToCheck(value: Boolean) = ds.edit { it[KEY_SHAKE_CHECK] = value }
    suspend fun setAppCheckStyle(style: Int) = ds.edit { it[KEY_APP_CHECK_STYLE] = style }
    suspend fun setAppLockOnFail(value: Boolean) = ds.edit { it[KEY_APP_LOCK_ON_FAIL] = value }
    suspend fun setLocationModeEnabled(value: Boolean) = ds.edit { it[KEY_LOCATION_MODE] = value }
    suspend fun setPublicGuardEnabled(value: Boolean) = ds.edit { it[KEY_PUBLIC_GUARD] = value }
    suspend fun setPublicUseDefault(value: Boolean) = ds.edit { it[KEY_PUBLIC_USE_DEFAULT] = value }
    suspend fun setPublicResponsiveness(level: Int) = ds.edit { it[KEY_PUBLIC_RESP] = level }
    suspend fun setPublicCustomIntervalSeconds(seconds: Int) = ds.edit { it[KEY_PUBLIC_CUSTOM_INTERVAL] = seconds }
    suspend fun setPublicFirstCheckOnUnlock(value: Boolean) = ds.edit { it[KEY_PUBLIC_FIRST_CHECK] = value }
    suspend fun setPublicCheckRamp(value: String) = ds.edit { it[KEY_PUBLIC_RAMP] = value }
    suspend fun setPublicShakeToCheck(value: Boolean) = ds.edit { it[KEY_PUBLIC_SHAKE] = value }
    suspend fun setPublicLockOnNoFace(value: Boolean) = ds.edit { it[KEY_PUBLIC_NO_FACE] = value }
    suspend fun setTriggerApps(packages: Set<String>) = ds.edit { it[KEY_TRIGGER_APPS] = packages }
    suspend fun setSetupDismissed(value: Boolean) = ds.edit { it[KEY_SETUP_DISMISSED] = value }
    suspend fun setSensitivity(value: Float) = ds.edit { it[KEY_SENSITIVITY] = value }
    suspend fun setLockOnUnknownFace(value: Boolean) = ds.edit { it[KEY_LOCK_UNKNOWN] = value }
    suspend fun setLockOnBlockedPerson(value: Boolean) = ds.edit { it[KEY_LOCK_BLOCKED] = value }
    suspend fun setLockOnMultipleFaces(value: Boolean) = ds.edit { it[KEY_MULTI_FACE] = value }
    suspend fun setLockOnNoFace(value: Boolean) = ds.edit { it[KEY_NO_FACE] = value }
    suspend fun setCaptureIntruders(value: Boolean) = ds.edit { it[KEY_CAPTURE] = value }
    suspend fun setLowLightAction(value: Int) = ds.edit { it[KEY_LOW_LIGHT_ACTION] = value }
    suspend fun setVoiceListeningMode(mode: Int) = ds.edit { it[KEY_VOICE_MODE] = mode }
    suspend fun setTestMode(value: Boolean) = ds.edit { it[KEY_TEST_MODE] = value }
    suspend fun setLockedApps(packages: Set<String>) = ds.edit { it[KEY_LOCKED_APPS] = packages }
    suspend fun setActiveProfile(name: String) = ds.edit { it[KEY_ACTIVE_PROFILE] = name }
    suspend fun setGuardActivityLog(json: String) = ds.edit { it[KEY_GUARD_ACTIVITY] = json }

    suspend fun setEmailAlertsEnabled(value: Boolean) = ds.edit { it[KEY_EMAIL_ENABLED] = value }
    suspend fun setSmtpHost(value: String) = ds.edit { it[KEY_SMTP_HOST] = value }
    suspend fun setSmtpPort(value: Int) = ds.edit { it[KEY_SMTP_PORT] = value }
    suspend fun setSmtpUser(value: String) = ds.edit { it[KEY_SMTP_USER] = value }
    /**
     * Stores the SMTP password encrypted at rest. Returns false — and persists nothing — if
     * Keystore encryption fails, so a credential is never silently written in plaintext.
     */
    suspend fun setSmtpPassword(value: String): Boolean {
        if (value.isBlank()) {
            ds.edit { it[KEY_SMTP_PASS] = "" }
            return true
        }
        val encrypted = crypto.encryptString(value) ?: return false
        ds.edit { it[KEY_SMTP_PASS] = encrypted }
        return true
    }
    suspend fun setAlertRecipient(value: String) = ds.edit { it[KEY_ALERT_RECIPIENT] = value }
    suspend fun setSmsAlertsEnabled(value: Boolean) = ds.edit { it[KEY_SMS_ENABLED] = value }
    suspend fun setTrustedNumber(value: String) = ds.edit { it[KEY_TRUSTED_NUMBER] = value }
    suspend fun setFindMyPhoneEnabled(value: Boolean) = ds.edit { it[KEY_FIND_ENABLED] = value }
    suspend fun setFindKeyword(value: String) = ds.edit { it[KEY_FIND_KEYWORD] = value }
    suspend fun setArmKeyword(value: String) = ds.edit { it[KEY_ARM_KEYWORD] = value }
    suspend fun setFindTrustedOnly(value: Boolean) = ds.edit { it[KEY_FIND_TRUSTED_ONLY] = value }

    suspend fun setPremiumCached(value: Boolean) = ds.edit { it[KEY_PREMIUM_CACHED] = value }
    suspend fun setGuardDisclosureAccepted(value: Boolean) = ds.edit { it[KEY_GUARD_DISCLOSURE] = value }
    suspend fun setWrongUnlockThreshold(value: Int) = ds.edit { it[KEY_WRONG_UNLOCK_THRESHOLD] = value }
    suspend fun setCrashLogEnabled(value: Boolean) = ds.edit { it[KEY_CRASH_LOG] = value }

    suspend fun setAppearanceRulesEnabled(value: Boolean) = ds.edit { it[KEY_APPEARANCE_RULES] = value }
    suspend fun setIgnoreHairColors(value: Set<String>) = ds.edit { it[KEY_IGNORE_HAIR] = value }
    suspend fun setIgnoreEyeTones(value: Set<String>) = ds.edit { it[KEY_IGNORE_EYES] = value }
    suspend fun setIgnoreSexes(value: Set<String>) = ds.edit { it[KEY_IGNORE_SEX] = value }
    suspend fun setRequireLiveness(value: Boolean) = ds.edit { it[KEY_REQUIRE_LIVENESS] = value }

    /** Counts a failed device unlock; returns the new consecutive-failure count. */
    suspend fun recordWrongUnlock(): Int {
        var count = 0
        ds.edit { prefs ->
            count = (prefs[KEY_WRONG_UNLOCK_COUNT] ?: 0) + 1
            prefs[KEY_WRONG_UNLOCK_COUNT] = count
        }
        return count
    }

    /** Clears the failed-unlock streak after a successful device unlock. */
    suspend fun resetWrongUnlocks() = ds.edit { it.remove(KEY_WRONG_UNLOCK_COUNT) }

    /** Persist all three PINs (real required; decoy/panic optional). */
    suspend fun setPins(real: String, decoy: String?, panic: String?) {
        val salt = PinManager.newSalt()
        ds.edit { prefs ->
            prefs[KEY_PIN_SALT] = salt
            prefs[KEY_PIN_REAL] = PinManager.hash(real, salt)
            if (!decoy.isNullOrBlank()) prefs[KEY_PIN_DECOY] = PinManager.hash(decoy, salt)
            if (!panic.isNullOrBlank()) prefs[KEY_PIN_PANIC] = PinManager.hash(panic, salt)
        }
    }

    /**
     * Sets or replaces a single PIN while keeping the others intact. All PINs share one salt, so
     * this reuses the existing salt (rotating it would silently invalidate the untouched PINs).
     * Returns false when no PIN exists yet and [type] isn't REAL — the real PIN must come first.
     */
    suspend fun setSinglePin(type: PinType, pin: String): Boolean {
        val existingSalt = ds.data.first()[KEY_PIN_SALT]
        if (existingSalt == null && type != PinType.REAL) return false
        val salt = existingSalt ?: PinManager.newSalt()
        val hash = withContext(Dispatchers.Default) { PinManager.hash(pin, salt) }
        ds.edit { prefs ->
            prefs[KEY_PIN_SALT] = salt
            when (type) {
                PinType.REAL -> prefs[KEY_PIN_REAL] = hash
                PinType.DECOY -> prefs[KEY_PIN_DECOY] = hash
                PinType.PANIC -> prefs[KEY_PIN_PANIC] = hash
            }
        }
        return true
    }

    /** Removes the decoy or panic PIN. The real PIN can only be replaced, never removed. */
    suspend fun clearSecondaryPin(type: PinType) {
        ds.edit { prefs ->
            when (type) {
                PinType.DECOY -> prefs.remove(KEY_PIN_DECOY)
                PinType.PANIC -> prefs.remove(KEY_PIN_PANIC)
                PinType.REAL -> Unit
            }
        }
    }

    /**
     * Records a wrong PIN entry. After [LOCK_AFTER_ATTEMPTS] failures, locks PIN entry for an
     * exponentially growing window (capped at [MAX_LOCK_MS]). Returns the new locked-until epoch-ms
     * (0 if not yet locked out).
     */
    /**
     * Stores a recovery code, hashed with its own salt.
     *
     * Separate salt from the PINs on purpose: the code and the PIN are independent credentials, and
     * sharing a salt would mean rotating one silently changed the derivation of the other.
     */
    suspend fun setRecoveryCode(code: String) {
        val salt = PinManager.newSalt()
        val canonical = PinManager.normalizeRecoveryCode(code)
        ds.edit { prefs ->
            prefs[KEY_RECOVERY_SALT] = salt
            prefs[KEY_RECOVERY_CODE] = PinManager.hash(canonical, salt)
        }
    }

    /**
     * Verifies a typed recovery code. Returns false when no code was ever set, so a device without
     * one cannot be talked into accepting anything.
     */
    suspend fun verifyRecoveryCode(input: String): Boolean {
        val prefs = ds.data.first()
        val stored = prefs[KEY_RECOVERY_CODE] ?: return false
        val salt = prefs[KEY_RECOVERY_SALT] ?: return false
        return PinManager.verify(PinManager.normalizeRecoveryCode(input), salt, stored)
    }

    /**
     * The install's own secret, embedded in every backup this device exports.
     *
     * It exists so that "restore from a backup" can be a *recovery* route rather than a bypass.
     * Decrypting a backup only proves you know some password, and anyone can export a backup from
     * their own install under a password of their choosing — so decryption alone would let a thief
     * reset the PIN of a phone they just picked up. Matching this token proves the file came from
     * this install, which nobody but the owner can produce.
     *
     * Generated on demand (first export) rather than at setup, so nothing has to be migrated, and
     * written inside a single [ds.edit] so two concurrent exports cannot mint different tokens.
     */
    suspend fun installToken(): String {
        var token = ""
        ds.edit { prefs ->
            token = prefs[KEY_INSTALL_TOKEN] ?: PinManager.newToken().also { prefs[KEY_INSTALL_TOKEN] = it }
        }
        return token
    }

    /** The stored install token, or null if this install has never exported a backup. */
    suspend fun installTokenOrNull(): String? = ds.data.first()[KEY_INSTALL_TOKEN]

    /**
     * Clears the brute-force lockout. Called after a successful recovery so the user can set a new
     * PIN immediately instead of waiting out a backoff they just proved they own the device past.
     */
    suspend fun clearPinLockout() = ds.edit { prefs ->
        prefs[KEY_PIN_ATTEMPTS] = 0
        prefs[KEY_PIN_LOCKED_UNTIL] = 0L
    }

    suspend fun recordPinFailure(): Long {
        var lockedUntil = 0L
        ds.edit { prefs ->
            val attempts = (prefs[KEY_PIN_ATTEMPTS] ?: 0) + 1
            prefs[KEY_PIN_ATTEMPTS] = attempts
            val duration = lockoutDurationMs(attempts)
            if (duration > 0L) {
                lockedUntil = System.currentTimeMillis() + duration
                prefs[KEY_PIN_LOCKED_UNTIL] = lockedUntil
            }
        }
        return lockedUntil
    }

    /** Clears the lockout counter after a correct PIN. */
    suspend fun recordPinSuccess() = ds.edit {
        it.remove(KEY_PIN_ATTEMPTS)
        it.remove(KEY_PIN_LOCKED_UNTIL)
    }

    /**
     * Returns which PIN role the entered code matches, or null if none. Verifies against both the
     * current (PBKDF2) and legacy (SHA-256) stored formats so existing PINs keep working.
     *
     * The PBKDF2 comparison (120k iterations) is CPU-heavy and is deliberately run on
     * [Dispatchers.Default] — callers verify from the UI (viewModelScope = Main), and hashing on the
     * main thread was janking the PIN pad and the unlock transition.
     */
    suspend fun verifyPin(pin: String): PinType? {
        val prefs = ds.data.first()
        val salt = prefs[KEY_PIN_SALT] ?: return null
        return withContext(Dispatchers.Default) {
            fun matches(stored: String?) = stored != null && PinManager.verify(pin, salt, stored)
            when {
                matches(prefs[KEY_PIN_REAL]) -> PinType.REAL
                matches(prefs[KEY_PIN_DECOY]) -> PinType.DECOY
                matches(prefs[KEY_PIN_PANIC]) -> PinType.PANIC
                else -> null
            }
        }
    }

    companion object {
        private val KEY_WEEKLY_DIGEST = booleanPreferencesKey("weekly_digest_enabled")
        private val KEY_LAST_DIGEST_AT = longPreferencesKey("last_digest_at")
        private val KEY_GUARD_HEARTBEAT = longPreferencesKey("guard_heartbeat_at")
        private val KEY_GUARD_CLEAN_STOP = booleanPreferencesKey("guard_stopped_cleanly")
        private val KEY_TRUSTED_WIFI_ENABLED = booleanPreferencesKey("trusted_wifi_enabled")
        private val KEY_TRUSTED_SSIDS = stringSetPreferencesKey("trusted_ssids")
        private val KEY_INTRUDER_SEEN_AT = longPreferencesKey("intruder_alert_seen_at")
        private val KEY_PENDING_FALSE_LOCK_AT = longPreferencesKey("pending_false_lock_at")
        private val KEY_PENDING_FALSE_LOCK_PHOTO = stringPreferencesKey("pending_false_lock_photo")
        private val KEY_ONBOARDED = booleanPreferencesKey("onboarded")
        private val KEY_RELOCK_AFTER = intPreferencesKey("relock_after_seconds")
        private val KEY_RECOVERY_CODE = stringPreferencesKey("recovery_code_hash")
        private val KEY_RECOVERY_SALT = stringPreferencesKey("recovery_code_salt")
        private val KEY_INSTALL_TOKEN = stringPreferencesKey("install_token")
        private val KEY_EVIDENCE_RETENTION = intPreferencesKey("evidence_retention_days")
        private val KEY_THEME_MODE = intPreferencesKey("theme_mode")
        private val KEY_ANIMATIONS_MODE = intPreferencesKey("animations_mode")
        private val KEY_HIGH_CONTRAST = booleanPreferencesKey("high_contrast")
        private val KEY_ALLOW_SCREEN_CAPTURE = booleanPreferencesKey("allow_screen_capture")
        private val KEY_GUARDING_ENABLED = booleanPreferencesKey("guarding_enabled")
        private val KEY_RESPONSIVENESS = intPreferencesKey("responsiveness")
        private val KEY_INTERVAL_ENABLED = booleanPreferencesKey("interval_check_enabled")
        private val KEY_CUSTOM_INTERVAL = intPreferencesKey("custom_interval_seconds")
        private val KEY_FIRST_CHECK = booleanPreferencesKey("first_check_on_unlock")
        private val KEY_CHECK_RAMP = stringPreferencesKey("check_ramp")
        private val KEY_SHAKE_CHECK = booleanPreferencesKey("shake_to_check")
        private val KEY_APP_CHECK_STYLE = intPreferencesKey("app_check_style")
        private val KEY_APP_LOCK_ON_FAIL = booleanPreferencesKey("app_lock_on_fail")
        private val KEY_LOCATION_MODE = booleanPreferencesKey("location_mode_enabled")
        private val KEY_PUBLIC_GUARD = booleanPreferencesKey("public_guard_enabled")
        private val KEY_PUBLIC_USE_DEFAULT = booleanPreferencesKey("public_use_default")
        private val KEY_PUBLIC_RESP = intPreferencesKey("public_responsiveness")
        private val KEY_PUBLIC_CUSTOM_INTERVAL = intPreferencesKey("public_custom_interval")
        private val KEY_PUBLIC_FIRST_CHECK = booleanPreferencesKey("public_first_check")
        private val KEY_PUBLIC_RAMP = stringPreferencesKey("public_ramp")
        private val KEY_PUBLIC_SHAKE = booleanPreferencesKey("public_shake")
        private val KEY_PUBLIC_NO_FACE = booleanPreferencesKey("public_lock_no_face")
        private val KEY_TRIGGER_APPS = stringSetPreferencesKey("trigger_apps")
        private val KEY_SETUP_DISMISSED = booleanPreferencesKey("setup_dismissed")
        private val KEY_SENSITIVITY = floatPreferencesKey("sensitivity")
        private val KEY_LOCK_UNKNOWN = booleanPreferencesKey("lock_unknown_face")
        private val KEY_LOCK_BLOCKED = booleanPreferencesKey("lock_blocked_person")
        private val KEY_MULTI_FACE = booleanPreferencesKey("lock_multi_face")
        private val KEY_NO_FACE = booleanPreferencesKey("lock_no_face")
        private val KEY_CAPTURE = booleanPreferencesKey("capture_intruders")
        private val KEY_LOW_LIGHT_ACTION = intPreferencesKey("low_light_action")
        private val KEY_VOICE_MODE = intPreferencesKey("voice_listening_mode")
        private val KEY_TEST_MODE = booleanPreferencesKey("test_mode")
        private val KEY_LOCKED_APPS = stringSetPreferencesKey("locked_apps")
        private val KEY_ACTIVE_PROFILE = stringPreferencesKey("active_profile")
        private val KEY_GUARD_ACTIVITY = stringPreferencesKey("guard_activity_log")
        private val KEY_EMAIL_ENABLED = booleanPreferencesKey("email_alerts_enabled")
        private val KEY_SMTP_HOST = stringPreferencesKey("smtp_host")
        private val KEY_SMTP_PORT = intPreferencesKey("smtp_port")
        private val KEY_SMTP_USER = stringPreferencesKey("smtp_user")
        private val KEY_SMTP_PASS = stringPreferencesKey("smtp_pass")
        private val KEY_ALERT_RECIPIENT = stringPreferencesKey("alert_recipient")
        private val KEY_SMS_ENABLED = booleanPreferencesKey("sms_alerts_enabled")
        private val KEY_TRUSTED_NUMBER = stringPreferencesKey("trusted_number")
        private val KEY_FIND_ENABLED = booleanPreferencesKey("find_enabled")
        private val KEY_FIND_KEYWORD = stringPreferencesKey("find_keyword")
        private val KEY_ARM_KEYWORD = stringPreferencesKey("arm_keyword")
        private val KEY_FIND_TRUSTED_ONLY = booleanPreferencesKey("find_trusted_only")
        private val KEY_PREMIUM_CACHED = booleanPreferencesKey("premium_cached")
        private val KEY_GUARD_DISCLOSURE = booleanPreferencesKey("guard_disclosure_accepted")
        private val KEY_WRONG_UNLOCK_THRESHOLD = intPreferencesKey("wrong_unlock_threshold")
        private val KEY_WRONG_UNLOCK_COUNT = intPreferencesKey("wrong_unlock_count")
        private val KEY_CRASH_LOG = booleanPreferencesKey("crash_log_enabled")
        private val KEY_APPEARANCE_RULES = booleanPreferencesKey("appearance_rules_enabled")
        private val KEY_IGNORE_HAIR = stringSetPreferencesKey("appearance_ignore_hair")
        private val KEY_IGNORE_EYES = stringSetPreferencesKey("appearance_ignore_eyes")
        private val KEY_IGNORE_SEX = stringSetPreferencesKey("appearance_ignore_sex")
        private val KEY_REQUIRE_LIVENESS = booleanPreferencesKey("require_liveness")
        private val KEY_PIN_SALT = stringPreferencesKey("pin_salt")
        private val KEY_PIN_REAL = stringPreferencesKey("pin_real")
        private val KEY_PIN_DECOY = stringPreferencesKey("pin_decoy")
        private val KEY_PIN_PANIC = stringPreferencesKey("pin_panic")
        private val KEY_PIN_ATTEMPTS = intPreferencesKey("pin_failed_attempts")
        private val KEY_PIN_LOCKED_UNTIL = longPreferencesKey("pin_locked_until")

        /**
         * The out-of-the-box find-my-phone keyword. It's public knowledge (it ships in the app),
         * so it is only honored while "trusted number only" is on; see [SmsReceiver].
         */
        const val DEFAULT_FIND_KEYWORD = "GUARDIA LOCATE"
        /** Out-of-the-box remote-arm keyword; like the locate keyword, honored trusted-number-only. */
        const val DEFAULT_ARM_KEYWORD = "GUARDIA PROTECT"

        /** Wrong PIN entries allowed before the lockout backoff begins. */
        const val LOCK_AFTER_ATTEMPTS = 5

        const val THEME_SYSTEM = 0
        const val THEME_DARK = 1
        const val THEME_LIGHT = 2
        const val MOTION_SYSTEM = 0
        const val MOTION_ON = 1
        const val MOTION_OFF = 2
        /** First lockout duration; each further failure doubles it up to [MAX_LOCK_MS]. */
        private const val BASE_LOCK_MS = 30_000L
        private const val MAX_LOCK_MS = 15 * 60_000L

        /**
         * How long PIN entry is barred after [attempts] consecutive wrong entries; 0 while the
         * user is still under [LOCK_AFTER_ATTEMPTS].
         *
         * Pulled out of [recordPinFailure] so the rule can be tested without a DataStore or a
         * clock. The arithmetic is unchanged: the shift is clamped before it is applied, because
         * `shl` on a Long wraps its count modulo 64 - past 64 failures an unclamped shift would
         * roll over and hand out a *shorter* lockout than the one before it.
         */
        internal fun lockoutDurationMs(attempts: Int): Long {
            if (attempts < LOCK_AFTER_ATTEMPTS) return 0L
            val over = attempts - LOCK_AFTER_ATTEMPTS
            return (BASE_LOCK_MS shl over.coerceIn(0, 20)).coerceAtMost(MAX_LOCK_MS)
        }
    }
}
