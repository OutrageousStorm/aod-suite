package com.outrageousstorm.aodsuite.ui

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.outrageousstorm.aodsuite.databinding.ActivityMainBinding
import com.outrageousstorm.aodsuite.shizuku.ShizukuHelper
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val vm: MainViewModel by viewModels { MainViewModelFactory(this) }

    // Shizuku permission callback
    private val shizukuPermListener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
        val granted = grantResult == PackageManager.PERMISSION_GRANTED
        Toast.makeText(this, if (granted) "Shizuku granted ✓" else "Shizuku denied ✗", Toast.LENGTH_SHORT).show()
        vm.refresh(ShizukuHelper.isAvailable, granted)
    }

    // Image picker
    private val pickImage = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri: Uri? = result.data?.data
            vm.setSelectedImage(uri)
            uri?.let { binding.tvImageName.text = getFileName(it) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        Shizuku.addRequestPermissionResultListener(shizukuPermListener)

        setupUi()
        observeState()

        // Initial state check
        vm.refresh(ShizukuHelper.isAvailable, ShizukuHelper.isGranted)
    }

    override fun onDestroy() {
        super.onDestroy()
        Shizuku.removeRequestPermissionResultListener(shizukuPermListener)
    }

    // ─── UI setup ─────────────────────────────────────────────────────────────

    private fun setupUi() {

        // Shizuku
        binding.btnRequestShizuku.setOnClickListener {
            if (!ShizukuHelper.isAvailable) {
                Toast.makeText(this, "Shizuku is not running. Install & start Shizuku first.", Toast.LENGTH_LONG).show()
            } else {
                ShizukuHelper.requestPermission()
            }
        }

        // AOD toggle
        binding.switchAod.setOnCheckedChangeListener { _, checked ->
            vm.setAod(checked)
        }

        // Brightness slider
        binding.sliderBrightness.addOnChangeListener { _, value, _ ->
            vm.setMinBrightness(value.toInt())
            binding.tvBrightnessValue.text = "${value.toInt()}%"
        }
        binding.btnApplyBrightness.setOnClickListener {
            vm.applyMinBrightness()
        }

        // Blur radius slider
        binding.sliderBlur.addOnChangeListener { _, value, _ ->
            vm.setBlurRadius(value.toInt())
            binding.tvBlurValue.text = "Radius: ${value.toInt()}"
        }

        // Image picker
        binding.btnPickImage.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            pickImage.launch(intent)
        }
        binding.btnApplyWallpaper.setOnClickListener {
            vm.applyBlurredWallpaper()
        }

        // Gestures
        binding.switchTap.setOnCheckedChangeListener { _, checked -> vm.setAodTap(checked) }
        binding.switchRaise.setOnCheckedChangeListener { _, checked -> vm.setRaiseToWake(checked) }

        // Night mode
        binding.switchNightMode.setOnCheckedChangeListener { _, checked -> vm.setNightMode(checked) }
        binding.sliderNightTemp.addOnChangeListener { _, value, _ ->
            vm.setNightTemp(value.toInt())
            binding.tvNightTempValue.text = "${value.toInt()}K"
        }
        binding.btnApplyNightMode.setOnClickListener { vm.applyNightMode() }

        // Timeout
        binding.sliderTimeout.addOnChangeListener { _, value, _ ->
            val sec = value.toInt()
            vm.setAodTimeout(sec)
            binding.tvTimeoutValue.text = if (sec == 0) "Never" else "${sec}s"
        }
        binding.btnApplyTimeout.setOnClickListener { vm.applyAodTimeout() }

        // Battery
        binding.btnForceBattery.setOnClickListener { vm.forceBatteryIgnore() }

        // Dump
        binding.btnDump.setOnClickListener { vm.dumpSettings() }

        // Clear log
        binding.btnClearLog.setOnClickListener {
            binding.tvLog.text = ""
        }
    }

    // ─── State observation ────────────────────────────────────────────────────

    private fun observeState() {
        lifecycleScope.launch {
            vm.state.collect { s ->

                // Shizuku status
                val status = when {
                    !s.shizukuAvailable -> "⚠ Shizuku not running"
                    !s.shizukuGranted   -> "⚠ Permission needed"
                    else                -> "✓ Shizuku active"
                }
                binding.tvShizukuStatus.text = status
                binding.btnRequestShizuku.isEnabled = s.shizukuAvailable && !s.shizukuGranted

                // Controls enabled only when Shizuku is ready
                val enabled = s.shizukuGranted
                binding.switchAod.isEnabled = enabled
                binding.sliderBrightness.isEnabled = enabled
                binding.btnApplyBrightness.isEnabled = enabled
                binding.btnPickImage.isEnabled = enabled
                binding.btnApplyWallpaper.isEnabled = enabled && s.selectedImageUri != null
                binding.switchTap.isEnabled = enabled
                binding.switchRaise.isEnabled = enabled
                binding.switchNightMode.isEnabled = enabled
                binding.sliderNightTemp.isEnabled = enabled && s.nightMode
                binding.btnApplyNightMode.isEnabled = enabled
                binding.sliderTimeout.isEnabled = enabled
                binding.btnApplyTimeout.isEnabled = enabled
                binding.btnForceBattery.isEnabled = enabled
                binding.btnDump.isEnabled = enabled

                // AOD toggle (suppress listener briefly)
                binding.switchAod.isChecked = s.aodEnabled

                // Sliders
                if (binding.sliderBrightness.value != s.brightnessPercent.toFloat()) {
                    binding.sliderBrightness.value = s.brightnessPercent.toFloat()
                }
                binding.tvBrightnessValue.text = "${s.brightnessPercent}%"
                binding.tvBlurValue.text = "Radius: ${s.blurRadius}"

                // Night temp
                if (binding.sliderNightTemp.value != s.nightTemp.toFloat()) {
                    binding.sliderNightTemp.value = s.nightTemp.toFloat().coerceIn(1500f, 6500f)
                }
                binding.tvNightTempValue.text = "${s.nightTemp}K"

                // Status + log
                binding.tvStatus.text = s.statusMessage
                if (s.logOutput.isNotBlank()) {
                    binding.tvLog.text = s.logOutput.takeLast(3000)
                }
            }
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private fun getFileName(uri: Uri): String {
        return try {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                cursor.moveToFirst(); cursor.getString(idx)
            } ?: uri.lastPathSegment ?: "image"
        } catch (e: Exception) { "image" }
    }
}
