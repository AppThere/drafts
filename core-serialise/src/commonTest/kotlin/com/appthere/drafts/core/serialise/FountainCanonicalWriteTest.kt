package com.appthere.drafts.core.serialise

import com.appthere.drafts.core.model.BlockIndex
import com.appthere.drafts.core.model.BlockRole
import com.appthere.drafts.core.model.Paragraph
import com.appthere.drafts.core.model.Text
import com.appthere.drafts.core.parse.fountain.FountainDocumentParser
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The canonical path: Fountain written from the IR, with no original bytes to fall back on.
 *
 * Separate from [FountainRoundTripTest] because that test cannot reach this code. A block that still
 * carries its source span is re-emitted verbatim, so every round-trip assertion passes whether the
 * writers are right or absent -- which makes a green round-trip suite no evidence at all about them.
 * These assertions withhold the source, which is exactly the state of a block the user has edited or
 * just typed.
 *
 * What is being checked is mostly *forcing*: whether the writer knows when the words alone will be
 * read back as the element they belong to, and reaches for a marker only when they will not. The
 * stronger form of that question is in [reparses], at the bottom.
 */
class FountainCanonicalWriteTest {
    private val parser = FountainDocumentParser()
    private val serialiser = FountainSerialiser()

    @Test
    fun `a slugline that announces itself needs no forcing stop`() {
        assertWrites("INT. HOUSE - DAY", from = "INT. HOUSE - DAY\n")
        assertWrites("EXT. POOL - NIGHT", from = "EXT. POOL - NIGHT\n")
    }

    @Test
    fun `a slugline that does not gets its stop back`() {
        assertWrites(".SNIPER SCOPE POV", from = ".SNIPER SCOPE POV\n")
    }

    @Test
    fun `a scene number is written back out of the attributes`() {
        assertWrites("INT. HOUSE - DAY #1#", from = "INT. HOUSE - DAY #1#\n")
        assertWrites(".SNIPER SCOPE POV #A-1.2#", from = ".SNIPER SCOPE POV #A-1.2#\n")
    }

    @Test
    fun `uppercase action is forced so it is not read as a cue`() {
        // The one genuine ambiguity in the format: nothing but the case of the letters separates a
        // line of action from a character name, so an uppercase line of action has to say so.
        assertWrites("!BANG", from = "!BANG\n")
        assertWrites("She sets down the lamp.", from = "She sets down the lamp.\n")
    }

    @Test
    fun `action is forced when its words begin with another element's marker`() {
        // Not the uppercase case: these words are lowercase and would still be misread, because the
        // first character of the line is what Fountain checks before it checks anything else.
        assertWrites("!.not a scene heading", from = "!.not a scene heading\n")
        assertWrites("!# not a section", from = "!# not a section\n")
        assertWrites("!> not a transition", from = "!> not a transition\n")
    }

    @Test
    fun `a character name that reads as a slugline gets an at sign`() {
        // All capitals and so a valid cue by the uppercase rule -- and also a scene prefix followed by
        // a full stop, which the parser checks first. Written bare it would become a slugline and take
        // the speech under it with it.
        assertWrites("@INT. GUY\nHello.", from = "@INT. GUY\nHello.\n")
    }

    @Test
    fun `a character name that is not all capitals gets an at sign`() {
        assertWrites("@McAVOY\nI said no.", from = "@McAVOY\nI said no.\n")
        assertWrites("STEEL\nI said no.", from = "STEEL\nI said no.\n")
    }

    @Test
    fun `a dual dialogue cue keeps its caret`() {
        assertWrites("STEEL ^\nNo.", from = "STEEL ^\nNo.\n")
    }

    @Test
    fun `a transition that ends in TO needs no angle bracket`() {
        assertWrites("CUT TO:", from = "CUT TO:\n")
    }

    @Test
    fun `a transition that does not gets one`() {
        assertWrites("> Burn to White.", from = "> Burn to White.\n")
    }

    @Test
    fun `centred text gets both of its brackets back`() {
        assertWrites("> THE END <", from = "> THE END <\n")
    }

    @Test
    fun `a run of lyrics is a run of lines`() {
        assertWrites("~One line\n~And another", from = "~One line\n~And another\n")
    }

    @Test
    fun `sections synopses and page breaks keep their markers`() {
        assertWrites("## Sequence A", from = "## Sequence A\n")
        assertWrites("= Steel arrives.", from = "= Steel arrives.\n")
        assertWrites("===", from = "===\n")
    }

    @Test
    fun `a speech is joined to its cue by one newline`() {
        // Two newlines would end the speech: the blank line is what closes it. This is the one place
        // where getting the separator wrong changes what the file says rather than how it looks.
        assertWrites(
            "STEEL\n(quietly)\nSo much for retirement.",
            from = "STEEL\n(quietly)\nSo much for retirement.\n",
        )
    }

    @Test
    fun `a boneyard is written without escaping anything inside it`() {
        val boneyard = "/" + "*\nCut this later. *not italic*\n" + "*" + "/"

        assertWrites(boneyard, from = "$boneyard\n")
    }

    @Test
    fun `a note keeps its brackets`() {
        assertWrites("She reads it. [[which page?]]", from = "She reads it. [[which page?]]\n")
    }

    @Test
    fun `emphasis is written with asterisks and underline with underscores`() {
        assertWrites("She was *emphatic*.", from = "She was *emphatic*.\n")
        assertWrites("He was **sure**.", from = "He was **sure**.\n")
        assertWrites("It was ***both***.", from = "It was ***both***.\n")
        assertWrites("The word was _underlined_.", from = "The word was _underlined_.\n")
    }

    @Test
    fun `a marker the author escaped is escaped again`() {
        // Text holds semantic characters: the parse turned `\*` into `*`. Writing it bare would turn
        // the author's asterisk into italics, so the backslash has to go back on.
        assertWrites("An escaped \\*asterisk\\*.", from = "An escaped \\*asterisk\\*.\n")

        // The underscore separately: it is underline rather than emphasis here, so it is a second
        // marker to put the backslash back in front of rather than the same one twice.
        assertWrites("An escaped \\_underscore\\_.", from = "An escaped \\_underscore\\_.\n")
    }

    @Test
    fun `editing one line of dialogue leaves the speech around it intact`() {
        // The mixed state every real save is in: one block on the canonical path, its neighbours still
        // copying bytes. In a screenplay that is the dangerous case, because the single newlines
        // holding a speech together are in the *gaps* between spans rather than in any block, and a
        // serialiser that rebuilt them would end the speech at the edited line.
        val source = "INT. HOUSE - DAY\n\nSTEEL\n(quietly)\nSo much for retirement.\n\nCUT TO:\n"
        val document = parser.parse(source)
        val target = document.blocks.indexOfFirst { it.role == BlockRole.DIALOGUE }

        val edited =
            document.copy(
                blocks =
                    document.blocks.mapIndexed { index, block ->
                        // Keeps the original span: the line still occupies that region of the file.
                        if (index == target) {
                            Paragraph(listOf(Text("I am not.")), BlockRole.DIALOGUE, source = block.source)
                        } else {
                            block
                        }
                    },
            )

        assertEquals(
            "INT. HOUSE - DAY\n\nSTEEL\n(quietly)\nI am not.\n\nCUT TO:\n",
            serialiser.serialise(edited, source, rewritten = setOf(BlockIndex(target))),
            "Rewriting one line of dialogue disturbed the rest of the file",
        )
    }

    @Test
    fun `editing a slugline decides its forcing afresh`() {
        // The forcing character is not a property of the file, it is recomputed from the words -- so a
        // heading edited from one that announces itself to one that does not has to acquire a stop.
        val source = "INT. HOUSE - DAY\n\nShe sets down the lamp.\n"
        val document = parser.parse(source)

        val edited =
            document.copy(
                blocks =
                    listOf(
                        Paragraph(
                            listOf(Text("SNIPER SCOPE POV")),
                            BlockRole.SCENE_HEADING,
                            source = document.blocks.first().source,
                        ),
                    ) + document.blocks.drop(1),
            )

        assertEquals(
            ".SNIPER SCOPE POV\n\nShe sets down the lamp.\n",
            serialiser.serialise(edited, source, rewritten = setOf(BlockIndex(0))),
            "An edited slugline did not acquire the forcing stop it now needs",
        )
    }

    @Test
    fun `a line typed into a speech joins the speech`() {
        // An insertion rather than an edit: a block with no span was never in the file, so it has no
        // region to replace and the serialiser has to work out what to put in front of it. Inside a
        // speech the answer is one newline -- a blank line would end the speech and turn the new line
        // into action, which is the whole reason the separator is a decision rather than a constant.
        val source = "INT. HOUSE - DAY\n\nSTEEL\nSo much for retirement.\n"
        val document = parser.parse(source)

        val typed = document.copy(blocks = document.blocks + Paragraph(listOf(Text("I mean it.")), BlockRole.DIALOGUE))

        assertEquals(
            "INT. HOUSE - DAY\n\nSTEEL\nSo much for retirement.\nI mean it.\n",
            serialiser.serialise(typed, source),
            "A typed line of dialogue was not joined to the speech above it",
        )
    }

    @Test
    fun `a line typed after a speech is separated from it`() {
        val source = "INT. HOUSE - DAY\n\nSTEEL\nSo much for retirement.\n"
        val document = parser.parse(source)

        val typed = document.copy(blocks = document.blocks + Paragraph(listOf(Text("He stands.")), BlockRole.ACTION))

        assertEquals(
            "INT. HOUSE - DAY\n\nSTEEL\nSo much for retirement.\n\nHe stands.\n",
            serialiser.serialise(typed, source),
            "A typed line of action was not separated from the speech above it",
        )
    }

    @Test
    fun `canonical output reparses to the same elements`() {
        // The property the forcing logic exists for, and the only assertion here that would notice a
        // marker chosen wrongly rather than merely differently. Write the whole script from the IR,
        // read it back, and compare what each block turned out to be.
        reparses(FountainRoundTripFixtures.SCREENPLAY)
        reparses("!BANG\n\n.SNIPER SCOPE POV\n\n@McAVOY\nI said no.\n")
        reparses("STEEL\n(quietly)\nSo much for retirement.\n\nCUT TO:\n")

        // The ambiguous cases. A trailing space after the colon is a documented escape to action, and
        // `..` reserves the ellipsis -- so each of these is a line whose element depends on one
        // character, written back out and read again.
        reparses("CUT TO: \n")
        reparses("..and then she was gone.\n")
        reparses("A LOUD CRASH.\n")
    }

    /** Asserts that serialising without source and reparsing yields the same roles and the same words. */
    private fun reparses(source: String) {
        val first = parser.parse(source)
        val written = serialiser.serialise(first)
        val again = parser.parse(written)

        assertEquals(
            first.blocks.map { it.role to it.wordsOf() },
            again.blocks.map { it.role to it.wordsOf() },
            "Canonical output did not read back as the same script:\n$written",
        )
    }

    private fun assertWrites(
        expected: String,
        from: String,
    ) {
        assertEquals(expected, serialiser.serialise(parser.parse(from)), "Canonical output was wrong")
    }
}
