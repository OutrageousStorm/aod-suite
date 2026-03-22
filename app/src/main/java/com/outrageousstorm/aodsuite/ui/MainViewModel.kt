package com.outrageousstorm.aodsuite.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.outrageousstorm.aodsuite.aod.AodRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class UiState(
    val shizukuAvailable: Boolean = false,
    val shizukuGranted: Boolean = false,
    val permissionsGranted: Boolean = false,
    val aodEnabled: Boolean = false,
    val brightnessPercent: Int = 20,
    val blurRadius: Int = 12,
    val selectedImageUri: Uri? = null,
    val aodTap: Boolean = true,
    val raiseToWake: Boolean = true,
    val nightMode: Boolean = false,
    val nightTemp: Int = 3200,
    val aodTimeoutSec: Int = 0,
    val statusMessage: String = "Tap Setup to grant permissions",
    val loading: Boolean = false,
    val logOutput: String = ""
)

class MainViewModel(private val repo: AodRepository, private val packageName: String) : ViewModel() {

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun refresh(available: Boolean, granted: Boolean) {
        viewModelScope.launch {
            val aod = if (granted) repo.getAodState() else false
            val bright = if (granted) repo.getCurrentBrightness() else 20
            _state.value = _state.value.copy(
                shizukuAvailable = available,
                shizukuGranted = granted,
                aodEnabled = aod,
                brightnessPercent = if (bright >= 0) bright else 20
            )
        }
    }

    /** One-time setup: grant WRITE_SECURE_SETTINGS to this app via Shizuku */
    fun grantPermissions() = launch("Setup — granting permissions") {
        val r = repo.grantPermissions(packageName)
        if (r.success) {
            _state.value = _state.value.copy(permissionsGranted = true)
            "✅ Permissions granted! All features unlocked."
        } else {
            "❌ Grant failed: ${r.stderr}"
        }
    }

    fun setAod(enabled: Boolean) = launch("Toggle AOD") {
        val r = repo.enableAod(enabled)
        if (r.success) _state.value = _state.value.copy(aodEnabled = enabled)
        if (r.success) "✅ AOD ${if (enabled) "enabled" else "disabled"}"
        else "❌ ${r.stderr}"
    }

    fun setMinBrightness(percent: Int) {
        _state.value = _state.value.copy(brightnessPercent = percent)
    }

    fun applyMinBrightness() = launch("Apply brightness") {
        val r = repo.setMinBrightness(_state.value.brightnessPercent)
        if (r.success) "✅ Brightness set to ${_state.value.brightnessPercent}%"
        else "❌ ${r.stderr}"
    }

    fun setBlurRadius(r: Int) {
        _state.value = _state.value.copy(blurRadius = r)
    }

    fun setSelectedImage(uri: Uri?) {
        _state.value = _state.value.copy(selectedImageUri = uri)
    }

    fun applyBlurredWallpaper() = launch("Apply blurred wallpaper") {
        val uri = _state.value.selectedImageUri ?: return@launch "No image selected"
        val result = repo.applyBlurredWallpaper(uri, _state.value.blurRadius)
        result.getOrElse { "Error: ${it.message}" }
    }

    fun setAodTap(enabled: Boolean) = launch("Set tap gesture") {
        _state.value = _state.value.copy(aodTap = enabled)
        val r = repo.setAodTap(enabled)
        if (r.success) "✅ Tap gesture ${if (enabled) "on" else "off"}" else "❌ ${r.stderr}"
    }

    fun setRaiseToWake(enabled: Boolean) = launch("Set raise-to-wake") {
        _state.value = _state.value.copy(raiseToWake = enabled)
        val r = repo.setRaiseToWake(enabled)
        if (r.success) "✅ Raise-to-wake ${if (enabled) "on" else "off"}" else "❌ ${r.stderr}"
    }

    fun setNightMode(enabled: Boolean) = launch("Night mode") {
        _state.value = _state.value.copy(nightMode = enabled)
        val r = repo.setNightMode(enabled, _state.value.nightTemp)
        if (r.success) "✅ Night mode ${if (enabled) "on" else "off"}" else "❌ ${r.stderr}"
    }

    fun setNightTemp(kelvin: Int) = launch("Night temperature") {
        _state.value = _state.value.copy(nightTemp = kelvin)
        val r = repo.setNightMode(_state.value.nightMode, kelvin)
        if (r.success) "✅ Temp: ${kelvin}K" else "❌ ${r.stderr}"
    }

    fun setAodTimeout(seconds: Int) = launch("AOD timeout") {
        _state.value = _state.value.copy(aodTimeoutSec = seconds)
        val r = repo.setAodTimeout(seconds)
        if (r.success) "✅ Timeout: ${if (seconds == 0) "never" else "${seconds}s"}" else "❌ ${r.stderr}"
    }

    fun dumpSettings() = launch("Dump settings") {
        repo.dumpAllSettings()
    }

    private fun launch(opName: String, block: suspend () -> String) =
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, statusMessage = "$opName...")
            val msg = try { block() } catch (e: Exception) { "Exception: ${e.message}" }
            _state.value = _state.value.copy(
                loading = false,
                statusMessage = msg,
                logOutput = "${_state.value.logOutput}\n[$opName] $msg".takeLast(2000)
            )
        }
}
