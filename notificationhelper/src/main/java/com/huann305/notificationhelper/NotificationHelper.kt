package com.huann305.notificationhelper

import android.Manifest
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.Looper
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.RemoteViews
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.bumptech.glide.Glide
import com.huann305.notificationhelper.core.R
import java.util.Calendar
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

object NotificationHelper {
    const val DEFAULT_CHANNEL_ID = "notification_helper_default"
    const val DEFAULT_CHANNEL_NAME = "Notifications"
    const val DEFAULT_CHANNEL_DESCRIPTION = "General app notifications"
    const val DEFAULT_NOTIFICATION_ID = 305
    const val REQUEST_CODE_POST_NOTIFICATIONS = 1305
    const val DEFAULT_DAILY_WORK_NAME = "notification_helper_daily"

    private const val WORK_TAG = "notification_helper_work"
    private const val DEFAULT_DRAWABLE_BITMAP_SIZE_PX = 128
    private const val REMOTE_ICON_SIZE_PX = 256
    private const val REMOTE_IMAGE_WIDTH_PX = 1024
    private const val REMOTE_IMAGE_HEIGHT_PX = 512
    private const val MILLIS_PER_SECOND = 1_000L
    private const val MILLIS_PER_MINUTE = 60_000L
    private const val MILLIS_PER_HOUR = 3_600_000L
    private val remoteImageExecutor = Executors.newSingleThreadExecutor()

    @JvmStatic
    fun createNotificationChannel(
        context: Context,
        options: NotificationOptions = NotificationOptions()
    ) {
        ensureChannel(context.applicationContext, options)
    }

    @JvmStatic
    fun hasPostNotificationsPermission(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
    }

    @JvmStatic
    fun areNotificationsEnabled(context: Context): Boolean {
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    @JvmStatic
    fun requestPostNotificationsPermission(
        activity: Activity,
        requestCode: Int = REQUEST_CODE_POST_NOTIFICATIONS
    ): Boolean {
        if (hasPostNotificationsPermission(activity)) return true
        ActivityCompat.requestPermissions(
            activity,
            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
            requestCode
        )
        return false
    }

    @JvmStatic
    fun openAppNotificationSettings(context: Context): Boolean {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        }
        return startActivitySafely(context, intent)
    }

    @JvmStatic
    fun openNotificationChannelSettings(context: Context, channelId: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || channelId.isBlank()) {
            return openAppNotificationSettings(context)
        }
        val intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            putExtra(Settings.EXTRA_CHANNEL_ID, channelId)
        }
        return startActivitySafely(context, intent) || openAppNotificationSettings(context)
    }

    @JvmStatic
    fun getNotificationChannelImportance(context: Context, channelId: String): Int? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || channelId.isBlank()) return null
        return notificationManager(context)
            .getNotificationChannel(channelId)
            ?.importance
    }

    @JvmStatic
    fun isNotificationChannelEnabled(context: Context, channelId: String): Boolean {
        val importance = getNotificationChannelImportance(context, channelId)
        return importance == null || importance != NotificationManager.IMPORTANCE_NONE
    }

    @JvmStatic
    fun sendNow(
        context: Context,
        content: NotificationContent,
        options: NotificationOptions = NotificationOptions()
    ): Boolean {
        val appContext = context.applicationContext
        if (!canPostNotification(appContext)) return false
        ensureChannel(appContext, options)

        val resolvedImages = if (shouldLoadRemoteImagesBeforeNotify(content)) {
            loadRemoteImages(appContext, content.bigLayout)
        } else {
            ResolvedNotificationImages()
        }
        val notification = buildNotification(appContext, content, options, resolvedImages).build()
        if (!notifyNotification(appContext, options, notification)) return false

        if (shouldUpdateRemoteImagesAfterNotify(content)) {
            remoteImageExecutor.execute {
                val remoteImages = loadRemoteImages(appContext, content.bigLayout)
                if (remoteImages.hasAny) {
                    val updatedNotification = buildNotification(
                        appContext,
                        content,
                        options,
                        remoteImages
                    ).build()
                    notifyNotification(appContext, options, updatedNotification)
                }
            }
        }
        return true
    }

    private fun notifyNotification(
        context: Context,
        options: NotificationOptions,
        notification: Notification
    ): Boolean {
        return runCatching {
            NotificationManagerCompat.from(context).notify(
                options.notificationTag,
                options.notificationId,
                notification
            )
            true
        }.getOrDefault(false)
    }

    @JvmStatic
    fun schedule(
        context: Context,
        request: ScheduledNotificationRequest
    ): UUID {
        val data = buildWorkData(request.content, request.options)
        val workRequest = OneTimeWorkRequestBuilder<NotificationWorker>()
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
        content: NotificationContent,
        options: NotificationOptions = NotificationOptions(),
        uniqueWorkName: String? = null
    ): UUID {
        return schedule(
            context,
            ScheduledNotificationRequest(
                content = content,
                options = options,
                delayMillis = delayMillis,
                uniqueWorkName = uniqueWorkName
            )
        )
    }

    @JvmStatic
    fun scheduleAfter(
        context: Context,
        hours: Long = 0L,
        minutes: Long = 0L,
        seconds: Long = 0L,
        content: NotificationContent,
        options: NotificationOptions = NotificationOptions(),
        uniqueWorkName: String? = null
    ): UUID {
        return scheduleAfter(
            context = context,
            delayMillis = delayMillisFrom(hours, minutes, seconds),
            content = content,
            options = options,
            uniqueWorkName = uniqueWorkName
        )
    }

    @JvmStatic
    fun scheduleAt(
        context: Context,
        triggerAtMillis: Long,
        content: NotificationContent,
        options: NotificationOptions = NotificationOptions(),
        uniqueWorkName: String? = null
    ): UUID {
        val delayMillis = (triggerAtMillis - System.currentTimeMillis()).coerceAtLeast(0L)
        return scheduleAfter(
            context,
            delayMillis,
            content,
            options,
            uniqueWorkName
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
        content: NotificationContent,
        options: NotificationOptions = NotificationOptions(),
        uniqueWorkName: String? = null
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
            uniqueWorkName = uniqueWorkName
        )
    }

    @JvmStatic
    fun scheduleDaily(
        context: Context,
        request: DailyNotificationRequest
    ): UUID {
        val data = buildWorkData(request.content, request.options)
        val workRequest = PeriodicWorkRequestBuilder<NotificationWorker>(1, TimeUnit.DAYS)
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
        content: NotificationContent,
        options: NotificationOptions = NotificationOptions(),
        uniqueWorkName: String = DEFAULT_DAILY_WORK_NAME
    ): UUID {
        return scheduleDaily(
            context,
            DailyNotificationRequest(
                content = content,
                hourOfDay = hourOfDay,
                minute = minute,
                options = options,
                uniqueWorkName = uniqueWorkName
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

    private fun canPostNotification(context: Context): Boolean {
        return hasPostNotificationsPermission(context) && areNotificationsEnabled(context)
    }

    private fun ensureChannel(context: Context, options: NotificationOptions) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            options.channelId,
            options.channelName,
            options.channelImportance
        ).apply {
            description = options.channelDescription
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            enableVibration(true)
        }
        notificationManager(context).createNotificationChannel(channel)
    }

    private fun buildNotification(
        context: Context,
        content: NotificationContent,
        options: NotificationOptions,
        resolvedImages: ResolvedNotificationImages = ResolvedNotificationImages()
    ): NotificationCompat.Builder {
        val builder = NotificationCompat.Builder(context, options.channelId)
            .setSmallIcon(resolveSmallIcon(options))
            .setContentTitle(content.title)
            .setContentText(content.message)
            .setPriority(options.priority)
            .setCategory(options.category)
            .setAutoCancel(options.autoCancel)
            .setOnlyAlertOnce(options.onlyAlertOnce)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setShowWhen(true)
            .setWhen(System.currentTimeMillis())

        options.color?.let(builder::setColor)
        val contentIntent = buildContentPendingIntent(context, content, options)
        contentIntent?.let(builder::setContentIntent)
        if (content.bigLayout.isEnabled) {
            applyBigNotificationLayout(context, builder, content, options, contentIntent, resolvedImages)
        } else {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(content.bigText ?: content.message))
        }
        return builder
    }

    private fun applyBigNotificationLayout(
        context: Context,
        builder: NotificationCompat.Builder,
        content: NotificationContent,
        options: NotificationOptions,
        contentIntent: PendingIntent?,
        resolvedImages: ResolvedNotificationImages
    ) {
        val layout = content.bigLayout
        if (!layout.usesCustomView) {
            applySystemBigNotificationStyle(
                context,
                builder,
                content,
                options,
                layout,
                contentIntent,
                resolvedImages
            )
            return
        }

        val compactLayout = layout.compactLayoutResId
            .takeIf { it != 0 }
            ?.let { resolveRemoteViewsLayout(context, it, 0) }
            ?.takeIf { it != 0 }
        val expandedLayout = resolveRemoteViewsLayout(
            context = context,
            layoutResId = layout.expandedLayoutResId.takeIf { it != 0 } ?: R.layout.nh_notification_big,
            fallbackLayoutResId = R.layout.nh_notification_big
        )

        val expandedViews = buildNotificationRemoteViews(
            context = context,
            layoutResId = expandedLayout,
            content = content,
            layout = layout,
            contentIntent = contentIntent,
            isExpanded = true,
            resolvedImages = resolvedImages
        )

        builder.setCustomBigContentView(expandedViews)

        compactLayout?.let { layoutResId ->
            val compactViews = buildNotificationRemoteViews(
                context = context,
                layoutResId = layoutResId,
                content = content,
                layout = layout,
                contentIntent = contentIntent,
                isExpanded = false,
                resolvedImages = resolvedImages
            )
            builder.setCustomContentView(compactViews)
        }

        if (layout.decorateCustomView) {
            builder.setStyle(NotificationCompat.DecoratedCustomViewStyle())
        }
    }

    private fun applySystemBigNotificationStyle(
        context: Context,
        builder: NotificationCompat.Builder,
        content: NotificationContent,
        options: NotificationOptions,
        layout: BigNotificationLayout,
        contentIntent: PendingIntent?,
        resolvedImages: ResolvedNotificationImages
    ) {
        (resolvedImages.iconBitmap ?: loadBitmap(context, layout.iconResId))?.let(builder::setLargeIcon)
        val bigText = content.bigText ?: content.message
        val bigPicture = resolvedImages.imageBitmap ?: loadBitmap(context, layout.imageResId)

        if (bigPicture != null) {
            builder.setStyle(
                NotificationCompat.BigPictureStyle()
                    .bigPicture(bigPicture)
                    .setBigContentTitle(content.title)
                    .setSummaryText(bigText)
            )
        } else {
            builder.setStyle(
                NotificationCompat.BigTextStyle()
                    .setBigContentTitle(content.title)
                    .bigText(bigText)
            )
        }

        if (contentIntent != null && layout.actionText.isNotBlank()) {
            builder.addAction(resolveSmallIcon(options), layout.actionText, contentIntent)
        }
    }

    private fun buildNotificationRemoteViews(
        context: Context,
        layoutResId: Int,
        content: NotificationContent,
        layout: BigNotificationLayout,
        contentIntent: PendingIntent?,
        isExpanded: Boolean,
        resolvedImages: ResolvedNotificationImages
    ): RemoteViews {
        val views = RemoteViews(context.packageName, layoutResId)
        val bindings = availableBindings(context, layoutResId)

        fun hasView(id: Int) = bindings.viewIds.contains(id)
        fun hasText(id: Int) = bindings.textViewIds.contains(id)
        fun hasImage(id: Int) = bindings.imageViewIds.contains(id)

        if (hasText(R.id.notification_helper_normal_title)) {
            views.setTextViewText(R.id.notification_helper_normal_title, content.title)
        }
        if (hasText(R.id.notification_helper_normal_message)) {
            views.setTextViewText(
                R.id.notification_helper_normal_message,
                if (isExpanded) content.bigText ?: content.message else content.message
            )
        }
        if (hasText(R.id.notification_helper_normal_primary_action)) {
            views.setTextViewText(
                R.id.notification_helper_normal_primary_action,
                layout.actionText
            )
        }

        bindImage(
            views = views,
            imageViewIds = bindings.imageViewIds,
            viewId = R.id.notification_helper_normal_icon,
            bitmap = resolvedImages.iconBitmap,
            imageResId = layout.iconResId
        )
        bindImage(
            views = views,
            imageViewIds = bindings.imageViewIds,
            viewId = R.id.notification_helper_normal_image,
            bitmap = resolvedImages.imageBitmap,
            imageResId = layout.imageResId
        )

        contentIntent?.let { pendingIntent ->
            if (hasView(R.id.notification_helper_normal_root)) {
                views.setOnClickPendingIntent(R.id.notification_helper_normal_root, pendingIntent)
            }
            if (hasView(R.id.notification_helper_normal_primary_action)) {
                views.setOnClickPendingIntent(
                    R.id.notification_helper_normal_primary_action,
                    pendingIntent
                )
            }
        }

        return views
    }

    private fun bindImage(
        views: RemoteViews,
        imageViewIds: Set<Int>,
        viewId: Int,
        bitmap: Bitmap?,
        imageResId: Int
    ) {
        if (!imageViewIds.contains(viewId)) return
        when {
            bitmap != null -> {
                views.setViewVisibility(viewId, View.VISIBLE)
                views.setImageViewBitmap(viewId, bitmap)
            }

            imageResId != 0 -> {
                views.setViewVisibility(viewId, View.VISIBLE)
                views.setImageViewResource(viewId, imageResId)
            }

            else -> {
                views.setViewVisibility(viewId, View.GONE)
            }
        }
    }

    private fun availableBindings(context: Context, layoutResId: Int): RemoteLayoutBindings {
        val root = runCatching {
            LayoutInflater.from(context).inflate(layoutResId, null, false)
        }.getOrNull() ?: return RemoteLayoutBindings()

        val viewIds = mutableSetOf<Int>()
        val textViewIds = mutableSetOf<Int>()
        val imageViewIds = mutableSetOf<Int>()

        NOTIFICATION_LAYOUT_IDS.forEach { id ->
            val view = root.findViewById<View>(id) ?: return@forEach
            viewIds.add(id)
            if (view is TextView) textViewIds.add(id)
            if (view is ImageView) imageViewIds.add(id)
        }

        return RemoteLayoutBindings(
            viewIds = viewIds,
            textViewIds = textViewIds,
            imageViewIds = imageViewIds
        )
    }

    private fun resolveRemoteViewsLayout(
        context: Context,
        layoutResId: Int,
        fallbackLayoutResId: Int
    ): Int {
        if (canInflateLayout(context, layoutResId)) return layoutResId
        return fallbackLayoutResId.takeIf { it != 0 && canInflateLayout(context, it) } ?: 0
    }

    private fun canInflateLayout(context: Context, layoutResId: Int): Boolean {
        if (layoutResId == 0) return false
        return runCatching {
            LayoutInflater.from(context).inflate(layoutResId, null, false)
            true
        }.getOrDefault(false)
    }

    private fun shouldLoadRemoteImagesBeforeNotify(content: NotificationContent): Boolean {
        return content.bigLayout.isEnabled &&
            content.bigLayout.hasRemoteImages &&
            !isMainThread()
    }

    private fun shouldUpdateRemoteImagesAfterNotify(content: NotificationContent): Boolean {
        return content.bigLayout.isEnabled &&
            content.bigLayout.hasRemoteImages &&
            isMainThread()
    }

    private fun isMainThread(): Boolean {
        return Looper.myLooper() == Looper.getMainLooper()
    }

    private fun loadRemoteImages(
        context: Context,
        layout: BigNotificationLayout
    ): ResolvedNotificationImages {
        return ResolvedNotificationImages(
            iconBitmap = loadBitmapFromUrl(
                context = context,
                url = layout.iconUrl,
                width = REMOTE_ICON_SIZE_PX,
                height = REMOTE_ICON_SIZE_PX
            ),
            imageBitmap = loadBitmapFromUrl(
                context = context,
                url = layout.imageUrl,
                width = REMOTE_IMAGE_WIDTH_PX,
                height = REMOTE_IMAGE_HEIGHT_PX
            )
        )
    }

    private fun loadBitmapFromUrl(
        context: Context,
        url: String?,
        width: Int,
        height: Int
    ): Bitmap? {
        if (url.isNullOrBlank()) return null
        return runCatching {
            Glide.with(context.applicationContext)
                .asBitmap()
                .load(url)
                .submit(width, height)
                .get()
        }.getOrNull()
    }

    private fun loadBitmap(context: Context, drawableResId: Int): Bitmap? {
        if (drawableResId == 0) return null
        val drawable = runCatching {
            ContextCompat.getDrawable(context, drawableResId)
        }.getOrNull() ?: return null

        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            return drawable.bitmap
        }

        val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: DEFAULT_DRAWABLE_BITMAP_SIZE_PX
        val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: DEFAULT_DRAWABLE_BITMAP_SIZE_PX
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    private fun buildContentPendingIntent(
        context: Context,
        content: NotificationContent,
        options: NotificationOptions
    ): PendingIntent? {
        val intent = content.targetIntent ?: context.packageManager.getLaunchIntentForPackage(context.packageName)
        intent ?: return null
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context,
            options.requestCode,
            intent,
            pendingIntentFlags()
        )
    }

    private fun buildWorkData(
        content: NotificationContent,
        options: NotificationOptions
    ) = androidx.work.Data.Builder()
        .also { content.toData(it) }
        .also { options.toData(it) }
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
        return options.smallIconResId.takeIf { it != 0 } ?: R.drawable.nh_ic_notification
    }

    private fun pendingIntentFlags(): Int {
        return PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    }

    private fun notificationManager(context: Context): NotificationManager {
        return context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
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

    private val NOTIFICATION_LAYOUT_IDS = intArrayOf(
        R.id.notification_helper_normal_root,
        R.id.notification_helper_normal_icon,
        R.id.notification_helper_normal_title,
        R.id.notification_helper_normal_message,
        R.id.notification_helper_normal_image,
        R.id.notification_helper_normal_primary_action
    )

    private data class RemoteLayoutBindings(
        val viewIds: Set<Int> = emptySet(),
        val textViewIds: Set<Int> = emptySet(),
        val imageViewIds: Set<Int> = emptySet()
    )

    private data class ResolvedNotificationImages(
        val iconBitmap: Bitmap? = null,
        val imageBitmap: Bitmap? = null
    ) {
        val hasAny: Boolean
            get() = iconBitmap != null || imageBitmap != null
    }
}
