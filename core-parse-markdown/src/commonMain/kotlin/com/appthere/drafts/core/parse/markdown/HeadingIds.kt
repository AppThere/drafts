package com.appthere.drafts.core.parse.markdown

/**
 * Generates heading ids using GitHub's algorithm (`markdown-dialect.md` 7).
 *
 * "Lowercase, strip punctuation except hyphens, spaces to hyphens, Unicode preserved, duplicates
 * suffixed `-1`, `-2`. An explicit `{#id}` overrides the generated one."
 *
 * Unicode being preserved is the part worth stating: a heading written in Japanese or Arabic gets
 * an id in that script rather than an empty string or a transliteration. That is GitHub's behaviour
 * and it is the right one for a tool whose users write in their own languages.
 */
internal class HeadingIds {
    private val used = mutableMapOf<String, Int>()

    /**
     * The id for [text], unique within this document.
     *
     * Stateful by necessity -- uniqueness is a property of the document, not of the heading -- so
     * one instance serves one document and is created per parse.
     */
    fun generate(text: String): String {
        val slug = slugify(text)
        val seen = used[slug] ?: 0
        used[slug] = seen + 1

        return if (seen == 0) slug else "$slug-$seen"
    }

    /** Records an explicit id so a later generated one cannot collide with it. */
    fun reserve(id: String) {
        used[id] = (used[id] ?: 0) + 1
    }

    private fun slugify(text: String): String =
        text
            .lowercase()
            .map { char ->
                when {
                    char.isLetterOrDigit() -> char
                    char == '-' || char == '_' -> char
                    char.isWhitespace() -> '-'
                    else -> SKIP
                }
            }.filter { it != SKIP }
            .joinToString("")

    private companion object {
        /**
         * Marks a character to drop.
         *
         * NUL rather than a space: whitespace is mapped to a hyphen in the branch above, so a
         * space sentinel would make the two indistinguishable and leave the result depending
         * on the order of those branches.
         */
        const val SKIP = '\u0000'
    }
}
