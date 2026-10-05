package com.appthere.drafts.editor.ui

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import com.appthere.drafts.core.model.SourceSpan

/**
 * A preview rendering, and where each part of it came from.
 *
 * Preview text is not the source text. Markup is hidden, decoration the author never typed is added
 * -- a bullet, a quote rule -- and escapes are resolved, so offset 12 in the preview is some other
 * offset entirely in the block's source. Everything that reaches outside a single block needs the
 * correspondence: a click has to become a caret, and a selection drawn over preview glyphs has to
 * become a range of source to copy.
 *
 * **Offsets are relative to the block**, matching [com.appthere.drafts.editor.engine.Caret], which
 * has always been block-relative. Storing document offsets meant the map went stale whenever an
 * edit above shifted the block -- so every block below the caret needed a new preview on every
 * keystroke, for a change that alters nothing it draws. Relative offsets simply do not notice the
 * block moving.
 *
 * The map is recorded while the preview is built rather than recovered afterwards by comparing the
 * two strings. Comparing would be a guess -- a plausible one, and wrong often enough to put the
 * caret a character out, which is precisely the kind of small wrongness that makes an editor feel
 * untrustworthy.
 *
 * Decoration the preview supplies has no source and is deliberately absent from the map. An offset
 * inside it resolves to the nearest real text, which is what a reader means when they click a
 * bullet.
 */
@Immutable
internal class BlockPreview(
    val text: AnnotatedString,
    private val runs: List<PreviewRun>,
) {
    /**
     * The same preview in capitals: 5.4's caps for a scene heading, a character and a transition.
     *
     * One character for one, so every offset into the preview still means what it meant -- the
     * selection layer and the caret map through them. `uppercaseChar` keeps that; `uppercase` does
     * not ("ß" becomes "SS").
     */
    fun inCaps(): BlockPreview =
        BlockPreview(
            AnnotatedString(
                CharArray(text.length) { text[it].uppercaseChar() }.concatToString(),
                text.spanStyles,
                text.paragraphStyles,
            ),
            runs,
        )

    /** How far into the block's source the character at [previewOffset] came from. */
    fun sourceOffsetAt(previewOffset: Int): Int? {
        if (runs.isEmpty()) return null

        val run = runs.lastOrNull { it.previewStart <= previewOffset } ?: runs.first()
        return run.sourceOffsetAt(previewOffset)
    }

    /** Where in the preview a block-relative source offset appears, so it can be drawn over. */
    fun previewOffsetAt(sourceOffset: Int): Int? {
        if (runs.isEmpty()) return null

        val run = runs.lastOrNull { it.sourceStart <= sourceOffset } ?: runs.first()
        return run.previewOffsetAt(sourceOffset)
    }

    /**
     * The preview's words without its decoration, for 10.1's spoken description.
     *
     * Decoration is what the preview draws that the document does not contain -- a quote's rule, a
     * list's bullets, a section break's line -- and a screen reader would otherwise read it out: "box
     * drawings light vertical" before every quotation. What is kept is every character a run maps to
     * the source. A decoration line break becomes a space, so two list items do not run together.
     */
    val spoken: String by lazy {
        val fromSource = BooleanArray(text.length)
        runs.forEach { run -> for (at in run.previewStart until run.previewEnd) fromSource[at] = true }

        val words =
            buildString {
                text.text.forEachIndexed { at, char ->
                    if (fromSource[at]) {
                        append(char)
                    } else if (char == '\n') {
                        append(' ')
                    }
                }
            }.replace(whitespace, " ").trim()

        words.ifEmpty { text.text }
    }

    /** The preview range covering a block-relative source range, clamped to this block. */
    fun previewRangeOf(
        sourceStart: Int,
        sourceEnd: Int,
    ): IntRange? {
        val start = previewOffsetAt(sourceStart)
        val end = previewOffsetAt(sourceEnd)

        return if (start == null || end == null) null else minOf(start, end)..maxOf(start, end)
    }
}

/**
 * One stretch of preview text that came from one stretch of source.
 *
 * The two lengths need not match: `\*` is two characters of source and one of preview, and an
 * escaped run drifts as it goes. Offsets are mapped proportionally within a run and clamped to it,
 * which keeps the map monotonic -- more important than exactness, because a map that goes backwards
 * would let a selection invert itself halfway through a drag.
 */
internal data class PreviewRun(
    val previewStart: Int,
    val previewEnd: Int,
    val sourceStart: Int,
    val sourceEnd: Int,
) {
    fun sourceOffsetAt(previewOffset: Int): Int {
        val into = (previewOffset - previewStart).coerceIn(0, previewEnd - previewStart)
        return (sourceStart + into).coerceIn(sourceStart, sourceEnd)
    }

    fun previewOffsetAt(sourceOffset: Int): Int {
        val into = (sourceOffset - sourceStart).coerceIn(0, sourceEnd - sourceStart)
        return (previewStart + into).coerceIn(previewStart, previewEnd)
    }
}

/**
 * Builds a preview and its map together.
 *
 * Wraps [AnnotatedString.Builder] rather than extending it so that every append has to say where
 * the text came from. Passing `null` is allowed and means "the preview invented this", which is a
 * claim the caller has to make on purpose.
 */
internal class PreviewBuilder(
    private val blockStart: Int,
) {
    private val out = AnnotatedString.Builder()
    private val runs = mutableListOf<PreviewRun>()

    fun append(
        value: String,
        span: SourceSpan?,
    ) {
        val start = out.length
        out.append(value)
        if (span != null) {
            runs +=
                PreviewRun(
                    previewStart = start,
                    previewEnd = out.length,
                    sourceStart = span.start.value - blockStart,
                    sourceEnd = span.endExclusive.value - blockStart,
                )
        }
    }

    /** Text the preview supplies itself: bullets, rules, the space between table cells. */
    fun decoration(value: String) = append(value, null)

    fun push(style: SpanStyle) = out.pushStyle(style)

    fun pop() = out.pop()

    inline fun styled(
        style: SpanStyle,
        body: () -> Unit,
    ) {
        push(style)
        body()
        pop()
    }

    fun build() = BlockPreview(out.toAnnotatedString(), runs.sortedBy { it.previewStart })
}

private val whitespace = Regex("""\s+""")
