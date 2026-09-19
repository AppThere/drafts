package com.appthere.drafts.core.parse.markdown

import com.appthere.drafts.core.model.Block
import com.appthere.drafts.core.model.DefEntry
import com.appthere.drafts.core.model.DefinitionList
import com.appthere.drafts.core.model.Document
import com.appthere.drafts.core.model.Inline
import com.appthere.drafts.core.model.LineBreak
import com.appthere.drafts.core.model.Paragraph
import com.appthere.drafts.core.model.SourceSpan
import com.appthere.drafts.core.model.Text

/**
 * Builds definition lists out of what the parser saw as ordinary paragraphs
 * (`markdown-dialect.md` 5).
 *
 * ```
 * Term
 * : Definition text
 * ```
 *
 * CommonMark has no such construct, so `intellij-markdown` produces one paragraph joined by soft
 * line breaks. The structure is recovered here by splitting that paragraph on its breaks and
 * reading the lines: a first line that is not a definition is the term, and every `: ` line after
 * it is a definition of that term.
 *
 * 5 calls this "the least standardised of the seven", with PHP Markdown Extra as the reference and
 * Goldmark as the behavioural target where Extra is ambiguous. Two rules from it shape the code:
 *
 * - "A `:` line with no preceding term line is a paragraph, not a definition." A lone `: text`
 *   stays exactly as it was.
 * - "A blank line between term and definition makes the list *loose*." A blank line means the
 *   parser produced two paragraphs rather than one, which is precisely how looseness is detected.
 */
internal object DefinitionListRestorer {
    fun apply(document: Document): Document = document.copy(blocks = fold(document.blocks))

    /**
     * Walks the block list, folding paragraphs into definition lists.
     *
     * A loose definition list spans two blocks -- the term's paragraph and the definitions' -- so
     * this cannot be a simple `map`; it has to be able to consume the block after the one it is
     * looking at.
     */
    private fun fold(blocks: List<Block>): List<Block> {
        val result = mutableListOf<Block>()
        var index = 0

        while (index < blocks.size) {
            val tight = (blocks[index] as? Paragraph)?.asTightDefinitionList()
            val loose = looseDefinitionList(blocks, index)

            when {
                tight != null -> {
                    result.add(tight)
                    index++
                }

                loose != null -> {
                    result.add(loose)
                    index += 2
                }

                else -> {
                    result.add(blocks[index])
                    index++
                }
            }
        }
        return result
    }

    /**
     * `Term` then `: definition` with no blank line, which the parser gives as one paragraph.
     */
    private fun Paragraph.asTightDefinitionList(): DefinitionList? {
        val lines = inlines.splitOnSoftBreaks()
        val isDefinitionList =
            lines.size >= TERM_PLUS_ONE_DEFINITION &&
                !lines.first().isDefinitionLine() &&
                lines.drop(1).all { it.isDefinitionLine() }

        return if (!isDefinitionList) {
            null
        } else {
            DefinitionList(
                entries =
                    listOf(
                        DefEntry(
                            term = lines.first(),
                            definitions = lines.drop(1).map { it.asDefinition() },
                        ),
                    ),
                source = source,
            )
        }
    }

    /**
     * `Term`, a blank line, then `: definition` -- which the parser gives as two paragraphs.
     *
     * The blank line is what makes the list loose, and 5 says loose definitions are wrapped in
     * paragraphs on output, so the flag is carried rather than inferred later.
     */
    private fun looseDefinitionList(
        blocks: List<Block>,
        index: Int,
    ): DefinitionList? {
        val term = blocks.getOrNull(index) as? Paragraph
        val definitions = blocks.getOrNull(index + 1) as? Paragraph
        if (term == null || definitions == null) return null

        val termLines = term.inlines.splitOnSoftBreaks()
        val definitionLines = definitions.inlines.splitOnSoftBreaks()

        val isDefinitionList =
            termLines.size == 1 &&
                !termLines.first().isDefinitionLine() &&
                definitionLines.isNotEmpty() &&
                definitionLines.all { it.isDefinitionLine() }

        return if (!isDefinitionList) {
            null
        } else {
            DefinitionList(
                entries =
                    listOf(
                        DefEntry(
                            term = termLines.first(),
                            definitions = definitionLines.map { it.asDefinition() },
                            loose = true,
                        ),
                    ),
                source = term.source.spanning(definitions.source),
            )
        }
    }

    /** Splits inline content into lines at soft breaks. Hard breaks stay inside a line. */
    private fun List<Inline>.splitOnSoftBreaks(): List<List<Inline>> {
        val lines = mutableListOf<List<Inline>>()
        var current = mutableListOf<Inline>()

        forEach { inline ->
            if (inline is LineBreak && !inline.hard) {
                lines.add(current)
                current = mutableListOf()
            } else {
                current.add(inline)
            }
        }
        lines.add(current)

        return lines.filter { it.isNotEmpty() }
    }

    /** A definition line opens with a colon and whitespace. */
    private fun List<Inline>.isDefinitionLine(): Boolean =
        (firstOrNull() as? Text)?.value?.let { it.startsWith(": ") || it.startsWith(":\t") } == true

    /** The line with its `: ` marker removed, as a paragraph. */
    private fun List<Inline>.asDefinition(): List<Block> {
        val first = first() as Text
        val body = first.value.removeRange(0, MARKER_LENGTH)
        val span = first.source?.let { SourceSpan.of(it.start.value + MARKER_LENGTH, it.endExclusive.value) }

        return listOf(
            Paragraph(
                inlines = listOfNotNull(body.takeIf { it.isNotEmpty() }?.let { Text(it, span) }) + drop(1),
                source = first.source,
            ),
        )
    }

    private fun SourceSpan?.spanning(other: SourceSpan?): SourceSpan? =
        when {
            this == null -> other
            other == null -> this
            else -> SourceSpan(start, other.endExclusive)
        }

    /** A term line and at least one definition line. */
    private const val TERM_PLUS_ONE_DEFINITION = 2

    /** `: ` */
    private const val MARKER_LENGTH = 2
}
