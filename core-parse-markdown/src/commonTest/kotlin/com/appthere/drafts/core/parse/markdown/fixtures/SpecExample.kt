package com.appthere.drafts.core.parse.markdown.fixtures

/**
 * One example from the CommonMark specification: the input, and the HTML the spec says it
 * must produce.
 */
internal data class SpecExample(
    val number: Int,
    val section: String,
    val markdown: String,
    val html: String,
)

/** All 652 examples from CommonMark 0.31.2, in specification order. */
internal val commonMarkExamples: List<SpecExample> =
    commonmarkexamples1 +
        commonmarkexamples2 +
        commonmarkexamples3 +
        commonmarkexamples4 +
        commonmarkexamples5
