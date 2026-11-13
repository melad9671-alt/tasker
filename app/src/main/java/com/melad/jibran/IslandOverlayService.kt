package com.melad.jibran

import android.app.*
import android.content.*
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.MediaMetadata
import android.media.session.MediaController
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
        @Volatile var isRunning = false
            private set
    }

    private lateinit var wm: WindowManager
    private var root: FrameLayout? = null
    private var card: LinearLayout? = null
    private var titleView: TextView? = null
    private var subView: TextView? = null
    private var controls: LinearLayout? = null
    private var expanded = false
    private var currentController: MediaController? = null
    private var collapseTask: Runnable? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_NOTIFICATION) {
                val title = intent.getStringExtra("title").orEmpty()
                val text = intent.getStringExtra("text").orEmpty()
                if (title.isNotBlank() || text.isNotBlank()) {
                    showExpanded(title.ifBlank { "إشعار جديد" }, text, 5000, false)
                }
            }
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        createChannel()
        startForeground(NOTIF_ID, NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Dynamic Island تعمل")
            .setContentText("خدمة الكبسولة الذكية")
            .setOngoing(true).build())
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val filter = IntentFilter(ACTION_NOTIFICATION)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED) else registerReceiver(receiver, filter)
        if (Settings.canDrawOverlays(this)) createOverlay()
        refreshMedia()
        registerReceiver(powerReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        })
    }

    private val powerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_POWER_CONNECTED) showExpanded("تم توصيل الشاحن", "جاري شحن الهاتف ⚡", 3000, false)
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
        setStroke(dp(1), 0xFF292D36.toInt())
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
        }
        val dot = View(this).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.BLACK) }
        }
        content.addView(dot, LinearLayout.LayoutParams(dp(25), dp(25)))
        titleView = makeText("", 12, Color.WHITE, true).apply { visibility = View.GONE }
        subView = makeText("", 10, 0xFFBDBDBD.toInt(), false).apply { visibility = View.GONE }
        content.addView(titleView)
        content.addView(subView)

        controls = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            orientation = LinearLayout.HORIZONTAL
            visibility = View.GONE
        }
        fun control(symbol: String, action: () -> Unit) {
            controls?.addView(makeText(symbol, 17, Color.WHITE, true).apply {
                setPadding(dp(13), dp(5), dp(13), dp(5))
                setOnClickListener { action() }
            })
        }
        control("⏮") { currentController?.transportControls?.skipToPrevious(); refreshMedia() }
        control("▶") {
            val c = currentController ?: return@control
            if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause()
            else c.transportControls.play()
            refreshMedia()
        }
        control("⏭") { currentController?.transportControls?.skipToNext(); refreshMedia() }
        content.addView(controls)
        host.addView(content, FrameLayout.LayoutParams(dp(35), dp(32), Gravity.TOP or Gravity.CENTER_HORIZONTAL))
        host.setOnClickListener {
            if (expanded) {
                collapse()
            } else {
                refreshMedia()
                val c = currentController
                val metadata = c?.metadata
                val hasTrack = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty().isNotBlank()
                if (c != null && hasTrack) {
                    val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty()
                    showExpanded(metadata?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty(),
                        artist.ifBlank { "يتم تشغيل الوسائط" }, 0, true)
                } // No active track: keep the island as a quiet camera-like dot.
            }
        }
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
            if (Build.VERSION.SDK_INT >= 28) layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
        try { wm.addView(host, params) } catch (_: Exception) { root = null }
        collapse()
    }

    private fun showExpanded(title: String, subtitle: String, duration: Long, showControls: Boolean) {
        val host = root ?: return
        collapseTask?.let { host.removeCallbacks(it) }
        expanded = true
        titleView?.text = title
        subView?.text = subtitle
        titleView?.visibility = View.VISIBLE
        subView?.visibility = if (subtitle.isBlank()) View.GONE else View.VISIBLE
        card?.getChildAt(0)?.visibility = View.GONE
        controls?.visibility = if (showControls && currentController != null) View.VISIBLE else View.GONE
        card?.background = makeShape(Color.BLACK, 27)
        val cardParams = card?.layoutParams as? FrameLayout.LayoutParams
        cardParams?.width = dp(214)
        cardParams?.height = dp(if (showControls && currentController != null) 112 else 82)
        cardParams?.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        if (cardParams != null) card?.layoutParams = cardParams
        val params = host.layoutParams as WindowManager.LayoutParams
        params.width = dp(230)
        params.height = dp(if (showControls && currentController != null) 122 else 92)
        try { wm.updateViewLayout(host, params) } catch (_: Exception) {}
        if (duration > 0) {
            val task = Runnable { if (expanded) collapse() }
            collapseTask = task
            host.postDelayed(task, duration)
        }
    }

    private fun collapse() {
        val host = root ?: return
        collapseTask?.let { host.removeCallbacks(it) }
        collapseTask = null
        expanded = false
        titleView?.text = ""
        subView?.text = ""
        titleView?.visibility = View.GONE
        subView?.visibility = View.GONE
        card?.getChildAt(0)?.visibility = View.VISIBLE
        controls?.visibility = View.GONE
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
            val manager = getSystemService(MEDIA_SESSION_SERVICE) as android.media.session.MediaSessionManager
            val active = manager.getActiveSessions(ComponentName(this, IslandNotificationListener::class.java))
            currentController = active.firstOrNull { controller ->
                controller.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty().isNotBlank()
            }
            if (expanded && currentController != null && controls?.visibility == View.VISIBLE) {
                val m = currentController?.metadata
                titleView?.text = m?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty()
                subView?.text = m?.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty()
            }
        } catch (_: SecurityException) {
            currentController = null
        } catch (_: Exception) {
            currentController = null
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY

    override fun onDestroy() {
        isRunning = false
        collapseTask?.let { root?.removeCallbacks(it) }
        try { unregisterReceiver(receiver) } catch (_: Exception) {}
        try { unregisterReceiver(powerReceiver) } catch (_: Exception) {}
        try { root?.let { wm.removeView(it) } } catch (_: Exception) {}
        root = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
