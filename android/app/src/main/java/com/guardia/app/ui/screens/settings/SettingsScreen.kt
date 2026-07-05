package com.guardia.app.ui.screens.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CreditCard
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
import androidx.compose.material.icons.filled.Workspaces
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.guardia.app.ui.components.GuardiaScaffold
import com.guardia.app.ui.components.NavRow
import com.guardia.app.ui.components.SettingsColumn
import com.guardia.app.ui.components.SettingsGroup

data class SettingsCategory(
    val key: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val premium: Boolean = false,
)

/** key -> category metadata, grouped into sections for the list. */
val settingsCategories: List<SettingsCategory> = listOf(
    SettingsCategory("guarding", "Guarding & Triggers", "Responsiveness and battery", Icons.Filled.Shield),
    SettingsCategory("detection", "Detection & Sensitivity", "Match strictness and test mode", Icons.Filled.Tune),
    SettingsCategory("appcheck", "Check on App Open", "Immediate face check when chosen apps open", Icons.Filled.PhonelinkLock),
    SettingsCategory("response", "Response Actions", "Who triggers a lock, and evidence", Icons.Filled.GppGood),
    SettingsCategory("applock", "App Lock", "Per-app PIN/face gate", Icons.Filled.Apps, premium = true),
    SettingsCategory("voice", "Voice Safeword", "Start/stop guarding by voice", Icons.Filled.RecordVoiceOver),
    SettingsCategory("location", "Location Protection", "Guard differently by where you are", Icons.Filled.LocationOn, premium = true),
    SettingsCategory("profiles", "Profiles", "Home / Public / Work presets", Icons.Filled.Workspaces, premium = true),
    SettingsCategory("alerts", "Alerts & Recovery", "Email/SMS alerts, find my phone", Icons.Filled.NotificationsActive, premium = true),
    SettingsCategory("scanner", "Security Scanner", "On-device threat hygiene", Icons.Filled.GppGood),
    SettingsCategory("cameramic", "Camera & Mic Monitor", "Spot another app using your camera or mic", Icons.Filled.Videocam),
    SettingsCategory("people", "People & Enrollment", "Manage authorized faces", Icons.Filled.Face),
    SettingsCategory("blocked", "Blocked People", "Look-alikes that must never unlock", Icons.Filled.Block),
    SettingsCategory("pins", "Access & PINs", "Real / decoy / panic PINs", Icons.Filled.Password),
    SettingsCategory("privacy", "Privacy & Data", "Retention, encryption, purge", Icons.Filled.PrivacyTip),
    SettingsCategory("system", "System & Reliability", "Device admin, auto-start", Icons.Filled.AdminPanelSettings),
    SettingsCategory("account", "Account & Subscription", "Plan and billing", Icons.Filled.CreditCard),
)

private val sectionLayout: List<Pair<String, List<String>>> = listOf(
    "Protection" to listOf("guarding", "detection", "appcheck", "response", "applock", "voice", "location"),
    "Recovery" to listOf("profiles", "alerts", "scanner"),
    "Access" to listOf("people", "blocked", "pins", "privacy"),
    "App" to listOf("system", "account"),
)

@Composable
fun SettingsScreen(
    onOpenCategory: (String) -> Unit,
    viewModel: SettingsOverviewViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
) {
    val states by viewModel.states.collectAsStateWithLifecycle()
    val premium by viewModel.premium.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }

    fun valueFor(key: String): String? = states[key]
        ?: if (key == "account") (if (premium) "Premium" else "Free") else null

    GuardiaScaffold(title = "Settings") { padding ->
        SettingsColumn(padding) {
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
                val matches = settingsCategories.filter {
                    it.title.contains(query, ignoreCase = true) || it.subtitle.contains(query, ignoreCase = true)
                }
                SettingsGroup(title = "Results (${matches.size})") {
                    matches.forEachIndexed { index, cat ->
                        NavRow(
                            title = cat.title,
                            subtitle = cat.subtitle,
                            leading = cat.icon,
                            premium = cat.premium,
                            value = valueFor(cat.key),
                            leadingTint = tintFor(cat.key),
                            onClick = { onOpenCategory(cat.key) },
                        )
                        if (index < matches.lastIndex) com.guardia.app.ui.components.RowDivider()
                    }
                }
            } else {
                sectionLayout.forEach { (section, keys) ->
                    SettingsGroup(title = section) {
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
            }
        }
    }
}

/** Per-category accent so the list scans faster. */
@Composable
private fun tintFor(key: String): androidx.compose.ui.graphics.Color = when (key) {
    "guarding", "detection", "appcheck" -> MaterialTheme.colorScheme.primary
    "response", "applock", "voice", "location" -> MaterialTheme.colorScheme.tertiary
    "profiles", "alerts", "scanner" -> MaterialTheme.colorScheme.secondary
    "blocked" -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.primary
}
