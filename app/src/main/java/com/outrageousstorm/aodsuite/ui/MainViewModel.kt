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
    val aodEnabled: Boolean = false,
    val brightnessPercent: Int = 20,
    val blurRadius: Int = 12,
    val selectedImageUri: Uri? = null,
    val aodTap: Boolean = true,
    val raiseToWake: Boolean = true,
    val nightMode: Boolean = false,
    val nightTemp: Int = 3200,
    val aodTimeoutSec: Int = 0,
    val statusMessage: String = "Ready",
    val loading: Boolean = false,
    val logOutput: String = ""
)

class MainViewModel(private val repo: AodRepository) : ViewModel() {

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

    fun setAod(enabled: Boolean) = launch("Toggle AOD") {
        val r = repo.enableAod(enabled)
        _state.value = _state.value.copy(aodEnabled = enabled)
        r.output
    }

    fun setMinBrightness(percent: Int) {
        _state.value = _state.value.copy(brightnessPercent = percent)
    }

    fun applyMinBrightness() = launch("Apply brightness") {
        val results = repo.setMinBrightness(_state.value.brightnessPercent)
        results.joinToString("\n") { (if (it.success) "✓" else "✗") + " " + it.output.take(60) }
    }

    fun setBlurRadius(r: Int) {
        _state.value = _state.value.copy(blurRadius = r)
    }

    fun setSelectedImage(uri: Uri?) {
        _state.value = _state.value.copy(selectedImageUri = uri)
    }

    fun applyBlurredWallpaper() = launch("Apply blurred wallpaper") {
        val uri = _state.value.selectedImageUri
            ?: return@launch "No image selected"
        val result = repo.applyBlurredWallpaper(uri, _state.value.blurRadius)
        result.getOrElse { "Error: ${it.message}" }
    }

    fun setAodTap(enabled: Boolean) = launch("Set tap gesture") {
        _state.value = _state.value.copy(aodTap = enabled)
        repo.setAodTap(enabled).output
    }

    fun setRaiseToWake(enabled: Boolean) = launch("Set raise-to-wake") {
        _state.value = _state.value.copy(raiseToWake = enabled)
        repo.setRaiseToWake(enabled).output
    }

    fun setNightMode(enabled: Boolean) {
        _state.value = _state.value.copy(nightMode = enabled)
    }

    fun setNightTemp(k: Int) {
        _state.value = _state.value.copy(nightTemp = k)
    }

    fun applyNightMode() = launch("Apply night mode") {
        val results = repo.setNightMode(_state.value.nightMode, _state.value.nightTemp)
        results.joinToString("\n") { (if (it.success) "✓" else "✗") + " " + it.output.take(60) }
    }

    fun setAodTimeout(sec: Int) {
        _state.value = _state.value.copy(aodTimeoutSec = sec)
    }

    fun applyAodTimeout() = launch("Apply AOD timeout") {
        repo.setAodTimeout(_state.value.aodTimeoutSec).output
    }

    fun forceBatteryIgnore() = launch("Ignore battery restriction") {
        repo.forceBatteryIgnore().output
    }

    fun dumpSettings() = launch("Dump AOD settings") {
        repo.dumpAllSettings()
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private fun launch(label: String, block: suspend () -> String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, statusMessage = "$label…")
            val out = try { block() } catch (e: Exception) { "Error: ${e.message}" }
            _state.value = _state.value.copy(
                loading = false,
                statusMessage = "$label done",
                logOutput = _state.value.logOutput + "\n[$label]\n$out\n"
            )
        }
    }
}
