package com.appthere.drafts.project

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * `.drafts/project.toml` (`projects.md` 4): order, settings, labels, statuses, targets and
 * collections.
 *
 * Every field has a default, so a project with no file, or a file with only a name in it, reads as
 * a project. Compile targets and export styles are Phase 10's and not modelled yet; a file that has
 * them is read but not written over (see [ProjectManifest]).
 */
@Serializable
data class ProjectFile(
    val project: ProjectSettings = ProjectSettings(),
    val labels: List<Label> = emptyList(),
    val statuses: List<Status> = emptyList(),
    val targets: Targets? = null,
    val collections: Map<String, Collection> = emptyMap(),
)

/** `[project]`, with the order of each folder that has one. */
@Serializable
data class ProjectSettings(
    val name: String? = null,
    val schema: Int = SCHEMA,
    val orderMode: OrderMode = OrderMode.Manifest,
    val defaultKind: String = "markdown",
    /**
     * One table per folder: `[[project.order]]`, its `folder` and its `entries`. Not the spec's
     * first shape, a table keyed by path, which the TOML library cannot read or write (decided
     * 2026-10-06; `projects.md` 4 says so). Still one line of entries per folder, so the file
     * stays diffable.
     */
    val order: List<FolderOrder> = emptyList(),
) {
    companion object {
        /** The version of this file's layout. A later layout bumps it. */
        const val SCHEMA = 1
    }
}

/** How a reorder is kept: `projects.md` 3's two modes. */
@Serializable
enum class OrderMode {
    /** Clean filenames; the order lives in `project.toml`. The default. */
    @SerialName("manifest")
    Manifest,

    /** A reorder renames files `01-`, `02-`, so the order is visible outside the application. */
    @SerialName("prefix")
    Prefix,
}

/** The order of one folder's entries, by name, as the writer arranged them. */
@Serializable
data class FolderOrder(
    val folder: String,
    val entries: List<String>,
)

@Serializable
data class Label(
    val id: String,
    val name: String,
    val color: String,
)

@Serializable
data class Status(
    val id: String,
    val name: String,
)

/** `projects.md` 11: the project's target, and the session's, in words (or pages for a screenplay). */
@Serializable
data class Targets(
    val project: Long? = null,
    val session: Long? = null,
)

/** A saved view of the project: `[collections.<id>]`. */
@Serializable
data class Collection(
    val name: String,
    val type: String,
    val query: String? = null,
)
