package com.guardia.app.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.GppGood
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.PhonelinkLock
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Workspaces
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.guardia.app.ui.components.GuardiaScaffold
import com.guardia.app.ui.components.NavRow
import com.guardia.app.ui.components.OutlinedTextField
import com.guardia.app.ui.components.SettingsColumn
import com.guardia.app.ui.components.SettingsGroup
import com.guardia.app.ui.theme.Guardia
import androidx.compose.material.icons.filled.Palette

data class SettingsCategory(
    val key: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val premium: Boolean = false,
)

/**
 * key -> category metadata, grouped into sections for the list. Copy rule: the title names the
 * feature in the user's words, the subtitle says what it does for them — no internal jargon
 * ("responsiveness", "triggers", "hygiene").
 */
val settingsCategories: List<SettingsCategory> = listOf(
    SettingsCategory("guarding", "Check Schedule", "How often Guardia looks at who's using the phone", Icons.Filled.Shield),
    SettingsCategory("detection", "Face Matching", "Strictness, dark-room behavior, test mode", Icons.Filled.Tune),
    SettingsCategory("appcheck", "Face Check for Apps", "Verify your face the moment chosen apps open", Icons.Filled.PhonelinkLock),
    SettingsCategory("response", "Locking & Evidence", "What triggers a lock, intruder selfies", Icons.Filled.GppGood),
    SettingsCategory("applock", "App Lock", "Ask for your PIN before chosen apps open", Icons.Filled.Apps, premium = true),
    SettingsCategory("location", "Location Rules", "Guard differently at home, work, or out", Icons.Filled.LocationOn, premium = true),
    SettingsCategory("profiles", "Profiles", "One-tap presets: Home, Public, Work", Icons.Filled.Workspaces, premium = true),
    SettingsCategory("alerts", "Alerts & Find My Phone", "Email alerts and remote locate", Icons.Filled.NotificationsActive, premium = true),
    SettingsCategory("security", "Security Center", "Your device's security score, app audit, and integrity", Icons.Filled.Shield),
    SettingsCategory("scanner", "Security Check-up", "Scan this phone's security settings", Icons.Filled.GppGood),
    SettingsCategory("appaudit", "App Privacy Audit", "See which apps can watch, listen, or read your data", Icons.Filled.PrivacyTip),
    SettingsCategory("cameramic", "Camera & Mic Monitor", "Know when another app watches or listens", Icons.Filled.Videocam),
    SettingsCategory("people", "Trusted People", "Faces that are allowed to use this phone", Icons.Filled.Face),
    SettingsCategory("blocked", "Blocked People", "Faces that always lock the phone", Icons.Filled.Block),
    SettingsCategory("pins", "PINs", "Your real, decoy, and panic PINs", Icons.Filled.Password),
    SettingsCategory("privacy", "Privacy & Data", "What's stored, for how long, delete it all", Icons.Filled.PrivacyTip),
    SettingsCategory("powersetup", "Power Setup", "Every permission Guardia needs, in one place", Icons.Filled.GppGood),
    SettingsCategory("system", "Permissions & Reliability", "Device admin, auto-start, battery", Icons.Filled.AdminPanelSettings),
    SettingsCategory("appearance", "Appearance", "Theme and motion", Icons.Filled.Palette),
)

/** An individual setting the search can find, so users don't need to know our category layout. */
private data class SettingEntry(val label: String, val category: String, val keywords: String)

private val settingsIndex = listOf(
    SettingEntry("Test mode", "detection", "test notifications preview try match percent"),
    SettingEntry("Match strictness", "detection", "sensitivity strict recognition threshold slider"),
    SettingEntry("When it's too dark", "detection", "dark low light night brighten flash"),
    SettingEntry("Check frequency", "guarding", "interval responsiveness battery how often schedule saver balanced max"),
    SettingEntry("Check on unlock", "guarding", "unlock first check ramp shake"),
    SettingEntry("What triggers a lock", "response", "unknown face multiple faces no face lock when stranger"),
    SettingEntry("Capture intruders", "response", "selfie photo evidence camera capture"),
    SettingEntry("Capture after failed unlocks", "response", "wrong pin password unlock attempts selfie"),
    SettingEntry("Apps with face check", "appcheck", "face check app open guarded apps"),
    SettingEntry("Apps behind App Lock", "applock", "pin gate lock apps private"),
    SettingEntry("Safe zones", "location", "home work zone gps location map"),
    SettingEntry("Email alerts", "alerts", "email smtp alert intruder send"),
    SettingEntry("Find my phone", "alerts", "sms locate keyword lost stolen find"),
    SettingEntry("Weekly digest", "alerts", "summary weekly notification report receipt"),
    SettingEntry("Change PINs", "pins", "pin decoy panic real change code"),
    SettingEntry("Recovery code", "pins", "recovery forgot lost pin reset code backup access"),
    SettingEntry("Ask for PIN again", "pins", "relock lock leaving background timeout auto lock"),
    SettingEntry("Evidence & data retention", "privacy", "retention delete purge data storage encrypted"),
    SettingEntry("Device locking permission", "powersetup", "device admin lock permission grant"),
    SettingEntry("Battery optimization", "powersetup", "battery killed background unrestricted stopped"),
    SettingEntry("Display over other apps", "powersetup", "overlay brightness dark permission"),
    SettingEntry("Start on boot", "system", "boot restart reboot reliability auto"),
    SettingEntry("Privacy Policy", "privacy", "privacy policy legal data gdpr what is collected stored"),
    SettingEntry("Terms of Use", "privacy", "terms conditions eula legal agreement license"),
    SettingEntry("Security Center", "security", "security score scan hub device posture protection center"),
    SettingEntry("App privacy audit", "appaudit", "apps spy permissions camera microphone audit risk privacy which apps"),
    SettingEntry("Security check-up", "scanner", "scan device root patch lock screen posture security checks"),
    SettingEntry("Camera and mic monitor", "cameramic", "camera microphone spying watching listening indicator"),
    SettingEntry("Trusted Wi-Fi", "guarding", "wifi network home relax trusted ssid"),
    SettingEntry("Guest pass", "people", "guest lend borrow temporary visitor"),
    SettingEntry("Keep intruder photos for", "privacy", "retention delete auto evidence storage days"),
    SettingEntry("Theme", "appearance", "dark light appearance theme night mode"),
    SettingEntry("Animations", "appearance", "motion animation reduce battery ambient"),
)

private val sectionLayout: List<Pair<String, List<String>>> = listOf(
    "Core protection" to listOf("guarding", "detection", "response"),
    "Extra protection" to listOf("appcheck", "applock", "location"),
    "Alerts & tools" to listOf("alerts", "profiles", "security"),
    "People & access" to listOf("people", "blocked", "pins", "privacy"),
    "App" to listOf("powersetup", "system", "appearance"),
)

/** One quiet line under each section header so the list scans without opening anything. */
private val sectionDescriptions = mapOf(
    "Core protection" to "How Guardia watches and when it locks",
    "Extra protection" to "Optional layers on top of the core",
    "Alerts & tools" to "How Guardia reports back to you",
    "People & access" to "Who's trusted, who's blocked, your PINs",
    "App" to "Setup, reliability, and your plan",
)

@Composable
fun SettingsScreen(
    onOpenCategory: (String) -> Unit,
    viewModel: SettingsOverviewViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
) {
    val states by viewModel.states.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }

    fun valueFor(key: String): String? = states[key]

    GuardiaScaffold(title = "Settings") { padding ->
        SettingsColumn(padding) {
            ProtectionHealthBanner(onFix = { onOpenCategory("powersetup") })
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search settings") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
            )

            if (query.isNotBlank()) {
                // Individual settings first — users search for the thing ("test mode", "battery"),
                // not for whichever category we filed it under.
                val settingMatches = settingsIndex.filter {
                    it.label.contains(query, ignoreCase = true) || it.keywords.contains(query, ignoreCase = true)
                }
                val categoryMatches = settingsCategories.filter {
                    it.title.contains(query, ignoreCase = true) || it.subtitle.contains(query, ignoreCase = true)
                }
                if (settingMatches.isEmpty() && categoryMatches.isEmpty()) {
                    SettingsGroup(title = "No results") {
                        NavRow(
                            title = "Nothing matches \"$query\"",
                            subtitle = "Try a different word, like \"battery\" or \"pin\"",
                            onClick = { query = "" },
                        )
                    }
                }
                if (settingMatches.isNotEmpty()) {
                    SettingsGroup(title = "Settings") {
                        settingMatches.forEachIndexed { index, entry ->
                            val cat = settingsCategories.first { it.key == entry.category }
                            NavRow(
                                title = entry.label,
                                subtitle = "In ${cat.title}",
                                leading = cat.icon,
                                leadingTint = tintFor(cat.key),
                                onClick = { onOpenCategory(cat.key) },
                            )
                            if (index < settingMatches.lastIndex) com.guardia.app.ui.components.RowDivider()
                        }
                    }
                }
                if (categoryMatches.isNotEmpty()) {
                    SettingsGroup(title = "Sections") {
                        categoryMatches.forEachIndexed { index, cat ->
                            NavRow(
                                title = cat.title,
                                subtitle = cat.subtitle,
                                leading = cat.icon,
                                premium = cat.premium,
                                value = valueFor(cat.key),
                                leadingTint = tintFor(cat.key),
                                onClick = { onOpenCategory(cat.key) },
                            )
                            if (index < categoryMatches.lastIndex) com.guardia.app.ui.components.RowDivider()
                        }
                    }
                }
            } else {
                var legalDoc by remember { mutableStateOf<Int?>(null) }
                val testMode by viewModel.testMode.collectAsStateWithLifecycle()
                val captureIntruders by viewModel.captureIntruders.collectAsStateWithLifecycle()
                SettingsGroup(title = "Quick controls") {
                    com.guardia.app.ui.components.SwitchRow(
                        "Test mode",
                        testMode,
                        viewModel::setTestMode,
                        subtitle = "Preview results as notifications — never locks.",
                    )
                    com.guardia.app.ui.components.RowDivider()
                    com.guardia.app.ui.components.SwitchRow(
                        "Capture intruders",
                        captureIntruders,
                        viewModel::setCaptureIntruders,
                        subtitle = "Save an encrypted selfie of whoever triggers a lock.",
                    )
                }
                sectionLayout.forEach { (section, keys) ->
                    SettingsGroup(title = section, subtitle = sectionDescriptions[section]) {
                        keys.forEachIndexed { index, key ->
                            val cat = settingsCategories.first { it.key == key }
                            NavRow(
                                title = cat.title,
                                subtitle = cat.subtitle,
                                leading = cat.icon,
                                premium = cat.premium,
                                value = valueFor(cat.key),
                                leadingTint = tintFor(cat.key),
                                onClick = { onOpenCategory(cat.key) },
                            )
                            if (index < keys.lastIndex) com.guardia.app.ui.components.RowDivider()
                        }
                    }
                }

                // The documents users are entitled to see without hunting: full text, on-device.
                SettingsGroup(title = "Legal") {
                    NavRow(
                        title = "Privacy Policy",
                        subtitle = "Exactly what the app touches and where it stays — everything on-device",
                        leading = Icons.Filled.PrivacyTip,
                        onClick = { legalDoc = com.guardia.app.R.raw.privacy_policy },
                    )
                    com.guardia.app.ui.components.RowDivider()
                    NavRow(
                        title = "Terms of Use",
                        subtitle = "Your license, your responsibilities, our promises",
                        leading = Icons.Filled.Description,
                        onClick = { legalDoc = com.guardia.app.R.raw.terms_of_use },
                    )
                }

                legalDoc?.let { res ->
                    com.guardia.app.ui.components.LegalDocDialog(
                        title = if (res == com.guardia.app.R.raw.privacy_policy) "Privacy Policy" else "Terms of Use",
                        rawRes = res,
                        onDismiss = { legalDoc = null },
                    )
                }
            }
        }
    }
}

/**
 * One glanceable answer to "is protection actually working?" at the top of Settings. Lists what's
 * broken in plain words and taps through to the fix. Hidden entirely when everything is healthy.
 */
@Composable
private fun ProtectionHealthBanner(onFix: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var missing by remember { mutableStateOf(listOf<String>()) }
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        missing = buildList {
            if (androidx.core.content.ContextCompat.checkSelfPermission(
                    context, android.Manifest.permission.CAMERA,
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) add("camera access")
            if (!com.guardia.app.core.system.DeviceAdminManager.isAdminActive(context)) add("device locking")
        }
        onPauseOrDispose { }
    }
    if (missing.isEmpty()) return
    com.guardia.app.ui.components.GuardiaCard(modifier = Modifier.fillMaxWidth(), onClick = onFix) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Protection is limited",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.error,
                )
                Text(
                    "Missing: ${missing.joinToString(" and ")}. Tap to fix.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Per-category accent.
 *
 * A long settings list is genuinely hard to scan when every icon is the same colour — the eye has
 * nothing to aim at and has to read each label in turn. Grouping the categories by hue gives it
 * targets: cyan is the guard itself, violet is what happens in response, amber is the things that
 * reach outside the phone, red is the block list, and everything to do with the device and the app
 * stays neutral. Each tile draws that hue as a soft gradient with a matching hairline (see
 * [com.guardia.app.ui.components.ShIconBox]), so a row of them reads as one set rather than as a
 * pile of stickers.
 */
@Composable
private fun tintFor(key: String): androidx.compose.ui.graphics.Color = when (key) {
    // The guard loop: the app being alive.
    "guarding", "detection", "appcheck" -> Guardia.colors.brand
    // What Guardia does when it finds someone.
    "response", "applock", "profiles" -> Guardia.colors.violet
    // Things that leave the device or depend on where it is.
    "location", "alerts" -> Guardia.colors.warning
    // Who is allowed, and who never is.
    "people" -> Guardia.colors.success
    "blocked" -> Guardia.colors.destructive
    // Security tooling and everything about the data itself.
    "scanner", "privacy", "pins", "security", "appaudit", "cameramic" -> Guardia.colors.info
    "appearance" -> Guardia.colors.violet
    // Device plumbing stays deliberately quiet.
    else -> Guardia.colors.mutedForeground
}
