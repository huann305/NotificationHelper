# WorkManager instantiates workers by class name.
-keep class com.huann305.notificationhelper.NotificationWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# Keep the alarm receiver entry point available for scheduled notifications.
-keep class com.huann305.notificationhelper.NotificationAlarmReceiver { *; }
