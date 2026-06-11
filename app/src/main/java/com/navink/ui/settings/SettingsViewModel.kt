package com.navink.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.navink.data.remote.SubsonicService
import com.navink.data.repository.DownloadRepository
import com.navink.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val connected: Boolean = false,
    val storageLocation: String = "external",
    val offlineMode: Boolean = false,
    val storageUsedMb: Long = 0,
    val verifyMessage: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val service: SubsonicService,
    private val downloadRepository: DownloadRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val creds = settingsRepository.getCredentials()
            _state.value = _state.value.copy(
                serverUrl = creds.serverUrl,
                username = creds.username,
                password = creds.password,
                storageLocation = settingsRepository.getStorageLocation(),
                offlineMode = settingsRepository.getOfflineMode(),
            )
            refreshStorageUsed()
        }
    }

    fun onServerUrlChange(v: String) { _state.value = _state.value.copy(serverUrl = v) }
    fun onUsernameChange(v: String) { _state.value = _state.value.copy(username = v) }
    fun onPasswordChange(v: String) { _state.value = _state.value.copy(password = v) }

    fun connect(onSuccess: () -> Unit) {
        val s = _state.value
        if (s.serverUrl.isBlank() || s.username.isBlank() || s.password.isBlank()) {
            _state.value = s.copy(error = "All fields required")
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                settingsRepository.saveCredentials(s.serverUrl, s.username, s.password)
                val response = service.ping()
                if (response.response.status == "ok") {
                    _state.value = _state.value.copy(isLoading = false, connected = true)
                    onSuccess()
                } else {
                    val msg = response.response.error?.message ?: "Connection failed"
                    _state.value = _state.value.copy(isLoading = false, error = msg)
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = "Cannot reach server: ${e.message}")
            }
        }
    }

    fun setStorageLocation(location: String) {
        viewModelScope.launch {
            settingsRepository.saveStorageLocation(location)
            _state.value = _state.value.copy(storageLocation = location)
        }
    }

    fun toggleOfflineMode() {
        viewModelScope.launch {
            val next = !_state.value.offlineMode
            if (next) downloadRepository.verifyDownloads()
            settingsRepository.saveOfflineMode(next)
            _state.value = _state.value.copy(offlineMode = next)
        }
    }

    fun verifyDownloads() {
        viewModelScope.launch {
            val repaired = downloadRepository.verifyDownloads()
            _state.value = _state.value.copy(
                verifyMessage = if (repaired == 0) "All downloads OK" else "$repaired stale entries repaired"
            )
            refreshStorageUsed()
        }
    }

    private suspend fun refreshStorageUsed() {
        _state.value = _state.value.copy(
            storageUsedMb = downloadRepository.storageUsedBytes() / (1024 * 1024)
        )
    }
}
