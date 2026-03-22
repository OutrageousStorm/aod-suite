package com.outrageousstorm.aodsuite.shizuku

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

private const val TAG = "ShizukuHelper"
private const val SHIZUKU_REQUEST_CODE = 1001

data class ShellResult(
    val stdout: String,
    val stderr: String,
    val exitCode: Int
) {
    val success: Boolean get() = exitCode == 0
    val output: String get() = if (stdout.isNotBlank()) stdout else stderr
}

object ShizukuHelper {

    val isAvailable: Boolean
        get() = try { Shizuku.pingBinder() } catch (e: Exception) { false }

    val isGranted: Boolean
        get() = try {
            Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) { false }

    fun requestPermission(requestCode: Int = SHIZUKU_REQUEST_CODE) {
        Shizuku.requestPermission(requestCode)
    }

    suspend fun exec(command: String): ShellResult = withContext(Dispatchers.IO) {
        Log.d(TAG, "exec: $command")
        try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            val stdout = process.inputStream.bufferedReader().readText().trim()
            val stderr = process.errorStream.bufferedReader().readText().trim()
            val exit = process.waitFor()
            Log.d(TAG, "stdout=$stdout stderr=$stderr exit=$exit")
            ShellResult(stdout, stderr, exit)
        } catch (e: Exception) {
            Log.e(TAG, "exec failed", e)
            ShellResult("", e.message ?: "unknown error", -1)
        }
    }
}
