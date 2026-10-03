package com.appthere.drafts.core.parse.fountain

import com.appthere.drafts.core.model.Attributes
import com.appthere.drafts.core.model.Block
import com.appthere.drafts.core.model.BlockRole
import com.appthere.drafts.core.model.Paragraph
import com.appthere.drafts.core.model.SourceSpan
import com.appthere.drafts.core.model.Text

/*
 * Turning a span of the source into a block.
 *
 * Apart from the parser because none of this has to decide anything: the parser works out what a
 * run of lines is, and these put that decision into the IR. They are also where the one invariant
 * every block satisfies lives -- its source is the text it was made from, and its words are that
 * text less whatever marker announced it.
 */

/**
 * A paragraph whose words are [span] less its [marker], and whose source is all of [span].
 *
 * The two differ for every forced element: a forced scene heading's words are "SNIPER SCOPE POV"
 * and its source is ".SNIPER SCOPE POV", because "the period is not rendered" and the period is
 * still in the file.
 */
internal fun paragraph(
    source: String,
    span: SourceSpan,
    role: BlockRole,
    marker: Int = 0,
): Block =
    Paragraph(
        inlines = inlinesIn(source, SourceSpan.of(span.start.value + marker, span.endExclusive.value)),
        role = role,
        source = span,
    )

/** One block of a speech, which carries no marker and so is all words. */
internal fun spoken(
    source: String,
    span: SourceSpan,
    role: BlockRole,
    marks: Attributes,
): Block = Paragraph(inlines = inlinesIn(source, span), role = role, attrs = marks, source = span)

/** One lyric line, less its tilde: "Each line is its own lyric element; the `~` is not rendered." */
internal fun lyric(
    source: String,
    line: Line,
): Block = paragraph(source, line.span, BlockRole.LYRIC, forcingOf(line.text)?.markerLength ?: 0)

/**
 * The text of [span], unparsed.
 *
 * For the two blocks the inline pass must not touch: a boneyard, where "emphasis does not apply",
 * and anything else whose characters are to be shown exactly as typed.
 */
internal fun verbatim(
    source: String,
    span: SourceSpan,
): Text = Text(source.substring(span.start.value, span.endExclusive.value), span)
