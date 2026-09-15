package com.appthere.drafts.core.model

/**
 * A whole document.
 *
 * The unit the editor opens, the parsers produce, and every backend consumes.
 */
data class Document(
    val blocks: List<Block>,
    val footnotes: Map<String, List<Block>> = emptyMap(),
    val frontMatter: FrontMatter? = null,
    val metadata: DocMetadata = DocMetadata(),
) {
    companion object {
        val EMPTY = Document(blocks = emptyList())
    }
}

/**
 * Front matter, preserved exactly as written.
 *
 * `markdown-dialect.md` is emphatic that this is never normalised between formats: TOML stays
 * TOML, key order is preserved, whitespace is preserved. [text] is the raw content between the
 * delimiters, and the only thing anyone may do with it is put it back.
 *
 * The delimiters are worth a note. YAML's `---` collides with CommonMark's thematic break, so
 * front matter must be stripped before parsing or the first line of a YAML-fronted document
 * parses as a horizontal rule.
 */
data class FrontMatter(
    val format: FrontMatterFormat,
    val text: String,
    val source: SourceSpan? = null,
)

/** Which of the three front matter syntaxes was used. */
enum class FrontMatterFormat(
    val openingDelimiter: String,
    val closingDelimiter: String,
) {
    /** `+++` … `+++` */
    TOML("+++", "+++"),

    /** `---` … `---` */
    YAML("---", "---"),

    /** `{` … `}` -- delimiters are part of the JSON itself. */
    JSON("{", "}"),
}

/**
 * Document-level metadata, for the headers that EPUB, ODT and DOCX all require.
 *
 * Distinct from [FrontMatter]: that is bytes to preserve, this is values to use. They may well
 * describe the same title, and keeping them apart means extracting metadata never risks rewriting
 * the author's front matter.
 */
data class DocMetadata(
    val title: String? = null,
    val author: String? = null,
    val language: String? = null,
)
