package com.appthere.drafts.platform.files

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.attribute.BasicFileAttributes

/**
 * [FolderListing] over paths: the desktop's folders, and the app-private ones on Android.
 *
 * Off the main thread, as every read of the disk is (`engineering-conventions.md` 4.2).
 *
 * A link is listed as what it is and not followed: a link to a folder is not walked into, so a
 * link back up the tree cannot send a walk round in a circle, and a project never silently takes
 * in a folder that lives somewhere else.
 */
class PathFolderListing(
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : FolderListing {
    override suspend fun entriesOf(folder: DocumentRef): List<FolderEntry>? =
        withContext(io) {
            try {
                Files.newDirectoryStream(Paths.get(folder.token)).use { stream ->
                    stream.mapNotNull { path -> entryFor(path) }
                }
            } catch (_: IOException) {
                null
            } catch (_: SecurityException) {
                null
            }
        }

    /** One entry, or null for one that vanished between being listed and being looked at. */
    private fun entryFor(path: Path): FolderEntry? =
        try {
            val attributes = Files.readAttributes(path, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
            FolderEntry(
                name = path.fileName.toString(),
                ref = DocumentRef(path.toString()),
                isFolder = attributes.isDirectory,
                modifiedEpochMillis = attributes.lastModifiedTime().toMillis(),
            )
        } catch (_: IOException) {
            null
        }
}
