package com.namaz.reminder

import android.content.Context

object PrayerStore {
    private const val PREFS = "namaz_prefs"

    val PRAYERS = listOf("Fajr", "Zohar", "Asr", "Maghrib", "Isha")
    val PRAYER_URDU = listOf("فجر", "ظہر", "عصر", "مغرب", "عشاء")

    // Default times: Fajr 5:10 AM, Zohar 1:30 PM, Asr 5:00 PM, Maghrib 6:45 PM, Isha 8:30 PM
    private val DEF_H = intArrayOf(5, 13, 17, 18, 20)
    private val DEF_M = intArrayOf(10, 30, 0, 45, 30)

    private fun prefs(c: Context) =
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getHour(c: Context, i: Int): Int = prefs(c).getInt("h$i", DEF_H[i])
    fun getMinute(c: Context, i: Int): Int = prefs(c).getInt("m$i", DEF_M[i])
    fun isEnabled(c: Context, i: Int): Boolean = prefs(c).getBoolean("e$i", true)
    fun isMasterOn(c: Context): Boolean = prefs(c).getBoolean("master", true)

    fun setTime(c: Context, i: Int, h: Int, m: Int) {
        prefs(c).edit().putInt("h$i", h).putInt("m$i", m).apply()
    }

    fun setEnabled(c: Context, i: Int, v: Boolean) {
        prefs(c).edit().putBoolean("e$i", v).apply()
    }

    fun setMasterOn(c: Context, v: Boolean) {
        prefs(c).edit().putBoolean("master", v).apply()
    }

    fun timeLabel(c: Context, i: Int): String {
        val h = getHour(c, i)
        val m = getMinute(c, i)
        val ampm = if (h < 12) "AM" else "PM"
        var hh = h % 12
        if (hh == 0) hh = 12
        return "%d:%02d %s".format(hh, m, ampm)
    }
}
