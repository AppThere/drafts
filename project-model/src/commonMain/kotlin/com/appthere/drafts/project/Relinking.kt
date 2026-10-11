package com.appthere.drafts.project

/**
 * The writer's answers to what reconciliation found (`projects/reconciliation`): relinks accepted,
 * declined, or made by hand with *Locate file…*, and *New* marks seen or dismissed.
 *
 * A relink moves the missing file's history -- its place in the order, its sidecar metadata, its
 * snapshots -- to the file it turned out to be, all of it or none of it. It never merges: a file
 * that has history of its own is refused, and keeps it.
 */
class Relinking(
    private val session: ProjectSession,
) {
    /** The offer taken: [Offer.to] is what [Offer.from] became. */
    suspend fun accept(offer: Offer): Outcome =
        relink(ProjectPath(offer.from), ProjectPath(offer.to), offer.folder) { it.accepted(offer) }

    /** The offer refused: both stay as they are, and it is not made again. */
    suspend fun decline(offer: Offer) {
        session.answer { pending, index -> pending.declined(offer, index) }
    }

    /**
     * *Locate file…*: [missing] is the file at [file], whatever its content -- unless that file has
     * notes or versions of its own ([Outcome.HasHistory]).
     */
    suspend fun locate(
        missing: ProjectPath,
        file: ProjectPath,
    ): Outcome =
        relink(missing, file, folder = false) { pending ->
            pending.copy(offers = pending.offers.filterNot { it.from == missing.value }).seen(file)
        }

    /** Opened in an editor, or dismissed: no longer *New*. */
    suspend fun seen(path: ProjectPath) {
        session.answer { pending, _ -> pending.seen(path) }
    }

    /** *Mark folder as seen*: nothing in [folder], however deep, is *New* any more. */
    suspend fun seenUnder(folder: ProjectPath) {
        session.answer { pending, _ -> pending.seenUnder(folder) }
    }

    private suspend fun relink(
        from: ProjectPath,
        to: ProjectPath,
        folder: Boolean,
        answered: (PendingState) -> PendingState,
    ): Outcome =
        session.change { view ->
            if (view.present(to) == null) return@change Change(Outcome.Failed("$to is not there"))
            if (!folder && session.sidecar.hasHistory(to)) return@change Change(Outcome.HasHistory)

            val steps =
                listOfNotNull(session.rewrite(view, view.manifest.file.relinked(from, to))) +
                    session.sidecar.carrying(from to to, folder)
            Change(Carry(steps).run(), forgotten = setOf(from), answered = answered).onlyIfDone()
        }
}
