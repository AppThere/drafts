package com.appthere.drafts.core.serialise

import com.appthere.drafts.core.model.Align
import com.appthere.drafts.core.model.Block
import com.appthere.drafts.core.model.BlockQuote
import com.appthere.drafts.core.model.CodeBlock
import com.appthere.drafts.core.model.DefinitionList
import com.appthere.drafts.core.model.Figure
import com.appthere.drafts.core.model.Heading
import com.appthere.drafts.core.model.HeadingStyle
import com.appthere.drafts.core.model.LinkReferenceDefinition
import com.appthere.drafts.core.model.ListBlock
import com.appthere.drafts.core.model.Paragraph
import com.appthere.drafts.core.model.RawPassthrough
import com.appthere.drafts.core.model.Table
import com.appthere.drafts.core.model.ThematicBreak

/**
 * Writes [Block] content back to Markdown, from the IR alone.
 *
 * This is the canonical path -- the one taken for blocks the user edited or created, where there
 * are no original bytes to re-emit. [MarkdownSerialiser] prefers the source whenever it can.
 *
 * Structural spelling comes from the node: a list that was written with `*` is written back with
 * `*`, a Setext heading stays Setext, an indented code block stays indented. The IR's defaults are
 * the round-trip contract's canonical style, so a synthesised block lands on it naturally.
 */
internal class BlockWriter(
    private val inlines: InlineWriter,
) {
    fun write(block: Block): String =
        when (block) {
            is Paragraph -> inlines.write(block.inlines)
            is Heading -> heading(block)
            is ListBlock -> list(block)
            is BlockQuote -> quote(block)
            is CodeBlock -> code(block)
            is ThematicBreak -> THEMATIC_BREAK
            is LinkReferenceDefinition -> definition(block)
            is RawPassthrough -> block.text
            is Figure -> figure(block)
            is DefinitionList -> definitionList(block)
            is Table -> table(block)
            else -> ""
        }

    private fun heading(block: Heading): String {
        val text = inlines.write(block.inlines)
        return when (block.style) {
            HeadingStyle.ATX -> {
                "${"#".repeat(block.level)} $text"
            }

            HeadingStyle.SETEXT -> {
                val rule = if (block.level == 1) "=" else "-"
                "$text\n" + rule.repeat(maxOf(text.length, MIN_SETEXT_RULE))
            }
        }
    }

    /**
     * A list, with each item indented to hang under its own marker.
     *
     * Loose lists get a blank line between items, which is what makes them loose -- the flag is not
     * decoration, it is the thing that has to be written back for the document to reparse the same
     * way.
     */
    private fun list(block: ListBlock): String {
        val separator = if (block.tight) "\n" else "\n\n"

        return block.items
            .mapIndexed { index, item ->
                val marker =
                    if (block.ordered) "${block.start + index}${block.marker.char}" else "${block.marker.char}"
                val body = item.joinToString("\n\n") { write(it) }
                val indent = " ".repeat(marker.length + 1)

                "$marker ${body.prependIndentAfterFirstLine(indent)}"
            }.joinToString(separator)
    }

    private fun quote(block: BlockQuote): String =
        block.children
            .joinToString("\n\n") { write(it) }
            .lines()
            .joinToString("\n") { if (it.isEmpty()) ">" else "> $it" }

    private fun code(block: CodeBlock): String {
        val fence = block.fence
        return if (fence == null) {
            block.text.lines().joinToString("\n") { if (it.isEmpty()) it else INDENT + it }
        } else {
            val rule = fence.char.toString().repeat(fence.length)
            "$rule${block.language.orEmpty()}\n${block.text.trimEnd('\n')}\n$rule"
        }
    }

    private fun definition(block: LinkReferenceDefinition): String =
        "[${block.label}]: ${block.href}" + block.title?.let { """ "$it"""" }.orEmpty()

    private fun figure(block: Figure): String {
        val image = inlines.write(listOf(block.image))
        return block.caption?.let { "$image\n${inlines.write(it)}" } ?: image
    }

    private fun definitionList(block: DefinitionList): String =
        block.entries.joinToString("\n\n") { entry ->
            val definitions =
                entry.definitions.joinToString("\n") { blocks ->
                    ": " + blocks.joinToString("\n\n") { write(it) }
                }
            inlines.write(entry.term) + "\n" + definitions
        }

    private fun table(block: Table): String {
        val header = block.header.joinToString(" | ", "| ", " |") { inlines.write(it) }
        val rule = block.alignments.joinToString(" | ", "| ", " |") { it.rule() }
        val rows =
            block.rows.joinToString("\n") { row ->
                row.joinToString(" | ", "| ", " |") { inlines.write(it) }
            }

        return listOf(header, rule, rows).filter { it.isNotEmpty() }.joinToString("\n")
    }

    private fun Align.rule(): String =
        when (this) {
            Align.LEFT -> ":---"
            Align.RIGHT -> "---:"
            Align.CENTER -> ":---:"
            Align.NONE -> "---"
        }

    /** Indents every line after the first, so continuation lines hang under the item's text. */
    private fun String.prependIndentAfterFirstLine(indent: String): String =
        lines()
            .mapIndexed { index, line ->
                if (index == 0 || line.isEmpty()) line else indent + line
            }.joinToString("\n")

    private companion object {
        const val THEMATIC_BREAK = "---"
        const val INDENT = "    "

        /** Short headings still get a rule long enough to read as one. */
        const val MIN_SETEXT_RULE = 3
    }
}
