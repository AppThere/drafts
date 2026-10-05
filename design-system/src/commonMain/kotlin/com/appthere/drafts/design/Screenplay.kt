package com.appthere.drafts.design

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign

/**
 * The screenplay roles of `appthere-drafts.md` 5.4 and 4.5, transcribed.
 *
 * "Fountain uses a monospace face and print-derived proportions, expressed as percentages of the
 * content column rather than absolute measurements -- the same approach already settled for
 * Fountain -> EPUB export, reused here so screen and export agree."
 *
 * | Role | Left inset | Right inset | Alignment |
 * |---|---|---|---|
 * | Scene heading | 0% | 0% | Left, caps |
 * | Action | 0% | 0% | Left |
 * | Character | 36.7% | 0% | Left, caps |
 * | Parenthetical | 26.7% | 30% | Left |
 * | Dialogue | 16.7% | 25% | Left |
 * | Transition | -- | 0% | Right, caps |
 * | Centered | -- | -- | Centre |
 *
 * Every role is monospace -- 5.1: "all Fountain content" -- and every ratio is 1: a screenplay has
 * one size of type, and 5.4 sets it from the width so that 61 characters fill the column. What the
 * spec does not give, the spacing, follows the printed page: a blank line between elements, two
 * before a scene, and none between a character and the speech under it.
 */
object Screenplay {
    /** One blank line, in em of the role: the line height, so a gap is exactly one empty line. */
    private const val LINE = 1.35f

    val SceneHeading =
        ProseRole(
            ratio = 1f,
            weight = FontWeight.W700,
            lineHeight = LINE,
            spaceBefore = LINE * 2,
            spaceAfter = 0f,
            monospace = true,
            caps = true,
        )

    val Action =
        ProseRole(
            ratio = 1f,
            weight = FontWeight.W400,
            lineHeight = LINE,
            spaceBefore = LINE,
            spaceAfter = 0f,
            monospace = true,
        )

    val Character =
        ProseRole(
            ratio = 1f,
            weight = FontWeight.W400,
            lineHeight = LINE,
            spaceBefore = LINE,
            spaceAfter = 0f,
            monospace = true,
            caps = true,
            insetStart = 0.367f,
        )

    val Parenthetical =
        ProseRole(
            ratio = 1f,
            weight = FontWeight.W400,
            lineHeight = LINE,
            spaceBefore = 0f,
            spaceAfter = 0f,
            monospace = true,
            insetStart = 0.267f,
            insetEnd = 0.30f,
        )

    val Dialogue =
        ProseRole(
            ratio = 1f,
            weight = FontWeight.W400,
            lineHeight = LINE,
            spaceBefore = 0f,
            spaceAfter = 0f,
            monospace = true,
            insetStart = 0.167f,
            insetEnd = 0.25f,
        )

    val Transition =
        ProseRole(
            ratio = 1f,
            weight = FontWeight.W400,
            lineHeight = LINE,
            spaceBefore = LINE,
            spaceAfter = 0f,
            monospace = true,
            caps = true,
            alignment = TextAlign.End,
        )

    val Centered =
        ProseRole(
            ratio = 1f,
            weight = FontWeight.W400,
            lineHeight = LINE,
            spaceBefore = LINE,
            spaceAfter = 0f,
            monospace = true,
            alignment = TextAlign.Center,
        )

    /** Lyrics are sung dialogue, set in italic where speech would be. */
    val Lyric =
        ProseRole(
            ratio = 1f,
            weight = FontWeight.W400,
            lineHeight = LINE,
            spaceBefore = 0f,
            spaceAfter = 0f,
            italic = true,
            monospace = true,
            insetStart = 0.167f,
            insetEnd = 0.25f,
        )

    /** A `#` section: an act or a sequence, which organises the script rather than being read in it. */
    val Section =
        ProseRole(
            ratio = 1f,
            weight = FontWeight.W700,
            lineHeight = LINE,
            spaceBefore = LINE * 2,
            spaceAfter = 0f,
            monospace = true,
        )

    /** Every role, so a test can walk the table rather than the entries someone remembered. */
    val all = listOf(SceneHeading, Action, Character, Parenthetical, Dialogue, Transition, Centered, Lyric, Section)
}
