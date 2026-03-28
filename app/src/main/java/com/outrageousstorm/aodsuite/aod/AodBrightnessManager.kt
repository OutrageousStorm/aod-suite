package com.outrageousstorm.aodsuite.aod

import android.content.Context

/**
 * AodBrightnessManager — controls AOD minimum brightness level.
 * Uses WRITE_SECURE_SETTINGS (granted via Shizuku) to write doze_minimum_display_power_off_brightness.
 */
class AodBrightnessManager(private val context: Context) {

    companion object {
        private const val KEY_AOD_BRIGHTNESS = "doze_minimum_display_power_off_brightness"
        private const val KEY_DOZE_PULSE_ON_PICK_UP = "doze_pulse_on_pick_up"
        private const val KEY_DOZE_PULSE_ON_DOUBLE_TAP = "doze_pulse_on_double_tap"
        private const val KEY_DOZE_ALWAYS_ON = "doze_always_on"

        // Brightness range: 1 (dimmest) to 255 (max for AOD)
        const val BRIGHTNESS_MIN = 1
        const val BRIGHTNESS_MAX = 100   // AOD rarely needs above 100
        const val BRIGHTNESS_DEFAULT = 20
    }

    /** Set AOD display brightness (1–100 recommended) */
    fun setBrightness(level: Int): Boolean {
        val clamped = level.coerceIn(BRIGHTNESS_MIN, BRIGHTNESS_MAX)
        return try {
            android.provider.Settings.Secure.putInt(
                context.contentResolver,
                KEY_AOD_BRIGHTNESS,
                clamped
            )
        } catch (e: SecurityException) {
            false  // Need WRITE_SECURE_SETTINGS — grant via Shizuku Setup button
        }
    }

    fun getBrightness(): Int =
        android.provider.Settings.Secure.getInt(
            context.contentResolver,
            KEY_AOD_BRIGHTNESS,
            BRIGHTNESS_DEFAULT
        )

    /** Enable/disable tap-to-wake on AOD */
    fun setTapToWake(enabled: Boolean) {
        android.provider.Settings.Secure.putInt(
            context.contentResolver,
            KEY_DOZE_PULSE_ON_DOUBLE_TAP,
            if (enabled) 1 else 0
        )
    }

    /** Enable/disable lift-to-wake */
    fun setLiftToWake(enabled: Boolean) {
        android.provider.Settings.Secure.putInt(
            context.contentResolver,
            KEY_DOZE_PULSE_ON_PICK_UP,
            if (enabled) 1 else 0
        )
    }
}
