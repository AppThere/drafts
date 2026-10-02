package com.appthere.drafts.platform.intents

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Whether a handover is a text document at all (`appthere-drafts.md` 9.2).
 *
 * The filters this application declares have to include `application/octet-stream`, or a mail
 * client's attachment never reaches it -- the URI carries no extension and the type is a shrug. The
 * cost is that every unknown binary on the device can be pointed at Drafts, and opening a JPEG as
 * Markdown is a screenful of replacement characters with nothing to say why.
 */
class LooksLikeTextTest {
    @Test
    fun `prose is text`() {
        assertTrue(DocumentKind.looksLikeText("# Chapter three\n\nThe salt road ran north.\n".encodeToByteArray()))
    }

    @Test
    fun `an empty handover is text`() {
        // There is nothing in it to be anything else, and 7.4's untitled document is empty too.
        assertTrue(DocumentKind.looksLikeText(ByteArray(0)))
    }

    @Test
    fun `text in scripts beyond ASCII is text`() {
        // The check must not be an ASCII check. 5.1 lists the scripts this application renders.
        val mixed = "Καλημέρα. こんにちは。مرحبا. Привет.\n"

        assertTrue(DocumentKind.looksLikeText(mixed.encodeToByteArray()))
    }

    @Test
    fun `a PNG is not text`() {
        // Its signature carries a NUL in the first sixteen bytes, as most binary formats do.
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D)

        assertFalse(DocumentKind.looksLikeText(png))
    }

    @Test
    fun `arbitrary bytes with no NUL are not text`() {
        // The second signal. A run of high bytes is not valid UTF-8 and decodes to replacements.
        val noise = ByteArray(64) { (0x80 + (it % 0x40)).toByte() }

        assertFalse(DocumentKind.looksLikeText(noise))
    }

    @Test
    fun `a character cut in half at the end of the read is forgiven`() {
        // The opening is the first few kilobytes, so the last character may be incomplete. A file
        // refused for that would be a document the reader could never open.
        val whole = "Chapter three. 𝄞".encodeToByteArray()

        (1..3).forEach { missing ->
            assertTrue(
                DocumentKind.looksLikeText(whole.copyOf(whole.size - missing)),
                "A document cut $missing bytes short was refused",
            )
        }
    }

    @Test
    fun `a screenplay is text and still reads as a screenplay`() {
        // The two checks are separate questions and both have to hold for 9.2's sniffing to work.
        val scene = "Title: The Salt Road\n\nINT. KITCHEN - NIGHT\n"

        assertTrue(DocumentKind.looksLikeText(scene.encodeToByteArray()))
        assertEquals(DocumentKind.Fountain, DocumentKind.sniff(scene))
    }
}
