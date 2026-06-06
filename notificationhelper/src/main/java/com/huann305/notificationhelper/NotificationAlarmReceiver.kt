package com.huann305.notificationhelper

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.Data

class NotificationAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val data = intent.getByteArrayExtra(NotificationAlarmContract.EXTRA_DATA)
            ?.let { bytes -> runCatching { Data.fromByteArray(bytes) }.getOrNull() }
            ?: return

        val appContext = context.applicationContext
        NotificationHelper.forgetAlarm(
            appContext,
            intent.getIntExtra(NotificationAlarmContract.EXTRA_REQUEST_CODE, 0)
        )

        if (
            !NotificationHelper.hasPostNotificationsPermission(appContext) ||
            !NotificationHelper.areNotificationsEnabled(appContext)
        ) {
            return
        }

        NotificationHelper.sendNow(
            context = appContext,
            content = data.toNotificationContent(),
            options = data.toNotificationOptions()
        )
    }
}
