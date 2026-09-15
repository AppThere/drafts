package com.appthere.drafts.core.model

/**
 * What a block *is*, independent of how it was written.
 *
 * `export-pipeline.md` calls this "the join between Markdown and Fountain", and it is the piece
 * that lets one IR serve both. A screenplay is structurally a flat sequence of paragraphs, each
 * carrying a role; backends map role to a named paragraph style and little else. A Markdown
 * document uses the shared roles and the block types carry the structure.
 *
 * The list is transcribed from `export-pipeline.md`. Adding a role is a spec change first.
 */
enum class BlockRole {
    // --- Shared between Markdown and Fountain -------------------------------------------------
    BODY,
    HEADING,
    QUOTE,
    CODE,
    LIST_ITEM,
    DEFINITION_TERM,
    DEFINITION_BODY,

    // --- Fountain, rendering ------------------------------------------------------------------
    SCENE_HEADING,
    ACTION,
    CHARACTER,
    DIALOGUE,
    PARENTHETICAL,
    DUAL_DIALOGUE_LEFT,
    DUAL_DIALOGUE_RIGHT,
    TRANSITION,
    CENTERED,
    LYRIC,
    PAGE_BREAK,

    // --- Fountain, non-rendering --------------------------------------------------------------
    // Present in the document and the editor, absent from exported output.
    SECTION,
    SYNOPSIS,
    NOTE,
    ;

    /**
     * True for roles that carry screenplay meaning.
     *
     * Not a rendering decision -- [SECTION], [SYNOPSIS] and [NOTE] are Fountain roles that do not
     * render. Use [rendersInOutput] for that question.
     */
    val isFountainSpecific: Boolean get() = this in FOUNTAIN_ROLES

    /**
     * False for the three Fountain roles that exist for the writer rather than the reader.
     *
     * Sections, synopses and notes are visible while drafting and absent from every export.
     */
    val rendersInOutput: Boolean get() = this !in NON_RENDERING_ROLES

    private companion object {
        val NON_RENDERING_ROLES = setOf(SECTION, SYNOPSIS, NOTE)

        val FOUNTAIN_ROLES =
            setOf(
                SCENE_HEADING,
                ACTION,
                CHARACTER,
                DIALOGUE,
                PARENTHETICAL,
                DUAL_DIALOGUE_LEFT,
                DUAL_DIALOGUE_RIGHT,
                TRANSITION,
                CENTERED,
                LYRIC,
                PAGE_BREAK,
                SECTION,
                SYNOPSIS,
                NOTE,
            )
    }
}
