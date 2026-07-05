package com.guardia.app.ui.screens.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guardia.app.core.backup.BackupManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class BackupViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val backup: BackupManager,
) : ViewModel() {

    fun export(uri: Uri, password: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val result = runCatching {
                val bytes = backup.export(password.toCharArray())
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                        ?: error("Could not write file")
                }
            }
            if (result.isSuccess) onResult(true, "Backup saved. Keep your password safe - it can't be recovered.")
            else onResult(false, result.exceptionOrNull()?.message ?: "Backup failed")
        }
    }

    fun import(uri: Uri, password: String, replace: Boolean, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val bytes = runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                }
            }.getOrNull()
            if (bytes == null) {
                onResult(false, "Could not open that file")
                return@launch
            }
            when (val r = backup.import(bytes, password.toCharArray(), replace)) {
                is BackupManager.ImportResult.Success ->
                    onResult(true, "Restored ${r.people} people and ${r.samples} face samples.")
                is BackupManager.ImportResult.Error -> onResult(false, r.message)
            }
        }
    }
}
