package com.appthere.drafts.core.parse.fountain

import com.appthere.drafts.core.fountain.FountainKeywords
import com.appthere.drafts.core.fountain.KeywordPreset
import com.appthere.drafts.core.model.BlockRole
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 11.3: "A French screenwriter writing `INT. CUISINE - JOUR` works by accident; one writing
 * `INTÉRIEUR` does not." With the French words, both do.
 */
class KeywordPresetTest {
    @Test
    fun `in English a long French heading is action`() {
        assertEquals(BlockRole.ACTION, roleOf("INTÉRIEUR CUISINE - JOUR", FountainKeywords.ENGLISH))
    }

    @Test
    fun `in French a long heading is a scene heading with or without its accent`() {
        assertEquals(BlockRole.SCENE_HEADING, roleOf("INTÉRIEUR CUISINE - JOUR", preset("Français")))
        assertEquals(BlockRole.SCENE_HEADING, roleOf("EXTERIEUR RUE - NUIT", preset("Français")))
    }

    @Test
    fun `in French the short English heading still reads`() {
        assertEquals(BlockRole.SCENE_HEADING, roleOf("INT. CUISINE - JOUR", preset("Français")))
    }

    @Test
    fun `each preset reads its own transition`() {
        assertEquals(BlockRole.TRANSITION, roleOf("COUPE À :", preset("Français")))
        assertEquals(BlockRole.TRANSITION, roleOf("CORTE A:", preset("Español")))
        assertEquals(BlockRole.TRANSITION, roleOf("CORTA PARA:", preset("Português")))
        assertEquals(BlockRole.TRANSITION, roleOf("CUT TO:", FountainKeywords.ENGLISH))
    }

    @Test
    fun `a typed word reads as a heading once it is in the list`() {
        val keywords = FountainKeywords.of(listOf("INNEN", "AUSSEN"), "ZU:")!!

        assertEquals(BlockRole.SCENE_HEADING, roleOf("INNEN KÜCHE - TAG", keywords))
        assertEquals(BlockRole.ACTION, roleOf("INNEN KÜCHE - TAG", FountainKeywords.ENGLISH))
    }

    /** The role of [line], alone between blank lines, as [keywords] read it. */
    private fun roleOf(
        line: String,
        keywords: FountainKeywords,
    ): BlockRole? =
        FountainDocumentParser(keywords)
            .parse("Before.\n\n$line\n\nAfter.\n")
            .blocks[1]
            .role

    private fun preset(name: String) = KeywordPreset.ALL.single { it.name == name }.keywords
}
