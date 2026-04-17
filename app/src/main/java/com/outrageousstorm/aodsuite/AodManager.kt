package com.outrageousstorm.aodsuite

import android.content.ContentResolver
import android.provider.Settings

class AodManager(private val resolver: ContentResolver) {
    
    fun getMinBrightness(): Int = 
        Settings.Secure.getInt(resolver, "aod_minimum_brightness", 30)
    
    fun setMinBrightness(level: Int) {
        Settings.Secure.putInt(resolver, "aod_minimum_brightness", level.coerceIn(0, 255))
    }
    
    fun isEnabled(): Boolean = 
        Settings.Secure.getInt(resolver, "aod_enabled", 1) == 1
    
    fun setEnabled(enabled: Boolean) {
        Settings.Secure.putInt(resolver, "aod_enabled", if (enabled) 1 else 0)
    }
    
    fun getTimeout(): Int = 
        Settings.Secure.getInt(resolver, "aod_timeout_seconds", 60)
    
    fun setTimeout(seconds: Int) {
        Settings.Secure.putInt(resolver, "aod_timeout_seconds", seconds.coerceIn(0, 600))
    }
}
