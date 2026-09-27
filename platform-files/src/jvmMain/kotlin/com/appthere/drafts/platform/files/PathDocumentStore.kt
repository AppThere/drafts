package com.appthere.drafts.platform.files

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.AccessDeniedException
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption

/**
 * The desktop [DocumentStore]: a [DocumentRef] is an absolute path.
 *
 * Desktop is the one platform where that is true. Android hands out `content://` URIs and iOS
 * security-scoped bookmarks, which is why [DocumentRef] is opaque -- this class is allowed to know
 * the token is a path, and nothing above it is.
 */
class PathDocumentStore(
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : DocumentStore {
    override suspend fun read(ref: DocumentRef): DocumentContents =
        withContext(io) {
            val path = ref.path()
            val bytes = Files.readAllBytes(path)
            DocumentContents(
                text = bytes.decodeToString(),
                facts = factsOf(path, bytes),
                writable = Files.isWritable(path),
            )
        }

    override suspend fun facts(ref: DocumentRef): FileFacts? =
        withContext(io) {
            runCatching { factsOf(ref.path(), Files.readAllBytes(ref.path())) }.getOrNull()
        }

    /**
     * 8.2, with the re-read and the write in one place.
     *
     * The digest is recomputed here rather than taken from the caller because the caller's copy is
     * by definition old -- it was recorded at open, and the whole question is whether the file has
     * moved on since.
     */
    override suspend fun writeIfUnchanged(
        ref: DocumentRef,
        text: String,
        expected: Digest,
    ): WriteOutcome =
        withContext(io) {
            val path = ref.path()
            val current =
                try {
                    sha256(Files.readAllBytes(path))
                } catch (unreadable: IOException) {
                    return@withContext unreadable.asUnavailable()
                }

            if (current != expected) {
                return@withContext WriteOutcome.Conflict(expected = expected, found = current)
            }
            atomically(path, text)
        }

    override suspend fun writeAtomically(
        ref: DocumentRef,
        text: String,
    ): WriteOutcome = withContext(io) { atomically(ref.path(), text) }

    override suspend fun delete(ref: DocumentRef): Boolean =
        withContext(io) {
            runCatching { Files.deleteIfExists(ref.path()) }.getOrDefault(false)
        }

    /**
     * 8.1's sequence, exactly: "write `snapshot.md.tmp`, flush and fsync, `rename` over
     * `snapshot.md`".
     *
     * Each step earns its place. Writing to a temporary file means the target is never in a
     * half-written state. [FileChannel.force] is the fsync: without it the rename can reach the
     * disk before the bytes do, and a crash then leaves a file that is intact, newly named, and
     * empty. The rename is atomic on every target filesystem, so a reader who loses power mid-save
     * finds the old version rather than a truncated one.
     *
     * The temporary file is created in the target's own directory, not the system temp directory,
     * because a rename across filesystems is not a rename -- it is a copy, and copies are not
     * atomic.
     */
    private fun atomically(
        path: Path,
        text: String,
    ): WriteOutcome {
        val directory =
            path.toAbsolutePath().normalize().parent
                ?: return WriteOutcome.Unavailable(
                    reason = WriteOutcome.Reason.Missing,
                    detail = "$path has no directory to write into",
                )

        // A rename needs permission on the *directory*, not on the file it replaces, so without
        // this the atomic write happily overwrites a file the reader has no write permission for
        // -- verified on this machine as an ordinary user, not assumed. 8.4 makes `readOnly` a
        // state the reader is shown; this is what makes it mean anything. It belongs here rather
        // than in the caller because 4.2's whole point is that there is one place a write happens.
        return when {
            Files.exists(path) && !Files.isWritable(path) -> {
                WriteOutcome.Unavailable(WriteOutcome.Reason.Denied, "$path is not writable")
            }

            else -> {
                write(path, directory, text.encodeToByteArray())
            }
        }
    }

    private fun write(
        path: Path,
        directory: Path,
        bytes: ByteArray,
    ): WriteOutcome =
        try {
            Files.createDirectories(directory)
            renameInto(path, directory, bytes)
            sync(directory)
            WriteOutcome.Written(factsOf(path, bytes))
        } catch (failed: IOException) {
            failed.asUnavailable()
        }

    /**
     * The temporary file is created in the target's own directory, not the system temp directory,
     * because a rename across filesystems is not a rename -- it is a copy, and copies are not
     * atomic.
     *
     * A failure leaves nothing behind. The target still holds the previous version, and a stray
     * temporary file in a project directory would be picked up as a document by the binder.
     */
    private fun renameInto(
        path: Path,
        directory: Path,
        bytes: ByteArray,
    ) {
        val temporary = Files.createTempFile(directory, path.fileName.toString(), TEMPORARY_SUFFIX)
        try {
            fill(temporary, bytes)
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE)
        } catch (failed: IOException) {
            Files.deleteIfExists(temporary)
            throw failed
        }
    }

    /**
     * The flush and fsync of 8.1's "write `snapshot.md.tmp`, flush and fsync, `rename`".
     *
     * [FileChannel.force] is the fsync, and skipping it would not show up in any test on a machine
     * that does not lose power: without it the rename can reach the disk before the bytes do, and
     * a crash then leaves a file that is intact, newly named, and empty.
     *
     * The loop is there because a channel write is permitted to be short. In practice it is not,
     * for a local file -- but the one time it is, the alternative is a snapshot silently missing
     * its tail.
     */
    private fun fill(
        temporary: Path,
        bytes: ByteArray,
    ) {
        FileChannel.open(temporary, StandardOpenOption.WRITE).use { channel ->
            val buffer = ByteBuffer.wrap(bytes)
            while (buffer.hasRemaining()) {
                channel.write(buffer)
            }
            channel.force(true)
        }
    }

    /**
     * Makes the rename itself durable.
     *
     * fsync on the file persists its contents; the directory entry that gives those contents the
     * right name is a separate piece of metadata with its own moment of reaching the disk. Without
     * this a crash immediately after a save can leave the old file still in place -- the exact
     * outcome 8.1 says the atomic write prevents.
     *
     * Best effort on purpose: opening a directory as a channel is a POSIX affordance, and Windows
     * refuses it. There the filesystem orders metadata itself, so there is nothing to do and an
     * exception here would fail a write that in fact succeeded.
     */
    private fun sync(directory: Path) {
        runCatching {
            FileChannel.open(directory, StandardOpenOption.READ).use { it.force(true) }
        }
    }

    private fun factsOf(
        path: Path,
        bytes: ByteArray,
    ): FileFacts =
        FileFacts(
            digest = sha256(bytes),
            size = bytes.size.toLong(),
            modifiedEpochMillis = Files.getLastModifiedTime(path).toMillis(),
        )

    private companion object {
        const val TEMPORARY_SUFFIX = ".tmp"

        fun DocumentRef.path(): Path = Path.of(token)

        /**
         * Sorts an I/O failure into 8.4's terms.
         *
         * Only the first two make a session `orphaned`. Everything else -- a full disk, a failing
         * drive, a network share that went away mid-write -- leaves the file where it was, and the
         * detail is carried through so the reader is told which of those it was rather than a
         * generic apology.
         */
        fun IOException.asUnavailable(): WriteOutcome.Unavailable =
            WriteOutcome.Unavailable(
                reason =
                    when (this) {
                        is NoSuchFileException -> WriteOutcome.Reason.Missing
                        is AccessDeniedException -> WriteOutcome.Reason.Denied
                        else -> WriteOutcome.Reason.Failed
                    },
                detail = "${this::class.simpleName}: ${message ?: "no detail"}",
            )
    }
}
