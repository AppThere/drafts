package com.appthere.drafts.app

import com.appthere.drafts.editor.engine.DocumentSession
import com.appthere.drafts.editor.ui.EditorState
import com.appthere.drafts.platform.intents.DocumentKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 7.4: "The suggested name comes from the first heading, or from a Fountain title page's `Title:`." */
class SuggestedNameTest {
    @Test
    fun `a markdown document is named for its first heading`() {
        assertEquals("The Salt Road.md", name("Some opening words.\n\n# The Salt Road\n\n## Part one\n"))
    }

    @Test
    fun `the heading is named as a reader reads it`() {
        // Markup is not part of the title.
        assertEquals("Using foo and bar.md", name("## Using `foo` and *bar*\n"))
    }

    @Test
    fun `a document with no heading keeps the name it has`() {
        assertEquals("Untitled.md", name("Just a paragraph.\n"))
    }

    @Test
    fun `a screenplay is named from its title page`() {
        val script = "Title: _**The Salt Road**_\nCredit: Written by\nAuthor: Marla\n\nINT. DOCK - NIGHT\n"

        assertEquals("The Salt Road.fountain", name(script, DocumentKind.Fountain))
    }

    @Test
    fun `a title set over two lines is read as one`() {
        val script = "Title:\n    THE SALT\n    ROAD\nAuthor: Marla\n\nINT. DOCK - NIGHT\n"

        assertEquals("THE SALT ROAD.fountain", name(script, DocumentKind.Fountain))
    }

    @Test
    fun `a title key after the title page is not a title`() {
        // The title page ends at the first blank line. A character saying "Title: pending" in a
        // scene is dialogue.
        assertEquals("Untitled.fountain", name("INT. DOCK - NIGHT\n\nTitle: pending\n", DocumentKind.Fountain))
    }

    @Test
    fun `characters no desktop allows in a file name are removed`() {
        assertEquals("Act I Why Now.md", name("# Act I: Why / Now?\n"))
    }

    @Test
    fun `a very long title is cut to a length a path has room for`() {
        val suggested = name("# ${"word ".repeat(40)}\n")

        assertTrue(suggested.removeSuffix(".md").length <= 80, "\"$suggested\" is too long")
        assertTrue(!suggested.contains(" .md"), "The cut left a trailing space before the extension")
    }

    @Test
    fun `a title windows reserves for a device falls back`() {
        assertEquals("Untitled.md", name("# CON\n"))
    }

    @Test
    fun `a copy is named as the reader's own version`() {
        // 8.2's "Save a copy...". Offered the conflicted file's own name, the dialog would open on
        // the version the reader chose to keep.
        assertEquals("chapter (my version).md", copyName("chapter.md", "my version"))
        assertEquals("The Salt Road (my version).fountain", copyName("The Salt Road.fountain", "my version"))
    }

    @Test
    fun `a name with no extension is still marked`() {
        assertEquals("NOTES (my version)", copyName("NOTES", "my version"))
    }

    private fun name(
        text: String,
        kind: DocumentKind = DocumentKind.Markdown,
    ) = EditorState(DocumentSession(text)).suggestedFileName(kind, fallback = "Untitled")
}
