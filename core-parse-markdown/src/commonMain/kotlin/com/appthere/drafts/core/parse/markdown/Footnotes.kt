package com.appthere.drafts.core.parse.markdown

import com.appthere.drafts.core.model.SourceSpan

/**
 * A `[^label]:` definition found in the source.
 *
 * @param markerEnd offset of the first character after `[^label]:` and its trailing space, which is
 *   where the footnote's own content begins.
 */
internal data class FootnoteDefinitionMarker(
    val label: String,
    val start: Int,
    val markerEnd: Int,
)

/** A `[^label]` reference found in the source. */
internal data class FootnoteReference(
    val label: String,
    val span: SourceSpan,
)

/**
 * Finds footnote definitions and references (`markdown-dialect.md` 4).
 *
 * `intellij-markdown` has no footnote support, and this dialect needs one. Rather than extend the
 * parser, footnotes are recovered from the tree afterwards -- which works because the parser
 * already produces something sensible and, crucially, correctly positioned: a definition line
 * becomes an ordinary paragraph whose span begins exactly at the definition, so nothing has to be
 * re-parsed and every offset stays absolute.
 *
 * What the parser makes of a *reference* is inconsistent -- sometimes a shortcut reference link,
 * sometimes a bare link label swallowed into the surrounding text -- so references are found by
 * scanning the source instead, and whichever IR nodes cover them are replaced.
 */
internal object FootnoteScanner {
    /** Definition markers, in document order. */
    fun definitions(source: String): List<FootnoteDefinitionMarker> =
        definitionLine
            .findAll(source)
            .map { match ->
                FootnoteDefinitionMarker(
                    label = normaliseLinkLabel(match.groupValues[LABEL_GROUP]),
                    start = match.range.first,
                    markerEnd = match.range.last + 1,
                )
            }.toList()

    /**
     * References, excluding the `[^label]` that opens a definition.
     *
     * Without that exclusion every definition would register as a reference to itself, and a
     * footnote would appear in the output the moment it was defined.
     */
    fun references(
        source: String,
        definitions: List<FootnoteDefinitionMarker>,
    ): List<FootnoteReference> {
        val definitionStarts = definitions.map { it.start }.toSet()

        return reference
            .findAll(source)
            .filterNot { match -> definitionStarts.any { it == match.range.first || it == match.range.first - 1 } }
            .map { match ->
                FootnoteReference(
                    label = normaliseLinkLabel(match.groupValues[LABEL_GROUP]),
                    span = SourceSpan.of(match.range.first, match.range.last + 1),
                )
            }.toList()
    }

    private const val LABEL_GROUP = 1
}

/**
 * `[^label]:` at the start of a line, plus the whitespace after it.
 *
 * Up to three leading spaces, matching CommonMark's tolerance elsewhere; four would be an indented
 * code block. The match covers only the marker, so the body begins where the match ends.
 */
private val definitionLine = Regex("""(?m)^ {0,3}\[\^([^\]\s]+)\]:[ \t]*""")

/** `[^label]`, the reference form. Labels may not contain whitespace or a closing bracket. */
private val reference = Regex("""\[\^([^\]\s]+)\]""")
