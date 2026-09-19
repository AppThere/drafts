package com.appthere.drafts.editor.engine

import com.appthere.drafts.core.model.Heading
import com.appthere.drafts.core.model.ListBlock
import com.appthere.drafts.core.model.Paragraph
import com.appthere.drafts.core.model.SourceSpan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The engine half of the Phase 2 gate.
 *
 * Two of the gate criteria live here, and both are worded to be *asserted* rather than observed:
 * "reparse range is provably bounded -- asserted in a test, not observed", and "focus and caret
 * survive every structural edit", of which block identity is the engine's half.
 *
 * The fixture is the one the plan names: ten thousand words. A bounded reparse looks identical to
 * an unbounded one on a three-paragraph document, which is exactly why the criterion specifies a
 * size.
 */
class DocumentSessionTest {
    @Test
    fun `a keystroke reparses a bounded window rather than the document`() {
        // The criterion that decides the architecture. If a keystroke in the middle of a long
        // document reparses the whole thing, per-block editing buys nothing and the approach has to
        // be reconsidered before anything is built on it.
        val session = DocumentSession(longDocument())
        val target = session.blocks[MIDDLE_BLOCK].block.source!!

        val outcome = session.edit(SourceSpan.of(target.start.value, target.start.value), "x")

        assertTrue(
            outcome.reparsed.length < session.text.length / BOUND_DIVISOR,
            "Reparsed ${outcome.reparsed.length} of ${session.text.length} code units, " +
                "which is not a bounded window",
        )
    }

    @Test
    fun `the reparse window covers only a handful of blocks`() {
        val session = DocumentSession(longDocument())
        val blockCount = session.blocks.size
        val target = session.blocks[MIDDLE_BLOCK].block.source!!

        val outcome = session.edit(SourceSpan.of(target.start.value, target.start.value), "x")

        assertTrue(
            outcome.blocksReplaced <= MAX_WINDOW_BLOCKS,
            "Replaced ${outcome.blocksReplaced} blocks of $blockCount; the window should be the " +
                "edited block plus its neighbours",
        )
    }

    @Test
    fun `an edit produces the text the user typed`() {
        // Bounded reparse is worthless if it gets the document wrong. This is the control.
        val session = DocumentSession("First para.\n\nSecond para.\n\nThird para.\n")
        val second = session.blocks[1].block.source!!

        session.edit(SourceSpan.of(second.start.value, second.start.value), "New ")

        assertEquals("First para.\n\nNew Second para.\n\nThird para.\n", session.text)
    }

    @Test
    fun `the edited block keeps its identity`() {
        // engineering-conventions.md 4.2: a LazyColumn without stable keys "causes focus and caret
        // loss on structural edits -- a correctness bug, not a performance one". If a keystroke
        // changes the id, the field being typed into is destroyed and recreated mid-word.
        val session = DocumentSession("First para.\n\nSecond para.\n\nThird para.\n")
        val before = session.blocks.map { it.id }
        val second = session.blocks[1].block.source!!

        session.edit(SourceSpan.of(second.start.value, second.start.value), "New ")

        assertEquals(before, session.blocks.map { it.id }, "Block identities changed on a keystroke")
    }

    @Test
    fun `blocks after the edit keep their identity too`() {
        val session = DocumentSession(longDocument())
        val tailBefore = session.blocks.takeLast(TAIL_SAMPLE).map { it.id }
        val target = session.blocks[MIDDLE_BLOCK].block.source!!

        session.edit(SourceSpan.of(target.start.value, target.start.value), "x")

        assertEquals(tailBefore, session.blocks.takeLast(TAIL_SAMPLE).map { it.id })
    }

    @Test
    fun `blocks after the edit have their spans corrected`() {
        // Untouched blocks are not reparsed, so nothing else will fix their offsets. A span left
        // stale points at the wrong characters -- the kind of wrong that surfaces much later.
        val session = DocumentSession("First para.\n\nSecond para.\n\nThird para.\n")

        session.edit(SourceSpan.of(0, 0), "New ")

        val third = session.blocks[2].block.source!!
        assertEquals(
            "Third para.",
            session.text.substring(third.start.value, third.endExclusive.value),
        )
    }

    @Test
    fun `inline spans after the edit are corrected as well`() {
        // Shifting only the block's own span would leave the inlines inside it pointing four
        // characters early, and nothing would notice until something trusted them.
        val session = DocumentSession("First para.\n\nSecond *word* here.\n")

        session.edit(SourceSpan.of(0, 0), "New ")

        val paragraph = session.blocks[1].block as Paragraph
        val emphasis = paragraph.inlines.first { it.source != null && it !is com.appthere.drafts.core.model.Text }
        val span = emphasis.source!!

        assertEquals("*word*", session.text.substring(span.start.value, span.endExclusive.value))
    }

    @Test
    fun `typing a heading marker promotes the paragraph`() {
        // 4.3: "Typing `## ` at the start of a paragraph promotes it to a heading." A structural
        // change inside the window, which is what the window exists to catch.
        val session = DocumentSession("First para.\n\nSecond para.\n")
        val second = session.blocks[1].block.source!!

        session.edit(SourceSpan.of(second.start.value, second.start.value), "## ")

        assertTrue(
            session.blocks[1].block is Heading,
            "Expected a heading, got ${session.blocks[1].block::class.simpleName}",
        )
    }

    @Test
    fun `an edit that joins a paragraph to the list above it is caught`() {
        // The reason the window includes a neighbour. Looking at the edited paragraph alone cannot
        // tell you it has just become the third item of the list above.
        val session = DocumentSession("- one\n- two\n\nthree\n")
        val paragraph =
            session.blocks
                .last()
                .block.source!!

        session.edit(SourceSpan.of(paragraph.start.value, paragraph.start.value), "- ")

        assertTrue(
            session.blocks.any { it.block is ListBlock },
            "Expected a list after joining: ${session.blocks.map { it.block::class.simpleName }}",
        )
    }

    @Test
    fun `a document of ten thousand words parses into many blocks`() {
        // Guards the fixture itself. A bounded-window assertion over three blocks would pass
        // trivially and prove nothing.
        val session = DocumentSession(longDocument())

        assertTrue(session.blocks.size > MIN_FIXTURE_BLOCKS, "Only ${session.blocks.size} blocks")
        assertTrue(session.text.length > MIN_FIXTURE_LENGTH, "Only ${session.text.length} code units")
    }

    private fun longDocument(): String = GateFixture.tenThousandWords()

    private companion object {
        /** Roughly the middle of the document, well away from either end. */
        const val MIDDLE_BLOCK = 100

        /** A bounded window should be a tiny fraction of the document, not merely smaller. */
        const val BOUND_DIVISOR = 20

        /** The edited block plus a neighbour either side, with room for a container splitting. */
        const val MAX_WINDOW_BLOCKS = 6

        const val TAIL_SAMPLE = 5
        const val MIN_FIXTURE_BLOCKS = 200
        const val MIN_FIXTURE_LENGTH = 50_000
    }
}
