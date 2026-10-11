package com.appthere.drafts.project

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** `projects/reconciliation`'s marks and offers, which outlast the reconcile that found them. */
class PendingStateTest {
    @Test
    fun `a new mark survives a reconcile that finds nothing`() {
        val marked = PendingState().merged(found(added = listOf(DOCK), present = listOf(DOCK)))

        val later = marked.merged(found(present = listOf(DOCK)))

        assertEquals(setOf(DOCK), later.new)
    }

    @Test
    fun `opening a file clears its mark and no other`() {
        val marked = PendingState(new = setOf(DOCK, MARLA))

        assertEquals(setOf(MARLA), marked.seen(ProjectPath(DOCK)).new)
    }

    @Test
    fun `a folder marked as seen clears every mark inside it and nothing beside it`() {
        val marked = PendingState(new = setOf("/Act 1/a.md", "/Act 1/Scenes/b.md", "/Act 10/c.md", MARLA))

        assertEquals(setOf("/Act 10/c.md", MARLA), marked.seenUnder(ProjectPath("/Act 1")).new)
    }

    @Test
    fun `a mark on a file that has gone again is let go`() {
        val marked = PendingState(new = setOf(DOCK))

        assertTrue(marked.merged(found(present = emptyList())).new.isEmpty())
    }

    @Test
    fun `an offer stays until it is answered`() {
        val offered =
            PendingState().merged(
                found(
                    present = listOf(DOCK),
                    missing = listOf(OLD),
                    relinks =
                        listOf(OLD to DOCK),
                ),
            )

        val later = offered.merged(found(present = listOf(DOCK), missing = listOf(OLD)))

        assertEquals(listOf(Offer(OLD, DOCK, folder = false)), later.offers)
    }

    @Test
    fun `an offer whose missing file came back is withdrawn`() {
        val offered = PendingState(offers = listOf(Offer(OLD, DOCK, folder = false)))

        assertTrue(offered.merged(found(present = listOf(DOCK, OLD))).offers.isEmpty())
    }

    @Test
    fun `declining keeps both and remembers the content it was about`() {
        val offer = Offer(OLD, DOCK, folder = false)
        val state = PendingState(new = setOf(DOCK), offers = listOf(offer))

        val declined = state.declined(offer, index(files = mapOf(DOCK to "d1"), missing = mapOf(OLD to "d1")))

        assertTrue(declined.offers.isEmpty())
        assertEquals(setOf(DOCK), declined.new)
        assertEquals(setOf(Decline(OLD, "d1")), declined.declines)
    }

    @Test
    fun `declining a folder declines each file in it`() {
        val offer = Offer("/Act 1", "/Act One", folder = true)

        val declined =
            PendingState(offers = listOf(offer)).declined(
                offer,
                index(missing = mapOf("/Act 1/a.md" to "a", "/Act 1/Scenes/b.md" to "b", "/Act 10/c.md" to "c")),
            )

        assertEquals(setOf(Decline("/Act 1/a.md", "a"), Decline("/Act 1/Scenes/b.md", "b")), declined.declines)
    }

    @Test
    fun `a decline lapses when its file is no longer missing`() {
        val state = PendingState(declines = setOf(Decline(OLD, "d1")))

        assertTrue(state.merged(found(present = listOf(OLD))).declines.isEmpty())
        assertEquals(state.declines, state.merged(found(missing = listOf(OLD))).declines)
    }

    @Test
    fun `accepting answers the offer and clears the new mark`() {
        val offer = Offer(OLD, DOCK, folder = false)

        val accepted = PendingState(new = setOf(DOCK, MARLA), offers = listOf(offer)).accepted(offer)

        assertTrue(accepted.offers.isEmpty())
        assertEquals(setOf(MARLA), accepted.new)
    }

    @Test
    fun `marks follow a rename the app made`() {
        val state =
            PendingState(new = setOf("/Act 1/a.md", MARLA), offers = listOf(Offer(OLD, "/Act 1/a.md", folder = false)))

        val moved = state.moved(ProjectPath("/Act 1"), ProjectPath("/Act One"))

        assertEquals(setOf("/Act One/a.md", MARLA), moved.new)
        assertEquals(listOf(Offer(OLD, "/Act One/a.md", folder = false)), moved.offers)
    }

    private fun found(
        added: List<String> = emptyList(),
        present: List<String> = emptyList(),
        missing: List<String> = emptyList(),
        relinks: List<Pair<String, String>> = emptyList(),
    ) = Reconciliation(
        added = added.map(::ProjectPath),
        returned = emptyList(),
        missing = missing.map(::ProjectPath),
        modified = emptyList(),
        relinks = relinks.map { Relink(ProjectPath(it.first), ProjectPath(it.second)) },
        folderRelinks = emptyList(),
        index = ProjectIndex(files = present.map { IndexedFile(it, 1, "digest of $it") }),
    )

    private fun index(
        files: Map<String, String> = emptyMap(),
        missing: Map<String, String> = emptyMap(),
    ) = ProjectIndex(
        files = files.map { IndexedFile(it.key, 1, it.value) },
        missing = missing.map { MissingFile(it.key, it.value, 0) },
    )

    private companion object {
        const val DOCK = "/02-dock.fountain"
        const val OLD = "/the-dock.fountain"
        const val MARLA = "/marla.md"
    }
}
