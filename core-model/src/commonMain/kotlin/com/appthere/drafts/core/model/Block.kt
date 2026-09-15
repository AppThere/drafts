package com.appthere.drafts.core.model

/**
 * A block-level node.
 *
 * Every block carries a [role] (`export-pipeline.md`'s join between Markdown and Fountain), its
 * [attrs], and the [source] it came from. Blocks that were synthesised rather than parsed -- a
 * paragraph the user just created, say -- have a null [source], and that null is meaningful: it is
 * how the serialiser knows it cannot re-emit original bytes and must fall back to canonical style.
 */
sealed interface Block {
    val role: BlockRole
    val attrs: Attributes
    val source: SourceSpan?

    /**
     * Child blocks, flattened.
     *
     * A uniform accessor is what makes this a tree rather than ten special cases. [ListBlock]
     * holds a list of items each of which is a list of blocks, and [DefinitionList] holds entries;
     * both flatten here so that a walk does not need to know which it is looking at.
     */
    val children: List<Block> get() = emptyList()
}

/** Body text. The overwhelming majority of blocks in a manuscript. */
data class Paragraph(
    val inlines: List<Inline>,
    override val role: BlockRole = BlockRole.BODY,
    override val attrs: Attributes = Attributes.EMPTY,
    override val source: SourceSpan? = null,
) : Block

/**
 * A heading, level 1 to 6.
 *
 * [style] records ATX (`# Heading`) versus Setext (`Heading` over `=====`). The round-trip
 * contract's canonical style is ATX for anything re-serialised, but an untouched Setext heading
 * has to come back as Setext, and a heading whose *text* was edited still needs to know which it
 * was.
 */
data class Heading(
    val level: Int,
    val inlines: List<Inline>,
    val style: HeadingStyle = HeadingStyle.ATX,
    override val attrs: Attributes = Attributes.EMPTY,
    override val source: SourceSpan? = null,
) : Block {
    override val role: BlockRole get() = BlockRole.HEADING

    init {
        require(level in MIN_LEVEL..MAX_LEVEL) {
            "Heading level must be $MIN_LEVEL..$MAX_LEVEL, got $level"
        }
        require(style == HeadingStyle.ATX || level <= MAX_SETEXT_LEVEL) {
            "Setext headings only reach level $MAX_SETEXT_LEVEL, got $level"
        }
    }

    companion object {
        const val MIN_LEVEL = 1
        const val MAX_LEVEL = 6

        /** Setext underlining expresses only two levels: `===` and `---`. */
        const val MAX_SETEXT_LEVEL = 2
    }
}

/** How a heading was written. */
enum class HeadingStyle {
    /** `## Heading` */
    ATX,

    /** `Heading` followed by `====` or `----`. */
    SETEXT,
}

/**
 * A bullet or ordered list.
 *
 * [tight] is CommonMark's distinction: a tight list renders its items without wrapping paragraphs,
 * a loose one with. [marker] is round-trip fidelity -- a document written with `*` bullets should
 * not come back with `-` bullets merely because it was opened.
 */
data class ListBlock(
    val ordered: Boolean,
    val items: List<List<Block>>,
    val start: Int = 1,
    val tight: Boolean = true,
    val marker: ListMarker = ListMarker.DASH,
    override val attrs: Attributes = Attributes.EMPTY,
    override val source: SourceSpan? = null,
) : Block {
    override val role: BlockRole get() = BlockRole.LIST_ITEM

    override val children: List<Block> get() = items.flatten()

    init {
        require(ordered == marker.isOrdered) {
            "Marker $marker does not match ordered=$ordered"
        }
    }
}

/** The character that introduces each list item. */
enum class ListMarker(
    val char: Char,
    val isOrdered: Boolean,
) {
    DASH('-', isOrdered = false),
    ASTERISK('*', isOrdered = false),
    PLUS('+', isOrdered = false),

    /** `1.` */
    PERIOD('.', isOrdered = true),

    /** `1)` */
    PAREN(')', isOrdered = true),
}

/**
 * A definition list (`markdown-dialect.md` 5).
 *
 * The least standardised of the dialect's extensions; PHP Markdown Extra is the reference and
 * Goldmark is the behavioural target where Extra is ambiguous.
 */
data class DefinitionList(
    val entries: List<DefEntry>,
    override val attrs: Attributes = Attributes.EMPTY,
    override val source: SourceSpan? = null,
) : Block {
    override val role: BlockRole get() = BlockRole.DEFINITION_TERM

    override val children: List<Block> get() = entries.flatMap { it.definitions.flatten() }
}

/**
 * One term and its definitions.
 *
 * [loose] follows 5: "a blank line between term and definition makes the list loose", which
 * decides whether definitions are wrapped in paragraphs on output.
 */
data class DefEntry(
    val term: List<Inline>,
    val definitions: List<List<Block>>,
    val loose: Boolean = false,
)

/**
 * A code block.
 *
 * [fence] is null for an indented code block. That is not a cosmetic difference: re-emitting an
 * indented block as fenced changes which characters are significant, and a block whose content
 * contains backticks cannot be fenced with the same run length it was found with.
 */
data class CodeBlock(
    val text: String,
    val language: String? = null,
    val fence: CodeFence? = null,
    override val attrs: Attributes = Attributes.EMPTY,
    override val source: SourceSpan? = null,
) : Block {
    override val role: BlockRole get() = BlockRole.CODE
}

/** The fence of a fenced code block: which character, and how many of them. */
data class CodeFence(
    val char: Char,
    val length: Int,
) {
    init {
        require(char == BACKTICK || char == TILDE) {
            "A code fence is made of backticks or tildes, got '$char'"
        }
        require(length >= MIN_LENGTH) { "A code fence needs at least $MIN_LENGTH characters, got $length" }
    }

    companion object {
        const val BACKTICK = '`'
        const val TILDE = '~'
        const val MIN_LENGTH = 3

        val DEFAULT = CodeFence(BACKTICK, MIN_LENGTH)
    }
}

/** A block quote. */
data class BlockQuote(
    override val children: List<Block>,
    override val attrs: Attributes = Attributes.EMPTY,
    override val source: SourceSpan? = null,
) : Block {
    override val role: BlockRole get() = BlockRole.QUOTE
}

/**
 * A GFM table (`markdown-dialect.md` 1).
 *
 * Cells hold inline content only -- no block content -- which 1 states directly. [rows] does not
 * include the header; [header] is separate because every backend treats it differently and
 * because the delimiter row's cell count must match it or the construct is not a table at all.
 */
data class Table(
    val header: List<List<Inline>>,
    val alignments: List<Align>,
    val rows: List<List<List<Inline>>>,
    override val attrs: Attributes = Attributes.EMPTY,
    override val source: SourceSpan? = null,
) : Block {
    override val role: BlockRole get() = BlockRole.BODY

    init {
        require(alignments.size == header.size) {
            "A table's delimiter row must have the same cell count as its header " +
                "(${alignments.size} vs ${header.size})"
        }
    }
}

/** Column alignment, set by the colons in a table's delimiter row. */
enum class Align {
    NONE,
    LEFT,
    CENTER,
    RIGHT,
}

/** `---`, `***`, `___`. */
data class ThematicBreak(
    override val attrs: Attributes = Attributes.EMPTY,
    override val source: SourceSpan? = null,
) : Block {
    override val role: BlockRole get() = BlockRole.BODY
}

/** An image promoted to a block, with an optional caption. */
data class Figure(
    val image: Image,
    val caption: List<Inline>? = null,
    override val attrs: Attributes = Attributes.EMPTY,
    override val source: SourceSpan? = null,
) : Block {
    override val role: BlockRole get() = BlockRole.BODY
}

/**
 * Block content that is never parsed and never reformatted: a shortcode on its own line, a block
 * of raw HTML, or anything else that must survive verbatim.
 *
 * Round-trip contract item 5 -- "unknown constructs pass through" -- lands here. Anything the
 * parser does not recognise is retained rather than dropped, which is the difference between an
 * editor and a renderer.
 */
data class RawPassthrough(
    val text: String,
    val origin: Origin,
    override val attrs: Attributes = Attributes.EMPTY,
    override val source: SourceSpan? = null,
) : Block {
    override val role: BlockRole get() = BlockRole.BODY
}

/**
 * A link reference definition: `[label]: /url "title"`.
 *
 * Produces no output of its own, but has to be kept. CommonMark removes these from the block
 * structure entirely; an editor cannot, because deleting one on save would break every
 * reference-style link that pointed at it.
 */
data class LinkReferenceDefinition(
    val label: String,
    val href: String,
    val title: String? = null,
    override val attrs: Attributes = Attributes.EMPTY,
    override val source: SourceSpan? = null,
) : Block {
    override val role: BlockRole get() = BlockRole.BODY
}
