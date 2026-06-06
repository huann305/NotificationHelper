package com.huann305.notificationhelper.fullscreen

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.Data
import com.huann305.notificationhelper.NotificationHelper

class FullScreenAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val data = intent.getByteArrayExtra(FullScreenAlarmContract.EXTRA_DATA)
            ?.let { bytes -> runCatching { Data.fromByteArray(bytes) }.getOrNull() }
            ?: return

        val appContext = context.applicationContext
        FullScreenNotificationHelper.forgetAlarm(
            appContext,
            intent.getIntExtra(FullScreenAlarmContract.EXTRA_REQUEST_CODE, 0)
        )

        if (
            !NotificationHelper.hasPostNotificationsPermission(appContext) ||
            !NotificationHelper.areNotificationsEnabled(appContext)
        ) {
            return
        }

        FullScreenNotificationHelper.sendFullScreenOnLockScreen(
            context = appContext,
            content = data.toFullScreenNotificationContent(),
            options = data.toNotificationOptions(),
            fallbackToNormalNotification = data.getBoolean(
                FullScreenDataKeys.FALLBACK_TO_NORMAL_NOTIFICATION,
                true
            ),
            launchActivityFallbackOnLockScreen = data.getBoolean(
                FullScreenDataKeys.LAUNCH_ACTIVITY_FALLBACK_ON_LOCK_SCREEN,
                true
            )
        )
    }
}
