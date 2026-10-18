package com.namaz.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

object AlarmScheduler {
    const val ACTION_REMIND = "com.namaz.reminder.REMIND"
    const val EXTRA_INDEX = "prayer_index"
    const val EXTRA_EARLY = "is_early"
    const val EXTRA_TEST = "is_test"

    /** Cancel everything, then schedule all enabled prayers (daily repeating). */
    fun scheduleAll(c: Context) {
        cancelAll(c)
        if (!PrayerStore.isMasterOn(c)) return
        if (!canScheduleExact(c)) return
        for (i in 0..4) {
            if (PrayerStore.isEnabled(c, i)) schedulePrayer(c, i)
        }
    }

    /** Schedule the 30-min-early warning + the on-time reminder for one prayer. */
    fun schedulePrayer(c: Context, i: Int) {
        val h = PrayerStore.getHour(c, i)
        val m = PrayerStore.getMinute(c, i)
        val now = System.currentTimeMillis()

        val early = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, h)
            set(Calendar.MINUTE, m)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.MINUTE, -30)
        }
        if (early.timeInMillis <= now) early.add(Calendar.DAY_OF_YEAR, 1)

        val onTime = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, h)
            set(Calendar.MINUTE, m)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (onTime.timeInMillis <= now) onTime.add(Calendar.DAY_OF_YEAR, 1)

        setAlarm(c, i, true, early.timeInMillis)
        setAlarm(c, i, false, onTime.timeInMillis)
    }

    /** One-shot test: full reminder flow 10 seconds from now. */
    fun scheduleTest(c: Context) {
        val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(c, AlarmReceiver::class.java).apply {
            action = ACTION_REMIND
            putExtra(EXTRA_INDEX, 0)
            putExtra(EXTRA_EARLY, true)
            putExtra(EXTRA_TEST, true)
        }
        val pi = PendingIntent.getBroadcast(
            c, 999, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            am.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 10_000, pi
            )
        } catch (_: SecurityException) { }
    }

    private fun setAlarm(c: Context, i: Int, early: Boolean, at: Long) {
        val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(c, AlarmReceiver::class.java).apply {
            action = ACTION_REMIND
            putExtra(EXTRA_INDEX, i)
            putExtra(EXTRA_EARLY, early)
        }
        val pi = PendingIntent.getBroadcast(
            c, reqCode(i, early), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } catch (_: SecurityException) { }
    }

    fun cancelAll(c: Context) {
        val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        for (i in 0..4) {
            for (early in listOf(true, false)) {
                val intent = Intent(c, AlarmReceiver::class.java).apply {
                    action = ACTION_REMIND
                }
                val pi = PendingIntent.getBroadcast(
                    c, reqCode(i, early), intent,
                    PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
                )
                if (pi != null) {
                    am.cancel(pi)
                    pi.cancel()
                }
            }
        }
    }

    private fun reqCode(i: Int, early: Boolean) = i * 10 + if (early) 1 else 2

    fun canScheduleExact(c: Context): Boolean {
        val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return if (Build.VERSION.SDK_INT >= 31) am.canScheduleExactAlarms() else true
    }
}
