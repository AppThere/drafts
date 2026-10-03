package com.appthere.drafts.core.parse.fountain

import com.appthere.drafts.core.fountain.forcingOf
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

/**
 * One centred line, less both of its brackets.
 *
 * "Bracketed by `>` and `<`. Whitespace inside is trimmed." Both brackets are markers, so neither is
 * part of the words -- and the trim is not cosmetic here: centring is measured from the text's own
 * width (5.4), so a trailing space nobody can see would shift the line off centre.
 *
 * One block per line, as with lyrics, because centring is a property of a line rather than of a
 * block of them. Each bracket is looked for on the line it is on instead of being taken from the
 * chunk's first line, so a second `> ... <` under the first is centred in its own right and a line
 * that happens to lack a bracket keeps the characters it does have.
 */
internal fun centred(
    source: String,
    line: Line,
): Block {
    val text = line.text
    val lead = text.takeWhile { it.isWhitespace() }.length
    var from = lead + if (text.startsWith(">", lead)) 1 else 0
    var to = text.trimEnd().length - if (text.trimEnd().endsWith("<")) 1 else 0

    while (from < to && text[from].isWhitespace()) from++
    while (to > from && text[to - 1].isWhitespace()) to--

    return Paragraph(
        inlines = inlinesIn(source, SourceSpan.of(line.start + from, line.start + to)),
        role = BlockRole.CENTERED,
        source = line.span,
    )
}
