package com.outrageousstorm.aodsuite.aod

import android.content.ContentResolver
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

private const val TAG = "AodRepo"

class AodRepository(private val cacheDir: File, private val cr: ContentResolver) {

    // ── Direct writes (work after pm grant WRITE_SECURE_SETTINGS) ────────────

    private fun putSecure(key: String, value: String) =
        runCatching { Settings.Secure.putString(cr, key, value) }.getOrDefault(false)

    private fun putSystem(key: String, value: String) =
        runCatching { Settings.System.putString(cr, key, value) }.getOrDefault(false)

    private fun getSecure(key: String) =
        runCatching { Settings.Secure.getString(cr, key) ?: "" }.getOrDefault("")

    private fun getSystem(key: String) =
        runCatching { Settings.System.getString(cr, key) ?: "" }.getOrDefault("")

    // ── One-time permission grant ─────────────────────────────────────────────

    suspend fun grantPermissions(pkg: String) = ShizukuHelper.grantSelf(pkg)

    // ── AOD ──────────────────────────────────────────────────────────────────

    suspend fun getAodState() = withContext(Dispatchers.IO) { getSecure("doze_always_on") == "1" }

    suspend fun enableAod(enable: Boolean): ShellResult = withContext(Dispatchers.IO) {
        val ok = putSecure("doze_always_on", if (enable) "1" else "0")
        if (ok) ShellResult("ok", "", 0) else ShellResult("", "WRITE_SECURE_SETTINGS not granted — tap Setup first", 1)
    }

    // ── Brightness ───────────────────────────────────────────────────────────

    suspend fun getCurrentBrightness() = withContext(Dispatchers.IO) {
        getSystem("screen_brightness").toIntOrNull()?.let { it * 100 / 255 } ?: -1
    }

    suspend fun setMinBrightness(percent: Int): ShellResult = withContext(Dispatchers.IO) {
        val clamped  = percent.coerceIn(0, 100)
        val intVal   = (clamped * 255 / 100).toString()
        val floatVal = "%.4f".format(clamped / 100f)
        val ok1 = putSystem("screen_brightness", intVal)
        val ok2 = putSecure("screen_brightness_float", floatVal)
        // device_config needs shell — use Shizuku.newProcess
        ShizukuHelper.exec("device_config put display_manager aod_min_brightness_float $floatVal")
        if (ok1 || ok2) ShellResult("$percent%", "", 0)
        else ShellResult("", "WRITE_SECURE_SETTINGS not granted — tap Setup first", 1)
    }

    // ── Gestures ─────────────────────────────────────────────────────────────

    suspend fun setAodTap(enabled: Boolean): ShellResult = withContext(Dispatchers.IO) {
        val ok = putSecure("doze_tap_gesture", if (enabled) "1" else "0")
        if (ok) ShellResult("ok", "", 0) else ShellResult("", "Permission not granted", 1)
    }

    suspend fun setRaiseToWake(enabled: Boolean): ShellResult = withContext(Dispatchers.IO) {
        val ok = putSecure("doze_pick_up_gesture", if (enabled) "1" else "0")
        if (ok) ShellResult("ok", "", 0) else ShellResult("", "Permission not granted", 1)
    }

    // ── Night mode ───────────────────────────────────────────────────────────

    suspend fun setNightMode(enabled: Boolean, kelvin: Int = 3000): ShellResult = withContext(Dispatchers.IO) {
        putSecure("night_display_activated", if (enabled) "1" else "0")
        val ok = putSecure("night_display_color_temperature", kelvin.toString())
        if (ok) ShellResult("ok", "", 0) else ShellResult("", "Permission not granted", 1)
    }

    // ── Timeout ──────────────────────────────────────────────────────────────

    suspend fun setAodTimeout(seconds: Int): ShellResult = withContext(Dispatchers.IO) {
        val ok = putSecure("doze_timeout", (seconds * 1000).toString())
        if (ok) ShellResult("ok", "", 0) else ShellResult("", "Permission not granted", 1)
    }

    // ── Wallpaper ────────────────────────────────────────────────────────────

    suspend fun applyBlurredWallpaper(imageUri: Uri, blurRadius: Int): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val bmp = cr.openInputStream(imageUri)?.use { BitmapFactory.decodeStream(it) }
                    ?: error("Cannot open image")
                val blurred = softwareBlur(bmp, blurRadius)
                val outFile = File(cacheDir, "aod_bg.jpg")
                FileOutputStream(outFile).use { blurred.compress(Bitmap.CompressFormat.JPEG, 90, it) }
                ShizukuHelper.exec("cp '${outFile.absolutePath}' /sdcard/aod_bg.jpg")
                ShizukuHelper.exec("cp /sdcard/aod_bg.jpg /data/system/users/0/wallpaper_lock")
                ShizukuHelper.exec("chmod 640 /data/system/users/0/wallpaper_lock")
                ShizukuHelper.exec("am broadcast -a android.intent.action.WALLPAPER_CHANGED")
                "Applied (blur radius $blurRadius)"
            }
        }

    // ── Debug ────────────────────────────────────────────────────────────────

    suspend fun dumpAllSettings(): String {
        val s = ShizukuHelper.exec("settings list secure | grep -iE 'doze|aod|brightness'")
        val y = ShizukuHelper.exec("settings list system | grep -iE 'doze|aod|brightness'")
        return "=Secure=\n${s.output}\n=System=\n${y.output}"
    }

    // ── Stack blur (pure Kotlin) ──────────────────────────────────────────────

    private fun softwareBlur(src: Bitmap, radius: Int): Bitmap {
        val r = radius.coerceIn(1, 25)
        val w = src.width; val h = src.height
        val out = src.copy(Bitmap.Config.ARGB_8888, true)
        val pix = IntArray(w * h)
        out.getPixels(pix, 0, w, 0, 0, w, h)
        stackBlur(pix, w, h, r)
        out.setPixels(pix, 0, w, 0, 0, w, h)
        return out
    }

    private fun stackBlur(pix: IntArray, w: Int, h: Int, r: Int) {
        val wm = w-1; val hm = h-1; val div = r+r+1
        val vmin = IntArray(max(w,h))
        val ds = ((div+1) shr 1)*((div+1) shr 1)
        val dv = IntArray(256*ds) { it/ds }
        val stk = IntArray(div*3)
        var yw = 0; var yi = 0
        for (y in 0 until h) {
            var ri=0;var gi=0;var bi=0;var ro=0;var go=0;var bo=0;var rs=0;var gs=0;var bs=0
            for (i in -r..r) {
                val p=pix[yi+min(wm,max(i,0))]; val s=((i+r)*3)
                stk[s]=(p shr 16)and 255; stk[s+1]=(p shr 8)and 255; stk[s+2]=p and 255
                val rb=r+1-abs(i)
                rs+=stk[s]*rb; gs+=stk[s+1]*rb; bs+=stk[s+2]*rb
                if(i>0){ri+=stk[s];gi+=stk[s+1];bi+=stk[s+2]}else{ro+=stk[s];go+=stk[s+1];bo+=stk[s+2]}
            }
            var sp=r
            for (x in 0 until w) {
                pix[yi]=(0xFF shl 24)or(dv[rs] shl 16)or(dv[gs] shl 8)or dv[bs]
                rs-=ro; gs-=go; bs-=bo
                val ss=(sp-r+div)%div; val s=ss*3
                ro-=stk[s]; go-=stk[s+1]; bo-=stk[s+2]
                if(y==0) vmin[x]=min(x+r+1,wm)
                val p=pix[yw+vmin[x]]
                stk[s]=(p shr 16)and 255; stk[s+1]=(p shr 8)and 255; stk[s+2]=p and 255
                ri+=stk[s]; gi+=stk[s+1]; bi+=stk[s+2]
                rs+=ri; gs+=gi; bs+=bi
                sp=(sp+1)%div; val s2=sp*3
                ro+=stk[s2]; go+=stk[s2+1]; bo+=stk[s2+2]
                ri-=stk[s2]; gi-=stk[s2+1]; bi-=stk[s2+2]
                yi++
            }
            yw+=w
        }
        for (x in 0 until w) {
            var ri=0;var gi=0;var bi=0;var ro=0;var go=0;var bo=0;var rs=0;var gs=0;var bs=0
            var yp=-r*w
            for (i in -r..r) {
                val yi2=max(0,yp)+x; val s=(i+r)*3
                stk[s]=((pix[yi2] shr 16)and 255); stk[s+1]=((pix[yi2] shr 8)and 255); stk[s+2]=(pix[yi2] and 255)
                val rb=r+1-abs(i)
                rs+=stk[s]*rb; gs+=stk[s+1]*rb; bs+=stk[s+2]*rb
                if(i>0){ri+=stk[s];gi+=stk[s+1];bi+=stk[s+2]}else{ro+=stk[s];go+=stk[s+1];bo+=stk[s+2]}
                if(i<hm) yp+=w
            }
            yi=x; var sp=r
            for (y in 0 until h) {
                pix[yi]=(0xFF shl 24)or(dv[rs] shl 16)or(dv[gs] shl 8)or dv[bs]
                rs-=ro; gs-=go; bs-=bo
                val ss=(sp-r+div)%div; val s=ss*3
                ro-=stk[s]; go-=stk[s+1]; bo-=stk[s+2]
                if(x==0) vmin[y]=min(y+r+1,hm)*w
                val p2=x+vmin[y]
                stk[s]=((pix[p2] shr 16)and 255); stk[s+1]=((pix[p2] shr 8)and 255); stk[s+2]=(pix[p2] and 255)
                ri+=stk[s]; gi+=stk[s+1]; bi+=stk[s+2]
                rs+=ri; gs+=gi; bs+=bi
                sp=(sp+1)%div; val s2=sp*3
                ro+=stk[s2]; go+=stk[s2+1]; bo+=stk[s2+2]
                ri-=stk[s2]; gi-=stk[s2+1]; bi-=stk[s2+2]
                yi+=w
            }
        }
    }
}
