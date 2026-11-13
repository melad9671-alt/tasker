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
    var lastCommand by remember { mutableStateOf("أنا هنا. قل لي ما الذي تحتاجه.") }
    val transition = rememberInfiniteTransition(label = "jibran-orb")
    val pulse by transition.animateFloat(
        initialValue = 0.97f, targetValue = 1.035f,
        animationSpec = infiniteRepeatable(tween(2200), RepeatMode.Reverse),
        label = "orb-pulse"
    )
    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF070812)) {
        Box(Modifier.fillMaxSize()) {
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(
                    Brush.radialGradient(listOf(Color(0x442E176A), Color.Transparent)),
                    radius = size.minDimension * .8f,
                    center = Offset(size.width * .48f, size.height * .34f)
                )
                drawCircle(
                    Brush.radialGradient(listOf(Color(0x22205B75), Color.Transparent)),
                    radius = size.minDimension * .55f,
                    center = Offset(size.width * .95f, size.height * .78f)
                )
            }
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("JIBRAN AI", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.2.sp)
                        Text("مساعدك الشخصي", color = Color(0xFF9295B5), fontSize = 12.sp)
                    }
                    Box(
                        Modifier.clip(RoundedCornerShape(30.dp))
                            .background(Color(0x221FC997))
                            .border(1.dp, Color(0x5539D6A7), RoundedCornerShape(30.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text("●  جاهز", color = Color(0xFF65E2B7), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                Column(
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(30.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xFF17182D), Color(0xFF101222), Color(0xFF0C0E1A))
                            )
                        )
                        .border(1.dp, Color(0xFF292B49), RoundedCornerShape(30.dp))
                        .padding(horizontal = 20.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("مستعد للاستماع", color = Color(0xFFB7B9D0), fontSize = 12.sp, letterSpacing = 1.sp)
                    Spacer(Modifier.height(18.dp))
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(214.dp)) {
                        Box(
                            Modifier.size((196 * pulse).dp).blur(34.dp)
                                .background(
                                    Brush.radialGradient(
                                        listOf(Color(0x778B5CF6), Color(0x3336D9F5), Color.Transparent)
                                    ),
                                    CircleShape
                                )
                        )
                        Canvas(Modifier.size(194.dp)) {
                            drawCircle(
                                Brush.radialGradient(
                                    listOf(Color(0xFF49317F), Color(0xFF1A1D3B), Color(0xFF0B0D19))
                                )
                            )
                            drawCircle(
                                Brush.sweepGradient(
                                    listOf(Color.Transparent, Color(0xFF9B7BFF), Color(0xFF42DDF3), Color.Transparent)
                                ),
                                style = Stroke(width = 2.2.dp.toPx())
                            )
                            drawCircle(
                                Brush.radialGradient(
                                    listOf(Color(0xFFE1D7FF), Color(0xFF8D6BEA), Color(0xFF27345A))
                                ),
                                radius = size.minDimension * .27f
                            )
                            drawCircle(
                                Color(0x55FFFFFF),
                                radius = size.minDimension * .37f,
                                style = Stroke(width = 1.dp.toPx())
                            )
                        }
                        Icon(
                            Icons.Rounded.SettingsVoice,
                            contentDescription = "Jibran AI",
                            tint = Color.White,
                            modifier = Modifier.size(43.dp)
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        if (listening) "أستمع إليك الآن" else "مرحباً، أنا جبران",
                        color = Color.White,
                        fontSize = 23.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        lastCommand,
                        color = Color(0xFFA4A7C2),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    Box(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                            .background(Color(0x331C2039))
                            .padding(horizontal = 14.dp, vertical = 11.dp)
                    ) {
                        Text(
                            "تحدث بطريقتك الطبيعية؛ لا تحتاج إلى اختيار أمر من قائمة.",
                            color = Color(0xFFB9BBD0),
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Button(
                        onClick = {
                            listening = true
                            lastCommand = "أنا أستمع… تفضل بالكلام"
                            onListen()
                        },
                        modifier = Modifier.fillMaxWidth().height(62.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7547D8))
                    ) {
                        Icon(Icons.Rounded.Mic, contentDescription = null, tint = Color.White)
                        Spacer(Modifier.width(10.dp))
                        Text("تحدث مع جبران", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = onCycleVoice,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Rounded.SettingsVoice, contentDescription = null, tint = Color(0xFFB9A4FF))
                        Spacer(Modifier.width(8.dp))
                        Text("اختيار صوت آخر من أصوات الجهاز", color = Color(0xFFD5D7EC), fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(5.dp))
                    Text("JIBRAN AI  •  YOUR PERSONAL ASSISTANT", color = Color(0xFF686B87), fontSize = 8.sp, letterSpacing = 1.2.sp)
                }
            }
        }
    }
}
