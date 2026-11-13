package com.melad.jibran

import android.animation.ValueAnimator
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.net.URLEncoder
import java.util.Locale

class WakeWordService : Service() {
    companion object {
        const val ACTION_STOP = "com.melad.jibran.STOP_WAKE_WORD"
        const val EXTRA_COMMAND = "com.melad.jibran.VOICE_COMMAND"
        private const val CHANNEL_ID = "jibran_wake_word"
        private const val NOTIFICATION_ID = 7401
        private val LANGUAGES = arrayListOf(
            "ar-EG", "en-US", "es-ES", "fr-FR", "de-DE", "it-IT", "pt-BR",
            "ru-RU", "zh-CN", "ja-JP", "ko-KR", "hi-IN", "tr-TR", "id-ID"
        )
    }

    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var windowManager: WindowManager? = null
    private var bubble: View? = null
    private var orbView: ImageView? = null
    private var bubbleStatus: TextView? = null
    private var orbAnimator: ValueAnimator? = null
    private var tts: TextToSpeech? = null
    private var commandMode = false
    private var listening = false
    private var stopped = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale("ar", "EG")
                tts?.setSpeechRate(0.94f)
            }
        }
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
        listenAgain(200)
        return START_STICKY
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            listening = true
            if (commandMode) setBubbleStatus("أسمعك الآن")
        }

        override fun onBeginningOfSpeech() {
            if (commandMode) setBubbleStatus("أسمعك الآن")
        }

        override fun onRmsChanged(rmsdB: Float) {
            val level = ((rmsdB + 2f) / 14f).coerceIn(0f, 1f)
            val scale = 0.94f + level * 0.24f
            orbView?.scaleX = scale
            orbView?.scaleY = scale
            orbView?.alpha = 0.82f + level * 0.18f
        }

        override fun onBufferReceived(buffer: ByteArray?) { }

        override fun onEndOfSpeech() {
            listening = false
            if (commandMode) setBubbleStatus("بفهم كلامك…")
        }

        override fun onResults(results: Bundle?) {
            listening = false
            val spoken = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull().orEmpty()
            processRecognizedSpeech(spoken)
            listenAgain(220)
        }

        override fun onPartialResults(partialResults: Bundle?) { }
        override fun onEvent(eventType: Int, params: Bundle?) { }

        override fun onError(error: Int) {
            listening = false
            // Android's recognizer ends sessions after silence/no-match; re-arm quietly.
            listenAgain(if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) 900 else 350)
        }
    }

    private fun processRecognizedSpeech(spoken: String) {
        if (spoken.isBlank()) return
        if (commandMode) {
            commandMode = false
            hideBubble()
            if (tryHandleTorchCommand(spoken)) return
            launchCommandInApp(spoken)
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
                hideBubble()
                if (!tryHandleTorchCommand(remainder)) launchCommandInApp(remainder)
            } else {
                commandMode = true
                setBubbleStatus("أسمعك الآن")
            }
        }
    }

    private fun launchCommandInApp(command: String) {
        val open = Intent(this, MainActivity::class.java).apply {
            putExtra(EXTRA_COMMAND, command)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        startActivity(open)
    }

    private fun tryHandleTorchCommand(raw: String): Boolean {
        val command = normalize(raw)
        if (!listOf("كشاف", "فلاش", "flashlight", "torch").any { command.contains(it) }) return false
        val wantsOff = listOf(
            "اقفل", "اطفي", "اطفئ", "اغلق", "وقف", "يتقفل", "يقفل", "يطفي", "ينطفي",
            "turn off", "switch off", "disable", "off"
        ).any { command.contains(it) }
        val wantsOn = listOf("شغل", "افتح", "ولع", "turn on", "switch on", "enable", "on")
            .any { command.contains(it) }
        if (!wantsOff && !wantsOn) return false

        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            speak("إذن الكاميرا غير مفعّل. افتح جبران واسمح بالإذن أولاً.")
            return true
        }

        val delay = requestedDelayMillis(command)
        val enabled = wantsOn && !wantsOff
        if (delay != null) {
            speak("حاضر، ه" + (if (enabled) "شغّل" else "طفي") + " الكشاف بعد " + humanDelay(delay))
            handler.postDelayed({ setTorch(enabled) }, delay)
        } else {
            setTorch(enabled)
        }
        return true
    }

    private fun requestedDelayMillis(command: String): Long? {
        val arabic = Regex("بعد\\s*(?:(\\d+|واحد|واحدة|اتنين|اثنين|تلاتة|ثلاثة|اربعة|أربعة|خمسة|عشرة)\\s*)?(ثانية|ثواني|دقيقة|دقايق|دقائق|ساعة|ساعات)")
            .find(command)
        if (arabic != null) {
            val amount = numberValue(arabic.groupValues[1]) ?: 1L
            val multiplier = when (arabic.groupValues[2]) {
                "ثانية", "ثواني" -> 1_000L
                "دقيقة", "دقايق", "دقائق" -> 60_000L
                else -> 3_600_000L
            }
            return (amount * multiplier).coerceAtMost(24L * 60L * 60L * 1000L)
        }
        val english = Regex("in\\s+(?:(\\d+)\\s*)?(seconds?|minutes?|hours?)").find(command) ?: return null
        val amount = english.groupValues[1].toLongOrNull() ?: 1L
        val multiplier = when {
            english.groupValues[2].startsWith("second") -> 1_000L
            english.groupValues[2].startsWith("minute") -> 60_000L
            else -> 3_600_000L
        }
        return (amount * multiplier).coerceAtMost(24L * 60L * 60L * 1000L)
    }

    private fun numberValue(value: String): Long? = when (value) {
        "", "واحد", "واحدة" -> 1L
        "اتنين", "اثنين" -> 2L
        "تلاتة", "ثلاثة" -> 3L
        "اربعة", "أربعة" -> 4L
        "خمسة" -> 5L
        "عشرة" -> 10L
        else -> value.toLongOrNull()
    }

    private fun humanDelay(delayMs: Long): String {
        val seconds = delayMs / 1000L
        return when {
            seconds % 3600L == 0L -> (seconds / 3600L).toString() + " ساعة"
            seconds % 60L == 0L -> (seconds / 60L).toString() + " دقيقة"
            else -> seconds.toString() + " ثانية"
        }
    }

    private fun setTorch(enabled: Boolean) {
        try {
            val manager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = manager.cameraIdList.firstOrNull { id ->
                manager.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
            if (cameraId == null) {
                speak("الكشاف غير متاح على الجهاز")
                return
            }
            manager.setTorchMode(cameraId, enabled)
            speak(if (enabled) "تم تشغيل الكشاف" else "تم إيقاف الكشاف")
        } catch (_: Exception) {
            speak("لم أستطع التحكم في الكشاف الآن")
        }
    }

    private fun speak(message: String) {
        tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "jibran_service_reply")
    }

    private fun listenAgain(delay: Long) {
        if (stopped || !SpeechRecognizer.isRecognitionAvailable(this)) return
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({
            if (stopped || listening) return@postDelayed
            val speechIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                if (Build.VERSION.SDK_INT >= 34) {
                    putExtra("android.speech.extra.ENABLE_LANGUAGE_DETECTION", true)
                    putStringArrayListExtra("android.speech.extra.LANGUAGE_DETECTION_ALLOWED_LANGUAGES", ArrayList(LANGUAGES))
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ar-EG")
                } else {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                }
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 4)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, if (commandMode) 10000L else 8500L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, if (commandMode) 8500L else 7000L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, if (commandMode) 15000L else 5000L)
            }
            try {
                recognizer?.startListening(speechIntent)
            } catch (_: Exception) {
                listenAgain(900)
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
            contentDescription = "كرة جبران المتفاعلة"
        }
        orbView = orb
        container.addView(orb, LinearLayout.LayoutParams(dp(48), dp(48)))
        val status = TextView(this).apply {
            text = "أسمعك الآن"
            setTextColor(0xFFE9E1FF.toInt())
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        bubbleStatus = status
        container.addView(status, LinearLayout.LayoutParams(dp(66), dp(17)))
        container.setOnClickListener {
            commandMode = true
            setBubbleStatus("أسمعك الآن")
            listenAgain(100)
        }
        val overlayType = if (Build.VERSION.SDK_INT >= 26) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val params = WindowManager.LayoutParams(
            dp(78), dp(78), overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = dp(66)
        }
        try {
            windowManager?.addView(container, params)
            bubble = container
            orbAnimator = ValueAnimator.ofFloat(0f, 360f).apply {
                duration = 5200L
                repeatCount = ValueAnimator.INFINITE
                addUpdateListener { orbView?.rotation = it.animatedValue as Float }
                start()
            }
        } catch (_: Exception) {
            bubble = null
            orbView = null
        }
    }

    private fun setBubbleStatus(value: String) {
        bubbleStatus?.text = value
    }

    private fun hideBubble() {
        orbAnimator?.cancel()
        orbAnimator = null
        bubble?.let { view ->
            try { windowManager?.removeView(view) } catch (_: Exception) { }
        }
        bubble = null
        orbView = null
        bubbleStatus = null
    }

    private fun normalize(value: String): String = value.lowercase(Locale.ROOT)
        .replace(Regex("[ًٌٍَُِّْـ]"), "")
        .replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا')
        .replace('ى', 'ي').replace(Regex("\\s+"), " ").trim()

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                CHANNEL_ID, "استماع جبران", NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "إشعار ظاهر عند تفعيل جملة تنبيه جبران"
                setShowBadge(false)
            }
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val stopIntent = PendingIntent.getService(
            this, 7402, Intent(this, WakeWordService::class.java).setAction(ACTION_STOP),
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
        tts?.shutdown()
        tts = null
        if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE) else stopForeground(true)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
