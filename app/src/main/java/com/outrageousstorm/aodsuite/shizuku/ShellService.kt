package com.outrageousstorm.aodsuite.shizuku

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.Settings
import android.util.Log

private const val TAG = "ShellService"

/**
 * Runs inside the Shizuku process (shell UID = 2000).
 * Shell UID has WRITE_SECURE_SETTINGS — we write settings via ContentResolver
 * directly, avoiding the SecurityException that subprocess exec() causes.
 */
class ShellService : IShellService.Stub() {

    private val appContext: Context? by lazy {
        try {
            val clazz = Class.forName("android.app.ActivityThread")
            val method = clazz.getMethod("currentApplication")
            method.invoke(null) as? Context
        } catch (e: Exception) {
            Log.e(TAG, "Could not get app context", e)
            null
        }
    }

    override fun putSecure(key: String, value: String): Boolean =
        putSetting(Settings.Secure.CONTENT_URI, "secure", key, value)

    override fun putSystem(key: String, value: String): Boolean =
        putSetting(Settings.System.CONTENT_URI, "system", key, value)

    override fun putGlobal(key: String, value: String): Boolean =
        putSetting(Settings.Global.CONTENT_URI, "global", key, value)

    override fun getSecure(key: String): String = try {
        appContext?.contentResolver?.let { Settings.Secure.getString(it, key) }
            ?: shellExec("settings get secure $key")
    } catch (e: Exception) { "" }

    override fun getSystem(key: String): String = try {
        appContext?.contentResolver?.let { Settings.System.getString(it, key) }
            ?: shellExec("settings get system $key")
    } catch (e: Exception) { "" }

    override fun exec(command: String): String {
        Log.d(TAG, "exec uid=${android.os.Process.myUid()}: $command")
        return shellExec(command)
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private fun putSetting(uri: Uri, namespace: String, key: String, value: String): Boolean {
        Log.d(TAG, "putSetting uid=${android.os.Process.myUid()} $namespace/$key=$value")
        val cr = appContext?.contentResolver
        return if (cr != null) {
            try {
                val cv = ContentValues(2)
                cv.put("name", key)
                cv.put("value", value)
                cr.insert(uri, cv)
                Log.d(TAG, "putSetting OK via ContentResolver")
                true
            } catch (e: Exception) {
                Log.w(TAG, "ContentResolver failed ($e), trying shell fallback")
                shellExec("settings put $namespace $key $value").contains("EXIT:0").also { ok ->
                    Log.d(TAG, "shell fallback result: $ok")
                }
            }
        } else {
            Log.w(TAG, "No ContentResolver — using shell")
            shellExec("settings put $namespace $key $value").contains("EXIT:0")
        }
    }

    private fun shellExec(command: String): String {
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
