package com.appthere.drafts.core.model

/**
 * Inline content: the things that live inside a paragraph, a heading, or a table cell.
 *
 * Several of these carry more than a renderer would need -- which delimiter the author used, how
 * many backticks, whether a link was written inline or by reference. That is deliberate and it is
 * what `markdown-dialect.md`'s round-trip contract asks for. This is an editor: a document that
 * comes back from a save with `_word_` rewritten as `*word*` has been damaged, even though every
 * renderer agrees the two are identical.
 *
 * Recording the choice in the IR rather than recovering it from [source] means the serialiser is
 * correct for edited subtrees too, not only untouched ones.
 */
sealed interface Inline {
    /** Where this came from, or null if it was synthesised rather than parsed. */
    val source: SourceSpan?

    /** Nested inline content. Empty for leaves. */
    val children: List<Inline> get() = emptyList()
}

/** Literal text. The typographer never runs over this on the way in -- see `markdown-dialect.md` 6. */
data class Text(
    val value: String,
    override val source: SourceSpan? = null,
) : Inline

/**
 * `*emphasis*` or `**strong**`.
 *
 * [delimiter] exists because round-trip contract item 3 requires that `*x*` stays `*x*` and `_x_`
 * stays `_x_`. Phase 1's acceptance names "mixed emphasis delimiters" explicitly as something a
 * corpus round-trip must survive.
 */
data class Emphasis(
    val strong: Boolean,
    override val children: List<Inline>,
    val delimiter: EmphasisDelimiter = EmphasisDelimiter.ASTERISK,
    override val source: SourceSpan? = null,
) : Inline

/** Which character the author used to mark emphasis. Both are valid CommonMark; neither is canonical. */
enum class EmphasisDelimiter(
    val char: Char,
) {
    ASTERISK('*'),
    UNDERSCORE('_'),
}

/**
 * `~struck~` or `~~struck~~`.
 *
 * [tildeCount] is 1 or 2. `markdown-dialect.md` 2 accepts both and says three or more is not
 * strikethrough at all, so the count has to survive a round trip the same way an emphasis
 * delimiter does.
 */
data class Strikethrough(
    override val children: List<Inline>,
    val tildeCount: Int = 2,
    override val source: SourceSpan? = null,
) : Inline {
    init {
        require(tildeCount in 1..2) {
            "Strikethrough uses one or two tildes; three or more is literal text. Got $tildeCount"
        }
    }
}

/**
 * `` `code` ``.
 *
 * [backtickCount] is how many backticks fenced it. A span containing a backtick needs two to fence
 * it, and re-emitting with one would change the meaning rather than merely the spelling.
 */
data class CodeSpan(
    val text: String,
    val backtickCount: Int = 1,
    override val source: SourceSpan? = null,
) : Inline {
    init {
        require(backtickCount >= 1) { "A code span needs at least one backtick, got $backtickCount" }
    }
}

/**
 * A link.
 *
 * [form] records how it was written. The round-trip contract's canonical-style note says
 * "reference-style links preserved as found", which is not something a serialiser can work out
 * from an href alone.
 */
data class Link(
    val href: String,
    override val children: List<Inline>,
    val title: String? = null,
    val form: LinkForm = LinkForm.Inline,
    override val source: SourceSpan? = null,
) : Inline

/** How a link was spelled in the source. */
sealed interface LinkForm {
    /** `[text](/url "title")` */
    data object Inline : LinkForm

    /** `[text][label]` */
    data class Reference(
        val label: String,
    ) : LinkForm

    /** `[label][]` -- the label doubles as the text. */
    data class Collapsed(
        val label: String,
    ) : LinkForm

    /** `[label]` -- no brackets after it at all. */
    data class Shortcut(
        val label: String,
    ) : LinkForm

    /** `<https://example.com>`, or a bare URL promoted by linkify (`markdown-dialect.md` 3). */
    data class Autolink(
        val linkified: Boolean,
    ) : LinkForm
}

/**
 * An image.
 *
 * Carries [attrs] because `markdown-dialect.md` 7 allows an attribute block on the line after a
 * standalone image, and those attributes belong to the image rather than to the paragraph
 * containing it.
 */
data class Image(
    val src: String,
    val alt: String,
    val title: String? = null,
    val attrs: Attributes = Attributes.EMPTY,
    override val source: SourceSpan? = null,
) : Inline

/**
 * `[^label]`.
 *
 * Holds only the label. The body lives in [Document.footnotes], because
 * `markdown-dialect.md` 4 allows a definition to appear anywhere and to be referenced more than
 * once, and output ordering follows first reference rather than definition order.
 */
data class FootnoteRef(
    val label: String,
    override val source: SourceSpan? = null,
) : Inline

/** A line break: two trailing spaces or a backslash if [hard], otherwise a plain newline. */
data class LineBreak(
    val hard: Boolean,
    override val source: SourceSpan? = null,
) : Inline

/**
 * Inline content that is never parsed and never reformatted -- a Hugo shortcode inside a
 * paragraph, or a span of raw HTML.
 *
 * `markdown-dialect.md` describes shortcodes as "tokenised into opaque atomic spans before
 * parsing, restored verbatim on serialise". Opaque is the operative word: nothing downstream may
 * inspect [text], and no transform may touch it.
 */
data class RawInline(
    val text: String,
    val origin: Origin,
    override val source: SourceSpan? = null,
) : Inline

/** Where a piece of passthrough content came from, so it can be restored in the same spelling. */
enum class Origin {
    /** `{{< shortcode >}}` */
    HUGO_SHORTCODE_ANGLE,

    /** `{{% shortcode %}}` */
    HUGO_SHORTCODE_PERCENT,

    /** Raw HTML, which CommonMark permits and this dialect passes through untouched. */
    RAW_HTML,
}
