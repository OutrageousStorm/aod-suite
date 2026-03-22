package com.outrageousstorm.aodsuite.ui

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.outrageousstorm.aodsuite.databinding.ActivityMainBinding
import com.outrageousstorm.aodsuite.shizuku.ShizukuHelper
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val vm: MainViewModel by viewModels { MainViewModelFactory(this) }

    private val shizukuPermListener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
        val granted = grantResult == PackageManager.PERMISSION_GRANTED
        Toast.makeText(this, if (granted) "Shizuku granted ✓" else "Shizuku denied ✗", Toast.LENGTH_SHORT).show()
        vm.refresh(ShizukuHelper.isAvailable, granted)
    }

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
        vm.refresh(ShizukuHelper.isAvailable, ShizukuHelper.isGranted)
    }

    override fun onDestroy() {
        super.onDestroy()
        Shizuku.removeRequestPermissionResultListener(shizukuPermListener)
    }

    private fun setupUi() {
        binding.btnRequestShizuku.setOnClickListener {
            if (!ShizukuHelper.isAvailable) {
                Toast.makeText(this, "Shizuku is not running. Install & start it first.", Toast.LENGTH_LONG).show()
            } else {
                ShizukuHelper.requestPermission()
            }
        }

        // One-time setup: grant WRITE_SECURE_SETTINGS to this app via Shizuku
        binding.btnSetup.setOnClickListener {
            if (!ShizukuHelper.isAvailable || !ShizukuHelper.isGranted) {
                Toast.makeText(this, "Connect Shizuku first", Toast.LENGTH_SHORT).show()
            } else {
                vm.grantPermissions()
            }
        }

        binding.switchAod.setOnCheckedChangeListener { _, checked -> vm.setAod(checked) }

        binding.sliderBrightness.addOnChangeListener { _, value, _ ->
            vm.setMinBrightness(value.toInt())
            binding.tvBrightnessValue.text = "${value.toInt()}%"
        }
        binding.btnApplyBrightness.setOnClickListener { vm.applyMinBrightness() }

        binding.sliderBlur.addOnChangeListener { _, value, _ ->
            vm.setBlurRadius(value.toInt())
            binding.tvBlurValue.text = "Radius: ${value.toInt()}"
        }

        binding.btnPickImage.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            pickImage.launch(intent)
        }
        binding.btnApplyWallpaper.setOnClickListener { vm.applyBlurredWallpaper() }

        binding.switchTap.setOnCheckedChangeListener { _, checked -> vm.setAodTap(checked) }
        binding.switchRaise.setOnCheckedChangeListener { _, checked -> vm.setRaiseToWake(checked) }
        binding.switchNightMode.setOnCheckedChangeListener { _, checked -> vm.setNightMode(checked) }
        binding.sliderNightTemp.addOnChangeListener { _, value, _ -> vm.setNightTemp(value.toInt()) }
        binding.sliderTimeout.addOnChangeListener { _, value, _ -> vm.setAodTimeout(value.toInt()) }

        binding.btnDump.setOnClickListener { vm.dumpSettings() }
        binding.btnClearLog.setOnClickListener {
            binding.tvLog.text = ""
        }
    }

    private fun observeState() {
        lifecycleScope.launch {
            vm.state.collect { s ->
                binding.tvShizukuStatus.text = when {
                    !s.shizukuAvailable -> "⚠️ Shizuku not running"
                    !s.shizukuGranted   -> "⚠️ Shizuku: tap to grant"
                    else                -> "✓ Shizuku connected"
                }
                binding.btnRequestShizuku.visibility =
                    if (s.shizukuAvailable && s.shizukuGranted) View.GONE else View.VISIBLE

                // Show Setup button when Shizuku is ready but app permissions not yet granted
                binding.btnSetup.visibility =
                    if (s.shizukuGranted && !s.permissionsGranted) View.VISIBLE else View.GONE

                binding.progressBar.visibility = if (s.loading) View.VISIBLE else View.GONE

                binding.switchAod.isChecked = s.aodEnabled
                binding.tvBrightnessValue.text = "${s.brightnessPercent}%"
                binding.tvBlurValue.text = "Radius: ${s.blurRadius}"
                binding.tvStatus.text = s.statusMessage
                binding.tvLog.text = s.logOutput
            }
        }
    }

    private fun getFileName(uri: Uri): String =
        contentResolver.query(uri, arrayOf("_display_name"), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else uri.lastPathSegment
        } ?: uri.lastPathSegment ?: "image"
}
