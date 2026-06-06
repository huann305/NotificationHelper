# NotificationHelper

Android notification helper library with two separate artifacts:

| Artifact | Purpose | Sensitive permission |
| --- | --- | --- |
| `notificationhelper` | Normal notifications, POST_NOTIFICATIONS permission helper, immediate send, AlarmManager/WorkManager scheduling, big notifications, optional custom RemoteViews layouts, URL images. | None beyond normal notification permission. |
| `notificationhelper-fullscreen` | Optional full-screen lock-screen notifications and full-screen layout UI. | `USE_FULL_SCREEN_INTENT` |

Import `notificationhelper-fullscreen` only in apps that really need full-screen lock-screen behavior. Apps that only use normal notifications should import `notificationhelper` only.

## Install

After publishing to Maven Local or your Maven repository:

```kotlin
dependencies {
    implementation("io.github.huann305:notificationhelper:1.0.2")

    // Optional. Import only when the app needs lock-screen full-screen notifications.
    implementation("io.github.huann305:notificationhelper-fullscreen:1.0.2")
}
```

For local development inside this sample project:

```kotlin
dependencies {
    implementation(project(":notificationhelper"))
    implementation(project(":notificationhelper-fullscreen"))
}
```

## Permissions

The base artifact merges:

```xml
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.INTERNET" />
```

`INTERNET` is used only when `iconUrl`, `imageUrl`, `appIconUrl`, or full-screen image URLs are used. It is a normal Android permission.

The optional full-screen artifact also merges:

```xml
<uses-permission android:name="android.permission.USE_FULL_SCREEN_INTENT" />
```

## Normal Notifications

Create a channel once, or let `sendNow` create it automatically:

```kotlin
NotificationHelper.createNotificationChannel(
    context,
    NotificationOptions(
        channelId = "general",
        channelName = "General notifications"
    )
)
```

Request Android 13+ notification permission:

```kotlin
NotificationHelper.requestPostNotificationsPermission(activity)
```

Send now:

```kotlin
NotificationHelper.sendNow(
    context = context,
    content = NotificationContent(
        title = "Title",
        message = "Message",
        targetIntent = Intent(context, MainActivity::class.java)
    ),
    options = NotificationOptions(
        channelId = "general",
        notificationId = 1001,
        smallIconResId = R.drawable.ic_notification
    )
)
```

Schedule one-time notifications. One-time `schedule`, `scheduleAfter`, and `scheduleAt` use `AlarmManager` by default so the notification can still fire if the app process is killed from Recent Apps. No exact-alarm permission is added.

```kotlin
NotificationHelper.scheduleAfter(
    context = context,
    delayMillis = 10_000L,
    content = NotificationContent("Scheduled", "Sent after 10 seconds"),
    options = NotificationOptions(notificationId = 1002),
    uniqueWorkName = "scheduled_notification"
)

NotificationHelper.scheduleAfter(
    context = context,
    hours = 1,
    minutes = 30,
    seconds = 0,
    content = NotificationContent("Scheduled", "Sent after 1 hour 30 minutes"),
    options = NotificationOptions(notificationId = 1004),
    uniqueWorkName = "scheduled_notification_duration"
)

NotificationHelper.scheduleAt(
    context = context,
    year = 2026,
    month = 12,
    dayOfMonth = 31,
    hourOfDay = 23,
    minute = 59,
    second = 0,
    content = NotificationContent("Scheduled", "Sent at a specific date and time"),
    options = NotificationOptions(notificationId = 1005),
    uniqueWorkName = "scheduled_notification_at"
)

NotificationHelper.scheduleDaily(
    context = context,
    hourOfDay = 9,
    minute = 0,
    content = NotificationContent("Daily", "Sent every day"),
    options = NotificationOptions(notificationId = 1003),
    uniqueWorkName = "daily_notification"
)
```

`AlarmManager` scheduling is still not guaranteed after a real force-stop. Some OEMs, including Xiaomi/HyperOS builds, can treat clearing Recent Apps as a force-stop; in that state Android blocks app alarms, jobs, and broadcasts until the user opens the app again. Ask users to allow autostart/no battery restriction for reminder-style apps.
Daily schedules use WorkManager.

To force WorkManager for one-time work:

```kotlin
NotificationHelper.schedule(
    context,
    ScheduledNotificationRequest(
        content = NotificationContent("Scheduled", "Sent by WorkManager"),
        options = NotificationOptions(notificationId = 1006),
        delayMillis = 10_000L,
        uniqueWorkName = "workmanager_notification",
        backend = NotificationScheduleBackend.WORK_MANAGER
    )
)
```

For `scheduleAt(year, month, dayOfMonth, hourOfDay, minute, second, ...)`, `month` uses the natural range `1..12`.

## Big Notifications

Use Android's native expanded style by passing `BigNotificationLayout()` without `CUSTOM_VIEW`:

```kotlin
NotificationHelper.sendNow(
    context = context,
    content = NotificationContent(
        title = "Big notification",
        message = "Collapsed text",
        bigText = "Expanded text",
        targetIntent = Intent(context, MainActivity::class.java),
        bigLayout = BigNotificationLayout(
            imageUrl = "https://example.com/image.jpg",
            actionText = "Open"
        )
    )
)
```

Native style does not use XML. It fits system and lock-screen notification cards best.

Use XML only when you need a custom `RemoteViews` layout:

```kotlin
NotificationHelper.sendNow(
    context = context,
    content = NotificationContent(
        title = "XML notification",
        message = "Collapsed text",
        bigText = "Expanded text",
        targetIntent = Intent(context, MainActivity::class.java),
        bigLayout = BigNotificationLayout(
            mode = BigNotificationLayoutMode.CUSTOM_VIEW,
            expandedLayoutResId = R.layout.my_notification_big,
            imageUrl = "https://example.com/image.jpg"
        )
    )
)
```

If `expandedLayoutResId` is omitted in `CUSTOM_VIEW` mode, the library uses its built-in expanded XML layout. If `compactLayoutResId` is omitted, the collapsed notification and heads-up banner use the system template. This avoids clipping on OEM heads-up notifications.

Custom notification layouts must be Android `RemoteViews` compatible. Add only the ids your layout needs:

```xml
@id/notification_helper_normal_root
@id/notification_helper_normal_title
@id/notification_helper_normal_message
@id/notification_helper_normal_image
@id/notification_helper_normal_primary_action
```

Optional icon id:

```xml
@id/notification_helper_normal_icon
```

The helper binds text only to `TextView` ids and images only to `ImageView` ids, so custom layouts with missing or differently typed optional ids are skipped safely.

## Full-Screen Lock-Screen Notifications

Import the optional artifact only when needed:

```kotlin
dependencies {
    implementation("io.github.huann305:notificationhelper-fullscreen:1.0.2")
}
```

Check and request full-screen intent permission on Android 14+:

```kotlin
val launcher = registerForActivityResult(
    ActivityResultContracts.StartActivityForResult()
) {
    val enabled = FullScreenNotificationHelper.canUseFullScreenIntent(this)
}

if (FullScreenNotificationHelper.shouldRequestFullScreenIntentPermission(this)) {
    launcher.launch(
        FullScreenNotificationHelper.createFullScreenIntentPermissionIntent(this)
    )
}
```

Some OEMs also require lock-screen display permission:

```kotlin
if (!FullScreenNotificationHelper.canShowOnLockScreen(context)) {
    FullScreenNotificationHelper.openLockScreenSettings(context)
}
```

Send full-screen only when the phone is locked or screen is off:

```kotlin
FullScreenNotificationHelper.sendFullScreenOnLockScreen(
    context = context,
    content = FullScreenNotificationContent(
        notificationContent = NotificationContent(
            title = "Full-screen",
            message = "Shown when the device is locked",
            targetIntent = Intent(context, MainActivity::class.java)
        ),
        layoutResId = R.layout.my_full_screen_notification,
        appIconUrl = "https://example.com/logo.png",
        imageUrl = "https://example.com/image.jpg",
        actionText = "Open",
        dismissText = "Dismiss"
    ),
    options = NotificationOptions(notificationId = 2001),
    fallbackToNormalNotification = true
)
```

Schedule full-screen notification:

```kotlin
FullScreenNotificationHelper.scheduleAfter(
    context = context,
    hours = 0,
    minutes = 0,
    seconds = 10,
    content = FullScreenNotificationContent(
        notificationContent = NotificationContent(
            title = "Scheduled full-screen",
            message = "Shown full-screen if the phone is locked"
        )
    ),
    options = NotificationOptions(notificationId = 2002),
    uniqueWorkName = "scheduled_fullscreen",
    fallbackToNormalNotification = true,
    launchActivityFallbackOnLockScreen = true
)

FullScreenNotificationHelper.scheduleAt(
    context = context,
    year = 2026,
    month = 12,
    dayOfMonth = 31,
    hourOfDay = 23,
    minute = 59,
    second = 0,
    content = FullScreenNotificationContent(
        notificationContent = NotificationContent(
            title = "Scheduled full-screen",
            message = "Shown full-screen at a specific date and time"
        )
    ),
    options = NotificationOptions(notificationId = 2003),
    uniqueWorkName = "scheduled_fullscreen_at",
    fallbackToNormalNotification = true,
    launchActivityFallbackOnLockScreen = true
)
```

One-time full-screen schedules also use `AlarmManager` by default. Use `FullScreenScheduledNotificationRequest(backend = NotificationScheduleBackend.WORK_MANAGER, ...)` only when you specifically want WorkManager semantics.

If full-screen permission is missing or the device is not locked, `fallbackToNormalNotification = true` posts a normal notification instead. Set it to `false` if the app should not post a fallback card.

`launchActivityFallbackOnLockScreen = true` makes the helper send the same full-screen `PendingIntent` once more with Android background-activity-launch options after posting the standard FSI notification. This helps on some Xiaomi/HyperOS builds that downgrade a granted FSI notification to a lock-screen heads-up card. Android/OEM background activity policies can still block the launch; check logcat for `NHFullScreen` and `ActivityTaskManager` if it does.

### Full-Screen Troubleshooting

On Android 14+ the system grants full-screen notifications only through Special App Access. Android's FSI behavior is:

- unlocked screen: persistent heads-up notification
- locked/off screen: launches the full-screen intent when FSI permission is granted
- denied FSI: heads-up fallback instead of launching full-screen

For apps targeting Android 15+ and Android 16+, the library opts in on the full-screen `PendingIntent` creator side so the system is allowed to use that pending intent for a background activity launch. The scheduled fallback also sends the pending intent with sender-side background launch options.

Check runtime state:

```kotlin
val status = FullScreenNotificationHelper.getStatus(
    context,
    NotificationOptions(channelId = "full_screen_notifications")
)
```

Important fields:

```kotlin
status.canUseFullScreenIntent
status.canShowOnLockScreen
status.isScreenLockedOrOff
status.channelImportance
status.canTryFullScreenOnLockScreen
status.canAttemptFullScreenOnLockScreen
```

Use a dedicated high-importance channel for full-screen notifications. Android does not let apps raise the importance of an existing channel after the user or system has changed it. If `channelImportance` is lower than `NotificationManager.IMPORTANCE_HIGH`, create a new channel id or ask the user to update the channel settings:

```kotlin
FullScreenNotificationHelper.openFullScreenNotificationChannelSettings(
    context,
    NotificationOptions(channelId = "full_screen_notifications")
)
```

On Xiaomi/HyperOS, also enable the app's lock-screen notification permission:

```kotlin
FullScreenNotificationHelper.openLockScreenSettings(context)
```

## Custom Full-Screen Layout

Pass any host-app layout through `FullScreenNotificationContent.layoutResId`. If the layout id is invalid or omitted, the built-in default layout is used.

The full-screen Activity fills and wires optional ids:

```xml
@id/notification_helper_root
@id/notification_helper_time
@id/notification_helper_date
@id/notification_helper_close
@id/notification_helper_notification_container
@id/notification_helper_app_icon
@id/notification_helper_title
@id/notification_helper_message
@id/notification_helper_image
@id/notification_helper_primary_action
@id/notification_helper_dismiss_action
```

`notification_helper_primary_action` and `notification_helper_notification_container` open the target intent. `notification_helper_close` and `notification_helper_dismiss_action` dismiss the full-screen UI.

You can override the time/date text:

```kotlin
FullScreenNotificationContent(
    notificationContent = NotificationContent("Title", "Message"),
    timeText = "09:30",
    dateText = "Monday, Jun 8"
)
```

## Publish

See [PUBLISHING.md](PUBLISHING.md).
