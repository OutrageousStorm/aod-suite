package com.outrageousstorm.aodsuite.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.outrageousstorm.aodsuite.R
import com.outrageousstorm.aodsuite.aod.AodBrightnessManager
import com.outrageousstorm.aodsuite.aod.AodScheduler

/**
 * SettingsFragment — brightness slider, tap-to-wake toggle, schedule on/off pickers.
 */
class SettingsFragment : Fragment() {

    private val viewModel: MainViewModel by activityViewModels()
    private lateinit var brightnessManager: AodBrightnessManager
    private lateinit var scheduler: AodScheduler

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        brightnessManager = AodBrightnessManager(requireContext())
        scheduler = AodScheduler(requireContext())

        // Brightness slider
        val brightnessSlider = view.findViewById<SeekBar>(R.id.seekbar_brightness)
        val brightnessLabel  = view.findViewById<TextView>(R.id.tv_brightness_value)
        brightnessSlider?.let { sb ->
            sb.max = AodBrightnessManager.BRIGHTNESS_MAX
            sb.progress = brightnessManager.getBrightness()
            brightnessLabel?.text = "${sb.progress}"
            sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar, progress: Int, fromUser: Boolean) {
                    brightnessLabel?.text = "$progress"
                }
                override fun onStartTrackingTouch(s: SeekBar) {}
                override fun onStopTrackingTouch(s: SeekBar) {
                    brightnessManager.setBrightness(s.progress)
                    Toast.makeText(context, "AOD brightness: ${s.progress}", Toast.LENGTH_SHORT).show()
                }
            })
        }

        // Tap to wake toggle
        view.findViewById<Switch>(R.id.switch_tap_to_wake)?.setOnCheckedChangeListener { _, checked ->
            brightnessManager.setTapToWake(checked)
        }

        // Lift to wake toggle
        view.findViewById<Switch>(R.id.switch_lift_to_wake)?.setOnCheckedChangeListener { _, checked ->
            brightnessManager.setLiftToWake(checked)
        }

        // Schedule — simple TimePicker approach
        view.findViewById<Button>(R.id.btn_apply_schedule)?.setOnClickListener {
            val onHour   = view.findViewById<TimePicker>(R.id.timepicker_on)?.hour  ?: 22
            val onMin    = view.findViewById<TimePicker>(R.id.timepicker_on)?.minute ?: 0
            val offHour  = view.findViewById<TimePicker>(R.id.timepicker_off)?.hour  ?: 7
            val offMin   = view.findViewById<TimePicker>(R.id.timepicker_off)?.minute ?: 0
            scheduler.schedule(onHour, onMin, offHour, offMin)
            Toast.makeText(context,
                "Schedule set: ON ${onHour}:${onMin.toString().padStart(2,'0')} / OFF ${offHour}:${offMin.toString().padStart(2,'0')}",
                Toast.LENGTH_LONG).show()
        }

        view.findViewById<Button>(R.id.btn_clear_schedule)?.setOnClickListener {
            scheduler.cancel()
            Toast.makeText(context, "Schedule cleared", Toast.LENGTH_SHORT).show()
        }
    }
}
