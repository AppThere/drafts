package com.appthere.drafts.platform.files

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Paths

/**
 * [FolderRenaming] over paths.
 *
 * `Files.move` without `REPLACE_EXISTING`, and deliberately without `ATOMIC_MOVE`: the JDK leaves it
 * "implementation specific" whether an atomic move replaces a file that is already there, and on
 * Unix it does. Within one folder a plain move is a rename all the same; it is only the guarantee
 * against replacing that is wanted, and that is what the plain one gives.
 */
class PathFolderRenaming(
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : FolderRenaming {
    override suspend fun rename(
        ref: DocumentRef,
        name: String,
    ): RenameOutcome =
        withContext(io) {
            val from = Paths.get(ref.token)
            val to = from.resolveSibling(name)

            try {
                RenameOutcome.Renamed(DocumentRef(Files.move(from, to).toString()))
            } catch (_: FileAlreadyExistsException) {
                RenameOutcome.Taken
            } catch (failed: IOException) {
                RenameOutcome.Failed(failed.message.orEmpty())
            } catch (denied: SecurityException) {
                RenameOutcome.Failed(denied.message.orEmpty())
            }
        }
}
