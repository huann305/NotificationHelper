package com.huann305.notificationhelper.fullscreen

import android.Manifest
import android.app.Activity
import android.app.ActivityOptions
import android.app.AppOpsManager
import android.app.KeyguardManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.huann305.notificationhelper.NotificationContent
import com.huann305.notificationhelper.NotificationHelper
import com.huann305.notificationhelper.NotificationOptions
import java.util.Calendar
import java.util.UUID
import java.util.concurrent.TimeUnit
import com.huann305.notificationhelper.core.R as BaseR

object FullScreenNotificationHelper {
    const val DEFAULT_DAILY_WORK_NAME = "notification_helper_fullscreen_daily"

    private const val TAG = "NHFullScreen"
    private const val WORK_TAG = "notification_helper_fullscreen_work"
    private const val FULL_SCREEN_REQUEST_CODE_OFFSET = 10_000
    private const val MIUI_SHOW_ON_LOCK_SCREEN_OP = 10020
    private const val MILLIS_PER_SECOND = 1_000L
    private const val MILLIS_PER_MINUTE = 60_000L
    private const val MILLIS_PER_HOUR = 3_600_000L

    @JvmStatic
    fun canUseFullScreenIntent(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
            notificationManager(context).canUseFullScreenIntent()
    }

    @JvmStatic
    fun shouldRequestFullScreenIntentPermission(context: Context): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            !canUseFullScreenIntent(context)
    }

    @JvmStatic
    fun requestFullScreenIntentPermission(activity: Activity): Boolean {
        if (!shouldRequestFullScreenIntentPermission(activity)) return true
        openFullScreenIntentSettings(activity)
        return false
    }

    @JvmStatic
    fun createFullScreenIntentPermissionIntent(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        } else {
            appDetailsSettingsIntent(context)
        }
    }

    @JvmStatic
    fun openFullScreenIntentSettings(context: Context): Boolean {
        return startActivitySafely(context, createFullScreenIntentPermissionIntent(context))
    }

    @JvmStatic
    fun canShowOnLockScreen(context: Context): Boolean {
        return runCatching {
            val manager = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val method = AppOpsManager::class.java.getDeclaredMethod(
                "checkOpNoThrow",
                Integer.TYPE,
                Integer.TYPE,
                String::class.java
            )
            val result = method.invoke(
                manager,
                MIUI_SHOW_ON_LOCK_SCREEN_OP,
                Process.myUid(),
                context.packageName
            ) as Int
            result == AppOpsManager.MODE_ALLOWED
        }.getOrDefault(true)
    }

    @JvmStatic
    fun openLockScreenSettings(context: Context): Boolean {
        val miuiIntents = listOf(
            Intent("miui.intent.action.APP_PERM_EDITOR").apply {
                setClassName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.permissions.PermissionsEditorActivity"
                )
            },
            Intent("miui.intent.action.APP_PERM_EDITOR").apply {
                setClassName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.permissions.AppPermissionsEditorActivity"
                )
            },
            Intent("miui.intent.action.APP_PERM_EDITOR")
        ).map { intent ->
            intent.apply {
                putExtra("extra_pkgname", context.packageName)
                putExtra("extra_package_uid", Process.myUid())
                putExtra("extra_permission_id", MIUI_SHOW_ON_LOCK_SCREEN_OP)
            }
        }
        return miuiIntents.any { startActivitySafely(context, it) } ||
            startActivitySafely(context, appDetailsSettingsIntent(context))
    }

    @JvmStatic
    fun openFullScreenNotificationChannelSettings(
        context: Context,
        options: NotificationOptions = NotificationOptions()
    ): Boolean {
        return NotificationHelper.openNotificationChannelSettings(context, options.channelId)
    }

    @JvmStatic
    fun getStatus(
        context: Context,
        options: NotificationOptions = NotificationOptions()
    ): FullScreenNotificationStatus {
        val appContext = context.applicationContext
        val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as PowerManager
        val keyguardManager = appContext.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        val isInteractive = powerManager.isInteractive
        val isDeviceLocked = keyguardManager.isDeviceLocked
        val isKeyguardLocked = keyguardManager.isKeyguardLocked
        return FullScreenNotificationStatus(
            hasPostNotificationsPermission = NotificationHelper.hasPostNotificationsPermission(appContext),
            areNotificationsEnabled = NotificationHelper.areNotificationsEnabled(appContext),
            canUseFullScreenIntent = canUseFullScreenIntent(appContext),
            canShowOnLockScreen = canShowOnLockScreen(appContext),
            isScreenLockedOrOff = !isInteractive || isDeviceLocked || isKeyguardLocked,
            isInteractive = isInteractive,
            isDeviceLocked = isDeviceLocked,
            isKeyguardLocked = isKeyguardLocked,
            channelId = options.channelId,
            channelImportance = NotificationHelper.getNotificationChannelImportance(
                appContext,
                options.channelId
            ),
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            sdkInt = Build.VERSION.SDK_INT
        )
    }

    @JvmStatic
    fun sendFullScreen(
        context: Context,
        content: FullScreenNotificationContent,
        options: NotificationOptions = NotificationOptions(),
        fallbackToNormalNotification: Boolean = true
    ): Boolean {
        val appContext = context.applicationContext
        if (
            !NotificationHelper.hasPostNotificationsPermission(appContext) ||
            !NotificationHelper.areNotificationsEnabled(appContext)
        ) {
            return false
        }
        if (!canUseFullScreenIntent(appContext)) {
            return fallbackToNormalNotification &&
                NotificationHelper.sendNow(appContext, content.notificationContent, options)
        }

        val channelOptions = options.copy(channelImportance = NotificationManager.IMPORTANCE_HIGH)
        NotificationHelper.createNotificationChannel(appContext, channelOptions)
        val fullScreenPendingIntent = buildFullScreenPendingIntent(appContext, content, options)
        val notification = buildNotification(appContext, content.notificationContent, channelOptions)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setContentIntent(fullScreenPendingIntent)
            .build()
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        NotificationManagerCompat.from(appContext).notify(
            options.notificationTag,
            options.notificationId,
            notification
        )
        return true
    }

    @JvmStatic
    fun sendFullScreenOnLockScreen(
        context: Context,
        content: FullScreenNotificationContent,
        options: NotificationOptions = NotificationOptions(),
        fallbackToNormalNotification: Boolean = true,
        launchActivityFallbackOnLockScreen: Boolean = true
    ): Boolean {
        val appContext = context.applicationContext
        val screenLockedOrOff = isScreenLockedOrOff(appContext)
        val shouldUseFullScreen = screenLockedOrOff &&
            canUseFullScreenIntent(appContext)

        if (shouldUseFullScreen) {
            val sent = sendFullScreen(appContext, content, options, fallbackToNormalNotification)
            if (sent && launchActivityFallbackOnLockScreen) {
                launchFullScreenActivityFallback(appContext, content, options)
            }
            return sent
        }

        return fallbackToNormalNotification &&
            NotificationHelper.sendNow(appContext, content.notificationContent, options)
    }

    @JvmStatic
    fun schedule(
        context: Context,
        request: FullScreenScheduledNotificationRequest
    ): UUID {
        val data = buildWorkData(
            content = request.content,
            options = request.options,
            fallbackToNormalNotification = request.fallbackToNormalNotification,
            launchActivityFallbackOnLockScreen = request.launchActivityFallbackOnLockScreen
        )
        val workRequest = OneTimeWorkRequestBuilder<FullScreenNotificationWorker>()
            .setInitialDelay(request.delayMillis, TimeUnit.MILLISECONDS)
            .setInputData(data)
            .addTag(WORK_TAG)
            .build()

        val workManager = WorkManager.getInstance(context.applicationContext)
        if (request.uniqueWorkName.isNullOrBlank()) {
            workManager.enqueue(workRequest)
        } else {
            workManager.enqueueUniqueWork(
                request.uniqueWorkName,
                if (request.replaceExisting) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
                workRequest
            )
        }
        return workRequest.id
    }

    @JvmStatic
    fun scheduleAfter(
        context: Context,
        delayMillis: Long,
        content: FullScreenNotificationContent,
        options: NotificationOptions = NotificationOptions(),
        uniqueWorkName: String? = null,
        fallbackToNormalNotification: Boolean = true,
        launchActivityFallbackOnLockScreen: Boolean = true
    ): UUID {
        return schedule(
            context,
            FullScreenScheduledNotificationRequest(
                content = content,
                options = options,
                delayMillis = delayMillis,
                uniqueWorkName = uniqueWorkName,
                fallbackToNormalNotification = fallbackToNormalNotification,
                launchActivityFallbackOnLockScreen = launchActivityFallbackOnLockScreen
            )
        )
    }

    @JvmStatic
    fun scheduleAfter(
        context: Context,
        hours: Long = 0L,
        minutes: Long = 0L,
        seconds: Long = 0L,
        content: FullScreenNotificationContent,
        options: NotificationOptions = NotificationOptions(),
        uniqueWorkName: String? = null,
        fallbackToNormalNotification: Boolean = true,
        launchActivityFallbackOnLockScreen: Boolean = true
    ): UUID {
        return scheduleAfter(
            context = context,
            delayMillis = delayMillisFrom(hours, minutes, seconds),
            content = content,
            options = options,
            uniqueWorkName = uniqueWorkName,
            fallbackToNormalNotification = fallbackToNormalNotification,
            launchActivityFallbackOnLockScreen = launchActivityFallbackOnLockScreen
        )
    }

    @JvmStatic
    fun scheduleAt(
        context: Context,
        triggerAtMillis: Long,
        content: FullScreenNotificationContent,
        options: NotificationOptions = NotificationOptions(),
        uniqueWorkName: String? = null,
        fallbackToNormalNotification: Boolean = true,
        launchActivityFallbackOnLockScreen: Boolean = true
    ): UUID {
        val delayMillis = (triggerAtMillis - System.currentTimeMillis()).coerceAtLeast(0L)
        return scheduleAfter(
            context,
            delayMillis,
            content,
            options,
            uniqueWorkName,
            fallbackToNormalNotification,
            launchActivityFallbackOnLockScreen
        )
    }

    @JvmStatic
    fun scheduleAt(
        context: Context,
        year: Int,
        month: Int,
        dayOfMonth: Int,
        hourOfDay: Int,
        minute: Int,
        second: Int = 0,
        content: FullScreenNotificationContent,
        options: NotificationOptions = NotificationOptions(),
        uniqueWorkName: String? = null,
        fallbackToNormalNotification: Boolean = true,
        launchActivityFallbackOnLockScreen: Boolean = true
    ): UUID {
        return scheduleAt(
            context = context,
            triggerAtMillis = triggerAtMillisFrom(
                year = year,
                month = month,
                dayOfMonth = dayOfMonth,
                hourOfDay = hourOfDay,
                minute = minute,
                second = second
            ),
            content = content,
            options = options,
            uniqueWorkName = uniqueWorkName,
            fallbackToNormalNotification = fallbackToNormalNotification,
            launchActivityFallbackOnLockScreen = launchActivityFallbackOnLockScreen
        )
    }

    @JvmStatic
    fun scheduleDaily(
        context: Context,
        request: FullScreenDailyNotificationRequest
    ): UUID {
        val data = buildWorkData(
            content = request.content,
            options = request.options,
            fallbackToNormalNotification = request.fallbackToNormalNotification,
            launchActivityFallbackOnLockScreen = request.launchActivityFallbackOnLockScreen
        )
        val workRequest = PeriodicWorkRequestBuilder<FullScreenNotificationWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(nextDailyDelayMillis(request.hourOfDay, request.minute), TimeUnit.MILLISECONDS)
            .setInputData(data)
            .addTag(WORK_TAG)
            .build()

        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            request.uniqueWorkName,
            if (request.replaceExisting) {
                ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE
            } else {
                ExistingPeriodicWorkPolicy.KEEP
            },
            workRequest
        )
        return workRequest.id
    }

    @JvmStatic
    fun scheduleDaily(
        context: Context,
        hourOfDay: Int,
        minute: Int,
        content: FullScreenNotificationContent,
        options: NotificationOptions = NotificationOptions(),
        uniqueWorkName: String = DEFAULT_DAILY_WORK_NAME,
        fallbackToNormalNotification: Boolean = true,
        launchActivityFallbackOnLockScreen: Boolean = true
    ): UUID {
        return scheduleDaily(
            context,
            FullScreenDailyNotificationRequest(
                content = content,
                hourOfDay = hourOfDay,
                minute = minute,
                options = options,
                uniqueWorkName = uniqueWorkName,
                fallbackToNormalNotification = fallbackToNormalNotification,
                launchActivityFallbackOnLockScreen = launchActivityFallbackOnLockScreen
            )
        )
    }

    @JvmStatic
    fun cancelScheduled(context: Context, uniqueWorkName: String) {
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(uniqueWorkName)
    }

    @JvmStatic
    fun cancelAllScheduled(context: Context) {
        WorkManager.getInstance(context.applicationContext).cancelAllWorkByTag(WORK_TAG)
    }

    @JvmStatic
    fun isScreenLockedOrOff(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        return !powerManager.isInteractive ||
            keyguardManager.isDeviceLocked ||
            keyguardManager.isKeyguardLocked
    }

    private fun buildNotification(
        context: Context,
        content: NotificationContent,
        options: NotificationOptions
    ): NotificationCompat.Builder {
        val builder = NotificationCompat.Builder(context, options.channelId)
            .setSmallIcon(resolveSmallIcon(options))
            .setContentTitle(content.title)
            .setContentText(content.message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content.bigText ?: content.message))
            .setPriority(options.priority)
            .setCategory(options.category)
            .setAutoCancel(options.autoCancel)
            .setOnlyAlertOnce(options.onlyAlertOnce)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setShowWhen(true)
            .setWhen(System.currentTimeMillis())

        options.color?.let(builder::setColor)
        return builder
    }

    private fun buildFullScreenPendingIntent(
        context: Context,
        content: FullScreenNotificationContent,
        options: NotificationOptions
    ): PendingIntent {
        return PendingIntent.getActivity(
            context,
            options.requestCode + FULL_SCREEN_REQUEST_CODE_OFFSET,
            buildFullScreenActivityIntent(context, content, options),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            buildPendingIntentCreatorActivityOptions()
        )
    }

    private fun buildFullScreenActivityIntent(
        context: Context,
        content: FullScreenNotificationContent,
        options: NotificationOptions
    ): Intent {
        val normal = content.notificationContent
        return Intent(context, NotificationFullScreenActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(NotificationFullScreenActivity.EXTRA_LAYOUT_RES_ID, content.layoutResId)
            putExtra(NotificationFullScreenActivity.EXTRA_APP_ICON_RES_ID, content.appIconResId)
            putExtra(NotificationFullScreenActivity.EXTRA_APP_ICON_URL, content.appIconUrl)
            putExtra(NotificationFullScreenActivity.EXTRA_IMAGE_RES_ID, content.imageResId)
            putExtra(NotificationFullScreenActivity.EXTRA_IMAGE_URL, content.imageUrl)
            putExtra(NotificationFullScreenActivity.EXTRA_TITLE, content.fullScreenTitle ?: normal.title)
            putExtra(NotificationFullScreenActivity.EXTRA_MESSAGE, content.fullScreenMessage ?: normal.message)
            putExtra(NotificationFullScreenActivity.EXTRA_TIME_TEXT, content.timeText)
            putExtra(NotificationFullScreenActivity.EXTRA_DATE_TEXT, content.dateText)
            putExtra(NotificationFullScreenActivity.EXTRA_ACTION_TEXT, content.actionText)
            putExtra(NotificationFullScreenActivity.EXTRA_DISMISS_TEXT, content.dismissText)
            putExtra(NotificationFullScreenActivity.EXTRA_NOTIFICATION_ID, options.notificationId)
            putExtra(NotificationFullScreenActivity.EXTRA_NOTIFICATION_TAG, options.notificationTag)
            putExtra(
                NotificationFullScreenActivity.EXTRA_TARGET_INTENT_URI,
                normal.targetIntent?.toUri(Intent.URI_INTENT_SCHEME)
            )
        }
    }

    private fun launchFullScreenActivityFallback(
        context: Context,
        content: FullScreenNotificationContent,
        options: NotificationOptions
    ): Boolean {
        return runCatching {
            val pendingIntent = buildFullScreenPendingIntent(context, content, options)
            val senderOptions = buildPendingIntentSenderActivityOptions()
            if (senderOptions == null) {
                pendingIntent.send()
            } else {
                pendingIntent.send(context, 0, null, null, null, null, senderOptions)
            }
            true
        }.onFailure { error ->
            Log.w(TAG, "Full-screen activity fallback launch failed", error)
        }.getOrDefault(false)
    }

    private fun buildPendingIntentCreatorActivityOptions(): Bundle? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return null
        return ActivityOptions.makeBasic()
            .setPendingIntentCreatorBackgroundActivityStartMode(
                requiredBackgroundActivityStartMode()
            )
            .toBundle()
    }

    private fun buildPendingIntentSenderActivityOptions(): Bundle? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return null
        return ActivityOptions.makeBasic()
            .setPendingIntentBackgroundActivityStartMode(
                requiredBackgroundActivityStartMode()
            )
            .toBundle()
    }

    @Suppress("DEPRECATION")
    private fun requiredBackgroundActivityStartMode(): Int {
        return if (Build.VERSION.SDK_INT >= 36) {
            ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS
        } else {
            ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
        }
    }

    private fun buildWorkData(
        content: FullScreenNotificationContent,
        options: NotificationOptions,
        fallbackToNormalNotification: Boolean,
        launchActivityFallbackOnLockScreen: Boolean
    ) = androidx.work.Data.Builder()
        .also { content.toData(it) }
        .also { options.toFullScreenData(it) }
        .putBoolean(FullScreenDataKeys.FALLBACK_TO_NORMAL_NOTIFICATION, fallbackToNormalNotification)
        .putBoolean(
            FullScreenDataKeys.LAUNCH_ACTIVITY_FALLBACK_ON_LOCK_SCREEN,
            launchActivityFallbackOnLockScreen
        )
        .build()

    private fun nextDailyDelayMillis(hourOfDay: Int, minute: Int): Long {
        val now = Calendar.getInstance()
        val next = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hourOfDay)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (!after(now)) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        return (next.timeInMillis - now.timeInMillis).coerceAtLeast(0L)
    }

    private fun triggerAtMillisFrom(
        year: Int,
        month: Int,
        dayOfMonth: Int,
        hourOfDay: Int,
        minute: Int,
        second: Int
    ): Long {
        require(year >= 1) { "year must be >= 1" }
        require(month in 1..12) { "month must be in 1..12" }
        require(dayOfMonth in 1..31) { "dayOfMonth must be in 1..31" }
        require(hourOfDay in 0..23) { "hourOfDay must be in 0..23" }
        require(minute in 0..59) { "minute must be in 0..59" }
        require(second in 0..59) { "second must be in 0..59" }

        return runCatching {
            Calendar.getInstance().apply {
                isLenient = false
                clear()
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, month - 1)
                set(Calendar.DAY_OF_MONTH, dayOfMonth)
                set(Calendar.HOUR_OF_DAY, hourOfDay)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, second)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        }.getOrElse { error ->
            throw IllegalArgumentException("date/time is invalid", error)
        }
    }

    private fun delayMillisFrom(hours: Long, minutes: Long, seconds: Long): Long {
        require(hours >= 0L) { "hours must be >= 0" }
        require(minutes >= 0L) { "minutes must be >= 0" }
        require(seconds >= 0L) { "seconds must be >= 0" }
        return runCatching {
            Math.addExact(
                Math.addExact(
                    Math.multiplyExact(hours, MILLIS_PER_HOUR),
                    Math.multiplyExact(minutes, MILLIS_PER_MINUTE)
                ),
                Math.multiplyExact(seconds, MILLIS_PER_SECOND)
            )
        }.getOrElse { error ->
            throw IllegalArgumentException("delay is too large", error)
        }
    }

    private fun resolveSmallIcon(options: NotificationOptions): Int {
        return options.smallIconResId.takeIf { it != 0 } ?: BaseR.drawable.nh_ic_notification
    }

    private fun notificationManager(context: Context): NotificationManager {
        return context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    private fun appDetailsSettingsIntent(context: Context): Intent {
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
    }

    private fun startActivitySafely(context: Context, intent: Intent): Boolean {
        return runCatching {
            if (context !is Activity) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }
}
