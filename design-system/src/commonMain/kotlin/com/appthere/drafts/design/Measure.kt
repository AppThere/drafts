package com.appthere.drafts.design

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * How wide the content column is, and how much air sits either side of it.
 *
 * `appthere-drafts.md` 5.3 gives the arithmetic exactly:
 *
 * ```
 * contentWidth = min(availableWidth - 2 × gutter, 34em)
 * gutter = max(16dp, 4% of availableWidth)
 * ```
 *
 * The 34em comes from a 68-character measure -- "computed as `34 × bodySize`" -- which is the
 * long-standing typographic figure for a line a reader's eye can return from without losing its
 * place. At the 18sp default that is about 612dp: "comfortable on a tablet, full-bleed on a phone,
 * centred with generous margins on a desktop window."
 *
 * Note which way the clamp runs. On a narrow screen the column is the window minus its gutters and
 * the 34em never applies; on a wide one the column stops at 34em and the leftover becomes margin.
 * There is no breakpoint in it -- 6 says the layout "responds continuously rather than snapping" --
 * so a window dragged wider grows its margins, not its line length.
 */
@Immutable
data class ContentMeasure(
    val gutter: Dp,
    val contentWidth: Dp,
)

object Measure {
    /** 5.3: the floor under the proportional gutter, so a phone still has an edge. */
    val MinimumGutter = 16.dp

    /** 5.3: "gutter = max(16dp, 4% of availableWidth)". */
    const val GUTTER_FRACTION = 0.04f

    /** 5.3: 68 characters of body text, as `34 × bodySize`. */
    const val MEASURE_EM = 34f

    /** 5.5 lets the reader choose between 55 and 85 characters; the em figure is half of that. */
    const val MINIMUM_CHARACTERS = 55f
    const val MAXIMUM_CHARACTERS = 85f

    /**
     * The column for a window of [availableWidth], with body text of [bodySize] in Dp.
     *
     * [bodySize] arrives already converted from sp, because only the caller has a density and a
     * font scale -- and the font scale is exactly what has to reach this calculation, or a reader
     * at 200% gets 68 characters of enormous text on one line instead of a measure that held.
     */
    fun of(
        availableWidth: Dp,
        bodySize: Dp,
        characters: Float = MEASURE_EM * 2,
    ): ContentMeasure {
        val gutter = maxOf(MinimumGutter, availableWidth * GUTTER_FRACTION)
        val withinWindow = availableWidth - gutter * 2
        val idealMeasure = bodySize * (characters / 2)

        return ContentMeasure(
            gutter = gutter,
            contentWidth = minOf(withinWindow, idealMeasure).coerceAtLeast(0.dp),
        )
    }
}
