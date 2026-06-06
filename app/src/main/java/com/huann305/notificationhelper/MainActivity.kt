package com.huann305.notificationhelper

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.huann305.notificationhelper.fullscreen.FullScreenNotificationContent
import com.huann305.notificationhelper.fullscreen.FullScreenNotificationHelper
import java.util.Calendar

class MainActivity : AppCompatActivity() {
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            showStatus(if (granted) "Notification permission granted" else "Notification permission denied")
        }

    private val fullScreenIntentPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            val granted = FullScreenNotificationHelper.canUseFullScreenIntent(this)
            showStatus(if (granted) "Full-screen notification permission enabled" else "Full-screen notification permission disabled")
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        NotificationHelper.createNotificationChannel(this, demoOptions(1))
        NotificationHelper.createNotificationChannel(this, demoFullScreenOptions(1))
        setupDemoButtons()
        refreshPermissionStatus()
    }

    private fun setupDemoButtons() {
        findViewById<View>(R.id.btnRequestNotification).setOnClickListener {
            requestNotificationPermission()
        }
        findViewById<View>(R.id.btnOpenFullScreenSettings).setOnClickListener {
            if (!FullScreenNotificationHelper.shouldRequestFullScreenIntentPermission(this)) {
                showFullScreenStatus("Full-screen notification permission is enabled")
            } else {
                fullScreenIntentPermissionLauncher.launch(
                    FullScreenNotificationHelper.createFullScreenIntentPermissionIntent(this)
                )
            }
        }
        findViewById<View>(R.id.btnOpenLockScreenSettings).setOnClickListener {
            if (FullScreenNotificationHelper.canShowOnLockScreen(this)) {
                FullScreenNotificationHelper.openFullScreenNotificationChannelSettings(
                    this,
                    demoFullScreenOptions(1)
                )
            } else {
                FullScreenNotificationHelper.openLockScreenSettings(this)
            }
        }
        findViewById<View>(R.id.btnSendNow).setOnClickListener {
            val sent = NotificationHelper.sendNow(
                this,
                NotificationContent(
                    title = "NotificationHelper",
                    message = "This notification was sent immediately.",
                    targetIntent = mainTargetIntent()
                ),
                demoOptions(1001)
            )
            showStatus(if (sent) "Sent immediate notification" else "Notification permission is missing")
        }
        findViewById<View>(R.id.btnSendBigNotification).setOnClickListener {
            val sent = NotificationHelper.sendNow(
                this,
                NotificationContent(
                    title = "Big notification",
                    message = "This notification uses the XML RemoteViews layout.",
                    bigText = "This expanded content is rendered by the library XML layout. The image is loaded from URL with Glide and then the same notification is updated.",
                    targetIntent = mainTargetIntent(),
                    bigLayout = BigNotificationLayout(
                        mode = BigNotificationLayoutMode.CUSTOM_VIEW,
                        imageUrl = "https://picsum.photos/1024/512",
                        actionText = "Open app"
                    )
                ),
                demoOptions(1007)
            )
            showStatus(if (sent) "Sent big notification with layout" else "Notification permission is missing")
        }
        findViewById<View>(R.id.btnScheduleTenSeconds).setOnClickListener {
            NotificationHelper.scheduleAfter(
                context = this,
                seconds = 10,
                content = NotificationContent(
                    title = "Scheduled notification",
                    message = "This notification was scheduled 10 seconds ago.",
                    targetIntent = mainTargetIntent()
                ),
                options = demoOptions(1002),
                uniqueWorkName = DEMO_ONE_TIME_WORK
            )
            showStatus("Scheduled one notification after 10 seconds")
        }
        findViewById<View>(R.id.btnScheduleDaily).setOnClickListener {
            val nextMinute = Calendar.getInstance().apply {
                add(Calendar.MINUTE, 1)
            }
            NotificationHelper.scheduleDaily(
                context = this,
                hourOfDay = nextMinute.get(Calendar.HOUR_OF_DAY),
                minute = nextMinute.get(Calendar.MINUTE),
                content = NotificationContent(
                    title = "Daily notification",
                    message = "This daily notification was scheduled by NotificationHelper.",
                    targetIntent = mainTargetIntent()
                ),
                options = demoOptions(1003),
                uniqueWorkName = DEMO_DAILY_WORK
            )
            showStatus("Scheduled daily notification for the next minute")
        }
        findViewById<View>(R.id.btnFullScreen).setOnClickListener {
            val sent = FullScreenNotificationHelper.sendFullScreenOnLockScreen(
                this,
                demoFullScreenContent(
                    title = "Full-screen notification",
                    message = "Lock the phone, then send this again to see the full-screen UI."
                ),
                demoFullScreenOptions(1004)
            )
            showStatus(
                if (sent) {
                    "Sent full-screen request. ${fullScreenStatusText()}"
                } else {
                    "Full-screen request failed. ${fullScreenStatusText()}"
                }
            )
        }
        findViewById<View>(R.id.btnScheduleFullScreenTenSeconds).setOnClickListener {
            FullScreenNotificationHelper.scheduleAfter(
                context = this,
                seconds = 5,
                content = demoFullScreenContent(
                    title = "Scheduled full-screen",
                    message = "This full-screen notification was scheduled 5 seconds ago."
                ),
                options = demoFullScreenOptions(1005),
                uniqueWorkName = DEMO_FULLSCREEN_ONE_TIME_WORK,
                launchActivityFallbackOnLockScreen = true
            )
            showStatus("Scheduled full-screen notification after 5 seconds. Lock the phone now. ${fullScreenStatusText()}")
        }
        findViewById<View>(R.id.btnScheduleFullScreenDaily).setOnClickListener {
            val nextMinute = Calendar.getInstance().apply {
                add(Calendar.MINUTE, 1)
            }
            FullScreenNotificationHelper.scheduleDaily(
                context = this,
                hourOfDay = nextMinute.get(Calendar.HOUR_OF_DAY),
                minute = nextMinute.get(Calendar.MINUTE),
                content = demoFullScreenContent(
                    title = "Daily full-screen",
                    message = "This daily full-screen notification was scheduled by NotificationHelper."
                ),
                options = demoFullScreenOptions(1006),
                uniqueWorkName = DEMO_FULLSCREEN_DAILY_WORK
            )
            showStatus("Scheduled daily full-screen notification for the next minute. ${fullScreenStatusText()}")
        }
        findViewById<View>(R.id.btnCancelScheduled).setOnClickListener {
            NotificationHelper.cancelScheduled(this, DEMO_ONE_TIME_WORK)
            NotificationHelper.cancelScheduled(this, DEMO_DAILY_WORK)
            FullScreenNotificationHelper.cancelScheduled(this, DEMO_FULLSCREEN_ONE_TIME_WORK)
            FullScreenNotificationHelper.cancelScheduled(this, DEMO_FULLSCREEN_DAILY_WORK)
            showStatus("Canceled demo schedules")
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            showStatus("Notification permission is granted on this Android version")
        }
    }

    private fun refreshPermissionStatus() {
        val notification = NotificationHelper.hasPostNotificationsPermission(this)
        val status = FullScreenNotificationHelper.getStatus(this, demoFullScreenOptions(1))
        showStatus(
            "notification=$notification, ${formatFullScreenStatus(status)}"
        )
    }

    private fun demoOptions(notificationId: Int): NotificationOptions {
        return NotificationOptions(
            channelId = "demo_notifications",
            channelName = "Demo notifications",
            channelDescription = "Notifications created from the demo app",
            notificationId = notificationId,
            smallIconResId = R.drawable.ic_launcher_foreground
        )
    }

    private fun demoFullScreenOptions(notificationId: Int): NotificationOptions {
        return NotificationOptions(
            channelId = "demo_fullscreen_notifications",
            channelName = "Demo full-screen notifications",
            channelDescription = "Full-screen lock-screen notifications created from the demo app",
            notificationId = notificationId,
            smallIconResId = R.drawable.ic_launcher_foreground
        )
    }

    private fun demoFullScreenContent(title: String, message: String): FullScreenNotificationContent {
        return FullScreenNotificationContent(
            notificationContent = NotificationContent(
                title = title,
                message = message,
                targetIntent = mainTargetIntent()
            ),
            layoutResId = R.layout.demo_full_screen_notification,
            dismissText = "Dismiss"
        )
    }

    private fun mainTargetIntent(): Intent {
        return Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
    }

    private fun showStatus(message: String) {
        findViewById<TextView>(R.id.tvStatus).text = message
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun showFullScreenStatus(prefix: String) {
        showStatus("$prefix. ${fullScreenStatusText()}")
    }

    private fun fullScreenStatusText(): String {
        return formatFullScreenStatus(
            FullScreenNotificationHelper.getStatus(this, demoFullScreenOptions(1))
        )
    }

    private fun formatFullScreenStatus(status: com.huann305.notificationhelper.fullscreen.FullScreenNotificationStatus): String {
        return "fsi=${status.canUseFullScreenIntent}, lockPerm=${status.canShowOnLockScreen}, locked=${status.isScreenLockedOrOff}, channel=${status.channelImportance}, canTry=${status.canTryFullScreenOnLockScreen}, canAttempt=${status.canAttemptFullScreenOnLockScreen}"
    }

    companion object {
        private const val DEMO_ONE_TIME_WORK = "demo_one_time_notification"
        private const val DEMO_DAILY_WORK = "demo_daily_notification"
        private const val DEMO_FULLSCREEN_ONE_TIME_WORK = "demo_fullscreen_one_time_notification"
        private const val DEMO_FULLSCREEN_DAILY_WORK = "demo_fullscreen_daily_notification"
    }
}
