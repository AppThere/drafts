package com.appthere.drafts.core.serialise

import com.appthere.drafts.core.parse.fountain.FountainDocumentParser
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Parse, serialise, compare bytes.
 *
 * Phase 7's acceptance: "round-trip is byte-identical on a corpus of real .fountain files". The same
 * property Phase 1 asked of Markdown, and it matters more here rather than less -- a screenplay is a
 * single file a writer lives inside for a year, and the thing they will never forgive is a save that
 * quietly moved their text.
 *
 * `fountain.md` claims this is nearly free: "Fountain is its own canonical serialisation. Preserve the
 * source verbatim for untouched regions and you get perfect fidelity for free." Free is not the same
 * as automatic. The corpus below is chosen for the places where it could stop being true: the
 * whitespace screenwriters put in by hand, the blank line that has a space in it on purpose, and the
 * markers that are in the file but not in any block's words.
 */
class FountainRoundTripTest {
    private val parser = FountainDocumentParser()
    private val serialiser = FountainSerialiser()

    @Test
    fun `a scene with a speech in it round-trips byte for byte`() {
        assertRoundTrips(
            """
            |INT. HOUSE - DAY
            |
            |She sets down the lamp.
            |
            |STEEL
            |(quietly)
            |So much for retirement.
            |
            """.trimMargin(),
        )
    }

    @Test
    fun `every forcing character survives`() {
        assertRoundTrips(".SNIPER SCOPE POV\n")
        assertRoundTrips("!SCREAMING IN THE DARK\n")
        assertRoundTrips("@McAVOY\nI said no.\n")
        assertRoundTrips("~Willy Wonka! Willy Wonka!\n")
        assertRoundTrips("> Burn to White.\n")
        assertRoundTrips("> THE END <\n")
        assertRoundTrips("===\n")
        assertRoundTrips("= Steel arrives at the pool.\n")
    }

    @Test
    fun `section depth is preserved`() {
        assertRoundTrips("# Act I\n\n## Sequence A\n\n### Scene 1\n")
    }

    @Test
    fun `a scene number stays where it was written`() {
        // The number is held in the attributes rather than the words, so it is one of the few things
        // the serialiser has to put back from somewhere other than the inlines.
        assertRoundTrips("INT. HOUSE - DAY #1#\n\nAction.\n")
        assertRoundTrips("EXT. POOL - NIGHT #A-1.2#\n")
    }

    @Test
    fun `hand-positioned whitespace is not straightened out`() {
        // Screenwriters push transitions towards the right margin with spaces. A serialiser that
        // normalised them would be reformatting the script on every save.
        assertRoundTrips("Action.\n\n                        CUT TO:\n\nEXT. POOL - NIGHT\n")
        assertRoundTrips("        hand-positioned\n")
    }

    @Test
    fun `a blank line with a space in it keeps its space`() {
        // "To include a blank line within dialogue, the blank line must contain at least one space."
        // The space is load-bearing: strip it and the speech ends where the writer did not end it.
        assertRoundTrips("STEEL\nFirst half.\n \nSecond half.\n")
    }

    @Test
    fun `a dual dialogue caret survives`() {
        assertRoundTrips("BRICK\nScrew retirement.\n\nSTEEL ^\nScrew retirement.\n")
    }

    @Test
    fun `boneyards and notes come back exactly as typed`() {
        assertRoundTrips("Action.\n\n" + "/" + "*\nCut this later.\n" + "*" + "/\n\nMore action.\n")
        assertRoundTrips("She reads the last page. [[but which page?]]\n")
    }

    @Test
    fun `emphasis and underline keep their own delimiters`() {
        assertRoundTrips("She was *emphatic* about it.\n")
        assertRoundTrips("He was **absolutely** sure.\n")
        assertRoundTrips("It was ***both at once***.\n")
        assertRoundTrips("The word was _underlined_.\n")
        assertRoundTrips("An escaped \\*asterisk\\* stays an asterisk.\n")
    }

    @Test
    fun `asterisks that are not markup are left alone`() {
        // "Asterisks or underscores surrounded by spaces on both sides are treated as literal
        // characters." A parser that read this as emphasis would eat the arithmetic.
        assertRoundTrips("Two times three is 2 * 3.\n")
        assertRoundTrips("A snake_case_name in a note.\n")
    }

    @Test
    fun `the ambiguous cases keep the characters that disambiguate them`() {
        // The plan's third acceptance criterion, asked of the serialiser rather than the parser. Each
        // of these is a case where one character decides what the element *is*, so losing it on a save
        // would not reflow the script, it would change what the script says.
        assertRoundTrips("CUT TO: \n")
        assertRoundTrips("..and then she was gone.\n")
        assertRoundTrips("...a long pause.\n")
        assertRoundTrips("A LOUD CRASH.\n")
        assertRoundTrips("STEEL\nA LOUD CRASH.\n")
    }

    @Test
    fun `a title page is not rewritten`() {
        assertRoundTrips(
            """
            |Title: _BRICK & STEEL_
            |Credit: Written by
            |Author: Stu Maschwitz
            |Draft date: 1/20/2012
            |
            |INT. HOUSE - DAY
            |
            """.trimMargin(),
        )
    }

    @Test
    fun `irregular blank lines are left irregular`() {
        assertRoundTrips("INT. HOUSE - DAY\n\n\n\nAction.\n\n\nMore action.\n")
    }

    @Test
    fun `a file with no trailing newline does not acquire one`() {
        assertRoundTrips("INT. HOUSE - DAY\n\nAction.")
    }

    @Test
    fun `an empty document round-trips to nothing`() {
        assertRoundTrips("")
    }

    @Test
    fun `whitespace-only trailing lines are kept`() {
        assertRoundTrips("Action.\n\n   \n")
    }

    @Test
    fun `the whole screenplay round-trips`() {
        assertRoundTrips(FountainRoundTripFixtures.SCREENPLAY)
    }

    private fun assertRoundTrips(source: String) {
        assertEquals(source, serialiser.serialise(parser.parse(source), source), "Round trip changed the bytes")
    }
}
