package com.outrageousstorm.aodsuite.aod

import android.content.ContentResolver
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import com.outrageousstorm.aodsuite.shizuku.ShellResult
import com.outrageousstorm.aodsuite.shizuku.ShizukuHelper
import jp.wasabeef.blurry.internal.BlurFactor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

private const val TAG = "AodRepository"

class AodRepository(private val cacheDir: File, private val contentResolver: ContentResolver) {

    // ─── AOD toggle ──────────────────────────────────────────────────────────

    suspend fun enableAod(enable: Boolean): ShellResult =
        ShizukuHelper.exec(if (enable) AodCommands.ENABLE_AOD else AodCommands.DISABLE_AOD)

    suspend fun getAodState(): Boolean {
        val r = ShizukuHelper.exec(AodCommands.GET_AOD_STATE)
        return r.stdout.trim() == "1"
    }

    // ─── Brightness ──────────────────────────────────────────────────────────

    suspend fun setMinBrightness(percent: Int): List<ShellResult> {
        val cmds = AodCommands.setMinBrightness(percent)
        return cmds.map { ShizukuHelper.exec(it) }
    }

    suspend fun getCurrentBrightness(): Int {
        val r = ShizukuHelper.exec(AodCommands.GET_BRIGHTNESS)
        return r.stdout.toIntOrNull()?.let { it * 100 / 255 } ?: -1
    }

    // ─── Wallpaper / blur ────────────────────────────────────────────────────

    /**
     * Takes a [Uri] from the image picker, blurs it at [blurRadius] (1–25),
     * saves to cache, then pushes via Shizuku shell.
     */
    suspend fun applyBlurredWallpaper(imageUri: Uri, blurRadius: Int): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                // 1. Decode bitmap
                val bmp: Bitmap = contentResolver.openInputStream(imageUri)?.use {
                    android.graphics.BitmapFactory.decodeStream(it)
                } ?: return@withContext Result.failure(Exception("Cannot open image"))

                // 2. Blur in software (RenderScript not available on all ABIs in ci)
                val blurred = softwareBlur(bmp, blurRadius)

                // 3. Save JPEG to app cache
                val outFile = File(cacheDir, "aod_wallpaper_blurred.jpg")
                FileOutputStream(outFile).use { out ->
                    blurred.compress(Bitmap.CompressFormat.JPEG, 90, out)
                }
                Log.d(TAG, "Blurred image saved: ${outFile.absolutePath}")

                // 4. Push to /sdcard via Shizuku shell cp
                val pushResult = ShizukuHelper.exec(
                    AodCommands.pushToSdcard(outFile.absolutePath)
                )
                if (!pushResult.success) {
                    return@withContext Result.failure(Exception("Push failed: ${pushResult.stderr}"))
                }

                // 5. Apply as wallpaper
                val applyResults = AodCommands.setAodWallpaper("/sdcard/aod_wallpaper.jpg")
                    .map { ShizukuHelper.exec(it) }

                val failed = applyResults.filter { !it.success }
                if (failed.isNotEmpty()) {
                    Log.w(TAG, "Some wallpaper commands failed: ${failed.map { it.stderr }}")
                }

                Result.success("Blurred wallpaper applied (${blurRadius}px radius)")
            } catch (e: Exception) {
                Log.e(TAG, "applyBlurredWallpaper error", e)
                Result.failure(e)
            }
        }

    // ─── Misc tweaks ─────────────────────────────────────────────────────────

    suspend fun setAodTap(enabled: Boolean) =
        ShizukuHelper.exec(if (enabled) AodCommands.ENABLE_AOD_TAP else "settings put secure doze_tap_gesture 0")

    suspend fun setRaiseToWake(enabled: Boolean) =
        ShizukuHelper.exec(if (enabled) AodCommands.ENABLE_RAISE_TO_WAKE else "settings put secure doze_pick_up_gesture 0")

    suspend fun setNightMode(enabled: Boolean, kelvin: Int = 3000): List<ShellResult> =
        listOf(
            ShizukuHelper.exec(AodCommands.setNightMode(enabled)),
            ShizukuHelper.exec(AodCommands.setNightModeTemp(kelvin))
        )

    suspend fun setAodTimeout(seconds: Int) =
        ShizukuHelper.exec(AodCommands.setAodTimeout(seconds))

    suspend fun forceBatteryIgnore() =
        ShizukuHelper.exec(AodCommands.FORCE_AOD_ALL_BATTERY)

    suspend fun dumpAllSettings(): String =
        ShizukuHelper.exec(AodCommands.GET_ALL_AOD_SETTINGS).output

    // ─── Software Gaussian blur (pure Kotlin, no RenderScript) ───────────────

    private fun softwareBlur(src: Bitmap, radius: Int): Bitmap {
        val r = radius.coerceIn(1, 25)
        val width  = src.width
        val height = src.height
        val pixels = IntArray(width * height)
        src.getPixels(pixels, 0, width, 0, 0, width, height)

        fun blur(px: IntArray, w: Int, h: Int) {
            val wm = w - 1; val hm = h - 1
            val wh = w * h; val div = r + r + 1
            val ri = IntArray(wh); val gi = IntArray(wh); val bi = IntArray(wh)
            var rsum: Int; var gsum: Int; var bsum: Int
            var x: Int; var y: Int; var i: Int; var p: Int
            var yp: Int; var yi: Int; var yw: Int
            val vmin = IntArray(maxOf(w, h))
            var divsum = (div + 1) shr 1; divsum *= divsum
            val dv = IntArray(256 * divsum)
            for (idx in dv.indices) dv[idx] = idx / divsum
            var stackpointer: Int
            var stackstart: Int
            val stack = IntArray(div * 3)
            var sir: Int
            var rbs: Int
            val r1 = r + 1
            var routsum: Int; var goutsum: Int; var boutsum: Int
            var rinsum: Int; var ginsum: Int; var binsum: Int
            yw = 0; yi = 0
            for (yIdx in 0 until h) {
                rinsum = 0; ginsum = 0; binsum = 0
                routsum = 0; goutsum = 0; boutsum = 0
                rsum = 0; gsum = 0; bsum = 0
                for (jj in -r..r) {
                    p = px[yi + minOf(wm, maxOf(jj, 0))]
                    sir = jj + r; stack[sir * 3] = (p and 0xff0000) shr 16
                    stack[sir * 3 + 1] = (p and 0x00ff00) shr 8
                    stack[sir * 3 + 2] = p and 0x0000ff
                    rbs = r1 - Math.abs(jj)
                    rsum += stack[sir * 3] * rbs; gsum += stack[sir * 3 + 1] * rbs; bsum += stack[sir * 3 + 2] * rbs
                    if (jj > 0) { rinsum += stack[sir * 3]; ginsum += stack[sir * 3 + 1]; binsum += stack[sir * 3 + 2] }
                    else { routsum += stack[sir * 3]; goutsum += stack[sir * 3 + 1]; boutsum += stack[sir * 3 + 2] }
                }
                stackpointer = r
                for (xIdx in 0 until w) {
                    ri[yi] = dv[rsum]; gi[yi] = dv[gsum]; bi[yi] = dv[bsum]
                    rsum -= routsum; gsum -= goutsum; bsum -= boutsum
                    stackstart = stackpointer - r + div
                    sir = (stackstart % div) * 3
                    routsum -= stack[sir]; goutsum -= stack[sir + 1]; boutsum -= stack[sir + 2]
                    if (yIdx == 0) vmin[xIdx] = minOf(xIdx + r + 1, wm)
                    p = px[yw + vmin[xIdx]]
                    stack[sir] = (p and 0xff0000) shr 16; stack[sir + 1] = (p and 0x00ff00) shr 8; stack[sir + 2] = p and 0x0000ff
                    rinsum += stack[sir]; ginsum += stack[sir + 1]; binsum += stack[sir + 2]
                    rsum += rinsum; gsum += ginsum; bsum += binsum
                    stackpointer = (stackpointer + 1) % div
                    sir = stackpointer * 3
                    routsum += stack[sir]; goutsum += stack[sir + 1]; boutsum += stack[sir + 2]
                    rinsum -= stack[sir]; ginsum -= stack[sir + 1]; binsum -= stack[sir + 2]
                    yi++
                }
                yw += w
            }
            for (xIdx in 0 until w) {
                rinsum = 0; ginsum = 0; binsum = 0
                routsum = 0; goutsum = 0; boutsum = 0
                rsum = 0; gsum = 0; bsum = 0
                yp = -r * w
                for (jj in -r..r) {
                    yi = maxOf(0, yp) + xIdx
                    sir = (jj + r) * 3
                    stack[sir] = ri[yi]; stack[sir + 1] = gi[yi]; stack[sir + 2] = bi[yi]
                    rbs = r1 - Math.abs(jj)
                    rsum += ri[yi] * rbs; gsum += gi[yi] * rbs; bsum += bi[yi] * rbs
                    if (jj > 0) { rinsum += stack[sir]; ginsum += stack[sir + 1]; binsum += stack[sir + 2] }
                    else { routsum += stack[sir]; goutsum += stack[sir + 1]; boutsum += stack[sir + 2] }
                    if (jj < hm) yp += w
                }
                yi = xIdx; stackpointer = r
                for (yIdx in 0 until h) {
                    px[yi] = -0x1000000 or (dv[rsum] shl 16) or (dv[gsum] shl 8) or dv[bsum]
                    rsum -= routsum; gsum -= goutsum; bsum -= boutsum
                    stackstart = stackpointer - r + div
                    sir = (stackstart % div) * 3
                    routsum -= stack[sir]; goutsum -= stack[sir + 1]; boutsum -= stack[sir + 2]
                    if (xIdx == 0) vmin[yIdx] = minOf(yIdx + r1, hm) * w
                    p = xIdx + vmin[yIdx]
                    stack[sir] = ri[p]; stack[sir + 1] = gi[p]; stack[sir + 2] = bi[p]
                    rinsum += stack[sir]; ginsum += stack[sir + 1]; binsum += stack[sir + 2]
                    rsum += rinsum; gsum += ginsum; bsum += binsum
                    stackpointer = (stackpointer + 1) % div; sir = stackpointer * 3
                    routsum += stack[sir]; goutsum += stack[sir + 1]; boutsum += stack[sir + 2]
                    rinsum -= stack[sir]; ginsum -= stack[sir + 1]; binsum -= stack[sir + 2]
                    yi += w
                }
            }
        }

        val out = src.copy(Bitmap.Config.ARGB_8888, true)
        val px = IntArray(width * height)
        out.getPixels(px, 0, width, 0, 0, width, height)
        blur(px, width, height)
        out.setPixels(px, 0, width, 0, 0, width, height)
        return out
    }
}
