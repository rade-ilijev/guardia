package com.guardia.app.ui.screens.security

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.ScreenshotMonitor
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.guardia.app.core.security.SecurityAuditor
import com.guardia.app.ui.components.AppIcon
import com.guardia.app.ui.components.CircularProgressIndicator
import com.guardia.app.ui.components.EmptyState
import com.guardia.app.ui.components.GuardiaCard
import com.guardia.app.ui.components.GuardiaScaffold
import com.guardia.app.ui.components.SectionHeader
import com.guardia.app.ui.components.TextButton
import com.guardia.app.ui.theme.DataDisplay
import com.guardia.app.ui.theme.Guardia
import com.guardia.app.ui.theme.OverlineStyle
import com.guardia.app.ui.theme.Spacing

@Composable
fun AppAuditScreen(
    onBack: () -> Unit,
    viewModel: AppAuditViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()

    GuardiaScaffold(title = "App privacy audit", onBack = onBack) { padding ->
        when {
            ui.loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            ui.visible.isEmpty() -> EmptyState(
                icon = Icons.Filled.Shield,
                title = "Nothing risky found",
                subtitle = "No installed apps hold sensitive permissions right now.",
                modifier = Modifier.fillMaxSize().padding(padding),
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, top = Spacing.sm, bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                item { AuditHeader(ui.highRiskCount, ui.visible.size) }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SectionHeader("Apps by risk")
                        TextButton(onClick = viewModel::toggleSystem) {
                            Text(if (ui.showSystem) "Hide system apps" else "Show system apps")
                        }
                    }
                }
                items(ui.visible, key = { it.packageName }) { app ->
                    AppCard(app) { viewModel.openAppDetails(app.packageName) }
                }
            }
        }
    }
}

@Composable
private fun AuditHeader(highRisk: Int, total: Int) {
    GuardiaCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("HIGH RISK", style = OverlineStyle, color = MaterialTheme.colorScheme.error)
                Text(highRisk.toString(), style = DataDisplay, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    if (highRisk == 0) "No apps can both spy and send it off-device"
                    else "app${if (highRisk == 1) "" else "s"} can access something sensitive and reach the internet",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(Spacing.md))
            Text(
                "$total scanned",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AppCard(app: SecurityAuditor.AppAudit, onClick: () -> Unit) {
    val (accent, label) = when (app.risk) {
        SecurityAuditor.Risk.HIGH -> MaterialTheme.colorScheme.error to "HIGH"
        SecurityAuditor.Risk.MEDIUM -> MaterialTheme.colorScheme.tertiary to "MEDIUM"
        SecurityAuditor.Risk.LOW -> Guardia.colors.success to "LOW"
    }
    GuardiaCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Column(Modifier.fillMaxWidth().padding(Spacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // The app's own icon is the fastest way to recognise a row: a user knows their
                // banking app by its mark long before they finish reading its name.
                AppIcon(app.packageName, Modifier.size(40.dp))
                Spacer(Modifier.width(Spacing.md))
                Column(Modifier.weight(1f)) {
                    Text(app.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Text(
                        buildString {
                            append(if (app.isSystem) "System app" else "Installed app")
                            if (app.hasInternet) append(" · has internet")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                RiskPill(label, accent)
                Spacer(Modifier.width(Spacing.sm))
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(Spacing.md))
            @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                app.capabilities.forEach { cap ->
                    CapabilityChip(cap)
                }
            }
        }
    }
}

@Composable
private fun RiskPill(label: String, accent: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(accent.copy(alpha = 0.16f))
            .padding(horizontal = 10.dp, vertical = 3.dp),
    ) {
        Text(label, style = OverlineStyle, color = accent)
    }
}

@Composable
private fun CapabilityChip(cap: SecurityAuditor.Capability) {
    val tint = if (cap.high) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(tint.copy(alpha = 0.10f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = capabilityIcon(cap.kind),
            // The label beside it already says this; the glyph is what makes a wall of chips
            // scannable, not a second thing to read out.
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(13.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(cap.label, style = MaterialTheme.typography.labelMedium, color = tint)
    }
}

/**
 * A glyph per capability kind, keyed on the enum rather than the label so translating a string
 * never silently changes a picture.
 */
private fun capabilityIcon(kind: SecurityAuditor.CapabilityKind): ImageVector = when (kind) {
    SecurityAuditor.CapabilityKind.SCREEN -> Icons.Filled.ScreenshotMonitor
    SecurityAuditor.CapabilityKind.NOTIFICATIONS -> Icons.Filled.Notifications
    SecurityAuditor.CapabilityKind.DEVICE_ADMIN -> Icons.Filled.AdminPanelSettings
    SecurityAuditor.CapabilityKind.MICROPHONE -> Icons.Filled.Mic
    SecurityAuditor.CapabilityKind.CAMERA -> Icons.Filled.PhotoCamera
    SecurityAuditor.CapabilityKind.SMS_READ -> Icons.AutoMirrored.Filled.Message
    SecurityAuditor.CapabilityKind.SMS_SEND -> Icons.AutoMirrored.Filled.Send
    SecurityAuditor.CapabilityKind.CALL_LOG -> Icons.Filled.Call
    SecurityAuditor.CapabilityKind.LOCATION -> Icons.Filled.LocationOn
    SecurityAuditor.CapabilityKind.OVERLAY -> Icons.Filled.Layers
    SecurityAuditor.CapabilityKind.CONTACTS -> Icons.Filled.Contacts
    SecurityAuditor.CapabilityKind.CALENDAR -> Icons.Filled.CalendarMonth
    SecurityAuditor.CapabilityKind.APP_LIST -> Icons.Filled.Apps
    SecurityAuditor.CapabilityKind.INSTALL_APPS -> Icons.Filled.Download
}
