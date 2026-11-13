package com.melad.jibran

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import java.util.Locale

class WakeWordService : Service() {
    companion object {
        const val ACTION_STOP = "com.melad.jibran.STOP_WAKE_WORD"
        const val EXTRA_COMMAND = "com.melad.jibran.VOICE_COMMAND"
        private const val CHANNEL_ID = "jibran_wake_word"
        private const val NOTIFICATION_ID = 7401
    }

    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var windowManager: WindowManager? = null
    private var bubble: View? = null
    private var commandMode = false
    private var listening = false
    private var stopped = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(this)
            recognizer?.setRecognitionListener(listener)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, buildNotification())
        stopped = false
        listenAgain(250)
        return START_STICKY
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) { listening = true }
        override fun onBeginningOfSpeech() { }
        override fun onRmsChanged(rmsdB: Float) { }
        override fun onBufferReceived(buffer: ByteArray?) { }
        override fun onEndOfSpeech() { listening = false }

        override fun onResults(results: Bundle?) {
            listening = false
            val spoken = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull().orEmpty()
            processRecognizedSpeech(spoken)
            listenAgain(450)
        }

        override fun onPartialResults(partialResults: Bundle?) { }
        override fun onEvent(eventType: Int, params: Bundle?) { }

        override fun onError(error: Int) {
            listening = false
            // Speech timeout/no-match is normal while waiting for a wake phrase.
            // Re-arm quietly instead of interrupting the user with repeated apologies.
            listenAgain(if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) 1200 else 700)
        }
    }

    private fun processRecognizedSpeech(spoken: String) {
        if (spoken.isBlank()) return
        if (commandMode) {
            commandMode = false
            hideBubble()
            val open = Intent(this, MainActivity::class.java).apply {
                putExtra(EXTRA_COMMAND, spoken)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            startActivity(open)
            return
        }

        val phrase = getSharedPreferences("jibran", MODE_PRIVATE)
            .getString("wake_phrase", "يا جبران").orEmpty()
        val normalizedSpeech = normalize(spoken)
        val normalizedPhrase = normalize(phrase)
        if (normalizedPhrase.isNotBlank() && normalizedSpeech.contains(normalizedPhrase)) {
            val index = normalizedSpeech.indexOf(normalizedPhrase)
            val remainder = normalizedSpeech.substring(index + normalizedPhrase.length).trim()
            showBubble()
            if (remainder.isNotBlank()) {
                commandMode = false
                val open = Intent(this, MainActivity::class.java).apply {
                    putExtra(EXTRA_COMMAND, remainder)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                startActivity(open)
                hideBubble()
            } else {
                commandMode = true
            }
        }
    }

    private fun listenAgain(delay: Long) {
        if (stopped || !SpeechRecognizer.isRecognitionAvailable(this)) return
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({
            if (stopped || listening) return@postDelayed
            val speechIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-EG")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, if (commandMode) 9000L else 5000L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, if (commandMode) 7500L else 3500L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, if (commandMode) 15000L else 3000L)
            }
            try {
                recognizer?.startListening(speechIntent)
            } catch (_: Exception) {
                listenAgain(1200)
            }
        }, delay)
    }

    private fun showBubble() {
        if (bubble != null || !android.provider.Settings.canDrawOverlays(this)) return
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(7), dp(6), dp(7), dp(5))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(40).toFloat()
                setColor(0xEE100D20.toInt())
                setStroke(dp(1), 0xAA8B5CF6.toInt())
            }
        }
        val orb = ImageView(this).apply {
            setImageResource(R.drawable.ic_jibran_orb)
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "جبران يستمع إليك"
        }
        container.addView(orb, LinearLayout.LayoutParams(dp(48), dp(48)))
        val status = TextView(this).apply {
            text = "أستمع إليك"
            setTextColor(0xFFE9E1FF.toInt())
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        container.addView(status, LinearLayout.LayoutParams(dp(66), dp(17)))
        container.setOnClickListener {
            commandMode = true
            listenAgain(100)
        }
        val overlayType = if (Build.VERSION.SDK_INT >= 26) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val params = WindowManager.LayoutParams(
            dp(78),
            dp(78),
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = dp(66)
        }
        try {
            windowManager?.addView(container, params)
            bubble = container
        } catch (_: Exception) {
            bubble = null
        }
    }

    private fun hideBubble() {
        bubble?.let { view ->
            try { windowManager?.removeView(view) } catch (_: Exception) { }
        }
        bubble = null
    }

    private fun normalize(value: String): String = value.lowercase(Locale("ar", "EG"))
        .replace(Regex("[ًٌٍَُِّْـ]"), "")
        .replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا')
        .replace('ى', 'ي').replace(Regex("\\s+"), " ").trim()

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "استماع جبران",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "إشعار ظاهر عند تفعيل جملة تنبيه جبران"
                setShowBadge(false)
            }
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val stopIntent = PendingIntent.getService(
            this,
            7402,
            Intent(this, WakeWordService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("جبران يستمع لجملة التنبيه")
            .setContentText("الميكروفون يعمل. اضغط إيقاف لإنهاء الاستماع.")
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(android.R.drawable.ic_media_pause, "إيقاف الاستماع", stopIntent)
            .build()
    }

    override fun onDestroy() {
        stopped = true
        handler.removeCallbacksAndMessages(null)
        hideBubble()
        recognizer?.cancel()
        recognizer?.destroy()
        recognizer = null
        if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE)
        else stopForeground(true)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
