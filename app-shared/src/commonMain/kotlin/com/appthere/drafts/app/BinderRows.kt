package com.appthere.drafts.app

import androidx.compose.ui.semantics.CustomAccessibilityAction
import com.appthere.drafts.platform.intents.DocumentKind
import com.appthere.drafts.project.ProjectNode
import com.appthere.drafts.project.ProjectPath
import com.appthere.drafts.project.ProjectView

/**
 * The binder as a list (`projects/binder`): the project's tree with every expanded folder's children
 * under it, one [BinderRow] each, in the order the project resolves.
 *
 * A list rather than nested composables because a project is hundreds of files and the binder is a
 * `LazyColumn`, keyed by path, so that a row keeps its state -- focus, a drag in progress -- when
 * others come and go around it.
 */
internal fun binderRows(
    view: ProjectView,
    expanded: Set<ProjectPath>,
): List<BinderRow> = rowsOf(view.project.root, level = 1, expanded, view.pending.new)

private fun rowsOf(
    folder: ProjectNode.Folder,
    level: Int,
    expanded: Set<ProjectPath>,
    new: Set<String>,
): List<BinderRow> =
    folder.children.flatMapIndexed { index, node ->
        val row = BinderRow(node, level, index + 1, folder.children.size, node.path in expanded, node.path.value in new)
        if (node is ProjectNode.Folder &&
            row.expanded
        ) {
            listOf(row) + rowsOf(node, level + 1, expanded, new)
        } else {
            listOf(row)
        }
    }

/**
 * One line of the binder: [node], at nesting [level] (1 at the top), [position] of [siblings] in
 * its folder -- what a screen reader announces, since the indentation that says so to the eye says
 * nothing to anything else.
 */
internal data class BinderRow(
    val node: ProjectNode,
    val level: Int,
    val position: Int,
    val siblings: Int,
    val expanded: Boolean,
    /** Marked *New* on this device: not yet opened or dismissed. */
    val new: Boolean,
) {
    val path: ProjectPath get() = node.path

    val kind: RowKind
        get() =
            when (node) {
                is ProjectNode.Folder -> {
                    RowKind.Folder
                }

                is ProjectNode.Missing -> {
                    RowKind.Missing
                }

                is ProjectNode.Attachment -> {
                    RowKind.Other
                }

                is ProjectNode.Document -> {
                    if (node.kind ==
                        DocumentKind.Fountain
                    ) {
                        RowKind.Fountain
                    } else {
                        RowKind.Markdown
                    }
                }
            }

    /** Not placed in an order the writer made: *Unsorted* (`projects.md` 3). */
    val unsorted: Boolean get() = node.unplaced

    val isFolder: Boolean get() = node is ProjectNode.Folder

    /** Something on disk the writer can act on, as opposed to a name remembered for a missing file. */
    val present: Boolean get() = node.ref != null
}

/** What a row is, which it shows in words or an icon with a name -- never by colour alone. */
internal enum class RowKind { Folder, Markdown, Fountain, Other, Missing }

/**
 * [rows] with the row at [path] moved one place up or down among its siblings, as the folder's new
 * order of names -- for *Move up* and *Move down*. Null at the end it cannot move past.
 */
internal fun reorderedBy(
    rows: List<BinderRow>,
    path: ProjectPath,
    step: Int,
): List<String>? {
    val siblings =
        rows
            .filter {
                it.path.parent == path.parent &&
                    it.level == rows.first { row -> row.path == path }.level
            }.map { it.node.name }
    val from = siblings.indexOf(path.name)
    val to = from + step
    return if (from < 0 || to !in siblings.indices) null else siblings.toMutableList().apply { add(to, removeAt(from)) }
}

/** Where on a row something is dropped: its upper edge, its middle, or its lower edge. */
internal enum class DropZone { Before, Into, After }

/** What a drop does: [folder]'s new order, or a move into [folder] at [at] (its end when null). */
internal sealed interface Drop {
    data class Reorder(
        val folder: ProjectPath,
        val names: List<String>,
    ) : Drop

    data class Move(
        val folder: ProjectPath,
        val at: Int?,
    ) : Drop
}

/**
 * What dropping [source] on [target], at [zone], means (`projects/binder`, "Reordering within a
 * folder" and "Moving to another folder"). The middle of a folder is into it; an edge of anything is
 * beside it, in its folder -- a reorder if that is the folder [source] is already in, a move to that
 * place if not. Nothing, for a drop on itself or into a folder inside itself.
 */
internal fun dropOf(
    rows: List<BinderRow>,
    source: BinderRow,
    target: BinderRow,
    zone: DropZone,
): Drop? {
    val into = zone == DropZone.Into && target.isFolder
    val folder = if (into) target.path else target.path.parent
    return when {
        source.path == target.path || folder.isWithin(source.path) -> null
        into -> Drop.Move(folder, at = null)
        else -> beside(rows, source, target, after = zone != DropZone.Before)
    }
}

/** [source] dropped beside [target], before it or [after]: a reorder in its own folder, a move into another. */
private fun beside(
    rows: List<BinderRow>,
    source: BinderRow,
    target: BinderRow,
    after: Boolean,
): Drop {
    val folder = target.path.parent
    val siblings = rows.filter { it.path.parent == folder && it.level == target.level }.map { it.node.name }
    val place = siblings.indexOf(target.node.name) + if (after) 1 else 0
    if (folder != source.path.parent) return Drop.Move(folder, at = place)

    val at = placeAfterRemoval(siblings, source, place)
    val rest = siblings - source.node.name
    return Drop.Reorder(folder, rest.take(at) + source.node.name + rest.drop(at))
}

/** Where [beside] lands once [source] has been taken out of [siblings] ahead of it. */
private fun placeAfterRemoval(
    siblings: List<String>,
    source: BinderRow,
    beside: Int,
): Int = if (siblings.indexOf(source.node.name) in 0 until beside) beside - 1 else beside

/** Something the writer can do to a row, from its menu or from assistive technology's actions. */
internal class RowAction(
    val label: String,
    val run: () -> Unit,
) {
    val accessibility: CustomAccessibilityAction get() = CustomAccessibilityAction(label) { run().let { true } }
}
