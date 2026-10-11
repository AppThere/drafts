package com.appthere.drafts.project

import com.appthere.drafts.platform.files.DocumentStore
import kotlinx.serialization.Serializable

/**
 * What changed in the project while the application was not looking (`projects/reconciliation`),
 * worked out from a fresh walk, `project.toml`, and the index kept from last time on this device.
 *
 * Observations, and nothing more: what the writer has yet to see or answer is pending state, kept
 * by the project's session (`projects-core` design D2). Nothing here writes at all.
 */
data class Reconciliation(
    /** Files the index has never seen: added outside the application. They are *New*. */
    val added: List<ProjectPath>,
    /** Files the index had recorded missing and that are back where they were. Not *New*. */
    val returned: List<ProjectPath>,
    /** Files the index or the order remembers and the folder no longer has. */
    val missing: List<ProjectPath>,
    /** Files whose content changed since the index saw them. An open one is 8.2's digest check's. */
    val modified: List<ProjectPath>,
    /** A missing file whose content has turned up, unambiguously, under another name. */
    val relinks: List<Relink>,
    /** A missing folder all of whose files have turned up under one new folder. */
    val folderRelinks: List<Relink>,
    /** What to keep for next time. */
    val index: ProjectIndex,
)

/** [from], which is missing, looks like [to], which is new: the same file or folder, renamed. */
data class Relink(
    val from: ProjectPath,
    val to: ProjectPath,
)

/**
 * A relink the writer declined: [from] is not the file whose content is [digest], under any name,
 * for as long as [from] is missing (`projects/reconciliation`, "Declining a relink keeps both").
 */
@Serializable
data class Decline(
    val from: String,
    val digest: String,
)

/**
 * Works out a [Reconciliation].
 *
 * A file is read only when the index has nothing for it, its modification time has changed, or its
 * platform reports none (0), so reconciling an unchanged project reads no file. Missing files are
 * kept in the index for [RETENTION_MILLIS] -- 9's "metadata retained for 30 days" -- and then
 * forgotten; a missing name in `project.toml`'s order is never dropped (that is the writer's).
 */
class Reconciler(
    private val store: DocumentStore,
) {
    /**
     * [unseen] are files still marked *New* from earlier reconciles, which a missing file may still
     * be relinked to; [declines] are the relinks the writer has said no to.
     */
    suspend fun reconcile(
        project: Project,
        file: ProjectFile,
        previous: ProjectIndex?,
        now: Long,
        unseen: Set<ProjectPath> = emptySet(),
        declines: Set<Decline> = emptySet(),
    ): Reconciliation {
        // With no index there is nothing to have changed since: the first reconcile is the baseline,
        // and calling every file in the project "new" would be noise, not news.
        val baseline = previous ?: ProjectIndex()
        val known = baseline.files.associateBy { it.path }
        val wasMissing = baseline.missing.map { it.path }.toSet()
        val present = project.root.files()
        val indexed = present.mapNotNull { node -> indexed(node, known[node.path.value]) }
        // Present means in the folder: a file there that could not be read is not missing.
        val here = present.map { it.path.value }.toSet()

        val gone = baseline.files.filter { it.path !in here }
        val missing =
            gone.map { MissingFile(it.path, it.digest, now, it.blank) } +
                baseline.missing.filter { kept ->
                    kept.path !in here && gone.none { it.path == kept.path } &&
                        now - kept.sinceEpochMillis < RETENTION_MILLIS
                }

        val unknown = if (previous == null) emptyList() else indexed.filter { it.path !in known }
        val added = unknown.filter { it.path !in wasMissing }
        val found =
            RelinkCandidates(missing, indexed.filter { it in added || ProjectPath(it.path) in unseen }, declines)
        val folderRelinks = FolderRelinking(project, baseline, found).relinks()

        return Reconciliation(
            added = added.map { ProjectPath(it.path) },
            returned = unknown.filter { it.path in wasMissing }.map { ProjectPath(it.path) },
            missing = (missing.map { it.path } + missingFromOrder(project, file, here)).distinct().map(::ProjectPath),
            modified =
                indexed
                    .filter {
                        known[it.path]?.let { was ->
                            was.digest != it.digest
                        } == true
                    }.map { ProjectPath(it.path) },
            relinks = found.relinks(folderRelinks),
            folderRelinks = folderRelinks,
            index = ProjectIndex(files = indexed, missing = missing),
        )
    }

    /**
     * [node] as the index should hold it: [was] if the listing says it has not changed since, read
     * again if it has, or if the platform gives no modification time to tell. One that cannot be
     * read keeps what the index had.
     */
    private suspend fun indexed(
        node: ProjectNode,
        was: IndexedFile?,
    ): IndexedFile? {
        val modified = node.modifiedEpochMillis()
        val ref = node.ref
        val unchanged = was != null && modified != UNKNOWN && was.modifiedEpochMillis == modified
        val contents = if (ref == null || unchanged) null else runCatching { store.read(ref) }.getOrNull()

        return contents?.let {
            IndexedFile(node.path.value, modified, it.facts.digest.toString(), blank = it.text.isBlank())
        } ?: was
    }

    /** Names the order holds that are neither a file nor a folder in the project. */
    private fun missingFromOrder(
        project: Project,
        file: ProjectFile,
        here: Set<String>,
    ): List<String> {
        val folders =
            project.root
                .folders()
                .map { it.path.value }
                .toSet()
        return file.project.order
            .flatMap { order -> order.entries.map { ProjectPath(order.folder).child(it).value } }
            .filter { it !in here && it !in folders }
    }

    companion object {
        /** `projects.md` 9: "metadata retained for 30 days". */
        const val RETENTION_MILLIS = 30L * 24 * 60 * 60 * 1000

        /** What a platform that reports no modification time reports. */
        private const val UNKNOWN = 0L
    }
}

/**
 * What can be relinked to what: the [missing] files, the [candidates] they may have become -- new
 * files, or files still marked *New* -- and the relinks the writer has [declines]d.
 *
 * A match is a candidate whose content is a missing file's, when exactly one candidate has it and no
 * other missing file does. Never on blank content, which every empty scene shares, and never one the
 * writer declined: a wrong relink would attach one document's history to another.
 */
internal class RelinkCandidates(
    val missing: List<MissingFile>,
    private val candidates: List<IndexedFile>,
    private val declines: Set<Decline>,
) {
    fun isUsable(gone: MissingFile): Boolean =
        gone.digest != null && !gone.blank && Decline(gone.path, gone.digest) !in declines

    fun matchOf(gone: MissingFile): IndexedFile? =
        candidates
            .filter { !it.blank && it.digest == gone.digest }
            .singleOrNull()
            ?.takeIf { isUsable(gone) && missing.count { it.digest == gone.digest } == 1 }

    /** Relinks offered file by file, for the missing files not under any of [folders]' sources. */
    fun relinks(folders: List<Relink>): List<Relink> =
        missing
            .filter { gone -> folders.none { gone.path.startsWith(it.from.value + "/") } }
            .mapNotNull { gone ->
                matchOf(gone)
                    ?.takeIf { match -> folders.none { match.path.startsWith(it.to.value + "/") } }
                    ?.let { Relink(ProjectPath(gone.path), ProjectPath(it.path)) }
            }
}

private fun ProjectNode.modifiedEpochMillis(): Long =
    when (this) {
        is ProjectNode.Document -> modifiedEpochMillis
        is ProjectNode.Attachment -> modifiedEpochMillis
        else -> 0
    }
