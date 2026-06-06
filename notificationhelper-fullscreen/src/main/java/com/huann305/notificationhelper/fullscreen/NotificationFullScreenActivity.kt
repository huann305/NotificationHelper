package com.huann305.notificationhelper.fullscreen

import android.app.Activity
import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.bumptech.glide.Glide
import com.huann305.notificationhelper.NotificationHelper
import com.huann305.notificationhelper.fullscreen.core.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NotificationFullScreenActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureLockScreenWindow()
        setContentViewSafely(resolveLayoutResId())
        renderContent()
        setupEvents()
        hideSystemBarsWhenReady()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        setContentViewSafely(resolveLayoutResId())
        renderContent()
        setupEvents()
        hideSystemBarsWhenReady()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemBarsWhenReady()
        }
    }

    private fun configureLockScreenWindow() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window.setDimAmount(0f)
        window.setFormat(PixelFormat.TRANSLUCENT)
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
    }

    private fun hideSystemBarsWhenReady() {
        window.decorView.post {
            hideSystemBarsSafely()
        }
    }

    private fun hideSystemBarsSafely() {
        runCatching {
            val decorView = window.decorView
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowInsetsControllerCompat(window, decorView).apply {
                hide(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
                systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }

            @Suppress("DEPRECATION")
            decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        }.onFailure { error ->
            Log.w(TAG, "Unable to hide system bars for full-screen notification", error)
        }
    }

    private fun resolveLayoutResId(): Int {
        val externalLayout = intent.getIntExtra(EXTRA_LAYOUT_RES_ID, 0)
        return externalLayout.takeIf { it != 0 } ?: R.layout.nh_activity_full_screen
    }

    private fun setContentViewSafely(layoutResId: Int) {
        val renderedExternalLayout = runCatching {
            setContentView(layoutResId)
            true
        }.getOrDefault(false)

        if (!renderedExternalLayout) {
            setContentView(R.layout.nh_activity_full_screen)
        }
        keepWindowTransparent()
    }

    private fun keepWindowTransparent() {
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window.decorView.background = ColorDrawable(Color.TRANSPARENT)
        findViewOrNull(R.id.notification_helper_root)?.let { root ->
            if (root.background == null) {
                root.setBackgroundColor(Color.TRANSPARENT)
            }
        }
    }

    private fun renderContent() {
        bindText(
            R.id.notification_helper_time,
            intent.getStringExtra(EXTRA_TIME_TEXT)
                ?: SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        )
        bindText(
            R.id.notification_helper_date,
            intent.getStringExtra(EXTRA_DATE_TEXT)
                ?: SimpleDateFormat("EEEE, MMM d", Locale.getDefault()).format(Date())
        )
        bindText(R.id.notification_helper_title, intent.getStringExtra(EXTRA_TITLE).orEmpty())
        bindText(R.id.notification_helper_message, intent.getStringExtra(EXTRA_MESSAGE).orEmpty())
        bindText(
            R.id.notification_helper_primary_action,
            intent.getStringExtra(EXTRA_ACTION_TEXT) ?: "Open"
        )
        bindText(
            R.id.notification_helper_dismiss_action,
            intent.getStringExtra(EXTRA_DISMISS_TEXT) ?: "Dismiss"
        )

        loadImage(
            imageView = findImageView(R.id.notification_helper_app_icon),
            imageUrl = intent.getStringExtra(EXTRA_APP_ICON_URL),
            imageResId = intent.getIntExtra(EXTRA_APP_ICON_RES_ID, 0)
        )

        loadImage(
            imageView = findImageView(R.id.notification_helper_image),
            imageUrl = intent.getStringExtra(EXTRA_IMAGE_URL),
            imageResId = intent.getIntExtra(EXTRA_IMAGE_RES_ID, 0)
        )
    }

    private fun loadImage(imageView: ImageView?, imageUrl: String?, imageResId: Int) {
        imageView ?: return
        when {
            !imageUrl.isNullOrBlank() -> {
                imageView.visibility = View.VISIBLE
                val request = Glide.with(imageView).load(imageUrl)
                if (imageResId != 0) {
                    request.error(imageResId)
                }
                request.into(imageView)
            }

            imageResId != 0 -> {
                imageView.visibility = View.VISIBLE
                imageView.setImageResource(imageResId)
            }
        }
    }

    private fun setupEvents() {
        findViewOrNull(R.id.notification_helper_primary_action)?.setOnClickListener {
            openTargetAfterUnlock()
        }
        findViewOrNull(R.id.notification_helper_notification_container)?.setOnClickListener {
            openTargetAfterUnlock()
        }
        findViewOrNull(R.id.notification_helper_close)?.setOnClickListener {
            cancelNotification()
            finish()
        }
        findViewOrNull(R.id.notification_helper_dismiss_action)?.setOnClickListener {
            cancelNotification()
            finish()
        }
    }

    private fun openTargetAfterUnlock() {
        val keyguardManager = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
        if (keyguardManager.isKeyguardLocked) {
            keyguardManager.requestDismissKeyguard(
                this,
                object : KeyguardManager.KeyguardDismissCallback() {
                    override fun onDismissSucceeded() {
                        openTarget()
                    }

                    override fun onDismissCancelled() {
                        openTarget()
                    }

                    override fun onDismissError() {
                        openTarget()
                    }
                }
            )
        } else {
            openTarget()
        }
    }

    private fun openTarget() {
        cancelNotification()
        val targetIntent = intent.getStringExtra(EXTRA_TARGET_INTENT_URI)?.let { uri ->
            runCatching { Intent.parseUri(uri, Intent.URI_INTENT_SCHEME) }.getOrNull()
        } ?: packageManager.getLaunchIntentForPackage(packageName)

        if (targetIntent != null) {
            targetIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            startActivity(targetIntent)
        }
        finish()
    }

    private fun cancelNotification() {
        val id = intent.getIntExtra(EXTRA_NOTIFICATION_ID, NotificationHelper.DEFAULT_NOTIFICATION_ID)
        val tag = intent.getStringExtra(EXTRA_NOTIFICATION_TAG)
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (tag.isNullOrBlank()) {
            notificationManager.cancel(id)
        } else {
            notificationManager.cancel(tag, id)
        }
    }

    private fun bindText(id: Int, value: String) {
        (findViewOrNull(id) as? TextView)?.text = value
    }

    private fun findImageView(id: Int): ImageView? {
        return findViewOrNull(id) as? ImageView
    }

    private fun findViewOrNull(id: Int): View? {
        return runCatching { findViewById<View>(id) }.getOrNull()
    }

    companion object {
        private const val TAG = "NHFullScreenActivity"

        const val EXTRA_LAYOUT_RES_ID = "nh_extra_layout_res_id"
        const val EXTRA_APP_ICON_RES_ID = "nh_extra_app_icon_res_id"
        const val EXTRA_APP_ICON_URL = "nh_extra_app_icon_url"
        const val EXTRA_IMAGE_RES_ID = "nh_extra_image_res_id"
        const val EXTRA_IMAGE_URL = "nh_extra_image_url"
        const val EXTRA_TITLE = "nh_extra_title"
        const val EXTRA_MESSAGE = "nh_extra_message"
        const val EXTRA_TIME_TEXT = "nh_extra_time_text"
        const val EXTRA_DATE_TEXT = "nh_extra_date_text"
        const val EXTRA_ACTION_TEXT = "nh_extra_action_text"
        const val EXTRA_DISMISS_TEXT = "nh_extra_dismiss_text"
        const val EXTRA_TARGET_INTENT_URI = "nh_extra_target_intent_uri"
        const val EXTRA_NOTIFICATION_ID = "nh_extra_notification_id"
        const val EXTRA_NOTIFICATION_TAG = "nh_extra_notification_tag"
    }
}
