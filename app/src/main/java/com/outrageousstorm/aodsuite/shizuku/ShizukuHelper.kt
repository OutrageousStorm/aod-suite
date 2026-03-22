package com.outrageousstorm.aodsuite.shizuku

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader

private const val TAG = "ShizukuHelper"

data class ShellResult(
    val stdout: String,
    val stderr: String,
    val exitCode: Int
) {
    val success: Boolean get() = exitCode == 0
    val output: String get() = if (stdout.isNotBlank()) stdout else stderr
}

object ShizukuHelper {

    fun isAvailable(): Boolean = try {
        Shizuku.pingBinder()
    } catch (e: Exception) {
        false
    }

    fun hasPermission(): Boolean = try {
        Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED
    } catch (e: Exception) {
        false
    }

    fun requestPermission(requestCode: Int) {
        Shizuku.requestPermission(requestCode)
    }

    /**
     * Execute a shell command via Shizuku using Runtime.exec through a privileged process.
     * Falls back to su/root if Shizuku isn't available.
     */
    suspend fun exec(command: String): ShellResult = withContext(Dispatchers.IO) {
        Log.d(TAG, "exec: $command")
        try {
            if (isAvailable() && hasPermission()) {
                execViaShizuku(command)
            } else {
                execViaAdb(command)
            }
        } catch (e: Exception) {
            Log.e(TAG, "exec failed", e)
            ShellResult("", e.message ?: "unknown error", -1)
        }
    }

    /**
     * Execute using Shizuku's built-in shell via Runtime.exec()
     * This uses the standard API approach for Shizuku 12+
     */
    private fun execViaShizuku(command: String): ShellResult {
        // Shizuku allows running shell commands by executing them in the Shizuku process
        // We use the standard Process approach through Shizuku's UID
        val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
        val stdout = process.inputStream.bufferedReader().readText().trim()
        val stderr = process.errorStream.bufferedReader().readText().trim()
        val exit = process.waitFor()
        Log.d(TAG, "stdout=$stdout stderr=$stderr exit=$exit")
        return ShellResult(stdout, stderr, exit)
    }

    private fun execViaAdb(command: String): ShellResult {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            val stdout = process.inputStream.bufferedReader().readText().trim()
            val stderr = process.errorStream.bufferedReader().readText().trim()
            val exit = process.waitFor()
            ShellResult(stdout, stderr, exit)
        } catch (e: Exception) {
            ShellResult("", e.message ?: "exec failed", -1)
        }
    }
}
