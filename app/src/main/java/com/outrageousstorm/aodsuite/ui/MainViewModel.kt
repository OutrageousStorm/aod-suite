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
    val nightMode: Boolean = false,
    val nightTemp: Int = 3200,
    val aodTimeoutSec: Int = 0,
    val statusMessage: String = "Connect Shizuku → tap Setup → done",
    val loading: Boolean = false,
    val logOutput: String = ""
)

class MainViewModel(
    private val repo: AodRepository,
    private val packageName: String
) : ViewModel() {

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun refreshShizuku(available: Boolean, granted: Boolean) {
        _state.value = _state.value.copy(shizukuAvailable = available, shizukuGranted = granted)
    }

    fun grantPermissions() = run("Setup") {
        val r = repo.grantPermissions(packageName)
        if (r.success) {
            _state.value = _state.value.copy(permissionsGranted = true)
            "✅ Permissions granted! All features unlocked."
        } else {
            "❌ Grant failed: ${r.stderr.take(100)}"
        }
    }

    fun setAod(enabled: Boolean) = run("Toggle AOD") {
        val r = repo.enableAod(enabled)
        if (r.success) _state.value = _state.value.copy(aodEnabled = enabled)
        if (r.success) "✅ AOD ${if (enabled) "on" else "off"}" else "❌ ${r.stderr.take(100)}"
    }

    fun setMinBrightness(pct: Int) { _state.value = _state.value.copy(brightnessPercent = pct) }

    fun applyMinBrightness() = run("Apply brightness") {
        val r = repo.setMinBrightness(_state.value.brightnessPercent)
        if (r.success) "✅ Brightness → ${_state.value.brightnessPercent}%" else "❌ ${r.stderr.take(100)}"
    }

    fun setBlurRadius(v: Int) { _state.value = _state.value.copy(blurRadius = v) }
    fun setSelectedImage(uri: Uri?) { _state.value = _state.value.copy(selectedImageUri = uri) }

    fun applyWallpaper() = run("Apply wallpaper") {
        val uri = _state.value.selectedImageUri ?: return@run "No image selected"
        repo.applyBlurredWallpaper(uri, _state.value.blurRadius).getOrElse { "Error: ${it.message}" }
    }

    fun setAodTap(on: Boolean) = run("Tap gesture") {
        val r = repo.setAodTap(on); if (r.success) "✅ Tap ${if(on)"on"else"off"}" else "❌ ${r.stderr}"
    }
    fun setRaiseToWake(on: Boolean) = run("Raise-to-wake") {
        val r = repo.setRaiseToWake(on); if (r.success) "✅ Raise ${if(on)"on"else"off"}" else "❌ ${r.stderr}"
    }
    fun setNightMode(on: Boolean) = run("Night mode") {
        _state.value = _state.value.copy(nightMode = on)
        val r = repo.setNightMode(on, _state.value.nightTemp)
        if (r.success) "✅ Night ${if(on)"on"else"off"}" else "❌ ${r.stderr}"
    }
    fun setNightTemp(k: Int) = run("Night temp") {
        _state.value = _state.value.copy(nightTemp = k)
        val r = repo.setNightMode(_state.value.nightMode, k)
        if (r.success) "✅ ${k}K" else "❌ ${r.stderr}"
    }
    fun setAodTimeout(s: Int) = run("Timeout") {
        _state.value = _state.value.copy(aodTimeoutSec = s)
        val r = repo.setAodTimeout(s)
        if (r.success) "✅ Timeout ${if(s==0)"never" else "${s}s"}" else "❌ ${r.stderr}"
    }
    fun dumpSettings() = run("Dump") { repo.dumpAllSettings() }

    private fun run(op: String, block: suspend () -> String) = viewModelScope.launch {
        _state.value = _state.value.copy(loading = true, statusMessage = "$op…")
        val msg = runCatching { block() }.getOrElse { "❌ ${it.message?.take(80)}" }
        _state.value = _state.value.copy(
            loading = false, statusMessage = msg,
            logOutput = (_state.value.logOutput + "\n[$op] $msg").takeLast(2000)
        )
    }
}
