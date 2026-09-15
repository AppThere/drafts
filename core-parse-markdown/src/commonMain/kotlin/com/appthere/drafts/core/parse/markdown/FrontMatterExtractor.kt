package com.appthere.drafts.core.parse.markdown

import com.appthere.drafts.core.model.FrontMatter
import com.appthere.drafts.core.model.FrontMatterFormat
import com.appthere.drafts.core.model.SourceSpan

/**
 * Finds the front matter at the head of a document, if there is any.
 *
 * `markdown-dialect.md` puts this outside the Markdown parser for a concrete reason: "the YAML
 * front matter delimiter collides with CommonMark's thematic break. Stripping must happen first or
 * `---` parses as `<hr>`." A document opening with `---` would otherwise lose its metadata to a
 * horizontal rule, and the author would find out when their site build broke.
 *
 * Three formats, never normalised between them -- TOML stays TOML. The content is kept exactly as
 * written, key order and whitespace included, and the only thing anyone may do with it is put it
 * back.
 *
 * The region is handed back as a span rather than removed, so the rest of the document keeps its
 * absolute offsets. [MarkdownDocumentParser] masks it before parsing; masking rather than stripping
 * is what lets every block span still index into the original string.
 */
internal object FrontMatterExtractor {
    /**
     * Front matter at offset 0, or null.
     *
     * Only at offset 0: a `+++` further down the file is a thematic break, and treating it as
     * metadata would silently delete a chunk of someone's prose.
     */
    fun extract(source: String): FrontMatter? =
        when {
            source.startsWith(FrontMatterFormat.TOML.openingDelimiter) -> {
                fenced(source, FrontMatterFormat.TOML)
            }

            source.startsWith(FrontMatterFormat.YAML.openingDelimiter) -> {
                fenced(source, FrontMatterFormat.YAML)
            }

            looksLikeJsonObject(source) -> {
                json(source)
            }

            else -> {
                null
            }
        }

    /**
     * TOML and YAML: a delimiter line, content, and a matching closing delimiter line.
     *
     * The opening delimiter must be alone on its line. `---foo` is not front matter, and neither is
     * a Setext underline that happens to be the first line of the file.
     */
    private fun fenced(
        source: String,
        format: FrontMatterFormat,
    ): FrontMatter? {
        val delimiter = format.openingDelimiter
        val firstLineEnd = source.indexOf('\n')

        // One guard covering both ways the opening line can disqualify the block: there is no line
        // at all, or the delimiter is not alone on it. `---foo` is not front matter.
        if (firstLineEnd < 0 || source.substring(delimiter.length, firstLineEnd).isNotBlank()) {
            return null
        }

        return findClosingDelimiter(source, delimiter, firstLineEnd + 1)?.let { closing ->
            FrontMatter(
                format = format,
                text = source.substring(firstLineEnd + 1, closing.contentEnd),
                source = SourceSpan.of(0, closing.regionEnd),
            )
        }
    }

    private fun findClosingDelimiter(
        source: String,
        delimiter: String,
        from: Int,
    ): Region? {
        var lineStart = from

        while (lineStart < source.length) {
            val lineEnd = source.lineEndFrom(lineStart)
            if (source.substring(lineStart, lineEnd).trimEnd() == delimiter) {
                // The region covers the closing delimiter and the newline after it, so the body
                // begins on a fresh line. A file ending at the delimiter has no newline to include.
                return Region(contentEnd = lineStart, regionEnd = minOf(lineEnd + 1, source.length))
            }
            lineStart = lineEnd + 1
        }
        return null
    }

    /** The end of the line starting at [from]: the next newline, or the end of the string. */
    private fun String.lineEndFrom(from: Int): Int = indexOf('\n', from).takeIf { it >= 0 } ?: length

    /**
     * True when the document opens with something that is actually a JSON object.
     *
     * A leading `{` is not enough, and assuming it was is a bug this cost real debugging to find:
     * a Hugo shortcode at the top of a file -- `{{< figure src="a.png" >}}` -- also starts with a
     * brace, and its braces balance. It was being detected as JSON front matter, masked out, and
     * the whole document came back empty.
     *
     * So the first non-whitespace character after the brace has to be a `"` (the first key) or a
     * closing `}` (an empty object). A shortcode's second character is another `{`, which is not
     * valid at the start of a JSON object.
     */
    private fun looksLikeJsonObject(source: String): Boolean {
        if (!source.startsWith(FrontMatterFormat.JSON.openingDelimiter)) return false

        val next = source.drop(1).firstOrNull { !it.isWhitespace() }
        return next == '"' || next == '}'
    }

    /**
     * JSON front matter, where the braces are the delimiters and part of the content.
     *
     * Brace counting has to skip strings, or a `}` inside a value ends the block early. Escapes
     * have to be honoured inside those strings for the same reason.
     */
    private fun json(source: String): FrontMatter? {
        var depth = 0
        var inString = false
        var escaped = false

        source.forEachIndexed { index, char ->
            when {
                escaped -> {
                    escaped = false
                }

                char == '\\' && inString -> {
                    escaped = true
                }

                char == '"' -> {
                    inString = !inString
                }

                inString -> {
                    Unit
                }

                char == '{' -> {
                    depth++
                }

                char == '}' -> {
                    depth--
                    if (depth == 0) return jsonAt(source, index)
                }
            }
        }
        return null
    }

    private fun jsonAt(
        source: String,
        closingBrace: Int,
    ): FrontMatter {
        val afterBrace = closingBrace + 1
        val regionEnd = if (afterBrace < source.length && source[afterBrace] == '\n') afterBrace + 1 else afterBrace

        return FrontMatter(
            format = FrontMatterFormat.JSON,
            text = source.substring(0, afterBrace),
            source = SourceSpan.of(0, regionEnd),
        )
    }

    /** Where the content ends and where the whole region ends, which differ by the delimiter line. */
    private data class Region(
        val contentEnd: Int,
        val regionEnd: Int,
    )
}
