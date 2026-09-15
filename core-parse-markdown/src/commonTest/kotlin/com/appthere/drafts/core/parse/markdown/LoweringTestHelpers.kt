package com.appthere.drafts.core.parse.markdown

import com.appthere.drafts.core.model.Block
import com.appthere.drafts.core.model.CodeSpan
import com.appthere.drafts.core.model.Document
import com.appthere.drafts.core.model.Emphasis
import com.appthere.drafts.core.model.Heading
import com.appthere.drafts.core.model.Inline
import com.appthere.drafts.core.model.Link
import com.appthere.drafts.core.model.Paragraph
import com.appthere.drafts.core.model.Text

/*
 * Shared by the lowering tests, which are split by concern the same way the lowering itself is.
 *
 * These read the IR rather than the CST on purpose. A test that asserted node types would break on
 * an `intellij-markdown` upgrade without anything actually being wrong, and the IR is the contract
 * that matters to everything downstream.
 */

/** Inline content of a block, for the block types that have any. */
internal fun Block.inlinesOf(): List<Inline> =
    when (this) {
        is Paragraph -> inlines
        is Heading -> inlines
        else -> emptyList()
    }

/** The first inline of a given type anywhere in the document. */
internal inline fun <reified T : Inline> Document.firstInline(): T =
    blocks.flatMap { it.inlinesOf() }.filterIsInstance<T>().firstOrNull()
        ?: error("No ${T::class.simpleName} found in $blocks")

internal fun Paragraph.plainText(): String = inlines.plainText()

/** Text content with all markup flattened away -- what a reader would see. */
internal fun List<Inline>.plainText(): String =
    joinToString("") { inline ->
        when (inline) {
            is Text -> inline.value
            is Emphasis -> inline.children.plainText()
            is Link -> inline.children.plainText()
            is CodeSpan -> inline.text
            else -> ""
        }
    }
