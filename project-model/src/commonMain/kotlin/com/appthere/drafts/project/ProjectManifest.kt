package com.appthere.drafts.project

import com.akuleshov7.ktoml.Toml
import com.akuleshov7.ktoml.TomlInputConfig
import com.akuleshov7.ktoml.exceptions.TomlDecodingException
import com.appthere.drafts.platform.files.Digest
import com.appthere.drafts.platform.files.DocumentRef
import com.appthere.drafts.platform.files.DocumentStore
import com.appthere.drafts.platform.files.FolderListing
import com.appthere.drafts.platform.files.WriteOutcome

/**
 * Reads and writes a project's `.drafts/project.toml` (`projects.md` 4).
 *
 * **Line-stable on write.** The file is written from the model in one fixed layout, and the model
 * keeps everything in the order it was read -- folders, entries, labels -- so a change to one
 * folder's order changes that folder's lines and no others. "This is the only file in the project
 * that two devices can contend over."
 *
 * **Never written over what it does not understand.** A file with something this version does not
 * model -- a later schema, a compile target added by hand before Phase 10 -- is read for what it
 * can be, and marked [ManifestState.NotWritable]. Writing the model back would drop the rest,
 * silently, and some of it may be the writer's own configuration.
 *
 * **Not written over a newer one.** A write goes through 8.2's digest check against the file as it
 * was read, so a `git pull` or a sync that changed it meanwhile is a [ManifestWrite.Conflict] rather
 * than something lost. Comments are not kept when the application writes the file.
 *
 * The sidecar is made by the first write (13.1: created "only when the reader does something that
 * needs it"); reading never creates anything.
 */
class ProjectManifest(
    private val listing: FolderListing,
    private val store: DocumentStore,
) {
    /** What `project.toml` in the project at [root] says, and whether it can be written. */
    suspend fun read(root: DocumentRef): ManifestState {
        val sidecar = listing.entriesOf(root)?.firstOrNull { it.isFolder && it.name == SIDECAR }
        val file = sidecar?.let { listing.entriesOf(it.ref) }?.firstOrNull { !it.isFolder && it.name == FILE }

        return file?.let { found ->
            runCatching { store.read(found.ref) }.fold(
                onSuccess = { contents -> decode(contents.text, found.ref, contents.facts.digest) },
                onFailure = { ManifestState.Unreadable },
            )
        } ?: ManifestState.Absent
    }

    /**
     * Writes [file] over what [read] found, and answers with what is there now. Refused, without
     * writing, for anything but a file that was absent or fully understood.
     */
    suspend fun write(
        root: DocumentRef,
        file: ProjectFile,
        read: ManifestState,
    ): ManifestWrite {
        val text = Toml.encodeToString(ProjectFile.serializer(), file)

        val outcome =
            when (read) {
                is ManifestState.Read -> store.writeIfUnchanged(read.ref, text, read.digest)
                ManifestState.Absent -> create(fileIn(root), text)
                else -> return ManifestWrite.Refused(read)
            }

        return when (outcome) {
            is WriteOutcome.Written -> {
                ManifestWrite.Written(
                    ManifestState.Read(file, fileIn(root), outcome.facts.digest),
                )
            }

            is WriteOutcome.Conflict -> {
                ManifestWrite.Conflict
            }

            is WriteOutcome.Unavailable -> {
                ManifestWrite.Failed(outcome.reason)
            }
        }
    }

    /** The first `project.toml`, unless one has appeared since the project was read. */
    private suspend fun create(
        ref: DocumentRef,
        text: String,
    ): WriteOutcome =
        store.facts(ref)?.let { WriteOutcome.Conflict(expected = it.digest, found = it.digest) }
            ?: store.writeAtomically(ref, text)

    private fun fileIn(root: DocumentRef): DocumentRef = listing.childOf(listing.childOf(root, SIDECAR), FILE)

    /**
     * Strictly first. If that fails and a lenient read does not, what failed was a name this version
     * does not know -- the file is fine, and just not ours to rewrite. If both fail, it is not a
     * file this version can read.
     */
    private fun decode(
        text: String,
        ref: DocumentRef,
        digest: Digest,
    ): ManifestState =
        try {
            ManifestState.Read(strict.decodeFromString(ProjectFile.serializer(), text), ref, digest)
        } catch (strictly: TomlDecodingException) {
            runCatching { lenient.decodeFromString(ProjectFile.serializer(), text) }.fold(
                onSuccess = { ManifestState.NotWritable(it, strictly.message.orEmpty()) },
                onFailure = { ManifestState.Invalid(strictly.message.orEmpty()) },
            )
        }

    private companion object {
        const val SIDECAR = ".drafts"
        const val FILE = "project.toml"

        val strict = Toml
        val lenient = Toml(TomlInputConfig(ignoreUnknownNames = true))
    }
}

/** What reading `project.toml` found. */
sealed interface ManifestState {
    /** The project the file describes: the defaults, when there is no file. */
    val file: ProjectFile

    /** No `.drafts/project.toml`: an implicit project (13.1), with every default. */
    data object Absent : ManifestState {
        override val file = ProjectFile()
    }

    /** Read and understood in full, and so safe to write back. */
    data class Read(
        override val file: ProjectFile,
        val ref: DocumentRef,
        val digest: Digest,
    ) : ManifestState

    /** Read for what this version understands; the rest is why it is not written over. */
    data class NotWritable(
        override val file: ProjectFile,
        val reason: String,
    ) : ManifestState

    /** Not TOML this version can read at all. The project opens with defaults; the file is kept. */
    data class Invalid(
        val reason: String,
    ) : ManifestState {
        override val file = ProjectFile()
    }

    /** There, and could not be read: not permitted, or gone between being listed and read. */
    data object Unreadable : ManifestState {
        override val file = ProjectFile()
    }
}

/** What writing `project.toml` did. */
sealed interface ManifestWrite {
    /** Written; [now] is what the next write is checked against. */
    data class Written(
        val now: ManifestState.Read,
    ) : ManifestWrite

    /** It changed since it was read. Nothing was written; read it again and reapply. */
    data object Conflict : ManifestWrite

    /** The file is one this version must not write over; nothing was written. */
    data class Refused(
        val state: ManifestState,
    ) : ManifestWrite

    /** The write itself failed. Nothing changed on disk. */
    data class Failed(
        val reason: WriteOutcome.Reason,
    ) : ManifestWrite
}
