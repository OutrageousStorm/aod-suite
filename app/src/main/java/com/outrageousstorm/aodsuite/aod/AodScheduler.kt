package com.outrageousstorm.aodsuite.aod

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.BroadcastReceiver
import java.util.Calendar

/**
 * AodScheduler — schedule AOD on/off times using AlarmManager.
 * Broadcasts ACTION_AOD_ON / ACTION_AOD_OFF at user-configured times.
 */
class AodScheduler(private val context: Context) {

    companion object {
        const val ACTION_AOD_ON  = "com.outrageousstorm.aodsuite.AOD_ON"
        const val ACTION_AOD_OFF = "com.outrageousstorm.aodsuite.AOD_OFF"
        private const val REQUEST_ON  = 101
        private const val REQUEST_OFF = 102
    }

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /**
     * Schedule AOD to turn on at [onHour]:[onMinute] and off at [offHour]:[offMinute] daily.
     */
    fun schedule(onHour: Int, onMinute: Int, offHour: Int, offMinute: Int) {
        scheduleAlarm(ACTION_AOD_ON,  onHour,  onMinute,  REQUEST_ON)
        scheduleAlarm(ACTION_AOD_OFF, offHour, offMinute, REQUEST_OFF)
    }

    fun cancel() {
        cancelAlarm(ACTION_AOD_ON,  REQUEST_ON)
        cancelAlarm(ACTION_AOD_OFF, REQUEST_OFF)
    }

    private fun scheduleAlarm(action: String, hour: Int, minute: Int, requestCode: Int) {
        val intent = Intent(action).setPackage(context.packageName)
        val pi = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            if (before(Calendar.getInstance())) add(Calendar.DAY_OF_YEAR, 1)
        }
        alarmManager.setRepeating(
            AlarmManager.RTC_WAKEUP,
            cal.timeInMillis,
            AlarmManager.INTERVAL_DAY,
            pi
        )
    }

    private fun cancelAlarm(action: String, requestCode: Int) {
        val intent = Intent(action).setPackage(context.packageName)
        val pi = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        ) ?: return
        alarmManager.cancel(pi)
        pi.cancel()
    }
}

/** Receives scheduled AOD alarms and applies the setting */
class AodScheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val repo = AodRepository(context)
        when (intent.action) {
            AodScheduler.ACTION_AOD_ON  -> repo.setAodEnabled(true)
            AodScheduler.ACTION_AOD_OFF -> repo.setAodEnabled(false)
        }
    }
}
