package com.guardia.app.ui.screens.lock

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.guardia.app.core.security.PinType
import com.guardia.app.data.AppPreferences
import com.guardia.app.ui.components.PinDots
import com.guardia.app.ui.components.PinPad
import com.guardia.app.ui.theme.Guardia
import com.guardia.app.ui.theme.OverlineStyle
import kotlinx.coroutines.delay
import com.guardia.app.ui.components.glow
import com.guardia.app.ui.components.animateEntrance
import com.guardia.app.ui.theme.SpaceGrotesk
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Key
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.guardia.app.core.security.PinManager
import com.guardia.app.ui.components.ButtonSize
import com.guardia.app.ui.components.ButtonVariant
import com.guardia.app.ui.components.ShButton
import com.guardia.app.ui.components.ShInput
import com.guardia.app.ui.theme.MonoCaption
import com.guardia.app.ui.theme.Spacing
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.guardia.app.R

@Composable
fun LockScreen(
    onUnlocked: () -> Unit,
    onDecoy: () -> Unit,
    viewModel: LockViewModel = hiltViewModel(),
) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    var showRecovery by remember { mutableStateOf(false) }
    // True once the current entry has produced a wrong (sub-max-length) result, so backspacing the
    // buffer to empty still counts as one failed attempt (prevents short-PIN brute cycling).
    var sawWrong by remember { mutableStateOf(false) }

    val lockedUntil by viewModel.lockedUntil.collectAsStateWithLifecycle()
    val attempts by viewModel.failedAttempts.collectAsStateWithLifecycle()
    val recoveryAvailable by viewModel.recoveryAvailable.collectAsStateWithLifecycle()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(lockedUntil) {
        while (lockedUntil > System.currentTimeMillis()) {
            now = System.currentTimeMillis()
            delay(500)
        }
        now = System.currentTimeMillis()
    }
    val locked = lockedUntil > now
    val remainingSec = ((lockedUntil - now + 999) / 1000).coerceAtLeast(0)
    if (locked && pin.isNotEmpty()) pin = ""

    fun registerWrong() {
        error = true
        pin = ""
        sawWrong = false
        viewModel.onWrongAttempt()
    }

    fun attempt(current: String) {
        viewModel.verify(current) { type ->
            when (type) {
                PinType.REAL -> { viewModel.onSuccess(); onUnlocked() }
                PinType.DECOY -> { viewModel.onSuccess(); onDecoy() }
                PinType.PANIC -> { viewModel.onSuccess(); onDecoy() }
                null -> { if (current.length >= 6) registerWrong() else sawWrong = true }
            }
        }
    }

    val attemptsLeft = AppPreferences.LOCK_AFTER_ATTEMPTS - attempts
    val statusLabel = when {
        locked -> stringResource(R.string.lock_status_locked_out)
        error -> stringResource(R.string.lock_status_denied)
        else -> stringResource(R.string.lock_status_secured)
    }
    val statusColor = if (error || locked) Guardia.colors.destructive else Guardia.colors.success
    // The attempts-left line is a plural resource, not a string with an "s" appended: English has
    // two forms, Polish four, Arabic six, and concatenation gets none of them right. Compose has no
    // stringResource for plurals, so it goes through the resources object directly.
    val resources = LocalContext.current.resources
    val subtitle = when {
        locked -> stringResource(R.string.lock_subtitle_lockout, remainingSec)
        error && attempts in 1 until AppPreferences.LOCK_AFTER_ATTEMPTS && attemptsLeft <= 2 ->
            resources.getQuantityString(R.plurals.lock_subtitle_attempts_left, attemptsLeft, attemptsLeft)
        error -> stringResource(R.string.lock_subtitle_incorrect)
        else -> stringResource(R.string.lock_subtitle_enter_pin)
    }

    // With re-locking on departure this is the screen the owner sees most, so it earns the same
    // care as the dashboard: a lit mark on a wide glow, the wordmark in the brand face, and a
    // staggered entrance so returning to the app feels like arriving rather than being stopped.
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.animateEntrance(0)) {
            Box(
                Modifier
                    .size(240.dp)
                    .background(
                        Brush.radialGradient(
                            listOf(statusColor.copy(alpha = 0.18f), Color.Transparent),
                        ),
                        CircleShape,
                    ),
            )
            LockMark(alert = error || locked)
        }
        Spacer(Modifier.height(20.dp))
        Text(
            "Guardia",
            style = MaterialTheme.typography.headlineMedium.copy(fontFamily = SpaceGrotesk),
            fontWeight = FontWeight.Bold,
            modifier = Modifier.animateEntrance(1),
        )
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.animateEntrance(2)) {
            Box(
                Modifier
                    .glow(statusColor, CircleShape, radius = 9.dp, alpha = 0.9f)
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(statusColor),
            )
            Spacer(Modifier.size(8.dp))
            Text(statusLabel, style = OverlineStyle, color = statusColor)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = if (error || locked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(40.dp))
        PinDots(length = pin.length, modifier = Modifier.animateEntrance(3))
        Spacer(Modifier.height(48.dp))
        PinPad(
            modifier = Modifier.animateEntrance(4),
            enabled = !locked,
            onDigit = { digit ->
                if (!locked && pin.length < 6) {
                    error = false
                    pin += digit.toString()
                    if (pin.length >= 4) attempt(pin)
                }
            },
            onBackspace = {
                if (pin.isNotEmpty()) {
                    pin = pin.dropLast(1)
                    if (pin.isEmpty() && sawWrong) registerWrong()
                }
            },
        )
        // Always offered now that there are two routes in: the recovery code, and a backup file
        // this install exported. The code has to have been minted to be usable, but a backup can
        // exist on any device without Guardia knowing about it, so hiding the entrance would leave
        // people who *can* get back in staring at a dead end.
        Spacer(Modifier.height(20.dp))
        ShButton(
            text = stringResource(R.string.lock_forgot_pin),
            onClick = { showRecovery = true },
            variant = ButtonVariant.Ghost,
            size = ButtonSize.Sm,
            modifier = Modifier.animateEntrance(5),
        )
    }

    if (showRecovery) {
        RecoveryDialog(
            viewModel = viewModel,
            recoveryAvailable = recoveryAvailable,
            onDismiss = { showRecovery = false },
            onRecovered = { showRecovery = false; onUnlocked() },
        )
    }
}

/**
 * Recovery: prove ownership, then choose a new PIN.
 *
 * Two proofs are accepted, and they are deliberately different in kind. The recovery code is
 * something the owner wrote down at setup. A backup file is something only this install could have
 * produced, unlocked by the password the owner chose for it — see [com.guardia.app.core.backup.BackupManager.verifyOwner]
 * for why the file alone is not enough. Either one clears the brute-force lockout and lets the real
 * PIN be replaced; neither reveals anything about the stored PIN, and neither route ever confirms a
 * partial guess.
 *
 * The backup route imports nothing. Someone who forgot a PIN still has all of their enrolled faces;
 * the file is being read as a credential, not as data.
 */
@Composable
private fun RecoveryDialog(
    viewModel: LockViewModel,
    recoveryAvailable: Boolean,
    onDismiss: () -> Unit,
    onRecovered: () -> Unit,
) {
    val c = Guardia.colors
    // With no code minted there is only one route, so skip a chooser that would offer one option.
    var stage by remember {
        mutableStateOf(if (recoveryAvailable) RecoveryStage.CHOOSE else RecoveryStage.BACKUP)
    }
    var code by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var backupUri by remember { mutableStateOf<Uri?>(null) }
    var backupPassword by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    // Hoisted out of the callbacks below: `stringResource` is @Composable, and an onClick or an
    // onValueChange is not. Reading them here is also what makes the messages follow a language
    // change, since the whole dialog recomposes when the configuration does.
    val wrongCode = stringResource(R.string.recovery_code_wrong)
    val pinsDiffer = stringResource(R.string.recovery_pins_differ)

    // Opening the system picker stops the activity, but the gate is already LOCKED so nothing
    // re-locks and this dialog's state survives the trip.
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { backupUri = uri; message = null }
    }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.popover,
        icon = {
            Icon(
                if (stage == RecoveryStage.BACKUP) Icons.Filled.FolderOpen else Icons.Filled.Key,
                contentDescription = null,
                tint = if (message != null) c.destructive else c.brand,
            )
        },
        title = {
            Text(
                when (stage) {
                    RecoveryStage.CHOOSE -> stringResource(R.string.recovery_title_choose)
                    RecoveryStage.CODE -> stringResource(R.string.recovery_title_code)
                    RecoveryStage.BACKUP -> stringResource(R.string.recovery_title_backup)
                    RecoveryStage.NEW_PIN -> stringResource(R.string.recovery_title_new_pin)
                },
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                when (stage) {
                    RecoveryStage.CHOOSE -> {
                        Text(
                            stringResource(R.string.recovery_choose_body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = c.mutedForeground,
                        )
                        ShButton(
                            text = stringResource(R.string.recovery_choose_code),
                            onClick = { stage = RecoveryStage.CODE; message = null },
                            variant = ButtonVariant.Outline,
                            leadingIcon = Icons.Filled.Key,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        ShButton(
                            text = stringResource(R.string.recovery_choose_backup),
                            onClick = { stage = RecoveryStage.BACKUP; message = null },
                            variant = ButtonVariant.Outline,
                            leadingIcon = Icons.Filled.FolderOpen,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    RecoveryStage.CODE -> {
                        Text(
                            stringResource(R.string.recovery_code_body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = c.mutedForeground,
                        )
                        ShInput(
                            value = code,
                            onValueChange = { input ->
                                code = input
                                message = null
                                if (PinManager.looksLikeRecoveryCode(input)) {
                                    viewModel.verifyRecoveryCode(input) { ok ->
                                        if (ok) {
                                            stage = RecoveryStage.NEW_PIN
                                            message = null
                                        } else {
                                            message = wrongCode
                                        }
                                    }
                                }
                            },
                            placeholder = stringResource(R.string.recovery_code_placeholder),
                            isError = message != null,
                            errorMessage = message,
                            textStyle = MonoCaption.copy(fontSize = 16.sp, letterSpacing = 1.sp),
                        )
                    }
                    RecoveryStage.BACKUP -> {
                        Text(
                            stringResource(R.string.recovery_backup_body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = c.mutedForeground,
                        )
                        // Someone with no recovery code has landed here as their only route, so
                        // say what happens if the file doesn't exist either. Anyone who still has
                        // a code doesn't need to be told this and shouldn't be worried by it.
                        if (!recoveryAvailable) {
                            Text(
                                stringResource(R.string.recovery_backup_last_resort),
                                style = MaterialTheme.typography.bodySmall,
                                color = c.mutedForeground,
                            )
                        }
                        ShButton(
                            text = stringResource(
                                if (backupUri == null) R.string.recovery_backup_choose_file
                                else R.string.recovery_backup_file_selected,
                            ),
                            onClick = { picker.launch(arrayOf("*/*")) },
                            variant = ButtonVariant.Outline,
                            leadingIcon = Icons.Filled.FolderOpen,
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        ShInput(
                            value = backupPassword,
                            onValueChange = { backupPassword = it; message = null },
                            placeholder = stringResource(R.string.recovery_backup_password),
                            enabled = !busy,
                            isError = message != null,
                            errorMessage = message,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            visualTransformation = PasswordVisualTransformation(),
                        )
                    }
                    RecoveryStage.NEW_PIN -> {
                        Text(
                            stringResource(R.string.recovery_new_pin_body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = c.mutedForeground,
                        )
                        ShInput(
                            value = newPin,
                            onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) { newPin = it; message = null } },
                            placeholder = stringResource(R.string.recovery_new_pin),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            visualTransformation = PasswordVisualTransformation(),
                        )
                        ShInput(
                            value = confirmPin,
                            onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) { confirmPin = it; message = null } },
                            placeholder = stringResource(R.string.recovery_confirm_pin),
                            isError = message != null,
                            errorMessage = message,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            visualTransformation = PasswordVisualTransformation(),
                        )
                    }
                }
                message?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = c.destructiveForeground)
                }
            }
        },
        confirmButton = {
            when (stage) {
                RecoveryStage.BACKUP -> ShButton(
                    text = stringResource(
                        if (busy) R.string.recovery_backup_checking else R.string.action_continue,
                    ),
                    enabled = !busy && backupUri != null && backupPassword.isNotEmpty(),
                    onClick = {
                        val uri = backupUri ?: return@ShButton
                        busy = true
                        message = null
                        viewModel.verifyBackupFile(uri, backupPassword) { ok, error ->
                            busy = false
                            if (ok) {
                                // The password is no longer needed; don't leave it sitting in state.
                                backupPassword = ""
                                stage = RecoveryStage.NEW_PIN
                            } else {
                                message = error
                            }
                        }
                    },
                )
                RecoveryStage.NEW_PIN -> ShButton(
                    text = stringResource(R.string.recovery_set_pin_and_unlock),
                    enabled = newPin.length in 4..6,
                    onClick = {
                        if (newPin != confirmPin) {
                            message = pinsDiffer
                        } else {
                            viewModel.setNewPin(newPin) { onRecovered() }
                        }
                    },
                )
                else -> Unit
            }
        },
        dismissButton = {
            // From a route that was reached through the chooser, the way out is back to the chooser,
            // not out of recovery entirely - someone whose code didn't work should find the other
            // route without starting over.
            val canGoBack = recoveryAvailable &&
                (stage == RecoveryStage.CODE || stage == RecoveryStage.BACKUP)
            ShButton(
                text = stringResource(if (canGoBack) R.string.action_back else R.string.action_cancel),
                onClick = {
                    if (canGoBack) {
                        stage = RecoveryStage.CHOOSE
                        message = null
                    } else {
                        onDismiss()
                    }
                },
                variant = ButtonVariant.Ghost,
                enabled = !busy,
            )
        },
    )
}

private enum class RecoveryStage { CHOOSE, CODE, BACKUP, NEW_PIN }

/** Lock-screen mark: a shield on a hairline ring, with one sweeping arc while the PIN is live. */
@Composable
private fun LockMark(alert: Boolean) {
    val c = Guardia.colors
    val ring = if (alert) c.destructive else c.success
    val reduced = com.guardia.app.ui.components.rememberReducedMotion()
    val sweepAngle = if (reduced || alert) {
        -90f
    } else {
        val transition = rememberInfiniteTransition(label = "lockMark")
        val v by transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing)),
            label = "sweep",
        )
        v
    }

    Box(contentAlignment = Alignment.Center) {
        // A single sweeping arc on a hairline track, matching the dashboard's status gauge. The old
        // mark stacked a radial glow, a sweep-gradient scan ring and a gradient-filled shield; in a
        // flat neutral system that reads as decoration, so only the motion that means "checking"
        // survived.
        androidx.compose.foundation.Canvas(Modifier.size(116.dp)) {
            val stroke = 3.dp.toPx()
            drawCircle(color = c.muted, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
            drawArc(
                color = ring,
                startAngle = sweepAngle,
                sweepAngle = if (alert) 360f else 100f,
                useCenter = false,
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    stroke,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round,
                ),
            )
        }
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(if (alert) c.destructiveSubtle else c.muted),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Shield,
                contentDescription = null,
                tint = ring,
                modifier = Modifier.size(36.dp),
            )
        }
    }
}
