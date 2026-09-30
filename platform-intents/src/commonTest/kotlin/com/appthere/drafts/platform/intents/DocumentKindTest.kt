package com.appthere.drafts.platform.intents

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 9.1's table, and the traps 9.2 names around it.
 *
 * Both formats have more than one extension and one of them has no MIME type at all, so this is
 * the difference between opening a screenplay as a screenplay and opening it as prose with every
 * scene heading flattened into a paragraph.
 */
class DocumentKindTest {
    @Test
    fun `every markdown extension in the table is recognised`() {
        // All four, because the ones nobody tests are the ones that fall out of a rewrite.
        listOf("chapter.md", "chapter.markdown", "chapter.mdown", "chapter.mkd").forEach {
            assertEquals(DocumentKind.Markdown, DocumentKind.of(it), "for $it")
        }
    }

    @Test
    fun `both fountain extensions are recognised`() {
        assertEquals(DocumentKind.Fountain, DocumentKind.of("big-fish.fountain"))
        assertEquals(DocumentKind.Fountain, DocumentKind.of("big-fish.spmd"))
    }

    @Test
    fun `spmd is a screenplay rather than markdown`() {
        // The one that a two-branch `endsWith(".fountain") else markdown` gets wrong, and gets
        // wrong quietly: the file opens, and every scene heading is a paragraph.
        assertEquals(DocumentKind.Fountain, DocumentKind.of("notes.spmd"))
    }

    @Test
    fun `mdx is not markdown`() {
        // 9.2 names this one: "`.*\\.md` will also match `notes.mdx` -- validate after receiving."
        assertNull(DocumentKind.of("notes.mdx"))
    }

    @Test
    fun `an unknown extension is unknown rather than markdown`() {
        // Null and not a default. A function that answers Markdown for everything it does not
        // recognise is how the `.mdx` trap gets sprung in the first place.
        assertNull(DocumentKind.of("photo.png"))
        assertNull(DocumentKind.of("README"))
    }

    @Test
    fun `the extension is matched case-insensitively`() {
        // `CHAPTER.MD` off a Windows share is the same document as `chapter.md`.
        assertEquals(DocumentKind.Markdown, DocumentKind.of("CHAPTER.MD"))
        assertEquals(DocumentKind.Fountain, DocumentKind.of("SCRIPT.Fountain"))
    }

    @Test
    fun `only the last extension counts`() {
        // A document named for what it is about, not a compound type.
        assertEquals(DocumentKind.Markdown, DocumentKind.of("notes.fountain.md"))
        assertEquals(DocumentKind.Fountain, DocumentKind.of("draft.md.fountain"))
    }

    @Test
    fun `a dotfile with no extension is unknown`() {
        assertNull(DocumentKind.of(".gitignore"))
    }

    @Test
    fun `the registered markdown mime type is recognised`() {
        assertEquals(DocumentKind.Markdown, DocumentKind.ofMimeType("text/markdown"))
        assertEquals(DocumentKind.Markdown, DocumentKind.ofMimeType("text/x-markdown"))
    }

    @Test
    fun `a mime type with parameters still matches`() {
        // RFC 7763 registers `text/markdown; variant=...`, and senders include it.
        assertEquals(DocumentKind.Markdown, DocumentKind.ofMimeType("text/markdown; charset=utf-8"))
    }

    @Test
    fun `fountain has no mime type to match`() {
        // 9.1: "Fountain has no registered MIME type and no public UTI". Claiming one would mean
        // matching a type nothing sends, and quietly not matching the extension that is sent.
        assertNull(DocumentKind.ofMimeType("text/fountain"))
        assertEquals(emptyList(), DocumentKind.Fountain.mimeTypes)
    }

    @Test
    fun `octet-stream matches nothing`() {
        // 9.2's first trap: "Downloads and messaging apps frequently hand over
        // `application/octet-stream` regardless of the real type." The extension has to decide.
        assertNull(DocumentKind.ofMimeType("application/octet-stream"))
        assertEquals(DocumentKind.Markdown, DocumentKind.of("handed-over.md"))
    }

    @Test
    fun `a scene heading gives a screenplay away`() {
        // 9.2's sniffing, for the octet-stream handover. A screenplay opened as Markdown loses
        // every scene heading into a paragraph, which is the mistake worth catching.
        assertEquals(DocumentKind.Fountain, DocumentKind.sniff("INT. KITCHEN - DAY\n\nShe waits.\n"))
        assertEquals(DocumentKind.Fountain, DocumentKind.sniff("EXT. STREET - NIGHT\n"))
    }

    @Test
    fun `a title page gives one away too`() {
        // The other thing at the top of a Fountain file, and the case where the first scene is
        // further down than a naive look at line one would reach.
        assertEquals(
            DocumentKind.Fountain,
            DocumentKind.sniff("Title: Big Fish\nCredit: written by\nAuthor: John August\n"),
        )
    }

    @Test
    fun `a forced scene heading counts`() {
        // Fountain lets a writer force one with a leading full stop when the line is not INT or EXT.
        assertEquals(DocumentKind.Fountain, DocumentKind.sniff(".THE VOID\n\nNothing.\n"))
    }

    @Test
    fun `prose is markdown`() {
        // Everything that is not evidently a screenplay. Markdown is this application's primary
        // format and the safer of the two to be wrong about.
        assertEquals(DocumentKind.Markdown, DocumentKind.sniff("# Chapter One\n\nIt was a bright day.\n"))
        assertEquals(DocumentKind.Markdown, DocumentKind.sniff("Just a sentence with no markup.\n"))
    }

    @Test
    fun `a sentence starting with an ellipsis is not a scene heading`() {
        // A forced scene heading is one full stop. Prose that trails off is three, and sending a
        // novel to the screenplay parser would make every paragraph a character cue.
        assertEquals(DocumentKind.Markdown, DocumentKind.sniff("...and then she left.\n"))
    }

    @Test
    fun `a paragraph about interiors is not a screenplay`() {
        // "Interesting" and "Internal" both start with INT. The full stop is the signal.
        assertEquals(DocumentKind.Markdown, DocumentKind.sniff("Interesting, she thought.\n"))
        assertEquals(DocumentKind.Markdown, DocumentKind.sniff("Internal affairs called again.\n"))
    }

    @Test
    fun `an empty handover tells us nothing`() {
        // Null rather than a guess: there is nothing here to be confident about, and the caller
        // has a default of its own.
        assertNull(DocumentKind.sniff(""))
        assertNull(DocumentKind.sniff("   \n\n  \n"))
    }

    @Test
    fun `a screenplay is found below its title page`() {
        // The scene that proves it can be a long way down. Reading only the first line would miss
        // every screenplay that has a title page, which is most of them.
        val document = (1..20).joinToString("\n") { "Notes line $it" } + "\n\nINT. OFFICE - DAY\n"

        assertEquals(DocumentKind.Fountain, DocumentKind.sniff(document))
    }

    @Test
    fun `only the opening is read`() {
        // A novel is not walked to decide something its first page already answers. A scene heading
        // a thousand lines into a Markdown document is somebody quoting a screenplay.
        val document = (1..500).joinToString("\n") { "Paragraph $it of ordinary prose." } + "\nINT. LATER - DAY\n"

        assertEquals(DocumentKind.Markdown, DocumentKind.sniff(document))
    }
}
