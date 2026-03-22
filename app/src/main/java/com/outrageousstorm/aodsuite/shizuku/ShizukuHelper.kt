package com.outrageousstorm.aodsuite.shizuku

import android.content.pm.PackageManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuRemoteProcess

private const val TAG = "ShizukuHelper"
private const val SHIZUKU_PERMISSION = "moe.shizuku.manager.permission.API_V23"

object ShizukuHelper {

    // ─── State ────────────────────────────────────────────────────────────────

    val isAvailable: Boolean
        get() = try { Shizuku.pingBinder() } catch (e: Exception) { false }

    val isGranted: Boolean
        get() = try {
            if (Shizuku.isPreV11()) {
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            } else {
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            }
        } catch (e: Exception) { false }

    // ─── Permission ───────────────────────────────────────────────────────────

    fun requestPermission(code: Int = 42) {
        try {
            Shizuku.requestPermission(code)
        } catch (e: Exception) {
            Log.e(TAG, "requestPermission failed", e)
        }
    }

    // ─── Execution ────────────────────────────────────────────────────────────

    /**
     * Run a shell command via Shizuku and return (exitCode, stdout, stderr)
     */
    suspend fun exec(command: String): ShellResult = withContext(Dispatchers.IO) {
        if (!isAvailable) return@withContext ShellResult(-1, "", "Shizuku not available")
        if (!isGranted)   return@withContext ShellResult(-2, "", "Shizuku permission not granted")

        return@withContext try {
            val process: ShizukuRemoteProcess = Shizuku.newProcess(
                arrayOf("sh", "-c", command), null, null
            )
            val stdout = process.inputStream.bufferedReader().readText()
            val stderr = process.errorStream.bufferedReader().readText()
            val code   = process.waitFor()
            Log.d(TAG, "exec[$code] $command → $stdout $stderr")
            ShellResult(code, stdout.trim(), stderr.trim())
        } catch (e: Exception) {
            Log.e(TAG, "exec failed: $command", e)
            ShellResult(-3, "", e.message ?: "Unknown error")
        }
    }

    /**
     * Run multiple commands in sequence; returns list of results
     */
    suspend fun execAll(vararg commands: String): List<ShellResult> =
        commands.map { exec(it) }
}

data class ShellResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String
) {
    val success: Boolean get() = exitCode == 0
    val output: String get() = stdout.ifBlank { stderr }
}
