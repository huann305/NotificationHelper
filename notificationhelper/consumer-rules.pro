# WorkManager instantiates workers by class name.
-keep class com.huann305.notificationhelper.NotificationWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
