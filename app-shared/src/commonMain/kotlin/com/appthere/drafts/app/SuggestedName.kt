package com.appthere.drafts.app

import com.appthere.drafts.core.model.Heading
import com.appthere.drafts.core.model.plainText
import com.appthere.drafts.editor.ui.EditorState
import com.appthere.drafts.platform.intents.DocumentKind

/**
 * The file name 7.4's *Save As* offers.
 *
 * "The suggested name comes from the first heading, or from a Fountain title page's `Title:`, and
 * the extension from the kind (9.1)." [fallback] is what a document with neither gets -- the name
 * it has had all along.
 *
 * Only a suggestion: the reader can type anything in the dialog. So it is made safe to accept as it
 * stands on all three desktops rather than faithful to every character of the title.
 */
fun EditorState.suggestedFileName(
    kind: DocumentKind,
    fallback: String,
): String {
    val title =
        when (kind) {
            DocumentKind.Fountain -> fountainTitle(text) ?: firstHeading()
            DocumentKind.Markdown -> firstHeading()
        }
    val base = title?.let(::fileNameSafe)?.takeIf { it.isNotEmpty() } ?: fallback

    return "$base.${kind.extensions.first()}"
}

/**
 * [name] marked as the reader's own version, for 8.2's "Save a copy...": `chapter.md` becomes
 * `chapter (my version).md`.
 *
 * The copy has to be called something else. Offered the conflicted file's own name, the save dialog
 * would open on the one version the reader chose to keep, one Return away from replacing it.
 */
fun copyName(
    name: String,
    label: String,
): String {
    val dot = name.lastIndexOf('.').takeIf { it > 0 } ?: name.length
    return "${name.substring(0, dot)} ($label)${name.substring(dot)}"
}

private fun EditorState.firstHeading(): String? =
    blocks
        .asSequence()
        .map { it.block }
        .filterIsInstance<Heading>()
        .firstOrNull()
        ?.inlines
        ?.plainText()

/**
 * The `Title:` of a Fountain title page, which is the run of `Key: value` lines the document opens
 * with, ending at the first blank line. A value may start on the key's line or continue on indented
 * lines below it -- the usual shape for a title set over two lines. Fountain's emphasis markers are
 * dropped: `_**THE SALT ROAD**_` is titled THE SALT ROAD.
 */
private fun fountainTitle(text: String): String? {
    val page = text.lineSequence().takeWhile { it.isNotBlank() }.toList()
    val at = page.indexOfFirst { titleKey.matches(it) }
    if (at < 0) return null

    val inline =
        titleKey
            .matchEntire(page[at])
            ?.groupValues
            ?.get(1)
            .orEmpty()
    val continued = page.drop(at + 1).takeWhile { it.startsWith("   ") || it.startsWith("\t") }
    val title = (listOf(inline) + continued).joinToString(" ") { it.trim() }

    return title.replace(fountainEmphasis, "").trim().takeIf { it.isNotEmpty() }
}

/**
 * [title] as something every desktop will accept as a file name.
 *
 * The characters Windows forbids (a superset of the other two), control characters, runs of
 * whitespace, and the trailing dots and spaces Windows strips silently. Long titles are cut at a
 * length that leaves room in a path, without splitting a character in two. Windows' reserved device
 * names are refused outright rather than mangled.
 */
private fun fileNameSafe(title: String): String {
    val cleaned =
        title
            .filterNot { it in FORBIDDEN || it.isISOControl() }
            .replace(whitespace, " ")
            .trim()
            .trimEnd('.', ' ')
    val cut = if (cleaned.length > MAX_LENGTH) cleaned.take(MAX_LENGTH).trimEnd('.', ' ') else cleaned
    val whole = if (cut.lastOrNull()?.isHighSurrogate() == true) cut.dropLast(1) else cut

    return if (whole.uppercase() in reserved) "" else whole
}

private val titleKey = Regex("""^[Tt][Ii][Tt][Ll][Ee]:\s*(.*)$""")
private val fountainEmphasis = Regex("""[*_]""")
private val whitespace = Regex("""\s+""")
private const val FORBIDDEN = "/\\:*?\"<>|"
private const val MAX_LENGTH = 80
private val reserved =
    setOf("CON", "PRN", "AUX", "NUL") + (1..9).flatMap { listOf("COM$it", "LPT$it") }
