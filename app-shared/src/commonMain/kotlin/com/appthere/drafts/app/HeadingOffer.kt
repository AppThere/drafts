package com.appthere.drafts.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import com.appthere.drafts.core.fountain.FountainKeywords
import com.appthere.drafts.core.fountain.unrecognisedHeadingWord
import com.appthere.drafts.core.model.BlockRole
import com.appthere.drafts.core.model.SourceSpan
import com.appthere.drafts.editor.engine.BlockId
import com.appthere.drafts.editor.ui.EditorState
import kotlinx.coroutines.launch

/**
 * 11.3: "the app should offer to insert [forcing characters] automatically when it detects an
 * unrecognised heading pattern."
 *
 * Offered for the line the caret has just left, not the one it is in: a heading half typed looks
 * like nothing yet, and an offer that came and went with each letter would be the interface
 * talking over the writer. Two answers, chosen with the product owner: force this line with `.`,
 * which keeps the file right in every Fountain tool, or add its first word to the screenplay's
 * heading words, which fixes every line like it at once. *Not now* is remembered for that line
 * while the window is open.
 */
@Stable
internal class HeadingOffer(
    private val editor: EditorState,
) {
    /** The line being offered, if it still looks like a heading. */
    var offered: BlockId? by mutableStateOf(null)
        private set

    private val declined = mutableSetOf<BlockId>()

    /** The word the offered line opens with, or null when there is nothing to offer. */
    val word: String? get() = offered?.let(::wordOf)

    /** The caret has left [block]. */
    fun left(block: BlockId) {
        if (block !in declined && wordOf(block) != null) offered = block
    }

    /** Forces the offered line as a scene heading with `.`, as one edit undo takes back. */
    fun mark() {
        val block = offered?.let(editor::blockOf) ?: return
        val start = block.source?.start?.value ?: return
        val indent = editor.sourceOf(block).takeWhile { it == ' ' || it == '\t' }.length

        editor.edit(SourceSpan.of(start + indent, start + indent), FORCE_SCENE_HEADING)
        offered = null
    }

    /** The word has been added to the screenplay's own, which answers the offer. */
    fun answered() {
        offered = null
    }

    fun notNow() {
        offered?.let(declined::add)
        offered = null
    }

    private fun wordOf(id: BlockId): String? {
        val keywords = editor.keywords
        val block = editor.blockOf(id)?.takeIf { it.role == BlockRole.ACTION }
        val line = block?.let { editor.sourceOf(it).trimEnd('\n') }?.takeIf { '\n' !in it }

        return if (keywords == null || line == null) null else unrecognisedHeadingWord(line, keywords)
    }

    private companion object {
        /** `fountain.md`: "**Forced:** prefix with a single period." */
        const val FORCE_SCENE_HEADING = "."
    }
}

/** Tells [offer] each time the caret leaves a block. */
@Composable
internal fun WatchHeadingOffer(
    editor: EditorState,
    offer: HeadingOffer,
) {
    LaunchedEffect(editor, offer) {
        var previous: BlockId? = null
        snapshotFlow { editor.caret?.block }.collect { current ->
            previous?.takeIf { it != current }?.let(offer::left)
            previous = current
        }
    }
}

/**
 * The offer, while there is a line to offer, and word that an added word could not be kept.
 *
 * Adding the word changes the screenplay's words through [keywordsChange], which reads it again and
 * keeps them. It applies either way; what can fail is keeping it for next time, and that is said.
 */
@Composable
internal fun HeadingOfferPrompt(
    editor: EditorState,
    keywordsChange: suspend (FountainKeywords) -> Boolean,
) {
    val scope = rememberCoroutineScope()
    // Keyed on the editor, which a reload from disk replaces.
    val offer = remember(editor) { HeadingOffer(editor) }
    var wordNotKept by remember(editor) { mutableStateOf(false) }
    WatchHeadingOffer(editor, offer)

    val word = offer.word
    val current = editor.keywords
    if (word != null && current != null) {
        HeadingOfferBanner(
            word = word,
            onMark = offer::mark,
            onAdd = {
                offer.answered()
                val words = current.copy(sceneHeadingPrefixes = current.sceneHeadingPrefixes + word)
                scope.launch { wordNotKept = !keywordsChange(words) }
            },
            onNotNow = offer::notNow,
        )
    }

    if (wordNotKept) WordsNotKeptBanner(onDismiss = { wordNotKept = false })
}
