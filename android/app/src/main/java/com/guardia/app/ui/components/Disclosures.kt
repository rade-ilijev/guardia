package com.guardia.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.guardia.app.ui.theme.Guardia
import com.guardia.app.ui.theme.Spacing

/**
 * Prominent disclosure for the Accessibility service.
 *
 * Google Play's *Use of the AccessibilityService API* policy allows a security app to use the API
 * for foreground-app detection, but only an app that genuinely assists users with disabilities may
 * set `android:isAccessibilityTool="true"` — and only such an app is exempt from prominent
 * disclosure. Guardia is not an assistive tool, so the flag is `false` in both accessibility
 * configs, and this screen is the disclosure the policy then requires: it appears in the normal
 * flow rather than behind a menu, states what the service can see, states what is done with it,
 * and only opens system settings on an explicit affirmative action.
 *
 * Use it through [rememberAccessibilityOptIn] rather than calling it directly, so every entry point
 * in the app goes through the same consent.
 */
@Composable
private fun AccessibilityDisclosureDialog(onAgree: () -> Unit, onDismiss: () -> Unit) {
    val c = Guardia.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.popover,
        icon = { Icon(Icons.Filled.Accessibility, contentDescription = null, tint = c.primary) },
        title = { Text("Turn on app detection?", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Text(
                    "Guardia uses Android's accessibility service to see which app is currently in " +
                        "the foreground. That is what makes per-app face checks, App Lock and tamper " +
                        "protection possible.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.mutedForeground,
                )
                DisclosurePoint("Guardia reads only the package name of the app in front — not what " +
                    "is on screen, and not what you type.")
                DisclosurePoint("Nothing from the accessibility service is stored, and none of it " +
                    "ever leaves this device.")
                DisclosurePoint("You can turn it off at any time in Android Settings → " +
                    "Accessibility, and Guardia keeps guarding without it.")
            }
        },
        confirmButton = { ShButton("Continue", onClick = onAgree) },
        dismissButton = { ShButton("Not now", onClick = onDismiss, variant = ButtonVariant.Ghost) },
    )
}

@Composable
private fun DisclosurePoint(text: String) {
    val c = Guardia.colors
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            Icons.Filled.Check,
            contentDescription = null,
            tint = c.success,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(Spacing.sm))
        Text(text, style = MaterialTheme.typography.bodySmall, color = c.mutedForeground)
    }
}

/**
 * Returns a callback that opens Android's accessibility settings *after* showing the disclosure
 * above. Every place in the app that sends the user to enable the service uses this, so the
 * consent cannot be bypassed by taking a different route through the UI.
 */
@Composable
fun rememberAccessibilityOptIn(): () -> Unit {
    val context = LocalContext.current
    var show by remember { mutableStateOf(false) }
    if (show) {
        AccessibilityDisclosureDialog(
            onAgree = {
                show = false
                runCatching {
                    context.startActivity(
                        android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS),
                    )
                }
            },
            onDismiss = { show = false },
        )
    }
    return { show = true }
}
