package com.guardia.app.ui.screens.paywall

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PhonelinkLock
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.guardia.app.core.billing.BillingManager
import com.guardia.app.ui.components.GuardiaCard
import com.guardia.app.ui.components.GuardiaScaffold
import com.guardia.app.ui.screens.settings.AccountViewModel
import com.guardia.app.ui.theme.Spacing

private data class PremiumFeature(val icon: ImageVector, val title: String, val subtitle: String)

private val premiumFeatures = listOf(
    PremiumFeature(Icons.Filled.PhonelinkLock, "App Lock", "Require your face to open chosen apps."),
    PremiumFeature(Icons.Filled.NotificationsActive, "Intruder alerts", "Email & SMS alerts with an attached photo."),
    PremiumFeature(Icons.Filled.LocationOn, "Find my phone", "Location included with security alerts."),
    PremiumFeature(Icons.Filled.Tune, "Security profiles", "Switch guarding behavior for home, work, travel."),
    PremiumFeature(Icons.Filled.Lock, "Custom check interval", "Fine-tune how often Guardia checks your face."),
)

@Composable
fun PaywallScreen(
    onBack: () -> Unit,
    viewModel: AccountViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val premium by viewModel.premium.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    val price by viewModel.price.collectAsStateWithLifecycle()
    val trial by viewModel.trial.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.connect() }

    GuardiaScaffold(title = "Guardia Premium", onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(Spacing.lg))
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                            )
                        )
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.WorkspacePremium, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(44.dp))
            }
            Spacer(Modifier.height(Spacing.md))
            Text("Unlock the full guard", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(Spacing.xs))
            Text(
                "Everything in Guardia stays on-device. Premium adds the advanced protection layer.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(Spacing.lg))
            GuardiaCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(Spacing.lg)) {
                    premiumFeatures.forEachIndexed { i, f ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = Spacing.sm)) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.size(Spacing.md))
                            Column(Modifier.weight(1f)) {
                                Text(f.title, style = MaterialTheme.typography.titleMedium)
                                Text(f.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(Spacing.lg))
            if (premium) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
                Spacer(Modifier.height(Spacing.sm))
                Text("Premium is active", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(Spacing.md))
                OutlinedButton(onClick = { viewModel.restore() }, modifier = Modifier.fillMaxWidth()) { Text("Manage / restore") }
            } else {
                Text(
                    price?.let { "$it / month" } ?: "$5 / month",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                trial?.let {
                    Spacer(Modifier.height(Spacing.xs))
                    Text("Start with a $it free trial", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(Spacing.md))
                when (status) {
                    BillingManager.Status.Ready -> Button(
                        onClick = { (context as? android.app.Activity)?.let { viewModel.purchase(it) } },
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                    ) { Text(if (trial != null) "Start free trial" else "Subscribe", style = MaterialTheme.typography.titleMedium) }
                    BillingManager.Status.Unavailable -> Text(
                        "Subscriptions aren't available on this device/build yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    else -> Text("Connecting to Play\u2026", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = { viewModel.restore() }, modifier = Modifier.fillMaxWidth()) { Text("Restore purchases") }
                Text(
                    "Cancel anytime in Google Play. Billed monthly after any trial.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(Spacing.xl))
        }
    }
}
