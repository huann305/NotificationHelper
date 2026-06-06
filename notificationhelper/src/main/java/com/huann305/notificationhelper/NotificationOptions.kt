package com.huann305.notificationhelper

import android.app.NotificationManager
import androidx.core.app.NotificationCompat

data class NotificationOptions(
    val channelId: String = NotificationHelper.DEFAULT_CHANNEL_ID,
    val channelName: String = NotificationHelper.DEFAULT_CHANNEL_NAME,
    val channelDescription: String = NotificationHelper.DEFAULT_CHANNEL_DESCRIPTION,
    val channelImportance: Int = NotificationManager.IMPORTANCE_HIGH,
    val notificationId: Int = NotificationHelper.DEFAULT_NOTIFICATION_ID,
    val notificationTag: String? = null,
    val requestCode: Int = notificationId,
    val smallIconResId: Int = 0,
    val color: Int? = null,
    val priority: Int = NotificationCompat.PRIORITY_HIGH,
    val category: String = NotificationCompat.CATEGORY_REMINDER,
    val autoCancel: Boolean = true,
    val onlyAlertOnce: Boolean = false
)
