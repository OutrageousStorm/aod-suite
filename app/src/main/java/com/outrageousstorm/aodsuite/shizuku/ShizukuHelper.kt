package com.outrageousstorm.aodsuite.shizuku

import android.content.ComponentName
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku
import kotlin.coroutines.resume

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

    private var shellService: IShellService? = null
    private var serviceConnection: ServiceConnection? = null

    val isAvailable: Boolean
        get() = try { Shizuku.pingBinder() } catch (e: Exception) { false }

    val isGranted: Boolean
        get() = try {
            Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) { false }

    fun requestPermission(requestCode: Int = SHIZUKU_REQUEST_CODE) {
        Shizuku.requestPermission(requestCode)
    }

    fun unbind() {
        serviceConnection?.let {
            try { Shizuku.unbindUserService(userServiceArgs, it, true) } catch (_: Exception) {}
        }
        shellService = null
        serviceConnection = null
    }

    private val userServiceArgs: Shizuku.UserServiceArgs by lazy {
        Shizuku.UserServiceArgs(
            ComponentName(
                "com.outrageousstorm.aodsuite",
                ShellService::class.java.name
            )
        )
            .daemon(false)
            .processNameSuffix("shell_service")
            .debuggable(false)
            .version(2)
    }

    private suspend fun getService(): IShellService? {
        shellService?.let { return it }
        if (!isAvailable || !isGranted) return null

        return withTimeoutOrNull(8_000) {
            suspendCancellableCoroutine { cont ->
                val conn = object : ServiceConnection {
                    override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                        val svc = IShellService.Stub.asInterface(binder)
                        shellService = svc
                        serviceConnection = this
                        if (cont.isActive) cont.resume(svc)
                    }
                    override fun onServiceDisconnected(name: ComponentName?) {
                        shellService = null
                    }
                }
                serviceConnection = conn
                try {
                    Shizuku.bindUserService(userServiceArgs, conn)
                } catch (e: Exception) {
                    Log.e(TAG, "bindUserService failed", e)
                    if (cont.isActive) cont.resume(null)
                }
            }
        }
    }

    // ─── High-level helpers (used by AodRepository) ───────────────────────────

    suspend fun putSecure(key: String, value: String): ShellResult = withContext(Dispatchers.IO) {
        val svc = getService() ?: return@withContext noService()
        val ok = try { svc.putSecure(key, value) } catch (e: Exception) { return@withContext error(e) }
        if (ok) ShellResult(value, "", 0) else ShellResult("", "putSecure failed", 1)
    }

    suspend fun putSystem(key: String, value: String): ShellResult = withContext(Dispatchers.IO) {
        val svc = getService() ?: return@withContext noService()
        val ok = try { svc.putSystem(key, value) } catch (e: Exception) { return@withContext error(e) }
        if (ok) ShellResult(value, "", 0) else ShellResult("", "putSystem failed", 1)
    }

    suspend fun putGlobal(key: String, value: String): ShellResult = withContext(Dispatchers.IO) {
        val svc = getService() ?: return@withContext noService()
        val ok = try { svc.putGlobal(key, value) } catch (e: Exception) { return@withContext error(e) }
        if (ok) ShellResult(value, "", 0) else ShellResult("", "putGlobal failed", 1)
    }

    suspend fun getSecure(key: String): String = withContext(Dispatchers.IO) {
        try { getService()?.getSecure(key) ?: "" } catch (e: Exception) { "" }
    }

    suspend fun getSystem(key: String): String = withContext(Dispatchers.IO) {
        try { getService()?.getSystem(key) ?: "" } catch (e: Exception) { "" }
    }

    suspend fun exec(command: String): ShellResult = withContext(Dispatchers.IO) {
        Log.d(TAG, "exec: $command")
        if (!isAvailable) return@withContext ShellResult("", "Shizuku not running", -1)
        if (!isGranted) return@withContext ShellResult("", "Shizuku permission not granted", -1)
        val svc = getService() ?: return@withContext noService()
        try {
            val raw = svc.exec(command)
            val lines = raw.split("\n", limit = 3)
            val exit = lines.getOrNull(0)?.removePrefix("EXIT:")?.toIntOrNull() ?: -1
            val out  = lines.getOrNull(1)?.removePrefix("OUT:") ?: ""
            val err  = lines.getOrNull(2)?.removePrefix("ERR:") ?: ""
            ShellResult(out, err, exit)
        } catch (e: Exception) {
            error(e)
        }
    }

    private fun noService() = ShellResult("", "Could not bind to shell service", -1)
    private fun error(e: Exception) = ShellResult("", e.message ?: "IPC error", -1)
}
