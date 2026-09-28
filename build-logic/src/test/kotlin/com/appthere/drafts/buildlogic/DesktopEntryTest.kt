package com.appthere.drafts.buildlogic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * `appthere-drafts.md` 9.4, Linux: "`%f` in `Exec` delivers the path."
 *
 * The entry below is what jpackage actually wrote, copied from a built package, so these tests
 * are about the file that ships rather than one imagined for them.
 */
class DesktopEntryTest {
    @Test
    fun `the launcher is given the path it was opened with`() {
        val exec = execOf(DesktopEntry.fixed(FROM_JPACKAGE, OPENS))

        assertEquals("Exec=/opt/appthere-drafts/bin/Drafts %f", exec)
    }

    @Test
    fun `every type the application opens is named once`() {
        val fixed = DesktopEntry.fixed(FROM_JPACKAGE, OPENS)

        val mimeLines = fixed.lines().filter { it.startsWith("MimeType=") }
        assertEquals(listOf("MimeType=text/markdown;text/x-markdown;text/x-fountain;"), mimeLines)
    }

    @Test
    fun `an entry that already takes a path is not given a second one`() {
        // Were jpackage ever to add a field code of its own, appending another would pass the
        // document twice -- and the second copy would open as a second window.
        val alreadyTaking = FROM_JPACKAGE.replace("bin/Drafts", "bin/Drafts %F")

        assertEquals("Exec=/opt/appthere-drafts/bin/Drafts %F", execOf(DesktopEntry.fixed(alreadyTaking, OPENS)))
    }

    @Test
    fun `everything else jpackage wrote is kept`() {
        val fixed = DesktopEntry.fixed(FROM_JPACKAGE, OPENS)

        listOf("Name=Drafts", "Icon=/opt/appthere-drafts/lib/Drafts.png", "Categories=Office", "Type=Application")
            .forEach { assertTrue(it in fixed.lines(), "$it went missing") }
    }

    @Test
    fun `an entry with no Exec line fails the build rather than shipping`() {
        assertFailsWith<IllegalStateException> {
            DesktopEntry.fixed(FROM_JPACKAGE.lines().filterNot { it.startsWith("Exec=") }.joinToString("\n"), OPENS)
        }
    }

    private fun execOf(entry: String) = entry.lines().single { it.startsWith("Exec=") }

    private companion object {
        val OPENS = listOf("text/markdown", "text/x-markdown", "text/x-fountain")

        val FROM_JPACKAGE =
            """
            [Desktop Entry]
            Name=Drafts
            Comment=Drafts
            Exec=/opt/appthere-drafts/bin/Drafts
            Icon=/opt/appthere-drafts/lib/Drafts.png
            Terminal=false
            Type=Application
            Categories=Office
            MimeType=text/x-fountain;text/x-fountain
            """.trimIndent()
    }
}
