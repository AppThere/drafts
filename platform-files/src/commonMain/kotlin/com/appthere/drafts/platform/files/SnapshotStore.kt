package com.appthere.drafts.platform.files

/**
 * Autosave, as `appthere-drafts.md` 8.1 defines it.
 *
 * "**Autosave never touches the user's file.** It writes a snapshot to app-private storage." That
 * sentence is the whole design. The user's file is protected by 8.2's digest check, which refuses
 * writes; a snapshot has nothing to refuse, so it can be written as often as it likes without ever
 * being able to destroy anything the reader did not ask it to.
 *
 * Writes go through [DocumentStore.writeAtomically], so a snapshot gets the same temp-fsync-rename
 * that 8.1 spells out -- "a crash mid-write leaves the previous snapshot intact". Reusing the store
 * rather than reaching for the filesystem is also what keeps the Konsist rule true: there is one
 * place in the project that writes, and this is not another one.
 *
 * [root] is supplied by the caller rather than discovered here. App-private storage is a different
 * question on every platform -- `Context.filesDir`, Application Support, an XDG data directory --
 * and two of those need a handle this module has no way to hold. The platform entry point knows;
 * this does not have to.
 */
class SnapshotStore(
    private val store: DocumentStore,
    private val root: String,
) {
    init {
        // 8.1 is specific: "write `snapshot.md.tmp`, flush and fsync, `rename` over `snapshot.md`.
        // Rename is atomic on every target filesystem; a crash mid-write leaves the previous
        // snapshot intact." A store that cannot do that would turn autosave from the thing that
        // protects the reader's work into a way of destroying it every three seconds.
        //
        // A check rather than a comment because the mistake is easy and silent: on Android the
        // reader's documents arrive through a store that cannot rename, and snapshots have to go
        // to app-private storage through one that can.
        require(store.writesAtomically) {
            "Snapshots need a store that writes atomically; ${store::class.simpleName} does not"
        }
    }

    /**
     * Writes the text and the session record for one document.
     *
     * The text goes first. If the process dies between the two writes, a snapshot with a stale
     * `meta.json` restores the right words at a slightly wrong caret; the other order loses the
     * words and keeps the caret, which is not a trade anyone would choose.
     */
    suspend fun write(
        session: SessionRecord,
        text: String,
    ): Boolean {
        val written = store.writeAtomically(snapshotOf(session.documentId), text)
        if (written !is WriteOutcome.Written) return false

        val record = SessionRecord.format.encodeToString(SessionRecord.serializer(), session)
        return store.writeAtomically(metaOf(session.documentId), record) is WriteOutcome.Written
    }

    /**
     * Starts 8.3's retention clock: the work in this snapshot is now safely in the file.
     *
     * Only the record is rewritten. The snapshot text stays exactly as it was, because it is still
     * the last thing autosave captured and rewriting it would be a write with nothing to write.
     * A document with no snapshot has nothing to stamp and nothing to prune.
     */
    suspend fun markSaved(
        documentId: String,
        now: Long,
    ): Boolean {
        val record = recordOf(documentId) ?: return false

        return putRecord(record.copy(savedAt = now))
    }

    /**
     * Writes a session record with no snapshot beside it.
     *
     * 7.3's session list needs a record the moment a document is opened, not the first time it is
     * edited. A document that was opened and read without being touched still has to come back on
     * the next launch, and until now a record only existed once autosave had written one.
     */
    suspend fun putRecord(record: SessionRecord): Boolean {
        val encoded = SessionRecord.format.encodeToString(SessionRecord.serializer(), record)

        return store.writeAtomically(metaOf(record.documentId), encoded) is WriteOutcome.Written
    }

    /** The snapshot text, or null if there is none. */
    suspend fun textOf(documentId: String): String? =
        runCatching { store.read(snapshotOf(documentId)).text }.getOrNull()

    /**
     * The session record, or null if it is missing or unreadable.
     *
     * A corrupt `meta.json` is null rather than an exception. It is a file on disk that a crash may
     * have caught mid-rename on a filesystem that did not honour the atomicity, and the text beside
     * it is still worth restoring -- 8.3's "Never auto-discard a snapshot" applies to the words,
     * which do not stop being the reader's because the caret position became unreadable.
     */
    suspend fun recordOf(documentId: String): SessionRecord? =
        runCatching {
            SessionRecord.format.decodeFromString(
                SessionRecord.serializer(),
                store.read(metaOf(documentId)).text,
            )
        }.getOrNull()

    /**
     * 8.3's question on launch: does this document's snapshot hold work the file does not?
     *
     * "compare snapshot digest against the file's current digest. If they differ, the snapshot
     * holds unsaved work." Digests and not timestamps, because a snapshot is written on a timer
     * and is therefore almost always newer than the file even when it says exactly the same thing
     * -- an mtime comparison would announce restored changes after every session.
     *
     * The record is carried along but not required. A snapshot whose `meta.json` is unreadable
     * still holds the reader's words, and 8.3 says "Never auto-discard a snapshot"; losing the
     * caret position is not a reason to lose the sentence it was sitting in.
     */
    suspend fun examine(
        documentId: String,
        fileDigest: Digest,
    ): Recovery {
        val text = textOf(documentId)

        return when {
            text == null -> Recovery.NothingToRestore
            sha256(text.encodeToByteArray()) == fileDigest -> Recovery.NothingToRestore
            else -> Recovery.UnsavedWork(text = text, record = recordOf(documentId))
        }
    }

    /** 8.3's pruning, for one document. Both files or neither. */
    suspend fun discard(documentId: String): Boolean {
        val text = store.delete(snapshotOf(documentId))
        val meta = store.delete(metaOf(documentId))
        return text || meta
    }

    /**
     * Every session that has a directory, per 7.3's `sessions/<documentId>/`.
     *
     * The directory listing *is* the session list. A separate index file would be a second thing to
     * keep in step with the first, and the failure mode of an index that drifts is a session nobody
     * looks at holding work nobody knows about.
     */
    suspend fun sessions(): List<String> = store.children(DocumentRef(root)).map { it.token.substringAfterLast('/') }

    /**
     * 8.3's pruning: "Retain snapshots for 30 days after a successful save, then prune."
     *
     * The clock starts at a successful save and nowhere else. A session whose record has no
     * [SessionRecord.savedAt] is never pruned however old it is -- it holds work that never reached
     * a file, which is precisely what "Never auto-discard a snapshot" is protecting. The same goes
     * for a session whose record cannot be read: unreadable is not the same as expired.
     *
     * Returns how many were pruned, which is the only way a caller can tell this did anything.
     */
    suspend fun prune(
        now: Long,
        retainMillis: Long = RETAIN_MILLIS,
    ): Int =
        sessions().count { documentId ->
            val savedAt = recordOf(documentId)?.savedAt
            savedAt != null && now - savedAt >= retainMillis && discard(documentId)
        }

    /** Where 7.3 says the snapshot lives: `.../sessions/<documentId>/snapshot.md`. */
    fun snapshotOf(documentId: String): DocumentRef = DocumentRef("${directoryOf(documentId)}/$SNAPSHOT")

    fun metaOf(documentId: String): DocumentRef = DocumentRef("${directoryOf(documentId)}/$META")

    private fun directoryOf(documentId: String) = "$root/$documentId"

    companion object {
        const val SNAPSHOT = "snapshot.md"
        const val META = "meta.json"

        /** 8.3's thirty days, in milliseconds. */
        const val RETAIN_MILLIS = 30L * 24 * 60 * 60 * 1000
    }
}
