package com.appthere.drafts.editor.engine

import com.appthere.drafts.core.fountain.FountainKeywords
import com.appthere.drafts.core.model.Block
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
    }

    /** Fountain 1.1 (`fountain.md`), with the scene-heading and transition words of [keywords]. */
    class Fountain(
        keywords: FountainKeywords = FountainKeywords.ENGLISH,
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
    }
}
