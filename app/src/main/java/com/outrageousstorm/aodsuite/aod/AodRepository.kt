package com.outrageousstorm.aodsuite.aod

import android.content.ContentResolver
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.Settings
import android.util.Log
import com.outrageousstorm.aodsuite.shizuku.ShellResult
import com.outrageousstorm.aodsuite.shizuku.ShizukuHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

private const val TAG = "AodRepository"

class AodRepository(private val cacheDir: File, private val contentResolver: ContentResolver) {

    // ─── Direct settings write (app needs WRITE_SECURE_SETTINGS granted) ─────

    private fun putSecureDirect(key: String, value: String): Boolean = try {
        Settings.Secure.putString(contentResolver, key, value)
    } catch (e: SecurityException) {
        Log.e(TAG, "putSecureDirect failed — WRITE_SECURE_SETTINGS not granted yet: $e")
        false
    }

    private fun putSystemDirect(key: String, value: String): Boolean = try {
        Settings.System.putString(contentResolver, key, value)
    } catch (e: SecurityException) {
        Log.e(TAG, "putSystemDirect failed: $e")
        false
    }

    private fun getSecureDirect(key: String): String =
        Settings.Secure.getString(contentResolver, key) ?: ""

    private fun getSystemDirect(key: String): String =
        Settings.System.getString(contentResolver, key) ?: ""

    // ─── AOD toggle ──────────────────────────────────────────────────────────

    suspend fun enableAod(enable: Boolean): ShellResult = withContext(Dispatchers.IO) {
        val value = if (enable) "1" else "0"
        val ok = putSecureDirect("doze_always_on", value)
        if (ok) ShellResult(value, "", 0)
        else ShellResult("", "Failed — run Setup (grant permissions) first", 1)
    }

    suspend fun getAodState(): Boolean = withContext(Dispatchers.IO) {
        getSecureDirect("doze_always_on") == "1"
    }

    // ─── Brightness ──────────────────────────────────────────────────────────

    suspend fun setMinBrightness(percent: Int): ShellResult = withContext(Dispatchers.IO) {
        val clamped = percent.coerceIn(0, 100)
        val floatVal = "%.4f".format(clamped / 100f)
        val intVal = (clamped * 255 / 100).toString()

        var ok = putSecureDirect("screen_brightness_float", floatVal)
        ok = putSystemDirect("screen_brightness", intVal) || ok
        // DeviceConfig needs shell — use Shizuku exec for this one
        ShizukuHelper.exec("device_config put display_manager aod_min_brightness_float $floatVal")

        if (ok) ShellResult("$percent%", "", 0)
        else ShellResult("", "Failed — run Setup (grant permissions) first", 1)
    }

    suspend fun getCurrentBrightness(): Int = withContext(Dispatchers.IO) {
        val raw = getSystemDirect("screen_brightness")
        raw.toIntOrNull()?.let { it * 100 / 255 } ?: -1
    }

    // ─── One-time permission grant via Shizuku ────────────────────────────────

    suspend fun grantPermissions(packageName: String): ShellResult =
        ShizukuHelper.grantSelfPermissions(packageName)

    // ─── Misc tweaks ─────────────────────────────────────────────────────────

    suspend fun setAodTap(enabled: Boolean): ShellResult = withContext(Dispatchers.IO) {
        val ok = putSecureDirect("doze_tap_gesture", if (enabled) "1" else "0")
        if (ok) ShellResult("ok", "", 0) else ShellResult("", "Permission not granted", 1)
    }

    suspend fun setRaiseToWake(enabled: Boolean): ShellResult = withContext(Dispatchers.IO) {
        val ok = putSecureDirect("doze_pick_up_gesture", if (enabled) "1" else "0")
        if (ok) ShellResult("ok", "", 0) else ShellResult("", "Permission not granted", 1)
    }

    suspend fun setNightMode(enabled: Boolean, kelvin: Int = 3000): ShellResult = withContext(Dispatchers.IO) {
        putSecureDirect("night_display_activated", if (enabled) "1" else "0")
        val ok = putSecureDirect("night_display_color_temperature", kelvin.toString())
        if (ok) ShellResult("ok", "", 0) else ShellResult("", "Permission not granted", 1)
    }

    suspend fun setAodTimeout(seconds: Int): ShellResult = withContext(Dispatchers.IO) {
        val ok = putSecureDirect("doze_timeout", (seconds * 1000).toString())
        if (ok) ShellResult("ok", "", 0) else ShellResult("", "Permission not granted", 1)
    }

    // ─── Wallpaper / blur ────────────────────────────────────────────────────

    suspend fun applyBlurredWallpaper(imageUri: Uri, blurRadius: Int): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val bmp: Bitmap = contentResolver.openInputStream(imageUri)?.use {
                    BitmapFactory.decodeStream(it)
                } ?: return@withContext Result.failure(Exception("Cannot open image"))
                val blurred = softwareBlur(bmp, blurRadius)
                val outFile = File(cacheDir, "aod_wallpaper_blurred.jpg")
                FileOutputStream(outFile).use { blurred.compress(Bitmap.CompressFormat.JPEG, 90, it) }
                ShizukuHelper.exec("cp '${outFile.absolutePath}' /sdcard/aod_wallpaper.jpg")
                ShizukuHelper.exec("cp /sdcard/aod_wallpaper.jpg /data/system/users/0/wallpaper")
                ShizukuHelper.exec("chmod 640 /data/system/users/0/wallpaper")
                ShizukuHelper.exec("am broadcast -a android.intent.action.WALLPAPER_CHANGED")
                Result.success("Applied (radius $blurRadius)")
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun dumpAllSettings(): String {
        val secure = ShizukuHelper.exec("settings list secure | grep -E 'doze|aod|brightness'")
        val system  = ShizukuHelper.exec("settings list system | grep -E 'doze|aod|brightness'")
        return "=== Secure ===\n${secure.output}\n=== System ===\n${system.output}"
    }

    // ─── Blur ────────────────────────────────────────────────────────────────

    private fun softwareBlur(src: Bitmap, radius: Int): Bitmap {
        val r = radius.coerceIn(1, 25)
        val out = src.copy(Bitmap.Config.ARGB_8888, true)
        val pixels = IntArray(out.width * out.height)
        out.getPixels(pixels, 0, out.width, 0, 0, out.width, out.height)
        stackBlur(pixels, out.width, out.height, r)
        out.setPixels(pixels, 0, out.width, 0, 0, out.width, out.height)
        return out
    }

    @Suppress("NAME_SHADOWING")
    private fun stackBlur(pix: IntArray, w: Int, h: Int, radius: Int) {
        val wm = w - 1; val hm = h - 1; val div = radius + radius + 1
        val vmin = IntArray(max(w, h))
        val divsum = ((div + 1) shr 1) * ((div + 1) shr 1)
        val dv = IntArray(256 * divsum) { it / divsum }
        var yw = 0; var yi = 0
        val stack = IntArray(div * 3)
        for (y in 0 until h) {
            var rinsum = 0; var ginsum = 0; var binsum = 0
            var routsum = 0; var goutsum = 0; var boutsum = 0
            var rsum = 0; var gsum = 0; var bsum = 0
            for (i in -radius..radius) {
                val p = pix[yi + min(wm, max(i, 0))]; val sir = (i + radius) * 3
                stack[sir] = (p and 0xff0000) shr 16; stack[sir+1] = (p and 0x00ff00) shr 8; stack[sir+2] = p and 0x0000ff
                val rbs = radius + 1 - abs(i)
                rsum += stack[sir] * rbs; gsum += stack[sir+1] * rbs; bsum += stack[sir+2] * rbs
                if (i > 0) { rinsum += stack[sir]; ginsum += stack[sir+1]; binsum += stack[sir+2] }
                else { routsum += stack[sir]; goutsum += stack[sir+1]; boutsum += stack[sir+2] }
            }
            var stackpointer = radius
            for (x in 0 until w) {
                pix[yi] = -0x1000000
                rsum -= routsum; gsum -= goutsum; bsum -= boutsum
                val stackstart = (stackpointer - radius + div) % div; val sir = stackstart * 3
                routsum -= stack[sir]; goutsum -= stack[sir+1]; boutsum -= stack[sir+2]
                if (y == 0) vmin[x] = min(x + radius + 1, wm)
                val p = pix[yw + vmin[x]]
                stack[sir] = (p and 0xff0000) shr 16; stack[sir+1] = (p and 0x00ff00) shr 8; stack[sir+2] = p and 0x0000ff
                rinsum += stack[sir]; ginsum += stack[sir+1]; binsum += stack[sir+2]
                rsum += rinsum; gsum += ginsum; bsum += binsum
                stackpointer = (stackpointer + 1) % div
                val sir2 = stackpointer * 3
                routsum += stack[sir2]; goutsum += stack[sir2+1]; boutsum += stack[sir2+2]
                rinsum -= stack[sir2]; ginsum -= stack[sir2+1]; binsum -= stack[sir2+2]
                yi++
            }
            yw += w
        }
        for (x in 0 until w) {
            var rinsum = 0; var ginsum = 0; var binsum = 0
            var routsum = 0; var goutsum = 0; var boutsum = 0
            var rsum = 0; var gsum = 0; var bsum = 0
            var yp = -radius * w
            for (i in -radius..radius) {
                val yi2 = max(0, yp) + x; val sir = (i + radius) * 3
                stack[sir] = 0; stack[sir+1] = 0; stack[sir+2] = 0
                val rbs = radius + 1 - abs(i)
                if (i > 0) { rinsum += stack[sir]; ginsum += stack[sir+1]; binsum += stack[sir+2] }
                else { routsum += stack[sir]; goutsum += stack[sir+1]; boutsum += stack[sir+2] }
                if (i < hm) yp += w
            }
            yi = x; var stackpointer = radius
            for (y in 0 until h) {
                pix[yi] = -0x1000000 or (dv[rsum] shl 16) or (dv[gsum] shl 8) or dv[bsum]
                rsum -= routsum; gsum -= goutsum; bsum -= boutsum
                val stackstart = (stackpointer - radius + div) % div; val sir = stackstart * 3
                routsum -= stack[sir]; goutsum -= stack[sir+1]; boutsum -= stack[sir+2]
                if (x == 0) vmin[y] = min(y + radius + 1, hm) * w
                val p2 = x + vmin[y]
                stack[sir] = 0; stack[sir+1] = 0; stack[sir+2] = 0
                rinsum += stack[sir]; ginsum += stack[sir+1]; binsum += stack[sir+2]
                rsum += rinsum; gsum += ginsum; bsum += binsum
                stackpointer = (stackpointer + 1) % div
                val sir2 = stackpointer * 3
                routsum += stack[sir2]; goutsum += stack[sir2+1]; boutsum += stack[sir2+2]
                rinsum -= stack[sir2]; ginsum -= stack[sir2+1]; binsum -= stack[sir2+2]
                yi += w
            }
        }
    }
}
