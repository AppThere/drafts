package com.appthere.drafts.core.parse.fountain

import com.appthere.drafts.core.fountain.DUAL_DIALOGUE_CLASS
import com.appthere.drafts.core.fountain.Element
import com.appthere.drafts.core.fountain.forcingOf
import com.appthere.drafts.core.fountain.isParenthetical
import com.appthere.drafts.core.model.Attributes
import com.appthere.drafts.core.model.Block
import com.appthere.drafts.core.model.BlockRole
import com.appthere.drafts.core.model.Paragraph
import com.appthere.drafts.core.model.SourceSpan

/*
 * A character's name and the speech under it: the one Fountain element made of several blocks,
 * because a name, its parentheticals, its dialogue and any lyrics sung in it are each a different
 * thing to set on the page.
 */

/** The roles a blank-line-with-a-space may continue, and a parenthetical may follow. */
internal val speechRoles = setOf(BlockRole.CHARACTER, BlockRole.DIALOGUE, BlockRole.PARENTHETICAL, BlockRole.LYRIC)

private const val DUAL_MARKER = "^"

/**
 * A character line and the speech under it.
 *
 * "Any text on the line immediately following a Character line or a Parenthetical" is dialogue,
 * and a line wrapped in parentheses in the same position is a parenthetical. Consecutive
 * dialogue lines are one block, which is what makes a speech one thing to edit.
 *
 * A `^` at the end of the character line is dual dialogue. The marker is dropped from the name
 * and recorded as a class, because what it decides is a two-column *layout* (5.4) rather than
 * what the element is.
 */
internal fun characterBlocks(
    source: String,
    lines: List<Line>,
    marker: Int = 0,
): List<Block> {
    val name = lines.first()
    val dual = name.text.trimEnd().endsWith(DUAL_MARKER)

    // "Whitespace before `^` is permitted and ignored", so the name ends at its last letter
    // rather than at the marker: `STEEL ^` is STEEL, not "STEEL ".
    val nameEnd =
        if (dual) {
            name.text
                .trimEnd()
                .dropLast(DUAL_MARKER.length)
                .trimEnd()
                .length
        } else {
            name.text.length
        }

    val blocks =
        mutableListOf<Block>(
            Paragraph(
                inlines = inlinesIn(source, SourceSpan.of(name.start + marker, name.start + nameEnd)),
                role = BlockRole.CHARACTER,
                attrs = if (dual) Attributes(classes = listOf(DUAL_DIALOGUE_CLASS)) else Attributes.EMPTY,
                source = name.span,
            ),
        )

    blocks += speechIn(source, lines.drop(1), BlockRole.CHARACTER, dual)
    return blocks
}

/** The parentheticals and dialogue of one speech, from [lines]. */
internal fun speechIn(
    source: String,
    lines: List<Line>,
    previous: BlockRole?,
    dual: Boolean = false,
): List<Block> {
    val marks = if (dual) Attributes(classes = listOf(DUAL_DIALOGUE_CLASS)) else Attributes.EMPTY
    val blocks = mutableListOf<Block>()
    var said = mutableListOf<Line>()
    var last = previous

    fun flush() {
        if (said.isEmpty()) return

        blocks +=
            spoken(source, SourceSpan.of(said.first().start, said.last().endExclusive), BlockRole.DIALOGUE, marks)
        said = mutableListOf()
        last = BlockRole.DIALOGUE
    }

    lines.forEach { line ->
        when {
            // `fountain.md`: "Lyrics may appear in dialogue." A sung line is its own element, as it
            // is anywhere else, and the speech carries on under it.
            forcingOf(line.text)?.element == Element.LYRIC -> {
                flush()
                blocks += lyric(source, line)
                last = BlockRole.LYRIC
            }

            // A parenthetical only counts where one may appear: "immediately following a Character
            // line or a Dialogue line". Anywhere else the parentheses are just parentheses.
            isParenthetical(line.text) && last in speechRoles -> {
                flush()
                blocks += spoken(source, line.span, BlockRole.PARENTHETICAL, marks)
                last = BlockRole.PARENTHETICAL
            }

            else -> {
                said += line
            }
        }
    }

    flush()
    return blocks
}
