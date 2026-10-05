package com.appthere.drafts.editor.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.appthere.drafts.editor.engine.BlockId

/**
 * 4.2's cross-fade, one for the whole editor rather than one per row.
 *
 * "Cross-fade inline decoration over 120ms with no layout animation ... Respect
 * `prefers-reduced-motion`: at reduced motion the switch is instantaneous."
 *
 * At most two blocks are ever fading: the one gaining the caret and the one losing it. A
 * `Crossfade` in every row gave each of the other few dozen on screen a transition, its state and
 * a keyed child of their own, composed as each row scrolled into view and torn down as it scrolled
 * out -- over a third of the cost of composing a row, for an animation none of them would play
 * (measured 2026-10-03). Here those rows draw their one state and nothing else.
 *
 * Made in the same composition as the focus change, so the first frame after a click already has
 * the fade at its start; started a frame later, the new state would flash up whole and then jump
 * back to transparent.
 */
@Stable
internal class RevealFade(
    private val gaining: BlockId?,
    private val losing: BlockId?,
    instant: Boolean,
) {
    private val progress = Animatable(if (instant) 1f else 0f)

    /** The block that holds the caret, which this fade was made for. */
    val focused: BlockId? get() = gaining

    /** How far [id] is through its fade, or null if it is not fading at all. */
    fun of(id: BlockId): Animatable<Float, AnimationVector1D>? = progress.takeIf { id == gaining || id == losing }

    suspend fun run(millis: Int) {
        progress.animateTo(1f, tween(millis))
    }
}

/**
 * The fade for the block that holds the caret now, against the one that held it before.
 *
 * Instant under reduced motion, and on the first composition: a document opened with its caret
 * already placed has not had anything revealed, so there is nothing to fade from.
 */
@Composable
internal fun rememberRevealFade(
    focused: BlockId?,
    millis: Int,
): RevealFade {
    val history = remember { FocusHistory() }
    val fade =
        remember(focused) {
            RevealFade(gaining = focused, losing = history.current, instant = millis == 0 || !history.started)
        }

    // After the composition, so the fade above was made against the caret's *previous* block.
    SideEffect {
        history.current = focused
        history.started = true
    }
    LaunchedEffect(fade) { fade.run(millis) }

    return fade
}

/** Where the caret was last composed, which is what the next fade fades from. */
private class FocusHistory {
    var current: BlockId? = null
    var started = false
}

/**
 * One row's content in whichever state it is in -- and, while [fade] runs, the state it is leaving
 * as well, the two cross-fading.
 *
 * Each state has one place in the row, preview first and reveal second, and is only ever there or
 * not. That is what keeps the field that takes the caret the same field, with the same input
 * connection, before and after the fade finishes: it never moves, so nothing about it is recreated.
 *
 * The alpha is read in the draw phase, so the fade does not recompose the row on every frame; only
 * whether the outgoing state is still there is a composition-time question.
 */
@Composable
internal fun RevealContent(
    revealed: Boolean,
    fade: Animatable<Float, AnimationVector1D>?,
    preview: @Composable () -> Unit,
    reveal: @Composable () -> Unit,
) {
    val fading = fade != null && fade.value < 1f

    // How opaque the reveal is: arriving, it goes 0 to 1 with the fade; leaving, 1 to 0.
    val revealAlpha: () -> Float = {
        val progress = fade?.value ?: 1f
        if (revealed) progress else 1f - progress
    }

    Box {
        if (!revealed || fading) {
            Box(fade.alpha { 1f - revealAlpha() }) { preview() }
        }
        if (revealed || fading) {
            Box(fade.alpha(revealAlpha)) { reveal() }
        }
    }
}

/** A layer with [alpha] while a fade is running, and no layer at all for a row that is not fading. */
private fun Animatable<Float, AnimationVector1D>?.alpha(alpha: () -> Float): Modifier =
    if (this == null) Modifier else Modifier.graphicsLayer { this.alpha = alpha() }
