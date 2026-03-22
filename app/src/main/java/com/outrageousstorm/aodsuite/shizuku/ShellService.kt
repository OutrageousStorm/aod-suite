package com.outrageousstorm.aodsuite.shizuku

import android.util.Log

private const val TAG = "ShellService"

class ShellService : IShellService.Stub() {
    override fun exec(command: String): String {
        return try {
            val p = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            val out = p.inputStream.bufferedReader().readText().trim()
            val err = p.errorStream.bufferedReader().readText().trim()
            val code = p.waitFor()
            "EXIT:$code|OUT:$out|ERR:$err"
        } catch (e: Exception) {
            Log.e(TAG, "exec error", e)
            "EXIT:-1|OUT:|ERR:${e.message}"
        }
    }
}
