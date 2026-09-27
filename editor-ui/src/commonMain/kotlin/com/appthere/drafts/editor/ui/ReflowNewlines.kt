package com.appthere.drafts.editor.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/**
 * The glyph a soft line break is shown as in reveal state.
 *
 * U+21B5, dimmed. A plain space would reflow just as well and would hide from the author that they
 * hard-wrapped at all -- which matters to anyone who wraps deliberately so their diffs stay
 * readable, as this project's own Markdown does.
 */
private const val RETURN_MARK = '↵'

/** The source as reveal state displays it: one line, with the author's breaks marked. */
internal fun reflowedForDisplay(source: String): String = source.replace('\n', RETURN_MARK)

/**
 * Shows the author's line breaks as marks instead of breaking the line.
 *
 * **Why this exists.** `appthere-drafts.md` 4.2 requires that revealing a block does not move the
 * document, and prescribes reserving the taller of the two states. Measured on a real screenplay,
 * that reservation left *28% of the document's height empty*: 769 of its 1,860 blocks are
 * hard-wrapped in the source, so preview reflowed them to fewer lines than reveal and every one of
 * them reserved the difference, permanently, in both states.
 *
 * The same measurement found the cause was not the one 4.2 describes. Its stated worry is that
 * "markup characters are *added* in reveal state, [so] a paragraph near a wrap boundary can gain a
 * line" -- and across 26,000 words that happened to exactly zero blocks. All of the cost came from
 * the author's own newlines. Reflowing them takes the slack to zero rather than paying for it.
 *
 * **Why it is honest.** 4.1 defines reveal as showing *markup characters* -- the `*`, the `#`, the
 * backticks -- and every one of those is still shown. A newline inside a paragraph is not markup;
 * the parser already treats it as a space, which is exactly why preview reflows it. Marking it
 * rather than obeying it shows the author something true that preview does not.
 *
 * **Why it is cheap.** The substitution is one character for one character, so every offset is
 * unchanged and the mapping is the identity. That is the whole difficulty of a
 * [VisualTransformation] and the reason 4.3 rejected transforming the document as a whole:
 * "offset mapping between raw and transformed text becomes intractable with hidden markup". Here
 * nothing is hidden and nothing moves, so there is no mapping to get wrong.
 */
internal class ReflowNewlines(
    private val marker: Color,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        if (!text.text.contains('\n')) return TransformedText(text, OffsetMapping.Identity)

        val shown =
            buildAnnotatedString {
                append(reflowedForDisplay(text.text))
                text.text.forEachIndexed { index, character ->
                    if (character == '\n') addStyle(SpanStyle(color = marker), index, index + 1)
                }
            }

        return TransformedText(shown, OffsetMapping.Identity)
    }

    override fun equals(other: Any?): Boolean = other is ReflowNewlines && other.marker == marker

    override fun hashCode(): Int = marker.hashCode()
}
