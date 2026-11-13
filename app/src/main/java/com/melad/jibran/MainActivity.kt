package com.melad.jibran

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(42), dp(24), dp(24))
            setBackgroundColor(Color.rgb(7, 8, 16))
        }
        root.addView(TextView(this).apply {
            text = "DYNAMIC ISLAND"
            textSize = 25f
            setTextColor(Color.WHITE)
            letterSpacing = .12f
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-1, dp(60)))
        root.addView(TextView(this).apply {
            text = "كبسولة ذكية صغيرة لشاشة Redmi Note 10S"
            textSize = 14f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-1, dp(46)))
        val status = TextView(this).apply {
            text = "الحالة: جاهز للإعداد"
            textSize = 14f
            setTextColor(Color.rgb(130, 220, 190))
            gravity = Gravity.CENTER
        }
        root.addView(status, LinearLayout.LayoutParams(-1, dp(58)))
        fun addButton(label: String, action: () -> Unit) {
            root.addView(Button(this).apply {
                text = label
                setOnClickListener { action() }
            }, LinearLayout.LayoutParams(-1, dp(54)).apply { bottomMargin = dp(12) })
        }
        addButton("١. السماح بالظهور فوق التطبيقات") {
            if (!Settings.canDrawOverlays(this)) startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            else Toast.makeText(this, "الصلاحية مفعّلة بالفعل", Toast.LENGTH_SHORT).show()
        }
        addButton("٢. تفعيل الوصول للإشعارات") { startActivity(Intent(ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        addButton("تشغيل Dynamic Island") {
            if (!Settings.canDrawOverlays(this)) Toast.makeText(this, "فعّل صلاحية الظهور فوق التطبيقات أولاً", Toast.LENGTH_LONG).show()
            else {
                val i = Intent(this, IslandOverlayService::class.java)
                if (Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)
                status.text = "الحالة: الكبسولة تعمل"
            }
        }
        addButton("إيقاف Dynamic Island") {
            stopService(Intent(this, IslandOverlayService::class.java))
            status.text = "الحالة: متوقفة"
        }
        root.addView(TextView(this).apply {
            text = "الإشعارات والتحكم في الموسيقى يحتاجان تفعيل صلاحية الوصول للإشعارات من إعدادات الهاتف. منطقة اللمس حول الكبسولة مخفية."
            textSize = 12f
            setTextColor(Color.GRAY)
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-1, dp(100)))
        setContentView(root)
    }
}
