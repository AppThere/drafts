package com.appthere.drafts.project

import kotlinx.serialization.Serializable

/**
 * What the project's files were when it was last reconciled: `projects.md` 9's memory of the tree,
 * from which "new", "missing" and "modified" are worked out the next time.
 *
 * Disposable: losing it costs a re-read of the project and the "missing" marks for whatever went
 * while it was gone, and nothing else.
 */
@Serializable
data class ProjectIndex(
    val files: List<IndexedFile> = emptyList(),
    val missing: List<MissingFile> = emptyList(),
)

/**
 * A file as it was: where, when it last changed, and its digest, which is what relinks it. [blank]
 * is content that is empty or only whitespace, which every new scene shares and so relinks nothing.
 */
@Serializable
data class IndexedFile(
    val path: String,
    val modifiedEpochMillis: Long,
    val digest: String,
    val blank: Boolean = false,
)

/**
 * A file that has gone, since [sinceEpochMillis]. Kept for 30 days -- "metadata retained for 30
 * days" -- with its [digest], so that if it turns up under another name it can be offered back.
 */
@Serializable
data class MissingFile(
    val path: String,
    val digest: String?,
    val sinceEpochMillis: Long,
    val blank: Boolean = false,
)
