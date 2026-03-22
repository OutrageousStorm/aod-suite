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

    private val shizukuPermListener = Shizuku.OnRequestPermissionResultListener { _, result ->
        val granted = result == PackageManager.PERMISSION_GRANTED
        Toast.makeText(this, if (granted) "Shizuku granted ✓" else "Shizuku denied", Toast.LENGTH_SHORT).show()
        vm.refreshShizuku(ShizukuHelper.isAvailable, granted)
    }

    private val pickImage = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val uri: Uri? = res.data?.data
            vm.setSelectedImage(uri)
            binding.tvImageName.text = uri?.lastPathSegment ?: "image selected"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        Shizuku.addRequestPermissionResultListener(shizukuPermListener)
        wire()
        observe()
        vm.refreshShizuku(ShizukuHelper.isAvailable, ShizukuHelper.isGranted)
    }

    override fun onDestroy() {
        super.onDestroy()
        runCatching { Shizuku.removeRequestPermissionResultListener(shizukuPermListener) }
    }

    private fun wire() {
        binding.btnRequestShizuku.setOnClickListener {
            if (!ShizukuHelper.isAvailable)
                Toast.makeText(this, "Shizuku not running — install & start it first", Toast.LENGTH_LONG).show()
            else ShizukuHelper.requestPermission()
        }
        binding.btnSetup.setOnClickListener {
            if (!ShizukuHelper.isGranted)
                Toast.makeText(this, "Grant Shizuku permission first", Toast.LENGTH_SHORT).show()
            else vm.grantPermissions()
        }
        binding.switchAod.setOnCheckedChangeListener { _, on -> vm.setAod(on) }
        binding.sliderBrightness.addOnChangeListener { _, v, _ ->
            vm.setMinBrightness(v.toInt()); binding.tvBrightnessValue.text = "${v.toInt()}%"
        }
        binding.btnApplyBrightness.setOnClickListener { vm.applyMinBrightness() }
        binding.sliderBlur.addOnChangeListener { _, v, _ ->
            vm.setBlurRadius(v.toInt()); binding.tvBlurValue.text = "Radius: ${v.toInt()}"
        }
        binding.btnPickImage.setOnClickListener {
            pickImage.launch(Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI))
        }
        binding.btnApplyWallpaper.setOnClickListener { vm.applyWallpaper() }
        binding.switchTap.setOnCheckedChangeListener { _, on -> vm.setAodTap(on) }
        binding.switchRaise.setOnCheckedChangeListener { _, on -> vm.setRaiseToWake(on) }
        binding.switchNightMode.setOnCheckedChangeListener { _, on -> vm.setNightMode(on) }
        binding.sliderNightTemp.addOnChangeListener { _, v, _ -> vm.setNightTemp(v.toInt()) }
        binding.sliderTimeout.addOnChangeListener { _, v, _ -> vm.setAodTimeout(v.toInt()) }
        binding.btnDump.setOnClickListener { vm.dumpSettings() }
        binding.btnClearLog.setOnClickListener { binding.tvLog.text = "" }
    }

    private fun observe() {
        lifecycleScope.launch {
            vm.state.collect { s ->
                binding.tvShizukuStatus.text = when {
                    !s.shizukuAvailable -> "⚠️ Shizuku not running"
                    !s.shizukuGranted   -> "⚠️ Tap to grant Shizuku"
                    s.permissionsGranted -> "✅ Ready"
                    else                -> "✓ Shizuku connected — tap Setup"
                }
                binding.btnRequestShizuku.visibility =
                    if (s.shizukuAvailable && s.shizukuGranted) View.GONE else View.VISIBLE
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
}
