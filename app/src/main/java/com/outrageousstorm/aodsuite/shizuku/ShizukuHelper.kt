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

data class ShellResult(val stdout: String, val stderr: String, val exitCode: Int) {
    val success get() = exitCode == 0
    val output  get() = if (stdout.isNotBlank()) stdout else stderr
}

object ShizukuHelper {

    @Volatile private var svc: IShellService? = null
    @Volatile private var conn: ServiceConnection? = null

    val isAvailable get() = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
    val isGranted   get() = runCatching {
        Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    fun requestPermission() = runCatching { Shizuku.requestPermission(1001) }

    private val serviceArgs by lazy {
        Shizuku.UserServiceArgs(ComponentName("com.outrageousstorm.aodsuite", ShellService::class.java.name))
            .daemon(false)
            .processNameSuffix("shell")
            .debuggable(false)
            .version(1)   // pinned — never change this
    }

    private suspend fun getService(): IShellService? {
        svc?.let { return it }
        if (!isAvailable || !isGranted) return null
        return withTimeoutOrNull(10_000L) {
            suspendCancellableCoroutine { cont ->
                val c = object : ServiceConnection {
                    override fun onServiceConnected(n: ComponentName?, b: IBinder?) {
                        svc = runCatching { IShellService.Stub.asInterface(b) }.getOrNull()
                        conn = this
                        if (cont.isActive) cont.resume(svc)
                    }
                    override fun onServiceDisconnected(n: ComponentName?) { svc = null }
                }
                conn = c
                runCatching { Shizuku.bindUserService(serviceArgs, c) }.onFailure {
                    Log.e(TAG, "bind failed", it)
                    if (cont.isActive) cont.resume(null)
                }
            }
        }
    }

    suspend fun exec(command: String): ShellResult = withContext(Dispatchers.IO) {
        if (!isAvailable) return@withContext err("Shizuku not running")
        if (!isGranted)   return@withContext err("Shizuku not granted")
        val s = runCatching { getService() }.getOrNull() ?: return@withContext err("Service bind failed")
        runCatching {
            val raw = s.exec(command)
            val exit = raw.substringAfter("EXIT:").substringBefore("|").toIntOrNull() ?: -1
            val out  = raw.substringAfter("OUT:").substringBefore("|ERR:")
            val er   = raw.substringAfter("|ERR:")
            ShellResult(out, er, exit)
        }.getOrElse { e -> err(e.message ?: "IPC error") }
    }

    suspend fun grantSelf(pkg: String): ShellResult {
        val r = exec("pm grant $pkg android.permission.WRITE_SECURE_SETTINGS")
        Log.d(TAG, "grantSelf: $r")
        return r
    }

    private fun err(m: String) = ShellResult("", m, -1)
}
