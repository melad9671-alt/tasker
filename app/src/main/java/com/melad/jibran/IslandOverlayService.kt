package com.melad.jibran

import android.app.*
import android.content.*
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.*
import android.widget.*
import androidx.core.app.NotificationCompat

class IslandOverlayService : Service() {
    companion object {
        const val ACTION_NOTIFICATION = "com.melad.jibran.ISLAND_NOTIFICATION"
        private const val CHANNEL = "dynamic_island_service"
        private const val NOTIF_ID = 9301
    }
    private lateinit var wm: WindowManager
    private var root: FrameLayout? = null
    private var card: LinearLayout? = null
    private var titleView: TextView? = null
    private var subView: TextView? = null
    private var expanded = false
    private var currentController: MediaController? = null
    private var notificationTitle = ""
    private var notificationText = ""
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_NOTIFICATION) {
                notificationTitle = intent.getStringExtra("title").orEmpty()
                notificationText = intent.getStringExtra("text").orEmpty()
                showExpanded(notificationTitle.ifBlank { "إشعار جديد" }, notificationText, 5000)
            }
        }
    }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIF_ID, NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Dynamic Island شغالة")
            .setContentText("اضغط على الكبسولة لفتحها")
            .setOngoing(true).build())
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val filter = IntentFilter(ACTION_NOTIFICATION)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, filter, RECEIVER_NOT_EXPORTED) else registerReceiver(receiver, filter)
        if (Settings.canDrawOverlays(this)) createOverlay()
        refreshMedia()
        registerReceiver(powerReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        })
    }
    private val powerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_POWER_CONNECTED) showExpanded("Charging ⚡", "تم توصيل الشاحن", 3200)
            else collapse()
        }
    }
    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(CHANNEL, "Dynamic Island", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
    private fun makeShape(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
        setStroke(dp(1), 0xFF25252B.toInt())
    }
    private fun makeText(value: String, size: Int, color: Int, bold: Boolean) = TextView(this).apply {
        text = value
        textSize = size.toFloat()
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        maxLines = 2
        ellipsize = android.text.TextUtils.TruncateAt.END
    }
    private fun createOverlay() {
        if (root != null) return
        val host = FrameLayout(this).apply { setBackgroundColor(Color.TRANSPARENT) }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(9), dp(7), dp(9), dp(7))
            background = null
        }
        // Tiny camera-like dot. The wider transparent host is the invisible touch zone.
        val dot = View(this).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.BLACK) }
        }
        content.addView(dot, LinearLayout.LayoutParams(dp(25), dp(25)))
        val title = makeText("", 12, Color.WHITE, true).apply { visibility = View.GONE }
        val subtitle = makeText("", 10, 0xFFBDBDBD.toInt(), false).apply { visibility = View.GONE }
        titleView = title
        subView = subtitle
        content.addView(title)
        content.addView(subtitle)
        val controls = LinearLayout(this).apply { gravity = Gravity.CENTER; orientation = LinearLayout.HORIZONTAL }
        fun control(symbol: String, action: () -> Unit) {
            controls.addView(makeText(symbol, 18, Color.WHITE, true).apply {
                setPadding(dp(12), dp(4), dp(12), dp(4))
                setOnClickListener { action() }
            })
        }
        control("⏮") { currentController?.transportControls?.skipToPrevious(); refreshMedia() }
        control("▶ / Ⅱ") {
            val state = currentController?.playbackState?.state
            if (state == PlaybackState.STATE_PLAYING) currentController?.transportControls?.pause()
            else currentController?.transportControls?.play()
            refreshMedia()
        }
        control("⏭") { currentController?.transportControls?.skipToNext(); refreshMedia() }
        controls.visibility = View.GONE
        content.addView(controls)
        host.addView(content, FrameLayout.LayoutParams(dp(35), dp(32), Gravity.TOP or Gravity.CENTER_HORIZONTAL))
        host.setOnClickListener {
            if (expanded) collapse() else {
                refreshMedia()
                val metadata = currentController?.metadata
                showExpanded(
                    metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "Dynamic Island",
                    metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: "اضغط للتحكم في الموسيقى",
                    0
                )
            }
        }
        // Child controls become visible only when expanded; in idle the host remains transparent.
        card = content
        root = host
        val params = WindowManager.LayoutParams(
            dp(180), dp(48),
            if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dp(1)
        }
        try { wm.addView(host, params) } catch (_: Exception) { root = null }
        collapse()
    }
    private fun showExpanded(title: String, subtitle: String, duration: Long) {
        val host = root ?: return
        expanded = true
        titleView?.text = title
        subView?.text = subtitle
        titleView?.visibility = View.VISIBLE
        subView?.visibility = View.VISIBLE
        card?.getChildAt(0)?.visibility = View.GONE
        card?.getChildAt(3)?.visibility = View.VISIBLE
        card?.background = makeShape(Color.BLACK, 27)
        val cardParams = card?.layoutParams as? FrameLayout.LayoutParams
        cardParams?.width = dp(214)
        cardParams?.height = dp(90)
        cardParams?.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        if (cardParams != null) card?.layoutParams = cardParams
        val params = host.layoutParams as WindowManager.LayoutParams
        params.width = dp(230)
        params.height = dp(100)
        try { wm.updateViewLayout(host, params) } catch (_: Exception) {}
        if (duration > 0) host.postDelayed({ if (expanded) collapse() }, duration)
    }
    private fun collapse() {
        val host = root ?: return
        expanded = false
        titleView?.text = ""
        subView?.text = ""
        titleView?.visibility = View.GONE
        subView?.visibility = View.GONE
        card?.getChildAt(0)?.visibility = View.VISIBLE
        card?.getChildAt(3)?.visibility = View.GONE
        card?.background = null
        val cardParams = card?.layoutParams as? FrameLayout.LayoutParams
        cardParams?.width = dp(30)
        cardParams?.height = dp(30)
        cardParams?.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        if (cardParams != null) card?.layoutParams = cardParams
        val params = host.layoutParams as WindowManager.LayoutParams
        params.width = dp(180)
        params.height = dp(48)
        try { wm.updateViewLayout(host, params) } catch (_: Exception) {}
    }
    private fun refreshMedia() {
        try {
            val manager = getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager
            val active = manager.getActiveSessions(ComponentName(this, IslandNotificationListener::class.java))
            currentController = active.firstOrNull()
            val c = currentController ?: return
            if (expanded) {
                val m = c.metadata
                titleView?.text = m?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "الموسيقى"
                subView?.text = m?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: ""
            }
        } catch (_: SecurityException) { currentController = null }
        catch (_: Exception) { currentController = null }
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY
    override fun onDestroy() {
        try { unregisterReceiver(receiver) } catch (_: Exception) {}
        try { unregisterReceiver(powerReceiver) } catch (_: Exception) {}
        try { root?.let { wm.removeView(it) } } catch (_: Exception) {}
        root = null
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
}
