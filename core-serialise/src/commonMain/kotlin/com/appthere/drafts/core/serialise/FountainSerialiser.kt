package com.appthere.drafts.core.serialise

import com.appthere.drafts.core.fountain.FountainKeywords
import com.appthere.drafts.core.model.Block
import com.appthere.drafts.core.model.BlockIndex
import com.appthere.drafts.core.model.BlockRole
import com.appthere.drafts.core.model.Document

/**
 * [Document] out to Fountain, source-preserving.
 *
 * `fountain.md`: "Fountain is its own canonical serialisation. Preserve the source verbatim for
 * untouched regions and you get perfect fidelity for free." That is literally what happens --
 * byte-preservation is [SourcePreserving], shared with Markdown and not reimplemented here, and what
 * this class adds is the two things Fountain does differently: how a block is spelled
 * ([FountainBlockWriter]), and what goes between two of them.
 *
 * [keywords] is 11.3's setting, and it reaches the *writer* for the same reason it reaches the
 * parser. Whether a slugline needs a forcing full stop in front of it depends on whether `INT.` is a
 * scene prefix in the language the script is written in, and a serialiser working from the English
 * list would put stops in front of every heading in a French screenplay.
 */
class FountainSerialiser(
    keywords: FountainKeywords = FountainKeywords.ENGLISH,
) {
    private val preserving =
        SourcePreserving(
            text = FountainBlockWriter(FountainInlineWriter(), BlockWriter(InlineWriter())::write, keywords)::write,
            gap = FountainGap,
        )

    /**
     * Serialises [document].
     *
     * @param source the text the document was parsed from. Supplying it enables byte-preservation;
     *   without it every block is written canonically, which is correct but not byte-identical.
     * @param rewritten blocks whose content no longer matches the source and must therefore be
     *   written from the IR, even though they still know where they came from.
     */
    fun serialise(
        document: Document,
        source: String? = null,
        rewritten: Set<BlockIndex> = emptySet(),
    ): String = preserving.serialise(document, source, rewritten)
}

/**
 * What separates two Fountain blocks: one newline or two.
 *
 * Unlike Markdown, where the answer is always a blank line, this changes what the file *means*. A
 * speech is a character cue with its dialogue on the lines immediately under it, and a blank line is
 * what ends it -- "to include a blank line within dialogue, the blank line must contain at least one
 * space". So putting a blank line between a cue and its dialogue does not reflow the script, it
 * orphans the cue and turns the dialogue into action.
 *
 * Lyrics and centred lines are the other case: each line is its own element, and consecutive ones are
 * written as consecutive lines rather than as separate blocks, which is how they were read.
 */
private object FountainGap : BlockGap {
    override fun between(
        before: Block,
        after: Block,
    ): String =
        when {
            before.role in SPEECH && after.role in SPOKEN -> LINE
            before.role == after.role && before.role in PER_LINE -> LINE
            else -> PARAGRAPH
        }

    /** The roles a speech may continue from. */
    private val SPEECH =
        setOf(
            BlockRole.CHARACTER,
            BlockRole.DIALOGUE,
            BlockRole.PARENTHETICAL,
            BlockRole.DUAL_DIALOGUE_LEFT,
            BlockRole.DUAL_DIALOGUE_RIGHT,
        )

    /** The roles that are part of a speech rather than the cue that opens one. */
    private val SPOKEN =
        setOf(
            BlockRole.DIALOGUE,
            BlockRole.PARENTHETICAL,
            BlockRole.DUAL_DIALOGUE_LEFT,
            BlockRole.DUAL_DIALOGUE_RIGHT,
        )

    /** The roles where one block is one line, so a run of them is a run of lines. */
    private val PER_LINE = setOf(BlockRole.LYRIC, BlockRole.CENTERED)

    private const val LINE = "\n"
    private const val PARAGRAPH = "\n\n"
}
