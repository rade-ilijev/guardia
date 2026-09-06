package com.guardia.app.core.guard

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.guardia.app.MainActivity
import com.guardia.app.R
import com.guardia.app.core.ml.AnalysisConfig
import com.guardia.app.core.ml.BitmapUtils
import com.guardia.app.core.ml.FacePipeline
import com.guardia.app.core.system.TestNotifier
import com.guardia.app.core.voice.VoiceController
import com.guardia.app.data.AppPreferences
import dagger.hilt.android.AndroidEntryPoint
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Persistent foreground service running the guard loop: a headless CameraX
 * ImageAnalysis feed, gated by [CaptureGate] for battery, analyzed by [FacePipeline],
 * with intruder response via [Responder].
 */
@AndroidEntryPoint
class GuardService : LifecycleService() {

    @Inject lateinit var facePipeline: FacePipeline
    @Inject lateinit var faceEmbedder: com.guardia.app.core.ml.FaceEmbedder
    @Inject lateinit var rulesEngine: RulesEngine
    @Inject lateinit var responder: Responder
    @Inject lateinit var captureGate: CaptureGate
    @Inject lateinit var prefs: AppPreferences
    @Inject lateinit var peopleRepository: com.guardia.app.data.PeopleRepository
    @Inject lateinit var eventsRepository: com.guardia.app.data.EventsRepository
    @Inject lateinit var intruderRepository: com.guardia.app.data.IntruderRepository
    @Inject lateinit var entitlements: com.guardia.app.core.billing.EntitlementManager
    @Inject lateinit var appTriggerManager: AppTriggerManager
    @Inject lateinit var locationZoneManager: com.guardia.app.core.location.LocationZoneManager
    @Inject lateinit var guardActivity: GuardActivityTracker

    private val analysisExecutor = Executors.newSingleThreadExecutor()
    private val analyzing = AtomicBoolean(false)
    private var cameraProvider: ProcessCameraProvider? = null
    @Volatile private var cameraTracked = false

    /** True while the camera is open for a single check; gates the scheduler from re-opening it. */
    private val capturing = AtomicBoolean(false)
    /** Elapsed-realtime stamp of when the current capture opened the camera (for battery stats). */
    @Volatile private var captureStartedAt = 0L
    private var captureJob: kotlinx.coroutines.Job? = null
    private val cameraMainExecutor by lazy { ContextCompat.getMainExecutor(this) }

    @Volatile private var sensitivity = 0.5f
    @Volatile private var captureIntruders = true
    @Volatile private var voiceMode = 0
    @Volatile private var testMode = false
    /** Low-light policy: 0 = ignore, 1 = brighten & retry, 2 = lock. */
    @Volatile private var lowLightAction = 1
    /** Low-light escalation phase: 0 = none, 1 = brightness boost (transparent), 2 = white flood. */
    private var lowLightPhase = 0
    private var lowLightPhaseAt = 0L
    @Volatile private var lastLowLightEpisodeAt = 0L
    private var lastLowLightTestNotifyAt = 0L
    /** When > 0 and reached, the scheduler grabs one frame now (used to re-check right after brightening). */
    @Volatile private var forceCaptureAt = 0L
    @Volatile private var lockOnUnknown = true
    @Volatile private var lockOnBlocked = true
    @Volatile private var lockOnMultiple = true
    @Volatile private var userLockOnNoFace = false

    // Appearance rules (experimental): suppress a lock for an unrecognized person whose estimated
    // look matches a user-selected bucket. Never applied to blocked/multi outcomes.
    @Volatile private var appearanceRulesOn = false
    @Volatile private var ignoreHair: Set<String> = emptySet()
    @Volatile private var ignoreEyes: Set<String> = emptySet()
    @Volatile private var ignoreSex: Set<String> = emptySet()
    private var noFaceStreak = 0
    private var voiceArmed = false
    private var lastRecognitionRecordAt = 0L

    // Premium scheduling inputs (gated by entitlement before they reach the capture gate).
    @Volatile private var firstCheckOnUnlock = false
    @Volatile private var checkRamp = ""
    @Volatile private var shakeToCheck = false
    @Volatile private var customInterval = 0

    // Cadence inputs that location mode can override.
    @Volatile private var userResponsiveness = 1
    @Volatile private var userIntervalEnabled = true
    @Volatile private var locationMode = false
    @Volatile private var locationPolicy =
        com.guardia.app.core.location.ZonePolicy(true, true, 2, 0, false, "", false, false, "", false)
    /** Effective lock-on-no-face after resolving location/default/custom; refreshed by [applyAll]. */
    @Volatile private var resolvedLockOnNoFace = false
    /** True once we are foreground; guards re-promotion when the location FGS type changes. */
    @Volatile private var isForeground = false
    /** Whether the current foreground type already includes the location subtype. */
    @Volatile private var fgsHasLocation = false

    /** Drives the poll loop's cadence: while the screen is off we can never capture, so idle slowly. */
    @Volatile private var screenInteractive = true

    /** Effective shake-to-check after premium/location resolution; keeps the poll tight (see [nextSchedulerDelay]). */
    @Volatile private var effectiveShake = false

    // Trusted Wi-Fi: while connected to a network the user trusts, periodic checks pause
    // (app-open triggers still run). Refreshed on the maintenance tick and on screen-on.
    @Volatile private var trustedWifiOn = false
    @Volatile private var trustedSsids: Set<String> = emptySet()
    @Volatile private var onTrustedWifi = false

    /** Re-evaluates the trusted-Wi-Fi state; re-plans the schedule when it flips. */
    private fun refreshTrustedWifi() {
        val now = trustedWifiOn &&
            com.guardia.app.core.system.WifiTrust.onTrustedNetwork(this, trustedSsids)
        if (now != onTrustedWifi) {
            onTrustedWifi = now
            GuardController.relaxedOnTrustedWifi.value = now
            applyAll()
        }
    }

    /** Wakes the scheduler out of a long sleep when an event makes an earlier check possible/needed. */
    private val schedulerNudge =
        kotlinx.coroutines.channels.Channel<Unit>(kotlinx.coroutines.channels.Channel.CONFLATED)

    private fun nudgeScheduler() { schedulerNudge.trySend(Unit) }

    /**
     * Aborts the capture currently in flight, if any. Set while the camera is open and cleared the
     * moment it is released, so a preempt that arrives between captures is a no-op.
     */
    @Volatile private var abortCapture: (() -> Unit)? = null

    /** Requests a forced capture at [at] (elapsedRealtime) and wakes the scheduler so it isn't missed. */
    private fun scheduleForcedCapture(at: Long) {
        forceCaptureAt = at
        nudgeScheduler()
    }

    /** Reacts to lock/unlock so the premium unlock ramp can re-arm each session. */
    private val screenReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON -> { screenInteractive = true; refreshTrustedWifi() }
                Intent.ACTION_USER_PRESENT -> { screenInteractive = true; captureGate.onUnlocked() }
                Intent.ACTION_SCREEN_OFF -> {
                    screenInteractive = false
                    captureGate.onScreenOff()
                    appTriggerManager.onScreenOff()
                }
            }
            // Screen state changes both the sleep budget and what's due (first-check-on-unlock).
            nudgeScheduler()
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        lifecycleScope.launch { prefs.sensitivity.collectLatest { sensitivity = it } }
        lifecycleScope.launch { prefs.captureIntruders.collectLatest { captureIntruders = it } }
        lifecycleScope.launch { prefs.lowLightAction.collectLatest { lowLightAction = it } }
        // Reconcile the voice service against the mode here (not just in onStartCommand): on a cold
        // start the first DataStore emission lands *after* onStartCommand, so arming "Always" mode
        // only there silently missed reboots; and a live mode change should arm/disarm immediately.
        lifecycleScope.launch {
            prefs.voiceListeningMode.collectLatest { mode ->
                voiceMode = mode
                if (mode == VOICE_ALWAYS && !voiceArmed) {
                    VoiceController.start(this@GuardService)
                    voiceArmed = true
                } else if (mode != VOICE_ALWAYS && voiceArmed) {
                    // Fallback mode re-arms on its own via the no-face streak.
                    VoiceController.stop(this@GuardService)
                    voiceArmed = false
                }
            }
        }
        lifecycleScope.launch { prefs.testMode.collectLatest { testMode = it } }
        lifecycleScope.launch { prefs.lockOnUnknownFace.collectLatest { lockOnUnknown = it } }
        lifecycleScope.launch { prefs.lockOnBlockedPerson.collectLatest { lockOnBlocked = it } }
        lifecycleScope.launch { prefs.lockOnMultipleFaces.collectLatest { lockOnMultiple = it } }
        lifecycleScope.launch { prefs.lockOnNoFace.collectLatest { userLockOnNoFace = it; applyAll() } }
        lifecycleScope.launch { prefs.appearanceRulesEnabled.collectLatest { appearanceRulesOn = it } }
        lifecycleScope.launch { prefs.ignoreHairColors.collectLatest { ignoreHair = it } }
        lifecycleScope.launch { prefs.ignoreEyeTones.collectLatest { ignoreEyes = it } }
        lifecycleScope.launch { prefs.ignoreSexes.collectLatest { ignoreSex = it } }
        lifecycleScope.launch { prefs.responsiveness.collectLatest { userResponsiveness = it; applyAll() } }
        lifecycleScope.launch { prefs.intervalCheckEnabled.collectLatest { userIntervalEnabled = it; applyAll() } }
        lifecycleScope.launch { prefs.customIntervalSeconds.collectLatest { customInterval = it; applyAll() } }
        lifecycleScope.launch { prefs.firstCheckOnUnlock.collectLatest { firstCheckOnUnlock = it; applyAll() } }
        lifecycleScope.launch { prefs.checkRamp.collectLatest { checkRamp = it; applyAll() } }
        lifecycleScope.launch { prefs.shakeToCheck.collectLatest { shakeToCheck = it; applyAll() } }
        lifecycleScope.launch { prefs.locationModeEnabled.collectLatest { locationMode = it; applyAll() } }
        lifecycleScope.launch { prefs.trustedWifiEnabled.collectLatest { trustedWifiOn = it; refreshTrustedWifi(); applyAll() } }
        lifecycleScope.launch { prefs.trustedSsids.collectLatest { trustedSsids = it; refreshTrustedWifi(); applyAll() } }
        lifecycleScope.launch { locationZoneManager.policy.collectLatest { locationPolicy = it; applyAll() } }
        lifecycleScope.launch { entitlements.premium.collectLatest { applyAll() } }

        ContextCompat.registerReceiver(
            this,
            screenReceiver,
            android.content.IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
                addAction(Intent.ACTION_SCREEN_OFF)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    private fun locationActive() = entitlements.isPremium && locationMode

    /** Lock-on-no-face after resolving location/default/custom (see [applyAll]). */
    private fun effectiveLockOnNoFace() = resolvedLockOnNoFace

    /** A fully-resolved capture schedule pushed to the [CaptureGate]. */
    private data class Schedule(
        val intervalEnabled: Boolean,
        val responsiveness: Int,
        val customInterval: Int,
        val firstCheck: Boolean,
        val ramp: List<Int>,
        val shake: Boolean,
        val lockOnNoFace: Boolean,
    )

    /** The schedule from the user's global Guarding & Triggers settings (premium extras gated). */
    private fun globalSchedule(premium: Boolean) = Schedule(
        intervalEnabled = userIntervalEnabled,
        responsiveness = userResponsiveness,
        customInterval = if (premium) customInterval else 0,
        firstCheck = premium && firstCheckOnUnlock,
        ramp = if (premium) parseRamp(checkRamp) else emptyList(),
        shake = premium && shakeToCheck,
        lockOnNoFace = userLockOnNoFace,
    )

    /**
     * Reconciles every scheduling input and pushes the result to the capture gate. Under location
     * mode (premium) the current place decides: it can be off, follow the global "default" schedule,
     * or use its own custom schedule (same options as Guarding & Triggers). Otherwise the user's
     * global settings apply, with premium extras zeroed out for free users.
     */
    private fun applyAll() {
        val premium = entitlements.isPremium
        val locActive = locationActive()

        val resolved = if (locActive) {
            val p = locationPolicy
            when {
                !p.guardEnabled -> Schedule(false, userResponsiveness, 0, false, emptyList(), false, false)
                p.useDefault -> globalSchedule(premium = true) // location mode is premium-only
                else -> Schedule(
                    intervalEnabled = true,
                    responsiveness = p.responsiveness,
                    customInterval = p.customIntervalSeconds,
                    firstCheck = p.firstCheckOnUnlock,
                    ramp = parseRamp(p.checkRamp),
                    shake = p.shakeToCheck,
                    lockOnNoFace = p.lockOnNoFace,
                )
            }
        } else {
            globalSchedule(premium)
        }
        // Trusted Wi-Fi overrides any schedule: on a network the user trusts, ALL automatic checks
        // pause — periodic, first-on-unlock, the unlock ramp, and shake (a shake fires through the
        // gate's immediate path, so leaving it enabled would leak checks past "relaxed"). Only
        // app-open face checks remain, because a guarded app is guarded anywhere.
        val s = if (onTrustedWifi) {
            resolved.copy(intervalEnabled = false, firstCheck = false, shake = false, ramp = emptyList())
        } else resolved

        captureGate.setResponsiveness(s.responsiveness)
        captureGate.setIntervalEnabled(s.intervalEnabled)
        captureGate.setFirstCheckOnUnlock(s.firstCheck)
        captureGate.setShakeEnabled(s.shake)
        captureGate.setCustomIntervalSeconds(s.customInterval)
        captureGate.setRamp(s.ramp)
        resolvedLockOnNoFace = s.lockOnNoFace
        effectiveShake = s.shake
        // The schedule changed, so any in-flight long sleep may now be wrong — re-plan it.
        nudgeScheduler()

        // Manage location sampling lifecycle.
        if (locActive) locationZoneManager.start() else locationZoneManager.stop()

        // If location mode just turned on/off, re-promote so the foreground service type matches
        // what we're actually doing (accessing location from an FGS requires the location subtype).
        val wantLocation = locActive && hasLocationPermission()
        if (isForeground && wantLocation != fgsHasLocation) startAsForeground()
    }

    private fun parseRamp(value: String): List<Int> =
        value.split(',').mapNotNull { it.trim().toIntOrNull() }.filter { it > 0 }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        // startForeground can throw ForegroundServiceStartNotAllowedException when the OS launched
        // us from a restricted context (e.g. BOOT_COMPLETED on Android 12+). If we can't legally
        // become a foreground service right now, stop cleanly instead of crashing; the next unlock
        // (or the user opening the app) will re-arm guarding.
        if (!startAsForeground()) {
            GuardController.onServiceState(GuardState.STOPPED)
            stopSelf()
            return START_NOT_STICKY
        }
        rulesEngine.reset()
        captureGate.start()
        // Liveness bookkeeping for the watchdog: mark this run as live-and-not-cleanly-stopped so
        // an OEM battery kill (which skips onDestroy) is detectable on the next app open.
        lifecycleScope.launch {
            runCatching {
                prefs.setGuardStoppedCleanly(false)
                prefs.setGuardHeartbeatAt(System.currentTimeMillis())
            }
        }
        if (hasCameraPermission()) startCaptureScheduler()
        if (voiceMode == VOICE_ALWAYS && !voiceArmed) {
            VoiceController.start(this)
            voiceArmed = true
        }
        // A security app must not degrade silently: if the face model failed to load, recognition
        // runs on the weak pixel-descriptor fallback and the owner needs to know.
        if (!faceEmbedder.usingModel) notifyDegradedRecognition()
        GuardController.onServiceState(GuardState.PROTECTED)
        // Same principle, one layer up: App Lock and per-app checks need Android's accessibility
        // grant, and an app update revokes it. Settling the state here means a guard started from
        // the tile, the widget or a reboot reports the truth without waiting for the dashboard.
        checkAccessibilityPrerequisite()
        return START_STICKY
    }

    /**
     * Moves the guard to [GuardState.NEEDS_ATTENTION] and warns if the features that need the
     * accessibility service are configured but the grant is gone. Cheap enough to run on every
     * start; the state only changes when the answer does.
     */
    private fun checkAccessibilityPrerequisite() {
        lifecycleScope.launch {
            val depends = prefs.lockedApps.first().isNotEmpty() || prefs.triggerApps.first().isNotEmpty()
            GuardController.refreshPrerequisites(this@GuardService, depends)
            if (depends && !com.guardia.app.core.system.AccessibilityAccess.isEnabled(this@GuardService)) {
                com.guardia.app.core.system.ProtectionWarning.showAccessibilityRevoked(this@GuardService)
            }
        }
    }

    override fun onDestroy() {
        // A normal stop reaches onDestroy; a battery-manager kill doesn't. Record the clean stop
        // on a scope that outlives this service so the watchdog doesn't cry wolf. (If the write
        // races a process death, the heartbeat staleness check still bounds the false-positive.)
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            runCatching { prefs.setGuardStoppedCleanly(true) }
            // The activity log is kept in memory while guarding and only written periodically;
            // a clean stop is the moment to make sure the last checks are on disk.
            runCatching { guardActivity.flush() }
        }
        CameraLease.setGuard(preempt = null, free = null)
        abortCapture = null
        runCatching { unregisterReceiver(screenReceiver) }
        runCatching { com.guardia.app.core.system.BrightnessOverlay.hide(this) }
        locationZoneManager.stop()
        captureGate.stop()
        captureJob?.cancel()
        runCatching { cameraProvider?.unbindAll() }
        if (voiceArmed) {
            VoiceController.stop(this)
            voiceArmed = false
        }
        analysisExecutor.shutdown()
        isForeground = false
        GuardController.relaxedOnTrustedWifi.value = false
        GuardController.onServiceState(GuardState.STOPPED)
        super.onDestroy()
    }

    /**
     * Instead of holding the camera open continuously (which keeps the OS privacy indicator lit),
     * we poll [CaptureGate] on a light timer and only open the camera for the brief moment of an
     * actual check, releasing it immediately afterward so the indicator turns off between checks.
     */
    private fun startCaptureScheduler() {
        // Anything the user is waiting on — a per-app face check, the enrollment preview — takes
        // the camera off us mid-capture rather than racing us for it, and we take our postponed
        // look as soon as it hands the camera back.
        CameraLease.setGuard(
            preempt = { abortCapture?.invoke() },
            free = {
                scheduleForcedCapture(
                    android.os.SystemClock.elapsedRealtime() + CAMERA_HANDBACK_DELAY_MS,
                )
            },
        )
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({ cameraProvider = runCatching { future.get() }.getOrNull() }, cameraMainExecutor)

        captureJob = lifecycleScope.launch {
            while (isActive) {
                // Sleep as close to the next due check as safely possible instead of ticking on a
                // fixed short interval; a nudge (unlock, schedule change, forced re-check) cuts the
                // sleep short so nothing waits on a stale plan.
                kotlinx.coroutines.withTimeoutOrNull(nextSchedulerDelay()) { schedulerNudge.receive() }
                maybeRunMaintenance()
                // A low-light re-check can request an immediate capture (just after we brighten the
                // screen) instead of waiting for the next scheduled interval.
                val forced = forceCaptureAt in 1..android.os.SystemClock.elapsedRealtime()
                // Yield the front camera while a per-app check is verifying, or while a screen in
                // front of the user is using it (enrollment), so nothing fights over the single
                // process-wide CameraX provider. A held lease skips the check rather than delaying
                // it: the owner is demonstrably at the phone.
                if (!capturing.get() && !appTriggerManager.checkInProgress && !CameraLease.isHeld &&
                    (forced || captureGate.shouldCapture())
                ) {
                    if (forced) forceCaptureAt = 0L
                    captureOnce()
                }
            }
        }
    }

    /** Wall-clock of the last maintenance pass (heartbeat + digest); throttles DataStore writes. */
    private var lastMaintenanceAt = 0L

    /**
     * Once a minute: write the liveness heartbeat (so a battery-manager kill is detectable on the
     * next app open — see GuardWatchdog) and post the weekly protection digest when it's due.
     */
    private fun maybeRunMaintenance() {
        val now = System.currentTimeMillis()
        if (now - lastMaintenanceAt < MAINTENANCE_INTERVAL_MS) return
        lastMaintenanceAt = now
        lifecycleScope.launch {
            runCatching { prefs.setGuardHeartbeatAt(now) }
            runCatching { maybePostWeeklyDigest(now) }
            // Evidence retention expires on the clock too. Same housekeeping tick, same reason:
            // there is no event that means "this photo is now old enough to delete".
            runCatching {
                val days = prefs.evidenceRetentionDays.first()
                val removed = intruderRepository.purgeOlderThan(days, now)
                if (removed > 0) {
                    eventsRepository.log(
                        com.guardia.app.domain.model.GuardEvent.Type.INFO,
                        "Deleted $removed intruder photo${if (removed == 1) "" else "s"} older than $days days",
                    )
                }
            }
            // Guest passes expire on the clock, not on an event — sweep them here.
            runCatching {
                if (peopleRepository.purgeExpiredGuests(now) > 0) {
                    eventsRepository.log(
                        com.guardia.app.domain.model.GuardEvent.Type.INFO,
                        "Guest pass expired — guest removed",
                    )
                }
            }
        }
        // Networks change without broadcasts we listen for; the minute tick is fresh enough.
        runCatching { refreshTrustedWifi() }
    }

    private suspend fun maybePostWeeklyDigest(now: Long) {
        if (!prefs.weeklyDigestEnabled.first()) return
        val last = prefs.lastDigestAt.first()
        if (last == 0L) {
            // First run starts the weekly clock; the first digest arrives a week from now.
            prefs.setLastDigestAt(now)
            return
        }
        if (now - last < DIGEST_PERIOD_MS) return
        prefs.setLastDigestAt(now)

        val events = eventsRepository.events.first().filter { it.timestamp >= last }
        val intruderLocks = events.count { it.type == com.guardia.app.domain.model.GuardEvent.Type.INTRUDER_LOCK }
        val wrongUnlocks = events.count { it.type == com.guardia.app.domain.model.GuardEvent.Type.WRONG_UNLOCK }
        val text = when {
            intruderLocks == 0 && wrongUnlocks == 0 -> "All clear — no intruder events this week."
            else -> buildString {
                if (intruderLocks > 0) append("$intruderLocks intruder lock${if (intruderLocks == 1) "" else "s"}")
                if (wrongUnlocks > 0) {
                    if (isNotEmpty()) append(" · ")
                    append("$wrongUnlocks wrong unlock${if (wrongUnlocks == 1) "" else "s"}")
                }
                append(" — details in Activity.")
            }
        }
        val nm = getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(DIGEST_CHANNEL_ID, "Weekly summary", NotificationManager.IMPORTANCE_LOW),
            )
        }
        val contentIntent = android.app.PendingIntent.getActivity(
            this, 2, Intent(this, MainActivity::class.java), android.app.PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, DIGEST_CHANNEL_ID)
            .setContentTitle("Your week with Guardia")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setSmallIcon(R.drawable.ic_stat_guardia)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()
        runCatching { nm.notify(DIGEST_NOTIFICATION_ID, notification) }
    }

    /**
     * How long the scheduler may sleep before re-evaluating. Screen off: a slow idle tick. Screen
     * on: until the capture gate's next due check, clamped so an async trigger (app-open immediate
     * request, keyguard state change) is still noticed within [MAX_POLL_INTERVAL_MS] — the gate's
     * answer is advisory, [CaptureGate.shouldCapture] stays the source of truth. Shake-to-check
     * flags captures from the sensor thread at any moment, so it keeps the legacy tight poll. A
     * pending forced re-check (low-light retry / rapid confirm) has its own earlier deadline.
     */
    private fun nextSchedulerDelay(): Long {
        if (!screenInteractive) return IDLE_POLL_INTERVAL_MS
        val forceIn = if (forceCaptureAt > 0L) {
            (forceCaptureAt - android.os.SystemClock.elapsedRealtime()).coerceAtLeast(MIN_POLL_INTERVAL_MS)
        } else Long.MAX_VALUE
        val gateIn = if (effectiveShake) POLL_INTERVAL_MS
        else captureGate.nextDueDelayMs().coerceIn(MIN_POLL_INTERVAL_MS, MAX_POLL_INTERVAL_MS)
        return minOf(forceIn, gateIn)
    }

    /** Opens the camera, grabs a single (warmed-up) frame, then releases the camera right away. */
    private fun captureOnce() {
        val provider = cameraProvider ?: return
        if (!capturing.compareAndSet(false, true)) return
        captureStartedAt = android.os.SystemClock.elapsedRealtime()

        val analysis = AnalysisConfig.builder().build()
        val handled = AtomicBoolean(false)
        val frameIndex = AtomicInteger(0)
        abortCapture = { if (handled.compareAndSet(false, true)) releaseCamera(provider, analysis) }

        analysis.setAnalyzer(analysisExecutor) { proxy ->
            val rotation = proxy.imageInfo.rotationDegrees
            // Skip the first frames so auto-exposure can settle before we read one.
            if (frameIndex.incrementAndGet() <= WARMUP_FRAMES) {
                proxy.close()
                return@setAnalyzer
            }
            val bmp = runCatching { proxy.toBitmap() }.getOrNull()
            proxy.close()
            if (bmp != null && handled.compareAndSet(false, true)) {
                releaseCamera(provider, analysis)
                onFrame(bmp, rotation)
            }
        }

        cameraMainExecutor.execute {
            runCatching {
                provider.unbindAll()
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, analysis)
                com.guardia.app.core.system.GuardiaCameraMic.enterCamera()
                cameraTracked = true
            }.onFailure { capturing.set(false) }
        }

        // Safety net: if no usable frame arrives, still release the camera and reset the gate.
        lifecycleScope.launch {
            delay(CAPTURE_TIMEOUT_MS)
            if (handled.compareAndSet(false, true)) {
                releaseCamera(provider, analysis)
            }
        }
    }

    private fun releaseCamera(provider: ProcessCameraProvider, analysis: ImageAnalysis) {
        abortCapture = null
        val onMs = android.os.SystemClock.elapsedRealtime() - captureStartedAt
        if (captureStartedAt > 0L && onMs in 1..(CAPTURE_TIMEOUT_MS + 1000)) {
            guardActivity.recordCheck(onMs)
        }
        cameraMainExecutor.execute {
            runCatching {
                analysis.clearAnalyzer()
                provider.unbind(analysis)
            }
            if (cameraTracked) { com.guardia.app.core.system.GuardiaCameraMic.exitCamera(); cameraTracked = false }
            capturing.set(false)
        }
    }

    /** True when a confident appearance estimate matches a bucket the user chose not to lock for. */
    private fun appearanceIgnored(look: com.guardia.app.core.ml.AppearanceAnalyzer.Appearance?): Boolean {
        if (look == null) return false
        // Sex comes from a dedicated classifier that only reports a confident value, so honor it
        // regardless of the coarse hair/eye confidence.
        if (look.sex != com.guardia.app.core.ml.AppearanceAnalyzer.Sex.UNKNOWN && ignoreSex.contains(look.sex.name)) return true
        if (look.confidence < com.guardia.app.core.ml.AppearanceAnalyzer.MIN_ACTIONABLE_CONFIDENCE) return false
        return ignoreHair.contains(look.hair.name) || ignoreEyes.contains(look.eyes.name)
    }

    /** A frame that would lock if it recurs — used to trigger an immediate confirming re-check. */
    private fun isSuspicious(analysis: FacePipeline.Analysis, triggers: RulesEngine.Triggers): Boolean =
        when (analysis.outcome) {
            FacePipeline.Outcome.NO_MATCH -> triggers.unknownFace
            FacePipeline.Outcome.BLOCKED -> triggers.blockedPerson
            FacePipeline.Outcome.MULTIPLE_FACES -> triggers.multipleFaces
            FacePipeline.Outcome.NO_FACE -> triggers.noFace
            else -> false
        }

    private fun onFrame(bitmap: android.graphics.Bitmap, rotation: Int) {
        if (!analyzing.compareAndSet(false, true)) return
        lifecycleScope.launch {
            // Rotate to upright exactly once; the pipeline, the low-light path, and any evidence
            // JPEG all share this bitmap instead of each producing their own rotated copy.
            // (When rotation is 0 this returns the source bitmap itself, so the two may be the
            // same object — the cleanup below accounts for that.)
            //
            // On Dispatchers.Default, deliberately. lifecycleScope is Dispatchers.Main.immediate,
            // so the whole of a check — a full-frame ARGB rotation, ML Kit detection, an embedding
            // per enrolled model version, the appearance estimate — was running on the UI thread,
            // every few seconds, for as long as the guard was on. Everything after the analysis
            // touches Android objects that want the main thread (overlays, the responder, the
            // voice controller), so only the compute moves.
            val upright = withContext(Dispatchers.Default) { BitmapUtils.rotate(bitmap, rotation) }
            try {
                val analysis = withContext(Dispatchers.Default) {
                    facePipeline.analyze(upright, 0, sensitivity)
                }
                // Dark / no-face fallback: arm the voice safeword so the owner can stop guarding by voice.
                if (analysis.outcome == FacePipeline.Outcome.NO_FACE) {
                    noFaceStreak++
                    if (voiceMode == VOICE_FALLBACK && noFaceStreak >= 3 && !voiceArmed) {
                        VoiceController.start(this@GuardService)
                        voiceArmed = true
                    }
                } else {
                    noFaceStreak = 0
                }
                if (testMode) notifyTestResult(analysis)
                // Record per-person presence stats (throttled to once per 30s to spare battery/DB).
                // Applies to both recognized owners and detected block-listed people.
                val recognizedSomeone = analysis.outcome == FacePipeline.Outcome.MATCH ||
                    analysis.outcome == FacePipeline.Outcome.BLOCKED
                if (recognizedSomeone && analysis.personId != null) {
                    val now = System.currentTimeMillis()
                    if (now - lastRecognitionRecordAt > 30_000L) {
                        lastRecognitionRecordAt = now
                        runCatching { peopleRepository.recordRecognition(analysis.personId, analysis.similarity) }
                    }
                }
                val triggers = RulesEngine.Triggers(
                    unknownFace = lockOnUnknown,
                    blockedPerson = lockOnBlocked,
                    multipleFaces = lockOnMultiple,
                    noFace = effectiveLockOnNoFace(),
                )
                // Optional appearance relax: for an *unrecognized* face (never blocked/multi) whose
                // confident appearance estimate matches a bucket the user chose to ignore, treat the
                // frame as "can't decide" so it won't lock. Block list and multi-face always win.
                val effective = if (appearanceRulesOn && analysis.outcome == FacePipeline.Outcome.NO_MATCH &&
                    appearanceIgnored(analysis.appearance)
                ) {
                    if (testMode) TestNotifier.showFaceResult(this@GuardService, "Appearance rule", "Would not lock (matches an ignored look)")
                    analysis.copy(outcome = FacePipeline.Outcome.INCONCLUSIVE, reason = FacePipeline.InconclusiveReason.FACE_UNCLEAR)
                } else analysis
                when (rulesEngine.onAnalysis(effective, triggers)) {
                    RulesEngine.Decision.LOCK -> {
                        // Save the upright frame so the stored selfie isn't sideways. Encoding is
                        // best-effort: a failure here must never prevent the lock, so we swallow it
                        // to null and still respond. The Responder locks before touching this JPEG
                        // regardless of the "Capture intruders" setting.
                        val jpeg = if (captureIntruders) {
                            withContext(Dispatchers.Default) {
                                runCatching { BitmapUtils.toJpeg(upright) }.getOrNull()
                            }
                        } else null
                        responder.onIntruder(jpeg, analysis, captureIntruders, testMode)
                    }
                    else -> {
                        // Rapid confirm: this frame looked like an intruder but we're still inside the
                        // grace window (one bad frame won't lock). Instead of waiting a whole cadence
                        // interval for the confirming frame, re-check almost immediately so a real
                        // intruder is confirmed and the device locks within ~1s.
                        if (isSuspicious(effective, triggers)) {
                            scheduleForcedCapture(android.os.SystemClock.elapsedRealtime() + RAPID_CONFIRM_MS)
                        }
                    }
                }
                // Low-light policy. Runs in test mode too (it only brightens — never locks — and
                // posts preview notifications), so the behavior can be verified safely.
                handleLowLight(analysis, upright)
            } catch (t: Throwable) {
                // Never let a single bad frame crash the guard coroutine and stop future checks.
                android.util.Log.w("GuardService", "Frame analysis failed", t)
            } finally {
                // A 1280x720 frame is ~3.7 MB, and guarding produces one every few seconds for
                // hours. Nothing outlives this block — the pipeline returns plain data and the
                // evidence JPEG is already encoded — so releasing them here keeps the guard
                // service's heap flat instead of leaning on the collector to notice.
                runCatching {
                    if (!upright.isRecycled) upright.recycle()
                    if (upright !== bitmap && !bitmap.isRecycled) bitmap.recycle()
                }
                analyzing.set(false)
            }
        }
    }

    /**
     * Handles frames that are too dark to recognize a face (INCONCLUSIVE / LOW_LIGHT). We always do
     * the normal check first (above); this only kicks in when that check couldn't see well enough.
     *
     * Escalation (one step per dark frame, ~[LOW_LIGHT_RECHECK_DELAY_MS] apart because each step
     * forces a quick re-capture):
     *  1. Boost screen brightness to 100% but keep the current screen visible (transparent), re-check.
     *  2. If still too dark, show a white "loading" screen for maximum light, re-check.
     *  3. If even that can't see a face: Lock policy locks; Brighten policy gives up and continues.
     *
     * The instant a brightened frame yields a real result, the normal pipeline above handles it
     * (recognizes the owner, or locks on an intruder) and we tear the overlay down here.
     * Test mode runs the full brighten/flood flow but never locks — it posts preview notifications.
     */
    private suspend fun handleLowLight(
        analysis: FacePipeline.Analysis,
        upright: android.graphics.Bitmap,
    ) {
        // Two dark cases both need brightening:
        //  - a face was found but its crop is too dark to trust (INCONCLUSIVE / LOW_LIGHT), and
        //  - it's so dark that ML Kit couldn't find a face at all (NO_FACE on a dark frame). The
        //    latter is what happens in a genuinely dark room — previously we never brightened then,
        //    so the face was never lit up and re-validated.
        val darkNoFace = analysis.outcome == FacePipeline.Outcome.NO_FACE &&
            lowLightAction != LOW_LIGHT_IGNORE &&
            // Scales the whole frame down to read its average brightness — off the main thread,
            // and only reached when there was no face to work with in the first place.
            withContext(Dispatchers.Default) { BitmapUtils.averageLuminance(upright) } < DARK_FRAME_LUMA
        val lowLight = (analysis.outcome == FacePipeline.Outcome.INCONCLUSIVE &&
            analysis.reason == FacePipeline.InconclusiveReason.LOW_LIGHT) || darkNoFace
        val now = android.os.SystemClock.elapsedRealtime()

        if (!lowLight) {
            // A clear result arrived — the episode is over. Drop any brightness overlay.
            if (lowLightPhase != PHASE_NONE) endLowLightEpisode(now)
            return
        }

        if (lowLightAction == LOW_LIGHT_IGNORE) {
            if (testMode) notifyLowLightTest("Too dark to decide", "Low-light action is off, so this is ignored (won't lock).")
            return
        }

        // Recover from a stalled episode (e.g. captures stopped) so phases don't get stuck.
        if (lowLightPhase != PHASE_NONE && now - lowLightPhaseAt > LOW_LIGHT_EPISODE_TIMEOUT_MS) {
            endLowLightEpisode(now)
        }

        when (lowLightPhase) {
            PHASE_NONE -> {
                // Brief cooldown after a finished episode so we don't strobe the screen repeatedly.
                if (now - lastLowLightEpisodeAt < LOW_LIGHT_COOLDOWN_MS) return
                enterLowLightPhase(PHASE_BRIGHTEN, now, white = false)
                if (testMode) notifyLowLightTest("Too dark — brightening", "Raising screen brightness to take a clearer look.")
            }
            PHASE_BRIGHTEN -> {
                // Brightness boost still wasn't enough → escalate to the white flood screen.
                enterLowLightPhase(PHASE_FLOOD, now, white = true)
                if (testMode) notifyLowLightTest("Still too dark — full brightness", "Lighting up the screen to illuminate your face.")
            }
            PHASE_FLOOD -> {
                // Even maximum light can't resolve it. Stop the episode and apply the policy.
                endLowLightEpisode(now)
                if (lowLightAction == LOW_LIGHT_LOCK) {
                    val jpeg = if (captureIntruders) {
                        withContext(Dispatchers.Default) {
                            runCatching { BitmapUtils.toJpeg(upright) }.getOrNull()
                        }
                    } else null
                    // Responder respects testMode: locks for real, or just previews "would lock".
                    responder.onIntruder(jpeg, analysis, captureIntruders, testMode)
                } else if (testMode) {
                    notifyLowLightTest("Too dark to verify", "Couldn't see a face even at full brightness — continuing (no lock).")
                }
            }
        }
    }

    /** Set once we've warned that brightening can't run, so a dark night doesn't spam warnings. */
    private var warnedNoOverlayPermission = false

    /** Moves to a brightness phase: shows the matching overlay and schedules a quick re-check. */
    private fun enterLowLightPhase(phase: Int, now: Long, white: Boolean) {
        lowLightPhase = phase
        lowLightPhaseAt = now
        if (com.guardia.app.core.system.BrightnessOverlay.canDraw(this)) {
            com.guardia.app.core.system.BrightnessOverlay.show(this, white, LOW_LIGHT_OVERLAY_AUTOHIDE_MS)
        } else {
            // The overlay is a silent no-op without "Display over other apps" — say so instead of
            // claiming the screen was brightened. (Posting this first wins over the generic
            // "brightening" test message thanks to the notify throttle.)
            if (testMode) {
                notifyLowLightTest(
                    "Too dark — can't brighten",
                    "Guardia needs \"Display over other apps\" to raise screen brightness. Allow it in Settings > Detection, or via the warning notification.",
                )
            }
            notifyOverlayPermissionMissing()
        }
        scheduleForcedCapture(now + LOW_LIGHT_RECHECK_DELAY_MS)
    }

    /** One-time, tap-to-fix warning that low-light brightening is disabled by a missing permission. */
    private fun notifyOverlayPermissionMissing() {
        if (warnedNoOverlayPermission) return
        warnedNoOverlayPermission = true
        val nm = getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(WARNING_CHANNEL_ID, "Protection warnings", NotificationManager.IMPORTANCE_HIGH),
            )
        }
        val settingsIntent = Intent(
            android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            android.net.Uri.parse("package:$packageName"),
        )
        val pi = android.app.PendingIntent.getActivity(
            this, 1, settingsIntent, android.app.PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, WARNING_CHANNEL_ID)
            .setContentTitle("Can't brighten the screen")
            .setContentText("Low-light checks want to raise brightness, but Guardia needs \"Display over other apps\". Tap to allow it.")
            .setStyle(NotificationCompat.BigTextStyle())
            .setSmallIcon(R.drawable.ic_stat_guardia)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        runCatching { nm.notify(OVERLAY_WARNING_NOTIFICATION_ID, notification) }
    }

    private fun endLowLightEpisode(now: Long) {
        lowLightPhase = PHASE_NONE
        lastLowLightEpisodeAt = now
        com.guardia.app.core.system.BrightnessOverlay.hide(this)
    }

    /** Throttled so a long dark stretch doesn't spam notifications while testing. */
    private fun notifyLowLightTest(title: String, text: String) {
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastLowLightTestNotifyAt < LOW_LIGHT_TEST_NOTIFY_THROTTLE_MS) return
        lastLowLightTestNotifyAt = now
        TestNotifier.showFaceResult(this, title, text)
    }

    private fun notifyTestResult(analysis: FacePipeline.Analysis) {
        val pct = (analysis.similarity * 100).toInt()
        val (title, text) = when (analysis.outcome) {
            FacePipeline.Outcome.MATCH -> "Face OK" to "Recognized ${analysis.personName ?: "you"} ($pct% match)"
            FacePipeline.Outcome.NO_MATCH -> "Face not recognized" to "Best match $pct% - below threshold"
            FacePipeline.Outcome.BLOCKED -> "Blocked person" to "Matched ${analysis.personName ?: "a blocked person"} ($pct%) - would lock"
            FacePipeline.Outcome.MULTIPLE_FACES -> "Multiple faces" to "More than one face in view"
            FacePipeline.Outcome.NO_FACE ->
                if (effectiveLockOnNoFace()) "No face detected" to "Camera sees no face - would lock if this continues"
                else "No face detected" to "Lock-on-no-face is off, so this is ignored"
            FacePipeline.Outcome.INCONCLUSIVE -> when (analysis.reason) {
                // Low light is owned by handleLowLight (it drives the brighten/flood flow + its own
                // preview notifications), so don't post a generic message here.
                FacePipeline.InconclusiveReason.LOW_LIGHT -> return
                FacePipeline.InconclusiveReason.NO_ENROLLMENT ->
                    "No one enrolled" to "No faces are enrolled yet. Open People and add your face so Guardia can recognize you."
                else -> "No clear result" to "Face wasn't clear enough - try again"
            }
        }
        TestNotifier.showFaceResult(this, title, text)
    }

    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    /**
     * Promotes this service to the foreground. Returns false (instead of crashing) when the OS
     * refuses the start — e.g. camera-typed FGS launched from a restricted background context on
     * Android 12+. We try the richest legal type first and fall back to a plain start.
     */
    private fun startAsForeground(): Boolean {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return runCatching { startForeground(NOTIFICATION_ID, notification) }.isSuccess
        }
        // Only declare the types we can currently back: camera needs the permission granted, and
        // location is added when the (premium) location mode is active with permission — otherwise
        // requesting a type we can't service is itself an error on newer Android.
        var type = 0
        if (hasCameraPermission()) type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
        if (locationActive() && hasLocationPermission()) {
            type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        }
        if (type == 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            type = ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        }
        val hasLocationType = type and ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION != 0
        val typed = runCatching {
            if (type != 0) startForeground(NOTIFICATION_ID, notification, type)
            else startForeground(NOTIFICATION_ID, notification)
        }
        if (typed.isSuccess) {
            isForeground = true
            fgsHasLocation = hasLocationType
            return true
        }
        // Last resort: an untyped foreground start (still better than dying).
        val plain = runCatching { startForeground(NOTIFICATION_ID, notification) }.isSuccess
        if (plain) { isForeground = true; fgsHasLocation = false }
        return plain
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun buildNotification(): Notification {
        val contentIntent = android.app.PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            android.app.PendingIntent.FLAG_IMMUTABLE,
        )
        // Stopping from the shade still demands the real PIN: this opens the same gate the Quick
        // Settings tile uses, rather than stopping the service directly. An ongoing notification is
        // visible on a locked phone, so a one-tap "stop" here would undo the whole app.
        val stopIntent = android.app.PendingIntent.getActivity(
            this,
            3,
            Intent(this, StopGuardActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            android.app.PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.guard_notif_protected))
            .setSmallIcon(R.drawable.ic_stat_guardia)
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(R.drawable.ic_stat_guardia, getString(R.string.guard_notif_stop), stopIntent)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.guard_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = getString(R.string.guard_channel_desc) }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    /** Warns the owner that the bundled face model didn't load and recognition is unreliable. */
    private fun notifyDegradedRecognition() {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(WARNING_CHANNEL_ID, "Protection warnings", NotificationManager.IMPORTANCE_HIGH),
            )
        }
        val notification = NotificationCompat.Builder(this, WARNING_CHANNEL_ID)
            .setContentTitle("Face recognition degraded")
            .setContentText("The on-device face model failed to load, so recognition is far less accurate. Reinstall Guardia to restore it.")
            .setStyle(NotificationCompat.BigTextStyle())
            .setSmallIcon(R.drawable.ic_stat_guardia)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .build()
        runCatching { nm.notify(DEGRADED_NOTIFICATION_ID, notification) }
    }

    companion object {
        private const val CHANNEL_ID = "guardia_guarding"
        private const val WARNING_CHANNEL_ID = "guardia_warnings"
        private const val NOTIFICATION_ID = 1001
        private const val DEGRADED_NOTIFICATION_ID = 1005
        private const val OVERLAY_WARNING_NOTIFICATION_ID = 1006
        private const val DIGEST_CHANNEL_ID = "guardia_digest"
        private const val DIGEST_NOTIFICATION_ID = 1007
        /** Heartbeat + digest bookkeeping cadence (also the max staleness of the heartbeat). */
        private const val MAINTENANCE_INTERVAL_MS = 60_000L
        private const val DIGEST_PERIOD_MS = 7L * 24 * 60 * 60 * 1000
        private const val VOICE_ALWAYS = 1
        private const val VOICE_FALLBACK = 2

        // Low-light policy values (mirror AppPreferences.lowLightAction).
        private const val LOW_LIGHT_IGNORE = 0
        private const val LOW_LIGHT_BRIGHTEN = 1
        private const val LOW_LIGHT_LOCK = 2
        /** Whole-frame mean luminance (0..255) below which a NO_FACE frame is treated as "too dark". */
        private const val DARK_FRAME_LUMA = 40f
        // Low-light escalation phases.
        private const val PHASE_NONE = 0
        private const val PHASE_BRIGHTEN = 1
        private const val PHASE_FLOOD = 2
        /** Delay after brightening before forcing a re-check, so the screen has actually lit up. */
        private const val LOW_LIGHT_RECHECK_DELAY_MS = 800L
        /** Safety auto-hide for the brightness overlay (refreshed each phase). */
        private const val LOW_LIGHT_OVERLAY_AUTOHIDE_MS = 4000L
        /** If no frame advances the episode within this window, reset it (stall recovery). */
        private const val LOW_LIGHT_EPISODE_TIMEOUT_MS = 6000L
        /** Minimum gap between low-light episodes so we don't strobe the screen. */
        private const val LOW_LIGHT_COOLDOWN_MS = 6000L
        /** Throttle for low-light preview notifications in test mode (under the re-check delay so
         *  each escalation step still posts a notification). */
        private const val LOW_LIGHT_TEST_NOTIFY_THROTTLE_MS = 500L

        /** Delay before the confirming re-check after a suspicious frame (rapid intruder confirm). */
        private const val RAPID_CONFIRM_MS = 550L

        /**
         * How long to wait after a foreground screen hands the camera back before taking the
         * check it postponed. Long enough for the check activity to finish tearing its camera
         * down, short enough that the gap in cover is measured in a second, not a cadence.
         */
        private const val CAMERA_HANDBACK_DELAY_MS = 900L

        /** Legacy tight poll, kept only while shake-to-check is armed (sensor sets flags async). */
        private const val POLL_INTERVAL_MS = 350L
        /** Floor for a computed sleep so a hot loop can never spin. */
        private const val MIN_POLL_INTERVAL_MS = 250L
        /** Ceiling for a computed sleep so async triggers are noticed promptly even mid-cadence. */
        private const val MAX_POLL_INTERVAL_MS = 1500L
        /** Slow poll while the screen is off — we can't capture then, so barely tick to save battery. */
        private const val IDLE_POLL_INTERVAL_MS = 3000L
        /** Frames discarded after opening the camera so auto-exposure can settle. */
        private const val WARMUP_FRAMES = 2
        /** Max time to keep the camera open waiting for a usable frame before releasing it. */
        private const val CAPTURE_TIMEOUT_MS = 2500L

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, GuardService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, GuardService::class.java))
        }
    }
}
