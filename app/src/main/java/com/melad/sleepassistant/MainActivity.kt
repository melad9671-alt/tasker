package com.melad.sleepassistant
import android.app.*
import android.content.*
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

private val BG=Color(0xFF080D18); private val PANEL=Color(0xFF121B2B); private val ACCENT=Color(0xFF8B7CFF); private val FG=Color(0xFFF4F6FC); private val MUTED=Color(0xFF98A6BD)
data class AppItem(val label:String,val pkg:String,val system:Boolean)
data class ActionItem(val label:String,val id:String,val cls:String,val clickable:Boolean)
class MainActivity:ComponentActivity(){override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);setContent{AppUI(this)}}}

@Composable private fun AppUI(activity:MainActivity){
 var page by remember{mutableIntStateOf(0)}; var selected by remember{mutableStateOf<AppItem?>(null)}; var search by remember{mutableStateOf("")}; var refresh by remember{mutableIntStateOf(0)}
 var picked by remember{mutableStateOf(listOf<ActionItem>())}; var tasks by remember{mutableStateOf(readTasks(activity))}; var schedule by remember{mutableStateOf(false)}
 val apps=remember(refresh){activity.packageManager.getInstalledApplications(0).filter{it.packageName!=activity.packageName}.map{AppItem(activity.packageManager.getApplicationLabel(it).toString(),it.packageName,(it.flags and ApplicationInfo.FLAG_SYSTEM)!=0)}.distinctBy{it.pkg}.sortedBy{it.label.lowercase()}}
 val actions=remember(refresh,selected){selected?.let{readActions(activity,it.pkg)}?:emptyList()}
 val enabled=remember(refresh){serviceEnabled(activity)}
 MaterialTheme(colorScheme=darkColorScheme(primary=ACCENT,background=BG,surface=PANEL)){
 Scaffold(containerColor=BG,bottomBar={NavigationBar(containerColor=PANEL){
  NavigationBarItem(page==0,{page=0;selected=null},{Icon(Icons.Default.Dashboard,null)},label={Text("Home")})
  NavigationBarItem(page==1,{page=1;selected=null},{Icon(Icons.Default.Apps,null)},label={Text("Apps")})
  NavigationBarItem(page==2,{page=2;selected=null},{Icon(Icons.Default.AccountTree,null)},label={Text("Automations")})
  NavigationBarItem(page==3,{page=3;selected=null},{Icon(Icons.Default.Tune,null)},label={Text("Settings")})
 }}){pad->Column(Modifier.fillMaxSize().padding(pad).padding(horizontal=17.dp)){
  Spacer(Modifier.height(15.dp));Row(verticalAlignment=Alignment.CenterVertically){
   Box(Modifier.size(42.dp).background(ACCENT.copy(alpha=.18f),RoundedCornerShape(13.dp)),contentAlignment=Alignment.Center){Icon(Icons.Default.AutoAwesome,null,tint=ACCENT)}
   Spacer(Modifier.width(10.dp));Column{Text("SLEEPLESS",color=FG,fontWeight=FontWeight.Black,letterSpacing=2.sp);Text("SMART PHONE AUTOMATION",color=MUTED,fontSize=9.sp,letterSpacing=1.sp)}
   Spacer(Modifier.weight(1f));IconButton(onClick={refresh++}){Icon(Icons.Default.Refresh,null,tint=MUTED)}
  };Spacer(Modifier.height(18.dp))
  when{
   selected!=null->{
    Row(verticalAlignment=Alignment.CenterVertically){IconButton(onClick={selected=null;refresh++}){Icon(Icons.Default.ArrowBack,null,tint=FG)}
     Column(Modifier.weight(1f)){Text(selected!!.label,color=FG,fontSize=21.sp,fontWeight=FontWeight.Bold);Text(selected!!.pkg,color=MUTED,fontSize=9.sp)}
     IconButton(onClick={openApp(activity,selected!!)}){Icon(Icons.Default.Explore,null,tint=ACCENT)}
    }
    Card(colors=CardDefaults.cardColors(containerColor=PANEL),shape=RoundedCornerShape(17.dp)){Column(Modifier.padding(14.dp)){
     Text("DISCOVER ACTIONS",color=FG,fontWeight=FontWeight.Bold)
     Text("Open the app and navigate through its screens. The Accessibility service discovers visible controls exposed by the app.",color=MUTED,fontSize=11.sp)
     Button(onClick={openApp(activity,selected!!)},modifier=Modifier.fillMaxWidth()){Text("Open & Explore App")}
    }}
    Spacer(Modifier.height(10.dp));Text("Available actions (${actions.size})",color=FG,fontWeight=FontWeight.Bold)
    if(actions.isEmpty())EmptyState("No actions discovered yet","Open the app, visit its screens, then return and refresh.")
    else LazyColumn(verticalArrangement=Arrangement.spacedBy(7.dp)){items(actions){a->
     val checked=picked.any{it.label==a.label&&it.id==a.id}
     Card(Modifier.fillMaxWidth().clickable{picked=if(checked)picked.filterNot{it.label==a.label&&it.id==a.id}else picked+a},colors=CardDefaults.cardColors(containerColor=if(checked)ACCENT.copy(alpha=.2f)else PANEL),shape=RoundedCornerShape(12.dp)){
      Row(Modifier.padding(11.dp),verticalAlignment=Alignment.CenterVertically){Icon(if(a.clickable)Icons.Default.TouchApp else Icons.Default.TextFields,null,tint=ACCENT);Spacer(Modifier.width(9.dp));Column(Modifier.weight(1f)){Text(a.label,color=FG,fontSize=13.sp);if(a.id.isNotBlank())Text(a.id,color=MUTED,fontSize=9.sp,maxLines=1)};Icon(if(checked)Icons.Default.CheckCircle else Icons.Default.AddCircleOutline,null,tint=ACCENT)}
     }
    }}
    if(picked.isNotEmpty())Button(onClick={page=2},modifier=Modifier.fillMaxWidth()){Text("Add ${picked.size} selected actions")}
   }
   page==0->{
    Text("Your phone, automated.",color=FG,fontSize=27.sp,fontWeight=FontWeight.Bold);Text("Build routines from actions available across your apps.",color=MUTED,fontSize=13.sp)
    Spacer(Modifier.height(18.dp));Card(colors=CardDefaults.cardColors(containerColor=PANEL),shape=RoundedCornerShape(19.dp)){Column(Modifier.padding(18.dp)){
     Text("AUTOMATION OVERVIEW",color=MUTED,fontSize=10.sp,letterSpacing=1.sp);Text("${apps.size} installed apps",color=FG,fontSize=23.sp,fontWeight=FontWeight.Bold);Text("${tasks.size} saved automations",color=MUTED,fontSize=12.sp)
     Button(onClick={page=1},modifier=Modifier.fillMaxWidth()){Text("Explore installed apps")}
    }}
    Spacer(Modifier.height(12.dp));Row(horizontalArrangement=Arrangement.spacedBy(9.dp)){HomeTile("Apps",Icons.Default.Apps,Modifier.weight(1f)){page=1};HomeTile("Automations",Icons.Default.AccountTree,Modifier.weight(1f)){page=2}}
   }
   page==1->{
    Text("Installed apps",color=FG,fontSize=25.sp,fontWeight=FontWeight.Bold);Text("System and downloaded apps",color=MUTED,fontSize=12.sp);Spacer(Modifier.height(10.dp))
    Row(Modifier.fillMaxWidth().background(PANEL,RoundedCornerShape(13.dp)).padding(12.dp),verticalAlignment=Alignment.CenterVertically){Icon(Icons.Default.Search,null,tint=MUTED);Spacer(Modifier.width(8.dp));BasicTextField(search,{search=it},Modifier.weight(1f),singleLine=true,textStyle=androidx.compose.ui.text.TextStyle(color=FG),decorationBox={inner->if(search.isEmpty())Text("Search apps...",color=MUTED);inner()})}
    Spacer(Modifier.height(8.dp));LazyVerticalGrid(columns=GridCells.Fixed(3),contentPadding=PaddingValues(bottom=14.dp),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
     items(apps.filter{it.label.contains(search,true)||it.pkg.contains(search,true)}){app->Card(Modifier.fillMaxWidth().clickable{selected=app},colors=CardDefaults.cardColors(containerColor=PANEL),shape=RoundedCornerShape(14.dp)){
      Column(Modifier.fillMaxWidth().padding(vertical=12.dp,horizontal=4.dp),horizontalAlignment=Alignment.CenterHorizontally){Box(Modifier.size(37.dp).background(ACCENT.copy(alpha=.18f),RoundedCornerShape(12.dp)),contentAlignment=Alignment.Center){Text(app.label.take(1).uppercase(),color=ACCENT,fontWeight=FontWeight.Bold,fontSize=18.sp)};Spacer(Modifier.height(6.dp));Text(app.label,color=FG,fontSize=10.sp,maxLines=1);Text(if(app.system)"SYSTEM"else"APP",color=MUTED,fontSize=8.sp)}
     }}
    }
   }
   page==2->{
    Text("Automation builder",color=FG,fontSize=25.sp,fontWeight=FontWeight.Bold);Text("Connect discovered actions into a routine.",color=MUTED,fontSize=12.sp);Spacer(Modifier.height(12.dp))
    Card(colors=CardDefaults.cardColors(containerColor=PANEL),shape=RoundedCornerShape(17.dp)){Column(Modifier.padding(14.dp)){
     Text("WORKFLOW STEPS",color=MUTED,fontSize=10.sp,letterSpacing=1.sp)
     if(picked.isEmpty())Text("Select an app, explore its screens, then add actions here.",color=MUTED,fontSize=12.sp)
     picked.forEachIndexed{index,a->Row(verticalAlignment=Alignment.CenterVertically){Text("${index+1}.",color=ACCENT);Spacer(Modifier.width(9.dp));Text(a.label,Modifier.weight(1f),color=FG,fontSize=12.sp);IconButton(onClick={picked=picked.filterIndexed{n,_->n!=index}}){Icon(Icons.Default.Close,null,tint=MUTED)}}}
     OutlinedButton(onClick={page=1;selected=null},modifier=Modifier.fillMaxWidth()){Text("Add actions from apps")}
     Button(onClick={schedule=true},enabled=picked.isNotEmpty(),modifier=Modifier.fillMaxWidth()){Text("Schedule automation")}
    }}
    Spacer(Modifier.height(12.dp));Text("SAVED AUTOMATIONS",color=MUTED,fontSize=11.sp,fontWeight=FontWeight.Bold)
    if(tasks.isEmpty())EmptyState("No automations yet","Your saved routines will appear here.") else LazyColumn{items(tasks){t->Card(colors=CardDefaults.cardColors(containerColor=PANEL),shape=RoundedCornerShape(12.dp)){Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically){Icon(Icons.Default.Alarm,null,tint=ACCENT);Spacer(Modifier.width(9.dp));Column{Text(t.optString("name"),color=FG);Text(t.optString("time"),color=MUTED,fontSize=11.sp)}}}}}
   }
   else->{
    Text("Settings & permissions",color=FG,fontSize=25.sp,fontWeight=FontWeight.Bold);Text("Android access required for app automation.",color=MUTED,fontSize=12.sp);Spacer(Modifier.height(12.dp))
    Card(colors=CardDefaults.cardColors(containerColor=PANEL),shape=RoundedCornerShape(17.dp)){Column(Modifier.padding(15.dp)){
     Row(verticalAlignment=Alignment.CenterVertically){Icon(Icons.Default.AccessibilityNew,null,tint=ACCENT);Spacer(Modifier.width(9.dp));Column(Modifier.weight(1f)){Text("Accessibility Service",color=FG,fontWeight=FontWeight.Bold);Text(if(enabled)"Enabled"else"Required for screen discovery",color=if(enabled)Color(0xFF70D9A1)else MUTED,fontSize=11.sp)}}
     Button(onClick={activity.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))},modifier=Modifier.fillMaxWidth()){Text("Open Accessibility Settings")}
    }}
    Spacer(Modifier.height(10.dp));Text("Only controls exposed to Android Accessibility can be discovered. Secure screens and app-private operations are unavailable.",color=MUTED,fontSize=12.sp)
   }
  }
 }}
 }
 if(schedule)AlertDialog(onDismissRequest={schedule=false},containerColor=PANEL,title={Text("Schedule automation",color=FG)},text={Text("Choose when to run these actions. Android may require exact-alarm permission and the Accessibility service must be enabled.",color=MUTED)},confirmButton={TextButton(onClick={
  TimePickerDialog(activity,{_,h,m->
   val cal=Calendar.getInstance().apply{set(Calendar.HOUR_OF_DAY,h);set(Calendar.MINUTE,m);set(Calendar.SECOND,0);if(timeInMillis<=System.currentTimeMillis())add(Calendar.DAY_OF_YEAR,1)}
   val pkg=selected?.pkg.orEmpty()
   if(pkg.isNotBlank()){val intent=Intent(activity,AutomationAlarmReceiver::class.java).putExtra("package",pkg).putExtra("steps",picked.joinToString("||"){it.label});val pi=PendingIntent.getBroadcast(activity,h*60+m,intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE);val alarm=activity.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    if(android.os.Build.VERSION.SDK_INT>=31&&!alarm.canScheduleExactAlarms())activity.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)) else alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,cal.timeInMillis,pi)
   }
   tasks=tasks+JSONObject().put("name",picked.joinToString(" → "){it.label}).put("time","%02d:%02d".format(h,m));saveTasks(activity,tasks);picked=emptyList();schedule=false
  },9,0,true).show()
 }){Text("Choose time")},dismissButton={TextButton(onClick={schedule=false}){Text("Cancel")}})
}
@Composable private fun HomeTile(label:String,icon:androidx.compose.ui.graphics.vector.ImageVector,modifier:Modifier,onClick:()->Unit){Card(modifier.clickable(onClick=onClick),colors=CardDefaults.cardColors(containerColor=PANEL),shape=RoundedCornerShape(14.dp)){Column(Modifier.fillMaxWidth().padding(15.dp),horizontalAlignment=Alignment.CenterHorizontally){Icon(icon,null,tint=ACCENT);Text(label,color=FG,fontSize=12.sp)}}}
@Composable private fun EmptyState(title:String,detail:String){Column(Modifier.fillMaxWidth().padding(23.dp),horizontalAlignment=Alignment.CenterHorizontally){Icon(Icons.Default.ExploreOff,null,tint=MUTED);Text(title,color=FG);Text(detail,color=MUTED,fontSize=11.sp)}}
private fun openApp(c:Context,a:AppItem){c.packageManager.getLaunchIntentForPackage(a.pkg)?.let{c.startActivity(it)}}
private fun readActions(c:Context,p:String):List<ActionItem>{val a=JSONArray(c.getSharedPreferences("discovery",0).getString(p,"[]"));return(0 until a.length()).map{val o=a.getJSONObject(it);ActionItem(o.optString("label"),o.optString("id"),o.optString("class"),o.optBoolean("clickable"))}}
private fun readTasks(c:Context):List<JSONObject>{val a=JSONArray(c.getSharedPreferences("automations",0).getString("tasks","[]"));return(0 until a.length()).map{a.getJSONObject(it)}}
private fun saveTasks(c:Context,t:List<JSONObject>){val a=JSONArray();t.forEach{a.put(it)};c.getSharedPreferences("automations",0).edit().putString("tasks",a.toString()).apply()}
private fun serviceEnabled(c:Context):Boolean{val key=c.packageName+"/"+ScreenDiscoveryService::class.java.name;val v=Settings.Secure.getString(c.contentResolver,Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)?:return false;return v.split(':').any{it.equals(key,true)}}
