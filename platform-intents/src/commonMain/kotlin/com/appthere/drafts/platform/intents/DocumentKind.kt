package com.appthere.drafts.platform.intents

/**
 * What kind of document a name says it is, per `appthere-drafts.md` 9.1.
 *
 * | | Markdown | Fountain |
 * |---|---|---|
 * | Extensions | `.md`, `.markdown`, `.mdown`, `.mkd` | `.fountain`, `.spmd` |
 * | MIME | `text/markdown` (RFC 7763) | none registered |
 * | UTI | `net.daringfireball.markdown` | must be declared -- export `io.fountain.fountain` |
 *
 * The table is the whole of it, and the reason it is a table rather than two `endsWith` calls is
 * that neither format has one extension. A `.spmd` screenplay opened as Markdown parses as
 * prose -- every scene heading becomes a paragraph -- and nothing about the result says why.
 */
enum class DocumentKind(
    val id: String,
    val extensions: List<String>,
    val mimeTypes: List<String>,
) {
    Markdown(
        id = "markdown",
        extensions = listOf("md", "markdown", "mdown", "mkd"),
        // `text/x-markdown` is not registered but is what several editors and mail clients send.
        mimeTypes = listOf("text/markdown", "text/x-markdown"),
    ),

    /**
     * Fountain has no registered MIME type: 9.1 says so, and 9.3 says the UTI has to be declared
     * as an exported one. Anything handing a `.fountain` file over will call it `text/plain` at
     * best, so the extension is the only reliable signal.
     */
    Fountain(
        id = "fountain",
        extensions = listOf("fountain", "spmd"),
        mimeTypes = emptyList(),
    ),
    ;

    companion object {
        /**
         * The kind a file name implies, or null if it implies none.
         *
         * Null rather than a default. 9.2 warns that "`.*\\.md` will also match `notes.mdx`", and
         * the way that trap is usually sprung is a function that returns Markdown for everything
         * it does not recognise. A caller that wants a default can say so; one that wanted to know
         * gets to find out.
         *
         * The comparison is on the last extension only and is case-insensitive, because
         * `CHAPTER.MD` off a Windows share is the same document as `chapter.md`.
         */
        fun of(name: String): DocumentKind? {
            val extension = name.substringAfterLast('.', "").lowercase()

            return entries.firstOrNull { extension in it.extensions }
        }

        /**
         * The kind a MIME type implies, or null.
         *
         * 9.2's first trap: "Downloads and messaging apps frequently hand over
         * `application/octet-stream` regardless of the real type." So a MIME type is a hint that
         * can confirm a kind and should never be trusted to deny one -- which is why the caller is
         * expected to try [of] first and fall back to this.
         */
        fun ofMimeType(mimeType: String): DocumentKind? =
            entries.firstOrNull { mimeType.substringBefore(';').trim().lowercase() in it.mimeTypes }
    }
}
