package com.appthere.drafts.a11y

import com.appthere.drafts.core.model.Block
import com.appthere.drafts.core.model.BlockQuote
import com.appthere.drafts.core.model.CodeBlock
import com.appthere.drafts.core.model.DefinitionList
import com.appthere.drafts.core.model.Figure
import com.appthere.drafts.core.model.Heading
import com.appthere.drafts.core.model.LinkReferenceDefinition
import com.appthere.drafts.core.model.ListBlock
import com.appthere.drafts.core.model.Paragraph
import com.appthere.drafts.core.model.RawPassthrough
import com.appthere.drafts.core.model.Table
import com.appthere.drafts.core.model.ThematicBreak
import com.appthere.drafts.i18n.Strings

/**
 * What each kind of block is called to someone who cannot see it (`appthere-drafts.md` 10.1).
 *
 * "Each block exposes a role via Compose `semantics`. Headings use `heading()`. Other blocks get a
 * `contentDescription` prefix naming the type: ... 'Block quote', 'Code block, Kotlin'." The
 * editor applies these; what they say is decided here, once, so a quote is called the same thing
 * wherever it is announced.
 */
object BlockNames {
    /** The block's kind, as a screen reader should name it: "Block quote", "Heading level 2". */
    fun kindOf(block: Block): String =
        when (block) {
            is Paragraph -> Strings.BLOCK_PARAGRAPH
            is Heading -> "${Strings.BLOCK_HEADING_LEVEL} ${block.level}"
            is BlockQuote -> Strings.BLOCK_QUOTE
            is CodeBlock -> codeBlock(block.language)
            is ListBlock -> if (block.ordered) Strings.BLOCK_NUMBERED_LIST else Strings.BLOCK_BULLETED_LIST
            is DefinitionList -> Strings.BLOCK_DEFINITION_LIST
            is Table -> Strings.BLOCK_TABLE
            is ThematicBreak -> Strings.BLOCK_SECTION_BREAK
            is Figure -> Strings.BLOCK_FIGURE
            is RawPassthrough -> Strings.BLOCK_RAW
            is LinkReferenceDefinition -> Strings.BLOCK_LINK_REFERENCE
        }

    /**
     * The prefix 10.1 puts before a block's text, or null where it would only add noise.
     *
     * A paragraph is what a reader assumes a block is, so naming every one would put "Paragraph"
     * before most of the document. A heading says what it is through its role instead -- `heading()`
     * is what lets a screen reader jump between them -- and a prefix as well would say it twice.
     */
    fun prefixOf(block: Block): String? =
        when (block) {
            is Paragraph, is Heading -> null
            else -> kindOf(block)
        }

    /**
     * 10.1's announcement for a structural edit -- "Structural edits (block promoted to heading,
     * blocks merged) get a `liveRegion` polite announcement: 'Heading level 2.'" -- or null when the
     * block is still the kind it was.
     *
     * Only a change of kind. Typing inside a paragraph changes the block and not what it is, and
     * announcing every keystroke's block would be exactly the chattiness 10.1 warns against.
     */
    fun announcement(
        before: Block,
        after: Block,
    ): String? = kindOf(after).takeIf { it != kindOf(before) }?.let { "$it." }

    /** "Code block, Kotlin": a fence's language as a name, capitalised the way it is spoken. */
    private fun codeBlock(language: String?): String =
        language
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { "${Strings.BLOCK_CODE}, ${it.replaceFirstChar(Char::uppercaseChar)}" }
            ?: Strings.BLOCK_CODE
}
