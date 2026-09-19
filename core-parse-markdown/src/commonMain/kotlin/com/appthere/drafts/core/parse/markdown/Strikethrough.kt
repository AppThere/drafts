package com.appthere.drafts.core.parse.markdown

import com.appthere.drafts.core.model.SourceSpan

/** A `~text~` or `~~text~~` run found in the source. */
internal data class StrikethroughRun(
    val span: SourceSpan,
    val contentStart: Int,
    val contentEnd: Int,
    val tildeCount: Int,
)

/**
 * Finds single-tilde strikethrough, which `intellij-markdown` does not.
 *
 * `gfm.md` 3: "One or two tildes: `~text~` or `~~text~~`. Follows the same delimiter-run flanking
 * logic as emphasis. Renders `<del>`. Three or more tildes do not create strikethrough."
 * `markdown-dialect.md` 2 says the same, and Hugo -- via Goldmark's GitHub-flavoured
 * strikethrough -- behaves that way too.
 *
 * The library only produces a STRIKETHROUGH node for a double-tilde run, and inconsistently: a
 * single-tilde run is recognised *only* after a double-tilde run has already appeared in the same
 * paragraph. So single-tilde runs are found here and the double-tilde ones are left to the library,
 * which handles them correctly.
 *
 * Flanking is approximated rather than implemented in full. The opening tilde must be followed by
 * a non-space and the closing one preceded by a non-space, which is the rule that matters in
 * practice and the one that stops `~ not struck ~` from being a run. The full left- and
 * right-flanking algorithm additionally consults the Unicode class of the characters either side,
 * and the conformance corpus is what will say whether that distinction ever arises here.
 */
internal object StrikethroughScanner {
    fun find(source: String): List<StrikethroughRun> =
        singleTilde
            .findAll(source)
            .filterNot { it.isPartOfLongerRun(source) }
            .map { match ->
                StrikethroughRun(
                    span = SourceSpan.of(match.range.first, match.range.last + 1),
                    contentStart = match.range.first + 1,
                    contentEnd = match.range.last,
                    tildeCount = 1,
                )
            }.toList()

    /**
     * True when the match abuts another tilde.
     *
     * `~~x~~` is the library's job and `~~~x~~~` is not strikethrough at all, so a run touching a
     * tilde on either side is not ours to claim.
     */
    private fun MatchResult.isPartOfLongerRun(source: String): Boolean =
        source.getOrNull(range.first - 1) == '~' || source.getOrNull(range.last + 1) == '~'

    /** A tilde, content with no tilde or newline in it, and a closing tilde. */
    private val singleTilde = Regex("""~(?![\s~])[^~\n]*(?<![\s])~""")
}
