package com.melad.sleepassistant
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
class AutomationAlarmReceiver:BroadcastReceiver(){
 override fun onReceive(context:Context,intent:Intent){
  val pkg=intent.getStringExtra("package").orEmpty();val steps=intent.getStringExtra("steps").orEmpty();if(pkg.isBlank()||steps.isBlank())return
  context.getSharedPreferences("automation_runtime",0).edit().putString("package",pkg).putString("steps",steps).apply()
  val launch=context.packageManager.getLaunchIntentForPackage(pkg)?:return;launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);context.startActivity(launch)
 }
}