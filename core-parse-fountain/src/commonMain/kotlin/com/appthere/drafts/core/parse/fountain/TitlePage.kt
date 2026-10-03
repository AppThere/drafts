package com.appthere.drafts.core.parse.fountain

import com.appthere.drafts.core.model.DocMetadata

/**
 * `fountain.md`'s title page: "Optional. Must be the first thing in the file. Key-value pairs,
 * colon-separated, terminated by a blank line."
 *
 * It is detected by a **recognised** key rather than by anything shaped like `Word:`, which the
 * spec's looser "if the file begins with a `Key:` line" would allow. The reason is one line:
 * `FADE IN:` is how a great many screenplays open, and it is shaped exactly like a key. Reading it
 * as a title page would swallow the first scene. Unknown keys are still permitted *inside* a title
 * page, as the spec requires -- it is only the first line that has to be one this application
 * knows.
 */
internal object TitlePage {
    /** Fountain's four emphasis markers, which a title page value may carry. */
    private const val EMPHASIS_MARKERS = "*_"

    /** The keys `fountain.md` lists, lowercased for comparison. */
    private val RECOGNISED =
        setOf(
            "title",
            "credit",
            "author",
            "authors",
            "source",
            "notes",
            "draft date",
            "date",
            "contact",
            "copyright",
        )

    /** Whether [chunk] opens the document with a title page. */
    fun opens(chunk: Chunk): Boolean = keyOf(chunk.lines.first().text) in RECOGNISED

    /**
     * The metadata a title page carries, for the headers every export format wants.
     *
     * Only the two keys that have somewhere to go. The rest stay in the block, which is where the
     * whole title page lives and what a serialiser writes back untouched.
     */
    fun metadataOf(chunk: Chunk): DocMetadata {
        val values = valuesIn(chunk)

        return DocMetadata(
            title = values["title"],
            author = values["author"] ?: values["authors"],
        )
    }

    /**
     * The key-value pairs, with values that may be inline or indented below.
     *
     * "Values may be inline after the colon, or indented on following lines (indentation is any
     * leading whitespace). Multi-line values preserve their line breaks." They are joined with a
     * space here, because what this is for is a document title in an export header and a header is
     * one line.
     */
    private fun valuesIn(chunk: Chunk): Map<String, String> {
        val values = mutableMapOf<String, MutableList<String>>()
        var key: String? = null

        chunk.lines.forEach { line ->
            // An indented line continues the value above it; anything else that names a key starts
            // a new one. "Indentation is any leading whitespace."
            val starts = if (line.text.first().isWhitespace()) null else keyOf(line.text)
            key = starts ?: key

            key?.let { values.getOrPut(it) { mutableListOf() } += valueOn(line.text, starts != null) }
        }

        return values
            .mapValues { (_, parts) -> withoutEmphasis(parts.filter { it.isNotEmpty() }.joinToString(" ")) }
            .filterValues { it.isNotEmpty() }
    }

    /** What a line contributes: the text after its colon if it names a key, or the whole line. */
    private fun valueOn(
        line: String,
        named: Boolean,
    ): String = if (named) line.substringAfter(':').trim() else line.trim()

    /**
     * [value] with Fountain's emphasis markers removed.
     *
     * "Title page values may contain emphasis markup", and the spec's own example is
     * `_**THE LAST BIRTHDAY CARD**_`. What this produces is metadata -- a title for an EPUB or a
     * DOCX header -- and those carry no markup, so the markers come out. The block keeps them: it
     * holds the source, and the source is what a serialiser writes back.
     *
     * A title containing a real underscore loses it. That is the same trade every Fountain tool
     * makes, and the alternative is an inline parser for a string that is going into a file header.
     */
    private fun withoutEmphasis(value: String): String = value.filterNot { it in EMPHASIS_MARKERS }

    /** The key a line declares, lowercased, or null if it declares none. */
    private fun keyOf(line: String): String? {
        val colon = line.indexOf(':').takeIf { it > 0 } ?: return null
        val key = line.substring(0, colon)

        return key.takeIf { it.all { char -> char.isLetter() || char == ' ' } }?.trim()?.lowercase()
    }
}
