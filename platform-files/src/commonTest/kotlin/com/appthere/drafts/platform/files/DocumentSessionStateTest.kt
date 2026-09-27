package com.appthere.drafts.platform.files

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 8.4's five states, and which one wins when more than one is true.
 *
 * All of this is pure: the state model never touches a file, which is what lets it be tested on
 * every target rather than only where there is a filesystem to borrow.
 */
class DocumentSessionStateTest {
    @Test
    fun `a freshly opened document is clean`() {
        assertEquals(DocumentState.Clean, opened().state)
    }

    @Test
    fun `an edit makes the document dirty`() {
        assertEquals(DocumentState.Dirty, opened().edited().state)
    }

    @Test
    fun `a saved document is clean again and remembers what it saved`() {
        // 8.2: the write "then updates `baseDigest` to the newly written content". Keeping the old
        // digest would make the session refuse its own next save as a conflict.
        val saved = FileFacts(digest = sha256("saved".encodeToByteArray()), size = 5, modifiedEpochMillis = 2)

        val after = opened().edited().wrote(WriteOutcome.Written(saved))

        assertEquals(DocumentState.Clean, after.state)
        assertEquals(saved, after.base)
    }

    @Test
    fun `a refused write makes the document conflicted and remembers what is on disk`() {
        // 8.2 offers "[ Show differences ]", which needs the other version to be identifiable.
        val found = sha256("theirs".encodeToByteArray())

        val after = opened().edited().wrote(WriteOutcome.Conflict(expected = DIGEST, found = found))

        assertEquals(DocumentState.Conflicted, after.state)
        assertEquals(found, after.changedOnDiskTo)
    }

    @Test
    fun `a deleted file orphans the session`() {
        val after = opened().edited().wrote(unavailable(WriteOutcome.Reason.Missing))

        assertEquals(DocumentState.Orphaned, after.state)
    }

    @Test
    fun `lost permission orphans the session`() {
        val after = opened().edited().wrote(unavailable(WriteOutcome.Reason.Denied))

        assertEquals(DocumentState.Orphaned, after.state)
    }

    @Test
    fun `a failed write leaves the document dirty rather than orphaned`() {
        // The distinction 8.4 draws: `orphaned` is "file deleted or permission lost". A full disk
        // is neither. Telling the reader their document is gone when it is sitting there intact
        // would be worse than telling them nothing.
        val after = opened().edited().wrote(unavailable(WriteOutcome.Reason.Failed))

        assertEquals(DocumentState.Dirty, after.state)
        assertTrue(after.hasUnsavedEdits, "The edits did not reach disk and are still unsaved")
    }

    @Test
    fun `a read-only document reports that rather than its unsaved edits`() {
        // Both facts are true at once and 8.4 allows only one to be shown. Read-only is the one
        // the reader can act on: it tells them why the save they are about to attempt will not
        // work. The edits themselves are still recorded, and 8.1's snapshot still holds them.
        val edited = opened(writable = false).edited()

        assertEquals(DocumentState.ReadOnly, edited.state)
        assertTrue(edited.hasUnsavedEdits, "The edit was dropped instead of recorded")
    }

    @Test
    fun `a conflict outranks unsaved edits`() {
        val conflicted = opened().edited().wrote(WriteOutcome.Conflict(DIGEST, sha256(ByteArray(0))))

        assertEquals(DocumentState.Conflicted, conflicted.state)
    }

    @Test
    fun `a missing file outranks a conflict`() {
        // Ordered least recoverable first. A reader whose file has been deleted needs that before
        // they need to know it also differs from what they have.
        val both =
            opened()
                .wrote(WriteOutcome.Conflict(DIGEST, sha256(ByteArray(0))))
                .wrote(unavailable(WriteOutcome.Reason.Missing))

        assertEquals(DocumentState.Orphaned, both.state)
    }

    @Test
    fun `reloading clears a conflict`() {
        // 8.2's "[ Reload and lose my changes ]" -- the only exit from `conflicted` that keeps the
        // reader in the same document.
        val theirs = contents("Theirs.\n")
        val conflicted = opened().edited().wrote(WriteOutcome.Conflict(DIGEST, theirs.facts.digest))

        val after = conflicted.reloaded(theirs)

        assertEquals(DocumentState.Clean, after.state)
        assertEquals(theirs.facts, after.base)
    }

    @Test
    fun `reloading a restored file clears orphaned`() {
        // A sync client putting a folder back is ordinary. Requiring the reader to close and
        // reopen the document to escape `orphaned` would make it look permanent when it is not.
        val orphaned = opened().edited().wrote(unavailable(WriteOutcome.Reason.Missing))

        assertEquals(DocumentState.Clean, orphaned.reloaded(contents("Back.\n")).state)
    }

    @Test
    fun `reloading picks up a change in writability`() {
        // The permissions may be why the file was unreachable in the first place, so re-reading
        // without re-asking would restore the session to a state it cannot actually save from.
        val reloaded = opened().reloaded(contents("Same.\n", writable = false))

        assertEquals(DocumentState.ReadOnly, reloaded.state)
    }

    private fun opened(writable: Boolean = true) = DocumentSessionState.opened(REF, contents("Opened.\n", writable))

    private fun contents(
        text: String,
        writable: Boolean = true,
    ) = DocumentContents(
        text = text,
        facts =
            FileFacts(
                digest = sha256(text.encodeToByteArray()),
                size = text.length.toLong(),
                modifiedEpochMillis = 1,
            ),
        writable = writable,
    )

    private fun unavailable(reason: WriteOutcome.Reason) = WriteOutcome.Unavailable(reason, "for the test")

    private companion object {
        val REF = DocumentRef("/documents/note.md")
        val DIGEST = sha256("Opened.\n".encodeToByteArray())
    }
}
