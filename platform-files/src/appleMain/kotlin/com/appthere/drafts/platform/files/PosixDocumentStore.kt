package com.appthere.drafts.platform.files

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.dataWithContentsOfFile
import platform.posix.O_CREAT
import platform.posix.O_RDONLY
import platform.posix.O_TRUNC
import platform.posix.O_WRONLY
import platform.posix.close
import platform.posix.fsync
import platform.posix.memcpy
import platform.posix.open
import platform.posix.rename
import platform.posix.stat
import platform.posix.unlink
import platform.posix.write

/**
 * The reader's documents on an Apple platform, over POSIX.
 *
 * **Compiled but never run.** Nothing in this project has executed on an Apple target; `check` only
 * compiles them. Written now so that iOS is not a hole in the design, and so the day a simulator
 * runs, the behaviour it needs is already here to be tested rather than written under pressure.
 *
 * POSIX rather than `NSData.writeToFile(atomically:)`. Foundation's atomic write does the temp and
 * the rename, and does not fsync -- which is the step `appthere-drafts.md` 8.1 names explicitly
 * ("write `snapshot.md.tmp`, flush and fsync, `rename`") and the step that decides whether the
 * bytes are actually down when the rename makes them visible. Without it a crash can leave a file
 * that is intact, newly named and empty.
 *
 * A token is a filesystem path. On iOS the reader's documents arrive as security-scoped URLs, so
 * the caller must be inside `startAccessingSecurityScopedResource` before using this -- resolving
 * the bookmark to a path is `resolveAppleBookmark`, and holding the scope open belongs to whoever
 * holds the document open.
 */
@OptIn(ExperimentalForeignApi::class)
class PosixDocumentStore(
    private val io: CoroutineDispatcher = Dispatchers.Default,
) : DocumentStore {
    /** A real filesystem, and the write below is the sequence 8.1 asks for. */
    override val writesAtomically: Boolean = true

    override suspend fun read(ref: DocumentRef): DocumentContents =
        withContext(io) {
            val bytes = bytesOf(ref.token) ?: error("${ref.token} could not be read")

            DocumentContents(
                text = bytes.decodeToString(),
                facts = factsOf(ref.token, bytes),
                writable = NSFileManager.defaultManager.isWritableFileAtPath(ref.token),
            )
        }

    override suspend fun facts(ref: DocumentRef): FileFacts? =
        withContext(io) { bytesOf(ref.token)?.let { factsOf(ref.token, it) } }

    override suspend fun writeIfUnchanged(
        ref: DocumentRef,
        text: String,
        expected: Digest,
    ): WriteOutcome =
        withContext(io) {
            val current =
                bytesOf(ref.token)
                    ?: return@withContext WriteOutcome.Unavailable(WriteOutcome.Reason.Missing, "${ref.token} is gone")

            val found = sha256(current)
            when {
                found != expected -> {
                    WriteOutcome.Conflict(expected = expected, found = found)
                }

                // Same trap as on the JVM: a rename needs permission on the directory, not on the
                // file it replaces, so without this the atomic write would sail past a read-only
                // document and 8.4's `readOnly` would mean nothing.
                !NSFileManager.defaultManager.isWritableFileAtPath(ref.token) -> {
                    WriteOutcome.Unavailable(WriteOutcome.Reason.Denied, "${ref.token} is not writable")
                }

                else -> {
                    atomically(ref.token, text)
                }
            }
        }

    override suspend fun writeAtomically(
        ref: DocumentRef,
        text: String,
    ): WriteOutcome = withContext(io) { atomically(ref.token, text) }

    override suspend fun children(ref: DocumentRef): List<DocumentRef> =
        withContext(io) {
            val names = NSFileManager.defaultManager.contentsOfDirectoryAtPath(ref.token, null).orEmpty()

            names.filterIsInstance<String>().map { DocumentRef("${ref.token.trimEnd('/')}/$it") }
        }

    override suspend fun delete(ref: DocumentRef): Boolean = withContext(io) { unlink(ref.token) == 0 }

    /**
     * 8.1's sequence: write a temporary file beside the target, fsync it, rename over the target,
     * then fsync the directory so the new name is as durable as the bytes it points at.
     *
     * The temporary file is in the target's own directory, because a rename across filesystems is
     * not a rename -- it is a copy, and copies are not atomic.
     */
    private fun atomically(
        path: String,
        text: String,
    ): WriteOutcome {
        val directory = path.substringBeforeLast('/', "").ifEmpty { "." }
        NSFileManager.defaultManager.createDirectoryAtPath(directory, true, null, null)

        val temporary = "$path$TEMPORARY_SUFFIX"
        val bytes = text.encodeToByteArray()
        val failure = renameInto(path, temporary, bytes)

        // Nothing is left behind on failure. The target still holds the previous version, and a
        // stray temporary file beside the reader's document would be picked up as one.
        if (failure != null) {
            unlink(temporary)
            return WriteOutcome.Unavailable(WriteOutcome.Reason.Failed, failure)
        }
        syncDirectory(directory)

        return WriteOutcome.Written(factsOf(path, bytes))
    }

    /** Writes the temporary file and renames it over [path]. Returns what went wrong, or null. */
    private fun renameInto(
        path: String,
        temporary: String,
        bytes: ByteArray,
    ): String? =
        when {
            !put(temporary, bytes) -> "could not write $temporary"
            rename(temporary, path) != 0 -> "could not rename over $path"
            else -> null
        }

    /** Writes every byte and fsyncs before closing. A short write is permitted, so it loops. */
    private fun put(
        path: String,
        bytes: ByteArray,
    ): Boolean {
        val descriptor = open(path, O_WRONLY or O_CREAT or O_TRUNC, OWNER_READ_WRITE)
        if (descriptor < 0) return false

        return try {
            fill(descriptor, bytes) && fsync(descriptor) == 0
        } finally {
            close(descriptor)
        }
    }

    /** A write is permitted to be short, so it loops. The one time it is, the tail would be lost. */
    private fun fill(
        descriptor: Int,
        bytes: ByteArray,
    ): Boolean =
        bytes.usePinned { pinned ->
            var written = 0
            while (written < bytes.size) {
                val moved = write(descriptor, pinned.addressOf(written), (bytes.size - written).toULong())
                if (moved <= 0) return@usePinned false
                written += moved.toInt()
            }
            true
        }

    /**
     * Makes the rename durable.
     *
     * The bytes and the directory entry that names them reach the disk independently, so without
     * this a crash right after a save can leave the old file in place -- the outcome 8.1 says the
     * atomic write prevents.
     */
    private fun syncDirectory(directory: String) {
        val descriptor = open(directory, O_RDONLY)
        if (descriptor >= 0) {
            fsync(descriptor)
            close(descriptor)
        }
    }

    private fun bytesOf(path: String): ByteArray? =
        NSData.dataWithContentsOfFile(path)?.let { data ->
            ByteArray(data.length.toInt()).also { out ->
                if (out.isNotEmpty()) out.usePinned { memcpy(it.addressOf(0), data.bytes, data.length) }
            }
        }

    private fun factsOf(
        path: String,
        bytes: ByteArray,
    ): FileFacts =
        FileFacts(
            digest = sha256(bytes),
            size = bytes.size.toLong(),
            modifiedEpochMillis = modifiedOf(path),
        )

    private fun modifiedOf(path: String): Long =
        memScoped {
            val info = alloc<stat>()
            if (stat(path, info.ptr) != 0) 0L else info.st_mtimespec.tv_sec * MILLIS_PER_SECOND
        }

    private companion object {
        const val TEMPORARY_SUFFIX = ".tmp"

        /** 0600: the reader's document, readable and writable by them and nobody else. */
        const val OWNER_READ_WRITE = 384

        const val MILLIS_PER_SECOND = 1_000L
    }
}
