package com.outrageousstorm.aodsuite.aod

import android.content.ContentResolver
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.ShizukuUserServiceArgs
import com.outrageousstorm.aodsuite.shizuku.ShizukuHelper
import java.util.concurrent.TimeoutException

private const val TAG = "AodRepository"

object AodRepository {

    suspend fun setAodMinBrightness(level: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "setAodMinBrightness: $level")
            // Method 1: Direct settings
            ShizukuHelper.putSecure("aod_brightness", level.toString())
            // Method 2: AOD-specific key (device-specific)
            ShizukuHelper.putSecure("aod_min_brightness", level.toString())
            true
        } catch (e: Exception) {
            Log.e(TAG, "setAodMinBrightness failed", e)
            false
        }
    }

    suspend fun toggleAod(enable: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "toggleAod: $enable")
            ShizukuHelper.putSecure("always_on_display", if (enable) "1" else "0")
            true
        } catch (e: Exception) {
            Log.e(TAG, "toggleAod failed", e)
            false
        }
    }

    suspend fun setAodTimeout(seconds: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "setAodTimeout: ${seconds}s")
            ShizukuHelper.putSecure("aod_tap_to_show_screen_timeout", seconds.toString())
            true
        } catch (e: Exception) {
            Log.e(TAG, "setAodTimeout failed", e)
            false
        }
    }

    suspend fun getAodStatus(): Map<String, Any> = withContext(Dispatchers.IO) {
        return@withContext try {
            mapOf(
                "brightness" to (ShizukuHelper.getSecure("aod_brightness") ?: "128"),
                "enabled" to (ShizukuHelper.getSecure("always_on_display") ?: "1"),
                "timeout" to (ShizukuHelper.getSecure("aod_tap_to_show_screen_timeout") ?: "0")
            )
        } catch (e: Exception) {
            mapOf("error" to e.message.toString())
        }
    }
}

private suspend inline fun ShizukuHelper.putSecure(key: String, value: String) {
    exec("settings put secure $key $value")
}

private suspend inline fun ShizukuHelper.getSecure(key: String): String? {
    return exec("settings get secure $key").output.takeIf { it.isNotEmpty() }
}
