package com.guardia.app.ui.screens.security

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.GppGood
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Videocam
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
import com.guardia.app.ui.components.GuardiaCard
import com.guardia.app.ui.components.GuardiaScaffold
import com.guardia.app.ui.components.ScoreRing
import com.guardia.app.ui.components.SectionHeader
import com.guardia.app.ui.screens.settings.ScannerViewModel
import com.guardia.app.ui.theme.Guardia
import com.guardia.app.ui.theme.Spacing

@Composable
fun SecurityCenterScreen(
    onBack: () -> Unit,
    onOpenScan: () -> Unit,
    onOpenAppAudit: () -> Unit,
    onOpenCameraMic: () -> Unit,
    scannerVm: ScannerViewModel = hiltViewModel(),
    hubVm: SecurityCenterViewModel = hiltViewModel(),
) {
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        scannerVm.scan()
        hubVm.refresh()
        onPauseOrDispose { }
    }
    val score by scannerVm.score.collectAsStateWithLifecycle()
    val checks by scannerVm.checks.collectAsStateWithLifecycle()
    val hub by hubVm.ui.collectAsStateWithLifecycle()

    val passed = checks.count { it.passed }
    val criticalFails = checks.count { !it.passed && it.severity == com.guardia.app.ui.screens.settings.Severity.CRITICAL }

    GuardiaScaffold(title = "Security Center", onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Spacer(Modifier.size(Spacing.sm))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                ScoreRing(score = score, label = "On-device security")
            }

            SectionHeader("Checks")
            SecurityRow(
                icon = Icons.Filled.GppGood,
                title = "Device security scan",
                status = if (checks.isEmpty()) "Scanning…"
                    else if (criticalFails > 0) "$criticalFails critical issue${if (criticalFails == 1) "" else "s"} to fix"
                    else "$passed of ${checks.size} checks passing",
                alert = criticalFails > 0,
                onClick = onOpenScan,
            )
            SecurityRow(
                icon = Icons.Filled.PrivacyTip,
                title = "App privacy audit",
                status = when {
                    hub.loading -> "Scanning installed apps…"
                    hub.highRiskApps == 0 -> "No high-risk apps found"
                    else -> "${hub.highRiskApps} app${if (hub.highRiskApps == 1) "" else "s"} can spy and reach the internet"
                },
                alert = hub.highRiskApps > 0,
                onClick = onOpenAppAudit,
            )
            SecurityRow(
                icon = Icons.Filled.Shield,
                title = "Guardia integrity",
                status = if (hub.integrityOk) "Environment looks clean"
                    else hub.integrityIssue ?: "Compromised environment detected",
                alert = !hub.integrityOk,
                onClick = null,
            )
            SecurityRow(
                icon = Icons.Filled.Videocam,
                title = "Camera & mic monitor",
                status = "See when another app watches or listens",
                alert = false,
                onClick = onOpenCameraMic,
            )
            Spacer(Modifier.size(80.dp))
        }
    }
}

@Composable
private fun SecurityRow(
    icon: ImageVector,
    title: String,
    status: String,
    alert: Boolean,
    onClick: (() -> Unit)?,
) {
    val tint = if (alert) Guardia.colors.destructive else Guardia.colors.success
    GuardiaCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape).background(tint.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(status, style = MaterialTheme.typography.bodySmall, color = if (alert) tint else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (onClick != null) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
