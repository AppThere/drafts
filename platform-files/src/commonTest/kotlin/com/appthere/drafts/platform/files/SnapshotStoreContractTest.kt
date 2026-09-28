package com.appthere.drafts.platform.files

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * 8.1's precondition, checked on every target rather than only where it is easy to get wrong.
 *
 * "Written atomically: write `snapshot.md.tmp`, flush and fsync, `rename` over `snapshot.md`.
 * Rename is atomic on every target filesystem; a crash mid-write leaves the previous snapshot
 * intact." A store that cannot do that turns autosave from the thing that protects the reader's
 * work into a way of destroying it every three seconds.
 *
 * Android is where this actually bites -- the reader's documents arrive through a store that has
 * no rename-over -- but the check belongs to [SnapshotStore] and so does the test.
 */
class SnapshotStoreContractTest {
    @Test
    fun `a snapshot store cannot be built on a store that does not write atomically`() {
        assertFailsWith<IllegalArgumentException> { SnapshotStore(NotAtomic, "/snapshots") }
    }

    @Test
    fun `the refusal says which store was at fault`() {
        // The mistake is a wiring one -- the wrong store passed at construction -- and the message
        // is what tells whoever hits it which of the two they handed over.
        val message = runCatching { SnapshotStore(NotAtomic, "/snapshots") }.exceptionOrNull()?.message

        assertTrue(message.orEmpty().contains("NotAtomic"), "The refusal said: $message")
    }

    /** Stands in for a Storage Access Framework store, which is the real case. */
    private object NotAtomic : DocumentStore {
        override val writesAtomically: Boolean = false

        override suspend fun read(ref: DocumentRef): DocumentContents = error("not used")

        override suspend fun facts(ref: DocumentRef): FileFacts? = null

        override suspend fun writeIfUnchanged(
            ref: DocumentRef,
            text: String,
            expected: Digest,
        ): WriteOutcome = error("not used")

        override suspend fun writeAtomically(
            ref: DocumentRef,
            text: String,
        ): WriteOutcome = error("not used")

        override suspend fun children(ref: DocumentRef): List<DocumentRef> = emptyList()

        override suspend fun delete(ref: DocumentRef): Boolean = false
    }
}
