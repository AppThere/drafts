package com.appthere.drafts.core.serialise

import com.appthere.drafts.core.fountain.DUAL_DIALOGUE_CLASS
import com.appthere.drafts.core.fountain.FountainKeywords
import com.appthere.drafts.core.fountain.forcingOf
import com.appthere.drafts.core.fountain.isCharacter
import com.appthere.drafts.core.fountain.isSceneHeading
import com.appthere.drafts.core.fountain.isTransition
import com.appthere.drafts.core.fountain.isUppercase
import com.appthere.drafts.core.model.Block
import com.appthere.drafts.core.model.BlockRole
import com.appthere.drafts.core.model.Heading
import com.appthere.drafts.core.model.Paragraph

/**
 * Writes [Block] content back to Fountain, from the IR alone.
 *
 * The canonical path -- the one taken for blocks the user edited or created, where there are no
 * original bytes to re-emit. [FountainSerialiser] prefers the source whenever it can.
 *
 * What makes this different from its Markdown counterpart is that Fountain's elements are mostly
 * *inferred from shape* rather than marked. `INT. HOUSE - DAY` is a scene heading because it begins
 * with `INT.`, and `CUT TO:` is a transition because it is uppercase and ends in `TO:`. So writing a
 * block out is not a matter of putting its marker back -- most blocks never had one -- it is a
 * matter of asking whether the words alone will be read as what the block *is*, and reaching for a
 * forcing character only when they will not.
 *
 * That question is answered by the same predicates the parser asks it with ([isSceneHeading] and the
 * rest, in `:core-fountain`), which is the only way the two can be guaranteed to agree. The
 * alternative -- forcing everything -- would be correct and would also mean a writer who fixes a
 * typo in one slugline finds a full stop in front of it, and in front of nothing else in the script.
 */
internal class FountainBlockWriter(
    private val inlines: FountainInlineWriter,
    private val markdown: BlockText,
    private val keywords: FountainKeywords,
) {
    fun write(block: Block): String =
        when (block) {
            // A Fountain section: `## Sequence A`. The one Fountain element with a depth, and so the
            // one the IR holds as a Heading rather than a Paragraph.
            is Heading -> "#".repeat(block.level) + " " + inlines.write(block.inlines)

            is Paragraph -> paragraph(block)

            // Not expressible in Fountain, which has no table, no list and no code block. Written in
            // Markdown, where at least the structure survives in a form a human can read; Fountain
            // will read it back as action, which is its own fallback for "any paragraph that doesn't
            // match another element".
            else -> markdown.write(block)
        }

    private fun paragraph(block: Paragraph): String {
        val text = inlines.write(block.inlines)

        return when (block.role) {
            BlockRole.SCENE_HEADING -> sceneHeading(text, block)

            BlockRole.ACTION -> action(text)

            BlockRole.CHARACTER -> character(text, block)

            BlockRole.TRANSITION -> transition(text)

            BlockRole.CENTERED -> "$CENTRE_OPEN $text $CENTRE_CLOSE"

            BlockRole.LYRIC -> LYRIC + text

            BlockRole.SYNOPSIS -> "$SYNOPSIS $text"

            BlockRole.PAGE_BREAK -> text.ifBlank { PAGE_BREAK }

            // A block-level boneyard, whose characters were never parsed and so must not be escaped.
            BlockRole.NOTE -> inlines.raw(block.inlines)

            // Dialogue and parentheticals are positional: they are what follows a character cue, and
            // Fountain gives them no marker of their own to put back. A parenthetical already carries
            // its brackets, because they are what identifies it.
            BlockRole.DIALOGUE, BlockRole.PARENTHETICAL -> text

            // Dual dialogue is a `^` on the character cue, so by the time a side is reached there is
            // nothing left to write but the words. The marker goes on in `character` below.
            BlockRole.DUAL_DIALOGUE_LEFT, BlockRole.DUAL_DIALOGUE_RIGHT -> text

            // The title page, and prose that arrived from the Markdown side of the IR. Neither takes
            // a forcing character: the title page is key-value lines that Fountain reads by position,
            // and body text is action by default.
            else -> text
        }
    }

    /**
     * A scene heading, with its number put back and a full stop in front if it needs one.
     *
     * The number lives in the attributes rather than the words -- it is archival, "appended in
     * `#...#` at end of line" -- so it is re-appended here. The forcing stop goes on only when the
     * words do not begin with a recognised prefix, which is what keeps `INT. HOUSE - DAY` spelled the
     * way screenwriters spell it.
     */
    private fun sceneHeading(
        text: String,
        block: Paragraph,
    ): String {
        val number = block.attrs[SCENE_NUMBER]?.let { " #$it#" }.orEmpty()
        val forced = if (isSceneHeading(text, keywords)) "" else FORCE_SCENE

        return forced + text + number
    }

    /**
     * Action, forced only where its first line would otherwise be read as something else.
     *
     * The ambiguity is real and one-directional: action is the fallback, so the only way a line of
     * action can be misread is by matching a *stronger* rule. An uppercase line is the whole problem
     * -- `A LOUD CRASH.` is action, and `STEEL` is a character cue, and nothing but the case of the
     * letters distinguishes them from each other. Forcing every uppercase line of action is therefore
     * the conservative choice, and `!` is invisible on the page.
     */
    private fun action(text: String): String {
        val first = text.lineSequence().firstOrNull().orEmpty()
        val ambiguous = isUppercase(first) || forcingOf(first) != null

        return if (ambiguous) FORCE_ACTION + text else text
    }

    /**
     * A character cue, with the dual-dialogue caret back on it.
     *
     * `@` goes on when the name is not all capitals -- which is the rule, and is what lets `McAVOY`
     * be a character -- and also when the name begins with a character Fountain would read as some
     * other element's marker, or reads as a scene heading. A cue called `INT GUY` is not a joke
     * anyone would write deliberately, but a parser reading it as a slugline would lose the speech
     * underneath it, and that is worth one `@`.
     */
    private fun character(
        text: String,
        block: Paragraph,
    ): String {
        val dual = if (block.attrs.hasClass(DUAL_DIALOGUE_CLASS)) " $DUAL_MARKER" else ""
        val forced = if (isCharacter(text) && forcingOf(text) == null && !isSceneHeading(text, keywords)) "" else "@"

        return forced + text + dual
    }

    /**
     * A transition, forced only when its shape will not carry it.
     *
     * "A line in all uppercase ending in `TO:`" needs nothing; anything else needs the `>`. Note that
     * the leading whitespace screenwriters use to push transitions to the right margin is not in the
     * words -- a block that still has its source keeps it, and a rewritten one is written flush and
     * centred by the renderer instead (5.4).
     */
    private fun transition(text: String): String =
        if (isTransition(text, keywords) && forcingOf(text) == null) text else "$FORCE_TRANSITION $text"

    private companion object {
        const val FORCE_SCENE = "."
        const val FORCE_ACTION = "!"
        const val FORCE_TRANSITION = ">"
        const val CENTRE_OPEN = ">"
        const val CENTRE_CLOSE = "<"
        const val LYRIC = "~"
        const val SYNOPSIS = "="
        const val PAGE_BREAK = "==="
        const val DUAL_MARKER = "^"

        /** Written by the parser, and read back here: the archival scene number. */
        const val SCENE_NUMBER = "scene"
    }
}
