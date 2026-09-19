package com.appthere.drafts.core.parse.markdown

import com.appthere.drafts.core.model.SourceSpan

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
    /**
     * References, excluding the `[^label]` that opens a definition.
     *
     * Without that exclusion every definition would register as a reference to itself, and a
     * footnote would appear in the output the moment it was defined.
     */
    fun references(
        source: String,
        definitionStarts: List<Int>,
    ): List<FootnoteReference> {
        val starts = definitionStarts.toSet()

        return reference
            .findAll(source)
            .filterNot { match -> starts.any { it == match.range.first || it == match.range.first - 1 } }
            .map { match ->
                FootnoteReference(
                    label = normaliseLinkLabel(match.groupValues[LABEL_GROUP]),
                    span = SourceSpan.of(match.range.first, match.range.last + 1),
                )
            }.toList()
    }

    private const val LABEL_GROUP = 1
}

/** `[^label]`, the reference form. Labels may not contain whitespace or a closing bracket. */
private val reference = Regex("""\[\^([^\]\s]+)\]""")
