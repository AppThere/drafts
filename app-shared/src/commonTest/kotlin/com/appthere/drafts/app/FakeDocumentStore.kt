package com.appthere.drafts.app

import com.appthere.drafts.platform.files.DocumentContents
import com.appthere.drafts.platform.files.DocumentRef
import com.appthere.drafts.platform.files.DocumentStore
import com.appthere.drafts.platform.files.FileFacts
import com.appthere.drafts.platform.files.WriteOutcome
import com.appthere.drafts.platform.files.sha256

/**
 * A document store in memory, for testing what [OpenDocument] does with the outcomes.
 *
 * It computes real digests and applies 8.2's comparison itself rather than returning a scripted
 * answer. A store that was told which outcome to return would let a test pass while `OpenDocument`
 * asked the wrong question -- which is precisely the mistake worth catching, since the caller
 * chooses the digest it compares against.
 *
 * What is faked is only the disk. `PathDocumentStoreTest` covers the real one.
 */
class FakeDocumentStore(
    private var contents: String = "",
    private var writable: Boolean = true,
    private var present: Boolean = true,
) : DocumentStore {
    var writes: Int = 0
        private set

    /** The next write fails the way a full disk fails: the file survives, unchanged. */
    var failsToWrite: Boolean = false

    private var modified: Long = 1

    /** Something else rewrote the file while the document was open. */
    fun changeOnDisk(text: String) {
        contents = text
        modified += 1
    }

    fun remove() {
        present = false
    }

    override suspend fun read(ref: DocumentRef): DocumentContents {
        check(present) { "$ref is not there" }
        return DocumentContents(text = contents, facts = facts(), writable = writable)
    }

    override suspend fun facts(ref: DocumentRef): FileFacts? = facts().takeIf { present }

    override suspend fun writeIfUnchanged(
        ref: DocumentRef,
        text: String,
        expected: com.appthere.drafts.platform.files.Digest,
    ): WriteOutcome =
        when {
            !present -> WriteOutcome.Unavailable(WriteOutcome.Reason.Missing, "$ref is not there")
            !writable -> WriteOutcome.Unavailable(WriteOutcome.Reason.Denied, "$ref is not writable")
            digest() != expected -> WriteOutcome.Conflict(expected = expected, found = digest())
            else -> writeAtomically(ref, text)
        }

    override suspend fun writeAtomically(
        ref: DocumentRef,
        text: String,
    ): WriteOutcome =
        if (failsToWrite) {
            WriteOutcome.Unavailable(WriteOutcome.Reason.Failed, "the disk is full")
        } else {
            contents = text
            modified += 1
            writes += 1
            WriteOutcome.Written(facts())
        }

    override suspend fun delete(ref: DocumentRef): Boolean = present.also { present = false }

    private fun digest() = sha256(contents.encodeToByteArray())

    private fun facts() = FileFacts(digest = digest(), size = contents.length.toLong(), modifiedEpochMillis = modified)
}
