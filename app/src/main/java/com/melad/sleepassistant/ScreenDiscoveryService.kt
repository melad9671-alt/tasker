package com.melad.sleepassistant
import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import org.json.JSONArray
import org.json.JSONObject
class ScreenDiscoveryService:AccessibilityService(){
 override fun onAccessibilityEvent(event:AccessibilityEvent?){
  val root=rootInActiveWindow?:return
  val pkg=event?.packageName?.toString()?:root.packageName?.toString()?:return
  if(pkg==packageName)return
  val found=LinkedHashMap<String,JSONObject>();collect(root,found,0);val arr=JSONArray();found.values.forEach{arr.put(it)}
  getSharedPreferences("discovery",0).edit().putString(pkg,arr.toString()).apply()
  val prefs=getSharedPreferences("automation_runtime",0);val pending=prefs.getString("steps",null)?:return
  val steps=pending.split("||").filter{it.isNotBlank()};if(steps.isEmpty()){prefs.edit().remove("steps").apply();return}
  val target=prefs.getString("package",null);if(target!=null&&target!=pkg)return
  if(clickMatch(root,steps.first())){val rest=steps.drop(1);prefs.edit().putString("steps",rest.joinToString("||")).apply();if(rest.isEmpty())prefs.edit().remove("package").apply()}
 }
 private fun collect(n:AccessibilityNodeInfo,out:LinkedHashMap<String,JSONObject>,depth:Int){
  if(depth>18||out.size>=160)return
  val t=n.text?.toString()?.trim().orEmpty();val d=n.contentDescription?.toString()?.trim().orEmpty();val label=if(t.isNotBlank())t else d
  if(label.isNotBlank()&&label.length<=100){val id=n.viewIdResourceName.orEmpty();val key="$label|$id|${n.className}";out[key]=JSONObject().put("label",label).put("id",id).put("class",n.className?.toString().orEmpty()).put("clickable",n.isClickable)}
  for(i in 0 until n.childCount){val child=n.getChild(i)?:continue;collect(child,out,depth+1);child.recycle()}
 }
 private fun clickMatch(root:AccessibilityNodeInfo,label:String):Boolean{
  for(node in root.findAccessibilityNodeInfosByText(label)){var cur:AccessibilityNodeInfo?=node
   while(cur!=null){if(cur.isClickable&&cur.isEnabled){val ok=cur.performAction(AccessibilityNodeInfo.ACTION_CLICK);node.recycle();return ok};cur=cur.parent}
   node.recycle()
  };return false
 }
 override fun onInterrupt(){}
}