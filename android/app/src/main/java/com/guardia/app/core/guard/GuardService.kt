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
import com.guardia.app.core.ml.BitmapUtils
import com.guardia.app.core.ml.FacePipeline
import com.guardia.app.core.system.TestNotifier
import com.guardia.app.core.voice.VoiceController
import com.guardia.app.data.AppPreferences
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

/**
 * Persistent foreground service running the guard loop: a headless CameraX
 * ImageAnalysis feed, gated by [CaptureGate] for battery, analyzed by [FacePipeline],
 * with intruder response via [Responder].
 */
@AndroidEntryPoint
class GuardService : LifecycleService() {

    @Inject lateinit var facePipeline: FacePipeline
    @Inject lateinit var rulesEngine: RulesEngine
    @Inject lateinit var responder: Responder
    @Inject lateinit var captureGate: CaptureGate
    @Inject lateinit var prefs: AppPreferences
    @Inject lateinit var peopleRepository: com.guardia.app.data.PeopleRepository
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

    /** Reacts to lock/unlock so the premium unlock ramp can re-arm each session. */
    private val screenReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON -> screenInteractive = true
                Intent.ACTION_USER_PRESENT -> { screenInteractive = true; captureGate.onUnlocked() }
                Intent.ACTION_SCREEN_OFF -> {
                    screenInteractive = false
                    captureGate.onScreenOff()
                    appTriggerManager.onScreenOff()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        lifecycleScope.launch { prefs.sensitivity.collectLatest { sensitivity = it } }
        lifecycleScope.launch { prefs.captureIntruders.collectLatest { captureIntruders = it } }
        lifecycleScope.launch { prefs.lowLightAction.collectLatest { lowLightAction = it } }
        lifecycleScope.launch { prefs.voiceListeningMode.collectLatest { voiceMode = it } }
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

        val s = if (locActive) {
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

        captureGate.setResponsiveness(s.responsiveness)
        captureGate.setIntervalEnabled(s.intervalEnabled)
        captureGate.setFirstCheckOnUnlock(s.firstCheck)
        captureGate.setShakeEnabled(s.shake)
        captureGate.setCustomIntervalSeconds(s.customInterval)
        captureGate.setRamp(s.ramp)
        resolvedLockOnNoFace = s.lockOnNoFace

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
        if (hasCameraPermission()) startCaptureScheduler()
        if (voiceMode == VOICE_ALWAYS) {
            VoiceController.start(this)
            voiceArmed = true
        }
        GuardController.onServiceState(GuardState.PROTECTED)
        return START_STICKY
    }

    override fun onDestroy() {
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
        isForeground = false
        GuardController.onServiceState(GuardState.STOPPED)
        super.onDestroy()
    }

    /**
     * Instead of holding the camera open continuously (which keeps the OS privacy indicator lit),
     * we poll [CaptureGate] on a light timer and only open the camera for the brief moment of an
     * actual check, releasing it immediately afterward so the indicator turns off between checks.
     */
    private fun startCaptureScheduler() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({ cameraProvider = runCatching { future.get() }.getOrNull() }, cameraMainExecutor)

        captureJob = lifecycleScope.launch {
            while (isActive) {
                // While the screen is off we can't capture (CaptureGate requires interactive +
                // unlocked), so poll slowly to avoid needless wakeups/binder calls; poll responsively
                // once the screen is on so first-check-on-unlock and ramps stay snappy.
                delay(if (screenInteractive) POLL_INTERVAL_MS else IDLE_POLL_INTERVAL_MS)
                // A low-light re-check can request an immediate capture (just after we brighten the
                // screen) instead of waiting for the next scheduled interval.
                val forced = forceCaptureAt in 1..android.os.SystemClock.elapsedRealtime()
                // Yield the front camera while a per-app check is verifying so the two don't fight
                // over the single process-wide CameraX provider.
                if (!capturing.get() && !appTriggerManager.checkInProgress &&
                    (forced || captureGate.shouldCapture())
                ) {
                    if (forced) forceCaptureAt = 0L
                    captureOnce()
                }
            }
        }
    }

    /** Opens the camera, grabs a single (warmed-up) frame, then releases the camera right away. */
    private fun captureOnce() {
        val provider = cameraProvider ?: return
        if (!capturing.compareAndSet(false, true)) return
        captureStartedAt = android.os.SystemClock.elapsedRealtime()

        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
        val handled = AtomicBoolean(false)
        val frameIndex = AtomicInteger(0)

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
            try {
                val analysis = facePipeline.analyze(bitmap, rotation, sensitivity)
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
                        // Save the upright (rotation-applied) frame so the stored selfie isn't sideways.
                        // Encoding is best-effort: a failure here must never prevent the lock, so we
                        // swallow it to null and still respond. The Responder locks before touching
                        // this JPEG regardless of the "Capture intruders" setting.
                        val jpeg = if (captureIntruders) {
                            runCatching { BitmapUtils.toJpeg(BitmapUtils.rotate(bitmap, rotation)) }.getOrNull()
                        } else null
                        responder.onIntruder(jpeg, analysis, captureIntruders, testMode)
                    }
                    else -> {
                        // Rapid confirm: this frame looked like an intruder but we're still inside the
                        // grace window (one bad frame won't lock). Instead of waiting a whole cadence
                        // interval for the confirming frame, re-check almost immediately so a real
                        // intruder is confirmed and the device locks within ~1s.
                        if (isSuspicious(effective, triggers)) {
                            forceCaptureAt = android.os.SystemClock.elapsedRealtime() + RAPID_CONFIRM_MS
                        }
                    }
                }
                // Low-light policy. Runs in test mode too (it only brightens — never locks — and
                // posts preview notifications), so the behavior can be verified safely.
                handleLowLight(analysis, bitmap, rotation)
            } catch (t: Throwable) {
                // Never let a single bad frame crash the guard coroutine and stop future checks.
                android.util.Log.w("GuardService", "Frame analysis failed", t)
            } finally {
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
        bitmap: android.graphics.Bitmap,
        rotation: Int,
    ) {
        val lowLight = analysis.outcome == FacePipeline.Outcome.INCONCLUSIVE &&
            analysis.reason == FacePipeline.InconclusiveReason.LOW_LIGHT
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
                        runCatching { BitmapUtils.toJpeg(BitmapUtils.rotate(bitmap, rotation)) }.getOrNull()
                    } else null
                    // Responder respects testMode: locks for real, or just previews "would lock".
                    responder.onIntruder(jpeg, analysis, captureIntruders, testMode)
                } else if (testMode) {
                    notifyLowLightTest("Too dark to verify", "Couldn't see a face even at full brightness — continuing (no lock).")
                }
            }
        }
    }

    /** Moves to a brightness phase: shows the matching overlay and schedules a quick re-check. */
    private fun enterLowLightPhase(phase: Int, now: Long, white: Boolean) {
        lowLightPhase = phase
        lowLightPhaseAt = now
        com.guardia.app.core.system.BrightnessOverlay.show(this, white, LOW_LIGHT_OVERLAY_AUTOHIDE_MS)
        forceCaptureAt = now + LOW_LIGHT_RECHECK_DELAY_MS
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
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.guard_notif_protected))
            .setSmallIcon(R.drawable.ic_stat_guardia)
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
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

    companion object {
        private const val CHANNEL_ID = "guardia_guarding"
        private const val NOTIFICATION_ID = 1001
        private const val VOICE_ALWAYS = 1
        private const val VOICE_FALLBACK = 2

        // Low-light policy values (mirror AppPreferences.lowLightAction).
        private const val LOW_LIGHT_IGNORE = 0
        private const val LOW_LIGHT_BRIGHTEN = 1
        private const val LOW_LIGHT_LOCK = 2
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

        /** How often the scheduler checks whether a capture is due (camera stays off meanwhile). */
        private const val POLL_INTERVAL_MS = 350L
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
