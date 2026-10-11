package com.appthere.drafts.app

import com.appthere.drafts.platform.files.DocumentRef
import com.appthere.drafts.platform.files.DocumentStore
import com.appthere.drafts.platform.files.WriteOutcome
import com.appthere.drafts.platform.files.epochMillis
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The projects this device has opened (`projects/project-windows`, "Recent projects" and "Open
 * projects are restored"): newest first, pinned ones kept, and for each one that was open when the
 * app last stopped, what its window showed -- the folders open in the binder, the item chosen, and
 * the document in its editor.
 *
 * [token] is the platform's access token for the folder: a path on the desktop, a persisted tree
 * grant on Android. Whether it still reaches anything is the host's to say.
 */
@Serializable
data class ProjectEntry(
    val token: String,
    val name: String,
    val pinned: Boolean = false,
    val openedEpochMillis: Long = 0,
    val open: Boolean = false,
    val expanded: List<String> = emptyList(),
    val selected: String? = null,
    /** The session id of the document in the project window's editor, so it comes back with it. */
    val document: String? = null,
)

/**
 * Keeps [ProjectEntry]s in `<root>/recent.json`, in the application's own storage. One writer at a
 * time; through a store that writes atomically, since this is what brings a writer's projects back.
 */
class ProjectList(
    private val store: DocumentStore,
    private val root: String,
) {
    private val mutex = Mutex()

    /** Pinned first, then the rest newest first, at most [RECENT] of those. */
    suspend fun entries(): List<ProjectEntry> =
        mutex.withLock { read() }.let { all ->
            all.filter { it.pinned }.sortedByDescending { it.openedEpochMillis } +
                all.filterNot { it.pinned }.sortedByDescending { it.openedEpochMillis }.take(RECENT)
        }

    /** The projects whose windows were open when the app stopped. */
    suspend fun open(): List<ProjectEntry> = mutex.withLock { read() }.filter { it.open }

    suspend fun opened(
        project: DocumentRef,
        name: String,
        now: Long = epochMillis(),
    ) = change(project) {
        it?.copy(name = name, openedEpochMillis = now, open = true)
            ?: ProjectEntry(project.token, name, openedEpochMillis = now, open = true)
    }

    suspend fun closed(project: DocumentRef) = change(project) { it?.copy(open = false, document = null) }

    suspend fun pin(
        project: DocumentRef,
        pinned: Boolean,
    ) = change(project) { it?.copy(pinned = pinned) }

    suspend fun remove(project: DocumentRef) = change(project) { null }

    /** What the window shows now, for when it comes back. */
    suspend fun remember(
        project: DocumentRef,
        expanded: List<String>,
        selected: String?,
        document: String?,
    ) = change(project) { it?.copy(expanded = expanded, selected = selected, document = document) }

    private suspend fun change(
        project: DocumentRef,
        update: (ProjectEntry?) -> ProjectEntry?,
    ): Boolean =
        mutex.withLock {
            val all = read()
            val changed = update(all.firstOrNull { it.token == project.token })
            val rest = all.filterNot { it.token == project.token }
            write(listOfNotNull(changed) + rest)
        }

    private suspend fun read(): List<ProjectEntry> =
        runCatching {
            format
                .decodeFromString(
                    Kept.serializer(),
                    store.read(file()).text,
                ).projects
        }.getOrDefault(emptyList())

    private suspend fun write(entries: List<ProjectEntry>): Boolean =
        store.writeAtomically(file(), format.encodeToString(Kept.serializer(), Kept(entries))) is WriteOutcome.Written

    private fun file() = DocumentRef("$root/$FILE")

    @Serializable
    private data class Kept(
        val projects: List<ProjectEntry> = emptyList(),
    )

    private companion object {
        const val FILE = "recent.json"

        /** How many unpinned projects the list keeps showing. */
        const val RECENT = 10

        val format = Json { ignoreUnknownKeys = true }
    }
}
