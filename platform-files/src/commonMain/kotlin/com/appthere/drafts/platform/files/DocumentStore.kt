package com.appthere.drafts.platform.files

import kotlin.jvm.JvmInline

/**
 * Where a document lives, as the platform names it.
 *
 * Opaque on purpose. `appthere-drafts.md` 7.3 gives three different things behind this: an Android
 * `content://` URI with a persisted permission, an iOS security-scoped bookmark, a desktop
 * absolute path. Nothing above this module should be able to tell which it has, or it will start
 * assuming one of them.
 */
@JvmInline
value class DocumentRef(
    val token: String,
)

/** What a file looked like when it was last examined -- 8.2's "digest, size, and mtime". */
data class FileFacts(
    val digest: Digest,
    val size: Long,
    val modifiedEpochMillis: Long,
)

/**
 * A document's bytes and the facts recorded about them at the moment they were read.
 *
 * [writable] is recorded at open because that is when 8.4 decides whether the session is
 * `readOnly`. Asking later would be asking a different question: the answer can change while the
 * document is open, and a reader who was told at open that they could save should be told at the
 * point of saving if that is no longer true -- which is a refused write, not a quiet state change.
 */
data class DocumentContents(
    val text: String,
    val facts: FileFacts,
    val writable: Boolean,
)

/**
 * Why a write did not happen.
 *
 * 8.2 turns the middle case into a question for the reader: "This file has changed on disk since
 * you opened it. [Save a copy…] [Reload and lose my changes] [Show differences] [Cancel]". The
 * store's job is to refuse and say so; choosing between those is the UI's.
 */
sealed interface WriteOutcome {
    /** The bytes are on disk, and this is the digest of what was written. */
    data class Written(
        val facts: FileFacts,
    ) : WriteOutcome

    /** 8.2: the file changed under us. Nothing was written. */
    data class Conflict(
        val expected: Digest,
        val found: Digest,
    ) : WriteOutcome

    /** The write could not happen. [reason] says whether the document is still reachable at all. */
    data class Unavailable(
        val reason: Reason,
        val detail: String,
    ) : WriteOutcome

    /**
     * Why a write could not happen -- and, for 8.4, whether that makes the session `orphaned`.
     *
     * The distinction is why this is not a string. 8.4 defines `orphaned` as "file deleted or
     * permission lost", which [Missing] and [Denied] are and [Failed] is not: a full disk or a
     * dropped network share leaves the document exactly where it was, and calling that session
     * orphaned would tell the reader their file is gone while it sits there intact.
     */
    enum class Reason {
        /** The file is not there any more. 8.4's "file deleted". */
        Missing,

        /** The file is there and we may no longer touch it. 8.4's "permission lost". */
        Denied,

        /** Something else went wrong -- a full disk, a failing drive. The document still exists. */
        Failed,
    }
}

/**
 * The only thing in the project that touches the filesystem.
 *
 * `engineering-conventions.md` 4.2 and the Konsist rule `only :platform-files writes to disk` make
 * that literal: no other module may so much as import `java.io.File`. The reason is 8.2 -- a write
 * that skipped the digest check would silently destroy someone's work, and the only way to
 * guarantee no write skips it is for there to be exactly one place a write can happen.
 *
 * Every method here is `suspend` because every one of them is I/O. That is also what keeps
 * `Dispatchers.IO` out of the shared code: the dispatcher choice belongs to the actual, where the
 * platform is known, and `commonMain` is forbidden from naming it at all.
 */
interface DocumentStore {
    /**
     * Whether this store can honour 8.1's "write, flush and fsync, `rename`".
     *
     * Not every platform can. Android's Storage Access Framework hands out `content://` URIs with
     * no rename-over-an-existing-document operation at all, so a write through it truncates and
     * refills the file in place. There is no way to fix that from here -- it is the shape of the
     * API -- so the honest thing is to say so and let [SnapshotStore] refuse to be built on one.
     *
     * 8.1's snapshots *must* have it: "a crash mid-write leaves the previous snapshot intact" is
     * the sentence that makes autosave safe to run every three seconds. 8.2's digest check does
     * not need it, which is why a document can still be saved on a platform where this is false.
     */
    val writesAtomically: Boolean

    /** Reads a document and records the facts 8.2 needs to detect a later change. */
    suspend fun read(ref: DocumentRef): DocumentContents

    /** The file's facts now, without reading all of it into memory if the platform can avoid it. */
    suspend fun facts(ref: DocumentRef): FileFacts?

    /**
     * Whether there is a document at [ref] at all, readable or not.
     *
     * 7.3 answers a vanished file differently from one that is there and cannot be read, and the
     * reader should be told which. The default asks [facts], which cannot tell the two apart -- a
     * file it cannot read has no facts either -- so a store that can, says so.
     */
    suspend fun exists(ref: DocumentRef): Boolean = facts(ref) != null

    /**
     * Writes [text] to the user's file, but only if it still matches [expected].
     *
     * The check and the write are the same operation on purpose. Splitting them would leave a
     * window in which the file could change between being checked and being overwritten, which is
     * precisely the situation 8.2 exists to prevent -- and the window is widest on the platforms
     * the spec is most worried about, "where cloud-sync providers rewrite files under you".
     */
    suspend fun writeIfUnchanged(
        ref: DocumentRef,
        text: String,
        expected: Digest,
    ): WriteOutcome

    /**
     * Writes without checking, for files that are ours rather than the user's.
     *
     * 8.1's snapshots go through here: they live in app-private storage, nothing else writes them,
     * and there is no digest to check against. Still atomic -- "a crash mid-write leaves the
     * previous snapshot intact" is the whole point of a snapshot.
     */
    suspend fun writeAtomically(
        ref: DocumentRef,
        text: String,
    ): WriteOutcome

    /**
     * What is directly inside a directory we own.
     *
     * Only ever used on app-private storage: 7.3's session list is a directory of them, and 8.3's
     * pruning has to walk it. Nothing here enumerates the reader's own filesystem, which is a
     * capability this application has no reason to want and every reason not to have.
     *
     * An unreadable or absent directory is an empty list rather than a failure. On a first run
     * there is no session directory at all, and launching must not depend on one existing.
     */
    suspend fun children(ref: DocumentRef): List<DocumentRef>

    /** Removes a file we own. Used to prune snapshots, per 8.3's thirty days. */
    suspend fun delete(ref: DocumentRef): Boolean
}
