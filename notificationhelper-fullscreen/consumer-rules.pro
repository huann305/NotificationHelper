# WorkManager instantiates workers by class name.
-keep class com.huann305.notificationhelper.fullscreen.FullScreenNotificationWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# Keep the full-screen activity entry point available for explicit intents.
-keep class com.huann305.notificationhelper.fullscreen.NotificationFullScreenActivity { *; }
