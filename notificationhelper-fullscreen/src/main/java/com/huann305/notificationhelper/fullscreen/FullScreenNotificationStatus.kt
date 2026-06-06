package com.huann305.notificationhelper.fullscreen

import android.app.NotificationManager

data class FullScreenNotificationStatus(
    val hasPostNotificationsPermission: Boolean,
    val areNotificationsEnabled: Boolean,
    val canUseFullScreenIntent: Boolean,
    val canShowOnLockScreen: Boolean,
    val isScreenLockedOrOff: Boolean,
    val isInteractive: Boolean,
    val isDeviceLocked: Boolean,
    val isKeyguardLocked: Boolean,
    val channelId: String,
    val channelImportance: Int?,
    val manufacturer: String,
    val model: String,
    val sdkInt: Int
) {
    val isChannelUsable: Boolean
        get() = channelImportance == null ||
            channelImportance >= NotificationManager.IMPORTANCE_HIGH

    val canTryFullScreenOnLockScreen: Boolean
        get() = hasPostNotificationsPermission &&
            areNotificationsEnabled &&
            canUseFullScreenIntent &&
            isScreenLockedOrOff &&
            isChannelUsable

    val canAttemptFullScreenOnLockScreen: Boolean
        get() = hasPostNotificationsPermission &&
            areNotificationsEnabled &&
            canUseFullScreenIntent &&
            canShowOnLockScreen &&
            isScreenLockedOrOff &&
            isChannelUsable
}
