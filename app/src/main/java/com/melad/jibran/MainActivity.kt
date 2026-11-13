package com.melad.jibran

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var overlayState: TextView
    private lateinit var notificationState: TextView
    private lateinit var serviceState: TextView

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun shape(color: Int, radius: Int = 20, stroke: Int = Color.TRANSPARENT) =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
            if (stroke != Color.TRANSPARENT) setStroke(dp(1), stroke)
        }

    private fun label(value: String, size: Float, color: Int, bold: Boolean = false) =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) typeface = Typeface.DEFAULT_BOLD
        }

    private fun actionButton(parent: LinearLayout, text: String, primary: Boolean = false, action: () -> Unit) {
        val button = TextView(this).apply {
            this.text = text
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(if (primary) Color.BLACK else Color.WHITE)
            background = shape(if (primary) 0xFFB9F5D0.toInt() else 0xFF242832.toInt(), 16,
                if (primary) Color.TRANSPARENT else 0xFF343A46.toInt())
            setPadding(dp(16), dp(16), dp(16), dp(16))
            setOnClickListener { action() }
        }
        parent.addView(button, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(10) })
    }

    private fun card(parent: LinearLayout, title: String, description: String, state: TextView): LinearLayout {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = shape(0xFF171B24.toInt(), 20, 0xFF282E39.toInt())
            setPadding(dp(18), dp(16), dp(18), dp(16))
        }
        box.addView(label(title, 16f, Color.WHITE, true))
        box.addView(label(description, 12f, 0xFF9DA6B5.toInt()).apply {
            setPadding(0, dp(7), 0, dp(10))
        })
        state.textSize = 12f
        state.setTextColor(0xFFB9F5D0.toInt())
        box.addView(state)
        parent.addView(box, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        return box
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = 0xFF0B0E14.toInt()
        window.navigationBarColor = 0xFF0B0E14.toInt()
        window.decorView.systemUiVisibility = 0

        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(28))
            background = shape(0xFF0B0E14.toInt(), 0)
        }
        val scroll = ScrollView(this).apply { isFillViewport = true }
        scroll.addView(page)

        page.addView(label("DYNAMIC", 12f, 0xFFB9F5D0.toInt(), true))
        page.addView(label("ISLAND", 34f, Color.WHITE, true).apply {
            setPadding(0, dp(2), 0, dp(4))
        })
        page.addView(label("كبسولة ذكية مصممة لشاشة Redmi Note 10S", 13f, 0xFFAAB2C0.toInt()))
        page.addView(label("الإعدادات", 18f, Color.WHITE, true).apply {
            setPadding(0, dp(26), 0, dp(2))
        })

        overlayState = label("", 12f, Color.WHITE)
        notificationState = label("", 12f, Color.WHITE)
        serviceState = label("", 12f, Color.WHITE)
        card(page, "الظهور فوق التطبيقات", "مطلوب لعرض النقطة والكبسولة فوق الشاشة.", overlayState)
        actionButton(page, "تفعيل صلاحية الظهور") {
            if (!Settings.canDrawOverlays(this)) {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            } else Toast.makeText(this, "الصلاحية مفعّلة بالفعل", Toast.LENGTH_SHORT).show()
        }

        card(page, "الوصول للإشعارات والموسيقى", "مطلوب لقراءة معاينة الإشعارات واكتشاف جلسة الموسيقى. لن يعمل بدون موافقتك.", notificationState)
        actionButton(page, "فتح إعدادات الوصول للإشعارات") {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        card(page, "حالة Dynamic Island", "شغّل الخدمة بعد تفعيل صلاحية الظهور. الموسيقى والإشعارات ميزات اختيارية حسب الصلاحيات.", serviceState)
        actionButton(page, "تشغيل Dynamic Island", true) {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "فعّل صلاحية الظهور أولاً", Toast.LENGTH_LONG).show()
            } else {
                val i = Intent(this, IslandOverlayService::class.java)
                if (Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)
                Toast.makeText(this, "تم طلب تشغيل الكبسولة", Toast.LENGTH_SHORT).show()
                refreshStates()
            }
        }
        actionButton(page, "إيقاف Dynamic Island") {
            stopService(Intent(this, IslandOverlayService::class.java))
            refreshStates()
        }
        page.addView(label("ملاحظة: Google Play Protect لا يمنح صلاحيات التطبيق. كل صلاحية يتم تفعيلها من إعدادات أندرويد بموافقتك.", 11f, 0xFF818B9B.toInt()).apply {
            setPadding(2, dp(20), 2, 0)
        })
        setContentView(scroll)
        refreshStates()
    }

    override fun onResume() {
        super.onResume()
        if (::overlayState.isInitialized) refreshStates()
    }

    private fun hasNotificationAccess(): Boolean {
        val enabled = Settings.Secure.getString(contentResolver, "enabled_notification_listeners").orEmpty()
        return enabled.split(':').any { it.equals(componentName.flattenToString(), ignoreCase = true) }
    }

    private fun refreshStates() {
        overlayState.text = if (Settings.canDrawOverlays(this)) "● مفعّلة" else "○ غير مفعّلة"
        notificationState.text = if (hasNotificationAccess()) "● مفعّلة" else "○ غير مفعّلة"
        serviceState.text = if (IslandOverlayService.isRunning) "● الخدمة تعمل" else "○ الخدمة متوقفة"
    }
}
