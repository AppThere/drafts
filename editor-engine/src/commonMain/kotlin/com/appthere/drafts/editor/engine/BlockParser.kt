package com.appthere.drafts.editor.engine

import com.appthere.drafts.core.fountain.FountainKeywords
import com.appthere.drafts.core.fountain.isCharacter
import com.appthere.drafts.core.model.Block
import com.appthere.drafts.core.model.BlockRole
import com.appthere.drafts.core.model.CodeBlock
import com.appthere.drafts.core.model.SourceSpan
import com.appthere.drafts.core.parse.fountain.FountainDocumentParser
import com.appthere.drafts.core.parse.markdown.MarkdownDocumentParser

/**
 * How one kind of document is read into blocks: the whole of it once, and a window of it after
 * every edit.
 *
 * `appthere-drafts.md` 9.1 gives each kind its own grammar and 4.3 reparses only the blocks around
 * an edit. The two kinds want different things from that window. Markdown's blocks can be read on
 * their own, so a window is cut out and parsed as text. Fountain is positional -- a character is an
 * uppercase line *with dialogue under it*, a title page exists only at the start of the file, and a
 * speech continues across a blank line of spaces -- so its window is first widened to whole chunks
 * and then read in the context of the text around it.
 */
sealed interface BlockParser {
    /** Every block of [text]. */
    fun parse(text: String): List<Block>

    /** [span] widened so that [reparse] can read it correctly. */
    fun window(
        text: String,
        span: SourceSpan,
    ): SourceSpan

    /** The blocks of [window] of [text], in document offsets. [above] is the block just before it. */
    fun reparse(
        text: String,
        window: SourceSpan,
        above: Block?,
    ): List<Block>

    /** What Enter inserts at document offset [at] of [text], which is in [block]. */
    fun enterAt(
        text: String,
        block: Block,
        at: Int,
    ): Enter

    /** CommonMark with this application's extensions (`markdown-dialect.md`). */
    class Markdown : BlockParser {
        private val parser = MarkdownDocumentParser()

        override fun parse(text: String): List<Block> = parser.parse(text).blocks

        override fun window(
            text: String,
            span: SourceSpan,
        ): SourceSpan = span

        override fun reparse(
            text: String,
            window: SourceSpan,
            above: Block?,
        ): List<Block> =
            parser
                .parse(text.substring(window.start.value, window.endExclusive.value))
                .blocks
                .map { it.shiftedBy(window.start.value) }

        /**
         * A blank line ends a paragraph, but inside a fenced code block it does not end anything --
         * the fence runs to its closing marker. Inserting one there would give the author two lines
         * where they asked for one, every time they pressed Enter while writing code.
         */
        override fun enterAt(
            text: String,
            block: Block,
            at: Int,
        ): Enter = if (block is CodeBlock) Enter(LINE_BREAK) else Enter(BLOCK_SEPARATOR)
    }

    /** Fountain 1.1 (`fountain.md`), with the scene-heading and transition words of [keywords]. */
    class Fountain(
        /** The scene-heading words and transition ending this screenplay is read with (11.3). */
        val keywords: FountainKeywords = FountainKeywords.ENGLISH,
    ) : BlockParser {
        private val parser = FountainDocumentParser(keywords)

        override fun parse(text: String): List<Block> = parser.parse(text).blocks

        override fun window(
            text: String,
            span: SourceSpan,
        ): SourceSpan = parser.windowAround(text, span.start.value, span.endExclusive.value)

        override fun reparse(
            text: String,
            window: SourceSpan,
            above: Block?,
        ): List<Block> = parser.parseWindow(text, window.start.value, window.endExclusive.value, above?.role)

        /**
         * A speech is written a line at a time with no blank line inside it: a blank line after a
         * name ends the speech before it starts, and the name, with no one speaking under it, reads
         * as action. So Enter at the end of a name -- or of a parenthetical -- with nothing under it
         * yet breaks the line once and opens a dialogue line there to type into. Enter again on
         * that empty line breaks it once more, which makes the blank line that ends the speech: the
         * way to go from a name straight to action, as a screenwriting application does it.
         *
         * Everywhere else Enter is the blank line that separates two elements.
         */
        override fun enterAt(
            text: String,
            block: Block,
            at: Int,
        ): Enter =
            when {
                opensSpeech(text, block, at) -> Enter(LINE_BREAK, opens = BlockRole.DIALOGUE)
                block.role == BlockRole.DIALOGUE && block.source?.length == 0 -> Enter(LINE_BREAK)
                else -> Enter(BLOCK_SEPARATOR)
            }

        /** Whether [at] is the end of a name or parenthetical that has nothing under it yet. */
        private fun opensSpeech(
            text: String,
            block: Block,
            at: Int,
        ): Boolean {
            val span = block.source ?: return false
            val line = text.substring(span.start.value, span.endExclusive.value)
            val heading =
                when (block.role) {
                    BlockRole.CHARACTER, BlockRole.PARENTHETICAL -> true

                    // A name typed and not yet spoken under is action until the dialogue arrives.
                    BlockRole.ACTION -> '\n' !in line && isCharacter(line)

                    else -> false
                }

            return heading && at == span.endExclusive.value && nothingUnder(text, at)
        }

        /** Whether the line after the one ending at [at] is empty, or there is none. */
        private fun nothingUnder(
            text: String,
            at: Int,
        ): Boolean = at == text.length || (text[at] == '\n' && (at + 1 == text.length || text[at + 1] == '\n'))
    }
}

/**
 * What Enter inserts: [inserted] at the caret, and -- when the line it makes is outside every block,
 * as the line under a character's name is -- the role of the empty line it [opens] for the caret.
 */
class Enter(
    val inserted: String,
    val opens: BlockRole? = null,
)

/** The blank line that separates two block-level constructs. */
private const val BLOCK_SEPARATOR = "\n\n"

/** One line break, which continues what it is in rather than ending it. */
private const val LINE_BREAK = "\n"
