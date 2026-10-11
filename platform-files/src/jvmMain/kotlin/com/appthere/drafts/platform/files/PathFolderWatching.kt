package com.appthere.drafts.platform.files

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import java.io.IOException
import java.nio.file.ClosedWatchServiceException
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardWatchEventKinds.ENTRY_CREATE
import java.nio.file.StandardWatchEventKinds.ENTRY_DELETE
import java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY
import java.nio.file.StandardWatchEventKinds.OVERFLOW
import java.nio.file.WatchKey
import java.nio.file.WatchService
import java.util.concurrent.TimeUnit
import kotlin.io.path.isDirectory
import kotlin.io.path.name

/**
 * [FolderWatching] on the desktop, over `WatchService` (`projects-core` design D8).
 *
 * `WatchService` watches one folder, not a tree, so every folder of the project is registered, and a
 * folder that appears is registered as it does. Folders whose names begin with a full stop are left
 * out -- `.git` above all, which churns on every commit -- except the project's own `.drafts/`,
 * whose `project.toml` a `git pull` or a sync can change.
 *
 * Changes are [batched]. If the platform overflowed, or a new folder could not be followed, the
 * signal is [FolderChange.Lost].
 *
 * On macOS the JDK's `WatchService` polls, slowly. Focus reconciles cover what it misses.
 */
class PathFolderWatching(
    private val quiet: Long = QUIET_MILLIS,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : FolderWatching {
    override fun changes(root: DocumentRef): Flow<FolderChange> =
        channelFlow {
            val service = FileSystems.getDefault().newWatchService()
            try {
                register(service, Paths.get(root.token))
                while (currentCoroutineContext().isActive) {
                    val key = service.poll(POLL_MILLIS, TimeUnit.MILLISECONDS) ?: continue
                    send(drain(service, key))
                    key.reset()
                }
            } catch (_: ClosedWatchServiceException) {
                // Closed by cancellation: nothing more to say.
            } finally {
                service.close()
            }
        }.flowOn(io).batched(quiet)

    private fun drain(
        service: WatchService,
        key: WatchKey,
    ): FolderChange {
        val folder = key.watchable() as Path
        // Every event looked at, each new folder registered, before deciding: not stopping at the first loss.
        val lost =
            key.pollEvents().map { event ->
                when (event.kind()) {
                    OVERFLOW -> true
                    ENTRY_CREATE -> !followedIfFolder(service, folder.resolve(event.context() as Path))
                    else -> false
                }
            }
        return if (lost.any { it }) FolderChange.Lost else FolderChange.Changed
    }

    /** A new [path], registered if it is a folder to follow. False only if it should have been and could not be. */
    private fun followedIfFolder(
        service: WatchService,
        path: Path,
    ): Boolean = !(path.isDirectory() && followed(path)) || register(service, path)

    /** Registers [folder] and every followed folder under it. False if some could not be. */
    private fun register(
        service: WatchService,
        folder: Path,
    ): Boolean =
        try {
            folder.register(service, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY)
            Files.newDirectoryStream(folder).use { entries ->
                entries
                    .filter { it.isDirectory() && followed(it) }
                    .map { register(service, it) }
                    .all { it }
            }
        } catch (_: IOException) {
            false
        }

    private fun followed(folder: Path): Boolean = !folder.name.startsWith(".") || folder.name == SIDECAR

    private companion object {
        const val QUIET_MILLIS = 300L
        const val POLL_MILLIS = 100L
        const val SIDECAR = ".drafts"
    }
}
