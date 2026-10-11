package com.appthere.drafts.platform.files

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

/**
 * [FolderEditing] over paths.
 *
 * `Files.move` without `REPLACE_EXISTING`, and deliberately without `ATOMIC_MOVE`: the JDK leaves it
 * "implementation specific" whether an atomic move replaces a file that is already there, and on
 * Unix it does. Within one folder a plain move is a rename all the same; it is only the guarantee
 * against replacing that is wanted, and that is what the plain one gives. Creating uses the calls
 * that fail on an existing name, for the same reason.
 *
 * A name is one name: one with a separator in it, or `.` or `..`, would reach outside the folder it
 * was meant for, and is refused before anything is touched.
 */
class PathFolderEditing(
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : FolderEditing {
    override suspend fun rename(
        ref: DocumentRef,
        name: String,
    ): FolderOutcome = named(name) { Files.move(pathOf(ref), pathOf(ref).resolveSibling(name)) }

    override suspend fun move(
        ref: DocumentRef,
        from: DocumentRef,
        to: DocumentRef,
    ): FolderOutcome =
        attempt {
            val moving = pathOf(ref)
            Files.move(moving, pathOf(to).resolve(moving.fileName))
        }

    override suspend fun createFile(
        folder: DocumentRef,
        name: String,
    ): FolderOutcome = named(name) { Files.createFile(pathOf(folder).resolve(name)) }

    override suspend fun createFolder(
        folder: DocumentRef,
        name: String,
    ): FolderOutcome = named(name) { Files.createDirectory(pathOf(folder).resolve(name)) }

    override suspend fun remove(ref: DocumentRef): FolderOutcome =
        attempt {
            val root = pathOf(ref)
            // Not following links: a link in the trash is removed as a link, and what it points at,
            // which is not the trash's, is left alone.
            Files.walkFileTree(root, Removing)
            root
        }

    private suspend fun named(
        name: String,
        change: () -> Path,
    ): FolderOutcome = if (isOneName(name)) attempt(change) else FolderOutcome.Failed("Not a single name: $name")

    private suspend fun attempt(change: () -> Path): FolderOutcome =
        withContext(io) {
            try {
                FolderOutcome.Done(DocumentRef(change().toString()))
            } catch (_: FileAlreadyExistsException) {
                FolderOutcome.Taken
            } catch (failed: IOException) {
                FolderOutcome.Failed(failed.message.orEmpty())
            } catch (denied: SecurityException) {
                FolderOutcome.Failed(denied.message.orEmpty())
            }
        }

    private fun pathOf(ref: DocumentRef): Path = Paths.get(ref.token)

    private object Removing : SimpleFileVisitor<Path>() {
        override fun visitFile(
            file: Path,
            attributes: BasicFileAttributes,
        ): FileVisitResult {
            Files.delete(file)
            return FileVisitResult.CONTINUE
        }

        override fun postVisitDirectory(
            directory: Path,
            failure: IOException?,
        ): FileVisitResult {
            if (failure != null) throw failure
            Files.delete(directory)
            return FileVisitResult.CONTINUE
        }
    }
}

/** A name that stays in the folder it is given to. */
internal fun isOneName(name: String): Boolean =
    name.isNotEmpty() && name != "." && name != ".." && name.none { it == '/' || it == '\\' }
