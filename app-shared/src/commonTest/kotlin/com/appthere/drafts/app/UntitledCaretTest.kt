package com.appthere.drafts.app

import com.appthere.drafts.platform.files.CaretRecord
import com.appthere.drafts.platform.files.DocumentRef
import com.appthere.drafts.platform.files.SessionRecord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * `appthere-drafts.md` 7.4: a new document is "ready to type into", and "Nothing stands between
 * launching the app and writing."
 *
 * A caret is what makes that true. Without one, the editor shows no field, the soft keyboard has
 * nothing to attach to, and the reader's first action has to be finding and tapping an empty page.
 *
 * Asserted here rather than through a composition because a focused text field never lets the
 * Compose test clock settle: `waitForIdle` on a window holding one does not return.
 */
class UntitledCaretTest {
    @Test
    fun `a new untitled document opens with the caret in it`() {
        val caret = assertNotNull(openUntitled(FakeDocumentStore(REF, "")).editor.caret)

        assertEquals(0, caret.offset)
    }

    @Test
    fun `a document that has words in it opens with no caret`() {
        // One the reader is coming back to. A caret blinking in a paragraph nobody asked to edit
        // invites an accidental keystroke, and 7.3 puts it back where they left it if they had one.
        assertNull(openUntitled(FakeDocumentStore(REF, ""), DRAFT).editor.caret)
    }

    @Test
    fun `a restored caret is not overwritten`() {
        val record = recordWith(CaretRecord(blockIndex = 0, offset = 4))

        val caret = assertNotNull(openUntitled(FakeDocumentStore(REF, ""), DRAFT, record).editor.caret)

        assertEquals(4, caret.offset)
    }

    @Test
    fun `a document emptied to nothing still has somewhere for the caret`() {
        // 7.3 records "nowhere yet" as -1, which is what a session that was never clicked into has.
        val record = recordWith(CaretRecord(blockIndex = -1, offset = 0))

        assertNotNull(openUntitled(FakeDocumentStore(REF, ""), "", record).editor.caret)
    }

    private fun recordWith(caret: CaretRecord) =
        SessionRecord(
            documentId = "doc",
            uri = null,
            displayName = "Untitled",
            kind = "markdown",
            caret = caret,
            scrollOffset = 0,
            baseDigest = null,
            snapshotPath = "/sessions/doc/snapshot.md",
        )

    private companion object {
        val REF = DocumentRef("/documents/note.md")
        const val DRAFT = "A first draft.\n"
    }
}
