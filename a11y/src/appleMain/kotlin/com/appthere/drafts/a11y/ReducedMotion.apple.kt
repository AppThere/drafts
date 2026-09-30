package com.appthere.drafts.a11y

import androidx.compose.runtime.Composable
import platform.UIKit.UIAccessibilityIsReduceMotionEnabled

/**
 * Apple's answer, from UIKit's own accessibility flag.
 *
 * Compiled but never run, like everything else on these targets: `check` compiles them and nothing
 * has executed there yet. The notification that this has changed mid-session
 * (`UIAccessibilityReduceMotionStatusDidChangeNotification`) is not observed, so a reader who
 * changes it while a document is open sees the change at the next launch.
 */
@Composable
actual fun systemPrefersReducedMotion(): Boolean = UIAccessibilityIsReduceMotionEnabled()
