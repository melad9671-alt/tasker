package com.melad.jibran

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.hardware.camera2.CameraManager
import android.os.Bundle
import android.provider.AlarmClock
import android.provider.Settings
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.SettingsVoice
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.util.Locale

private val Night = Color(0xFF070812)
private val Violet = Color(0xFF8B5CF6)
private val Cyan = Color(0xFF36D9F5)
private val Muted = Color(0xFF9698B5)

class MainActivity : ComponentActivity() {
    private var tts: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var lastIntent: String = ""
    private var torchEnabled = false
    private var voiceIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val engine = tts
                engine?.language = Locale("ar", "EG")
                val saved = getSharedPreferences("jibran", MODE_PRIVATE).getString("voice_name", null)
                val savedVoice = engine?.voices?.firstOrNull { it.name == saved }
                if (savedVoice != null) engine.voice = savedVoice
            }
        }
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        }
        setContent {
            MaterialTheme {
                JibranHome(
                    onSpeak = { speak(it) },
                    onListen = { startListening() },
                    onCommand = { handleCommand(it) },
                    onCycleVoice = { cycleVoice() }
                )
            }
        }
    }

    private fun cycleVoice() {
        val engine = tts ?: return
        val voices = engine.voices?.filter { it.locale.language == "ar" }?.toList().orEmpty()
        if (voices.isEmpty()) {
            speak("لا توجد أصوات عربية إضافية مثبتة على الجهاز. يمكنك تثبيت صوت عربي من إعدادات تحويل النص إلى كلام.")
            return
        }
        voiceIndex = (voiceIndex + 1) % voices.size
        engine.voice = voices[voiceIndex]
        getSharedPreferences("jibran", MODE_PRIVATE).edit().putString("voice_name", voices[voiceIndex].name).apply()
        speak("تم اختيار الصوت " + (voiceIndex + 1) + " من " + voices.size)
    }

    private fun speak(message: String) {
        tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "jibran_reply")
    }

    private fun startListening() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "اسمح لجبران باستخدام الميكروفون أولاً", Toast.LENGTH_LONG).show()
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 41)
            return
        }
        val recognizer = speechRecognizer ?: run {
            speak("التعرف على الصوت غير متاح على هذا الجهاز")
            return
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-EG")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "قول لجبران عايز تعمل إيه")
        }
        recognizer.setRecognitionListener(object : android.speech.RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) { runOnUiThread { speak("معلش، ما سمعتش الأمر بوضوح") } }
            override fun onResults(results: Bundle?) {
                val phrase = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                runOnUiThread { if (phrase != null) handleCommand(phrase) else speak("ممكن تعيد الأمر؟") }
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        recognizer.startListening(intent)
    }

    private fun handleCommand(raw: String) {
        val command = raw.lowercase(Locale("ar", "EG")).trim()
        val followUp = command in listOf("اقفله", "اطفيه", "قفله", "شغله", "افتحه", "زوده", "عليه", "قلله", "وطيه")
        val resolved = if (followUp && lastIntent == "torch") {
            when (command) {
                "اقفله", "اطفيه", "قفله" -> "اقفل الكشاف"
                "شغله", "افتحه" -> "شغل الكشاف"
                else -> command
            }
        } else command
        when {
            resolved.contains("اقفل الكشاف") || resolved.contains("اطفي الكشاف") || resolved.contains("وقف الكشاف") -> {
                setTorch(false)
                lastIntent = "torch"
            }
            resolved.contains("كشاف") || resolved.contains("فلاش") -> {
                val shouldTurnOn = !(resolved.contains("اقفل") || resolved.contains("اطفي") || resolved.contains("وقف"))
                setTorch(shouldTurnOn)
                lastIntent = "torch"
            }
            resolved.contains("واي فاي") || resolved.contains("wifi") || resolved.contains("الانترنت") -> {
                startActivity(Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY))
                speak("فتحت لك إعدادات الاتصال")
            }
            resolved.contains("منبه") || resolved.contains("منبّه") -> {
                val digits = Regex("\\d{1,2}").find(resolved)?.value?.toIntOrNull()
                if (digits != null && digits in 0..23) {
                    startActivity(Intent(AlarmClock.ACTION_SET_ALARM)
                        .putExtra(AlarmClock.EXTRA_HOUR, digits)
                        .putExtra(AlarmClock.EXTRA_MINUTES, 0)
                        .putExtra(AlarmClock.EXTRA_MESSAGE, "منبه من جبران"))
                    speak("فتحت إعداد المنبه للساعة $digits")
                } else speak("قل لي الساعة التي تريد ضبط المنبه عليها")
            }
            resolved.contains("يوتيوب") -> {
                val query = raw.substringAfter("يوتيوب", "").replace("وابحث عن", "").replace("ابحث عن", "").trim()
                val url = if (query.isBlank()) "https://www.youtube.com" else "https://www.youtube.com/results?search_query=" + java.net.URLEncoder.encode(query, "UTF-8")
                startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)))
                speak(if (query.isBlank()) "فتحت يوتيوب" else "فتحت نتائج البحث على يوتيوب")
            }
            resolved.contains("اوديو ماك") || resolved.contains("أوديو ماك") || resolved.contains("audiomack") -> {
                val query = raw.substringAfter("ماك", "").trim().ifBlank { raw.substringAfter("audiomack", "").trim() }
                val url = if (query.isBlank()) "https://audiomack.com" else "https://audiomack.com/search?q=" + java.net.URLEncoder.encode(query, "UTF-8")
                startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)))
                speak(if (query.isBlank()) "أفتح لك أوديو ماك" else "أفتح لك نتائج البحث عن $query في أوديو ماك")
            }
            resolved.contains("الصوت") -> {
                val number = Regex("\\d{1,3}").find(resolved)?.value?.toIntOrNull()
                val audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                if (number != null) {
                    val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                    audio.setStreamVolume(AudioManager.STREAM_MUSIC, (max * number.coerceIn(0,100) / 100f).toInt(), 0)
                    speak("تم ضبط مستوى الصوت")
                } else speak("قل لي مستوى الصوت الذي تريده من صفر إلى مئة")
            }
            resolved.contains("افتح") && (resolved.contains("الإعدادات") || resolved.contains("الاعدادات")) -> {
                startActivity(Intent(Settings.ACTION_SETTINGS)); speak("فتحت الإعدادات")
            }
            resolved.contains("درجة الحرارة") || resolved.contains("الطقس") || resolved.contains("الجو") -> {
                val query = if (resolved.contains("بكرة") || resolved.contains("غدا")) "طقس غدا" else "الطقس الآن"
                startActivity(Intent(Intent.ACTION_WEB_SEARCH).putExtra("query", query))
                speak("سأفتح لك أحدث بيانات الطقس. لم أربط جبران بعد بمصدر طقس مباشر، لذلك لن أخمن درجة الحرارة.")
            }
            resolved.startsWith("افتح ") -> {
                val name = resolved.removePrefix("افتح ").trim()
                val packageName = when {
                    name.contains("واتساب") -> "com.whatsapp"
                    name.contains("تيليجرام") -> "org.telegram.messenger"
                    name.contains("فيسبوك") -> "com.facebook.katana"
                    name.contains("انستجرام") || name.contains("إنستجرام") -> "com.instagram.android"
                    name.contains("سبوتيفاي") -> "com.spotify.music"
                    name.contains("أوديو ماك") || name.contains("اوديو ماك") -> "com.audiomack"
                    else -> null
                }
                val launch = packageName?.let { packageManager.getLaunchIntentForPackage(it) }
                if (launch != null) {
                    startActivity(launch); speak("فتحت $name")
                } else {
                    speak("لم أجد تطبيق $name مثبتاً، أو أن النظام لا يسمح بفتحه مباشرة")
                }
            }
            resolved.contains("ابحث") || resolved.contains("ما معنى") || resolved.contains("من هو") || resolved.contains("ما هي") || resolved.contains("ليه") || resolved.contains("لماذا") -> {
                startActivity(Intent(Intent.ACTION_WEB_SEARCH).putExtra("query", raw))
                speak("سأبحث لك عن إجابة موثوقة على الإنترنت")
            }
            else -> speak("فهمت كلامك، لكن لا أملك بعد خدمة ذكاء اصطناعي متصلة للإجابة عن هذا السؤال أو تنفيذ هذا الطلب. لن أدّعي أنني نفذته.")
        }
    }

    private fun setTorch(enabled: Boolean) {
        if (android.os.Build.VERSION.SDK_INT < 23) {
            speak("الكشاف غير مدعوم على إصدار أندرويد هذا")
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), 42)
            speak("اسمح لجبران باستخدام الكاميرا لتشغيل الكشاف، ثم أعد طلبك")
            return
        }
        try {
            val manager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = manager.cameraIdList.firstOrNull { id ->
                manager.getCameraCharacteristics(id).get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
            if (cameraId == null) {
                speak("هذا الجهاز لا يحتوي على كشاف متاح")
                return
            }
            manager.setTorchMode(cameraId, enabled)
            torchEnabled = enabled
            speak(if (enabled) "تم تشغيل الكشاف" else "تم إيقاف الكشاف")
        } catch (_: Exception) {
            speak("لم أستطع التحكم في الكشاف الآن")
        }
    }

    override fun onDestroy() {
        speechRecognizer?.destroy()
        tts?.shutdown()
        super.onDestroy()
    }
}

@Composable
private fun JibranHome(
    onSpeak: (String) -> Unit,
    onListen: () -> Unit,
    onCommand: (String) -> Unit,
    onCycleVoice: () -> Unit
) {
    var listening by remember { mutableStateOf(false) }
    var lastCommand by remember { mutableStateOf("أنا جبران. أنا هنا من أجلك.") }
    val motion = rememberInfiniteTransition(label = "jibran-motion")
    val aura by motion.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(2600), RepeatMode.Reverse),
        label = "aura"
    )
    val orbit by motion.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(18000)),
        label = "orbit"
    )

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF05050D)) {
        Box(Modifier.fillMaxSize()) {
            Canvas(Modifier.fillMaxSize()) {
                drawRect(
                    Brush.verticalGradient(
                        listOf(Color(0xFF090817), Color(0xFF070812), Color(0xFF03040A))
                    )
                )
                drawCircle(
                    Brush.radialGradient(
                        listOf(Color(0x332E176A), Color(0x11201445), Color.Transparent)
                    ),
                    radius = size.minDimension * .82f,
                    center = Offset(size.width * .5f, size.height * .47f)
                )
                drawCircle(
                    Brush.radialGradient(listOf(Color(0x22205B75), Color.Transparent)),
                    radius = size.minDimension * .55f,
                    center = Offset(size.width * .95f, size.height * .76f)
                )
            }

            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 25.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Minimal premium header
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            "JIBRAN",
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 4.sp
                        )
                        Text(
                            "A I   A S S I S T A N T",
                            color = Color(0xFF777391),
                            fontSize = 8.sp,
                            letterSpacing = 2.5.sp
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clip(RoundedCornerShape(30.dp))
                            .background(Color(0x171FC997))
                            .border(1.dp, Color(0x3339D6A7), RoundedCornerShape(30.dp))
                            .padding(horizontal = 11.dp, vertical = 7.dp)
                    ) {
                        Box(Modifier.size(6.dp).background(Color(0xFF58E2B1), CircleShape))
                        Spacer(Modifier.width(7.dp))
                        Text("ONLINE", color = Color(0xFF8EDFC3), fontSize = 9.sp, letterSpacing = 1.3.sp)
                    }
                }

                Spacer(Modifier.weight(0.45f))

                // Signature energy sphere
                Box(
                    modifier = Modifier.fillMaxWidth().weight(2.8f),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier.size(310.dp).blur(52.dp)
                            .background(
                                Brush.radialGradient(
                                    listOf(
                                        Color(0x665F35C9),
                                        Color(0x33342A83),
                                        Color(0x1124A9D1),
                                        Color.Transparent
                                    )
                                ),
                                CircleShape
                            )
                    )
                    Canvas(Modifier.size(294.dp)) {
                        val c = center
                        val r = size.minDimension
                        drawCircle(
                            Brush.radialGradient(
                                listOf(Color(0x1C9C72FF), Color(0x087C59D8), Color.Transparent)
                            ),
                            radius = r * .49f
                        )
                        drawCircle(
                            Color(0x168D7AFF),
                            radius = r * .455f,
                            style = Stroke(width = 1.dp.toPx())
                        )
                        drawCircle(
                            Brush.sweepGradient(
                                listOf(
                                    Color.Transparent,
                                    Color(0xFF8E63FF),
                                    Color(0xFF4DE2F5),
                                    Color(0x887A52DB),
                                    Color.Transparent
                                )
                            ),
                            radius = r * .455f,
                            style = Stroke(width = 1.7.dp.toPx())
                        )
                        drawCircle(
                            Color(0x337D6AD0),
                            radius = r * .385f,
                            style = Stroke(width = .8.dp.toPx())
                        )
                        drawCircle(
                            Brush.radialGradient(
                                listOf(
                                    Color(0xFFF0E8FF),
                                    Color(0xFFC4A7FF),
                                    Color(0xFF8958E8),
                                    Color(0xFF38266F),
                                    Color(0xFF10142C)
                                )
                            ),
                            radius = r * .315f
                        )
                        drawCircle(
                            Brush.radialGradient(
                                listOf(Color(0x99FFFFFF), Color(0x22E6D9FF), Color.Transparent),
                                center = Offset(c.x - r * .105f, c.y - r * .14f),
                                radius = r * .25f
                            ),
                            radius = r * .315f
                        )
                        drawArc(
                            color = Color(0x99C7B4FF),
                            startAngle = orbit,
                            sweepAngle = 72f,
                            useCenter = false,
                            topLeft = Offset(c.x - r * .405f, c.y - r * .405f),
                            size = androidx.compose.ui.geometry.Size(r * .81f, r * .81f),
                            style = Stroke(width = 2.2.dp.toPx())
                        )
                        drawCircle(
                            Color(0xFFB5F5FF),
                            radius = 3.2.dp.toPx(),
                            center = Offset(
                                c.x + kotlin.math.cos(Math.toRadians(orbit.toDouble())).toFloat() * r * .405f,
                                c.y + kotlin.math.sin(Math.toRadians(orbit.toDouble())).toFloat() * r * .405f
                            )
                        )
                    }
                    Box(
                        Modifier.size((280 * aura).dp).blur(48.dp)
                            .background(
                                Brush.radialGradient(
                                    listOf(Color(0x227B5BFF), Color.Transparent)
                                ),
                                CircleShape
                            )
                    )
                }

                Spacer(Modifier.height(2.dp))
                Text(
                    if (listening) "أستمع إليك" else "أنا جبران",
                    color = Color.White,
                    fontSize = 27.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = .4.sp
                )
                Spacer(Modifier.height(9.dp))
                Text(
                    lastCommand,
                    color = Color(0xFF9290AE),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 21.sp,
                    modifier = Modifier.padding(horizontal = 18.dp)
                )

                Spacer(Modifier.weight(0.5f))

                // Subtle voice activity line, no preset suggestions
                Row(
                    modifier = Modifier.height(26.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val levels = listOf(7, 12, 19, 11, 23, 14, 8, 17, 10, 21, 12, 7, 15, 9, 5)
                    levels.forEachIndexed { index, level ->
                        Box(
                            Modifier.padding(horizontal = 2.dp)
                                .width(2.dp)
                                .height((if (listening) level * aura else level * .48f).dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color(0xFFB99AFF), Color(0xFF4BCFE8))
                                    )
                                )
                        )
                    }
                }

                Spacer(Modifier.height(23.dp))
                // Large, clear primary microphone control
                Box(contentAlignment = Alignment.Center) {
                    Box(
                        Modifier.size(88.dp).blur(20.dp)
                            .background(Color(0x66865BFA), CircleShape)
                    )
                    Button(
                        onClick = {
                            listening = true
                            lastCommand = "أستمع إليك الآن…"
                            onListen()
                        },
                        modifier = Modifier.size(76.dp),
                        shape = CircleShape,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF8051E8)
                        )
                    ) {
                        Icon(
                            Icons.Rounded.Mic,
                            contentDescription = "تحدث مع جبران",
                            tint = Color.White,
                            modifier = Modifier.size(31.dp)
                        )
                    }
                }
                Spacer(Modifier.height(13.dp))
                Text(
                    "اضغط وتحدّث",
                    color = Color(0xFFB8B2D2),
                    fontSize = 12.sp,
                    letterSpacing = 1.sp
                )
                Spacer(Modifier.height(19.dp))
                OutlinedButton(
                    onClick = onCycleVoice,
                    modifier = Modifier.height(38.dp),
                    shape = RoundedCornerShape(22.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 15.dp, vertical = 4.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFFAAA2C9)
                    )
                ) {
                    Icon(
                        Icons.Rounded.SettingsVoice,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                        tint = Color(0xFFAA8CFF)
                    )
                    Spacer(Modifier.width(7.dp))
                    Text("الصوت", fontSize = 11.sp)
                }
                Spacer(Modifier.height(5.dp))
            }
        }
    }
}
