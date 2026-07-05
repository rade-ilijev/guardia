package com.guardia.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.guardia.app.R
import com.guardia.app.ui.theme.Spacing

/**
 * Loads a bundled legal document from res/raw, substitutes the editable tokens
 * ({{DEVELOPER}}, {{CONTACT}}, {{UPDATED}}) from string resources, and returns the text.
 * Everything is on-device — no network required.
 */
@Composable
fun rememberLegalText(rawRes: Int): String {
    val context = LocalContext.current
    return remember(rawRes) {
        runCatching {
            context.resources.openRawResource(rawRes).bufferedReader().use { it.readText() }
                .replace("{{DEVELOPER}}", context.getString(R.string.legal_developer_name))
                .replace("{{CONTACT}}", context.getString(R.string.legal_contact_email))
                .replace("{{UPDATED}}", context.getString(R.string.legal_updated_date))
        }.getOrDefault("Unable to load this document.")
    }
}

/** Full-screen, scrollable reader for a bundled legal document (Privacy Policy / Terms). */
@Composable
fun LegalDocDialog(title: String, rawRes: Int, onDismiss: () -> Unit) {
    val text = rememberLegalText(rawRes)
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.96f).fillMaxHeight(0.92f),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = Spacing.lg, end = Spacing.sm, top = Spacing.sm, bottom = Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Close")
                    }
                }
                RowDivider()
                Text(
                    text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = Spacing.lg, vertical = Spacing.md),
                )
            }
        }
    }
}
