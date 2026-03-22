package com.outrageousstorm.aodsuite.aod

/**
 * All ADB / settings-write commands for AOD customization.
 * These require WRITE_SECURE_SETTINGS — granted via Shizuku.
 */
object AodCommands {

    // ─── Always On Display toggle ─────────────────────────────────────────────

    /** Enable AOD (doze always-on) */
    const val ENABLE_AOD =
        "settings put secure doze_always_on 1"

    /** Disable AOD */
    const val DISABLE_AOD =
        "settings put secure doze_always_on 0"

    /** Read current AOD state (returns "0" or "1") */
    const val GET_AOD_STATE =
        "settings get secure doze_always_on"

    // ─── Minimum brightness ───────────────────────────────────────────────────

    /**
     * Set the minimum AOD brightness level.
     * The screen_brightness_float range is 0.0–1.0.
     * On most devices AOD uses the dim brightness table — we patch
     * config_screenBrightnessSettingMinimumFloat via DeviceConfig / settings.
     *
     * For devices that respect it, we also set the legacy int scale (0-255).
     * [value] 0–100 (percent; we map to float)
     */
    fun setMinBrightness(percent: Int): List<String> {
        val clamped = percent.coerceIn(0, 100)
        val floatVal = "%.4f".format(clamped / 100f)
        val intVal   = (clamped * 255 / 100)
        return listOf(
            // Secure setting – respected on AOSP/Pixel
            "settings put secure screen_brightness_float $floatVal",
            // System setting fallback
            "settings put system screen_brightness $intVal",
            // DeviceConfig AOD-specific (Android 12+)
            "device_config put display_manager aod_min_brightness_float $floatVal",
            // Some Samsung/OEM devices
            "settings put system aod_min_brightness $intVal"
        )
    }

    /** Read current brightness setting */
    const val GET_BRIGHTNESS =
        "settings get system screen_brightness"

    const val GET_BRIGHTNESS_FLOAT =
        "settings get secure screen_brightness_float"

    // ─── AOD Wallpaper / blurred background ───────────────────────────────────

    /**
     * Push a pre-blurred image to the device and set it as the lock-screen/AOD wallpaper.
     * [sourcePath] = local file path on device (after adb push)
     */
    fun setAodWallpaper(sourcePath: String): List<String> = listOf(
        // Copy to a protected system location accessible to WallpaperManager
        "cp '$sourcePath' /data/system/users/0/wallpaper",
        "chmod 640 /data/system/users/0/wallpaper",
        // Trigger wallpaper refresh
        "am broadcast -a android.intent.action.WALLPAPER_CHANGED",
        // Some ROMs store lock-screen wallpaper separately
        "cp '$sourcePath' /data/system/users/0/wallpaper_lock",
        "chmod 640 /data/system/users/0/wallpaper_lock",
    )

    /** Push a local file from app's cache to /sdcard first */
    fun pushToSdcard(localPath: String, remoteName: String = "aod_wallpaper.jpg"): String =
        "cp '$localPath' /sdcard/$remoteName"

    // ─── Doze / AOD appearance tweaks ────────────────────────────────────────

    /** Force AOD to show at all charge levels (disable "charging only" restriction) */
    const val FORCE_AOD_ALL_BATTERY =
        "settings put global always_on_display_schedule 0"

    /** Enable AOD on tap (pulse on pick-up) */
    const val ENABLE_AOD_TAP =
        "settings put secure doze_tap_gesture 1"

    /** Enable raise-to-wake */
    const val ENABLE_RAISE_TO_WAKE =
        "settings put secure doze_pick_up_gesture 1"

    /** Set AOD timeout (seconds; 0 = never). Some ROMs respect this */
    fun setAodTimeout(seconds: Int) =
        "settings put secure doze_timeout ${seconds * 1000}"

    // ─── Display colour / temperature on AOD ─────────────────────────────────

    /** Apply night-mode-style warm tint on AOD (colour temperature in Kelvin → matrix via libdisplay) */
    fun setNightMode(enabled: Boolean) =
        "settings put secure night_display_activated ${if (enabled) 1 else 0}"

    fun setNightModeTemp(kelvin: Int) =
        "settings put secure night_display_color_temperature $kelvin"

    // ─── Read-back helpers ────────────────────────────────────────────────────

    const val GET_ALL_AOD_SETTINGS = """
sh -c "echo '=== Secure ===' && \
settings list secure | grep -E 'doze|aod|brightness' && \
echo '=== System ===' && \
settings list system | grep -E 'doze|aod|brightness' && \
echo '=== Global ===' && \
settings list global | grep -E 'doze|aod|brightness'"
"""
}
