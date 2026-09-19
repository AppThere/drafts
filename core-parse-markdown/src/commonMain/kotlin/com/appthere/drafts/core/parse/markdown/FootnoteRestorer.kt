package com.appthere.drafts.core.parse.markdown

import com.appthere.drafts.core.model.Block
import com.appthere.drafts.core.model.Document
import com.appthere.drafts.core.model.FootnoteRef
import com.appthere.drafts.core.model.Heading
import com.appthere.drafts.core.model.Inline
import com.appthere.drafts.core.model.Paragraph
import com.appthere.drafts.core.model.SourceSpan
import com.appthere.drafts.core.model.Text

/**
 * Turns the parser's ordinary paragraphs and text back into footnotes (`markdown-dialect.md` 4).
 *
 * Three rules from the spec drive the shape of this:
 *
 * - "Definitions may appear anywhere in the document; they are collected in a first pass." So the
 *   definitions come out of the block list entirely and into [Document.footnotes]. Their source
 *   region then falls in a gap between blocks, which the serialiser copies verbatim -- so removing
 *   them costs nothing on the way back out.
 * - "Ordering in output follows order of *first reference*, not order of definition." The map is
 *   built by walking references in document order, which is why it is a `LinkedHashMap` and not a
 *   sorted or arbitrary one.
 * - "A reference with no definition renders as literal text." An unmatched `[^x]` is left exactly
 *   as the parser produced it, rather than becoming a [FootnoteRef] pointing at nothing.
 */
internal object FootnoteRestorer {
    fun apply(
        document: Document,
        source: String,
    ): Document {
        val markers = FootnoteScanner.definitions(source)
        if (markers.isEmpty()) return document

        val bodies = markers.associateBy { it.start }
        val remaining = mutableListOf<Block>()
        val definitions = mutableMapOf<String, List<Block>>()

        document.blocks.forEach { block ->
            val marker = block.source?.let { bodies[it.start.value] }
            if (marker == null) {
                remaining.add(block)
            } else {
                definitions[marker.label] = listOf(block.bodyAfter(marker.markerEnd))
            }
        }

        val references = FootnoteScanner.references(source, markers)

        return document.copy(
            blocks = remaining.map { it.withReferences(references, definitions.keys) },
            footnotes = orderByFirstReference(definitions, references),
        )
    }

    /**
     * The definition block with its `[^label]:` marker removed.
     *
     * The marker is part of the paragraph the parser built, so it has to come off the front of the
     * inline content -- leaving the body, with its spans still pointing where they did.
     */
    private fun Block.bodyAfter(markerEnd: Int): Block =
        when (this) {
            is Paragraph -> copy(inlines = inlines.dropBefore(markerEnd))
            else -> this
        }

    /**
     * Drops inline content lying before [offset], trimming the node that straddles it.
     *
     * A parsed [Text] value is exactly its span's slice of the source, so the surviving part can be
     * cut from the value without consulting the document.
     */
    private fun List<Inline>.dropBefore(offset: Int): List<Inline> =
        mapNotNull { inline ->
            val span = inline.source
            when {
                span == null || span.start.value >= offset -> inline
                span.endExclusive.value <= offset -> null
                inline is Text -> inline.tail(span, offset)
                else -> inline
            }
        }

    private fun Text.tail(
        span: SourceSpan,
        offset: Int,
    ): Text =
        Text(
            value = value.substring(offset - span.start.value),
            source = SourceSpan.of(offset, span.endExclusive.value),
        )

    /** Replaces the inline nodes covering each reference with a [FootnoteRef]. */
    private fun Block.withReferences(
        references: List<FootnoteReference>,
        defined: Set<String>,
    ): Block =
        when (this) {
            is Paragraph -> copy(inlines = inlines.replaceReferences(references, defined))
            is Heading -> copy(inlines = inlines.replaceReferences(references, defined))
            else -> this
        }

    private fun List<Inline>.replaceReferences(
        references: List<FootnoteReference>,
        defined: Set<String>,
    ): List<Inline> {
        // An undefined label stays literal text, so only defined references are candidates.
        val live = references.filter { it.label in defined }
        if (live.isEmpty()) return this

        val consumed = mutableSetOf<SourceSpan>()

        return flatMap { inline ->
            val span = inline.source
            val enclosing = span?.let { s -> live.firstOrNull { it.span.covers(s) } }

            when {
                enclosing != null -> {
                    if (consumed.add(enclosing.span)) listOf(enclosing.ref()) else emptyList()
                }

                inline is Text && span != null -> {
                    inline.splitAround(span, live, consumed)
                }

                else -> {
                    listOf(inline)
                }
            }
        }
    }

    private fun Text.splitAround(
        span: SourceSpan,
        references: List<FootnoteReference>,
        consumed: MutableSet<SourceSpan>,
    ): List<Inline> {
        val inside = references.filter { span.covers(it.span) }
        if (inside.isEmpty()) return listOf(this)

        val pieces = mutableListOf<Inline>()
        var cursor = span.start.value

        inside.forEach { reference ->
            val start = reference.span.start.value
            if (start > cursor) pieces.add(slice(span, cursor, start))
            if (consumed.add(reference.span)) pieces.add(reference.ref())
            cursor = reference.span.endExclusive.value
        }

        if (cursor < span.endExclusive.value) pieces.add(slice(span, cursor, span.endExclusive.value))
        return pieces
    }

    private fun Text.slice(
        span: SourceSpan,
        from: Int,
        to: Int,
    ): Text =
        Text(
            value = value.substring(from - span.start.value, to - span.start.value),
            source = SourceSpan.of(from, to),
        )

    private fun FootnoteReference.ref(): Inline = FootnoteRef(label, span)

    /**
     * Footnotes in order of first reference, per 4.
     *
     * A definition nobody references is not an error -- it is simply not rendered -- so it is kept,
     * after the referenced ones, rather than discarded.
     */
    private fun orderByFirstReference(
        definitions: Map<String, List<Block>>,
        references: List<FootnoteReference>,
    ): Map<String, List<Block>> {
        val ordered = linkedMapOf<String, List<Block>>()

        references.forEach { reference ->
            definitions[reference.label]?.let { ordered.putIfNotPresent(reference.label, it) }
        }
        definitions.forEach { (label, blocks) -> ordered.putIfNotPresent(label, blocks) }

        return ordered
    }

    private fun MutableMap<String, List<Block>>.putIfNotPresent(
        label: String,
        blocks: List<Block>,
    ) {
        if (label !in this) this[label] = blocks
    }
}
