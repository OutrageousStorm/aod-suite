package com.outrageousstorm.aodsuite.shizuku

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

private const val TAG = "ShizukuHelper"
private const val REQUEST_CODE = 1001

data class ShellResult(val stdout: String, val stderr: String, val exitCode: Int) {
    val success get() = exitCode == 0
    val output  get() = if (stdout.isNotBlank()) stdout else stderr
}

object ShizukuHelper {

    val isAvailable: Boolean
        get() = runCatching { Shizuku.pingBinder() }.getOrDefault(false)

    val isGranted: Boolean
        get() = runCatching {
            Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)

    fun requestPermission() {
        runCatching { Shizuku.requestPermission(REQUEST_CODE) }
    }

    /**
     * Run a shell command via Shizuku.newProcess() — runs as shell UID.
     * No UserService, no AIDL, no separate process that can crash Shizuku.
     */
    suspend fun exec(command: String): ShellResult = withContext(Dispatchers.IO) {
        Log.d(TAG, "exec: $command")
        if (!isAvailable) return@withContext ShellResult("", "Shizuku not running", -1)
        if (!isGranted)   return@withContext ShellResult("", "Shizuku not granted", -1)

        runCatching {
            val process = Shizuku.newProcess(arrayOf("sh", "-c", command), null, null)
            val stdout = process.inputStream.bufferedReader().readText().trim()
            val stderr = process.errorStream.bufferedReader().readText().trim()
            val exit   = process.waitFor()
            Log.d(TAG, "exit=$exit out=$stdout err=$stderr")
            ShellResult(stdout, stderr, exit)
        }.getOrElse { e ->
            Log.e(TAG, "exec failed", e)
            ShellResult("", e.message ?: "error", -1)
        }
    }

    /** Grant WRITE_SECURE_SETTINGS to this app. Call once via Setup button. */
    suspend fun grantSelf(packageName: String): ShellResult {
        val r = exec("pm grant $packageName android.permission.WRITE_SECURE_SETTINGS")
        Log.d(TAG, "grantSelf result: $r")
        return r
    }
}
