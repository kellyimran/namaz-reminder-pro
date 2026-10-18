package com.namaz.reminder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

class ReminderService : Service() {

    private var tts: TextToSpeech? = null
    private val channelId = "namaz_reminder_ch"

    private val earlyCores = listOf(
        "اپنا کام سمیٹ لو",
        "وضو کر لو اور تیار ہو جاؤ",
        "کوئی بہانہ نہیں چلے گا",
        "کیا پتا یہ تمہاری آخری نماز ہو",
        "دنیا کے کام رک جائیں گے، نماز کا وقت نہیں رکے گا"
    )

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val i = intent?.getIntExtra(AlarmScheduler.EXTRA_INDEX, 0) ?: 0
        val early = intent?.getBooleanExtra(AlarmScheduler.EXTRA_EARLY, true) ?: true
        val (title, spoken) = buildMessage(i, early)
        val notif = buildNotification(title, spoken)
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE)
        } else {
            startForeground(1, notif)
        }
        speak(spoken) { stopSelf() }
        // Safety net: never run longer than 90 seconds.
        Handler(Looper.getMainLooper()).postDelayed({ stopSelf() }, 90_000)
        return START_NOT_STICKY
    }

    private fun buildMessage(i: Int, early: Boolean): Pair<String, String> {
        val urdu = PrayerStore.PRAYER_URDU[i.coerceIn(0, 4)]
        return if (early) {
            val title = "$urdu کی نماز کا وقت ہونے والا ہے"
            title to "$title۔ ${earlyCores.random()}"
        } else {
            val title = "$urdu کی نماز کا وقت ہو گیا ہے"
            title to title
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val ch = NotificationChannel(
                channelId, "Namaz Reminders", NotificationManager.IMPORTANCE_HIGH
            )
            ch.description = "Namaz time reminders with Urdu voice"
            nm.createNotificationChannel(ch)
        }
    }

    @Suppress("DEPRECATION")
    private fun buildNotification(title: String, text: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, channelId)
        else Notification.Builder(this)
        return b.setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
    }

    private fun speak(text: String, done: () -> Unit) {
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val t = tts ?: run { done(); return@TextToSpeech }
                var r = t.setLanguage(Locale("ur", "PK"))
                if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
                    r = t.setLanguage(Locale("ur"))
                }
                if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
                    t.language = Locale.getDefault()
                }
                t.setSpeechRate(0.92f)
                t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) { done() }
                    override fun onError(utteranceId: String?) { done() }
                    override fun onError(utteranceId: String?, errorCode: Int) { done() }
                })
                t.speak(text, TextToSpeech.QUEUE_FLUSH, null, "namaz_reminder")
            } else {
                done()
            }
        }
    }

    override fun onDestroy() {
        try { tts?.stop() } catch (_: Exception) {}
        try { tts?.shutdown() } catch (_: Exception) {}
        tts = null
        super.onDestroy()
    }
}
