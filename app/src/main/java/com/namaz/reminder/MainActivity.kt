package com.namaz.reminder

import android.Manifest
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.util.Calendar
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var nextLabel: TextView
    private lateinit var masterSwitch: Switch
    private lateinit var exactWarn: TextView
    private val timeButtons = mutableListOf<Button>()
    private val toggles = mutableListOf<Switch>()
    private val handler = Handler(Looper.getMainLooper())
    private var voiceTts: TextToSpeech? = null

    private val notifPerm =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val tick = object : Runnable {
        override fun run() {
            updateNext()
            handler.postDelayed(this, 30_000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        // Make sure alarms exist for the saved settings.
        AlarmScheduler.scheduleAll(this)
    }

    override fun onResume() {
        super.onResume()
        // Re-schedule: user may have just granted exact-alarm permission in Settings.
        AlarmScheduler.scheduleAll(this)
        refreshAll()
        handler.post(tick)
    }

    override fun onPause() {
        handler.removeCallbacks(tick)
        super.onPause()
    }

    override fun onDestroy() {
        try { voiceTts?.shutdown() } catch (_: Exception) {}
        super.onDestroy()
    }

    // ---------- UI ----------

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
        }

        val title = TextView(this).apply {
            text = "🕌 Namaz Reminder Pro"
            textSize = 24f
            gravity = Gravity.CENTER
        }
        root.addView(title)

        nextLabel = TextView(this).apply {
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(0, 24, 0, 24)
        }
        root.addView(nextLabel)

        masterSwitch = Switch(this).apply { text = "Reminders ON / OFF" }
        root.addView(masterSwitch)

        exactWarn = TextView(this).apply {
            textSize = 14f
            setPadding(0, 16, 0, 16)
            visibility = View.GONE
        }
        root.addView(exactWarn)
        exactWarn.setOnClickListener {
            try {
                startActivity(
                    Intent(
                        Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        Uri.parse("package:$packageName")
                    )
                )
            } catch (_: Exception) { }
        }

        for (i in 0..4) {
            root.addView(prayerRow(i))
        }

        val voiceBtn = Button(this).apply { text = "🔊 Awaz test karein" }
        root.addView(voiceBtn)
        voiceBtn.setOnClickListener { testVoice() }

        val alarmBtn = Button(this).apply { text = "⏰ 10 second me reminder test karein" }
        root.addView(alarmBtn)
        alarmBtn.setOnClickListener {
            AlarmScheduler.scheduleTest(this)
            Toast.makeText(this, "10 second me reminder aayega", Toast.LENGTH_SHORT).show()
        }

        val hint = TextView(this).apply {
            text = "Har namaz se 30 minute pehle Urdu awaz me reminder aayega, aur namaz ke time par bhi."
            textSize = 13f
            setPadding(0, 24, 0, 0)
        }
        root.addView(hint)

        val scroll = ScrollView(this).apply { addView(root) }
        setContentView(scroll)
    }

    private fun prayerRow(i: Int): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 16, 0, 16)
        }
        val name = TextView(this).apply {
            text = PrayerStore.PRAYERS[i]
            textSize = 18f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val timeBtn = Button(this).apply { text = PrayerStore.timeLabel(this@MainActivity, i) }
        val sw = Switch(this)
        row.addView(name)
        row.addView(timeBtn)
        row.addView(sw)
        timeButtons.add(timeBtn)
        toggles.add(sw)

        timeBtn.setOnClickListener {
            TimePickerDialog(
                this,
                { _, h, m ->
                    PrayerStore.setTime(this, i, h, m)
                    AlarmScheduler.schedulePrayer(this, i)
                    refreshAll()
                },
                PrayerStore.getHour(this, i),
                PrayerStore.getMinute(this, i),
                false
            ).show()
        }
        return row
    }

    // ---------- refresh ----------

    private fun refreshAll() {
        masterSwitch.setOnCheckedChangeListener(null)
        masterSwitch.isChecked = PrayerStore.isMasterOn(this)
        masterSwitch.setOnCheckedChangeListener { _, on ->
            PrayerStore.setMasterOn(this, on)
            AlarmScheduler.scheduleAll(this)
            refreshAll()
        }
        for (i in 0..4) {
            timeButtons[i].text = PrayerStore.timeLabel(this, i)
            toggles[i].setOnCheckedChangeListener(null)
            toggles[i].isChecked = PrayerStore.isEnabled(this, i)
            val idx = i
            toggles[i].setOnCheckedChangeListener { _, on ->
                PrayerStore.setEnabled(this, idx, on)
                AlarmScheduler.scheduleAll(this)
                refreshAll()
            }
        }
        val needExact = Build.VERSION.SDK_INT >= 31 && !AlarmScheduler.canScheduleExact(this)
        exactWarn.visibility = if (needExact) View.VISIBLE else View.GONE
        if (needExact) {
            exactWarn.text = "⚠️ Exact alarm ki ijazat band hai — yahan tap karke ON karein, warna waqt par reminder nahi aayega."
        }
        updateNext()
    }

    private fun updateNext() {
        if (!::nextLabel.isInitialized) return
        val now = System.currentTimeMillis()
        var best = Long.MAX_VALUE
        var bestI = -1
        if (PrayerStore.isMasterOn(this)) {
            for (i in 0..4) {
                if (!PrayerStore.isEnabled(this, i)) continue
                val c = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, PrayerStore.getHour(this@MainActivity, i))
                    set(Calendar.MINUTE, PrayerStore.getMinute(this@MainActivity, i))
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                if (c.timeInMillis <= now) c.add(Calendar.DAY_OF_YEAR, 1)
                if (c.timeInMillis < best) {
                    best = c.timeInMillis
                    bestI = i
                }
            }
        }
        nextLabel.text = if (bestI < 0) {
            "Reminders off hain"
        } else {
            val diff = best - now
            val hh = diff / 3_600_000
            val mm = (diff % 3_600_000) / 60_000
            "Agli namaz: ${PrayerStore.PRAYERS[bestI]} (${PrayerStore.timeLabel(this, bestI)}) — $hh ghantay $mm min baad"
        }
    }

    // ---------- voice test ----------

    private fun testVoice() {
        try { voiceTts?.shutdown() } catch (_: Exception) {}
        voiceTts = TextToSpeech(this) { s ->
            if (s == TextToSpeech.SUCCESS) {
                val t = voiceTts ?: return@TextToSpeech
                t.language = Locale("ur", "PK")
                t.setSpeechRate(0.92f)
                t.speak(
                    "فجر کی نماز کا وقت ہونے والا ہے۔ اپنا کام سمیٹ لو",
                    TextToSpeech.QUEUE_FLUSH, null, "voice_test"
                )
            }
        }
        Toast.makeText(this, "Awaz suno...", Toast.LENGTH_SHORT).show()
    }
}
