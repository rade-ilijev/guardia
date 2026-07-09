package com.guardia.app.ui.screens.settings

import android.Manifest
import android.net.Uri
import com.guardia.app.R
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import com.guardia.app.ui.theme.Spacing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.guardia.app.core.system.DeviceAdminManager
import com.guardia.app.ui.components.BannerTone
import com.guardia.app.ui.components.GuardiaScaffold
import com.guardia.app.ui.components.InfoBanner
import com.guardia.app.ui.components.RadioRow
import com.guardia.app.ui.components.RowDivider
import com.guardia.app.ui.components.SettingsColumn
import com.guardia.app.ui.components.SettingsGroup
import com.guardia.app.ui.components.SliderRow
import com.guardia.app.ui.components.SwitchRow
import kotlin.math.roundToInt

@Composable
fun SettingsDetailScreen(
    categoryKey: String,
    onBack: () -> Unit,
    onUpgrade: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val title = settingsCategories.firstOrNull { it.key == categoryKey }?.title ?: "Settings"

    GuardiaScaffold(title = title, onBack = onBack) { padding ->
        SettingsColumn(padding) {
            when (categoryKey) {
                "guarding" -> GuardingSection(viewModel, onUpgrade)
                "detection" -> DetectionSection(viewModel)
                "appcheck" -> AppCheckSection()
                "response" -> ResponseSection(viewModel)
                "voice" -> VoiceSection(viewModel)
                "location" -> LocationSection(onUpgrade = onUpgrade)
                "privacy" -> PrivacySection(viewModel)
                "system" -> SystemSection()
                "applock" -> AppLockSection(onUpgrade = onUpgrade)
                "profiles" -> ProfilesSection(onUpgrade = onUpgrade)
                "alerts" -> AlertsSection(onUpgrade = onUpgrade)
                "scanner" -> ScannerSection()
                "cameramic" -> CameraMicSection()
                "pins" -> PinsSection()
                "account" -> AccountSection(onUpgrade = onUpgrade)
                else -> InfoBanner("This section is part of the roadmap.", Icons.Filled.Info)
            }
        }
    }
}

@Composable
private fun UpgradeButton(onUpgrade: () -> Unit) {
    Button(onClick = onUpgrade, modifier = Modifier.fillMaxWidth().height(48.dp)) {
        Icon(Icons.Filled.WorkspacePremium, contentDescription = null)
        Spacer(Modifier.width(Spacing.sm))
        Text("See Guardia Premium")
    }
}

@Composable
private fun GuardingSection(viewModel: SettingsViewModel, onUpgrade: () -> Unit = {}) {
    val responsiveness by viewModel.responsiveness.collectAsStateWithLifecycle()
    val intervalEnabled by viewModel.intervalCheckEnabled.collectAsStateWithLifecycle()
    val customInterval by viewModel.customIntervalSeconds.collectAsStateWithLifecycle()
    val firstCheck by viewModel.firstCheckOnUnlock.collectAsStateWithLifecycle()
    val shake by viewModel.shakeToCheck.collectAsStateWithLifecycle()
    val ramp by viewModel.checkRamp.collectAsStateWithLifecycle()
    val premium by viewModel.premium.collectAsStateWithLifecycle()

    InfoBanner(
        "Guardia checks your face in the background while you're using the phone. Pick how often below — checking more often is more secure but uses a little more battery.",
        Icons.Filled.Info,
    )

    SettingsGroup(title = "Background checks") {
        SwitchRow(
            "Periodic checks",
            intervalEnabled,
            viewModel::setIntervalCheckEnabled,
            subtitle = "Check your face on a schedule while you use the phone. Turn off to only check when chosen apps open.",
        )
    }

    if (intervalEnabled) {
        val usingCustom = premium && customInterval > 0
        // The profile picker and the custom interval are two ways to set the same thing, so only
        // show the profiles when a custom interval isn't overriding them.
        if (!usingCustom) {
            val labels = listOf(
                Triple("Battery saver", "A check every 5 minutes while unlocked.", 0),
                Triple("Balanced", "A check every 2.5 minutes while unlocked.", 1),
                Triple("Max security", "A check every minute while unlocked.", 2),
            )
            SettingsGroup(title = "How often to check") {
                labels.forEachIndexed { index, (label, sub, value) ->
                    RadioRow(
                        label = label,
                        subtitle = sub,
                        selected = responsiveness == value,
                        onClick = { viewModel.setResponsiveness(value) },
                    )
                    if (index < labels.lastIndex) RowDivider()
                }
            }
        }

        if (premium) {
            com.guardia.app.ui.components.SectionHeader("Custom interval", premium = true)
            CustomIntervalCard(seconds = customInterval, onChange = viewModel::setCustomIntervalSeconds)

            SettingsGroup(title = "On unlock") {
                SwitchRow(
                    "Check on unlock",
                    firstCheck,
                    viewModel::setFirstCheckOnUnlock,
                    subtitle = "Run a face check the instant the device is unlocked.",
                    premium = true,
                )
            }

            if (firstCheck) {
                com.guardia.app.ui.components.SectionHeader("Speed ramp", premium = true)
                InfoBanner(
                    "After the unlock check, run a few faster checks and then settle into the steady interval. Each step is the delay before the next check.",
                    Icons.Filled.Bolt,
                )
                RampEditor(ramp = ramp, onChange = viewModel::setRampList)
            }

            SettingsGroup(title = "Motion") {
                SwitchRow(
                    "Check on shake",
                    shake,
                    viewModel::setShakeToCheck,
                    subtitle = "Shake the phone to force an extra face check right away.",
                    premium = true,
                )
            }
        } else {
            InfoBanner(
                "Custom intervals, an instant check on unlock, a customizable speed ramp, and shake-to-check are Guardia Premium features.",
                Icons.Filled.Info,
                tone = BannerTone.Warning,
            )
            Spacer(Modifier.height(Spacing.sm))
            UpgradeButton(onUpgrade)
        }
    }

    InfoBanner(
        "Guardia only samples the front camera while the screen is on and the phone is unlocked — never on the lock screen.",
        Icons.Filled.Bolt,
    )
}

@Composable
private fun RampEditor(ramp: List<Int>, onChange: (List<Int>) -> Unit) {
    com.guardia.app.ui.components.GuardiaCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth()) {
            if (ramp.isEmpty()) {
                Text(
                    "No extra checks yet. Add a step to check again shortly after unlocking.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(Spacing.lg),
                )
            } else {
                ramp.forEachIndexed { index, seconds ->
                    RampStepRow(
                        index = index,
                        seconds = seconds,
                        onChange = { v -> onChange(ramp.mapIndexed { i, old -> if (i == index) v.coerceIn(1, 120) else old }) },
                        onRemove = { onChange(ramp.filterIndexed { i, _ -> i != index }) },
                    )
                    if (index < ramp.lastIndex) RowDivider()
                }
            }
        }
    }
    Spacer(Modifier.height(Spacing.sm))
    OutlinedButton(onClick = { onChange(ramp + 10) }, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Filled.Add, contentDescription = null)
        Spacer(Modifier.width(Spacing.sm))
        Text("Add a check step")
    }
}

/** Source toggle for a place: follow the global Guarding & Triggers schedule, or customize it here. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSourceToggle(useDefault: Boolean, onChange: (Boolean) -> Unit) {
    com.guardia.app.ui.components.SectionHeader("Settings here", premium = true)
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        SegmentedButton(
            selected = useDefault,
            onClick = { onChange(true) },
            shape = SegmentedButtonDefaults.itemShape(0, 2),
        ) { Text("Use default") }
        SegmentedButton(
            selected = !useDefault,
            onClick = { onChange(false) },
            shape = SegmentedButtonDefaults.itemShape(1, 2),
        ) { Text("Custom") }
    }
}

/**
 * The full Guarding & Triggers option set for a single place (public or a safe zone): responsiveness
 * or a custom interval, check-on-unlock with a speed ramp, shake-to-check, and lock-on-no-face.
 */
@Composable
private fun PlaceScheduleEditor(
    responsiveness: Int,
    onResponsiveness: (Int) -> Unit,
    customInterval: Int,
    onCustomInterval: (Int) -> Unit,
    firstCheck: Boolean,
    onFirstCheck: (Boolean) -> Unit,
    ramp: List<Int>,
    onRamp: (List<Int>) -> Unit,
    shake: Boolean,
    onShake: (Boolean) -> Unit,
    lockOnNoFace: Boolean,
    onLockOnNoFace: (Boolean) -> Unit,
) {
    if (customInterval <= 0) {
        com.guardia.app.ui.components.SectionHeader("Check frequency")
        ResponsivenessPicker(selected = responsiveness, onSelect = onResponsiveness)
    }
    com.guardia.app.ui.components.SectionHeader("Custom interval", premium = true)
    CustomIntervalCard(seconds = customInterval, onChange = onCustomInterval)
    SettingsGroup(title = "On unlock") {
        SwitchRow(
            "Check on unlock",
            firstCheck,
            onFirstCheck,
            subtitle = "Run a face check the instant the device is unlocked here.",
            premium = true,
        )
    }
    if (firstCheck) {
        com.guardia.app.ui.components.SectionHeader("Speed ramp", premium = true)
        RampEditor(ramp = ramp, onChange = onRamp)
    }
    SettingsGroup(title = "Motion") {
        SwitchRow(
            "Check on shake",
            shake,
            onShake,
            subtitle = "Shake the phone to force an extra check here.",
            premium = true,
        )
    }
    SettingsGroup(title = "Detection") {
        SwitchRow(
            "Lock when no face is visible",
            lockOnNoFace,
            onLockOnNoFace,
            subtitle = "Lock here if the camera can't see any face for a few seconds.",
        )
    }
}

@Composable
private fun RampStepRow(index: Int, seconds: Int, onChange: (Int) -> Unit, onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Check ${index + 2}", style = MaterialTheme.typography.bodyLarge)
            Text(
                "${seconds}s after the previous check",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = { onChange(seconds - 1) }, enabled = seconds > 1) {
            Icon(Icons.Filled.Remove, contentDescription = "Decrease")
        }
        Text(
            "${seconds}s",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.width(44.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        IconButton(onClick = { onChange(seconds + 1) }, enabled = seconds < 120) {
            Icon(Icons.Filled.Add, contentDescription = "Increase")
        }
        IconButton(onClick = onRemove) {
            Icon(Icons.Filled.Delete, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error)
        }
    }
}

/**
 * Premium custom interval. Stored as seconds; the UI lets the user pick a value in seconds OR
 * minutes (1–60 of either), so intervals from 1 second up to an hour are possible.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomIntervalCard(seconds: Int, onChange: (Int) -> Unit) {
    val enabled = seconds > 0
    // Any whole-minute value (>= 60s) is shown in minutes; sub-minute values stay in seconds.
    // Keyed on `seconds` so the unit re-derives once the stored value loads (and after edits).
    val derivedMinutes = seconds >= 60 && seconds % 60 == 0
    var useMinutes by remember(seconds) { mutableStateOf(derivedMinutes) }
    var pos by remember(seconds, useMinutes) {
        mutableStateOf((if (useMinutes) seconds / 60f else seconds.toFloat()).coerceIn(1f, 60f))
    }

    com.guardia.app.ui.components.GuardiaCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth()) {
            SwitchRow(
                "Custom interval",
                enabled,
                { on -> onChange(if (on) 30 else 0) },
                subtitle = "Set your own steady interval instead of the profile above.",
            )
            if (enabled) {
                RowDivider()
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                ) {
                    SegmentedButton(
                        selected = !useMinutes,
                        onClick = {
                            if (useMinutes) {
                                // Minutes -> seconds: keep the duration but cap at the 59s range.
                                useMinutes = false
                                onChange((pos.roundToInt() * 60).coerceIn(1, 59))
                            }
                        },
                        shape = SegmentedButtonDefaults.itemShape(0, 2),
                    ) { Text("Seconds") }
                    SegmentedButton(
                        selected = useMinutes,
                        onClick = {
                            if (!useMinutes) {
                                // Seconds -> minutes: round to the nearest minute (>= 1).
                                useMinutes = true
                                onChange((pos / 60f).roundToInt().coerceAtLeast(1) * 60)
                            }
                        },
                        shape = SegmentedButtonDefaults.itemShape(1, 2),
                    ) { Text("Minutes") }
                }
                // Seconds tops out at 59 so whole minutes are never ambiguous with seconds.
                val maxVal = if (useMinutes) 60f else 59f
                val rounded = pos.roundToInt().coerceIn(1, maxVal.toInt())
                SliderRow(
                    title = "Check every",
                    valueLabel = if (useMinutes) "$rounded min" else "${rounded}s",
                    value = pos.coerceIn(1f, maxVal),
                    onValueChange = { pos = it },
                    onValueChangeFinished = {
                        val r = pos.roundToInt().coerceIn(1, maxVal.toInt())
                        onChange(if (useMinutes) r * 60 else r)
                    },
                    valueRange = 1f..maxVal,
                    subtitle = "Steady interval once any unlock ramp finishes.",
                )
            }
        }
    }
}

@Composable
private fun DetectionSection(viewModel: SettingsViewModel) {
    val sensitivity by viewModel.sensitivity.collectAsStateWithLifecycle()
    val testMode by viewModel.testMode.collectAsStateWithLifecycle()
    val lowLight by viewModel.lowLightAction.collectAsStateWithLifecycle()

    InfoBanner(
        "Sensitivity controls how confident Guardia must be that a face is yours. Choosing who triggers a lock lives in Response Actions.",
        Icons.Filled.Info,
    )
    SettingsGroup(title = "Match sensitivity") {
        SliderRow(
            title = "Strictness",
            valueLabel = "${(sensitivity * 100).roundToInt()}%",
            value = sensitivity,
            onValueChange = viewModel::setSensitivity,
            valueRange = 0f..1f,
            subtitle = "Higher is stricter: fewer strangers slip through, but you may need a clearer look at the camera.",
        )
    }

    InfoBanner(
        "In the dark, the camera often can't see a face — so by default Guardia couldn't decide and wouldn't act. Choose what should happen when it's too dark to verify.",
        Icons.Filled.Info,
    )
    val lowLightOptions = listOf(
        Triple("Brighten screen & try again", "First raise brightness to 100% and re-check; if still too dark, briefly light the screen to see your face. Recommended.", 1),
        Triple("Lock the device", "If it stays too dark to verify, treat it as a threat and lock.", 2),
        Triple("Do nothing", "Never act when it's too dark (fewest false alarms, least protection).", 0),
    )
    SettingsGroup(title = "When it's too dark") {
        lowLightOptions.forEachIndexed { index, (label, sub, value) ->
            RadioRow(
                label = label,
                subtitle = sub,
                selected = lowLight == value,
                onClick = { viewModel.setLowLightAction(value) },
            )
            if (index < lowLightOptions.lastIndex) RowDivider()
        }
    }
    if (lowLight == 2) {
        InfoBanner(
            "Locking on darkness can lock more often (e.g. in your pocket or a dim room). Brighten & try again is gentler.",
            Icons.Filled.Info,
            tone = BannerTone.Warning,
        )
    }

    SettingsGroup(title = "Testing") {
        SwitchRow(
            "Test mode",
            testMode,
            viewModel::setTestMode,
            subtitle = "Preview results as notifications without ever locking the device. Great for tuning sensitivity.",
        )
    }
}

@Composable
private fun ResponseSection(viewModel: SettingsViewModel) {
    val unknown by viewModel.lockOnUnknownFace.collectAsStateWithLifecycle()
    val blocked by viewModel.lockOnBlockedPerson.collectAsStateWithLifecycle()
    val multiFace by viewModel.lockOnMultipleFaces.collectAsStateWithLifecycle()
    val noFace by viewModel.lockOnNoFace.collectAsStateWithLifecycle()
    val capture by viewModel.captureIntruders.collectAsStateWithLifecycle()
    val wrongUnlockThreshold by viewModel.wrongUnlockThreshold.collectAsStateWithLifecycle()

    InfoBanner(
        "Choose exactly when Guardia locks the phone during a background check. Locking uses Device Admin — enable it in System & Reliability.",
        Icons.Filled.Info,
    )
    SettingsGroup(title = "Lock the device when…") {
        SwitchRow(
            "Someone unrecognized is seen",
            unknown,
            viewModel::setLockOnUnknownFace,
            subtitle = "A face that isn't enrolled is using the phone. This is the core protection.",
        )
        RowDivider()
        SwitchRow(
            "A blocked look-alike is seen",
            blocked,
            viewModel::setLockOnBlockedPerson,
            subtitle = "A person on your Blocked list (e.g. a sibling who looks like you).",
        )
        RowDivider()
        SwitchRow(
            "More than one face is seen",
            multiFace,
            viewModel::setLockOnMultipleFaces,
            subtitle = "Someone is looking over your shoulder.",
        )
        RowDivider()
        SwitchRow(
            "No face is visible",
            noFace,
            viewModel::setLockOnNoFace,
            subtitle = "The camera is turned away or covered for a few seconds.",
        )
    }
    if (!unknown) {
        InfoBanner(
            "With \"unrecognized\" off, Guardia won't lock when a stranger uses your phone during background checks. Keep this on for real protection.",
            Icons.Filled.Info,
            tone = BannerTone.Danger,
        )
    }
    if (noFace) {
        InfoBanner(
            "Locking on no face is sensitive: in the dark or when you glance away it can lock more often.",
            Icons.Filled.Info,
            tone = BannerTone.Warning,
        )
    }
    AppearanceRules(viewModel)

    SettingsGroup(title = "Evidence") {
        SwitchRow(
            "Capture intruders",
            capture,
            viewModel::setCaptureIntruders,
            subtitle = "Save an encrypted selfie of whoever triggered the lock. View them in Activity.",
        )
        if (capture) {
            RowDivider()
            StepperRow(
                title = "Capture after failed unlocks",
                subtitle = if (wrongUnlockThreshold == 1)
                    "Snap a selfie on every wrong device-unlock attempt."
                else
                    "Snap a selfie after $wrongUnlockThreshold wrong unlock attempts in a row.",
                value = wrongUnlockThreshold,
                valueLabel = wrongUnlockThreshold.toString(),
                onDecrement = { viewModel.setWrongUnlockThreshold(wrongUnlockThreshold - 1) },
                onIncrement = { viewModel.setWrongUnlockThreshold(wrongUnlockThreshold + 1) },
                min = 1,
                max = 5,
            )
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun AppearanceRules(viewModel: SettingsViewModel) {
    val enabled by viewModel.appearanceRulesEnabled.collectAsStateWithLifecycle()
    val hair by viewModel.ignoreHairColors.collectAsStateWithLifecycle()
    val eyes by viewModel.ignoreEyeTones.collectAsStateWithLifecycle()

    SettingsGroup(title = "Appearance rules") {
        SwitchRow(
            "Relax by appearance",
            enabled,
            viewModel::setAppearanceRulesEnabled,
            subtitle = "Experimental. Skip locking for an unrecognized person whose estimated look matches what you choose below.",
        )
    }
    if (enabled) {
        InfoBanner(
            "Appearance (hair/eye tone) is estimated on-device from the camera and can be wrong, especially in poor light. This only relaxes locking — your block list and multi-face detection still always lock. Sex and other attributes are intentionally not used: they can't be estimated reliably or fairly from pixels.",
            Icons.Filled.Info,
            tone = BannerTone.Warning,
        )
        com.guardia.app.ui.components.GuardiaCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text("Don't lock for hair color", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                    listOf("DARK" to "Dark", "BROWN" to "Brown", "BLONDE" to "Blonde", "RED" to "Red", "GRAY" to "Gray")
                        .forEach { (key, label) ->
                            FilterChip(selected = key in hair, onClick = { viewModel.toggleIgnoreHair(key) }, label = { Text(label) })
                        }
                }
                Spacer(Modifier.height(16.dp))
                Text("Don't lock for eye tone", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                    listOf("DARK" to "Dark eyes", "LIGHT" to "Light eyes").forEach { (key, label) ->
                        FilterChip(selected = key in eyes, onClick = { viewModel.toggleIgnoreEye(key) }, label = { Text(label) })
                    }
                }
            }
        }
    }
}

/** A labelled −/+ stepper row for small bounded integer settings. */
@Composable
private fun StepperRow(
    title: String,
    subtitle: String,
    value: Int,
    valueLabel: String,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    min: Int,
    max: Int,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        androidx.compose.material3.FilledTonalIconButton(onClick = onDecrement, enabled = value > min) {
            Icon(Icons.Filled.Remove, contentDescription = "Decrease")
        }
        Text(
            valueLabel,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            modifier = Modifier.widthIn(min = 24.dp).padding(horizontal = 4.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        androidx.compose.material3.FilledTonalIconButton(onClick = onIncrement, enabled = value < max) {
            Icon(Icons.Filled.Add, contentDescription = "Increase")
        }
    }
}

@Composable
private fun LocationSection(onUpgrade: () -> Unit, viewModel: LocationViewModel = hiltViewModel()) {
    val premium by viewModel.premium.collectAsStateWithLifecycle()
    if (!premium) {
        PremiumGate(
            feature = "Location Protection",
            tagline = "Guardia adapts to where you are — relaxed at home, locked down in public — and pinpoints your phone if it goes missing.",
            benefits = listOf(
                "Safe zones with their own guarding rules",
                "Maximum security automatically in public",
                "Location included in your security alerts",
            ),
            onUpgrade = onUpgrade,
        ) { LocationPreview() }
        return
    }

    val context = LocalContext.current
    val locationMode by viewModel.locationMode.collectAsStateWithLifecycle()
    val publicGuard by viewModel.publicGuard.collectAsStateWithLifecycle()
    val publicUseDefault by viewModel.publicUseDefault.collectAsStateWithLifecycle()
    val publicResp by viewModel.publicResponsiveness.collectAsStateWithLifecycle()
    val publicCustomInterval by viewModel.publicCustomInterval.collectAsStateWithLifecycle()
    val publicFirstCheck by viewModel.publicFirstCheck.collectAsStateWithLifecycle()
    val publicRamp by viewModel.publicRamp.collectAsStateWithLifecycle()
    val publicShake by viewModel.publicShake.collectAsStateWithLifecycle()
    val publicNoFace by viewModel.publicLockOnNoFace.collectAsStateWithLifecycle()
    val zones by viewModel.zones.collectAsStateWithLifecycle()
    val policy by viewModel.policy.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    var hasFg by remember { mutableStateOf(viewModel.hasForegroundLocation()) }
    var hasBg by remember { mutableStateOf(viewModel.hasBackgroundLocation()) }
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        hasFg = viewModel.hasForegroundLocation()
        hasBg = viewModel.hasBackgroundLocation()
        onPauseOrDispose { }
    }

    val fgLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        hasFg = viewModel.hasForegroundLocation()
        if (hasFg) viewModel.onPermissionGranted()
    }

    message?.let { msg ->
        androidx.compose.runtime.LaunchedEffect(msg) {
            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
            viewModel.consumeMessage()
        }
    }

    var addOpen by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<com.guardia.app.domain.model.SafeZone?>(null) }

    InfoBanner(
        "Set places where you feel safe and choose whether Guardia guards there. Everywhere else is treated as public, with its own check frequency.",
        Icons.Filled.LocationOn,
    )

    SettingsGroup(title = "Location-based protection") {
        SwitchRow(
            "Use my location",
            locationMode,
            viewModel::setLocationMode,
            subtitle = "Switch guarding on/off and change how often it checks based on where you are.",
            enabled = hasFg,
            premium = true,
        )
    }

    if (!hasFg) {
        InfoBanner("Location permission is required to detect your safe zones.", Icons.Filled.Info, tone = BannerTone.Warning)
        OutlinedButton(
            onClick = {
                fgLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Grant location permission") }
        return
    }

    if (locationMode && !hasBg) {
        InfoBanner(
            "For guarding to follow your location while Guardia runs in the background, set location access to \"Allow all the time\".",
            Icons.Filled.Info,
            tone = BannerTone.Warning,
        )
        OutlinedButton(
            onClick = {
                context.startActivity(
                    android.content.Intent(
                        android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        android.net.Uri.parse("package:${context.packageName}"),
                    )
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Open location settings") }
    }

    com.guardia.app.ui.components.SectionHeader("Right now")
    com.guardia.app.ui.components.GuardiaCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(policy.label, style = MaterialTheme.typography.titleMedium)
                Text(
                    if (policy.guardEnabled) "Guarding is active here." else "Guarding is paused here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (policy.guardEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Updates automatically as you move.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { viewModel.refreshLocation() }) {
                Icon(Icons.Filled.Refresh, contentDescription = "Refresh location", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }

    SettingsGroup(title = "Public areas") {
        SwitchRow(
            "Guard in public",
            publicGuard,
            viewModel::setPublicGuard,
            subtitle = "Run checks whenever you're outside every safe zone.",
        )
    }
    if (publicGuard) {
        SettingsSourceToggle(useDefault = publicUseDefault, onChange = viewModel::setPublicUseDefault)
        if (publicUseDefault) {
            InfoBanner("Public areas use your Guarding & Triggers settings.", Icons.Filled.Info)
        } else {
            PlaceScheduleEditor(
                responsiveness = publicResp,
                onResponsiveness = viewModel::setPublicResponsiveness,
                customInterval = publicCustomInterval,
                onCustomInterval = viewModel::setPublicCustomInterval,
                firstCheck = publicFirstCheck,
                onFirstCheck = viewModel::setPublicFirstCheck,
                ramp = publicRamp,
                onRamp = viewModel::setPublicRamp,
                shake = publicShake,
                onShake = viewModel::setPublicShake,
                lockOnNoFace = publicNoFace,
                onLockOnNoFace = viewModel::setPublicLockOnNoFace,
            )
        }
    }

    com.guardia.app.ui.components.SectionHeader("Safe zones (${zones.size})")
    if (zones.isEmpty()) {
        InfoBanner("No safe zones yet. Add the place you're in now — like home — to get started.", Icons.Filled.Info)
    } else {
        zones.forEach { zone ->
            ZoneCard(
                zone = zone,
                isCurrent = policy.zoneId == zone.id,
                onRename = { renaming = zone },
                onRadius = { viewModel.setRadius(zone.id, it) },
                onGuard = { viewModel.setZoneGuard(zone.id, it) },
                onUseDefault = { viewModel.setZoneUseDefault(zone.id, it) },
                onDelete = { viewModel.deleteZone(zone.id) },
            )
            if (zone.guardEnabled) {
                if (zone.useDefault) {
                    InfoBanner("This zone uses your Guarding & Triggers settings.", Icons.Filled.Info)
                } else {
                    PlaceScheduleEditor(
                        responsiveness = zone.responsiveness,
                        onResponsiveness = { viewModel.setZoneResponsiveness(zone.id, it) },
                        customInterval = zone.customIntervalSeconds,
                        onCustomInterval = { viewModel.setZoneCustomInterval(zone.id, it) },
                        firstCheck = zone.firstCheckOnUnlock,
                        onFirstCheck = { viewModel.setZoneFirstCheck(zone.id, it) },
                        ramp = zone.checkRamp.split(',').mapNotNull { it.trim().toIntOrNull() }.filter { it > 0 },
                        onRamp = { viewModel.setZoneRamp(zone.id, it) },
                        shake = zone.shakeToCheck,
                        onShake = { viewModel.setZoneShake(zone.id, it) },
                        lockOnNoFace = zone.lockOnNoFace,
                        onLockOnNoFace = { viewModel.setZoneLockOnNoFace(zone.id, it) },
                    )
                }
            }
            Spacer(Modifier.height(Spacing.lg))
        }
    }
    OutlinedButton(onClick = { addOpen = true }, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Filled.Add, contentDescription = null)
        Spacer(Modifier.width(Spacing.sm))
        Text("Add current location")
    }

    if (addOpen) {
        ZoneNameDialog(
            title = "Name this place",
            initial = "Home",
            onDismiss = { addOpen = false },
            onConfirm = { name -> addOpen = false; viewModel.addCurrentLocation(name) },
        )
    }
    renaming?.let { zone ->
        ZoneNameDialog(
            title = "Rename zone",
            initial = zone.name,
            onDismiss = { renaming = null },
            onConfirm = { name -> renaming = null; viewModel.rename(zone.id, name) },
        )
    }
}

@Composable
private fun ResponsivenessPicker(selected: Int, onSelect: (Int) -> Unit) {
    val labels = listOf(
        Triple("Battery saver", "A check every 5 minutes.", 0),
        Triple("Balanced", "A check every 2.5 minutes.", 1),
        Triple("Max security", "A check every minute.", 2),
    )
    com.guardia.app.ui.components.GuardiaCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth()) {
            labels.forEachIndexed { index, (label, sub, value) ->
                RadioRow(
                    label = label,
                    subtitle = sub,
                    selected = selected == value,
                    onClick = { onSelect(value) },
                )
                if (index < labels.lastIndex) RowDivider()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ZoneCard(
    zone: com.guardia.app.domain.model.SafeZone,
    isCurrent: Boolean,
    onRename: () -> Unit,
    onRadius: (Int) -> Unit,
    onGuard: (Boolean) -> Unit,
    onUseDefault: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    var radius by remember(zone.id, zone.radiusMeters) { mutableStateOf(zone.radiusMeters.toFloat()) }
    com.guardia.app.ui.components.GuardiaCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(Spacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(zone.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (isCurrent) "You're here now" else "${"%.4f".format(zone.latitude)}, ${"%.4f".format(zone.longitude)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onRename) { Icon(Icons.Filled.Edit, contentDescription = "Rename") }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                }
            }
            SliderRow(
                title = "Radius",
                valueLabel = "${radius.roundToInt()} m",
                value = radius,
                onValueChange = { radius = it },
                onValueChangeFinished = { onRadius(radius.roundToInt()) },
                valueRange = 50f..2000f,
            )
            RowDivider()
            SwitchRow(
                "Protect here",
                zone.guardEnabled,
                onGuard,
                subtitle = "Run background checks while you're in this zone.",
            )
            if (zone.guardEnabled) {
                RowDivider()
                Column(Modifier.padding(top = Spacing.sm)) {
                    Text(
                        "Settings here",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(Spacing.sm))
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = zone.useDefault,
                            onClick = { onUseDefault(true) },
                            shape = SegmentedButtonDefaults.itemShape(0, 2),
                        ) { Text("Use default") }
                        SegmentedButton(
                            selected = !zone.useDefault,
                            onClick = { onUseDefault(false) },
                            shape = SegmentedButtonDefaults.itemShape(1, 2),
                        ) { Text("Custom") }
                    }
                }
            }
        }
    }
}

@Composable
private fun ZoneNameDialog(title: String, initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.LocationOn, contentDescription = null) },
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text("Name") },
            )
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                onClick = { onConfirm(name.ifBlank { "Safe zone" }) },
            ) { Text("Save") }
        },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun PrivacySection(viewModel: SettingsViewModel) {
    val context = LocalContext.current
    // Which bundled legal document to display full-screen (null = none).
    var legalDoc by remember { mutableStateOf<Int?>(null) }
    val crashLogEnabled by viewModel.crashLogEnabled.collectAsStateWithLifecycle()
    var crashLog by remember { mutableStateOf<String?>(null) }

    InfoBanner(
        "Guardia is built to be private: your face data and embeddings stay in this app's private storage and are never uploaded, and intruder photos and saved credentials are encrypted at rest. You control every permission and can erase everything anytime.",
        Icons.Filled.Info,
        tone = BannerTone.Success,
    )

    PermissionsControlPanel()

    BackupRestore()
    SettingsGroup(title = "Storage") {
        com.guardia.app.ui.components.NavRow(
            "Clear unused face photos",
            subtitle = "Remove cached gallery/enrollment crops no longer used for recognition.",
            onClick = {
                viewModel.cleanupUnusedPhotos { removed ->
                    val msg = if (removed > 0) "Removed $removed unused photo${if (removed == 1) "" else "s"}"
                    else "No unused photos to remove"
                    android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                }
            },
        )
    }
    SettingsGroup(title = "Clear data") {
        com.guardia.app.ui.components.NavRow("Delete all intruder photos", onClick = { viewModel.clearIntruderPhotos() })
        RowDivider()
        com.guardia.app.ui.components.NavRow("Clear activity log", onClick = { viewModel.clearActivityLog() })
    }
    InfoBanner(
        "Want a clean slate? Uninstalling Guardia permanently erases all on-device data — faces, photos, PINs, logs, and settings. We keep no copy.",
        Icons.Filled.Info,
    )
    SettingsGroup(title = "Diagnostics") {
        SwitchRow(
            "Local crash log",
            crashLogEnabled,
            viewModel::setCrashLogEnabled,
            subtitle = "If Guardia ever crashes, save the technical details to this device so you can share them with support. Guardia has no analytics — nothing is uploaded automatically.",
        )
        if (crashLogEnabled) {
            RowDivider()
            com.guardia.app.ui.components.NavRow(
                "View crash log",
                subtitle = "Read, share, or clear the saved crash details.",
                onClick = {
                    val text = viewModel.readCrashLog()
                    crashLog = text.ifBlank { "No crashes recorded. 🎉" }
                },
            )
        }
    }
    crashLog?.let { text ->
        AlertDialog(
            onDismissRequest = { crashLog = null },
            title = { Text("Crash log") },
            text = {
                androidx.compose.foundation.rememberScrollState().let { scroll ->
                    Column(Modifier.heightIn(max = 360.dp).verticalScroll(scroll)) {
                        Text(text, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(android.content.Intent.EXTRA_SUBJECT, "Guardia crash log")
                        putExtra(android.content.Intent.EXTRA_TEXT, text)
                    }
                    runCatching { context.startActivity(android.content.Intent.createChooser(send, "Share crash log")) }
                    crashLog = null
                }) { Text("Share") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.clearCrashLog(); crashLog = null }) { Text("Clear") }
            },
        )
    }
    SettingsGroup(title = "Legal") {
        com.guardia.app.ui.components.NavRow(
            "Privacy Policy",
            subtitle = "Exactly what Guardia touches and why — all on-device.",
            onClick = { legalDoc = R.raw.privacy_policy },
        )
        RowDivider()
        com.guardia.app.ui.components.NavRow(
            "Terms of Use",
            subtitle = "The agreement that governs your use of Guardia.",
            onClick = { legalDoc = R.raw.terms_of_use },
        )
    }

    legalDoc?.let { rawRes ->
        com.guardia.app.ui.components.LegalDocDialog(
            title = if (rawRes == R.raw.privacy_policy) "Privacy Policy" else "Terms of Use",
            rawRes = rawRes,
            onDismiss = { legalDoc = null },
        )
    }
}

/** A transparent overview of the access Guardia can use, with one-tap links to manage it all. */
@Composable
private fun PermissionsControlPanel() {
    val context = LocalContext.current
    InfoBanner(
        "You're in control. Every permission below is optional and can be turned off at any time — the matching feature simply stops. Guardia never hides what it's doing.",
        Icons.Filled.Info,
    )
    SettingsGroup(title = "Permissions & control") {
        com.guardia.app.ui.components.NavRow(
            "App permissions",
            subtitle = "Camera, microphone, location, SMS, notifications — grant or revoke each one.",
            onClick = {
                val intent = android.content.Intent(
                    android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.fromParts("package", context.packageName, null),
                ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(intent) }
            },
        )
        RowDivider()
        com.guardia.app.ui.components.NavRow(
            "Accessibility service",
            subtitle = "Used only to detect the foreground app for per-app checks. Turn it off here.",
            onClick = {
                runCatching {
                    context.startActivity(
                        android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
                            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            },
        )
        RowDivider()
        com.guardia.app.ui.components.NavRow(
            "Device Admin (lock only)",
            subtitle = "Lets Guardia lock the screen. It never wipes your device. Remove it anytime.",
            onClick = {
                runCatching {
                    context.startActivity(
                        android.content.Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS)
                            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            },
        )
    }
}


@Composable
private fun BackupRestore(viewModel: BackupViewModel = hiltViewModel()) {
    val context = LocalContext.current
    var dialog by remember { mutableStateOf<String?>(null) } // "export" or "import"
    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    var password by remember { mutableStateOf("") }
    var replace by remember { mutableStateOf(false) }

    val toast = { msg: String -> android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show() }

    val createDoc = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri -> if (uri != null) { pendingUri = uri; password = ""; dialog = "export" } }

    val openDoc = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) { pendingUri = uri; password = ""; replace = false; dialog = "import" } }

    SettingsGroup(title = "Backup & restore") {
        com.guardia.app.ui.components.NavRow(
            "Export enrollment",
            subtitle = "Save a password-protected backup of your people and faces.",
            onClick = { createDoc.launch("guardia-backup-${System.currentTimeMillis()}.gbk") },
        )
        RowDivider()
        com.guardia.app.ui.components.NavRow(
            "Restore from backup",
            subtitle = "Import a previously exported backup file.",
            onClick = { openDoc.launch(arrayOf("*/*")) },
        )
    }
    InfoBanner(
        "Backups are encrypted with your password (not your device key) so they work after reinstalling or on a new phone. The password cannot be recovered.",
        Icons.Filled.Info,
    )

    if (dialog != null) {
        val isExport = dialog == "export"
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { dialog = null },
            icon = { Icon(if (isExport) Icons.Filled.Lock else Icons.Filled.Restore, contentDescription = null) },
            title = { Text(if (isExport) "Set a backup password" else "Enter backup password") },
            text = {
                Column {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        singleLine = true,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (!isExport) {
                        Spacer(Modifier.height(Spacing.sm))
                        SwitchRow(
                            title = "Replace existing people",
                            subtitle = "Off merges the backup into your current data.",
                            checked = replace,
                            onCheckedChange = { replace = it },
                        )
                    }
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    enabled = password.length >= 4,
                    onClick = {
                        val uri = pendingUri
                        dialog = null
                        if (uri != null) {
                            if (isExport) viewModel.export(uri, password) { _, m -> toast(m) }
                            else viewModel.import(uri, password, replace) { _, m -> toast(m) }
                        }
                    },
                ) { Text(if (isExport) "Export" else "Restore") }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { dialog = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun VoiceSection(viewModel: SettingsViewModel) {
    val mode by viewModel.voiceListeningMode.collectAsStateWithLifecycle()
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val options = listOf(
        Triple("Off", "Voice safeword disabled.", 0),
        Triple("Always listening", "Listen continuously for the safeword.", 1),
        Triple("Only as face fallback", "Listen when face recognition is uncertain.", 2),
    )
    InfoBanner(
        "A safeword is a spoken phrase that confirms it's really you when the camera isn't sure. \"Always listening\" uses more battery; \"face fallback\" only listens during an uncertain check.",
        Icons.Filled.Info,
    )
    SettingsGroup(title = "Listening mode") {
        options.forEachIndexed { index, (label, sub, value) ->
            RadioRow(
                label = label,
                subtitle = sub,
                selected = mode == value,
                onClick = {
                    if (value != 0) micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    viewModel.setVoiceMode(value)
                },
            )
            if (index < options.lastIndex) RowDivider()
        }
    }
    InfoBanner(
        "Add a Picovoice AccessKey to local.properties to enable voice. Custom \"start/stop guarding\" phrases can be trained on the Picovoice console.",
        Icons.Filled.Info,
    )
}

@Composable
private fun SystemSection() {
    val context = LocalContext.current
    var adminActive by remember { mutableStateOf(DeviceAdminManager.isAdminActive(context)) }
    val adminLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        adminActive = DeviceAdminManager.isAdminActive(context)
    }

    InfoBanner(
        "Guardia needs Device Admin to lock the screen when an unauthorized person is detected. It only locks — it never wipes your phone unless you explicitly enable that elsewhere.",
        Icons.Filled.Info,
        tone = if (adminActive) BannerTone.Success else BannerTone.Warning,
    )
    com.guardia.app.ui.components.SectionHeader("Device admin")
    com.guardia.app.ui.components.GuardiaCard(modifier = Modifier.fillMaxWidth()) {
        androidx.compose.foundation.layout.Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                if (adminActive) "Active - Guardia can lock the screen." else "Not active - required to lock on a failed check.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            if (!adminActive) {
                Button(onClick = { adminLauncher.launch(DeviceAdminManager.enableIntent(context)) }, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                    Text("Enable device admin")
                }
            } else {
                OutlinedButton(onClick = { DeviceAdminManager.lockNow(context) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Lock device now (test)")
                }
            }
        }
    }
    InfoBanner("Guarding restarts after reboot if it was on. Some manufacturers require allowing auto-start in system settings.", Icons.Filled.Info)
}

@Composable
private fun AppCheckSection(viewModel: AppCheckViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val selected by viewModel.triggerApps.collectAsStateWithLifecycle()
    val apps by viewModel.apps.collectAsStateWithLifecycle()
    val checkStyle by viewModel.checkStyle.collectAsStateWithLifecycle()
    val lockOnFail by viewModel.lockOnFail.collectAsStateWithLifecycle()
    val accessibilityOn = remember {
        val flat = android.provider.Settings.Secure.getString(
            context.contentResolver,
            android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ).orEmpty()
        flat.contains("${context.packageName}/")
    }

    InfoBanner(
        "When a selected app opens, Guardia shows a quick face check first (guarding must be on). If an unauthorized person is detected, the app is closed — and, if you turn on the option below, the whole phone locks too. Uses the accessibility service to detect the foreground app.",
        Icons.Filled.Info,
        tone = if (accessibilityOn) BannerTone.Success else BannerTone.Warning,
    )
    if (!accessibilityOn) {
        OutlinedButton(
            onClick = { context.startActivity(android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Enable accessibility service") }
    }

    var canOverlay by remember { mutableStateOf(android.provider.Settings.canDrawOverlays(context)) }
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        canOverlay = android.provider.Settings.canDrawOverlays(context)
        onPauseOrDispose { }
    }
    if (!canOverlay) {
        InfoBanner(
            "Optional: allow \"Display over other apps\" so the check covers the screen instantly with no flash of the app underneath.",
            Icons.Filled.Info,
        )
        OutlinedButton(
            onClick = {
                context.startActivity(
                    android.content.Intent(
                        android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        android.net.Uri.parse("package:${context.packageName}"),
                    )
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Allow display over other apps") }
    }

    // Blur/Freeze render the app behind the gate using the accessibility screenshot capability,
    // which the Play build deliberately doesn't have — offer only the opaque style there.
    val styles = buildList {
        add(Triple("Loading spinner", "Hide the app behind a \"verifying\" screen.", 0))
        if (!com.guardia.app.BuildConfig.PLAY_BUILD) {
            add(Triple("Blur", "Show the app blurred while Guardia checks.", 1))
            add(Triple("Freeze", "Show the app frozen in place while checking.", 2))
        }
    }
    SettingsGroup(title = "While checking, show") {
        styles.forEachIndexed { index, (label, sub, value) ->
            RadioRow(
                label = label,
                subtitle = sub,
                selected = checkStyle == value,
                onClick = { viewModel.setCheckStyle(value) },
            )
            if (index < styles.lastIndex) RowDivider()
        }
    }

    SettingsGroup(title = "If it isn't you") {
        SwitchRow(
            title = "Lock the phone too",
            subtitle = "When an unauthorized person is detected, lock the device after closing the app. If you simply weren't recognized (no face / too dark), the app is only closed.",
            checked = lockOnFail,
            onCheckedChange = viewModel::setLockOnFail,
        )
    }

    com.guardia.app.ui.components.SectionHeader("Trigger apps (${selected.size})")
    AppListPicker(apps = apps, selected = selected, onToggle = viewModel::toggle)
}

/**
 * Persuasive paywall gate. Renders a real-looking [preview] of the locked feature, blurred and
 * non-interactive, with a compelling upgrade card floating on top. Tapping anywhere on the blurred
 * area (or the button) opens the paywall. This shows users exactly what they'd get — "see it,
 * blurred, until you subscribe" — which converts far better than a plain "locked" banner.
 */
@Composable
private fun PremiumGate(
    feature: String,
    tagline: String,
    benefits: List<String>,
    onUpgrade: () -> Unit,
    preview: @Composable () -> Unit,
) {
    val blurSupported = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S
    Box(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .let { if (blurSupported) it.blur(18.dp) else it },
        ) {
            Column { preview() }
        }
        // Scrim makes the area read as "locked" (and stands in for blur on Android < 12). Tapping
        // anywhere on the locked feature opens the paywall.
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(20.dp))
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.background.copy(alpha = if (blurSupported) 0.30f else 0.78f),
                            MaterialTheme.colorScheme.background.copy(alpha = if (blurSupported) 0.78f else 0.93f),
                        ),
                    ),
                )
                .clickable(
                    indication = null,
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    onClick = onUpgrade,
                ),
        )
        UpgradeOverlayCard(
            feature = feature,
            tagline = tagline,
            benefits = benefits,
            onUpgrade = onUpgrade,
            modifier = Modifier.align(Alignment.Center).padding(Spacing.md),
        )
    }
}

@Composable
private fun UpgradeOverlayCard(
    feature: String,
    tagline: String,
    benefits: List<String>,
    onUpgrade: () -> Unit,
    modifier: Modifier = Modifier,
) {
    com.guardia.app.ui.components.GuardiaCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.30f),
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                            ),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(Spacing.sm))
            Text(
                "Unlock $feature",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                tagline,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            if (benefits.isNotEmpty()) {
                Spacer(Modifier.height(Spacing.md))
                benefits.forEach { benefit ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(Spacing.sm))
                        Text(benefit, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Spacer(Modifier.height(Spacing.md))
            Button(onClick = onUpgrade, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                Icon(Icons.Filled.WorkspacePremium, contentDescription = null)
                Spacer(Modifier.width(Spacing.sm))
                Text("Unlock with Premium")
            }
            Spacer(Modifier.height(Spacing.xs))
            Text(
                "Free trial available · Cancel anytime in Google Play",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

/** Static, representative rows so the locked feature looks real behind the blur. */
@Composable
private fun PreviewRow(icon: ImageVector, title: String, subtitle: String, trailing: @Composable () -> Unit = {}) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing()
    }
}

@Composable
private fun AppLockPreview() {
    SettingsGroup(title = "Locked apps") {
        PreviewRow(Icons.Filled.Lock, "Banking", "Require your face to open") { Switch(checked = true, onCheckedChange = null) }
        RowDivider()
        PreviewRow(Icons.Filled.Lock, "Messages", "Require your face to open") { Switch(checked = true, onCheckedChange = null) }
        RowDivider()
        PreviewRow(Icons.Filled.Lock, "Photos", "Require your face to open") { Switch(checked = false, onCheckedChange = null) }
    }
}

@Composable
private fun AlertsPreview() {
    SettingsGroup(title = "Intruder alerts") {
        PreviewRow(Icons.Filled.Info, "Email alerts", "Send a photo to your inbox") { Switch(checked = true, onCheckedChange = null) }
        RowDivider()
        PreviewRow(Icons.Filled.Info, "SMS alerts", "Text a trusted number") { Switch(checked = true, onCheckedChange = null) }
        RowDivider()
        PreviewRow(Icons.Filled.LocationOn, "Find my phone", "Locate by secret keyword") { Switch(checked = false, onCheckedChange = null) }
    }
}

@Composable
private fun LocationPreview() {
    SettingsGroup(title = "Safe zones") {
        PreviewRow(Icons.Filled.LocationOn, "Home", "Relaxed checks when you're home") { Switch(checked = true, onCheckedChange = null) }
        RowDivider()
        PreviewRow(Icons.Filled.LocationOn, "Work", "Balanced protection at the office") { Switch(checked = true, onCheckedChange = null) }
        RowDivider()
        PreviewRow(Icons.Filled.Warning, "Public areas", "Maximum security everywhere else") { Switch(checked = true, onCheckedChange = null) }
    }
}

@Composable
private fun ProfilesPreview() {
    SettingsGroup(title = "Choose a profile") {
        PreviewRow(Icons.Filled.WorkspacePremium, "Home", "Relaxed, battery-friendly")
        RowDivider()
        PreviewRow(Icons.Filled.WorkspacePremium, "Work", "Balanced protection")
        RowDivider()
        PreviewRow(Icons.Filled.WorkspacePremium, "Travel", "Maximum security")
    }
}

@Composable
private fun AppLockSection(onUpgrade: () -> Unit = {}, viewModel: AppLockViewModel = hiltViewModel()) {
    val premium by viewModel.premium.collectAsStateWithLifecycle()
    if (!premium) {
        PremiumGate(
            feature = "App Lock",
            tagline = "Lock your most private apps behind your face — banking, chats, photos. Even someone holding your unlocked phone can't open them.",
            benefits = listOf(
                "Pick exactly which apps to protect",
                "Opens instantly for you, blocks everyone else",
                "Works even over the lock screen",
            ),
            onUpgrade = onUpgrade,
        ) { AppLockPreview() }
        return
    }
    val context = LocalContext.current
    val locked by viewModel.lockedApps.collectAsStateWithLifecycle()
    val apps by viewModel.apps.collectAsStateWithLifecycle()
    var accessibilityOn by remember { mutableStateOf(viewModel.isAccessibilityEnabled()) }

    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        accessibilityOn = viewModel.isAccessibilityEnabled()
        onPauseOrDispose { }
    }

    InfoBanner(
        "App Lock requires the Guardia accessibility service to detect which app is in the foreground. Enable it, then choose apps to lock.",
        Icons.Filled.Info,
        tone = if (accessibilityOn) BannerTone.Success else BannerTone.Warning,
    )
    OutlinedButton(
        onClick = { context.startActivity(android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
        modifier = Modifier.fillMaxWidth(),
    ) { Text(if (accessibilityOn) "Accessibility enabled - open settings" else "Enable accessibility service") }

    Spacer(Modifier.height(8.dp))
    com.guardia.app.ui.components.SectionHeader("Locked apps (${locked.size})")
    AppListPicker(apps = apps, selected = locked, onToggle = viewModel::toggle)
}

/** Searchable list of installed apps with their icons and a per-app toggle. */
@Composable
private fun AppListPicker(
    apps: List<InstalledApp>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
) {
    if (apps.isEmpty()) {
        com.guardia.app.ui.components.SkeletonRows()
        return
    }
    var query by remember { mutableStateOf("") }
    OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("Search apps") },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        singleLine = true,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    )
    Spacer(Modifier.height(Spacing.sm))

    val filtered = remember(apps, query) {
        if (query.isBlank()) apps
        else apps.filter { it.label.contains(query, ignoreCase = true) || it.packageName.contains(query, ignoreCase = true) }
    }
    if (filtered.isEmpty()) {
        Text(
            "No apps match \"$query\"",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(Spacing.md),
        )
        return
    }
    com.guardia.app.ui.components.GuardiaCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            filtered.forEachIndexed { index, app ->
                AppToggleRow(
                    app = app,
                    checked = selected.contains(app.packageName),
                    onCheckedChange = { onToggle(app.packageName) },
                )
                if (index < filtered.lastIndex) RowDivider()
            }
        }
    }
}

@Composable
private fun AppToggleRow(app: InstalledApp, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(app.packageName, Modifier.size(38.dp))
        Spacer(Modifier.width(Spacing.md))
        Text(
            app.label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(Spacing.sm))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun AppIcon(packageName: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val icon by produceState<ImageBitmap?>(null, packageName) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.packageManager.getApplicationIcon(packageName).toBitmap(96, 96).asImageBitmap()
            }.getOrNull()
        }
    }
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp)
    val bmp = icon
    if (bmp != null) {
        Image(bitmap = bmp, contentDescription = null, modifier = modifier.clip(shape))
    } else {
        Box(modifier.clip(shape).background(MaterialTheme.colorScheme.surfaceVariant))
    }
}

@Composable
private fun ProfilesSection(onUpgrade: () -> Unit = {}, viewModel: ProfilesViewModel = hiltViewModel()) {
    val premium by viewModel.premium.collectAsStateWithLifecycle()
    if (!premium) {
        PremiumGate(
            feature = "Security Profiles",
            tagline = "One tap switches your whole guarding setup for Home, Work, or Travel — relaxed where you're safe, locked down where you're not.",
            benefits = listOf(
                "Presets for home, work, and travel",
                "Switch protection in a single tap",
                "Saves battery when you don't need max security",
            ),
            onUpgrade = onUpgrade,
        ) { ProfilesPreview() }
        return
    }
    val active by viewModel.activeProfile.collectAsStateWithLifecycle()
    InfoBanner(
        "A profile applies a preset of guarding settings (responsiveness, sensitivity, capture). Switch any time; you can still fine-tune in the other sections.",
        Icons.Filled.Info,
    )
    SettingsGroup(title = "Choose a profile") {
        viewModel.profiles.forEachIndexed { index, profile ->
            RadioRow(
                label = profile.name,
                subtitle = profile.description,
                selected = active == profile.name,
                onClick = { viewModel.apply(profile) },
            )
            if (index < viewModel.profiles.lastIndex) RowDivider()
        }
    }
    if (active == "Default") {
        InfoBanner("No profile applied - using your custom settings.", Icons.Filled.Info)
    }
}

@Composable
private fun AlertsSection(onUpgrade: () -> Unit = {}, viewModel: AlertsViewModel = hiltViewModel()) {
    val premium by viewModel.premium.collectAsStateWithLifecycle()
    if (!premium) {
        PremiumGate(
            feature = "Alerts & Recovery",
            tagline = "The moment an intruder is caught, get an email or SMS with their photo — and find your phone if it's lost or stolen.",
            benefits = listOf(
                "Instant email & SMS intruder alerts with photo",
                "Find-my-phone by secret keyword text",
                "Location attached so you can recover it",
            ),
            onUpgrade = onUpgrade,
        ) { AlertsPreview() }
        return
    }
    val context = LocalContext.current
    val emailEnabled by viewModel.emailEnabled.collectAsStateWithLifecycle()
    val smtpHost by viewModel.smtpHost.collectAsStateWithLifecycle()
    val smtpPort by viewModel.smtpPort.collectAsStateWithLifecycle()
    val smtpUser by viewModel.smtpUser.collectAsStateWithLifecycle()
    val smtpPassword by viewModel.smtpPassword.collectAsStateWithLifecycle()
    val recipient by viewModel.recipient.collectAsStateWithLifecycle()
    val smsEnabled by viewModel.smsEnabled.collectAsStateWithLifecycle()
    val trustedNumber by viewModel.trustedNumber.collectAsStateWithLifecycle()
    val findEnabled by viewModel.findEnabled.collectAsStateWithLifecycle()
    val findKeyword by viewModel.findKeyword.collectAsStateWithLifecycle()
    val findTrustedOnly by viewModel.findTrustedOnly.collectAsStateWithLifecycle()

    val smsPerms = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}
    val locPerms = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}

    InfoBanner(
        "Alerts and find-my-phone send data off-device only when triggered. Credentials are stored on this device. Use an email app-password, not your main password.",
        Icons.Filled.Info,
    )

    SettingsGroup(title = "Email alerts") {
        SwitchRow("Send email on intruder", emailEnabled, viewModel::setEmailEnabled, subtitle = "Includes the intruder photo as an attachment.")
    }
    if (emailEnabled) {
        com.guardia.app.ui.components.GuardiaCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                FormField("SMTP host", smtpHost, viewModel::setSmtpHost)
                Spacer(Modifier.height(8.dp))
                FormField("SMTP port", smtpPort.toString(), { it.toIntOrNull()?.let(viewModel::setSmtpPort) })
                Spacer(Modifier.height(8.dp))
                FormField("Sender email (username)", smtpUser, viewModel::setSmtpUser)
                Spacer(Modifier.height(8.dp))
                FormField("App password", smtpPassword, viewModel::setSmtpPassword, password = true)
                Spacer(Modifier.height(8.dp))
                FormField("Recipient email", recipient, viewModel::setRecipient)
            }
        }
    }

    // SMS alerts & SMS find-my-phone ship in the full (sideload) build only; the Play build has
    // no SMS permissions (see the play manifest overlay), so hide these controls there.
    if (!com.guardia.app.BuildConfig.PLAY_BUILD) {
        SettingsGroup(title = "SMS alerts") {
            SwitchRow(
                "Send SMS on intruder", smsEnabled,
                { on -> if (on) smsPerms.launch(arrayOf(Manifest.permission.SEND_SMS)); viewModel.setSmsEnabled(on) },
                subtitle = "Texts a trusted number when the device locks.",
            )
        }
        if (smsEnabled) {
            com.guardia.app.ui.components.GuardiaCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    FormField("Trusted phone number", trustedNumber, viewModel::setTrustedNumber)
                }
            }
        }

        SettingsGroup(title = "Find my phone") {
            SwitchRow(
                "Locate by SMS keyword", findEnabled,
                { on ->
                    if (on) {
                        smsPerms.launch(arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.SEND_SMS))
                        locPerms.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                    }
                    viewModel.setFindEnabled(on)
                },
                subtitle = "Text the secret keyword to this phone to lock it and get its location.",
            )
        }
        if (findEnabled) {
            com.guardia.app.ui.components.GuardiaCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    FormField("Secret keyword", findKeyword, viewModel::setFindKeyword)
                }
            }
            SwitchRow(
                "Trusted number only", findTrustedOnly, viewModel::setFindTrustedOnly,
                subtitle = if (findTrustedOnly && trustedNumber.isBlank())
                    "Set a trusted phone number above — locate requests are ignored until one is set."
                else
                    "Only react to the keyword when it's sent from your trusted number. Turning this off lets any phone that knows the keyword locate this device.",
            )
        }
    }

    Button(
        onClick = { viewModel.sendTest { _, msg -> android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show() } },
        modifier = Modifier.fillMaxWidth().height(48.dp),
    ) { Text("Send test alert") }
}

@Composable
private fun FormField(label: String, value: String, onChange: (String) -> Unit, password: Boolean = false) {
    androidx.compose.material3.OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (password) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ScannerSection(viewModel: ScannerViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val checks by viewModel.checks.collectAsStateWithLifecycle()
    val score by viewModel.score.collectAsStateWithLifecycle()

    val adminLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { viewModel.scan() }

    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        viewModel.scan()
        onPauseOrDispose { }
    }

    androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth(), contentAlignment = androidx.compose.ui.Alignment.Center) {
        com.guardia.app.ui.components.ScoreRing(score = score, label = "Security score")
    }
    com.guardia.app.ui.components.SectionHeader("Checks")
    com.guardia.app.ui.components.GuardiaCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            checks.forEachIndexed { index, check ->
                CheckRow(check) { runFix(context, check.fix, adminLauncher) }
                if (index < checks.lastIndex) RowDivider()
            }
        }
    }
}

@Composable
private fun CameraMicSection(viewModel: CameraMicViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    InfoBanner(
        "Guardia watches whether your camera or microphone is in use. If something other than " +
            "Guardia is using them, you'll see a warning here. Android doesn't reveal exactly which " +
            "third-party app it is, so Guardia can only tell its own use apart from \"another app\".",
        Icons.Filled.Info,
    )
    com.guardia.app.ui.components.SectionHeader("Right now")
    com.guardia.app.ui.components.GuardiaCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            UsageRow(
                icon = Icons.Filled.Videocam,
                label = "Camera",
                active = state.cameraActive,
                byOther = state.otherCamera,
                bySelf = state.selfCamera,
            )
            RowDivider()
            UsageRow(
                icon = Icons.Filled.Mic,
                label = "Microphone",
                active = state.micActive,
                byOther = state.otherMic,
                bySelf = state.selfMic,
            )
        }
    }
    if (state.otherCamera || state.otherMic) {
        Spacer(Modifier.height(Spacing.sm))
        InfoBanner(
            buildString {
                append("Another app is using your ")
                append(
                    when {
                        state.otherCamera && state.otherMic -> "camera and microphone"
                        state.otherCamera -> "camera"
                        else -> "microphone"
                    },
                )
                append(" right now. If you didn't expect this, check which apps are open and review their permissions.")
            },
            Icons.Filled.Warning,
            tone = BannerTone.Warning,
        )
    }
}

@Composable
private fun UsageRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean,
    byOther: Boolean,
    bySelf: Boolean,
) {
    val (statusText, statusColor) = when {
        byOther -> "In use by another app" to MaterialTheme.colorScheme.error
        bySelf -> "In use by Guardia" to MaterialTheme.colorScheme.primary
        active -> "In use" to MaterialTheme.colorScheme.onSurface
        else -> "Not in use" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = statusColor)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            Text(statusText, style = MaterialTheme.typography.bodySmall, color = statusColor)
        }
        Icon(
            if (byOther) Icons.Filled.Warning else Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = if (byOther) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun CheckRow(check: SecurityCheck, onFix: () -> Unit) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        androidx.compose.material3.Icon(
            if (check.passed) Icons.Filled.CheckCircle else Icons.Filled.Warning,
            contentDescription = null,
            tint = if (check.passed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        )
        androidx.compose.foundation.layout.Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(check.title, style = MaterialTheme.typography.titleMedium)
            Text(check.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (!check.passed && check.fix != FixAction.NONE) {
            androidx.compose.material3.TextButton(onClick = onFix) { Text("Fix") }
        }
    }
}

private fun runFix(
    context: android.content.Context,
    fix: FixAction,
    adminLauncher: androidx.activity.result.ActivityResultLauncher<android.content.Intent>,
) {
    val launched = runCatching {
        when (fix) {
            FixAction.DEVICE_ADMIN -> adminLauncher.launch(DeviceAdminManager.enableIntent(context))
            FixAction.SECURITY_SETTINGS -> context.startActivity(android.content.Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS))
            FixAction.ACCESSIBILITY -> context.startActivity(android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS))
            FixAction.DEVELOPER -> context.startActivity(android.content.Intent(android.provider.Settings.ACTION_SETTINGS))
            FixAction.APP_DETAILS -> context.startActivity(
                android.content.Intent(
                    android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.fromParts("package", context.packageName, null),
                ),
            )
            FixAction.IN_APP -> android.widget.Toast.makeText(context, "Adjust this in the Guardia app", android.widget.Toast.LENGTH_SHORT).show()
            FixAction.NONE -> Unit
        }
    }.isSuccess
    if (!launched) {
        android.widget.Toast.makeText(context, "Couldn't open that settings screen on this device", android.widget.Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun PinsSection(viewModel: PinSettingsViewModel = hiltViewModel()) {
    val context = LocalContext.current
    var current by remember { mutableStateOf("") }
    var newReal by remember { mutableStateOf("") }
    var newDecoy by remember { mutableStateOf("") }
    var newPanic by remember { mutableStateOf("") }

    InfoBanner(
        "Three PINs, each 4-6 digits. Real opens Guardia. Decoy opens a harmless game (so a thief sees nothing sensitive). Panic looks normal but can trigger emergency actions.",
        Icons.Filled.Info,
    )
    com.guardia.app.ui.components.SectionHeader("Change PINs")
    com.guardia.app.ui.components.GuardiaCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            PinTextField("Current PIN", current) { current = it }
            Spacer(Modifier.height(12.dp))
            PinTextField("New real PIN", newReal) { newReal = it }
            Spacer(Modifier.height(12.dp))
            PinTextField("New decoy PIN (optional)", newDecoy) { newDecoy = it }
            Spacer(Modifier.height(12.dp))
            PinTextField("New panic PIN (optional)", newPanic) { newPanic = it }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    viewModel.changePins(current, newReal, newDecoy.ifBlank { null }, newPanic.ifBlank { null }) { ok, msg ->
                        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                        if (ok) { current = ""; newReal = ""; newDecoy = ""; newPanic = "" }
                    }
                },
                enabled = current.length in 4..6 && newReal.length in 4..6,
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) { Text("Update PINs") }
        }
    }
}

@Composable
private fun PinTextField(label: String, value: String, onChange: (String) -> Unit) {
    androidx.compose.material3.OutlinedTextField(
        value = value,
        onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) onChange(it) },
        label = { Text(label) },
        singleLine = true,
        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun AccountSection(viewModel: AccountViewModel = hiltViewModel(), onUpgrade: () -> Unit = {}) {
    val context = LocalContext.current
    val premium by viewModel.premium.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    val price by viewModel.price.collectAsStateWithLifecycle()

    androidx.compose.runtime.LaunchedEffect(Unit) { viewModel.connect() }

    if (premium) {
        InfoBanner("Guardia Premium is active. App Lock, Profiles, and Alerts are unlocked.", Icons.Filled.CheckCircle, tone = BannerTone.Success)
    } else {
        InfoBanner("Free plan. Upgrade to unlock App Lock, Profiles, and Alerts.", Icons.Filled.Info)
        Spacer(Modifier.height(Spacing.sm))
        UpgradeButton(onUpgrade)
    }

    com.guardia.app.ui.components.SectionHeader("Guardia Premium")
    com.guardia.app.ui.components.GuardiaCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                price?.let { "$it / month" } ?: "$5 / month",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Per-app lock, security profiles, and email/SMS intruder alerts with find-my-phone.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            when {
                premium -> OutlinedButton(onClick = { viewModel.restore() }, modifier = Modifier.fillMaxWidth()) { Text("Manage / restore") }
                status == com.guardia.app.core.billing.BillingManager.Status.Ready -> Button(
                    onClick = { (context as? android.app.Activity)?.let { viewModel.purchase(it) } },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) { Text("Subscribe") }
                status == com.guardia.app.core.billing.BillingManager.Status.Unavailable -> Text(
                    "Subscriptions aren't available on this device/build yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> Text("Connecting to Play...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))
            androidx.compose.material3.TextButton(onClick = { viewModel.restore() }, modifier = Modifier.fillMaxWidth()) { Text("Restore purchases") }
        }
    }
}
