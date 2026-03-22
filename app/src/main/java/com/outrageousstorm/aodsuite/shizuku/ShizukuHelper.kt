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
        get() = try { Shizuku.pingBinder() } catch (_: Exception) { false }

    val isGranted: Boolean
        get() = try {
            Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED
        } catch (_: Exception) { false }

    fun requestPermission(requestCode: Int = SHIZUKU_REQUEST_CODE) {
        try { Shizuku.requestPermission(requestCode) } catch (e: Exception) {
            Log.e(TAG, "requestPermission failed", e)
        }
    }

    fun unbind() {
        try {
            serviceConnection?.let { Shizuku.unbindUserService(userServiceArgs, it, true) }
        } catch (_: Exception) {}
        shellService = null
        serviceConnection = null
    }

    private val userServiceArgs by lazy {
        Shizuku.UserServiceArgs(
            ComponentName("com.outrageousstorm.aodsuite", ShellService::class.java.name)
        )
            .daemon(false)
            .processNameSuffix("shell_service")
            .debuggable(false)
            .version(4)
    }

    private suspend fun getService(): IShellService? {
        shellService?.let { return it }
        if (!isAvailable || !isGranted) return null

        return withTimeoutOrNull(8_000) {
            suspendCancellableCoroutine { cont ->
                val conn = object : ServiceConnection {
                    override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                        val svc = runCatching { IShellService.Stub.asInterface(binder) }.getOrNull()
                        shellService = svc
                        serviceConnection = this
                        if (cont.isActive) cont.resume(svc)
                    }
                    override fun onServiceDisconnected(name: ComponentName?) {
                        shellService = null
                    }
                }
                serviceConnection = conn
                runCatching { Shizuku.bindUserService(userServiceArgs, conn) }.onFailure { e ->
                    Log.e(TAG, "bindUserService failed", e)
                    if (cont.isActive) cont.resume(null)
                }
            }
        }
    }

    suspend fun exec(command: String): ShellResult = withContext(Dispatchers.IO) {
        if (!isAvailable) return@withContext err("Shizuku not running")
        if (!isGranted)   return@withContext err("Shizuku permission not granted")
        val svc = runCatching { getService() }.getOrNull()
            ?: return@withContext err("Could not bind shell service")
        runCatching {
            val raw = svc.exec(command)
            val parts = raw.split("\n", limit = 3)
            ShellResult(
                parts.getOrNull(1)?.removePrefix("OUT:") ?: "",
                parts.getOrNull(2)?.removePrefix("ERR:") ?: "",
                parts.getOrNull(0)?.removePrefix("EXIT:")?.toIntOrNull() ?: -1
            )
        }.getOrElse { e -> err(e.message ?: "IPC error") }
    }

    suspend fun grantSelfPermissions(packageName: String): ShellResult = withContext(Dispatchers.IO) {
        val r1 = exec("pm grant $packageName android.permission.WRITE_SECURE_SETTINGS")
        val r2 = exec("pm grant $packageName android.permission.WRITE_SETTINGS")
        Log.d(TAG, "grant WRITE_SECURE_SETTINGS: $r1")
        Log.d(TAG, "grant WRITE_SETTINGS: $r2")
        r1
    }

    private fun err(msg: String) = ShellResult("", msg, -1)
}
