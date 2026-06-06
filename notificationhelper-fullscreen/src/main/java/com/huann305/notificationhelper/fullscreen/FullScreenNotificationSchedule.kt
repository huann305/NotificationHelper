package com.huann305.notificationhelper.fullscreen

import com.huann305.notificationhelper.NotificationOptions

data class FullScreenScheduledNotificationRequest(
    val content: FullScreenNotificationContent,
    val options: NotificationOptions = NotificationOptions(),
    val delayMillis: Long,
    val uniqueWorkName: String? = null,
    val replaceExisting: Boolean = true,
    val fallbackToNormalNotification: Boolean = true,
    val launchActivityFallbackOnLockScreen: Boolean = true
) {
    init {
        require(delayMillis >= 0L) { "delayMillis must be >= 0" }
    }
}

data class FullScreenDailyNotificationRequest(
    val content: FullScreenNotificationContent,
    val hourOfDay: Int,
    val minute: Int,
    val options: NotificationOptions = NotificationOptions(),
    val uniqueWorkName: String = FullScreenNotificationHelper.DEFAULT_DAILY_WORK_NAME,
    val replaceExisting: Boolean = true,
    val fallbackToNormalNotification: Boolean = true,
    val launchActivityFallbackOnLockScreen: Boolean = true
) {
    init {
        require(hourOfDay in 0..23) { "hourOfDay must be in 0..23" }
        require(minute in 0..59) { "minute must be in 0..59" }
    }
}
