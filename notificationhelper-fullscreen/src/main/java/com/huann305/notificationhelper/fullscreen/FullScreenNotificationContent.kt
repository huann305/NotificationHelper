package com.huann305.notificationhelper.fullscreen

import com.huann305.notificationhelper.NotificationContent

data class FullScreenNotificationContent(
    val notificationContent: NotificationContent,
    val layoutResId: Int = 0,
    val appIconResId: Int = 0,
    val appIconUrl: String? = null,
    val imageResId: Int = 0,
    val imageUrl: String? = null,
    val fullScreenTitle: String? = null,
    val fullScreenMessage: String? = null,
    val timeText: String? = null,
    val dateText: String? = null,
    val actionText: String = "Open",
    val dismissText: String = "Dismiss"
)
