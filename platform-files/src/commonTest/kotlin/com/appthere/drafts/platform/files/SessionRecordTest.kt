package com.appthere.drafts.platform.files

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

/**
 * 7.3's session file, now that some sessions have no file of their own (7.4).
 *
 * The record is written by one version and read by another. A field that became nullable must
 * still read what earlier versions wrote, or an upgrade would lose every session a reader had open.
 */
class SessionRecordTest {
    @Test
    fun `an untitled document gets an id no other document has`() {
        // A UUID, per 7.3, because there is no location to derive one from. Two new documents
        // opened in the same second must not share a snapshot directory.
        val first = SessionIdentity.untitled(kind = "markdown", displayName = "Untitled")
        val second = SessionIdentity.untitled(kind = "markdown", displayName = "Untitled")

        assertNotEquals(first.documentId, second.documentId)
        assertNull(first.uri)
    }

    @Test
    fun `an untitled record survives the trip to disk and back`() {
        val record = RECORD.copy(uri = null, baseDigest = null)

        val decoded = SessionRecord.format.decodeFromString(SessionRecord.serializer(), encode(record))

        assertEquals(record, decoded)
    }

    @Test
    fun `a record written before untitled documents existed still reads`() {
        // Every file on disk today has a uri and a baseDigest. Making them nullable must not change
        // what those files mean.
        val decoded = SessionRecord.format.decodeFromString(SessionRecord.serializer(), encode(RECORD))

        assertEquals(RECORD.uri, decoded.uri)
        assertEquals(RECORD.baseDigest, decoded.baseDigest)
    }

    private fun encode(record: SessionRecord) = SessionRecord.format.encodeToString(SessionRecord.serializer(), record)

    private companion object {
        val RECORD =
            SessionRecord(
                documentId = "chapter-3",
                uri = "file:///documents/chapter-3.md",
                displayName = "chapter-3.md",
                kind = "markdown",
                caret = CaretRecord(blockIndex = 2, offset = 5),
                scrollOffset = 120,
                baseDigest = sha256("As opened.\n".encodeToByteArray()).toString(),
                snapshotPath = "/sessions/chapter-3/snapshot.md",
            )
    }
}
