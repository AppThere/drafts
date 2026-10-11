package com.appthere.drafts.project

/**
 * `projects.md` 3's three tiers, applied to a project's tree:
 *
 * 1. "Explicit order from `.drafts/project.toml`, for entries listed there."
 * 2. "Numeric filename prefix (`01-`, `02-`) if present -- parsed and used."
 * 3. "Alphabetical for anything else, appended after ordered entries."
 *
 * And the rule that makes external changes safe: "A file that appears in the folder but not in the
 * manifest is never hidden. It sorts to the end of its folder and shows a subtle 'new' marker until
 * the user places it." A folder the writer has never ordered has nothing to place anything against,
 * so nothing in it is new.
 */
fun Project.ordered(file: ProjectFile): Project {
    val orders = file.project.order.associate { it.folder to it.entries }
    return copy(root = root.ordered(orders))
}

private fun ProjectNode.Folder.ordered(orders: Map<String, List<String>>): ProjectNode.Folder {
    val listed = orders[path.value]
    val children = children.map { if (it is ProjectNode.Folder) it.ordered(orders) else it }

    // A name the order lists and the folder no longer has is kept in its place, as missing (9).
    val placed =
        listed.orEmpty().map { name ->
            children.firstOrNull { it.name == name } ?: ProjectNode.Missing(name, path.child(name))
        }
    val rest = children - placed.toSet()
    val prefixed = rest.filter { numericPrefixOf(it.name) != null }.sortedBy { numericPrefixOf(it.name) }
    val alphabetical = rest - prefixed.toSet()

    return copy(
        children =
            placed +
                (prefixed + alphabetical).map { child ->
                    if (listed == null) child else child.markedUnplaced()
                },
    )
}

private fun ProjectNode.markedUnplaced(): ProjectNode =
    when (this) {
        is ProjectNode.Folder -> copy(unplaced = true)
        is ProjectNode.Document -> copy(unplaced = true)
        is ProjectNode.Attachment -> copy(unplaced = true)
        is ProjectNode.Missing -> this
    }

/**
 * The number a name starts with, if it starts with one and a separator -- a hyphen, an underscore,
 * a space, or a full stop and a space: `01-cold-open.fountain` is 1, `2 The Dock.md` is 2, `3. The
 * End.md` is 3. Digits that run into the extension or a word (`1984.md`) are a title, not a prefix.
 */
internal fun numericPrefixOf(name: String): Int? =
    prefix
        .find(name)
        ?.groupValues
        ?.get(1)
        ?.toIntOrNull()

private val prefix = Regex("""^(\d+)(?:[-_ ]|\.\s)""")

/**
 * The project file with [folder]'s children in the order [names] gives: a reorder in `manifest`
 * mode, which writes the order and touches no file.
 *
 * Names the order held that [names] does not -- a file that is missing for now -- are kept, after
 * the rest, in the order they had: 9 says a manifest entry for a path that no longer exists is
 * kept and marked missing, "never silently drop it". A folder ordered for the first time is added
 * after the folders already ordered, so the lines already in the file stay where they are.
 */
fun ProjectFile.reordered(
    folder: ProjectPath,
    names: List<String>,
): ProjectFile {
    val existing = project.order.firstOrNull { it.folder == folder.value }
    val kept = existing?.entries.orEmpty().filterNot { it in names }
    val entry = FolderOrder(folder.value, names.distinct() + kept)

    val order =
        if (existing == null) {
            project.order + entry
        } else {
            project.order.map { if (it.folder == folder.value) entry else it }
        }
    return copy(project = project.copy(order = order))
}

/**
 * The project file after a `prefix` reorder renamed [folder]'s entries: any order kept for the
 * folder names them by their new names, in place, so the entries stay placed rather than turning
 * up as new.
 */
fun ProjectFile.renamed(
    folder: ProjectPath,
    renames: Map<String, String>,
): ProjectFile =
    copy(
        project =
            project.copy(
                order =
                    project.order.map { entry ->
                        if (entry.folder ==
                            folder.value
                        ) {
                            entry.copy(entries = entry.entries.map { renames[it] ?: it })
                        } else {
                            entry
                        }
                    },
            ),
    )

/**
 * The project file after [from] became [to] -- a rename, a move to another folder, or both
 * (`projects/binder`, "SHALL take its order entry").
 *
 * In the same folder the name is changed in place, keeping its position. Into another folder it
 * leaves the old folder's order and, if the new folder has one, joins it at the end ("SHALL arrive
 * at the end of the destination's order"). A folder takes its own order, and every order inside it,
 * to its new path.
 */
fun ProjectFile.moved(
    from: ProjectPath,
    to: ProjectPath,
): ProjectFile {
    val sameFolder = from.parent == to.parent
    val order =
        project.order.map { entry ->
            when {
                sameFolder && entry.folder == from.parent.value -> {
                    entry.copy(entries = entry.entries.map { if (it == from.name) to.name else it })
                }

                entry.folder == from.parent.value -> {
                    entry.copy(entries = entry.entries - from.name)
                }

                entry.folder == to.parent.value && to.name !in entry.entries -> {
                    entry.copy(entries = entry.entries + to.name)
                }

                entry.folder == from.value || entry.folder.startsWith(from.value + "/") -> {
                    entry.copy(folder = to.value + entry.folder.removePrefix(from.value))
                }

                else -> {
                    entry
                }
            }
        }
    return copy(project = project.copy(order = order))
}

/** The project file without [path] in its folder's order: the writer's *Remove from order*. */
fun ProjectFile.withoutEntry(path: ProjectPath): ProjectFile =
    copy(
        project =
            project.copy(
                order =
                    project.order.map {
                        if (it.folder ==
                            path.parent.value
                        ) {
                            it.copy(entries = it.entries - path.name)
                        } else {
                            it
                        }
                    },
            ),
    )

/**
 * The project file with [name] placed in [folder]'s order at [index] -- a new item, put where the
 * writer made it. A folder with no order is left without one: alphabetical is where it belongs.
 */
fun ProjectFile.inserted(
    folder: ProjectPath,
    name: String,
    index: Int,
): ProjectFile =
    copy(
        project =
            project.copy(
                order =
                    project.order.map { entry ->
                        if (entry.folder != folder.value) {
                            entry
                        } else {
                            val entries = entry.entries - name
                            entry.copy(entries = entries.take(index) + name + entries.drop(index))
                        }
                    },
            ),
    )

/**
 * The project file after the writer relinked [from], which is missing, to [to]
 * (`projects/reconciliation`, "Accepting a relink"). In one folder, [to] takes [from]'s place in the
 * order and leaves any other; in different folders [from]'s place goes and [to] stays where it is,
 * since an order belongs to one folder. A folder relinked takes its orders, and those inside it,
 * to its new path.
 */
fun ProjectFile.relinked(
    from: ProjectPath,
    to: ProjectPath,
): ProjectFile {
    val sameFolder = from.parent == to.parent
    val order =
        project.order.map { entry ->
            when {
                entry.folder == from.parent.value && sameFolder -> {
                    entry.copy(entries = (entry.entries - to.name).map { if (it == from.name) to.name else it })
                }

                entry.folder == from.parent.value -> {
                    entry.copy(entries = entry.entries - from.name)
                }

                entry.folder == from.value || entry.folder.startsWith(from.value + "/") -> {
                    entry.copy(folder = to.value + entry.folder.removePrefix(from.value))
                }

                else -> {
                    entry
                }
            }
        }
    return copy(project = project.copy(order = order))
}
