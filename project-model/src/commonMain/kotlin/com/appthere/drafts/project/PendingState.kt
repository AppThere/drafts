package com.appthere.drafts.project

import kotlinx.serialization.Serializable

/**
 * What this device's writer has not answered yet (`projects-core` design D2): files marked *New*,
 * relinks offered, and relinks declined.
 *
 * A [Reconciliation] is what one look at the folder found; this is what stays on screen until the
 * writer acts. *New* lasts until the file is opened or dismissed, an offer until it is accepted or
 * declined, and a decline for as long as the file it is about is missing. None of it is the folder's
 * business, so none of it is kept there: it is per device, beside the index.
 */
@Serializable
data class PendingState(
    val new: Set<String> = emptySet(),
    val offers: List<Offer> = emptyList(),
    val declines: Set<Decline> = emptySet(),
) {
    /**
     * This state after [reconciliation]: new files marked, new offers made, and whatever no longer
     * applies let go -- a mark on a file that has gone, an offer whose missing file is back or whose
     * new file has gone, a decline whose missing file is no longer missing.
     */
    fun merged(reconciliation: Reconciliation): PendingState {
        val present =
            reconciliation.index.files
                .map { it.path }
                .toSet()
        val missing = reconciliation.missing.map { it.value }.toSet()
        val made =
            reconciliation.relinks.map { Offer(it.from.value, it.to.value, folder = false) } +
                reconciliation.folderRelinks.map { Offer(it.from.value, it.to.value, folder = true) }

        return PendingState(
            new = (new + reconciliation.added.map { it.value }).filter { it in present }.toSet(),
            offers = (offers + made).distinct().filter { it.stillMissing(missing) && it.reaches(present) },
            declines = declines.filter { it.from in missing }.toSet(),
        )
    }

    /** Opened in an editor, or dismissed: no longer *New*. */
    fun seen(path: ProjectPath): PendingState = copy(new = new - path.value)

    /** Every file in [folder], and the folders inside it, marked as seen at once. */
    fun seenUnder(folder: ProjectPath): PendingState = copy(new = new.filterNot { it.isUnder(folder.value) }.toSet())

    /**
     * Declined: both stay as they are, and [offer] is not made again. A declined folder is declined
     * file by file, so that neither the folder nor any file in it is offered back.
     */
    fun declined(
        offer: Offer,
        index: ProjectIndex,
    ): PendingState {
        val digests = index.files.associate { it.path to it.digest }
        val refused =
            if (offer.folder) {
                index.missing.filter { it.path.isUnder(offer.from) }.mapNotNull { gone ->
                    gone.digest?.let { Decline(gone.path, it) }
                }
            } else {
                listOfNotNull(digests[offer.to]?.let { Decline(offer.from, it) })
            }
        return copy(offers = offers - offer, declines = declines + refused)
    }

    /** Accepted: the offer is answered, and what it brings back has been seen. */
    fun accepted(offer: Offer): PendingState =
        copy(offers = offers - offer, new = new.filterNot { it == offer.to || it.isUnder(offer.to) }.toSet())

    /**
     * [from] has become [to] by the app's own doing -- a rename, a move, a reorder in `prefix`
     * mode -- and the marks follow it (`projects/binder`, "SHALL take its ... pending marks").
     */
    fun moved(
        from: ProjectPath,
        to: ProjectPath,
    ): PendingState {
        fun follow(path: String): String =
            if (path == from.value ||
                path.isUnder(from.value)
            ) {
                to.value + path.removePrefix(from.value)
            } else {
                path
            }
        return copy(
            new = new.map(::follow).toSet(),
            offers = offers.map { it.copy(to = follow(it.to)) },
        )
    }
}

/** An offer to relink [from], which is missing, to [to], which is new: a file, or a [folder]. */
@Serializable
data class Offer(
    val from: String,
    val to: String,
    val folder: Boolean,
) {
    internal fun reaches(present: Set<String>): Boolean = if (folder) present.any { it.isUnder(to) } else to in present

    /** A folder is missing for as long as anything recorded in it is: only files are reported missing. */
    internal fun stillMissing(missing: Set<String>): Boolean =
        if (folder) {
            missing.any { it.isUnder(from) }
        } else {
            from in
                missing
        }
}

private fun String.isUnder(folder: String): Boolean = startsWith("$folder/")
