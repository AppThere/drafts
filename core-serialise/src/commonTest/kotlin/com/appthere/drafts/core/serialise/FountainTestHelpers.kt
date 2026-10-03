package com.appthere.drafts.core.serialise

import com.appthere.drafts.core.model.Block
import com.appthere.drafts.core.model.Heading
import com.appthere.drafts.core.model.Paragraph
import com.appthere.drafts.core.model.plainText

/**
 * A block's words, with its markup flattened away.
 *
 * For comparing two parses of the same script: the question is whether the elements came back the
 * same, not whether the second parse recorded the same spans -- it will not, because the canonical
 * output is a different string from the source.
 */
internal fun Block.wordsOf(): String =
    when (this) {
        is Heading -> inlines.plainText()
        is Paragraph -> inlines.plainText()
        else -> ""
    }

/**
 * A page of screenplay with most of Fountain in it at once.
 *
 * Built from `fountain.md`'s own examples rather than invented, so that if the spec and these tests
 * disagree the disagreement is visible here rather than in someone's script. Shared because the two
 * questions asked of it are different ones: that the bytes come back unchanged, and that the words
 * come back meaning the same thing when the bytes cannot.
 */
internal object FountainRoundTripFixtures {
    val SCREENPLAY =
        """
        |Title: BRICK & STEEL
        |Credit: Written by
        |Author: Stu Maschwitz
        |
        |# Act I
        |
        |= Steel arrives at the pool and finds nobody home.
        |
        |EXT. BRICK'S PATIO - DAY #1#
        |
        |A gorgeous day. The sun is shining. But BRICK BRADDOCK is sitting
        |in a lawn chair, looking miserable.
        |
        |BRICK
        |(dejected)
        |I'm retired.
        |
        |STEEL
        |You're the only one who can do this.
        |
        |BRICK ^
        |No.
        |
        |                                        CUT TO:
        |
        |.SNIPER SCOPE POV
        |
        |Zooming in on the back of a head. [[this needs a better beat]]
        |
        |~Willy Wonka! Willy Wonka! The candy man's gone!
        |~Willy Wonka! Willy Wonka! And now it's time to run!
        |
        |!BANG
        |
        |> Burn to White.
        |
        |===
        |
        |## Sequence A
        |
        |She reads the last page, *slowly*, and then _again_.
        |
        |> THE END <
        |
        """.trimMargin()
}
