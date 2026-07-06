package com.guardia.app.ui.screens.dashboard

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GppBad
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.guardia.app.core.guard.GuardActivity
import com.guardia.app.core.guard.GuardState
import com.guardia.app.core.system.DeviceAdminManager
import com.guardia.app.ui.components.GuardiaCard
import com.guardia.app.ui.components.StatusOrb
import com.guardia.app.ui.components.SectionHeader
import com.guardia.app.ui.components.StatTile
import com.guardia.app.ui.theme.Spacing

@Composable
fun DashboardScreen(
    onLock: () -> Unit,
    onOpenPeople: () -> Unit = {},
    onOpenActivity: () -> Unit = {},
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.guardState.collectAsStateWithLifecycle()
    val peopleCount by viewModel.peopleCount.collectAsStateWithLifecycle()
    val intruderCount by viewModel.intruderCount.collectAsStateWithLifecycle()
    val lastIntruderAt by viewModel.lastIntruderAt.collectAsStateWithLifecycle()
    val testMode by viewModel.testMode.collectAsStateWithLifecycle()
    val responsiveness by viewModel.responsiveness.collectAsStateWithLifecycle()
    val appActivity by viewModel.appActivity.collectAsStateWithLifecycle()
    val pinSet by viewModel.pinSet.collectAsStateWithLifecycle()
    val setupDismissed by viewModel.setupDismissed.collectAsStateWithLifecycle()
    val disclosureAccepted by viewModel.disclosureAccepted.collectAsStateWithLifecycle()
    val protectedNow = state == GuardState.PROTECTED
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current
    var showDisclosure by remember { mutableStateOf(false) }

    if (showDisclosure) {
        BackgroundCameraDisclosureDialog(
            onAgree = {
                showDisclosure = false
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                viewModel.acceptDisclosureAndStart()
            },
            onDismiss = { showDisclosure = false },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screen),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Spacer(Modifier.height(Spacing.md))
        HeroStatusCard(
            state = state,
            protectedNow = protectedNow,
            onToggle = {
                // Google Play requires a prominent, in-context disclosure before background camera
                // collection begins. Show it the first time the user turns guarding on.
                if (!protectedNow && !disclosureAccepted) {
                    showDisclosure = true
                } else {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.toggleGuarding()
                }
            },
        )
        if (!setupDismissed) {
            SetupChecklistCard(
                pinSet = pinSet,
                peopleEnrolled = peopleCount > 0,
                onAddPerson = onOpenPeople,
                onDismiss = { viewModel.dismissSetup() },
            )
        }
        DeviceAdminCard()
        SectionHeader("Overview")
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            StatTile(
                Icons.Filled.People,
                peopleCount.toString(),
                "Enrolled people",
                Modifier.weight(1f),
                onClick = onOpenPeople,
            )
            StatTile(
                Icons.Filled.Warning,
                intruderCount.toString(),
                "Intruder events",
                Modifier.weight(1f),
                tint = if (intruderCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                caption = lastIntruderAt?.let {
                    DateUtils.getRelativeTimeSpanString(
                        it,
                        System.currentTimeMillis(),
                        DateUtils.MINUTE_IN_MILLIS,
                        DateUtils.FORMAT_ABBREV_RELATIVE,
                    ).toString()
                },
                onClick = onOpenActivity,
            )
        }
        BatteryCard(guarding = protectedNow, responsiveness = responsiveness)
        AppBatteryCard(appActivity)
        TestModeCard(
            enabled = testMode,
            onChange = viewModel::setTestMode,
            onTestLock = {
                if (!viewModel.testDeviceLock()) {
                    android.widget.Toast.makeText(
                        context,
                        "Can't lock yet — enable Device Admin (or the App-detection service) first.",
                        android.widget.Toast.LENGTH_LONG,
                    ).show()
                }
            },
        )
        OutlinedButton(
            onClick = onLock,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(26.dp),
        ) {
            Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(Spacing.sm))
            Text("Lock Guardia")
        }
        Spacer(Modifier.height(100.dp))
    }
}

@Composable
private fun SetupChecklistCard(
    pinSet: Boolean,
    peopleEnrolled: Boolean,
    onAddPerson: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var adminActive by remember { mutableStateOf(DeviceAdminManager.isAdminActive(context)) }
    var accessibilityOn by remember { mutableStateOf(isAccessibilityEnabled(context)) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                adminActive = DeviceAdminManager.isAdminActive(context)
                accessibilityOn = isAccessibilityEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val steps = listOf(
        SetupStep("Set your PIN", pinSet) { android.widget.Toast.makeText(context, "Set this in Settings > Access & PINs", android.widget.Toast.LENGTH_SHORT).show() },
        SetupStep("Enroll your face", peopleEnrolled, onAddPerson),
        SetupStep("Enable device locking", adminActive) {
            runCatching { context.startActivity(DeviceAdminManager.enableIntent(context)) }
        },
        SetupStep("Enable App Lock service", accessibilityOn) {
            runCatching { context.startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        },
    )
    val done = steps.count { it.complete }
    if (done == steps.size) return

    GuardiaCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(Spacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Get protected", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("$done/${steps.size}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(Spacing.sm))
            LinearProgressIndicator(
                progress = { done.toFloat() / steps.size },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )
            Spacer(Modifier.height(Spacing.md))
            steps.forEach { step ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .let { if (!step.complete) it.clickable(onClick = step.action) else it }
                        .padding(vertical = Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (step.complete) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                        contentDescription = null,
                        tint = if (step.complete) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(Spacing.md))
                    Text(
                        step.label,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (step.complete) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.weight(1f))
                    if (!step.complete) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(Modifier.height(Spacing.xs))
            androidx.compose.material3.TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                Text("Dismiss")
            }
        }
    }
}

private data class SetupStep(val label: String, val complete: Boolean, val action: () -> Unit)

private fun isAccessibilityEnabled(context: Context): Boolean {
    val flat = android.provider.Settings.Secure.getString(
        context.contentResolver,
        android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
    ).orEmpty()
    return flat.contains("${context.packageName}/")
}

@Composable
private fun DeviceAdminCard() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var active by remember { mutableStateOf(DeviceAdminManager.isAdminActive(context)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        active = DeviceAdminManager.isAdminActive(context)
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) active = DeviceAdminManager.isAdminActive(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    if (active) return

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(Spacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.AdminPanelSettings, contentDescription = null)
                Spacer(Modifier.width(Spacing.sm))
                Text("Finish setup", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(Spacing.sm))
            Text(
                "Guardia needs Device Admin permission to lock the screen when an unauthorized person is detected. Without it, guarding can still detect and capture, but cannot lock.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(Spacing.md))
            Button(
                onClick = { launcher.launch(DeviceAdminManager.enableIntent(context)) },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) {
                Icon(Icons.Filled.AdminPanelSettings, contentDescription = null)
                Spacer(Modifier.width(Spacing.sm))
                Text("Enable device locking")
            }
        }
    }
}

@Composable
private fun BatteryCard(guarding: Boolean, responsiveness: Int) {
    val context = LocalContext.current
    var level by remember { mutableIntStateOf(batteryLevel(context)) }
    var charging by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
                val raw = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                if (raw >= 0) level = (raw * 100) / scale
                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL
            }
        }
        context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }

    val (impactLabel, impactDetail) = when {
        !guarding -> "Idle" to "Guarding is off - no extra battery use."
        responsiveness == 0 -> "Low impact" to "Battery saver profile - fewer camera checks."
        responsiveness == 2 -> "Higher impact" to "Max security profile - frequent camera checks."
        else -> "Moderate impact" to "Balanced profile - sensor-gated checks."
    }
    val barColor = when {
        level <= 15 -> MaterialTheme.colorScheme.error
        level <= 35 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }

    GuardiaCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(Spacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                com.guardia.app.ui.components.IconChip(
                    if (charging) Icons.Filled.BatteryChargingFull else Icons.Filled.BatteryFull,
                    tint = barColor,
                )
                Spacer(Modifier.width(Spacing.md))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Device battery", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (charging) "$level% - charging" else "$level%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(impactLabel, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = barColor)
            }
            Spacer(Modifier.height(Spacing.md))
            LinearProgressIndicator(
                progress = { level / 100f },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                color = barColor,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )
            Spacer(Modifier.height(Spacing.sm))
            Text(impactDetail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AppBatteryCard(activity: GuardActivity) {
    val accent = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val percentLabel = when {
        activity.estimatedPercent <= 0f -> "0%"
        activity.estimatedPercent < 0.1f -> "<0.1%"
        else -> "~" + String.format("%.1f", activity.estimatedPercent) + "%"
    }
    val maxBar = (activity.hourly.maxOrNull() ?: 0).coerceAtLeast(1)

    GuardiaCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(Spacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                com.guardia.app.ui.components.IconChip(Icons.Filled.BatteryStd, tint = accent)
                Spacer(Modifier.width(Spacing.md))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Guardia battery use", style = MaterialTheme.typography.titleMedium)
                    Text("Last 24 hours", style = MaterialTheme.typography.bodySmall, color = muted)
                }
                Text(
                    percentLabel,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = accent,
                )
            }
            Spacer(Modifier.height(Spacing.md))
            Row(
                modifier = Modifier.fillMaxWidth().height(40.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                activity.hourly.forEach { value ->
                    val frac = value.toFloat() / maxBar
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height((3 + frac * 37).dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (value > 0) accent else MaterialTheme.colorScheme.surfaceContainerHighest),
                    )
                }
            }
            Spacer(Modifier.height(Spacing.md))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.xl)) {
                MiniStat(activity.checks24h.toString(), "Checks")
                MiniStat(formatCameraTime(activity.cameraSeconds24h), "Camera time")
            }
            Spacer(Modifier.height(Spacing.sm))
            Text(
                "Estimated from camera activity. Actual drain varies by device.",
                style = MaterialTheme.typography.bodySmall,
                color = muted,
            )
        }
    }
}

@Composable
private fun MiniStat(value: String, label: String) {
    Column {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun formatCameraTime(seconds: Long): String = when {
    seconds <= 0 -> "0s"
    seconds < 60 -> "${seconds}s"
    seconds < 3600 -> "${seconds / 60}m ${seconds % 60}s"
    else -> "${seconds / 3600}h ${(seconds % 3600) / 60}m"
}

private fun batteryLevel(context: Context): Int {
    val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
    return bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.coerceIn(0, 100) ?: 0
}

@Composable
private fun TestModeCard(enabled: Boolean, onChange: (Boolean) -> Unit, onTestLock: () -> Unit) {
    GuardiaCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(Spacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                com.guardia.app.ui.components.IconChip(Icons.Filled.Science, tint = MaterialTheme.colorScheme.tertiary)
                Spacer(Modifier.width(Spacing.md))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Test mode", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Show recognition results as notifications instead of locking the device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(Spacing.sm))
                Switch(checked = enabled, onCheckedChange = onChange)
            }
            Spacer(Modifier.height(Spacing.md))
            OutlinedButton(onClick = onTestLock, modifier = Modifier.fillMaxWidth().height(46.dp)) {
                Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(Spacing.sm))
                Text("Test device lock now")
            }
            Text(
                "Locks this device right now so you can confirm locking works on your phone.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }
    }
}

@Composable
private fun HeroStatusCard(
    state: GuardState,
    protectedNow: Boolean,
    onToggle: () -> Unit,
) {
    val (label, sub, color) = when (state) {
        GuardState.PROTECTED -> Triple("PROTECTED", "Watching who uses this device", MaterialTheme.colorScheme.primary)
        GuardState.PAUSED -> Triple("PAUSED", "Guarding temporarily paused", MaterialTheme.colorScheme.tertiary)
        GuardState.NEEDS_ATTENTION -> Triple("NEEDS ATTENTION", "Check permissions to continue", MaterialTheme.colorScheme.error)
        GuardState.STOPPED -> Triple("OFF", "Guarding is not active", MaterialTheme.colorScheme.onSurfaceVariant)
    }
    val animColor by animateColorAsState(color, label = "statusColor")
    val heroIcon = when (state) {
        GuardState.PROTECTED -> Icons.Filled.VerifiedUser
        GuardState.NEEDS_ATTENTION -> Icons.Filled.GppBad
        else -> Icons.Filled.Shield
    }

    // No card here — the status hero sits directly on the background for a cleaner, futuristic feel.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.sm, vertical = Spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // A soft ambient glow behind the orb adds depth so the hero feels lit from within.
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(300.dp)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(animColor.copy(alpha = if (protectedNow) 0.16f else 0.05f), Color.Transparent),
                        ),
                        shape = CircleShape,
                    ),
            )
            StatusOrb(active = protectedNow, icon = heroIcon, accent = animColor, size = 200.dp)
        }
        Spacer(Modifier.height(Spacing.lg))
        StatusChip(label = label, color = animColor, live = protectedNow)
        Spacer(Modifier.height(Spacing.sm))
        Text(
            sub,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.lg))
        GuardPrimaryAction(protectedNow = protectedNow, accent = animColor, onToggle = onToggle)
    }
}

/**
 * The dashboard's single most important control. When off, a glowing gradient pill ("Start
 * guarding") that clearly invites action; when on, a restrained outlined "Stop guarding" so turning
 * protection off never looks like the primary thing to do.
 */
@Composable
private fun GuardPrimaryAction(protectedNow: Boolean, accent: Color, onToggle: () -> Unit) {
    val shape = RoundedCornerShape(30.dp)
    if (protectedNow) {
        OutlinedButton(
            onClick = onToggle,
            modifier = Modifier.fillMaxWidth().height(58.dp),
            shape = shape,
            border = androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f)),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) {
            Icon(Icons.Filled.PauseCircle, contentDescription = null)
            Spacer(Modifier.width(Spacing.sm))
            Text("Stop guarding", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
    } else {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .shadow(18.dp, shape, clip = false, ambientColor = accent, spotColor = accent)
                .clip(shape)
                .background(com.guardia.app.ui.theme.GuardiaHeroGradient)
                .clickable(onClick = onToggle),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Shield, contentDescription = null, tint = Color.White)
                Spacer(Modifier.width(Spacing.sm))
                Text(
                    "Start guarding",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }
        }
    }
}

/**
 * Prominent disclosure shown before the first time guarding starts, satisfying Google Play's
 * requirement to disclose background sensor (camera) access in-context, separate from the privacy
 * policy, with an explicit accept/decline choice.
 */
@Composable
private fun BackgroundCameraDisclosureDialog(onAgree: () -> Unit, onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text("Turn on guarding?") },
        text = {
            Text(
                "Guardia uses your front camera in the background to check whether the person using " +
                    "this device is you. Checks run only while the device is in use, a notification " +
                    "stays visible while guarding is on, and images are processed on your device and " +
                    "never uploaded.",
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            Button(onClick = onAgree) { Text("Turn on") }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Not now") }
        },
    )
}

@Composable
private fun StatusChip(label: String, color: Color, live: Boolean = false) {
    // Gentle pulse for the "live" dot so PROTECTED reads as actively watching.
    val transition = rememberInfiniteTransition(label = "chip")
    val dotAlpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            androidx.compose.animation.core.tween(1100),
            androidx.compose.animation.core.RepeatMode.Reverse,
        ),
        label = "dotAlpha",
    )
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(50))
            .padding(horizontal = Spacing.md, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (live) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = dotAlpha)),
            )
            Spacer(Modifier.width(Spacing.sm))
        }
        Text(
            label,
            style = com.guardia.app.ui.theme.StatusReadout,
            color = color,
        )
    }
}
