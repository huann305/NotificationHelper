package com.huann305.notificationhelper

data class ScheduledNotificationRequest(
    val content: NotificationContent,
    val options: NotificationOptions = NotificationOptions(),
    val delayMillis: Long,
    val uniqueWorkName: String? = null,
    val replaceExisting: Boolean = true,
    val backend: NotificationScheduleBackend = NotificationScheduleBackend.ALARM_MANAGER
) {
    init {
        require(delayMillis >= 0L) { "delayMillis must be >= 0" }
    }
}

data class DailyNotificationRequest(
    val content: NotificationContent,
    val hourOfDay: Int,
    val minute: Int,
    val options: NotificationOptions = NotificationOptions(),
    val uniqueWorkName: String = NotificationHelper.DEFAULT_DAILY_WORK_NAME,
    val replaceExisting: Boolean = true
) {
    init {
        require(hourOfDay in 0..23) { "hourOfDay must be in 0..23" }
        require(minute in 0..59) { "minute must be in 0..59" }
    }
}
