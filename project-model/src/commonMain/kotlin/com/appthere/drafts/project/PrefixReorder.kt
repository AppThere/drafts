package com.appthere.drafts.project

import com.appthere.drafts.platform.files.DocumentRef
import com.appthere.drafts.platform.files.FolderRenaming
import com.appthere.drafts.platform.files.RenameOutcome

/**
 * A reorder in `prefix` mode (`projects.md` 3): "Reordering renames files `01-`, `02-`; order is
 * visible outside the app."
 *
 * Each entry of the folder is numbered in the order given, files and folders alike, replacing any
 * prefix it had: `the-dock.fountain` at second place is `02-the-dock.fountain`. Only an entry whose
 * name changes is renamed. Numbers are at least two digits, wide enough for the folder, so they
 * sort in a file manager as they do here.
 *
 * **All or nothing.** If any rename fails, the ones already made are undone, so the folder is never
 * left half in one order and half in another. Where a new name is one another entry has now -- two
 * entries trading places -- every renamed entry goes through a temporary name first.
 *
 * **Nothing is ever replaced.** [FolderRenaming] refuses a name already taken, so a reorder that
 * would collide with something outside it fails and is undone rather than writing over a file.
 */
class PrefixReorder(
    private val renaming: FolderRenaming,
) {
    /**
     * Renames [entries] -- every entry of one folder, in the order wanted -- and answers with the
     * renames made, by old name, for whatever else follows a file: its open window, its sidecar.
     */
    suspend fun reorder(entries: List<ProjectNode>): PrefixOutcome {
        val width = maxOf(MINIMUM_WIDTH, entries.size.toString().length)
        val planned =
            entries.mapIndexedNotNull { index, node ->
                prefixed(node.name, index + 1, width).takeIf { it != node.name }?.let { Step(node.ref, it, node.name) }
            }

        val taken = entries.map { it.name }.toSet()
        return if (planned.any { it.name in taken }) viaTemporaryNames(planned) else directly(planned)
    }

    private suspend fun directly(planned: List<Step>): PrefixOutcome {
        val pass = renameAll(planned)
        return pass.failure?.let { undo(pass.done.asReversed(), it) } ?: reordered(pass.done)
    }

    private suspend fun viaTemporaryNames(planned: List<Step>): PrefixOutcome {
        val parked = renameAll(planned.map { it.copy(name = TEMPORARY + it.name) })
        parked.failure?.let { return undo(parked.done.asReversed(), it) }

        val placed = renameAll(parked.done.map { Step(it.ref, it.to.removePrefix(TEMPORARY), it.from) })

        // Those already placed go back first: in a swap, the names the parked ones need are the
        // ones the placed ones are holding.
        return placed.failure?.let {
            undo(placed.done.asReversed() + parked.done.drop(placed.done.size).asReversed(), it)
        } ?: reordered(placed.done)
    }

    /** Renames each of [steps] in turn, stopping at the first that fails. */
    private suspend fun renameAll(steps: List<Step>): Pass {
        val done = mutableListOf<Done>()
        for (step in steps) {
            val outcome = renaming.rename(step.ref, step.name)
            if (outcome !is RenameOutcome.Renamed) return Pass(done, outcome)
            done += Done(step.original, step.name, outcome.ref)
        }
        return Pass(done, failure = null)
    }

    private fun reordered(done: List<Done>): PrefixOutcome =
        PrefixOutcome.Reordered(done.associate { it.from to it.to })

    /** Takes back [steps], in the order given, and says why the reorder did not happen. */
    private suspend fun undo(
        steps: List<Done>,
        why: RenameOutcome,
    ): PrefixOutcome {
        val stranded = steps.filter { step -> renaming.rename(step.ref, step.original()) !is RenameOutcome.Renamed }
        return PrefixOutcome.NotReordered(reason = why, stranded = stranded.map { it.to })
    }

    /** A rename to make: [ref] to [name], for the entry first called [original]. */
    private data class Step(
        val ref: DocumentRef,
        val name: String,
        val original: String,
    )

    /** The renames one pass made, and the failure that stopped it if one did. */
    private class Pass(
        val done: List<Done>,
        val failure: RenameOutcome?,
    )

    /** One rename made: [from] the name it had, [to] the name it has, at [ref]. */
    private data class Done(
        val from: String,
        val to: String,
        val ref: DocumentRef,
    ) {
        /** The name to put back. */
        fun original(): String = from
    }

    private companion object {
        const val MINIMUM_WIDTH = 2

        /**
         * What an entry is called for the moment between its old name and its new one. Begins with
         * a full stop, so a file manager that looks in mid-reorder hides it, and the walk does too.
         */
        const val TEMPORARY = ".drafts-reorder-"
    }
}

/** What a prefix reorder did. */
sealed interface PrefixOutcome {
    /** Renamed as planned; [renames] maps each old name to its new one. */
    data class Reordered(
        val renames: Map<String, String>,
    ) : PrefixOutcome

    /**
     * Not reordered: [reason] is the rename that failed, and everything before it was undone --
     * except [stranded], names that could not be put back either, which the reader needs to hear
     * about by name.
     */
    data class NotReordered(
        val reason: RenameOutcome,
        val stranded: List<String>,
    ) : PrefixOutcome
}

/**
 * [name] numbered [number], [width] digits wide, in place of any prefix it had:
 * `prefixed("the-dock.fountain", 2, 2)` is `02-the-dock.fountain`, and so is
 * `prefixed("7 the-dock.fountain", 2, 2)`.
 */
internal fun prefixed(
    name: String,
    number: Int,
    width: Int,
): String = number.toString().padStart(width, '0') + "-" + name.replace(existingPrefix, "")

private val existingPrefix = Regex("""^\d+(?:[-_ ]|\.\s)\s*""")
