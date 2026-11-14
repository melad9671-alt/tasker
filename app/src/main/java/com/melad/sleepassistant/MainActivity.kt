package com.melad.sleepassistant

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Calendar

private val Night = Color(0xFF0B1020)
private val Panel = Color(0xFF151D31)
private val Accent = Color(0xFF8B7CFF)
private val Muted = Color(0xFF9AA6C0)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SleepAssistantApp(this) }
    }
}

@Composable
private fun SleepAssistantApp(activity: MainActivity) {
    var tab by remember { mutableStateOf(0) }
    var tasks by remember { mutableStateOf(listOf("تشغيل عدم الإزعاج — 03:00", "إيقاف عدم الإزعاج — 05:00")) }
    var showCreate by remember { mutableStateOf(false) }
    val nm = activity.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val dndAllowed = nm.isNotificationPolicyAccessGranted

    MaterialTheme(colorScheme = darkColorScheme(
        primary = Accent, background = Night, surface = Panel,
        onBackground = Color.White, onSurface = Color.White
    )) {
        Scaffold(containerColor = Night, bottomBar = {
            NavigationBar(containerColor = Panel) {
                NavigationBarItem(selected = tab == 0, onClick = { tab = 0 },
                    icon = { Icon(Icons.Default.Home, null) }, label = { Text("الرئيسية") })
                NavigationBarItem(selected = tab == 1, onClick = { tab = 1 },
                    icon = { Icon(Icons.Default.Schedule, null) }, label = { Text("المهام") })
                NavigationBarItem(selected = tab == 2, onClick = { tab = 2 },
                    icon = { Icon(Icons.Default.Apps, null) }, label = { Text("التطبيقات") })
            }
        }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Bedtime, null, tint = Accent, modifier = Modifier.size(30.dp))
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("SLEEP ASSISTANT", color = Color.White, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                        Text("خلّي المهام تخلص وإنت مرتاح", color = Muted, fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.height(24.dp))
                when (tab) {
                    0 -> {
                        Text("مساء الخير 👋", color = Muted)
                        Text("وقتك للراحة.", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Spacer(Modifier.height(18.dp))
                        Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(22.dp)) {
                            Column(Modifier.padding(18.dp)) {
                                Text("حالة الأتمتة", color = Muted)
                                Spacer(Modifier.height(8.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.AutoMode, null, tint = Accent)
                                    Spacer(Modifier.width(8.dp))
                                    Text("جاهز لتنظيم مهامك", fontWeight = FontWeight.SemiBold)
                                }
                                Spacer(Modifier.height(12.dp))
                                Text(if (dndAllowed) "إذن عدم الإزعاج: مفعّل" else "إذن عدم الإزعاج: يحتاج تفعيل من إعدادات أندرويد",
                                    color = if (dndAllowed) Color(0xFF72D6A0) else Color(0xFFFFC16B), fontSize = 12.sp)
                                if (!dndAllowed) {
                                    TextButton(onClick = { activity.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) }) {
                                        Text("تفعيل إذن عدم الإزعاج")
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(18.dp))
                        Text("اختصارات سريعة", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            QuickTile("عدم الإزعاج", Icons.Default.DoNotDisturbOn, Modifier.weight(1f)) {
                                if (!dndAllowed) activity.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                                else nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
                            }
                            QuickTile("التطبيقات", Icons.Default.Apps, Modifier.weight(1f)) { tab = 2 }
                        }
                        Spacer(Modifier.height(20.dp))
                        Button(onClick = { showCreate = true }, modifier = Modifier.fillMaxWidth().height(54.dp),
                            shape = RoundedCornerShape(16.dp)) {
                            Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("إنشاء مهمة مجدولة")
                        }
                    }
                    1 -> {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("المهام المجدولة", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                            IconButton(onClick = { showCreate = true }) { Icon(Icons.Default.Add, null, tint = Accent) }
                        }
                        tasks.forEach { task ->
                            Card(Modifier.fillMaxWidth().padding(vertical = 6.dp), colors = CardDefaults.cardColors(containerColor = Panel)) {
                                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Schedule, null, tint = Accent)
                                    Spacer(Modifier.width(12.dp))
                                    Text(task, Modifier.weight(1f))
                                }
                            }
                        }
                        if (tasks.isEmpty()) Text("لسه مفيش مهام. أنشئ أول مهمة ليك.", color = Muted)
                    }
                    else -> {
                        Text("التطبيقات", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Text("التطبيقات المثبتة على جهازك", color = Muted, fontSize = 12.sp)
                        Spacer(Modifier.height(12.dp))
                        val apps = remember {
                            activity.packageManager.getInstalledApplications(0)
                                .filter { activity.packageManager.getLaunchIntentForPackage(it.packageName) != null }
                                .sortedBy { activity.packageManager.getApplicationLabel(it).toString().lowercase() }
                        }
                        LazyColumn {
                            items(apps) { app ->
                                Row(Modifier.fillMaxWidth().clickable {
                                    activity.packageManager.getLaunchIntentForPackage(app.packageName)?.let(activity::startActivity)
                                }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    androidx.compose.foundation.Image(
                                        bitmap = android.graphics.drawable.Drawable.createFromStream(
                                            null, null
                                        )?.let { androidx.compose.ui.graphics.asImageBitmap() } ?: androidx.compose.ui.graphics.ImageBitmap(1,1),
                                        contentDescription = null, modifier = Modifier.size(1.dp)
                                    )
                                    Text(activity.packageManager.getApplicationLabel(app).toString(), Modifier.weight(1f))
                                    Icon(Icons.Default.OpenInNew, null, tint = Muted)
                                }
                                Divider(color = Panel)
                            }
                        }
                    }
                }
            }
        }
        if (showCreate) {
            CreateTaskDialog(onDismiss = { showCreate = false }, onCreate = { label, hour, minute, enabled ->
                tasks = tasks + "$label — %02d:%02d".format(hour, minute)
                scheduleDnd(activity, hour, minute, enabled)
                showCreate = false
            })
        }
    }
}

@Composable
private fun QuickTile(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Card(modifier.clickable(onClick = onClick), colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = Accent, modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(8.dp)); Text(title, fontSize = 13.sp)
        }
    }
}

@Composable
private fun CreateTaskDialog(onDismiss: () -> Unit, onCreate: (String, Int, Int, Boolean) -> Unit) {
    var action by remember { mutableStateOf(true) }
    var hour by remember { mutableIntStateOf(3) }
    var minute by remember { mutableIntStateOf(0) }
    AlertDialog(onDismissRequest = onDismiss, containerColor = Panel,
        title = { Text("مهمة جديدة") },
        text = {
            Column {
                Text("اختار الإجراء والوقت")
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(selected = action, onClick = { action = true }, label = { Text("تشغيل") })
                    Spacer(Modifier.width(8.dp))
                    FilterChip(selected = !action, onClick = { action = false }, label = { Text("إيقاف") })
                }
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = { /* time picker is opened by host callback below */ }) {
                    Text("%02d:%02d".format(hour, minute))
                }
                Text("يمكن ضبط الوقت من منتقي الوقت في الإصدار التالي.", color = Muted, fontSize = 11.sp)
            }
        },
        confirmButton = { TextButton(onClick = { onCreate(if (action) "تشغيل عدم الإزعاج" else "إيقاف عدم الإزعاج", hour, minute, action) }) { Text("حفظ") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

private fun scheduleDnd(context: Context, hour: Int, minute: Int, enabled: Boolean) {
    val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val intent = Intent(context, DndAlarmReceiver::class.java).putExtra("enabled", enabled)
    val requestCode = (hour * 60 + minute) + if (enabled) 10000 else 20000
    val pending = PendingIntent.getBroadcast(context, requestCode, intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    val cal = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
    }
    alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pending)
}

class DndAlarmReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!nm.isNotificationPolicyAccessGranted) return
        val enabled = intent.getBooleanExtra("enabled", true)
        nm.setInterruptionFilter(if (enabled) NotificationManager.INTERRUPTION_FILTER_PRIORITY else NotificationManager.INTERRUPTION_FILTER_ALL)
    }
}
