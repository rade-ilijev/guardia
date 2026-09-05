package com.guardia.app.ui.screens.lock

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guardia.app.core.backup.BackupManager
import com.guardia.app.core.security.PinType
import com.guardia.app.data.AppPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class LockViewModel @Inject constructor(
    private val prefs: AppPreferences,
    private val backup: BackupManager,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    /** Epoch-ms until which PIN entry is locked out (0 = open). */
    val lockedUntil: StateFlow<Long> =
        prefs.pinLockedUntil.stateIn(viewModelScope, SharingStarted.Eagerly, 0L)

    val failedAttempts: StateFlow<Int> =
        prefs.pinFailedAttempts.stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    fun verify(pin: String, onResult: (PinType?) -> Unit) {
        viewModelScope.launch {
            // Guard against verifying while locked out (defense in depth; UI also blocks input).
            if (System.currentTimeMillis() < prefs.pinLockedUntil.first()) {
                onResult(null)
                return@launch
            }
            onResult(prefs.verifyPin(pin))
        }
    }

    /**
     * Call when a completed PIN entry on Guardia's own lock screen was wrong. Increments the
     * brute-force backoff only — it does NOT capture an intruder selfie. Intruder capture is
     * reserved for failed *device* unlocks (system lock screen), handled by
     * [com.guardia.app.core.system.GuardDeviceAdminReceiver]; fumbling the app's own PIN is not
     * the same as an intruder trying to get into a locked phone.
     */
    fun onWrongAttempt() {
        viewModelScope.launch { prefs.recordPinFailure() }
    }

    fun onSuccess() {
        viewModelScope.launch { prefs.recordPinSuccess() }
    }

    /** Whether a recovery code exists, so the lock screen only offers a route that can work. */
    val recoveryAvailable: StateFlow<Boolean> =
        prefs.recoveryCodeSet.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /**
     * Checks a typed recovery code.
     *
     * Deliberately outside the PIN's brute-force backoff: someone locked out by wrong PIN guesses
     * is exactly the person who then needs this, and making them wait out the penalty first would
     * defeat the purpose. The code's own strength is what protects it — 56 bits, versus a PIN's
     * six digits, which is why the PIN needs a lockout and this does not.
     */
    fun verifyRecoveryCode(code: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch { onResult(prefs.verifyRecoveryCode(code)) }
    }

    /**
     * Checks a backup file as the second recovery route: the owner proves they hold a backup this
     * install exported *and* its password, and may then set a new PIN.
     *
     * Nothing is imported. Someone who has only forgotten their PIN still has every enrolled face
     * on the device; the file is being read as a credential, not as data. See
     * [BackupManager.verifyOwner] for why decrypting alone is not enough to authorise this.
     */
    fun verifyBackupFile(uri: Uri, password: String, onResult: (Boolean, String?) -> Unit) {
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
            when (val r = backup.verifyOwner(bytes, password.toCharArray())) {
                is BackupManager.OwnerCheck.Owned -> onResult(true, null)
                is BackupManager.OwnerCheck.Foreign ->
                    onResult(false, "That backup was made by a different install of Guardia, so it can't unlock this one.")
                is BackupManager.OwnerCheck.Error -> onResult(false, r.message)
            }
        }
    }

    /**
     * Replaces the real PIN after a successful recovery and clears the lockout. The decoy and panic
     * PINs are left alone — recovery proves ownership of the device, not that those should be reset.
     */
    fun setNewPin(pin: String, onDone: () -> Unit) {
        viewModelScope.launch {
            prefs.setSinglePin(com.guardia.app.core.security.PinType.REAL, pin)
            prefs.clearPinLockout()
            onDone()
        }
    }
}
