package com.melad.sleepassistant

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { SleepAssistantApp(this) } }
}

@Composable
private fun SleepAssistantApp(activity: MainActivity) {
    var tab by remember { mutableIntStateOf(0) }
    var showCreate by remember { mutableStateOf(false) }
    var tasks by remember { mutableStateOf(listOf<String>()) }
    val nm = activity.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val dndAllowed = nm.isNotificationPolicyAccessGranted
    MaterialTheme(colorScheme = darkColorScheme(primary = Accent, background = Night, surface = Panel)) {
        Scaffold(containerColor = Night, bottomBar = {
            NavigationBar(containerColor = Panel) {
                NavigationBarItem(tab == 0, { tab = 0 }, icon = { Icon(Icons.Default.Home, null) }, label = { Text("الرئيسية") })
                NavigationBarItem(tab == 1, { tab = 1 }, icon = { Icon(Icons.Default.Schedule, null) }, label = { Text("المهام") })
                NavigationBarItem(tab == 2, { tab = 2 }, icon = { Icon(Icons.Default.Apps, null) }, label = { Text("التطبيقات") })
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
                Spacer(Modifier.height(26.dp))
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
                                    Icon(Icons.Default.AutoMode, null, tint = Accent); Spacer(Modifier.width(8.dp))
                                    Text("جاهز لتنظيم مهامك", fontWeight = FontWeight.SemiBold)
                                }
                                Spacer(Modifier.height(10.dp))
                                Text(if (dndAllowed) "إذن عدم الإزعاج مفعّل" else "يلزم تفعيل إذن عدم الإزعاج من إعدادات أندرويد",
                                    color = if (dndAllowed) Color(0xFF72D6A0) else Color(0xFFFFC16B), fontSize = 12.sp)
                                if (!dndAllowed) TextButton(onClick = { activity.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) }) { Text("فتح إعدادات الإذن") }
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
                        Button(onClick = { showCreate = true }, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp)) {
                            Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("إنشاء مهمة مجدولة")
                        }
                    }
                    1 -> {
                        Text("المهام المجدولة", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(12.dp))
                        if (tasks.isEmpty()) Text("لسه مفيش مهام. أنشئ أول مهمة ليك.", color = Muted)
                        tasks.forEach { task ->
                            Card(Modifier.fillMaxWidth().padding(vertical = 5.dp), colors = CardDefaults.cardColors(containerColor = Panel)) {
                                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Schedule, null, tint = Accent); Spacer(Modifier.width(12.dp)); Text(task)
                                }
                            }
                        }
                        Button(onClick = { showCreate = true }, modifier = Modifier.fillMaxWidth()) { Text("إضافة مهمة") }
                    }
                    else -> {
                        Text("التطبيقات المثبتة", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Text("اضغط على تطبيق لفتحه", color = Muted, fontSize = 12.sp)
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
                                }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Android, null, tint = Accent); Spacer(Modifier.width(12.dp))
                                    Text(activity.packageManager.getApplicationLabel(app).toString(), Modifier.weight(1f))
                                    Icon(Icons.Default.OpenInNew, null, tint = Muted)
                                }
                                HorizontalDivider(color = Panel)
                            }
                        }
                    }
                }
            }
        }
        if (showCreate) CreateTaskDialog(activity, { showCreate = false }) { label, h, m, on ->
            tasks = tasks + "$label — %02d:%02d".format(h, m)
            scheduleDnd(activity, h, m, on)
            showCreate = false
        }
    }
}

@Composable
private fun QuickTile(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Card(modifier.clickable(onClick = onClick), colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = Accent, modifier = Modifier.size(28.dp)); Spacer(Modifier.height(8.dp)); Text(title, fontSize = 13.sp)
        }
    }
}

@Composable
private fun CreateTaskDialog(activity: MainActivity, onDismiss: () -> Unit, onCreate: (String, Int, Int, Boolean) -> Unit) {
    var enabled by remember { mutableStateOf(true) }
    var hour by remember { mutableIntStateOf(3) }
    var minute by remember { mutableIntStateOf(0) }
    AlertDialog(onDismissRequest = onDismiss, containerColor = Panel, title = { Text("مهمة عدم الإزعاج") },
        text = {
            Column {
                Text("حدد الإجراء والوقت"); Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(enabled, { enabled = true }, label = { Text("تشغيل") }); Spacer(Modifier.width(8.dp))
                    FilterChip(!enabled, { enabled = false }, label = { Text("إيقاف") })
                }
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = { TimePickerDialog(activity, { _, h, m -> hour = h; minute = m }, hour, minute, true).show() }) {
                    Text("الوقت: %02d:%02d".format(hour, minute))
                }
            }
        },
        confirmButton = { TextButton(onClick = { onCreate(if (enabled) "تشغيل عدم الإزعاج" else "إيقاف عدم الإزعاج", hour, minute, enabled) }) { Text("حفظ المهمة") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

private fun scheduleDnd(context: Context, hour: Int, minute: Int, enabled: Boolean) {
    val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    if (!alarm.canScheduleExactAlarms()) {
        context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + context.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return
    }
    val intent = Intent(context, DndAlarmReceiver::class.java).putExtra("enabled", enabled)
    val code = hour * 60 + minute + if (enabled) 10000 else 20000
    val pending = PendingIntent.getBroadcast(context, code, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    val cal = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
    }
    alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pending)
}

class DndAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!nm.isNotificationPolicyAccessGranted) return
        nm.setInterruptionFilter(if (intent.getBooleanExtra("enabled", true)) NotificationManager.INTERRUPTION_FILTER_PRIORITY else NotificationManager.INTERRUPTION_FILTER_ALL)
    }
}
