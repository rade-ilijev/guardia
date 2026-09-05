package com.guardia.app.ui.screens.onboarding

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guardia.app.core.backup.BackupManager
import com.guardia.app.core.billing.EntitlementManager
import com.guardia.app.data.AppPreferences
import com.guardia.app.data.PeopleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val prefs: AppPreferences,
    peopleRepository: PeopleRepository,
    entitlements: EntitlementManager,
    private val backup: BackupManager,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    /** Whether at least one face has been enrolled (drives the enroll-step check mark). */
    val hasEnrolledFace: StateFlow<Boolean> = peopleRepository.people
        .map { list -> list.any { !it.blocked } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val premium: StateFlow<Boolean> = entitlements.premium

    // The "Choose your protection" step writes preferences directly, so a new user leaves
    // onboarding with the interesting options already on instead of having to find them later.
    private fun <T> flow(f: kotlinx.coroutines.flow.Flow<T>, initial: T): StateFlow<T> =
        f.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), initial)

    val responsiveness: StateFlow<Int> = flow(prefs.responsiveness, 1)
    val firstCheckOnUnlock: StateFlow<Boolean> = flow(prefs.firstCheckOnUnlock, false)
    val captureIntruders: StateFlow<Boolean> = flow(prefs.captureIntruders, true)
    val shakeToCheck: StateFlow<Boolean> = flow(prefs.shakeToCheck, false)
    val lockOnMultipleFaces: StateFlow<Boolean> = flow(prefs.lockOnMultipleFaces, true)

    fun setResponsiveness(level: Int) { viewModelScope.launch { prefs.setResponsiveness(level) } }
    fun setFirstCheckOnUnlock(v: Boolean) { viewModelScope.launch { prefs.setFirstCheckOnUnlock(v) } }
    fun setCaptureIntruders(v: Boolean) { viewModelScope.launch { prefs.setCaptureIntruders(v) } }
    fun setShakeToCheck(v: Boolean) { viewModelScope.launch { prefs.setShakeToCheck(v) } }
    fun setLockOnMultipleFaces(v: Boolean) { viewModelScope.launch { prefs.setLockOnMultipleFaces(v) } }

    /**
     * Persists PINs without finishing onboarding (so the user can keep walking the steps), and
     * mints the recovery code at the same time.
     *
     * The code is generated here rather than being offered as an optional extra later, because the
     * people who most need it are exactly the ones who would skip an optional security chore — and
     * without it, a forgotten PIN means reinstalling and losing every enrolled face.
     */
    fun savePins(real: String, decoy: String?, panic: String?, onSaved: (String) -> Unit = {}) {
        viewModelScope.launch {
            prefs.setPins(real, decoy, panic)
            val code = com.guardia.app.core.security.PinManager.newRecoveryCode()
            prefs.setRecoveryCode(code)
            onSaved(code)
        }
    }

    /**
     * Restores people and faces from a backup during setup.
     *
     * Offered on the very first screen because the alternative is discovering it in Settings after
     * re-enrolling everyone by hand - by which point the restore is worth nothing. Merges rather
     * than replaces: on a fresh install there is nothing to replace, and on a re-run of onboarding
     * wiping whatever is already enrolled would be a surprise.
     */
    fun restoreFromBackup(uri: Uri, password: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val bytes = runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                }
            }.getOrNull()
            if (bytes == null) {
                onResult(false, "Could not open that file.")
                return@launch
            }
            when (val r = backup.import(bytes, password.toCharArray(), replace = false)) {
                is BackupManager.ImportResult.Success ->
                    onResult(true, "Restored ${r.people} people and ${r.samples} face samples.")
                is BackupManager.ImportResult.Error -> onResult(false, r.message)
            }
        }
    }

    /** Marks onboarding complete and routes into the app. */
    fun finish(onDone: () -> Unit) {
        viewModelScope.launch {
            prefs.setOnboarded(true)
            onDone()
        }
    }
}
