package com.nexora.app

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.random.Random

private val Ink = Color(0xFF09090D)
private val Panel = Color(0xFF171720)
private val Purple = Color(0xFF9B72F2)
private val Muted = Color(0xFF9A98A8)
private val White = Color(0xFFF5F3FA)

data class PlanTask(val id: Long, val title: String, val category: String, val minutes: Int, val done: Boolean = false, val period: String = "Daily")

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { NexoraApp() }
    }
}

@Composable
fun NexoraApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("nexora_local", Context.MODE_PRIVATE) }
    var userName by remember { mutableStateOf(prefs.getString("name", "") ?: "") }
    var stage by remember { mutableStateOf(prefs.getString("stage", "Secondary") ?: "Secondary") }
    var onboarded by remember { mutableStateOf(prefs.getBoolean("ready", false)) }
    var selectedTab by remember { mutableStateOf("Daily") }
    var tasks by remember { mutableStateOf(loadTasks(prefs)) }
    var showAdd by remember { mutableStateOf(false) }
    var activeId by remember { mutableStateOf<Long?>(null) }
    var remaining by remember { mutableIntStateOf(0) }
    var running by remember { mutableStateOf(false) }

    LaunchedEffect(running, activeId) {
        while (running && remaining > 0) {
            delay(1000)
            remaining--
        }
        if (running && remaining == 0) {
            tasks = tasks.map { if (it.id == activeId) it.copy(done = true) else it }
            saveTasks(prefs, tasks)
            running = false
            activeId = null
        }
    }

    MaterialTheme(colorScheme = darkColorScheme(
        background = Ink, surface = Panel, primary = Purple, onBackground = White,
        onSurface = White, secondary = Color(0xFF69B8D6)
    )) {
        Surface(Modifier.fillMaxSize(), color = Ink) {
            if (!onboarded) {
                Onboarding(
                    name = userName, onName = { userName = it }, stage = stage, onStage = { stage = it },
                    onFinish = {
                        prefs.edit().putString("name", userName.trim()).putString("stage", stage).putBoolean("ready", true).apply()
                        onboarded = true
                    }
                )
            } else {
                Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
                    Spacer(Modifier.height(24.dp))
                    Text("NEXORA", color = White, fontSize = 13.sp, letterSpacing = 4.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(18.dp))
                    Text("Your life, in rhythm.", color = White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Text("Hello, $userName  ·  $stage", color = Muted, fontSize = 13.sp)
                    Spacer(Modifier.height(20.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Daily", "Weekly", "Monthly").forEach { tab ->
                            TabPill(tab, selectedTab == tab) { selectedTab = tab }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Yearly", "Catch-up", "Extra").forEach { tab ->
                            TabPill(tab, selectedTab == tab) { selectedTab = tab }
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(selectedTab, color = White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                            Text(LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM")), color = Muted, fontSize = 12.sp)
                        }
                        FilledTonalButton(onClick = { tasks = (tasks.filterNot { it.period == selectedTab && it.category != "custom" } + generatePlan(stage, selectedTab)).also { saveTasks(prefs, it) } }) {
                            Icon(Icons.Default.AutoAwesome, null)
                            Spacer(Modifier.width(5.dp))
                            Text("Generate")
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    if (running) {
                        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF29203C)), shape = RoundedCornerShape(22.dp)) {
                            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                                Text("FOCUS SESSION", color = Purple, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Text(tasks.firstOrNull { it.id == activeId }?.title ?: "Focus", color = White, fontSize = 17.sp)
                                Text("%02d:%02d".format(remaining / 60, remaining % 60), color = White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                                OutlinedButton(onClick = { running = false }) { Text("Pause") }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (tasks.none { it.period == selectedTab }) item {
                            Text("No plans yet. Generate a flexible plan or add your first task.", color = Muted, modifier = Modifier.padding(vertical = 28.dp))
                        }
                        items(tasks.filter { it.period == selectedTab }, key = { it.id }) { task ->
                            TaskCard(task, onStart = {
                                activeId = task.id
                                remaining = task.minutes * 60
                                running = true
                            }, onDone = {
                                tasks = tasks.map { if (it.id == task.id) it.copy(done = true) else it }
                                saveTasks(prefs, tasks)
                            })
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = { showAdd = true }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(18.dp)) {
                            Icon(Icons.Default.Add, null); Text(" Add task")
                        }
                        Button(onClick = { tasks = (tasks.filterNot { it.period == selectedTab && it.category != "custom" } + generatePlan(stage, selectedTab)).also { saveTasks(prefs, it) } }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(18.dp)) {
                            Icon(Icons.Default.AutoAwesome, null); Text(" Refresh plan")
                        }
                    }
                }
            }
            if (showAdd) AddTaskDialog(onDismiss = { showAdd = false }, onSave = { title, mins ->
                tasks = (tasks + PlanTask(System.currentTimeMillis(), title, "custom", mins, period = selectedTab)).also { saveTasks(prefs, it) }
                showAdd = false
            })
        }
    }
}

@Composable
fun Onboarding(name: String, onName: (String) -> Unit, stage: String, onStage: (String) -> Unit, onFinish: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text("NEXORA", color = Purple, fontSize = 14.sp, letterSpacing = 5.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        Text("Make room for what matters.", color = White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("A planner that adapts to your real day.", color = Muted)
        Spacer(Modifier.height(30.dp))
        Text("What should we call you?", color = White, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = name, onValueChange = onName, modifier = Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("Your name") })
        Spacer(Modifier.height(20.dp))
        Text("Choose your current stage", color = White, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        listOf("Middle school", "Secondary", "University", "Graduate / Work", "Personal").forEach {
            val value = when (it) { "Middle school" -> "Middle school"; "Graduate / Work" -> "Graduate / Work"; else -> it }
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).border(1.dp, if (stage == value) Purple else Panel, RoundedCornerShape(16.dp)).background(if (stage == value) Color(0xFF211A31) else Panel, RoundedCornerShape(16.dp)).clickable { onStage(value) }.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(it, color = White, modifier = Modifier.weight(1f))
                if (stage == value) Icon(Icons.Default.CheckCircle, null, tint = Purple)
            }
        }
        Spacer(Modifier.height(24.dp))
        Button(onClick = onFinish, enabled = name.isNotBlank(), modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(18.dp)) { Text("Let's begin") }
        Text("Your profile stays on this device.", color = Muted, fontSize = 11.sp, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 12.dp))
    }
}

@Composable
fun TabPill(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(Modifier.width(100.dp).border(1.dp, if (selected) Purple else Color(0xFF292833), RoundedCornerShape(50)).background(if (selected) Color(0xFF2B2140) else Panel, RoundedCornerShape(50)).clickable { onClick() }.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
        Text(label, color = if (selected) White else Muted, fontSize = 12.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
fun TaskCard(task: PlanTask, onStart: () -> Unit, onDone: () -> Unit) {
    val tint = when (task.category) { "study" -> Purple; "break" -> Color.White; "commute" -> Color(0xFF69B8D6); else -> Color(0xFFE4A45D) }
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(4.dp).height(48.dp).background(tint, RoundedCornerShape(8.dp)))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(task.title, color = if (task.done) Muted else White, fontWeight = FontWeight.SemiBold)
                Text("${task.minutes} min  ·  ${task.category.uppercase()}", color = Muted, fontSize = 11.sp)
            }
            if (task.done) Text("DONE", color = Color(0xFF70C99B), fontSize = 11.sp)
            else if (task.category == "study" || task.category == "custom") {
                IconButton(onClick = onStart) { Icon(Icons.Default.PlayArrow, "Start", tint = Purple) }
            } else {
                TextButton(onClick = onDone) { Text("Done") }
            }
        }
    }
}

@Composable
fun AddTaskDialog(onDismiss: () -> Unit, onSave: (String, Int) -> Unit) {
    var title by remember { mutableStateOf("") }
    var mins by remember { mutableStateOf("30") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("New task") }, text = {
        Column {
            OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Task name") }, singleLine = true)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = mins, onValueChange = { mins = it.filter(Char::isDigit).take(3) }, label = { Text("Duration (minutes)") }, singleLine = true)
        }
    }, confirmButton = { TextButton(onClick = { onSave(title.trim(), (mins.toIntOrNull() ?: 30).coerceIn(1, 240)) }, enabled = title.isNotBlank()) { Text("Save") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

fun generatePlan(stage: String, period: String): List<PlanTask> {
    val subjects = when (stage) {
        "Middle school" -> listOf("الرياضيات", "العلوم", "اللغة العربية", "English", "الدراسات")
        "University" -> listOf("مراجعة محاضرة", "حل تدريبات", "قراءة مرجعية", "مشروع التخصص")
        else -> listOf("الرياضيات", "اللغة العربية", "اللغة الإنجليزية", "التاريخ", "العلوم")
    }.shuffled(Random(System.nanoTime()))
    val count = when (period) { "Weekly" -> 7; "Monthly" -> 12; "Yearly" -> 20; "Catch-up" -> 9; else -> 4 }
    val result = mutableListOf<PlanTask>()
    result += PlanTask(System.currentTimeMillis(), "راحة وفطار", "break", 30, period = period)
    repeat(count) { i ->
        result += PlanTask(System.currentTimeMillis() + i + 1, "مذاكرة ${subjects[i % subjects.size]}", "study", if (period == "Catch-up") 50 else 40, period = period)
        if (i % 2 == 1) result += PlanTask(System.currentTimeMillis() + 100 + i, "استراحة قصيرة", "break", 15, period = period)
    }
    return result
}

fun saveTasks(context: SharedPreferences, tasks: List<PlanTask>) {
    val array = JSONArray()
    tasks.forEach { t -> array.put(JSONObject().put("id", t.id).put("title", t.title).put("category", t.category).put("minutes", t.minutes).put("done", t.done).put("period", t.period)) }
    context.edit().putString("tasks", array.toString()).apply()
}

fun loadTasks(context: SharedPreferences): List<PlanTask> = try {
    val array = JSONArray(context.getString("tasks", "[]"))
    (0 until array.length()).map { i ->
        val o = array.getJSONObject(i)
        PlanTask(o.getLong("id"), o.getString("title"), o.getString("category"), o.getInt("minutes"), o.optBoolean("done"), o.optString("period", "Daily"))
    }
} catch (_: Exception) { emptyList() }

