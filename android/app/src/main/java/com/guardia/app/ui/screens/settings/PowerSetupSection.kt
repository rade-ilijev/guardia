package com.guardia.app.ui.screens.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.guardia.app.core.system.DeviceAdminManager
import com.guardia.app.ui.components.BannerTone
import com.guardia.app.ui.components.InfoBanner
import com.guardia.app.ui.components.NavRow
import com.guardia.app.ui.components.RowDivider
import com.guardia.app.ui.components.SettingsGroup
import com.guardia.app.ui.theme.Guardia
import com.guardia.app.ui.components.rememberAccessibilityOptIn

/** One special grant Guardia depends on: live status + what breaks without it + how to fix it. */
private data class PowerItem(
    val title: String,
    val why: String,
    val granted: Boolean,
    val fix: () -> Unit,
)

/**
 * The one screen that answers "is Guardia fully armed?" — every special permission with a live
 * status and a fix button, in plain language. Linked from the Settings health banner and listed
 * under the App section.
 */
@Composable
fun PowerSetupSection() {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        refresh++
        onPauseOrDispose { }
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }

    fun open(intent: Intent) {
        runCatching { context.startActivity(intent) }
    }

    // Play's AccessibilityService policy requires a prominent disclosure before the user is sent to
    // enable the service; rememberAccessibilityOptIn shows it.
    val openAccessibility = rememberAccessibilityOptIn()

    val items = run {
        refresh // recompute whenever we return to the screen or a permission result lands
        listOf(
            PowerItem(
                title = "Camera",
                why = "The core of Guardia: without it no face checks can run.",
                granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED,
                fix = { cameraLauncher.launch(Manifest.permission.CAMERA) },
            ),
            PowerItem(
                title = "Notifications",
                why = "Alerts, test results, and warnings are all silent without it.",
                granted = Build.VERSION.SDK_INT < 33 ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED,
                fix = {
                    if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                },
            ),
            PowerItem(
                title = "Device locking (Device Admin)",
                why = "Lets Guardia lock the phone when an intruder is seen. It never wipes data.",
                granted = DeviceAdminManager.isAdminActive(context),
                fix = { open(DeviceAdminManager.enableIntent(context)) },
            ),
            PowerItem(
                title = "App detection (Accessibility)",
                why = "Powers App Lock and per-app face checks. Never reads your screen content.",
                granted = isAccessibilityOn(context),
                fix = openAccessibility,
            ),
            PowerItem(
                title = "Display over other apps",
                why = "Needed to brighten the screen in the dark and cover apps during checks.",
                granted = Settings.canDrawOverlays(context),
                fix = {
                    open(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}"),
                        ),
                    )
                },
            ),
            PowerItem(
                title = "Unrestricted battery",
                why = "Stops your phone's battery manager from killing protection overnight.",
                granted = (context.getSystemService(Context.POWER_SERVICE) as PowerManager)
                    .isIgnoringBatteryOptimizations(context.packageName),
                fix = { open(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) },
            ),
        )
    }

    val missing = items.count { !it.granted }
    InfoBanner(
        if (missing == 0)
            "Everything Guardia needs is granted — protection is fully armed."
        else
            "Guardia works best with all of these. Tap a row to grant it; each one lists what stops working without it.",
        Icons.Filled.Info,
        tone = if (missing == 0) BannerTone.Success else BannerTone.Warning,
    )

    SettingsGroup(title = if (missing == 0) "All set" else "$missing thing${if (missing == 1) "" else "s"} to fix") {
        items.forEachIndexed { index, item ->
            NavRow(
                title = item.title,
                subtitle = item.why,
                leading = if (item.granted) Icons.Filled.CheckCircle else Icons.Filled.ErrorOutline,
                leadingTint = if (item.granted) Guardia.colors.success else Guardia.colors.destructive,
                onClick = item.fix,
            )
            if (index < items.lastIndex) RowDivider()
        }
    }
}

private fun isAccessibilityOn(context: Context): Boolean {
    val flat = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
    ).orEmpty()
    return flat.contains("${context.packageName}/")
}
