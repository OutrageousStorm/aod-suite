package com.outrageousstorm.aodsuite.shizuku

import android.util.Log

private const val TAG = "ShellService"

/**
 * Minimal Shizuku UserService — only used once to grant WRITE_SECURE_SETTINGS
 * to this app. After that, the app writes settings itself via ContentResolver.
 */
class ShellService : IShellService.Stub() {

    override fun exec(command: String): String {
        Log.d(TAG, "exec uid=${android.os.Process.myUid()}: $command")
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            val stdout = process.inputStream.bufferedReader().readText().trim()
            val stderr = process.errorStream.bufferedReader().readText().trim()
            val exit = process.waitFor()
            "EXIT:$exit\nOUT:$stdout\nERR:$stderr"
        } catch (e: Exception) {
            "EXIT:-1\nOUT:\nERR:${e.message}"
        }
    }
}
