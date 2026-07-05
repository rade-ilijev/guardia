package com.guardia.app.core.applock

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.guardia.app.core.security.PinType
import com.guardia.app.data.AppPreferences
import com.guardia.app.ui.components.PinDots
import com.guardia.app.ui.components.PinPad
import com.guardia.app.ui.theme.GuardiaHeroGradient
import com.guardia.app.ui.theme.GuardiaTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class AppLockActivity : ComponentActivity() {

    @Inject lateinit var prefs: AppPreferences
    @Inject lateinit var appLockManager: AppLockManager

    private var lockedPackage: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        lockedPackage = intent.getStringExtra(EXTRA_PACKAGE).orEmpty()
        val appLabel = runCatching {
            val pm = packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(lockedPackage, 0)).toString()
        }.getOrDefault("this app")

        onBackPressedDispatcher.addCallback(this) { goHome() }

        setContent {
            GuardiaTheme {
                AppLockContent(
                    appLabel = appLabel,
                    onVerify = ::verify,
                )
            }
        }
    }

    private fun verify(pin: String, onResult: (Boolean) -> Unit) {
        lifecycleScope.launch {
            val role = prefs.verifyPin(pin)
            if (role == PinType.REAL) {
                appLockManager.markUnlocked(lockedPackage)
                finish()
            } else {
                onResult(false)
            }
        }
    }

    private fun goHome() {
        val home = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(home)
        finish()
    }

    companion object {
        const val EXTRA_PACKAGE = "extra_package"
    }
}

@androidx.compose.runtime.Composable
private fun AppLockContent(appLabel: String, onVerify: (String, (Boolean) -> Unit) -> Unit) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier.size(80.dp).clip(CircleShape).background(GuardiaHeroGradient),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Lock, contentDescription = null, tint = Color.White, modifier = Modifier.size(40.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text("Locked by Guardia", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(
            if (error) "Incorrect PIN, try again" else "Enter your PIN to open $appLabel",
            style = MaterialTheme.typography.bodyMedium,
            color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(36.dp))
        PinDots(length = pin.length)
        Spacer(Modifier.height(44.dp))
        PinPad(
            onDigit = { digit ->
                if (pin.length < 6) {
                    error = false
                    pin += digit.toString()
                    if (pin.length >= 4) {
                        onVerify(pin) { ok ->
                            if (!ok && pin.length >= 6) { error = true; pin = "" }
                        }
                    }
                }
            },
            onBackspace = { if (pin.isNotEmpty()) pin = pin.dropLast(1) },
        )
    }
}
