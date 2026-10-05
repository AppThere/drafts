package com.appthere.drafts.design

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * A role in the prose scale: a size, a weight, and the space around it.
 *
 * Everything is a ratio of the base size rather than a measurement, which is the whole design of
 * `appthere-drafts.md` 5.2: "Base size is user-adjustable (14-28sp, default 18sp) and respects the
 * OS font-scale setting. All values below are ratios of base, so the whole system scales
 * coherently." A user at 28sp, or at a 200% system scale, gets a document with the same
 * proportions rather than a headline that has outgrown its page.
 *
 * [spaceBefore] and [spaceAfter] are in em of *this role's* size, as the spec's table gives them,
 * so a heading's air scales with the heading and not with the body around it.
 *
 * [insetStart] and [insetEnd] are fractions of the content column, and [alignment] where in what is
 * left the text sits: 5.4's screenplay geometry, which places a character name a third of the way
 * across and a transition at the right. Prose leaves all three at their defaults.
 */
@Immutable
data class ProseRole(
    val ratio: Float,
    val weight: FontWeight,
    val lineHeight: Float,
    val spaceBefore: Float,
    val spaceAfter: Float,
    val italic: Boolean = false,
    val monospace: Boolean = false,
    val tracking: Float = 0f,
    val caps: Boolean = false,
    val insetStart: Float = 0f,
    val insetEnd: Float = 0f,
    val alignment: TextAlign = TextAlign.Unspecified,
) {
    val fontStyle: FontStyle get() = if (italic) FontStyle.Italic else FontStyle.Normal

    /** The size of this role at a given base. */
    fun sizeAt(base: TextUnit): TextUnit = base * ratio

    /** Tracking as Compose expresses it: a fraction of the font size. */
    val letterSpacing: TextUnit get() = tracking.em
}

/**
 * The prose scale of `appthere-drafts.md` 5.2, transcribed.
 *
 * The modular ratio is 1.2 and most of the table is a power of it -- H1 is 1.2^4, H2 1.2^3, H3
 * 1.2^2 -- but not all of it, so the numbers are written out as the spec gives them rather than
 * generated from the ratio. A generated scale that disagreed with the table in the fourth decimal
 * would be a quiet divergence from the document everyone is reading.
 */
object Prose {
    /** The default, and the size the spec's absolute numbers in 5.2 are quoted at. */
    val DefaultBase = 18.sp

    /** 5.5: "Base size (14-28sp)". */
    val MinimumBase = 14.sp
    val MaximumBase = 28.sp

    val H1 =
        ProseRole(ratio = 2.07f, weight = FontWeight.W700, lineHeight = 1.2f, spaceBefore = 1.6f, spaceAfter = 0.5f)
    val H2 =
        ProseRole(ratio = 1.72f, weight = FontWeight.W700, lineHeight = 1.22f, spaceBefore = 1.5f, spaceAfter = 0.45f)
    val H3 =
        ProseRole(ratio = 1.44f, weight = FontWeight.W700, lineHeight = 1.25f, spaceBefore = 1.4f, spaceAfter = 0.4f)
    val H4 =
        ProseRole(ratio = 1.22f, weight = FontWeight.W600, lineHeight = 1.3f, spaceBefore = 1.3f, spaceAfter = 0.35f)
    val H5 =
        ProseRole(ratio = 1.11f, weight = FontWeight.W600, lineHeight = 1.35f, spaceBefore = 1.2f, spaceAfter = 0.3f)

    /** H6 is body-sized and distinguished by tracking and caps rather than by size. */
    val H6 =
        ProseRole(
            ratio = 1.00f,
            weight = FontWeight.W600,
            lineHeight = 1.4f,
            spaceBefore = 1.2f,
            spaceAfter = 0.3f,
            tracking = 0.02f,
            caps = true,
        )

    /** 1.6 line height is the number the whole scale is built around; it is not a default. */
    val Body =
        ProseRole(ratio = 1.00f, weight = FontWeight.W400, lineHeight = 1.6f, spaceBefore = 0f, spaceAfter = 0.75f)

    val Quote =
        ProseRole(
            ratio = 1.00f,
            weight = FontWeight.W400,
            lineHeight = 1.6f,
            spaceBefore = 0.75f,
            spaceAfter = 0.75f,
            italic = true,
        )

    val Code =
        ProseRole(
            ratio = 0.94f,
            weight = FontWeight.W400,
            lineHeight = 1.45f,
            spaceBefore = 0.75f,
            spaceAfter = 0.75f,
            monospace = true,
        )

    val Caption =
        ProseRole(ratio = 0.85f, weight = FontWeight.W400, lineHeight = 1.5f, spaceBefore = 0f, spaceAfter = 0f)

    /** Every role, so a test can walk the scale rather than the entries someone remembered. */
    val all = listOf(H1, H2, H3, H4, H5, H6, Body, Quote, Code, Caption)

    /** The heading of a given level, clamped: Markdown cannot express a level outside 1..6. */
    fun heading(level: Int): ProseRole = headings[(level - 1).coerceIn(headings.indices)]

    private val headings = listOf(H1, H2, H3, H4, H5, H6)

    /** A base size the user has asked for, held to the range 5.5 allows. */
    fun clampBase(base: TextUnit): TextUnit =
        when {
            base.value < MinimumBase.value -> MinimumBase
            base.value > MaximumBase.value -> MaximumBase
            else -> base
        }
}
