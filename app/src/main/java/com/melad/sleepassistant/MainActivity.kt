package com.melad.sleepassistant
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.DisposableEffect
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

private val bg=Color(0xFF080D18)
private val panel=Color(0xFF121B2B)
private val accent=Color(0xFF8B7CFF)
private val fg=Color(0xFFF4F6FC)
private val muted=Color(0xFF98A6BD)
data class AppItem(val label:String,val pkg:String)
data class ActionItem(val label:String,val id:String,val cls:String,val clickable:Boolean)
class MainActivity:ComponentActivity(){override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);setContent{Sleepless(this)}}}

@Composable private fun Sleepless(activity:MainActivity){
 var tab by remember{mutableIntStateOf(0)}
 var selected by remember{mutableStateOf<AppItem?>(null)}
 var search by remember{mutableStateOf("")}
 var refresh by remember{mutableIntStateOf(0)}
 var chosen by remember{mutableStateOf(listOf<ActionItem>())}
 var chosenPackage by remember{mutableStateOf<String?>(null)}
 DisposableEffect(activity){ val observer=LifecycleEventObserver{_,event->if(event==Lifecycle.Event.ON_RESUME)refresh++};activity.lifecycle.addObserver(observer);onDispose{activity.lifecycle.removeObserver(observer)} }
 var tasks by remember{mutableStateOf(readTasks(activity))}
 val apps=remember(refresh){ val pm=activity.packageManager; val launcher=Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER); pm.queryIntentActivities(launcher,PackageManager.MATCH_ALL).mapNotNull{ri->val pkg=ri.activityInfo?.packageName?:return@mapNotNull null;if(pkg==activity.packageName)return@mapNotNull null;AppItem(ri.loadLabel(pm).toString(),pkg)}.distinctBy{it.pkg}.sortedBy{it.label.lowercase()} }
 val actions=remember(refresh,selected){selected?.let{readActions(activity,it.pkg)}?:emptyList()}
 val enabled=remember(refresh){isEnabled(activity)}
 MaterialTheme(colorScheme=darkColorScheme(primary=accent,onPrimary=Color(0xFFFFFFFF),secondary=Color(0xFF65B8FF),onSecondary=Color(0xFF071426),tertiary=Color(0xFFFFC078),background=bg,onBackground=fg,surface=panel,onSurface=fg,surfaceVariant=Color(0xFF202B40),onSurfaceVariant=muted)){
  Scaffold(containerColor=bg,bottomBar={NavigationBar(containerColor=panel){
   NavigationBarItem(selected=tab==0,onClick={tab=0;selected=null},icon={Icon(Icons.Default.Home,null)},label={Text("Home")})
   NavigationBarItem(selected=tab==1,onClick={tab=1;selected=null},icon={Icon(Icons.Default.Apps,null)},label={Text("Apps")})
   NavigationBarItem(selected=tab==2,onClick={tab=2;selected=null},icon={Icon(Icons.Default.AccountTree,null)},label={Text("Automations")})
   NavigationBarItem(selected=tab==3,onClick={tab=3;selected=null},icon={Icon(Icons.Default.Settings,null)},label={Text("Settings")})
  }}){padding->
   Column(Modifier.fillMaxSize().padding(padding).padding(horizontal=16.dp)){
    Spacer(Modifier.height(14.dp))
    Row(verticalAlignment=Alignment.CenterVertically){
     Icon(Icons.Default.AutoAwesome,null,tint=accent,modifier=Modifier.size(30.dp))
     Spacer(Modifier.width(10.dp))
     Column{Text("SLEEPLESS",color=fg,fontWeight=FontWeight.Black,letterSpacing=2.sp);Text("SMART PHONE AUTOMATION",color=muted,fontSize=9.sp)}
     Spacer(Modifier.weight(1f))
     IconButton(onClick={refresh++}){Icon(Icons.Default.Refresh,null,tint=muted)}
    }
    Spacer(Modifier.height(14.dp))
    if(selected!=null){
     Row(verticalAlignment=Alignment.CenterVertically){
      IconButton(onClick={selected=null;refresh++}){Icon(Icons.Default.ArrowBack,null,tint=fg)}
      Column(Modifier.weight(1f)){Text(selected!!.label,color=fg,fontSize=21.sp,fontWeight=FontWeight.Bold);Text(selected!!.pkg,color=muted,fontSize=9.sp)}
      IconButton(onClick={activity.packageManager.getLaunchIntentForPackage(selected!!.pkg)?.let{activity.startActivity(it)}}){Icon(Icons.Default.OpenInNew,null,tint=accent)}
     }
     Card(colors=CardDefaults.cardColors(containerColor=panel)){Column(Modifier.padding(14.dp)){
      Text("EXPLORE THIS APP",color=fg,fontWeight=FontWeight.Bold)
      Text("افتح التطبيق وتنقّل بين شاشاته؛ العناصر القابلة للتفاعل التي يتيحها Android ستظهر هنا عند الرجوع.",color=muted,fontSize=11.sp)
      Button(onClick={activity.packageManager.getLaunchIntentForPackage(selected!!.pkg)?.let{activity.startActivity(it)}},modifier=Modifier.fillMaxWidth()){Text("Open app")}
      OutlinedButton(onClick={refresh++},modifier=Modifier.fillMaxWidth()){Text("Refresh discovered actions")}
     }}
     Spacer(Modifier.height(10.dp))
     Text("DISCOVERED ACTIONS ("+actions.size+")",color=fg,fontWeight=FontWeight.Bold)
     if(actions.isEmpty())Text("No controls found yet. Open the app, visit screens, then return and refresh.",color=muted,fontSize=12.sp,modifier=Modifier.padding(14.dp))
     else LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)){
      items(actions){a->
       val checked=chosen.contains(a)
       Card(Modifier.fillMaxWidth().clickable{chosen=if(checked)chosen.filterNot{it==a}else chosen+a},colors=CardDefaults.cardColors(containerColor=if(checked)accent.copy(alpha=.2f)else panel)){
        Row(Modifier.padding(11.dp),verticalAlignment=Alignment.CenterVertically){
         Icon(if(a.clickable)Icons.Default.TouchApp else Icons.Default.TextFields,null,tint=accent)
         Spacer(Modifier.width(9.dp))
         Column(Modifier.weight(1f)){Text(a.label,color=fg,fontSize=13.sp);if(a.id.isNotBlank())Text(a.id,color=muted,fontSize=9.sp)}
         Icon(if(checked)Icons.Default.CheckCircle else Icons.Default.AddCircleOutline,null,tint=accent)
         if(a.clickable) TextButton(onClick={activity.getSharedPreferences("automation_runtime",0).edit().putString("click_package",selected!!.pkg).putString("click_label",a.label).apply();activity.packageManager.getLaunchIntentForPackage(selected!!.pkg)?.let{activity.startActivity(it)}}){Text("Run")}
        }
       }
      }
     }
     if(chosen.isNotEmpty())Button(onClick={tab=2},modifier=Modifier.fillMaxWidth()){Text("Add "+chosen.size+" selected actions")}
    }else when(tab){
     0->{
      Text("Your phone, automated.",color=fg,fontSize=26.sp,fontWeight=FontWeight.Bold)
      Text("Build routines from actions inside your apps.",color=muted,fontSize=13.sp)
      Spacer(Modifier.height(16.dp))
      Card(colors=CardDefaults.cardColors(containerColor=panel)){Column(Modifier.padding(17.dp)){
       Text("AUTOMATION OVERVIEW",color=muted,fontSize=10.sp)
       Text(apps.size.toString()+" installed apps",color=fg,fontSize=23.sp,fontWeight=FontWeight.Bold)
       Text(tasks.size.toString()+" saved automations",color=muted,fontSize=12.sp)
       Button(onClick={tab=1},modifier=Modifier.fillMaxWidth()){Text("Explore installed apps")}
      }}
     }
     1->{
      Text("Installed apps",color=fg,fontSize=25.sp,fontWeight=FontWeight.Bold)
      Text("Apps visible in your app drawer",color=muted,fontSize=12.sp)
      Spacer(Modifier.height(10.dp))
      Row(Modifier.fillMaxWidth().background(panel,RoundedCornerShape(12.dp)).padding(12.dp),verticalAlignment=Alignment.CenterVertically){
       Icon(Icons.Default.Search,null,tint=muted);Spacer(Modifier.width(8.dp))
       BasicTextField(value=search,onValueChange={search=it},modifier=Modifier.weight(1f),singleLine=true,textStyle=androidx.compose.ui.text.TextStyle(color=fg),decorationBox={inner->if(search.isEmpty())Text("Search apps...",color=muted);inner()})
      }
      Spacer(Modifier.height(8.dp))
      LazyColumn(verticalArrangement=Arrangement.spacedBy(6.dp)){
       items(apps.filter{it.label.contains(search,true)||it.pkg.contains(search,true)}){app->
        Card(Modifier.fillMaxWidth().clickable{selected=app;chosenPackage=app.pkg},colors=CardDefaults.cardColors(containerColor=panel)){
         Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically){
          Image(bitmap=remember(app.pkg){activity.packageManager.getApplicationIcon(app.pkg).toBitmap(96,96).asImageBitmap()},contentDescription=app.label,modifier=Modifier.size(42.dp),contentScale=ContentScale.Fit)
          Spacer(Modifier.width(10.dp))
          Column(Modifier.weight(1f)){Text(app.label,color=fg);Text(app.pkg,color=muted,fontSize=9.sp)}
          Icon(Icons.Default.ChevronRight,null,tint=muted)
         }
        }
       }
      }
     }
     2->{
      Text("Automation builder",color=fg,fontSize=25.sp,fontWeight=FontWeight.Bold)
      Text("Combine actions into a scheduled routine.",color=muted,fontSize=12.sp)
      Spacer(Modifier.height(10.dp))
      Card(colors=CardDefaults.cardColors(containerColor=panel)){Column(Modifier.padding(14.dp)){
       Text("WORKFLOW STEPS",color=muted,fontSize=10.sp)
       if(chosen.isEmpty())Text("Select actions from an app to build a routine.",color=muted,fontSize=12.sp)
       chosen.forEachIndexed{index,item->Row(verticalAlignment=Alignment.CenterVertically){
        Text((index+1).toString()+".",color=accent);Spacer(Modifier.width(8.dp));Text(item.label,Modifier.weight(1f),color=fg,fontSize=12.sp)
        IconButton(onClick={chosen=chosen.filterNot{it==item}}){Icon(Icons.Default.Close,null,tint=muted)}
       }}
       OutlinedButton(onClick={tab=1},modifier=Modifier.fillMaxWidth()){Text("Choose app actions")}
       Button(onClick={
        val target=chosenPackage
        if(target!=null&&chosen.isNotEmpty()){
         TimePickerDialog(activity,{_,hour,minute->
          val cal=Calendar.getInstance().apply{set(Calendar.HOUR_OF_DAY,hour);set(Calendar.MINUTE,minute);set(Calendar.SECOND,0);if(timeInMillis<=System.currentTimeMillis())add(Calendar.DAY_OF_YEAR,1)}
          val intent=Intent(activity,AutomationAlarmReceiver::class.java).putExtra("package",target).putExtra("steps",chosen.joinToString("||"){it.label})
          val pi=PendingIntent.getBroadcast(activity,hour*60+minute,intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
          val alarm=activity.getSystemService(Context.ALARM_SERVICE) as AlarmManager
          if(android.os.Build.VERSION.SDK_INT>=31&&!alarm.canScheduleExactAlarms())activity.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
          else alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,cal.timeInMillis,pi)
          tasks=tasks+JSONObject().put("name",chosen.joinToString(" → "){it.label}).put("time","%02d:%02d".format(hour,minute)).put("package",target)
          saveTasks(activity,tasks);chosen=emptyList()
         },9,0,true).show()
        }
       },enabled=chosen.isNotEmpty()&&chosenPackage!=null,modifier=Modifier.fillMaxWidth()){Text("Schedule routine")}
      }}
      Spacer(Modifier.height(12.dp));Text("SAVED AUTOMATIONS",color=muted,fontSize=11.sp,fontWeight=FontWeight.Bold)
      LazyColumn(verticalArrangement=Arrangement.spacedBy(6.dp)){items(tasks){t->Card(colors=CardDefaults.cardColors(containerColor=panel)){Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically){Icon(Icons.Default.Alarm,null,tint=accent);Spacer(Modifier.width(8.dp));Column{Text(t.optString("name"),color=fg);Text(t.optString("time"),color=muted,fontSize=11.sp)}}}}}
     }
     else->{
      Text("Settings & permissions",color=fg,fontSize=25.sp,fontWeight=FontWeight.Bold)
      Card(colors=CardDefaults.cardColors(containerColor=panel)){Column(Modifier.padding(14.dp)){
       Text("Accessibility service",color=fg,fontWeight=FontWeight.Bold)
       Text(if(enabled)"Enabled"else"Required to discover app controls",color=muted,fontSize=11.sp)
       Button(onClick={activity.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))},modifier=Modifier.fillMaxWidth()){Text("Open Accessibility settings")}
      }}
      Text("Only controls exposed to Android Accessibility can be discovered. Protected screens may not be accessible.",color=muted,fontSize=12.sp)
     }
    }
   }
  }
 }
}
private fun readActions(c:Context,p:String):List<ActionItem>{val a=JSONArray(c.getSharedPreferences("discovery",0).getString(p,"[]"));return(0 until a.length()).map{val o=a.getJSONObject(it);ActionItem(o.optString("label"),o.optString("id"),o.optString("class"),o.optBoolean("clickable"))}}
private fun readTasks(c:Context):List<JSONObject>{val a=JSONArray(c.getSharedPreferences("automations",0).getString("tasks","[]"));return(0 until a.length()).map{a.getJSONObject(it)}}
private fun saveTasks(c:Context,t:List<JSONObject>){val a=JSONArray();t.forEach{a.put(it)};c.getSharedPreferences("automations",0).edit().putString("tasks",a.toString()).apply()}
private fun isEnabled(c:Context):Boolean{val key=c.packageName+"/"+ScreenDiscoveryService::class.java.name;val value=Settings.Secure.getString(c.contentResolver,Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)?:return false;return value.split(':').any{it.equals(key,true)}}
