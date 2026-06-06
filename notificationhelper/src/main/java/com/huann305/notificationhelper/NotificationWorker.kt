package com.huann305.notificationhelper

import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.Data
import androidx.work.Worker
import androidx.work.WorkerParameters

class NotificationWorker(
    context: android.content.Context,
    workerParams: WorkerParameters
) : Worker(context, workerParams) {

    override fun doWork(): Result {
        val content = inputData.toNotificationContent()
        val options = inputData.toNotificationOptions()

        if (
            !NotificationHelper.hasPostNotificationsPermission(applicationContext) ||
            !NotificationHelper.areNotificationsEnabled(applicationContext)
        ) {
            return Result.success()
        }

        val sent = NotificationHelper.sendNow(applicationContext, content, options)
        return if (sent) Result.success() else Result.retry()
    }
}

internal fun NotificationContent.toData(builder: Data.Builder): Data.Builder {
    builder.putString(NotificationDataKeys.TITLE, title)
    builder.putString(NotificationDataKeys.MESSAGE, message)
    builder.putString(NotificationDataKeys.BIG_TEXT, bigText)
    builder.putString(
        NotificationDataKeys.TARGET_INTENT_URI,
        targetIntent?.toUri(Intent.URI_INTENT_SCHEME)
    )
    builder.putBoolean(NotificationDataKeys.BIG_LAYOUT_ENABLED, bigLayout.isEnabled)
    builder.putInt(NotificationDataKeys.BIG_COMPACT_LAYOUT_RES_ID, bigLayout.compactLayoutResId)
    builder.putInt(NotificationDataKeys.BIG_EXPANDED_LAYOUT_RES_ID, bigLayout.expandedLayoutResId)
    builder.putInt(NotificationDataKeys.BIG_ICON_RES_ID, bigLayout.iconResId)
    builder.putString(NotificationDataKeys.BIG_ICON_URL, bigLayout.iconUrl)
    builder.putInt(NotificationDataKeys.BIG_IMAGE_RES_ID, bigLayout.imageResId)
    builder.putString(NotificationDataKeys.BIG_IMAGE_URL, bigLayout.imageUrl)
    builder.putString(NotificationDataKeys.BIG_ACTION_TEXT, bigLayout.actionText)
    builder.putString(NotificationDataKeys.BIG_LAYOUT_MODE, bigLayout.mode.name)
    builder.putBoolean(NotificationDataKeys.BIG_DECORATE_CUSTOM_VIEW, bigLayout.decorateCustomView)
    return builder
}

internal fun NotificationOptions.toData(builder: Data.Builder): Data.Builder {
    builder.putString(NotificationDataKeys.CHANNEL_ID, channelId)
    builder.putString(NotificationDataKeys.CHANNEL_NAME, channelName)
    builder.putString(NotificationDataKeys.CHANNEL_DESCRIPTION, channelDescription)
    builder.putInt(NotificationDataKeys.CHANNEL_IMPORTANCE, channelImportance)
    builder.putInt(NotificationDataKeys.NOTIFICATION_ID, notificationId)
    builder.putString(NotificationDataKeys.NOTIFICATION_TAG, notificationTag)
    builder.putInt(NotificationDataKeys.REQUEST_CODE, requestCode)
    builder.putInt(NotificationDataKeys.SMALL_ICON_RES_ID, smallIconResId)
    if (color != null) {
        builder.putBoolean(NotificationDataKeys.HAS_COLOR, true)
        builder.putInt(NotificationDataKeys.COLOR, color)
    }
    builder.putInt(NotificationDataKeys.PRIORITY, priority)
    builder.putString(NotificationDataKeys.CATEGORY, category)
    builder.putBoolean(NotificationDataKeys.AUTO_CANCEL, autoCancel)
    builder.putBoolean(NotificationDataKeys.ONLY_ALERT_ONCE, onlyAlertOnce)
    return builder
}

internal fun Data.toNotificationContent(): NotificationContent {
    val targetIntent = getString(NotificationDataKeys.TARGET_INTENT_URI)?.let { uri ->
        runCatching { Intent.parseUri(uri, Intent.URI_INTENT_SCHEME) }.getOrNull()
    }
    return NotificationContent(
        title = getString(NotificationDataKeys.TITLE).orEmpty(),
        message = getString(NotificationDataKeys.MESSAGE).orEmpty(),
        bigText = getString(NotificationDataKeys.BIG_TEXT),
        targetIntent = targetIntent,
        bigLayout = BigNotificationLayout(
            compactLayoutResId = getInt(NotificationDataKeys.BIG_COMPACT_LAYOUT_RES_ID, 0),
            expandedLayoutResId = getInt(NotificationDataKeys.BIG_EXPANDED_LAYOUT_RES_ID, 0),
            iconResId = getInt(NotificationDataKeys.BIG_ICON_RES_ID, 0),
            iconUrl = getString(NotificationDataKeys.BIG_ICON_URL),
            imageResId = getInt(NotificationDataKeys.BIG_IMAGE_RES_ID, 0),
            imageUrl = getString(NotificationDataKeys.BIG_IMAGE_URL),
            actionText = getString(NotificationDataKeys.BIG_ACTION_TEXT) ?: "Open",
            mode = getBigNotificationLayoutMode(),
            decorateCustomView = getBoolean(NotificationDataKeys.BIG_DECORATE_CUSTOM_VIEW, true),
            enabled = getBoolean(NotificationDataKeys.BIG_LAYOUT_ENABLED, false)
        )
    )
}

private fun Data.getBigNotificationLayoutMode(): BigNotificationLayoutMode {
    val rawMode = getString(NotificationDataKeys.BIG_LAYOUT_MODE)
    return rawMode?.let {
        runCatching { BigNotificationLayoutMode.valueOf(it) }.getOrNull()
    } ?: BigNotificationLayoutMode.SYSTEM_STYLE
}

internal fun Data.toNotificationOptions(): NotificationOptions {
    return NotificationOptions(
        channelId = getString(NotificationDataKeys.CHANNEL_ID)
            ?: NotificationHelper.DEFAULT_CHANNEL_ID,
        channelName = getString(NotificationDataKeys.CHANNEL_NAME)
            ?: NotificationHelper.DEFAULT_CHANNEL_NAME,
        channelDescription = getString(NotificationDataKeys.CHANNEL_DESCRIPTION)
            ?: NotificationHelper.DEFAULT_CHANNEL_DESCRIPTION,
        channelImportance = getInt(
            NotificationDataKeys.CHANNEL_IMPORTANCE,
            android.app.NotificationManager.IMPORTANCE_HIGH
        ),
        notificationId = getInt(
            NotificationDataKeys.NOTIFICATION_ID,
            NotificationHelper.DEFAULT_NOTIFICATION_ID
        ),
        notificationTag = getString(NotificationDataKeys.NOTIFICATION_TAG),
        requestCode = getInt(
            NotificationDataKeys.REQUEST_CODE,
            NotificationHelper.DEFAULT_NOTIFICATION_ID
        ),
        smallIconResId = getInt(NotificationDataKeys.SMALL_ICON_RES_ID, 0),
        color = if (getBoolean(NotificationDataKeys.HAS_COLOR, false)) {
            getInt(NotificationDataKeys.COLOR, 0)
        } else {
            null
        },
        priority = getInt(
            NotificationDataKeys.PRIORITY,
            NotificationCompat.PRIORITY_HIGH
        ),
        category = getString(NotificationDataKeys.CATEGORY)
            ?: NotificationCompat.CATEGORY_REMINDER,
        autoCancel = getBoolean(NotificationDataKeys.AUTO_CANCEL, true),
        onlyAlertOnce = getBoolean(NotificationDataKeys.ONLY_ALERT_ONCE, false)
    )
}
