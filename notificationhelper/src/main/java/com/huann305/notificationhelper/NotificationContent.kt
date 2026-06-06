package com.huann305.notificationhelper

import android.content.Intent

data class NotificationContent(
    val title: String,
    val message: String,
    val bigText: String? = null,
    val targetIntent: Intent? = null,
    val bigLayout: BigNotificationLayout = BigNotificationLayout(enabled = false)
)
