package com.appthere.drafts.core.parse.markdown

import com.appthere.drafts.core.model.Origin
import com.appthere.drafts.core.model.SourceSpan

/**
 * A Hugo shortcode found in the source, and the span it occupies.
 *
 * `markdown-dialect.md`: shortcodes are "collapsed into opaque atomic spans after parsing ...
 * Restored verbatim on serialise. Never reformatted." Opaque is the operative word --
 * nothing downstream may look inside [text], and no transform may touch it.
 */
internal data class ShortcodeRegion(
    val span: SourceSpan,
    val text: String,
    val origin: Origin,
) {
    /** True when [other] lies entirely within this region. */
    fun covers(other: SourceSpan): Boolean = span.covers(other)
}

/**
 * Finds the shortcodes in a document.
 *
 * `hugo-markdown.md` describes Hugo's pipeline: shortcodes are extracted *before* Goldmark sees the
 * text, replaced with placeholder tokens, and substituted back afterwards -- "if you're building an
 * editor, mirror this: treat shortcodes as opaque atomic spans."
 *
 * Mirrored by scanning the source and collapsing the nodes that cover each region, rather than by
 * masking before the parse. The two differ only in what the parser makes of a shortcode's innards
 * in the meantime, and [ShortcodeRestorer] discards those nodes wholesale either way.
 *
 * **Known divergence from Hugo, inside code.** Hugo extracts shortcodes everywhere, code fences
 * included -- which is why a Hugo author has to escape one to show it literally. This parser leaves
 * the contents of a code block alone, because `CodeBlock.text` is a string and cannot hold an
 * opaque span. For an editor that is the right thing to display: the author sees what they typed.
 * It will matter when export exists, and Phase 10 is where the two have to be reconciled.
 */
internal object ShortcodeScanner {
    fun find(source: String): List<ShortcodeRegion> =
        (
            regionsOf(source, angleShortcode, Origin.HUGO_SHORTCODE_ANGLE) +
                regionsOf(source, percentShortcode, Origin.HUGO_SHORTCODE_PERCENT)
        ).sortedBy { it.span.start.value }

    private fun regionsOf(
        source: String,
        pattern: Regex,
        origin: Origin,
    ): List<ShortcodeRegion> =
        pattern
            .findAll(source)
            .map { match ->
                ShortcodeRegion(
                    span = SourceSpan.of(match.range.first, match.range.last + 1),
                    text = match.value,
                    origin = origin,
                )
            }.toList()
}

/**
 * `{{< … >}}`. Non-greedy and dot-matches-newline: a shortcode may legitimately span lines, and a
 * greedy match would swallow everything between the first and last shortcode in the file.
 */
private val angleShortcode = Regex("""\{\{<[\s\S]*?>\}\}""")

/** `{{% … %}}`, the Markdown-processing variant. Same shape, different delimiter. */
private val percentShortcode = Regex("""\{\{%[\s\S]*?%\}\}""")
