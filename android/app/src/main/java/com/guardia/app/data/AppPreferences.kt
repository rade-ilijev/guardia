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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App-level preferences: onboarding, the three PIN hashes, and core guarding settings.
 */
@Singleton
class AppPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
    private val crypto: com.guardia.app.core.security.CryptoManager,
) {
    private val ds = context.dataStore

    val onboarded: Flow<Boolean> = ds.data.map { it[KEY_ONBOARDED] ?: false }
    val guardingEnabled: Flow<Boolean> = ds.data.map { it[KEY_GUARDING_ENABLED] ?: false }
    val checkIntervalMinutes: Flow<Int> = ds.data.map { it[KEY_INTERVAL] ?: 2 }
    /** Responsiveness profile: 0 = Battery saver, 1 = Balanced, 2 = Max security. */
    val responsiveness: Flow<Int> = ds.data.map { it[KEY_RESPONSIVENESS] ?: 1 }
    /** Whether periodic background face checks run at all (vs. only on app-open triggers). */
    val intervalCheckEnabled: Flow<Boolean> = ds.data.map { it[KEY_INTERVAL_ENABLED] ?: true }
    /** Premium custom interval in seconds; 0 means use the responsiveness profile cadence. */
    val customIntervalSeconds: Flow<Int> = ds.data.map { it[KEY_CUSTOM_INTERVAL] ?: 0 }
    /** Premium: run a face check the instant the device is unlocked. */
    val firstCheckOnUnlock: Flow<Boolean> = ds.data.map { it[KEY_FIRST_CHECK] ?: false }
    /**
     * Premium: comma-separated gaps (seconds) between the early checks after unlock, e.g. "3,5,10".
     * The first check happens on unlock; each value is the delay until the next check. After the
     * ramp is exhausted, the steady interval (custom or responsiveness) takes over.
     */
    val checkRamp: Flow<String> = ds.data.map { it[KEY_CHECK_RAMP] ?: "" }
    /** Premium: trigger an extra face check when the device is shaken. */
    val shakeToCheck: Flow<Boolean> = ds.data.map { it[KEY_SHAKE_CHECK] ?: false }
    /** Per-app check screen style: 0 = Loading spinner, 1 = Blur, 2 = Freeze. */
    val appCheckStyle: Flow<Int> = ds.data.map { it[KEY_APP_CHECK_STYLE] ?: 0 }
    /**
     * When a guarded app's face check fails and an unauthorized person is actually seen, also lock
     * the whole device (not just close the app). If the owner simply wasn't verified (no face / too
     * dark), the app is only closed regardless of this setting.
     */
    val appLockOnFail: Flow<Boolean> = ds.data.map { it[KEY_APP_LOCK_ON_FAIL] ?: true }
    /** Premium: drive guarding from the device's location vs. user-defined safe zones. */
    val locationModeEnabled: Flow<Boolean> = ds.data.map { it[KEY_LOCATION_MODE] ?: false }
    /** Whether guarding runs when outside every safe zone (a "public" area). */
    val publicGuardEnabled: Flow<Boolean> = ds.data.map { it[KEY_PUBLIC_GUARD] ?: true }
    /** When true, public areas use the global Guarding & Triggers schedule instead of the fields below. */
    val publicUseDefault: Flow<Boolean> = ds.data.map { it[KEY_PUBLIC_USE_DEFAULT] ?: true }
    /** Check cadence in public areas: 0 = saver, 1 = balanced, 2 = max. */
    val publicResponsiveness: Flow<Int> = ds.data.map { it[KEY_PUBLIC_RESP] ?: 2 }
    /** Public custom interval (seconds); 0 = use the responsiveness profile. */
    val publicCustomIntervalSeconds: Flow<Int> = ds.data.map { it[KEY_PUBLIC_CUSTOM_INTERVAL] ?: 0 }
    /** Public: run a check the instant the device is unlocked. */
    val publicFirstCheckOnUnlock: Flow<Boolean> = ds.data.map { it[KEY_PUBLIC_FIRST_CHECK] ?: false }
    /** Public: comma-separated ramp gaps (seconds). */
    val publicCheckRamp: Flow<String> = ds.data.map { it[KEY_PUBLIC_RAMP] ?: "" }
    /** Public: trigger a check on shake. */
    val publicShakeToCheck: Flow<Boolean> = ds.data.map { it[KEY_PUBLIC_SHAKE] ?: false }
    /** Lock when no face is visible while in a public area. */
    val publicLockOnNoFace: Flow<Boolean> = ds.data.map { it[KEY_PUBLIC_NO_FACE] ?: false }
    /** Packages that trigger an immediate face check when they come to the foreground. */
    val triggerApps: Flow<Set<String>> = ds.data.map { it[KEY_TRIGGER_APPS] ?: emptySet() }
    /** Whether the user dismissed the first-run setup checklist on the dashboard. */
    val setupDismissed: Flow<Boolean> = ds.data.map { it[KEY_SETUP_DISMISSED] ?: false }
    val sensitivity: Flow<Float> = ds.data.map { it[KEY_SENSITIVITY] ?: 0.65f }
    /** Lock when an unrecognized face is seen (the core protection). */
    val lockOnUnknownFace: Flow<Boolean> = ds.data.map { it[KEY_LOCK_UNKNOWN] ?: true }
    /** Lock when a block-listed (look-alike) person is recognized. */
    val lockOnBlockedPerson: Flow<Boolean> = ds.data.map { it[KEY_LOCK_BLOCKED] ?: true }
    val lockOnMultipleFaces: Flow<Boolean> = ds.data.map { it[KEY_MULTI_FACE] ?: true }
    /** Lock the device when no face is visible for a short streak (e.g. camera turned away/covered). */
    val lockOnNoFace: Flow<Boolean> = ds.data.map { it[KEY_NO_FACE] ?: true }
    val captureIntruders: Flow<Boolean> = ds.data.map { it[KEY_CAPTURE] ?: true }
    /**
     * What to do during background guarding when it's too dark to recognize a face safely:
     * 0 = Ignore (never act in low light — safest for false alarms),
     * 1 = Brighten the screen and try again (front "flash" to light the face, then re-check),
     * 2 = Lock (treat persistent unverifiable darkness as a threat and lock).
     * Default is Brighten & retry so darkness no longer silently disables protection.
     */
    val lowLightAction: Flow<Int> = ds.data.map { it[KEY_LOW_LIGHT_ACTION] ?: 1 }
    val pinIsSet: Flow<Boolean> = ds.data.map { it[KEY_PIN_REAL] != null }
    /** Number of consecutive wrong PIN entries since the last success (drives the lockout backoff). */
    val pinFailedAttempts: Flow<Int> = ds.data.map { it[KEY_PIN_ATTEMPTS] ?: 0 }
    /** Epoch-ms until which PIN entry is locked out (0 = not locked). */
    val pinLockedUntil: Flow<Long> = ds.data.map { it[KEY_PIN_LOCKED_UNTIL] ?: 0L }
    /** 0 = Off, 1 = Always, 2 = Fallback only. */
    val voiceListeningMode: Flow<Int> = ds.data.map { it[KEY_VOICE_MODE] ?: 0 }
    /** Test mode: show feedback notifications instead of locking the device. */
    val testMode: Flow<Boolean> = ds.data.map { it[KEY_TEST_MODE] ?: false }
    /** Package names that require a PIN/face unlock when opened (App Lock). */
    val lockedApps: Flow<Set<String>> = ds.data.map { it[KEY_LOCKED_APPS] ?: emptySet() }
    /** Active guarding profile name (preset applied to the guarding settings above). */
    val activeProfile: Flow<String> = ds.data.map { it[KEY_ACTIVE_PROFILE] ?: "Default" }
    /** JSON-encoded rolling 24h log of the app's own face-check activity (for battery stats). */
    val guardActivityLog: Flow<String> = ds.data.map { it[KEY_GUARD_ACTIVITY] ?: "" }

    // --- Alerts & Recovery ---
    val emailAlertsEnabled: Flow<Boolean> = ds.data.map { it[KEY_EMAIL_ENABLED] ?: false }
    val smtpHost: Flow<String> = ds.data.map { it[KEY_SMTP_HOST] ?: "smtp.gmail.com" }
    val smtpPort: Flow<Int> = ds.data.map { it[KEY_SMTP_PORT] ?: 587 }
    val smtpUser: Flow<String> = ds.data.map { it[KEY_SMTP_USER] ?: "" }
    /**
     * SMTP password, encrypted at rest with the Android Keystore. Values saved before encryption was
     * added (plaintext) still read back via the decrypt fallback, and are re-encrypted on next save.
     */
    val smtpPassword: Flow<String> = ds.data.map { prefs ->
        val stored = prefs[KEY_SMTP_PASS] ?: return@map ""
        if (stored.isBlank()) "" else crypto.decryptString(stored) ?: stored
    }
    val alertRecipient: Flow<String> = ds.data.map { it[KEY_ALERT_RECIPIENT] ?: "" }
    val smsAlertsEnabled: Flow<Boolean> = ds.data.map { it[KEY_SMS_ENABLED] ?: false }
    val trustedNumber: Flow<String> = ds.data.map { it[KEY_TRUSTED_NUMBER] ?: "" }
    val findMyPhoneEnabled: Flow<Boolean> = ds.data.map { it[KEY_FIND_ENABLED] ?: false }
    val findKeyword: Flow<String> = ds.data.map { it[KEY_FIND_KEYWORD] ?: "GUARDIA LOCATE" }
    /**
     * When true (default), find-my-phone only reacts to the trusted number, so a leaked keyword
     * alone can't be used by a stranger to locate the device.
     */
    val findTrustedOnly: Flow<Boolean> = ds.data.map { it[KEY_FIND_TRUSTED_ONLY] ?: true }

    /** Last-known premium entitlement so paying users keep features while offline. */
    val premiumCached: Flow<Boolean> = ds.data.map { it[KEY_PREMIUM_CACHED] ?: false }
    /** Whether the prominent background-camera disclosure was accepted (required before guarding). */
    val guardDisclosureAccepted: Flow<Boolean> = ds.data.map { it[KEY_GUARD_DISCLOSURE] ?: false }
    /** Failed device unlocks before an intruder selfie is captured (1 = every failure). */
    val wrongUnlockThreshold: Flow<Int> = ds.data.map { it[KEY_WRONG_UNLOCK_THRESHOLD] ?: 1 }
    /** Opt-in local crash log (written to app-private storage only, never uploaded). */
    val crashLogEnabled: Flow<Boolean> = ds.data.map { it[KEY_CRASH_LOG] ?: false }

    suspend fun setOnboarded(value: Boolean) = ds.edit { it[KEY_ONBOARDED] = value }
    suspend fun setGuardingEnabled(value: Boolean) = ds.edit { it[KEY_GUARDING_ENABLED] = value }
    suspend fun setInterval(min: Int) = ds.edit { it[KEY_INTERVAL] = min }
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
    suspend fun setSmtpPassword(value: String) = ds.edit {
        it[KEY_SMTP_PASS] = if (value.isBlank()) "" else crypto.encryptString(value)
    }
    suspend fun setAlertRecipient(value: String) = ds.edit { it[KEY_ALERT_RECIPIENT] = value }
    suspend fun setSmsAlertsEnabled(value: Boolean) = ds.edit { it[KEY_SMS_ENABLED] = value }
    suspend fun setTrustedNumber(value: String) = ds.edit { it[KEY_TRUSTED_NUMBER] = value }
    suspend fun setFindMyPhoneEnabled(value: Boolean) = ds.edit { it[KEY_FIND_ENABLED] = value }
    suspend fun setFindKeyword(value: String) = ds.edit { it[KEY_FIND_KEYWORD] = value }
    suspend fun setFindTrustedOnly(value: Boolean) = ds.edit { it[KEY_FIND_TRUSTED_ONLY] = value }

    suspend fun setPremiumCached(value: Boolean) = ds.edit { it[KEY_PREMIUM_CACHED] = value }
    suspend fun setGuardDisclosureAccepted(value: Boolean) = ds.edit { it[KEY_GUARD_DISCLOSURE] = value }
    suspend fun setWrongUnlockThreshold(value: Int) = ds.edit { it[KEY_WRONG_UNLOCK_THRESHOLD] = value }
    suspend fun setCrashLogEnabled(value: Boolean) = ds.edit { it[KEY_CRASH_LOG] = value }

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
     * Records a wrong PIN entry. After [LOCK_AFTER_ATTEMPTS] failures, locks PIN entry for an
     * exponentially growing window (capped at [MAX_LOCK_MS]). Returns the new locked-until epoch-ms
     * (0 if not yet locked out).
     */
    suspend fun recordPinFailure(): Long {
        var lockedUntil = 0L
        ds.edit { prefs ->
            val attempts = (prefs[KEY_PIN_ATTEMPTS] ?: 0) + 1
            prefs[KEY_PIN_ATTEMPTS] = attempts
            if (attempts >= LOCK_AFTER_ATTEMPTS) {
                val over = attempts - LOCK_AFTER_ATTEMPTS
                val duration = (BASE_LOCK_MS shl over.coerceIn(0, 20)).coerceAtMost(MAX_LOCK_MS)
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
     */
    suspend fun verifyPin(pin: String): PinType? {
        val prefs = ds.data.first()
        val salt = prefs[KEY_PIN_SALT] ?: return null
        fun matches(stored: String?) = stored != null && PinManager.verify(pin, salt, stored)
        return when {
            matches(prefs[KEY_PIN_REAL]) -> PinType.REAL
            matches(prefs[KEY_PIN_DECOY]) -> PinType.DECOY
            matches(prefs[KEY_PIN_PANIC]) -> PinType.PANIC
            else -> null
        }
    }

    companion object {
        private val KEY_ONBOARDED = booleanPreferencesKey("onboarded")
        private val KEY_GUARDING_ENABLED = booleanPreferencesKey("guarding_enabled")
        private val KEY_INTERVAL = intPreferencesKey("check_interval_min")
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
        private val KEY_FIND_TRUSTED_ONLY = booleanPreferencesKey("find_trusted_only")
        private val KEY_PREMIUM_CACHED = booleanPreferencesKey("premium_cached")
        private val KEY_GUARD_DISCLOSURE = booleanPreferencesKey("guard_disclosure_accepted")
        private val KEY_WRONG_UNLOCK_THRESHOLD = intPreferencesKey("wrong_unlock_threshold")
        private val KEY_WRONG_UNLOCK_COUNT = intPreferencesKey("wrong_unlock_count")
        private val KEY_CRASH_LOG = booleanPreferencesKey("crash_log_enabled")
        private val KEY_PIN_SALT = stringPreferencesKey("pin_salt")
        private val KEY_PIN_REAL = stringPreferencesKey("pin_real")
        private val KEY_PIN_DECOY = stringPreferencesKey("pin_decoy")
        private val KEY_PIN_PANIC = stringPreferencesKey("pin_panic")
        private val KEY_PIN_ATTEMPTS = intPreferencesKey("pin_failed_attempts")
        private val KEY_PIN_LOCKED_UNTIL = longPreferencesKey("pin_locked_until")

        /** Wrong PIN entries allowed before the lockout backoff begins. */
        const val LOCK_AFTER_ATTEMPTS = 5
        /** First lockout duration; each further failure doubles it up to [MAX_LOCK_MS]. */
        private const val BASE_LOCK_MS = 30_000L
        private const val MAX_LOCK_MS = 15 * 60_000L
    }
}
