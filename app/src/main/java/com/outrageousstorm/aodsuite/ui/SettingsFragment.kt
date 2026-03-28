package com.outrageousstorm.aodsuite.ui

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import com.outrageousstorm.aodsuite.R

class SettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)
        
        // Listen for brightness changes
        findPreference<SeekBarPreference>("aod_brightness")?.setOnPreferenceChangeListener { _, newVal ->
            val brightness = (newVal as Int)
            // Apply via AodRepository
            true
        }
        
        // Schedule on/off times
        findPreference<TimePreference>("aod_start_time")?.let {
            it.setOnPreferenceChangeListener { _, _ -> true }
        }
        
        findPreference<TimePreference>("aod_end_time")?.let {
            it.setOnPreferenceChangeListener { _, _ -> true }
        }
    }
}
