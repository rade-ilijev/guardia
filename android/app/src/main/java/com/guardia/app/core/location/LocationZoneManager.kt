package com.guardia.app.core.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.guardia.app.data.AppPreferences
import com.guardia.app.data.SafeZoneRepository
import com.guardia.app.domain.model.SafeZone
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/** The guarding policy in effect for the device's current location. */
data class ZonePolicy(
    val guardEnabled: Boolean,
    /** When true, this place uses the global Guarding & Triggers schedule (the fields below are ignored). */
    val useDefault: Boolean,
    val responsiveness: Int,
    val customIntervalSeconds: Int,
    val firstCheckOnUnlock: Boolean,
    val checkRamp: String,
    val shakeToCheck: Boolean,
    /** Lock when no face is visible in the current place. */
    val lockOnNoFace: Boolean,
    /** Human-readable place label for the UI (zone name, "Public area", or "Locating…"). */
    val label: String,
    val inZone: Boolean,
    val zoneId: String? = null,
)

/**
 * Tracks the device location (via fused provider) and resolves it against the user's safe zones to
 * produce the active [ZonePolicy]. Inside a zone, that zone's policy applies; outside every zone the
 * "public" policy from preferences applies. If the location is unknown we fall back to the public
 * policy so guarding is never silently disabled.
 */
@Singleton
class LocationZoneManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val zoneRepo: SafeZoneRepository,
    prefs: AppPreferences,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val client = LocationServices.getFusedLocationProviderClient(context)

    private val _policy = MutableStateFlow(
        ZonePolicy(true, true, 2, 0, false, "", false, false, "Locating…", false),
    )
    val policy: StateFlow<ZonePolicy> = _policy.asStateFlow()

    @Volatile private var zones: List<SafeZone> = emptyList()
    @Volatile private var publicGuard = true
    @Volatile private var publicUseDefault = true
    @Volatile private var publicResponsiveness = 2
    @Volatile private var publicCustomInterval = 0
    @Volatile private var publicFirstCheck = false
    @Volatile private var publicRamp = ""
    @Volatile private var publicShake = false
    @Volatile private var publicLockOnNoFace = false
    @Volatile private var lastLocation: Location? = null
    @Volatile private var updatesActive = false

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let {
                lastLocation = it
                recompute()
            }
        }
    }

    init {
        // Two combines (Kotlin's typed combine caps at 5 flows) folded into one.
        val publicA = combine(
            prefs.publicGuardEnabled,
            prefs.publicUseDefault,
            prefs.publicResponsiveness,
            prefs.publicCustomIntervalSeconds,
            prefs.publicFirstCheckOnUnlock,
        ) { guard, useDefault, resp, custom, first ->
            arrayOf(guard, useDefault, resp, custom, first)
        }
        val publicB = combine(
            prefs.publicCheckRamp,
            prefs.publicShakeToCheck,
            prefs.publicLockOnNoFace,
        ) { ramp, shake, noFace -> arrayOf(ramp, shake, noFace) }

        combine(zoneRepo.zones, publicA, publicB) { z, a, b ->
            zones = z
            publicGuard = a[0] as Boolean
            publicUseDefault = a[1] as Boolean
            publicResponsiveness = a[2] as Int
            publicCustomInterval = a[3] as Int
            publicFirstCheck = a[4] as Boolean
            publicRamp = b[0] as String
            publicShake = b[1] as Boolean
            publicLockOnNoFace = b[2] as Boolean
        }
            .onEach { recompute() }
            .launchIn(scope)
    }

    fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /** Begin periodic location updates and refresh once immediately. */
    fun start() {
        if (updatesActive || !hasLocationPermission()) return
        val request = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, UPDATE_INTERVAL_MS)
            .setMinUpdateDistanceMeters(UPDATE_DISTANCE_M)
            .setMinUpdateIntervalMillis(FASTEST_INTERVAL_MS)
            .build()
        try {
            client.requestLocationUpdates(request, callback, android.os.Looper.getMainLooper())
            updatesActive = true
            client.lastLocation.addOnSuccessListener { loc -> if (loc != null) { lastLocation = loc; recompute() } }
        } catch (e: SecurityException) {
            updatesActive = false
        }
    }

    fun stop() {
        if (!updatesActive) return
        runCatching { client.removeLocationUpdates(callback) }
        updatesActive = false
    }

    /**
     * Forces a fresh location fix right now and recomputes the active policy. Used by the Location
     * screen so the displayed place/protection status reflects reality without the user moving 25m
     * or re-toggling anything. Falls back to the last known fix if a fresh one isn't available.
     */
    fun refreshNow() {
        if (!hasLocationPermission()) return
        try {
            client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
                .addOnSuccessListener { loc ->
                    if (loc != null) { lastLocation = loc; recompute() } else {
                        client.lastLocation.addOnSuccessListener { last ->
                            if (last != null) { lastLocation = last; recompute() }
                        }
                    }
                }
        } catch (e: SecurityException) {
            // Permission revoked mid-session; ignore.
        }
    }

    /** One-shot high-accuracy fix used when pinning the current location as a zone. */
    suspend fun currentLocation(): Location? {
        if (!hasLocationPermission()) return null
        return suspendCancellableCoroutine { cont ->
            try {
                client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                    .addOnSuccessListener { loc ->
                        if (loc != null) cont.resume(loc)
                        else client.lastLocation
                            .addOnSuccessListener { last -> cont.resume(last) }
                            .addOnFailureListener { cont.resume(null) }
                    }
                    .addOnFailureListener { cont.resume(null) }
            } catch (e: SecurityException) {
                cont.resume(null)
            }
        }
    }

    private fun publicPolicy(label: String) = ZonePolicy(
        guardEnabled = publicGuard,
        useDefault = publicUseDefault,
        responsiveness = publicResponsiveness,
        customIntervalSeconds = publicCustomInterval,
        firstCheckOnUnlock = publicFirstCheck,
        checkRamp = publicRamp,
        shakeToCheck = publicShake,
        lockOnNoFace = publicLockOnNoFace,
        label = label,
        inZone = false,
    )

    private fun zonePolicy(zone: SafeZone) = ZonePolicy(
        guardEnabled = zone.guardEnabled,
        useDefault = zone.useDefault,
        responsiveness = zone.responsiveness,
        customIntervalSeconds = zone.customIntervalSeconds,
        firstCheckOnUnlock = zone.firstCheckOnUnlock,
        checkRamp = zone.checkRamp,
        shakeToCheck = zone.shakeToCheck,
        lockOnNoFace = zone.lockOnNoFace,
        label = zone.name,
        inZone = true,
        zoneId = zone.id,
    )

    private fun recompute() {
        val loc = lastLocation
        val policy = when {
            loc == null -> publicPolicy("Locating…")
            else -> {
                val nearest = zones
                    .map { it to distanceTo(loc, it) }
                    .filter { (zone, dist) -> dist <= zone.radiusMeters }
                    .minByOrNull { it.second }
                    ?.first
                if (nearest != null) zonePolicy(nearest) else publicPolicy("Public area")
            }
        }
        _policy.value = policy
    }

    private fun distanceTo(loc: Location, zone: SafeZone): Float {
        val out = FloatArray(1)
        Location.distanceBetween(loc.latitude, loc.longitude, zone.latitude, zone.longitude, out)
        return out[0]
    }

    private companion object {
        const val UPDATE_INTERVAL_MS = 90_000L    // ~1.5 min balanced-power sampling
        const val FASTEST_INTERVAL_MS = 20_000L
        const val UPDATE_DISTANCE_M = 15f
    }
}
