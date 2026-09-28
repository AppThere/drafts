package com.appthere.drafts.platform.intents

/**
 * What a launch asks the application for: a file to open (9.4), or a new document (7.4).
 *
 * From the command line, from the launcher's own entry points -- 7.4's "New Markdown document" and
 * "New Fountain screenplay" -- and handed from a second launch to the running instance. Kept as one
 * type because every one of those routes has to carry both.
 */
sealed interface LaunchRequest {
    /** A file, double-clicked or named on the command line. */
    data class Open(
        val path: String,
    ) : LaunchRequest

    /**
     * A new untitled document. [kind] is null when the launch did not say which -- a second launch
     * of the application from its icon -- and the document is then "the kind the reader last
     * created".
     */
    data class New(
        val kind: DocumentKind? = null,
    ) : LaunchRequest

    /** How this request travels to a running instance: one line of text. */
    fun encoded(): String =
        when (this) {
            is Open -> path
            is New -> NEW_PREFIX + (kind?.id ?: "")
        }

    companion object {
        /** The flag the launcher's entry points pass: `--new markdown`, `--new fountain`. */
        const val NEW_FLAG = "--new"

        /**
         * Starts a new-document line. A path cannot contain NUL on any platform, so no file name can
         * be mistaken for one -- and a path is sent exactly as it always was.
         */
        private const val NEW_PREFIX = "\u0000new "

        /**
         * What [args] ask for, or null if nothing. A first launch with nothing to ask for restores
         * the reader's sessions (7.3) and needs no request.
         */
        fun of(args: List<String>): LaunchRequest? =
            when {
                args.isEmpty() -> null
                args.first() == NEW_FLAG -> New(args.getOrNull(1)?.let(::kindNamed))
                else -> Open(args.first())
            }

        /** A line from [encoded], or null for one that is blank. */
        fun decoded(line: String): LaunchRequest? =
            when {
                line.isBlank() -> null
                line.startsWith(NEW_PREFIX) -> New(kindNamed(line.removePrefix(NEW_PREFIX)))
                else -> Open(line)
            }

        private fun kindNamed(id: String): DocumentKind? = DocumentKind.entries.firstOrNull { it.id == id.trim() }
    }
}
