package com.jarvis.assistant.device

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.AlarmClock
import android.util.Log
import java.util.Calendar

class AlarmController(private val context: Context? = null) {

    /**
     * Set an alarm at a given hour (24h). Uses the system alarm clock app
     * via ACTION_SET_ALARM (no extra permission needed).
     */
    fun setAlarm(hour: Int, minute: Int = 0, label: String = "JARVIS Alarm"): Boolean {
        Log.i("AlarmController", "Setting alarm at $hour:$minute ($label)")
        return try {
            val ctx = context ?: return false
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                putExtra(AlarmClock.EXTRA_HOUR, hour.coerceIn(0, 23))
                putExtra(AlarmClock.EXTRA_MINUTES, minute.coerceIn(0, 59))
                putExtra(AlarmClock.EXTRA_MESSAGE, label)
                putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            }
            if (intent.resolveActivity(ctx.packageManager) != null) {
                ctx.startActivity(intent)
                true
            } else {
                fallbackAlarmViaAlarmManager(hour, minute, label)
            }
        } catch (e: Exception) {
            Log.e("AlarmController", "Failed to set alarm", e)
            false
        }
    }

    private fun fallbackAlarmViaAlarmManager(hour: Int, minute: Int, label: String): Boolean {
        val ctx = context ?: return false
        return try {
            val am = ctx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return false
            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
            }
            val pi = PendingIntent.getBroadcast(
                ctx, 0,
                Intent(ctx, AlarmController::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
            } else {
                am.setExact(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
            }
            true
        } catch (e: Exception) {
            Log.e("AlarmController", "Fallback alarm failed", e)
            false
        }
    }

    /**
     * Schedule a one-time reminder (delay minutes) that speaks via TTS when fired.
     * Uses AlarmManager with a broadcast receiver that posts to the foreground service.
     */
    fun setReminder(delayMinutes: Int, message: String): Boolean {
        Log.i("AlarmController", "Setting reminder in $delayMinutes min: '$message'")
        return try {
            val ctx = context ?: return false
            val am = ctx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return false
            val triggerAt = System.currentTimeMillis() + delayMinutes.coerceAtLeast(1) * 60_000L
            val intent = Intent(ctx, com.jarvis.assistant.services.ReminderReceiver::class.java).apply {
                putExtra("reminder_text", message)
            }
            val pi = PendingIntent.getBroadcast(
                ctx, (System.currentTimeMillis() and 0xFFFF).toInt(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            } else {
                am.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            }
            true
        } catch (e: Exception) {
            Log.e("AlarmController", "Failed to set reminder", e)
            false
        }
    }

    /**
     * Schedule a countdown timer. Fires a TTS announcement when complete.
     */
    fun setTimer(seconds: Int, label: String = "Timer"): Boolean {
        Log.i("AlarmController", "Setting timer for $seconds s")
        return try {
            val ctx = context ?: return false
            val am = ctx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return false
            val triggerAt = System.currentTimeMillis() + seconds.coerceAtLeast(1) * 1000L
            val intent = Intent(ctx, com.jarvis.assistant.services.TimerReceiver::class.java).apply {
                putExtra("timer_label", label)
                putExtra("timer_seconds", seconds)
            }
            val pi = PendingIntent.getBroadcast(
                ctx, (System.currentTimeMillis() and 0xFFFF).toInt() + 1,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            } else {
                am.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            }
            true
        } catch (e: Exception) {
            Log.e("AlarmController", "Failed to set timer", e)
            false
        }
    }
}
