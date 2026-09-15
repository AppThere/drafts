package com.appthere.drafts.core.parse.markdown

import com.appthere.drafts.core.model.Origin
import com.appthere.drafts.core.model.SourceSpan

/**
 * A Hugo shortcode found in the source, and the span it occupies.
 *
 * `markdown-dialect.md`: shortcodes are "tokenised into opaque atomic spans before parsing,
 * restored verbatim on serialise. Never parsed, never reformatted." Opaque is the operative word --
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
 * Deliberately a scan over the raw source rather than a masking pre-pass. Masking -- substituting
 * inert characters of the same length before parsing -- is the obvious implementation of
 * "tokenised before parsing" and it has a sharp edge: a code fence containing shortcode syntax
 * would come back with the mask characters in its text, because a code block's text is read from
 * whatever string the parser saw.
 *
 * Scanning afterwards avoids that entirely. The parser sees the real document, code blocks keep
 * their real contents, and the shortcode regions are collapsed into opaque nodes once parsing is
 * done. What the parser makes of the shortcode's *innards* in the meantime does not matter,
 * because [ShortcodeRestorer] discards those nodes wholesale.
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
