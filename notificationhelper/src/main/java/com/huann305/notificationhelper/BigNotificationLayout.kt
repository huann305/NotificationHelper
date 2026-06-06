package com.huann305.notificationhelper

data class BigNotificationLayout(
    val compactLayoutResId: Int = 0,
    val expandedLayoutResId: Int = 0,
    val iconResId: Int = 0,
    val iconUrl: String? = null,
    val imageResId: Int = 0,
    val imageUrl: String? = null,
    val actionText: String = "Open",
    val mode: BigNotificationLayoutMode = BigNotificationLayoutMode.SYSTEM_STYLE,
    val decorateCustomView: Boolean = true,
    val enabled: Boolean = true
) {
    internal val isEnabled: Boolean
        get() = enabled

    internal val usesCustomView: Boolean
        get() = mode == BigNotificationLayoutMode.CUSTOM_VIEW ||
            compactLayoutResId != 0 ||
            expandedLayoutResId != 0

    internal val hasRemoteImages: Boolean
        get() = !iconUrl.isNullOrBlank() || !imageUrl.isNullOrBlank()
}
