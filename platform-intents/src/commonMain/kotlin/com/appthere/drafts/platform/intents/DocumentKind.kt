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

        /**
         * Whether a handover is a text document at all.
         *
         * 9.2 requires this application to accept `application/octet-stream`, because "Downloads and
         * messaging apps frequently hand over `application/octet-stream` regardless of the real
         * type" -- and a mail client's attachment URI carries no extension to narrow it with. The
         * price of that filter is that every unknown binary on the device can now be pointed at
         * Drafts, so something has to say no.
         *
         * Two signals, in order of certainty. A NUL byte settles it: no UTF-8 text document
         * contains one, and almost every binary format does. Otherwise the opening is decoded and
         * the replacement characters counted -- a file of arbitrary bytes produces them in
         * quantity, and a text file produces none.
         *
         * [opening] is the first few kilobytes, not the whole file, so the last character may be cut
         * in half. A four-byte sequence split across the end decodes to at most three replacements,
         * which is why that many are forgiven and a fourth is not.
         *
         * An empty file is text. There is nothing in it to be anything else, and 7.4's untitled
         * document is empty too.
         *
         * UTF-16 is refused, because it is full of NUL bytes. That is a real limitation rather than
         * an oversight: nothing else in this application reads UTF-16 either, so accepting the
         * handover would only move the failure later, to a document of mojibake.
         */
        fun looksLikeText(opening: ByteArray): Boolean =
            when {
                opening.isEmpty() -> true
                opening.any { it == NUL } -> false
                else -> opening.decodeToString().count { it == REPLACEMENT } <= SPLIT_CHARACTER
            }

        /**
         * What the *contents* look like, for handovers that say nothing useful.
         *
         * 9.2: "Downloads and messaging apps frequently hand over `application/octet-stream`
         * regardless of the real type. Include a permissive filter matched on extension, and sniff
         * content on open."
         *
         * Only ever consulted when the name and the MIME type have both failed, and only ever
         * returns an answer it is confident of. Fountain is the one worth detecting: a screenplay
         * opened as Markdown loses every scene heading into a paragraph, while prose opened as a
         * screenplay is merely odd. So Fountain needs real evidence and everything else that looks
         * like text is Markdown, which is this application's primary format.
         *
         * Reads the opening of the document rather than all of it. A scene heading or a title page
         * is at the top of a screenplay by definition, and a novel is not worth walking to decide
         * something the first page already answers.
         */
        fun sniff(text: String): DocumentKind? {
            val opening =
                text
                    .lineSequence()
                    .take(LINES_SNIFFED)
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }

            return when {
                opening.none() -> null
                opening.any { it.looksLikeScreenplay() } -> Fountain
                else -> Markdown
            }
        }

        /**
         * A line only a screenplay has.
         *
         * A scene heading, which Fountain writes as `INT.`/`EXT.` or forces with a leading full
         * stop; or a title-page key, which is the other thing at the top of a `.fountain` file.
         *
         * Deliberately narrow. A line of Markdown prose can start with almost anything, so a loose
         * rule here would send novels to the screenplay parser -- and the cost of guessing wrong in
         * that direction is a document whose every paragraph is a character cue.
         */
        private fun String.looksLikeScreenplay(): Boolean =
            SCENE_PREFIXES.any { startsWith(it, ignoreCase = true) } ||
                (startsWith(".") && length > 1 && !startsWith("..")) ||
                TITLE_KEYS.any { startsWith(it, ignoreCase = true) }

        /** Fountain's scene headings, which are the strongest signal a screenplay gives. */
        private val SCENE_PREFIXES = listOf("INT.", "EXT.", "INT/EXT", "EXT/INT", "I/E.")

        /** Title-page keys, which Fountain puts above everything else. */
        private val TITLE_KEYS = listOf("Title:", "Credit:", "Author:", "Draft date:", "Contact:")

        /** Far enough in to pass a title page and reach the first scene. */
        private const val LINES_SNIFFED = 40

        private const val NUL: Byte = 0

        private const val REPLACEMENT = '\uFFFD'

        /** The most replacement characters one character cut in half at the end of a read can make. */
        private const val SPLIT_CHARACTER = 3
    }
}
