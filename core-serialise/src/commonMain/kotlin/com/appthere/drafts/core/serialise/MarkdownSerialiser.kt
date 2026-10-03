package com.appthere.drafts.core.serialise

import com.appthere.drafts.core.model.BlockIndex
import com.appthere.drafts.core.model.Document

/**
 * [Document] out to Markdown, source-preserving.
 *
 * Byte-preservation -- which is the whole safety story, and is not Markdown-specific -- lives in
 * [SourcePreserving]. What is here is the two things that are specific to Markdown: how a block is
 * spelled ([BlockWriter]) and what separates two of them.
 *
 * The separator is a blank line, always. Markdown's block structure does not depend on how many
 * blank lines there are, only on there being one, so there is nothing for this to decide.
 */
class MarkdownSerialiser {
    private val preserving =
        SourcePreserving(
            text = BlockWriter(InlineWriter())::write,
            gap = { _, _ -> BLOCK_SEPARATOR },
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

    private companion object {
        const val BLOCK_SEPARATOR = "\n\n"
    }
}
