package com.appthere.drafts.editor.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appthere.drafts.core.model.Block
import com.appthere.drafts.core.model.BlockRole
import com.appthere.drafts.core.model.Heading
import com.appthere.drafts.design.ContentMeasure
import com.appthere.drafts.design.Measure
import com.appthere.drafts.design.ProseRole
import com.appthere.drafts.design.Screenplay
import com.appthere.drafts.design.proseStyleOf

/** The 5.4 role a block of a screenplay is laid out in, by the role the parser gave it. */
internal fun screenplayRoleOf(
    role: BlockRole?,
    block: Block,
): ProseRole =
    when (role) {
        BlockRole.SCENE_HEADING -> Screenplay.SceneHeading
        BlockRole.CHARACTER -> Screenplay.Character
        BlockRole.PARENTHETICAL -> Screenplay.Parenthetical
        BlockRole.DIALOGUE, BlockRole.DUAL_DIALOGUE_LEFT, BlockRole.DUAL_DIALOGUE_RIGHT -> Screenplay.Dialogue
        BlockRole.TRANSITION -> Screenplay.Transition
        BlockRole.CENTERED -> Screenplay.Centered
        BlockRole.LYRIC -> Screenplay.Lyric
        BlockRole.SECTION -> Screenplay.Section
        else -> if (block is Heading) Screenplay.Section else Screenplay.Action
    }

/**
 * Where a row's text sits: the width it is laid out in, how far it is inset from either side of
 * that, and the space above and below it.
 */
@Immutable
internal data class RowGeometry(
    val width: Dp,
    val insetStart: Dp,
    val insetEnd: Dp,
    val spaceBefore: Dp = 0.dp,
    val spaceAfter: Dp = 0.dp,
) {
    companion object {
        /** [role]'s insets, which are fractions of the column, in a column [width] wide. */
        fun of(
            width: Dp,
            role: ProseRole,
            spaceBefore: Dp,
            spaceAfter: Dp,
        ): RowGeometry = RowGeometry(width, width * role.insetStart, width * role.insetEnd, spaceBefore, spaceAfter)

        /**
         * [role] in one column of a dual pair, [half] wide, with no space around it: the speech is
         * unbroken and the pair brings its own (`DualRow`).
         *
         * 5.4: "Dual dialogue is a two-column layout at the 16.7%/25% insets, splitting the
         * available width." Each speech is set in its column as it would be in dialogue's band
         * across the page: dialogue fills it, and a name or a parenthetical keeps its inset beyond
         * dialogue's, in proportion to the narrower column.
         */
        fun inHalf(
            half: Dp,
            role: ProseRole,
        ): RowGeometry {
            val band = 1f - Screenplay.Dialogue.insetStart - Screenplay.Dialogue.insetEnd
            val start = ((role.insetStart - Screenplay.Dialogue.insetStart) / band).coerceAtLeast(0f)
            val end = ((role.insetEnd - Screenplay.Dialogue.insetEnd) / band).coerceAtLeast(0f)
            return RowGeometry(half, half * start, half * end)
        }
    }
}

/** A screenplay's column and the one size its type is set in. */
@Immutable
internal data class ScreenplayMeasure(
    val column: ContentMeasure,
    val fontSize: TextUnit,
)

/**
 * 5.4's column for a screenplay in a window of [availableWidth].
 *
 * "Set the mono size so exactly 61 monospace characters fit the content column" -- measured, from
 * the face itself, rather than assumed from a nominal character width. "The content column
 * additionally carries a US Letter width ceiling": however wide the window, the column is never
 * wider than a printed page's text area, 6.0 inches.
 *
 * With one floor 5.4 does not give: the size never drops below [floor], the reader's base size. On
 * a phone, 61 characters across the column would set the script around 10sp, which 10.2's
 * legibility does not allow; there the lines carry fewer than 61 characters and break earlier than
 * a printed page would. Where the window has room, the 61 hold.
 */
@Composable
internal fun screenplayMeasureOf(
    availableWidth: Dp,
    floor: TextUnit,
): ScreenplayMeasure {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current

    // The script's own style -- its face and the reader's letter spacing, which is in em and so
    // scales with the size -- at a reference size the advance scales from.
    val style = proseStyleOf(Screenplay.Action).textStyle.copy(fontSize = referenceSize)

    // The width of the 61 characters at that size.
    val sampleWidth = remember(style, density) { measurer.measure(sample, style).size.width }

    val gutter = maxOf(Measure.MinimumGutter, availableWidth * Measure.GUTTER_FRACTION)
    val width = minOf(availableWidth - gutter * 2, letterTextWidth).coerceAtLeast(0.dp)
    val fitting = referenceSize.value * with(density) { width.toPx() } / sampleWidth.coerceAtLeast(1)

    return ScreenplayMeasure(
        column = ContentMeasure(gutter = gutter, contentWidth = width),
        fontSize = maxOf(fitting, floor.value).sp,
    )
}

/** 5.4: "Standard US Letter screenplay geometry is a 6.0-inch text area". 160dp is an inch. */
private val letterTextWidth = 960.dp

/** 5.4's count, and the line a printed page at 12pt Courier carries. */
private const val CHARACTERS_PER_LINE = 61

private val sample = "0".repeat(CHARACTERS_PER_LINE)

private val referenceSize = 100.sp
