package com.appthere.drafts.app

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import com.appthere.drafts.design.LocalMotion
import kotlinx.coroutines.delay

/**
 * 12's auto-hide for one open document: whether the chrome is out of the way, and the way to bring
 * it back.
 *
 * [ChromeVisibility] is the policy, with no clock and no composition, so it can be tested at
 * explicit instants. This is that policy attached to a document on screen -- the one place that
 * knows both when an edit happened and what the chrome is currently doing.
 */
@Stable
internal class AutoHide {
    val visibility = ChromeVisibility()

    /** Whether the chrome is faded out right now. */
    var hidden by mutableStateOf(false)
        private set

    /** The reader reached for something, so the chrome comes back. */
    fun rouse() {
        visibility.roused()
        hidden = false
    }

    internal fun report(isHidden: Boolean) {
        hidden = isHidden
    }
}

/** An [AutoHide] for [document], running for as long as the document is on screen. */
@Composable
internal fun rememberAutoHide(document: OpenDocument): AutoHide {
    val autoHide = remember(document) { AutoHide() }

    ChromeEffect(
        visibility = autoHide.visibility,
        revision = document.editor.revision,
        onChange = autoHide::report,
    )

    return autoHide
}

/**
 * Runs 12's auto-hide for as long as the document is on screen.
 *
 * Its own composable with its own clock, for the reason the snapshot effect has one: with a real
 * monotonic source, `delay` obeys a test's virtual clock while the deadline arithmetic obeys the
 * wall clock, the deadline never arrives, and the test proves only that the two disagree.
 *
 * The deadline is slept to rather than polled, so nothing wakes once a frame to ask whether a
 * second and a half has gone by.
 */
@Composable
internal fun ChromeEffect(
    visibility: ChromeVisibility,
    revision: Int,
    onChange: (Boolean) -> Unit,
    now: () -> Long = Elapsed::millis,
) {
    val currentTime by rememberUpdatedState(now)
    val report by rememberUpdatedState(onChange)

    LaunchedEffect(visibility, revision) {
        // Revision zero is the document as opened. Treating it as typing would have the chrome
        // fade out three seconds after every launch, before anyone had touched anything.
        if (revision == 0) return@LaunchedEffect

        visibility.typed(currentTime())
        val deadline = visibility.hidesAt() ?: return@LaunchedEffect

        delay((deadline - currentTime()).coerceAtLeast(0))
        report(visibility.isHidden(currentTime()))
    }
}

/**
 * A fade that honours 10.2's reduced-motion preference.
 *
 * Inside the theme rather than outside it, because that is where the preference is readable. The
 * duration is the design system's own `chromeMillis`, which is already zero under reduced motion
 * -- 12 asks for a fade and 10.2 asks for that to be somebody's choice, and the number for it
 * existed before this did.
 */
@Composable
internal fun FadingChrome(
    hidden: Boolean,
    content: @Composable () -> Unit,
) {
    val motion = LocalMotion.current
    val alpha by animateFloatAsState(
        targetValue = if (hidden) 0f else 1f,
        animationSpec = tween(motion.chromeMillis),
    )

    Box(Modifier.alpha(alpha)) { content() }
}
