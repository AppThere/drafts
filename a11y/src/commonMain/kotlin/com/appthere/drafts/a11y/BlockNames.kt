package com.appthere.drafts.a11y

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import com.appthere.drafts.core.model.Block
import com.appthere.drafts.core.model.BlockQuote
import com.appthere.drafts.core.model.BlockRole
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
import com.appthere.drafts.i18n.resources.Res
import com.appthere.drafts.i18n.resources.block_bulleted_list
import com.appthere.drafts.i18n.resources.block_character
import com.appthere.drafts.i18n.resources.block_code
import com.appthere.drafts.i18n.resources.block_code_in
import com.appthere.drafts.i18n.resources.block_definition_list
import com.appthere.drafts.i18n.resources.block_dialogue
import com.appthere.drafts.i18n.resources.block_figure
import com.appthere.drafts.i18n.resources.block_heading_level
import com.appthere.drafts.i18n.resources.block_link_reference
import com.appthere.drafts.i18n.resources.block_lyric
import com.appthere.drafts.i18n.resources.block_numbered_list
import com.appthere.drafts.i18n.resources.block_paragraph
import com.appthere.drafts.i18n.resources.block_parenthetical
import com.appthere.drafts.i18n.resources.block_quote
import com.appthere.drafts.i18n.resources.block_raw
import com.appthere.drafts.i18n.resources.block_scene_heading
import com.appthere.drafts.i18n.resources.block_section_break
import com.appthere.drafts.i18n.resources.block_synopsis
import com.appthere.drafts.i18n.resources.block_table
import com.appthere.drafts.i18n.resources.block_transition
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * What a kind of block is called, before anyone has looked up the words.
 *
 * A name rather than a string, because the words come from 11.1's resources and resources are read
 * from a composition. The editor decides a block's name once, where it caches everything else about
 * the row; the words are looked up where the row is drawn. Between those two points this is what
 * travels, and it compares by value -- so a block that has not changed kind is still equal to
 * itself after a reparse.
 */
@Immutable
sealed interface BlockName {
    /** A kind with one word for it. */
    data class Named(
        val word: StringResource,
    ) : BlockName

    /** "Heading level 2": the level is part of what a screen reader says, and it is a number. */
    data class HeadingAt(
        val level: Int,
    ) : BlockName

    /** "Code block, Kotlin": the fence's language, capitalised the way it is spoken. */
    data class CodeIn(
        val language: String,
    ) : BlockName
}

/**
 * What each kind of block is called to someone who cannot see it (`appthere-drafts.md` 10.1).
 *
 * "Each block exposes a role via Compose `semantics`. Headings use `heading()`. Other blocks get a
 * `contentDescription` prefix naming the type: ... 'Block quote', 'Code block, Kotlin'." The
 * editor applies these; what they say is decided here, once, so a quote is called the same thing
 * wherever it is announced.
 */
object BlockNames {
    /** The block's kind, as a screen reader should name it. */
    fun kindOf(block: Block): BlockName =
        when (block) {
            is Paragraph -> {
                BlockName.Named(Res.string.block_paragraph)
            }

            is Heading -> {
                BlockName.HeadingAt(block.level)
            }

            is BlockQuote -> {
                BlockName.Named(Res.string.block_quote)
            }

            is CodeBlock -> {
                codeBlock(block.language)
            }

            is ListBlock -> {
                BlockName.Named(
                    if (block.ordered) Res.string.block_numbered_list else Res.string.block_bulleted_list,
                )
            }

            is DefinitionList -> {
                BlockName.Named(Res.string.block_definition_list)
            }

            is Table -> {
                BlockName.Named(Res.string.block_table)
            }

            is ThematicBreak -> {
                BlockName.Named(Res.string.block_section_break)
            }

            is Figure -> {
                BlockName.Named(Res.string.block_figure)
            }

            is RawPassthrough -> {
                BlockName.Named(Res.string.block_raw)
            }

            is LinkReferenceDefinition -> {
                BlockName.Named(Res.string.block_link_reference)
            }
        }

    /**
     * The prefix 10.1 puts before a block's text, or null where it would only add noise.
     *
     * A paragraph is what a reader assumes a block is, so naming every one would put "Paragraph"
     * before most of the document -- unless it is one of a screenplay's elements, which are named.
     * A heading says what it is through its role instead -- `heading()` is what lets a screen reader
     * jump between them -- and a prefix as well would say it twice.
     */
    fun prefixOf(block: Block): BlockName? =
        when (block) {
            is Paragraph -> screenplayRoles[block.role]?.let(BlockName::Named)
            is Heading -> null
            else -> kindOf(block)
        }

    /**
     * A screenplay's elements, named as 10.1 names them -- "Scene heading", "Dialogue" -- since in a
     * screenplay a paragraph is never just a paragraph. Action is what a reader assumes, as a
     * paragraph is in prose, and goes unnamed.
     *
     * Only the prefix, not the kind: [changed] announces a change of kind, and a screenplay line
     * changes role as it is typed -- a name is action until someone speaks under it -- which would
     * be an announcement a keystroke.
     */
    private val screenplayRoles =
        mapOf(
            BlockRole.SCENE_HEADING to Res.string.block_scene_heading,
            BlockRole.CHARACTER to Res.string.block_character,
            BlockRole.PARENTHETICAL to Res.string.block_parenthetical,
            BlockRole.DIALOGUE to Res.string.block_dialogue,
            BlockRole.TRANSITION to Res.string.block_transition,
            BlockRole.LYRIC to Res.string.block_lyric,
            BlockRole.SYNOPSIS to Res.string.block_synopsis,
        )

    /**
     * 10.1's announcement for a structural edit -- "Structural edits (block promoted to heading,
     * blocks merged) get a `liveRegion` polite announcement: 'Heading level 2.'" -- or null when the
     * block is still the kind it was.
     *
     * Only a change of kind. Typing inside a paragraph changes the block and not what it is, and
     * announcing every keystroke's block would be exactly the chattiness 10.1 warns against.
     */
    fun changed(
        before: Block,
        after: Block,
    ): BlockName? = kindOf(after).takeIf { it != kindOf(before) }

    /** A fence's language, or nothing to say about one that has none. */
    private fun codeBlock(language: String?): BlockName =
        language
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { BlockName.CodeIn(it.replaceFirstChar(Char::uppercaseChar)) }
            ?: BlockName.Named(Res.string.block_code)
}

/** The words for a [BlockName], read from 11.1's resources. */
@Composable
fun BlockName.spoken(): String =
    when (this) {
        is BlockName.Named -> stringResource(word)
        is BlockName.HeadingAt -> stringResource(Res.string.block_heading_level, level.toString())
        is BlockName.CodeIn -> stringResource(Res.string.block_code_in, language)
    }
