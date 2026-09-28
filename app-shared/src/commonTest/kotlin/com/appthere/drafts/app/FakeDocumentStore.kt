package com.appthere.drafts.app

import com.appthere.drafts.platform.files.Digest
import com.appthere.drafts.platform.files.DocumentContents
import com.appthere.drafts.platform.files.DocumentRef
import com.appthere.drafts.platform.files.DocumentStore
import com.appthere.drafts.platform.files.FileFacts
import com.appthere.drafts.platform.files.WriteOutcome
import com.appthere.drafts.platform.files.sha256

/**
 * Documents in memory, for testing what the layers above do with the outcomes.
 *
 * It computes real digests and applies 8.2's comparison itself rather than returning a scripted
 * answer. A store that was told which outcome to return would let a test pass while the caller
 * asked the wrong question -- which is exactly the mistake worth catching, since the caller chooses
 * the digest it compares against.
 *
 * Keyed by reference, not a single document. It has to be: 8.1's snapshots go through a store as
 * well, and the whole claim being tested is that they land somewhere other than the reader's file.
 * A fake holding one document would satisfy that assertion by making it impossible to express.
 *
 * What is faked is only the disk. `PathDocumentStoreTest` covers the real one.
 */
class FakeDocumentStore(
    seedRef: DocumentRef,
    seed: String,
    private val writable: Boolean = true,
) : DocumentStore {
    /** In memory, so a write either happened or did not. */
    override val writesAtomically: Boolean = true

    private val files: MutableMap<String, String> = mutableMapOf(seedRef.token to seed)
    private var modified: Long = 1

    var writes: Int = 0
        private set

    /** The next write fails the way a full disk fails: the file survives, unchanged. */
    var failsToWrite: Boolean = false

    /** Something else rewrote the file while the document was open. */
    fun changeOnDisk(
        ref: DocumentRef,
        text: String,
    ) {
        files[ref.token] = text
        modified += 1
    }

    fun remove(ref: DocumentRef) {
        files.remove(ref.token)
    }

    override suspend fun read(ref: DocumentRef): DocumentContents {
        val text = checkNotNull(files[ref.token]) { "$ref is not there" }
        return DocumentContents(text = text, facts = factsOf(text), writable = writable)
    }

    override suspend fun facts(ref: DocumentRef): FileFacts? = files[ref.token]?.let { factsOf(it) }

    override suspend fun writeIfUnchanged(
        ref: DocumentRef,
        text: String,
        expected: Digest,
    ): WriteOutcome {
        val current =
            files[ref.token] ?: return WriteOutcome.Unavailable(WriteOutcome.Reason.Missing, "$ref is not there")

        return when {
            !writable -> WriteOutcome.Unavailable(WriteOutcome.Reason.Denied, "$ref is not writable")
            digestOf(current) != expected -> WriteOutcome.Conflict(expected = expected, found = digestOf(current))
            else -> writeAtomically(ref, text)
        }
    }

    override suspend fun writeAtomically(
        ref: DocumentRef,
        text: String,
    ): WriteOutcome =
        if (failsToWrite) {
            WriteOutcome.Unavailable(WriteOutcome.Reason.Failed, "the disk is full")
        } else {
            files[ref.token] = text
            modified += 1
            writes += 1
            WriteOutcome.Written(factsOf(text))
        }

    /** Everything one level below [ref], as a real directory listing would report it. */
    override suspend fun children(ref: DocumentRef): List<DocumentRef> {
        val prefix = ref.token.trimEnd('/') + "/"

        return files.keys
            .filter { it.startsWith(prefix) }
            .map { DocumentRef(prefix + it.removePrefix(prefix).substringBefore('/')) }
            .distinct()
    }

    override suspend fun delete(ref: DocumentRef): Boolean = files.remove(ref.token) != null

    private fun digestOf(text: String) = sha256(text.encodeToByteArray())

    private fun factsOf(text: String) =
        FileFacts(digest = digestOf(text), size = text.length.toLong(), modifiedEpochMillis = modified)
}
