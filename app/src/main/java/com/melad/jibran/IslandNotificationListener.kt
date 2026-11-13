package com.melad.jibran

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.content.Intent

class IslandNotificationListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val extras = sbn.notification.extras
        val title = extras.getCharSequence("android.title")?.toString().orEmpty()
        val text = (extras.getCharSequence("android.text") ?: extras.getCharSequence("android.bigText"))?.toString().orEmpty()
        if (title.isBlank() && text.isBlank()) return
        sendBroadcast(Intent(IslandOverlayService.ACTION_NOTIFICATION).setPackage(packageName).apply {
            putExtra("title", title.take(80))
            putExtra("text", text.take(140))
            putExtra("package", sbn.packageName)
        })
    }
}
