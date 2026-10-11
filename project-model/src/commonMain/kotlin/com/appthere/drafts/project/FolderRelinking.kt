package com.appthere.drafts.project

/**
 * Finds folders renamed outside the app (`projects/reconciliation`, "A renamed folder is offered back
 * as one relink").
 *
 * A folder that has gone is matched to a folder that is new when every file the index recorded
 * under the old one is found, by content, uniquely, and at the same place relative to the folder,
 * under the new one. Anything less -- one file edited, one moved, one declined -- and there is no
 * folder offer, and each file that does match is offered on its own: the strict rule costs at worst
 * what file-by-file relinking costs, while a loose one would have to decide what happens to an order
 * table half of whose names went somewhere else.
 *
 * Only the topmost missing folder is matched: `/Act 1` renamed carries `/Act 1/Scenes` with it.
 */
internal class FolderRelinking(
    project: Project,
    private val baseline: ProjectIndex,
    private val found: RelinkCandidates,
) {
    private val folders =
        project.root
            .folders()
            .map { it.path.value }
            .toSet()

    fun relinks(): List<Relink> =
        found.missing
            .mapNotNull { topMissingFolderOf(it.path) }
            .distinct()
            .mapNotNull { folder -> relinkOf(folder) }

    private fun relinkOf(folder: String): Relink? {
        val under = found.missing.filter { it.path.startsWith("$folder/") }
        val usable = under.isNotEmpty() && under.all { found.isUsable(it) }
        val to = if (usable) destinationOf(folder, under) else null
        return to?.takeIf { isNewFolder(it) }?.let { Relink(ProjectPath(folder), ProjectPath(it)) }
    }

    /** The one folder every file under [folder] turned up in, at the same place within it; or null. */
    private fun destinationOf(
        folder: String,
        under: List<MissingFile>,
    ): String? {
        val destinations =
            under.map { gone ->
                val within = gone.path.removePrefix(folder)
                found
                    .matchOf(gone)
                    ?.path
                    ?.takeIf { it.endsWith(within) }
                    ?.removeSuffix(within)
            }
        return destinations.takeIf { null !in it }?.distinct()?.singleOrNull()
    }

    /** A folder present now that held nothing the index had seen: it appeared since. */
    private fun isNewFolder(path: String): Boolean =
        path in folders && baseline.files.none { it.path.startsWith("$path/") }

    /** The highest folder above [path] that is not in the project any more, if one is not. */
    private fun topMissingFolderOf(path: String): String? =
        path
            .split('/')
            .drop(1)
            .dropLast(1)
            .runningReduce { above, name -> "$above/$name" }
            .map { "/$it" }
            .firstOrNull { it !in folders }
}
