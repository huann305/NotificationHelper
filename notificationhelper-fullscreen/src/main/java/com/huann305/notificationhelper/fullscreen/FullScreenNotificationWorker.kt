package com.huann305.notificationhelper.fullscreen

import android.app.NotificationManager
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.Data
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.huann305.notificationhelper.NotificationContent
import com.huann305.notificationhelper.NotificationHelper
import com.huann305.notificationhelper.NotificationOptions

class FullScreenNotificationWorker(
    context: android.content.Context,
    workerParams: WorkerParameters
) : Worker(context, workerParams) {

    override fun doWork(): Result {
        if (
            !NotificationHelper.hasPostNotificationsPermission(applicationContext) ||
            !NotificationHelper.areNotificationsEnabled(applicationContext)
        ) {
            return Result.success()
        }

        val sent = FullScreenNotificationHelper.sendFullScreenOnLockScreen(
            context = applicationContext,
            content = inputData.toFullScreenNotificationContent(),
            options = inputData.toNotificationOptions(),
            fallbackToNormalNotification = inputData.getBoolean(
                FullScreenDataKeys.FALLBACK_TO_NORMAL_NOTIFICATION,
                true
            ),
            launchActivityFallbackOnLockScreen = inputData.getBoolean(
                FullScreenDataKeys.LAUNCH_ACTIVITY_FALLBACK_ON_LOCK_SCREEN,
                true
            )
        )
        return if (sent) Result.success() else Result.retry()
    }
}

internal fun FullScreenNotificationContent.toData(builder: Data.Builder): Data.Builder {
    val normal = notificationContent
    builder.putString(FullScreenDataKeys.TITLE, normal.title)
    builder.putString(FullScreenDataKeys.MESSAGE, normal.message)
    builder.putString(FullScreenDataKeys.BIG_TEXT, normal.bigText)
    builder.putString(
        FullScreenDataKeys.TARGET_INTENT_URI,
        normal.targetIntent?.toUri(Intent.URI_INTENT_SCHEME)
    )
    builder.putInt(FullScreenDataKeys.LAYOUT_RES_ID, layoutResId)
    builder.putInt(FullScreenDataKeys.APP_ICON_RES_ID, appIconResId)
    builder.putString(FullScreenDataKeys.APP_ICON_URL, appIconUrl)
    builder.putInt(FullScreenDataKeys.IMAGE_RES_ID, imageResId)
    builder.putString(FullScreenDataKeys.IMAGE_URL, imageUrl)
    builder.putString(FullScreenDataKeys.FULL_SCREEN_TITLE, fullScreenTitle)
    builder.putString(FullScreenDataKeys.FULL_SCREEN_MESSAGE, fullScreenMessage)
    builder.putString(FullScreenDataKeys.TIME_TEXT, timeText)
    builder.putString(FullScreenDataKeys.DATE_TEXT, dateText)
    builder.putString(FullScreenDataKeys.ACTION_TEXT, actionText)
    builder.putString(FullScreenDataKeys.DISMISS_TEXT, dismissText)
    return builder
}

internal fun NotificationOptions.toFullScreenData(builder: Data.Builder): Data.Builder {
    builder.putString(FullScreenDataKeys.CHANNEL_ID, channelId)
    builder.putString(FullScreenDataKeys.CHANNEL_NAME, channelName)
    builder.putString(FullScreenDataKeys.CHANNEL_DESCRIPTION, channelDescription)
    builder.putInt(FullScreenDataKeys.CHANNEL_IMPORTANCE, channelImportance)
    builder.putInt(FullScreenDataKeys.NOTIFICATION_ID, notificationId)
    builder.putString(FullScreenDataKeys.NOTIFICATION_TAG, notificationTag)
    builder.putInt(FullScreenDataKeys.REQUEST_CODE, requestCode)
    builder.putInt(FullScreenDataKeys.SMALL_ICON_RES_ID, smallIconResId)
    val notificationColor = color
    if (notificationColor != null) {
        builder.putBoolean(FullScreenDataKeys.HAS_COLOR, true)
        builder.putInt(FullScreenDataKeys.COLOR, notificationColor)
    }
    builder.putInt(FullScreenDataKeys.PRIORITY, priority)
    builder.putString(FullScreenDataKeys.CATEGORY, category)
    builder.putBoolean(FullScreenDataKeys.AUTO_CANCEL, autoCancel)
    builder.putBoolean(FullScreenDataKeys.ONLY_ALERT_ONCE, onlyAlertOnce)
    return builder
}

internal fun Data.toFullScreenNotificationContent(): FullScreenNotificationContent {
    val targetIntent = getString(FullScreenDataKeys.TARGET_INTENT_URI)?.let { uri ->
        runCatching { Intent.parseUri(uri, Intent.URI_INTENT_SCHEME) }.getOrNull()
    }
    val normal = NotificationContent(
        title = getString(FullScreenDataKeys.TITLE).orEmpty(),
        message = getString(FullScreenDataKeys.MESSAGE).orEmpty(),
        bigText = getString(FullScreenDataKeys.BIG_TEXT),
        targetIntent = targetIntent
    )
    return FullScreenNotificationContent(
        notificationContent = normal,
        layoutResId = getInt(FullScreenDataKeys.LAYOUT_RES_ID, 0),
        appIconResId = getInt(FullScreenDataKeys.APP_ICON_RES_ID, 0),
        appIconUrl = getString(FullScreenDataKeys.APP_ICON_URL),
        imageResId = getInt(FullScreenDataKeys.IMAGE_RES_ID, 0),
        imageUrl = getString(FullScreenDataKeys.IMAGE_URL),
        fullScreenTitle = getString(FullScreenDataKeys.FULL_SCREEN_TITLE),
        fullScreenMessage = getString(FullScreenDataKeys.FULL_SCREEN_MESSAGE),
        timeText = getString(FullScreenDataKeys.TIME_TEXT),
        dateText = getString(FullScreenDataKeys.DATE_TEXT),
        actionText = getString(FullScreenDataKeys.ACTION_TEXT) ?: "Open",
        dismissText = getString(FullScreenDataKeys.DISMISS_TEXT) ?: "Dismiss"
    )
}

internal fun Data.toNotificationOptions(): NotificationOptions {
    return NotificationOptions(
        channelId = getString(FullScreenDataKeys.CHANNEL_ID)
            ?: NotificationHelper.DEFAULT_CHANNEL_ID,
        channelName = getString(FullScreenDataKeys.CHANNEL_NAME)
            ?: NotificationHelper.DEFAULT_CHANNEL_NAME,
        channelDescription = getString(FullScreenDataKeys.CHANNEL_DESCRIPTION)
            ?: NotificationHelper.DEFAULT_CHANNEL_DESCRIPTION,
        channelImportance = getInt(
            FullScreenDataKeys.CHANNEL_IMPORTANCE,
            NotificationManager.IMPORTANCE_HIGH
        ),
        notificationId = getInt(
            FullScreenDataKeys.NOTIFICATION_ID,
            NotificationHelper.DEFAULT_NOTIFICATION_ID
        ),
        notificationTag = getString(FullScreenDataKeys.NOTIFICATION_TAG),
        requestCode = getInt(
            FullScreenDataKeys.REQUEST_CODE,
            NotificationHelper.DEFAULT_NOTIFICATION_ID
        ),
        smallIconResId = getInt(FullScreenDataKeys.SMALL_ICON_RES_ID, 0),
        color = if (getBoolean(FullScreenDataKeys.HAS_COLOR, false)) {
            getInt(FullScreenDataKeys.COLOR, 0)
        } else {
            null
        },
        priority = getInt(
            FullScreenDataKeys.PRIORITY,
            NotificationCompat.PRIORITY_HIGH
        ),
        category = getString(FullScreenDataKeys.CATEGORY)
            ?: NotificationCompat.CATEGORY_REMINDER,
        autoCancel = getBoolean(FullScreenDataKeys.AUTO_CANCEL, true),
        onlyAlertOnce = getBoolean(FullScreenDataKeys.ONLY_ALERT_ONCE, false)
    )
}
