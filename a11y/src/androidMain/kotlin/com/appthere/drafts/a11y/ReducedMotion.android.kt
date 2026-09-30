package com.appthere.drafts.a11y

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Android's answer: the animator duration scale, which Developer Options and the accessibility
 * settings both write.
 *
 * Zero means the system has been asked for no animation at all, which is what
 * `prefers-reduced-motion` means here. There is no separate "reduce motion" flag below API 33's
 * `ACCESSIBILITY_ANIMATIONS`, and the duration scale is what the platform's own animators obey --
 * so it is the setting a reader will have found.
 */
@Composable
actual fun systemPrefersReducedMotion(): Boolean {
    val context = LocalContext.current

    return remember(context) {
        runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }.getOrDefault(false)
    }
}
