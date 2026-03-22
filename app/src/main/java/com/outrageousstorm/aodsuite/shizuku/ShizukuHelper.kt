package com.outrageousstorm.aodsuite.shizuku

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuRemoteProcess

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

    /**
     * Execute a shell command through Shizuku (runs as shell/ADB uid 2000).
     * Uses Shizuku.newProcess() which spawns the process in the Shizuku server's context.
     */
    suspend fun exec(command: String): ShellResult = withContext(Dispatchers.IO) {
        Log.d(TAG, "exec: $command")
        if (!isAvailable) {
            return@withContext ShellResult("", "Shizuku is not running", -1)
        }
        if (!isGranted) {
            return@withContext ShellResult("", "Shizuku permission not granted", -1)
        }
        try {
            val process: ShizukuRemoteProcess = Shizuku.newProcess(
                arrayOf("sh", "-c", command),
                null,
                null
            )
            val stdout = process.inputStream.bufferedReader().readText().trim()
            val stderr = process.errorStream.bufferedReader().readText().trim()
            val exit = process.waitFor()
            Log.d(TAG, "stdout=$stdout stderr=$stderr exit=$exit")
            ShellResult(stdout, stderr, exit)
        } catch (e: Exception) {
            Log.e(TAG, "Shizuku exec failed", e)
            ShellResult("", e.message ?: "exec failed", -1)
        }
    }
}
