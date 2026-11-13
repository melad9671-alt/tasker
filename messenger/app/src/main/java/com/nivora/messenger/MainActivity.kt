package com.nivora.messenger

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private val Night = Color(0xFF07131F)
private val Panel = Color(0xFF102332)
private val PanelSoft = Color(0xFF142B3B)
private val Cyan = Color(0xFF69E2E5)
private val Mint = Color(0xFF8AF0D0)
private val Muted = Color(0xFF8FA7B5)
private val White = Color(0xFFF2FAFC)
private val Pink = Color(0xFFFF8EA8)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(7, 19, 31)
        window.navigationBarColor = android.graphics.Color.rgb(7, 19, 31)
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        setContent { NivoraApp() }
    }
}

private enum class Screen { Splash, Phone, Otp, Home, Chat }
private enum class MainTab { Chats, Stories, Missed }

private data class Contact(
    val name: String,
    val message: String,
    val time: String,
    val unread: Int = 0,
    val initials: String,
    val tint: Color
)

@Composable
private fun NivoraApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var screen by remember { mutableStateOf(Screen.Splash) }
    var tab by remember { mutableStateOf(MainTab.Chats) }
    var phone by remember { mutableStateOf("") }
    var otp by remember { mutableStateOf("") }
    var activeChat by remember { mutableStateOf("Lina Hassan") }
    var search by remember { mutableStateOf("") }
    var draft by remember { mutableStateOf("") }
    val messages = remember { mutableStateListOf("Hey! How's your day going?", "Pretty good. I wanted to show you something ✨") }
    val contacts = remember {
        listOf(
            Contact("Lina Hassan", "Sent you a photo", "Now", 2, "LH", Color(0xFF5E9DA7)),
            Contact("Omar Adel", "Voice message · 0:18", "12:42", 1, "OA", Color(0xFF8D78B8)),
            Contact("Mariam Samy", "Are we still meeting?", "11:08", 0, "MS", Color(0xFFB77E8D)),
            Contact("Youssef", "That sounds perfect", "Yesterday", 0, "Y", Color(0xFF789B78)),
            Contact("Nour", "😂😂😂", "Yesterday", 0, "N", Color(0xFFC08D58))
        )
    }
    val dark = darkColorScheme(
        primary = Cyan,
        secondary = Mint,
        background = Night,
        surface = Panel,
        onPrimary = Night,
        onBackground = White,
        onSurface = White
    )

    MaterialTheme(colorScheme = dark) {
        when (screen) {
            Screen.Splash -> {
                SplashScreen(onFinished = { screen = Screen.Phone })
            }
            Screen.Phone -> PhoneScreen(
                phone = phone,
                onPhone = { phone = it },
                onContinue = { screen = Screen.Otp }
            )
            Screen.Otp -> OtpScreen(
                otp = otp,
                onOtp = { otp = it },
                onBack = { screen = Screen.Phone },
                onVerify = { screen = Screen.Home }
            )
            Screen.Home -> HomeScreen(
                tab = tab,
                onTab = { tab = it },
                contacts = contacts,
                search = search,
                onSearch = { search = it },
                onOpenChat = { activeChat = it; screen = Screen.Chat },
                onSettings = { Toast.makeText(context, "Settings preview", Toast.LENGTH_SHORT).show() }
            )
            Screen.Chat -> ChatScreen(
                name = activeChat,
                messages = messages,
                draft = draft,
                onDraft = { draft = it },
                onBack = { screen = Screen.Home },
                onSend = {
                    if (draft.isNotBlank()) {
                        messages.add(draft.trim())
                        draft = ""
                    }
                }
            )
        }
    }
}

private object NivoraAppContextHolder {
    lateinit var context: android.content.Context
}

@Composable
private fun SplashScreen(onFinished: () -> Unit) {
    var phase by remember { mutableIntStateOf(0) }
    val dotProgress = androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (phase >= 1) 1f else 0f,
        animationSpec = tween(1900, easing = FastOutSlowInEasing), label = "fallingDot"
    )
    val outlineProgress = androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (phase >= 2) 1f else 0f,
        animationSpec = tween(1050, easing = FastOutSlowInEasing), label = "logoOutline"
    )
    val nameProgress = androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (phase >= 3) 1f else 0f,
        animationSpec = tween(950, easing = FastOutSlowInEasing), label = "brandReveal"
    )
    val contentAlpha = androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (phase >= 2) 1f else 0f,
        animationSpec = tween(550), label = "logoContent"
    )

    LaunchedEffect(Unit) {
        delay(250)
        phase = 1
        delay(2050)
        phase = 2
        delay(1100)
        phase = 3
        delay(1250)
        onFinished()
    }

    Box(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF071725), Color(0xFF0B2635), Color(0xFF07131F))))
            .clickable { onFinished() },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val t = dotProgress.value
            if (t < 1f) {
                val y = h * (.035f + .445f * t)
                val x = w * (.5f + .115f * kotlin.math.sin(t * Math.PI * 3.0).toFloat())
                val r = 11.dp.toPx()
                val drop = Path().apply {
                    moveTo(x, y - r * 1.65f)
                    cubicTo(x - r * .35f, y - r * .8f, x - r * 1.25f, y + r * .35f, x - r * .78f, y + r * .9f)
                    cubicTo(x - r * .35f, y + r * 1.48f, x + r * .55f, y + r * 1.42f, x + r * .85f, y + r * .72f)
                    cubicTo(x + r * 1.15f, y + r * .12f, x + r * .25f, y - r * .9f, x, y - r * 1.65f)
                    close()
                }
                drawPath(drop, Brush.linearGradient(listOf(Color(0xFFE1FFFF), Cyan, Color(0xFF36AEBB)), Offset(x-r,y-r), Offset(x+r,y+r)))
            }

            if (outlineProgress.value > 0f) {
                val cx = w * .5f
                val cy = h * .48f
                val rw = size.minDimension * .205f
                val rh = rw * 1.08f
                val p = Path().apply {
                    moveTo(cx, cy - rh)
                    cubicTo(cx + rw * 1.08f, cy - rh * .98f, cx + rw * 1.12f, cy + rh * .18f, cx, cy + rh)
                    cubicTo(cx - rw * 1.12f, cy + rh * .18f, cx - rw * 1.08f, cy - rh * .98f, cx, cy - rh)
                }
                drawPath(
                    p,
                    brush = Brush.linearGradient(listOf(Color(0xFFB8FFFF), Cyan, Mint)),
                    style = Stroke(width = 3.2.dp.toPx(), cap = StrokeCap.Round),
                    alpha = outlineProgress.value
                )
                val inner = Path().apply {
                    moveTo(cx - rw * .42f, cy + rh * .18f)
                    cubicTo(cx - rw * .06f, cy - rh * .55f, cx + rw * .10f, cy - rh * .55f, cx + rw * .42f, cy - rh * .18f)
                    cubicTo(cx + rw * .08f, cy + rh * .55f, cx - rw * .10f, cy + rh * .55f, cx - rw * .42f, cy + rh * .18f)
                    close()
                }
                drawPath(inner, Color(0xFF72E5E4).copy(alpha = contentAlpha.value))
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(190.dp))
            Box(Modifier.height(48.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    "NIVORA",
                    color = White,
                    fontSize = 27.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 7.sp,
                    modifier = Modifier.graphicsLayer {
                        clip = true
                        alpha = nameProgress.value
                        scaleX = nameProgress.value
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, .5f)
                    }
                )
            }
            Text(
                "closer, in every moment",
                color = Muted.copy(alpha = nameProgress.value),
                fontSize = 11.sp,
                letterSpacing = 1.8.sp
            )
        }
        Text(
            "TAP TO SKIP",
            Modifier.align(Alignment.BottomCenter).padding(bottom = 34.dp),
            color = Muted.copy(alpha = .7f),
            fontSize = 9.sp,
            letterSpacing = 2.sp
        )
    }
}

@Composable
private fun PhoneScreen(phone: String, onPhone: (String) -> Unit, onContinue: () -> Unit) {
    AuthFrame(
        eyebrow = "A quieter kind of connection",
        title = "Your people.\nYour space.",
        subtitle = "Sign in with your phone number to start your private circle."
    ) {
        Text("PHONE NUMBER", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.8.sp)
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Panel).padding(horizontal = 15.dp, vertical = 17.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("+20", color = Cyan, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(12.dp))
            Box(Modifier.width(1.dp).height(24.dp).background(Color.White.copy(alpha = .12f)))
            Spacer(Modifier.width(12.dp))
            BasicTextField(
                value = phone, onValueChange = { onPhone(it.filter(Char::isDigit).take(11)) },
                modifier = Modifier.weight(1f), singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                textStyle = androidx.compose.ui.text.TextStyle(color = White, fontSize = 16.sp),
                decorationBox = { inner ->
                    if (phone.isEmpty()) Text("Your phone number", color = Muted)
                    inner()
                }
            )
        }
        Spacer(Modifier.height(18.dp))
        GradientButton("Continue", onContinue, enabled = phone.length >= 8)
        Spacer(Modifier.height(14.dp))
        Text("By continuing, you agree to keep your circle respectful and safe.", color = Muted, fontSize = 11.sp, lineHeight = 17.sp)
        Spacer(Modifier.height(18.dp))
        Text("VERIFICATION PROVIDER SETUP REQUIRED", color = Color(0xFF64818F), fontSize = 9.sp, letterSpacing = 1.sp)
    }
}

@Composable
private fun OtpScreen(otp: String, onOtp: (String) -> Unit, onBack: () -> Unit, onVerify: () -> Unit) {
    AuthFrame(
        eyebrow = "One small step",
        title = "Verify it's\nreally you.",
        subtitle = "Enter the 6-digit code sent to your phone. Verification activates after a secure SMS provider is connected."
    ) {
        BasicTextField(
            value = otp,
            onValueChange = { onOtp(it.filter(Char::isDigit).take(6)) },
            modifier = Modifier.fillMaxWidth().height(58.dp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            textStyle = androidx.compose.ui.text.TextStyle(color = Color.Transparent),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(Color.Transparent),
            decorationBox = { innerTextField ->
                Box(Modifier.fillMaxSize()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        repeat(6) { index ->
                            Box(
                                Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(15.dp))
                                    .background(Panel)
                                    .border(1.dp, if (otp.length > index) Cyan.copy(alpha = .65f) else Color.White.copy(alpha = .06f), RoundedCornerShape(15.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(otp.getOrNull(index)?.toString() ?: "–", color = White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                    Box(Modifier.fillMaxSize().padding(1.dp)) { innerTextField() }
                }
            }
        )
        Spacer(Modifier.height(20.dp))
        GradientButton("Verify & enter", onVerify, enabled = otp.length == 6)
        TextButton(onClick = onBack) { Text("Change phone number", color = Cyan) }
    }
}

@Composable
private fun AuthFrame(eyebrow: String, title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    var entered by remember { mutableStateOf(false) }
    val enterAlpha by animateFloatAsState(if (entered) 1f else 0f, tween(650, easing = FastOutSlowInEasing), label = "authFade")
    val enterOffset by animateFloatAsState(if (entered) 0f else 34f, tween(650, easing = FastOutSlowInEasing), label = "authSlide")
    LaunchedEffect(Unit) { delay(80); entered = true }
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF0D2635), Night)))) {
        Box(Modifier.align(Alignment.TopEnd).padding(top = 52.dp, end = 30.dp).size(160.dp).background(Cyan.copy(alpha = .06f), CircleShape))
        Column(Modifier.fillMaxSize().graphicsLayer { alpha = enterAlpha; translationY = enterOffset }
            .imePadding().verticalScroll(androidx.compose.foundation.rememberScrollState())
            .padding(horizontal = 26.dp).padding(top = 30.dp, bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(Cyan), contentAlignment = Alignment.Center) {
                        Text("N", color = Night, fontWeight = FontWeight.Black, fontSize = 23.sp)
                    }
                    Spacer(Modifier.width(10.dp))
                    Text("NIVORA", color = White, fontWeight = FontWeight.Bold, letterSpacing = 3.sp, fontSize = 14.sp)
                }
                Spacer(Modifier.height(34.dp))
                Text(eyebrow.uppercase(), color = Cyan, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                Spacer(Modifier.height(15.dp))
                Text(title, color = White, fontSize = 39.sp, lineHeight = 43.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp)
                Spacer(Modifier.height(14.dp))
                Text(subtitle, color = Muted, fontSize = 14.sp, lineHeight = 22.sp)
            }
            Column(content = content)
        }
    }
}

@Composable
private fun GradientButton(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    Box(
        Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(18.dp))
            .background(if (enabled) Brush.horizontalGradient(listOf(Cyan, Mint)) else Brush.horizontalGradient(listOf(Color(0xFF35505B), Color(0xFF35505B))))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Text(text, color = if (enabled) Night else Muted, fontWeight = FontWeight.Bold, fontSize = 15.sp) }
}

@Composable
private fun HomeScreen(
    tab: MainTab, onTab: (MainTab) -> Unit, contacts: List<Contact>, search: String,
    onSearch: (String) -> Unit, onOpenChat: (String) -> Unit, onSettings: () -> Unit
) {
    Scaffold(
        containerColor = Night,
        bottomBar = {
            Row(
                Modifier.fillMaxWidth().background(Color(0xFF0A1B28)).padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                BottomItem("Chats", Icons.Default.ChatBubble, tab == MainTab.Chats) { onTab(MainTab.Chats) }
                BottomItem("Stories", Icons.Default.Waves, tab == MainTab.Stories) { onTab(MainTab.Stories) }
                BottomItem("Missed calls", Icons.Default.Call, tab == MainTab.Missed) { onTab(MainTab.Missed) }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.fillMaxWidth().padding(start = 22.dp, end = 18.dp, top = 18.dp, bottom = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("GOOD EVENING", color = Cyan, fontSize = 9.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
                    Text(when (tab) { MainTab.Chats -> "Your circle"; MainTab.Stories -> "Moments"; MainTab.Missed -> "Calls" }, color = White, fontSize = 29.sp, fontWeight = FontWeight.Bold)
                }
                IconButton(onClick = onSettings) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Muted)
                }
                Box(Modifier.size(42.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Cyan, Mint))), contentAlignment = Alignment.Center) {
                    Text("M", color = Night, fontWeight = FontWeight.Black, fontSize = 17.sp)
                }
            }
            when (tab) {
                MainTab.Chats -> {
                    SearchField(search, onSearch)
                    Spacer(Modifier.height(18.dp))
                    StoriesStrip()
                    Spacer(Modifier.height(18.dp))
                    Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("MESSAGES", color = Muted, fontSize = 10.sp, letterSpacing = 1.8.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        Text("See all", color = Cyan, fontSize = 11.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                        items(contacts.filter { it.name.contains(search, true) || it.message.contains(search, true) }) { contact ->
                            ChatRow(contact) { onOpenChat(contact.name) }
                        }
                    }
                }
                MainTab.Stories -> StoriesPage()
                MainTab.Missed -> MissedCallsPage()
            }
        }
    }
}

@Composable
private fun BottomItem(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean, onClick: () -> Unit) {
    Column(Modifier.clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 5.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = label, tint = if (selected) Cyan else Muted, modifier = Modifier.size(21.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, color = if (selected) Cyan else Muted, fontSize = 9.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun SearchField(value: String, onValue: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp).clip(RoundedCornerShape(16.dp)).background(Panel).padding(horizontal = 15.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Search, contentDescription = null, tint = Muted, modifier = Modifier.size(19.dp))
        Spacer(Modifier.width(10.dp))
        BasicTextField(value, onValue, Modifier.weight(1f), singleLine = true, textStyle = androidx.compose.ui.text.TextStyle(color = White, fontSize = 13.sp), decorationBox = { inner ->
            if (value.isEmpty()) Text("Search your conversations", color = Muted, fontSize = 13.sp)
            inner()
        })
    }
}

@Composable
private fun StoriesStrip() {
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(57.dp).clip(CircleShape).background(PanelSoft).border(1.dp, Color.White.copy(alpha = .1f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Add, contentDescription = "Add story", tint = Cyan, modifier = Modifier.size(25.dp))
            }
            Spacer(Modifier.height(6.dp)); Text("Your story", color = Muted, fontSize = 10.sp)
        }
        listOf("Lina", "Omar", "Mariam", "Nour").forEachIndexed { i, name ->
            val colors = listOf(Color(0xFF5E9DA7), Color(0xFF8D78B8), Color(0xFFB77E8D), Color(0xFFC08D58))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(57.dp).border(2.dp, Brush.linearGradient(listOf(Cyan, Mint)), CircleShape).padding(4.dp).clip(CircleShape).background(colors[i]), contentAlignment = Alignment.Center) {
                    Text(name.take(1), color = White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
                Spacer(Modifier.height(6.dp)); Text(name, color = Muted, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun ChatRow(contact: Contact, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(53.dp).clip(CircleShape).background(contact.tint), contentAlignment = Alignment.Center) {
            Text(contact.initials, color = White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(contact.name, color = White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(contact.message, color = Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(contact.time, color = if (contact.unread > 0) Cyan else Muted, fontSize = 9.sp)
            if (contact.unread > 0) {
                Spacer(Modifier.height(7.dp))
                Box(Modifier.size(19.dp).clip(CircleShape).background(Cyan), contentAlignment = Alignment.Center) {
                    Text(contact.unread.toString(), color = Night, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun StoriesPage() {
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Text("Your day, in little moments.", color = Muted, fontSize = 13.sp)
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Panel).clickable { }.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(54.dp).clip(CircleShape).background(Cyan.copy(alpha = .14f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Add, contentDescription = null, tint = Cyan, modifier = Modifier.size(27.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text("Share a moment", color = White, fontWeight = FontWeight.SemiBold)
                Text("A photo, a thought, a little update", color = Muted, fontSize = 11.sp)
            }
        }
        Spacer(Modifier.height(25.dp))
        Text("RECENT UPDATES", color = Muted, fontSize = 10.sp, letterSpacing = 1.8.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(14.dp))
        listOf("Lina Hassan", "Omar Adel", "Mariam Samy").forEachIndexed { i, name ->
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).border(2.dp, Brush.linearGradient(listOf(Cyan, Mint)), CircleShape).padding(4.dp).clip(CircleShape).background(listOf(Color(0xFF5E9DA7), Color(0xFF8D78B8), Color(0xFFB77E8D))[i]), contentAlignment = Alignment.Center) {
                    Text(name.take(1), color = White, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(13.dp))
                Column {
                    Text(name, color = White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Text(listOf("12 minutes ago", "1 hour ago", "3 hours ago")[i], color = Muted, fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
private fun MissedCallsPage() {
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Text("Calls you couldn't pick up.", color = Muted, fontSize = 13.sp)
        Spacer(Modifier.height(20.dp))
        listOf("Omar Adel", "Lina Hassan").forEachIndexed { i, name ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Panel).padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(45.dp).clip(CircleShape).background(if (i == 0) Color(0xFF8D78B8) else Color(0xFF5E9DA7)), contentAlignment = Alignment.Center) {
                    Text(name.take(1), color = White, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(name, color = White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Text(if (i == 0) "Missed voice call · 10:42" else "Missed video call · Yesterday", color = Pink, fontSize = 10.sp)
                }
                IconButton(onClick = { }) { Icon(Icons.Default.Call, contentDescription = "Call back", tint = Cyan) }
            }
            Spacer(Modifier.height(10.dp))
        }
        Spacer(Modifier.height(10.dp))
        Text("Your recent calls will appear here.", color = Muted, fontSize = 11.sp)
    }
}

@Composable
private fun ChatScreen(
    name: String, messages: List<String>, draft: String, onDraft: (String) -> Unit,
    onBack: () -> Unit, onSend: () -> Unit
) {
    var showAttachments by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    Column(Modifier.fillMaxSize().background(Night)) {
        Row(Modifier.fillMaxWidth().background(Color(0xFF0B1D2A)).padding(horizontal = 8.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = White) }
            Box(Modifier.size(38.dp).clip(CircleShape).background(Color(0xFF5E9DA7)), contentAlignment = Alignment.Center) { Text(name.take(1), color = White, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(name, color = White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text("tap for contact info", color = Muted, fontSize = 9.sp)
            }
            IconButton(onClick = { Toast.makeText(context, "Voice calling will be connected in the online phase", Toast.LENGTH_SHORT).show() }) { Icon(Icons.Default.Phone, contentDescription = "Voice call", tint = Cyan) }
            IconButton(onClick = { Toast.makeText(context, "Video calling will be connected in the online phase", Toast.LENGTH_SHORT).show() }) { Icon(Icons.Default.Videocam, contentDescription = "Video call", tint = Cyan) }
            IconButton(onClick = { }) { Icon(Icons.Default.MoreVert, contentDescription = "More", tint = Muted) }
        }
        Column(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 15.dp), verticalArrangement = Arrangement.Bottom) {
            Text("TODAY", Modifier.align(Alignment.CenterHorizontally).padding(vertical = 15.dp), color = Muted, fontSize = 9.sp, letterSpacing = 1.5.sp)
            messages.forEachIndexed { index, message ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (index % 2 == 0) Arrangement.Start else Arrangement.End) {
                    Box(
                        Modifier.widthIn(max = 285.dp).padding(vertical = 5.dp)
                            .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomEnd = if (index % 2 == 0) 18.dp else 5.dp, bottomStart = if (index % 2 == 0) 5.dp else 18.dp))
                            .background(if (index % 2 == 0) PanelSoft else Color(0xFF1A5962))
                            .padding(horizontal = 15.dp, vertical = 11.dp)
                    ) {
                        Column {
                            Text(message, color = White, fontSize = 13.sp, lineHeight = 19.sp)
                            Spacer(Modifier.height(4.dp))
                            Text(if (index % 2 == 0) "10:42" else "10:43  ✓✓", color = Color.White.copy(alpha = .55f), fontSize = 8.sp, modifier = Modifier.align(Alignment.End))
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
        }
        if (showAttachments) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceAround) {
                AttachmentChip("Photo", Icons.Default.Image) { Toast.makeText(context, "Photo picker will be connected", Toast.LENGTH_SHORT).show(); showAttachments = false }
                AttachmentChip("File", Icons.Default.AttachFile) { Toast.makeText(context, "File sharing will be connected", Toast.LENGTH_SHORT).show(); showAttachments = false }
                AttachmentChip("Camera", Icons.Default.CameraAlt) { Toast.makeText(context, "Camera will be connected", Toast.LENGTH_SHORT).show(); showAttachments = false }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { showAttachments = !showAttachments }) { Icon(Icons.Default.Add, contentDescription = "Attach", tint = Cyan) }
            Row(Modifier.weight(1f).clip(RoundedCornerShape(22.dp)).background(Panel).padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicTextField(draft, onDraft, Modifier.weight(1f), textStyle = androidx.compose.ui.text.TextStyle(color = White, fontSize = 13.sp), decorationBox = { inner ->
                    if (draft.isEmpty()) Text("Message...", color = Muted, fontSize = 13.sp)
                    inner()
                })
                Icon(Icons.Default.AttachFile, contentDescription = null, tint = Muted, modifier = Modifier.size(17.dp))
            }
            Spacer(Modifier.width(6.dp))
            if (draft.isBlank()) {
                IconButton(onClick = { Toast.makeText(context, "Hold to record a voice message in the full build", Toast.LENGTH_SHORT).show() }) {
                    Icon(Icons.Default.Mic, contentDescription = "Voice message", tint = Cyan)
                }
            } else {
                IconButton(onClick = onSend) { Icon(Icons.Default.Send, contentDescription = "Send", tint = Cyan) }
            }
        }
    }
}

@Composable
private fun AttachmentChip(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Column(Modifier.clip(RoundedCornerShape(14.dp)).background(Panel).clickable(onClick = onClick).padding(horizontal = 17.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = label, tint = Cyan)
        Spacer(Modifier.height(4.dp))
        Text(label, color = Muted, fontSize = 10.sp)
    }
}
