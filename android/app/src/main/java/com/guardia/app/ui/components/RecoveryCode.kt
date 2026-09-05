package com.guardia.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Key
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guardia.app.ui.theme.Guardia
import com.guardia.app.ui.theme.MonoCaption
import com.guardia.app.ui.theme.Radius
import com.guardia.app.ui.theme.Spacing

/**
 * Shows a freshly generated recovery code, once.
 *
 * This is the only moment the code is ever visible: it is stored hashed, exactly like a PIN, so
 * Guardia genuinely cannot show it again. The dialog says that plainly and refuses to close until
 * the user ticks that they've written it down — a modal that can be dismissed by tapping outside
 * would guarantee some people lose the only thing standing between them and a reinstall.
 *
 * The code is deliberately not treated as a secret to hide from the screen (no masking, copy
 * allowed): the user is being asked to record it, and making that harder just means they don't.
 */
@Composable
fun RecoveryCodeDialog(code: String, onAcknowledged: () -> Unit) {
    val c = Guardia.colors
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var acknowledged by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { /* deliberately not dismissible — see the doc comment */ },
        containerColor = c.popover,
        icon = { Icon(Icons.Filled.Key, contentDescription = null, tint = c.brand) },
        title = { Text("Save your recovery code", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Text(
                    "If you ever forget your PIN, this code is the only way back into Guardia. " +
                        "Write it down and keep it somewhere safe — not on this phone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.mutedForeground,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Radius.lg))
                        .background(c.brandSubtle)
                        .border(BorderStroke(1.dp, c.brand.copy(alpha = 0.35f)), RoundedCornerShape(Radius.lg))
                        .padding(Spacing.lg),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        code,
                        style = MonoCaption.copy(fontSize = 20.sp, letterSpacing = 2.sp),
                        color = c.brand,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                    )
                    ShIconButton(
                        icon = Icons.Filled.ContentCopy,
                        onClick = {
                            clipboard.setText(AnnotatedString(code))
                            android.widget.Toast
                                .makeText(context, "Recovery code copied", android.widget.Toast.LENGTH_SHORT)
                                .show()
                        },
                        contentDescription = "Copy recovery code",
                        tint = c.brand,
                    )
                }
                Text(
                    "Guardia stores only a hash of this code, so it can't be shown again. " +
                        "You can generate a new one anytime in Settings › PINs.",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.mutedForeground,
                )
                Spacer(Modifier.height(Spacing.xs))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ShCheckbox(checked = acknowledged, onCheckedChange = { acknowledged = it })
                    Spacer(Modifier.width(Spacing.md))
                    Text(
                        "I've saved it somewhere safe",
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.foreground,
                    )
                }
            }
        },
        confirmButton = {
            ShButton(text = "Done", enabled = acknowledged, onClick = onAcknowledged)
        },
    )
}
