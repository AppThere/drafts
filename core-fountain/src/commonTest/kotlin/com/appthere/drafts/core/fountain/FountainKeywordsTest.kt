package com.appthere.drafts.core.fountain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 11.3's words as a reader gives them: a list typed into a field, and an ending that must not be
 * empty -- every line ends in the empty string, so an empty ending makes every uppercase line alone
 * a transition.
 */
class FountainKeywordsTest {
    @Test
    fun `typed words are trimmed and the empty ones dropped`() {
        val keywords = FountainKeywords.of(listOf(" INTÉRIEUR", "", "EXTÉRIEUR ", "  ", "INTÉRIEUR"), "À :")

        assertEquals(listOf("INTÉRIEUR", "EXTÉRIEUR"), keywords?.sceneHeadingPrefixes)
        assertEquals("À :", keywords?.transitionSuffix)
    }

    @Test
    fun `an empty ending is refused`() {
        assertNull(FountainKeywords.of(listOf("INT"), ""))
        assertNull(FountainKeywords.of(listOf("INT"), "   "))
    }

    @Test
    fun `no heading words at all is allowed and every heading is then forced`() {
        val keywords = FountainKeywords.of(emptyList(), "TO:")

        assertEquals(emptyList(), keywords?.sceneHeadingPrefixes)
        assertFalse(isSceneHeading("INT. KITCHEN - DAY", requireNotNull(keywords)))
    }

    @Test
    fun `every preset keeps the English heading words`() {
        // A reader choosing their own language must not see the INT and EXT headings they already
        // wrote turn into action.
        KeywordPreset.ALL.forEach { preset ->
            assertTrue(
                preset.keywords.sceneHeadingPrefixes.containsAll(FountainKeywords.DEFAULT_SCENE_PREFIXES),
                "${preset.name} dropped the English words",
            )
        }
    }

    @Test
    fun `English is the first preset and is Fountain 1 point 1 itself`() {
        assertEquals(FountainKeywords.ENGLISH, KeywordPreset.ALL.first().keywords)
        assertEquals(
            KeywordPreset.ALL.size,
            KeywordPreset.ALL
                .map { it.name }
                .toSet()
                .size,
        )
    }

    @Test
    fun `a heading in a language the words do not know is offered by its first word`() {
        assertEquals("INTÉRIEUR", unrecognisedHeadingWord("INTÉRIEUR CUISINE - JOUR", FountainKeywords.ENGLISH))
        assertEquals("INNEN", unrecognisedHeadingWord("INNEN. KÜCHE - TAG", FountainKeywords.ENGLISH))
    }

    @Test
    fun `nothing is offered for a heading that already reads as one`() {
        assertNull(unrecognisedHeadingWord("INT. KITCHEN - DAY", FountainKeywords.ENGLISH))
        assertNull(unrecognisedHeadingWord(".INTÉRIEUR CUISINE - JOUR", FountainKeywords.ENGLISH))
    }

    @Test
    fun `nothing is offered for lines without a heading's shape`() {
        listOf(
            "She leaves - quickly.",
            "BANG - BANG",
            "CUT TO:",
            "THE DOOR SLAMS.",
            "INTÉRIEUR - JOUR",
            "1984 KITCHEN - DAY",
        ).forEach { line -> assertNull(unrecognisedHeadingWord(line, FountainKeywords.ENGLISH), line) }
    }
}
