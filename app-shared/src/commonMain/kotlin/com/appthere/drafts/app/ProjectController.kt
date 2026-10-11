package com.appthere.drafts.app

import com.appthere.drafts.platform.files.DocumentRef
import com.appthere.drafts.platform.intents.DocumentKind
import com.appthere.drafts.project.BinderOperations
import com.appthere.drafts.project.Offer
import com.appthere.drafts.project.Outcome
import com.appthere.drafts.project.ProjectNode
import com.appthere.drafts.project.ProjectPath
import com.appthere.drafts.project.ProjectSession
import com.appthere.drafts.project.ProjectView
import com.appthere.drafts.project.Relinking
import com.appthere.drafts.project.Trash
import com.appthere.drafts.project.Trashed
import com.appthere.drafts.project.folderAt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * What a project window's host does that the binder cannot: open a document, hand any other file to
 * the system, and say whether a document has edits not yet saved. Desktop and Android each have one.
 */
interface ProjectHost {
    /** Opens [document] in the project window's editor, or in a window of its own if [newWindow]. */
    fun openDocument(
        document: ProjectNode.Document,
        newWindow: Boolean,
    )

    /** Hands [file] to the system's default application (`projects.md` 13.3). */
    fun openExternally(file: DocumentRef)

    /** Whether the document at [file] is open with edits that are not in the file yet. */
    fun hasUnsavedEdits(file: DocumentRef): Boolean
}

/**
 * The binder's side of a project window (`projects/binder`): which folders are open, which item is
 * chosen, what the writer is being asked, and what they need to be told -- and every action they
 * can take, each run through the project's session and reported back in words.
 */
class ProjectController(
    val session: ProjectSession,
    private val scope: CoroutineScope,
    private val host: ProjectHost,
) {
    private val operations = BinderOperations(session)
    private val relinking = Relinking(session)
    private val trash = Trash(session)
    private val state = MutableStateFlow(BinderUi())

    val view: StateFlow<ProjectView?> get() = session.view
    val ui: StateFlow<BinderUi> = state.asStateFlow()

    fun toggle(folder: ProjectPath) = state.update { it.copy(expanded = it.expanded.toggled(folder)) }

    fun expanded(paths: Set<ProjectPath>) = state.update { it.copy(expanded = paths) }

    fun select(path: ProjectPath?) = state.update { it.copy(selected = path) }

    /** What the writer was told has been heard; what they were asked has been answered or put aside. */
    fun dismissProblem() = state.update { it.copy(problem = null) }

    fun ask(prompt: BinderPrompt?) = state.update { it.copy(prompt = prompt) }

    /** Opened from the binder: a document in the editor, anything else in the system's application. */
    fun open(
        node: ProjectNode,
        newWindow: Boolean = false,
    ) {
        when (node) {
            is ProjectNode.Document -> host.openDocument(node, newWindow).also { act { relinking.seen(node.path) } }
            is ProjectNode.Attachment -> host.openExternally(node.ref).also { act { relinking.seen(node.path) } }
            is ProjectNode.Folder -> toggle(node.path)
            is ProjectNode.Missing -> ask(BinderPrompt.Locate(node.path))
        }
    }

    /** *Move up* (-1) or *Move down* (+1) among its siblings, announced by its new place. */
    fun step(
        path: ProjectPath,
        by: Int,
    ) {
        val rows = binderRows(view.value ?: return, state.value.expanded)
        val names = reorderedBy(rows, path, by) ?: return
        change(path.name) {
            operations.reorder(path.parent, names).also { outcome ->
                if (outcome == Outcome.Done) announce(Announcement.Moved(names.indexOf(path.name) + 1, names.size))
            }
        }
    }

    /** Reordered by a drag within its folder: [names] is the folder's new order. */
    fun reorder(
        folder: ProjectPath,
        names: List<String>,
    ) = change(folder.name) { operations.reorder(folder, names) }

    /** Into [folder], by *Move to…* or by dragging, at [at] in its order if given and the end if not. */
    fun moveTo(
        path: ProjectPath,
        folder: ProjectPath,
        at: Int? = null,
    ) = change(path.name) {
        val moved = operations.move(path, folder)
        val names =
            view.value
                ?.project
                ?.root
                ?.folderAt(folder)
                ?.children
                ?.map { it.name }
                .orEmpty() - path.name
        if (moved != Outcome.Done ||
            at == null
        ) {
            moved
        } else {
            operations.reorder(folder, names.take(at) + path.name + names.drop(at))
        }
    }

    fun rename(
        path: ProjectPath,
        name: String,
    ) = change(name) { operations.rename(path, name) }

    fun createDocument(
        folder: ProjectPath,
        name: String,
        at: Int,
    ) = change(name) { operations.createDocument(folder, name, at) }

    fun createFolder(
        folder: ProjectPath,
        name: String,
        at: Int,
    ) = change(name) { operations.createFolder(folder, name, at) }

    /** To the trash -- after asking, if it is a document with unsaved edits (`projects/trash`). */
    fun delete(node: ProjectNode) {
        val ref = node.ref ?: return
        if (node is ProjectNode.Document &&
            host.hasUnsavedEdits(ref)
        ) {
            ask(BinderPrompt.ConfirmDelete(node.path))
        } else {
            deleteNow(node.path)
        }
    }

    fun deleteNow(path: ProjectPath) = change(path.name) { trash.delete(path) }

    fun removeFromOrder(path: ProjectPath) = change(path.name) { operations.removeFromOrder(path) }

    /** The writer's answers to what reconciling found: relinks, and *New* marks seen. */
    val answers = Answers()

    /** The project's trash, as the binder offers it. */
    val trashCan = TrashActions()

    inner class Answers {
        fun locate(
            missing: ProjectPath,
            file: ProjectPath,
        ) = change(file.name) { relinking.locate(missing, file) }

        fun accept(offer: Offer) = change(ProjectPath(offer.to).name) { relinking.accept(offer) }

        fun decline(offer: Offer) = act { relinking.decline(offer) }

        fun dismissNew(path: ProjectPath) = act { relinking.seen(path) }

        fun seenUnder(folder: ProjectPath) = act { relinking.seenUnder(folder) }
    }

    inner class TrashActions {
        suspend fun items(): List<Trashed> = trash.items()

        /** Back where it was, or -- if its name is taken there now -- the writer is asked for another. */
        fun restore(
            item: Trashed,
            name: String = item.entry.name,
        ) = act {
            when (val outcome = trash.restore(item, name)) {
                Outcome.Done -> Unit
                Outcome.Taken -> ask(BinderPrompt.RestoreAs(item))
                else -> state.update { it.copy(problem = Problem(outcome, name)) }
            }
        }

        /** Sidecar notes left by files deleted outside the app, old enough to offer removing. */
        suspend fun orphans(): List<ProjectPath> = trash.orphanedMetadata()

        fun removeOrphan(path: ProjectPath) = change(path.name) { trash.removeMetadata(path) }

        /** [item] gone for good, or everything if it is null. Asked and confirmed before it gets here. */
        fun empty(item: Trashed?) =
            change(item?.entry?.name.orEmpty()) {
                if (item ==
                    null
                ) {
                    trash.emptyAll()
                } else {
                    trash.empty(item)
                }
            }
    }

    /** Looks at the folder again: in full on opening, otherwise only what says it changed. */
    fun reconcile(full: Boolean = false) = act { session.reconcile(full) }

    private fun change(
        name: String,
        change: suspend () -> Outcome,
    ) = act {
        val outcome = change()
        if (outcome != Outcome.Done) state.update { it.copy(problem = Problem(outcome, name)) }
    }

    private fun announce(announcement: Announcement) = state.update { it.copy(announcement = announcement) }

    private fun act(work: suspend () -> Unit) {
        scope.launch { work() }
    }
}

/** The binder's own state, separate from the project's. */
data class BinderUi(
    val expanded: Set<ProjectPath> = emptySet(),
    val selected: ProjectPath? = null,
    /** Something the writer needs to be told: a change that could not be made. */
    val problem: Problem? = null,
    /** Something to say to assistive technology: where a moved item went. */
    val announcement: Announcement? = null,
    /** What the writer is being asked. */
    val prompt: BinderPrompt? = null,
)

/** A change that was not made: why ([outcome]), and the name it was about. */
data class Problem(
    val outcome: Outcome,
    val name: String,
)

/** Said to assistive technology after a change it could not otherwise perceive. */
sealed interface Announcement {
    /** Moved to [position] of [of] in its folder. */
    data class Moved(
        val position: Int,
        val of: Int,
    ) : Announcement
}

/** What the binder asks the writer. */
sealed interface BinderPrompt {
    data class Rename(
        val path: ProjectPath,
    ) : BinderPrompt

    data class Create(
        val folder: ProjectPath,
        val kind: DocumentKind?,
        val at: Int,
    ) : BinderPrompt

    data class MoveTo(
        val path: ProjectPath,
    ) : BinderPrompt

    data class Locate(
        val missing: ProjectPath,
    ) : BinderPrompt

    data class ConfirmDelete(
        val path: ProjectPath,
    ) : BinderPrompt

    data object Trash : BinderPrompt

    data class RestoreAs(
        val item: Trashed,
    ) : BinderPrompt

    data class ConfirmEmpty(
        val item: Trashed?,
    ) : BinderPrompt
}

private fun Set<ProjectPath>.toggled(path: ProjectPath): Set<ProjectPath> =
    if (path in
        this
    ) {
        this - path
    } else {
        this + path
    }
