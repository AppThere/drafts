package com.appthere.drafts.core.parse.markdown

/**
 * Link label normalisation, per CommonMark 0.31.2 section "Matching link labels".
 *
 * Two labels match if they are the same after: Unicode case folding, collapsing every run of
 * whitespace to a single space, and trimming. So `[FOO BAR]` and `[foo   bar]` are the same label.
 *
 * `markdown-dialect.md` 4 says footnote labels are "normalised the same way link labels are",
 * which is why this lives on its own rather than inside the link lowering -- the footnote pass
 * uses it too.
 *
 * `lowercase()` is used rather than true Unicode case folding. They differ for a small number of
 * characters (Turkish dotless i, German sharp s, Cherokee), and matching CommonMark exactly there
 * would need a case-folding table this project does not otherwise need. The conformance run in
 * the harness task is what will say whether that matters in practice; noted here so the next
 * person finds the reason rather than the omission.
 */
internal fun normaliseLinkLabel(label: String): String =
    label
        .trim()
        .replace(whitespaceRun, " ")
        .lowercase()

// camelCase because it is a private top-level `val` rather than a `const`, which is the
// distinction detekt's TopLevelPropertyNaming draws (a Regex cannot be const).
private val whitespaceRun = Regex("""\s+""")
