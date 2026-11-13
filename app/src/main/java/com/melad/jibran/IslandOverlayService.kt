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
import android.view.animation.DecelerateInterpolator
import android.widget.*
import androidx.core.app.NotificationCompat
import kotlin.math.max

class IslandOverlayService : Service() {
    companion object {
        const val ACTION_NOTIFICATION = "com.melad.jibran.ISLAND_NOTIFICATION"
        private const val CHANNEL = "dynamic_island_service"
        private const val NOTIF_ID = 9301
    }
    private lateinit var wm: WindowManager
    private var root: LinearLayout? = null
    private var pill: LinearLayout? = null
    private var titleView: TextView? = null
    private var subView: TextView? = null
    private var expanded = false
    private var currentController: MediaController? = null
    private var controllers = emptyList<MediaController>()
    private var notificationTitle = ""
    private var notificationText = ""
    private var notificationMode = false
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_NOTIFICATION) {
                notificationTitle = intent.getStringExtra("title").orEmpty()
                notificationText = intent.getStringExtra("text").orEmpty()
                notificationMode = true
                showExpanded(notificationTitle, notificationText, 4200)
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
            if (intent?.action == Intent.ACTION_POWER_CONNECTED) showExpanded("Charging", "تم توصيل الشاحن ⚡", 3000)
            else collapse()
        }
    }
    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(CHANNEL, "Dynamic Island", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
    private fun createOverlay() {
        if (root != null) return
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(5), dp(3), dp(5), dp(5))
            background = shape(Color.BLACK, 28)
            elevation = dp(8).toFloat()
        }
        val visual = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(5), dp(10), dp(7))
        }
        val head = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            orientation = LinearLayout.HORIZONTAL
        }
        val dot = View(this).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.BLACK) }
        }
        // The camera-sized idle pill is centered; the full transparent overlay is the hidden touch target.
        head.addView(dot, LinearLayout.LayoutParams(dp(23), dp(23)))
        visual.addView(head)
        val title = text("", 12, Color.WHITE, true)
        val subtitle = text("", 10, 0xFFBDBDBD.toInt(), false)
        titleView = title
        subView = subtitle
        visual.addView(title)
        visual.addView(subtitle)
        val controls = LinearLayout(this).apply { gravity = Gravity.CENTER; orientation = LinearLayout.HORIZONTAL }
        fun control(symbol: String, action: () -> Unit) {
            val b = text(symbol, 19, Color.WHITE, true).apply {
                gravity = Gravity.CENTER
                setPadding(dp(12), dp(3), dp(12), dp(3))
                setOnClickListener { action() }
            }
            controls.addView(b)
        }
        control("⏮") { currentController?.transportControls?.skipToPrevious(); refreshMedia() }
        control("▶/Ⅱ") {
            val state = currentController?.playbackState?.state
            if (state == PlaybackState.STATE_PLAYING) currentController?.transportControls?.pause()
            else currentController?.transportControls?.play()
            refreshMedia()
        }
        control("⏭") { currentController?.transportControls?.skipToNext(); refreshMedia() }
        visual.addView(controls)
        container.addView(visual)
        container.setOnClickListener {
            if (expanded) collapse() else {
                refreshMedia()
                val c = currentController
                if (c != null) showExpanded(c.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "الموسيقى",
                    c.metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: "التحكم في التشغيل", 0)
                else showExpanded("Dynamic Island", "مفيش تشغيل نشط حاليًا", 0)
            }
        }
        root = container
        pill = container
        val params = WindowManager.LayoutParams(
            dp(180), dp(34),
            if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dp(2)
        }
        try { wm.addView(container, params) } catch (_: Exception) { root = null }
    }
    private fun text(value: String, size: Int, color: Int, bold: Boolean) = TextView(this).apply {
        text = value
        textSize = size.toFloat()
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        maxLines = 2
    }
    private fun shape(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
        setStroke(dp(1), 0xFF24242A.toInt())
    }
    private fun showExpanded(title: String, subtitle: String, duration: Long) {
        val view = pill ?: return
        expanded = true
        titleView?.text = title
        subView?.text = subtitle
        val params = view.layoutParams as WindowManager.LayoutParams
        params.width = dp(220)
        params.height = dp(92)
        view.background = shape(Color.BLACK, 26)
        try { wm.updateViewLayout(view, params) } catch (_: Exception) {}
        if (duration > 0) view.postDelayed({ if (expanded) collapse() }, duration)
    }
    private fun collapse() {
        val view = pill ?: return
        expanded = false
        titleView?.text = ""
        subView?.text = ""
        val params = view.layoutParams as WindowManager.LayoutParams
        params.width = dp(180)
        params.height = dp(34)
        view.background = shape(Color.BLACK, 28)
        try { wm.updateViewLayout(view, params) } catch (_: Exception) {}
    }
    private fun refreshMedia() {
        try {
            val manager = getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager
            controllers = manager.getActiveSessions(android.content.ComponentName(this, IslandNotificationListener::class.java))
            currentController = controllers.firstOrNull()
            val c = currentController ?: return
            c.registerCallback(object : MediaController.Callback() {
                override fun onMetadataChanged(metadata: MediaMetadata?) {
                    if (expanded) showExpanded(metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "الموسيقى",
                        metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: "", 0)
                }
                override fun onPlaybackStateChanged(state: PlaybackState?) { }
            })
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
