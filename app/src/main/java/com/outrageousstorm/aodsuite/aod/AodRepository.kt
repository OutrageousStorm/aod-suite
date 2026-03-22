package com.outrageousstorm.aodsuite.aod

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
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

    // ─── AOD toggle ──────────────────────────────────────────────────────────

    suspend fun enableAod(enable: Boolean): ShellResult =
        ShizukuHelper.putSecure("doze_always_on", if (enable) "1" else "0")

    suspend fun getAodState(): Boolean =
        ShizukuHelper.getSecure("doze_always_on") == "1"

    // ─── Brightness ──────────────────────────────────────────────────────────

    suspend fun setMinBrightness(percent: Int): List<ShellResult> {
        val clamped = percent.coerceIn(0, 100)
        val floatVal = "%.4f".format(clamped / 100f)
        val intVal = (clamped * 255 / 100).toString()
        return listOf(
            ShizukuHelper.putSecure("screen_brightness_float", floatVal),
            ShizukuHelper.putSystem("screen_brightness", intVal),
            ShizukuHelper.exec("device_config put display_manager aod_min_brightness_float $floatVal"),
            ShizukuHelper.putSystem("aod_min_brightness", intVal)
        )
    }

    suspend fun getCurrentBrightness(): Int {
        val raw = ShizukuHelper.getSystem("screen_brightness")
        return raw.toIntOrNull()?.let { it * 100 / 255 } ?: -1
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
                FileOutputStream(outFile).use { out ->
                    blurred.compress(Bitmap.CompressFormat.JPEG, 90, out)
                }

                val pushResult = ShizukuHelper.exec("cp '${outFile.absolutePath}' /sdcard/aod_wallpaper.jpg")
                if (!pushResult.success) {
                    return@withContext Result.failure(Exception("Copy failed: ${pushResult.stderr}"))
                }

                // Apply as wallpaper
                listOf(
                    "cp /sdcard/aod_wallpaper.jpg /data/system/users/0/wallpaper",
                    "chmod 640 /data/system/users/0/wallpaper",
                    "am broadcast -a android.intent.action.WALLPAPER_CHANGED"
                ).forEach { ShizukuHelper.exec(it) }

                Result.success("Blurred wallpaper applied (radius: $blurRadius)")
            } catch (e: Exception) {
                Log.e(TAG, "applyBlurredWallpaper error", e)
                Result.failure(e)
            }
        }

    // ─── Misc tweaks ─────────────────────────────────────────────────────────

    suspend fun setAodTap(enabled: Boolean): ShellResult =
        ShizukuHelper.putSecure("doze_tap_gesture", if (enabled) "1" else "0")

    suspend fun setRaiseToWake(enabled: Boolean): ShellResult =
        ShizukuHelper.putSecure("doze_pick_up_gesture", if (enabled) "1" else "0")

    suspend fun setNightMode(enabled: Boolean, kelvin: Int = 3000): List<ShellResult> = listOf(
        ShizukuHelper.putSecure("night_display_activated", if (enabled) "1" else "0"),
        ShizukuHelper.putSecure("night_display_color_temperature", kelvin.toString())
    )

    suspend fun setAodTimeout(seconds: Int): ShellResult =
        ShizukuHelper.putSecure("doze_timeout", (seconds * 1000).toString())

    suspend fun forceBatteryIgnore(): ShellResult =
        ShizukuHelper.putGlobal("always_on_display_schedule", "0")

    suspend fun dumpAllSettings(): String {
        val secure = ShizukuHelper.exec("settings list secure | grep -E 'doze|aod|brightness'")
        val system = ShizukuHelper.exec("settings list system | grep -E 'doze|aod|brightness'")
        return "=== Secure ===\n${secure.output}\n=== System ===\n${system.output}"
    }

    // ─── Pure Kotlin Gaussian-stack blur ─────────────────────────────────────

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
        val wm = w - 1; val hm = h - 1; val wh = w * h; val div = radius + radius + 1
        val r = IntArray(wh); val g = IntArray(wh); val b = IntArray(wh)
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
                r[yi] = dv[rsum]; g[yi] = dv[gsum]; b[yi] = dv[bsum]
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
                stack[sir] = r[yi2]; stack[sir+1] = g[yi2]; stack[sir+2] = b[yi2]
                val rbs = radius + 1 - abs(i)
                rsum += r[yi2] * rbs; gsum += g[yi2] * rbs; bsum += b[yi2] * rbs
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
                stack[sir] = r[p2]; stack[sir+1] = g[p2]; stack[sir+2] = b[p2]
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
