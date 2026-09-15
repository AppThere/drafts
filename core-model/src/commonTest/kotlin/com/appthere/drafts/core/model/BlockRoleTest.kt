package com.appthere.drafts.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [BlockRole] is transcribed from `export-pipeline.md`, and a transcription that silently drifts
 * from its source is worse than no transcription. This test is the thing that notices.
 */
class BlockRoleTest {
    @Test
    fun `the role set matches the specification exactly`() {
        // Transcribed from export-pipeline.md, "BlockRole is the join between Markdown and
        // Fountain". Adding a role is a spec change first; this test failing means the two are
        // out of step, and the spec is the one that is right.
        val expected =
            setOf(
                "BODY",
                "HEADING",
                "QUOTE",
                "CODE",
                "LIST_ITEM",
                "DEFINITION_TERM",
                "DEFINITION_BODY",
                "SCENE_HEADING",
                "ACTION",
                "CHARACTER",
                "DIALOGUE",
                "PARENTHETICAL",
                "DUAL_DIALOGUE_LEFT",
                "DUAL_DIALOGUE_RIGHT",
                "TRANSITION",
                "CENTERED",
                "LYRIC",
                "PAGE_BREAK",
                "SECTION",
                "SYNOPSIS",
                "NOTE",
            )

        assertEquals(expected, BlockRole.entries.map { it.name }.toSet())
    }

    @Test
    fun `sections synopses and notes do not render`() {
        // export-pipeline.md groups these as "Fountain, non-rendering". They are for the writer,
        // and every export backend drops them.
        assertFalse(BlockRole.SECTION.rendersInOutput)
        assertFalse(BlockRole.SYNOPSIS.rendersInOutput)
        assertFalse(BlockRole.NOTE.rendersInOutput)
    }

    @Test
    fun `every other role renders`() {
        val nonRendering = setOf(BlockRole.SECTION, BlockRole.SYNOPSIS, BlockRole.NOTE)

        BlockRole.entries
            .filterNot { it in nonRendering }
            .forEach { assertTrue(it.rendersInOutput, "$it should render") }
    }

    @Test
    fun `shared roles are not marked Fountain specific`() {
        // These seven are the ones a Markdown document uses. If one were flagged Fountain-specific,
        // a backend keying off that flag would mis-style ordinary prose.
        listOf(
            BlockRole.BODY,
            BlockRole.HEADING,
            BlockRole.QUOTE,
            BlockRole.CODE,
            BlockRole.LIST_ITEM,
            BlockRole.DEFINITION_TERM,
            BlockRole.DEFINITION_BODY,
        ).forEach { assertFalse(it.isFountainSpecific, "$it is shared, not Fountain-specific") }
    }

    @Test
    fun `screenplay roles are marked Fountain specific`() {
        assertTrue(BlockRole.SCENE_HEADING.isFountainSpecific)
        assertTrue(BlockRole.DUAL_DIALOGUE_LEFT.isFountainSpecific)
        assertTrue(BlockRole.NOTE.isFountainSpecific, "Non-rendering roles are still Fountain roles")
    }

    @Test
    fun `every role is either shared or Fountain specific`() {
        val shared = BlockRole.entries.filterNot { it.isFountainSpecific }

        assertEquals(
            SHARED_ROLE_COUNT,
            shared.size,
            "export-pipeline.md lists exactly $SHARED_ROLE_COUNT shared roles",
        )
    }

    private companion object {
        const val SHARED_ROLE_COUNT = 7
    }
}
