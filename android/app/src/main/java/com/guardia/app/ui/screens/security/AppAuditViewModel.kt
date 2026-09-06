package com.guardia.app.ui.screens.security

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guardia.app.core.security.SecurityAuditor
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class AppAuditViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val auditor: SecurityAuditor,
) : ViewModel() {

    /**
     * Marked `@Immutable` for Compose: it holds a `List`, and Compose treats every `List` as unstable
     * because the interface allows a mutable implementation. Without the annotation, any composable
     * reading this state is re-run on *every* recomposition of its parent, even when the state itself
     * has not changed. The contents genuinely are never mutated after construction, so the promise is
     * safe to make — and it is what lets Compose skip the subtree.
     */
    @Immutable
    data class UiState(
        val loading: Boolean = true,
        val apps: List<SecurityAuditor.AppAudit> = emptyList(),
        val showSystem: Boolean = false,
    ) {
        /**
         * Derived once per state object, not on every read. As getters, these filtered the whole
         * installed-app list every time anything read them — and the screen reads `visible` four
         * times in a single composition, with `highRiskCount` filtering it a second time to count.
         * On a phone with a few hundred apps that is real work on the main thread, repeated for
         * nothing: the inputs cannot change without a new state object.
         */
        val visible: List<SecurityAuditor.AppAudit> =
            if (showSystem) apps else apps.filter { !it.isSystem }
        val highRiskCount: Int = visible.count { it.risk == SecurityAuditor.Risk.HIGH }
    }

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    init { scan() }

    /** [force] skips the auditor's cache — the pull-to-refresh case. */
    fun scan(force: Boolean = false) {
        _ui.value = _ui.value.copy(loading = true)
        viewModelScope.launch {
            val apps = runCatching { auditor.auditCached(force) }.getOrDefault(emptyList())
            _ui.value = _ui.value.copy(loading = false, apps = apps)
        }
    }

    fun toggleSystem() {
        _ui.value = _ui.value.copy(showSystem = !_ui.value.showSystem)
    }

    /** Opens the system app-details page so the user can revoke a permission. */
    fun openAppDetails(packageName: String) {
        runCatching {
            context.startActivity(
                android.content.Intent(
                    android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.parse("package:$packageName"),
                ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}
