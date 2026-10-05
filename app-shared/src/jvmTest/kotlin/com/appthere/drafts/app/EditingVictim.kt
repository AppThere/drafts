package com.appthere.drafts.app

import com.appthere.drafts.editor.engine.Caret
import com.appthere.drafts.editor.engine.DocumentSession
import com.appthere.drafts.editor.ui.EditorState
import com.appthere.drafts.editor.ui.replace
import com.appthere.drafts.platform.files.DocumentRef
import com.appthere.drafts.platform.files.DocumentSessionState
import com.appthere.drafts.platform.files.PathDocumentStore
import com.appthere.drafts.platform.files.SessionIdentity
import com.appthere.drafts.platform.files.SnapshotStore
import com.appthere.drafts.platform.files.SnapshotTrigger
import kotlinx.coroutines.runBlocking

/**
 * A process that edits a document and snapshots it, until something kills it.
 *
 * Run as a real child JVM by `KillMidEditTest`, for `IMPLEMENTATION-PLAN.md`'s Phase 4 criterion
 * "Kill the process mid-edit at 100 random points; work is recoverable every time."
 *
 * Every layer is the real one -- `DocumentSession`, `EditorState`, `SnapshotKeeper` -- because the
 * criterion is about an editing session being survivable, not about a file write in isolation.
 * `SnapshotKillTest` covers the write on its own; this covers the path that produces it.
 *
 * The edit is a single `x` at the head of the first block, so the document is always
 * `x`\*n + [victimBody] for some n. Anything else recovered is a document that was torn, and the reader
 * would have opened it to find their manuscript half-overwritten.
 */
fun main(args: Array<String>) {
    val sessions = args[0]
    val documentId = args[1]
    val path = args[2]

    val store = PathDocumentStore()
    val ref = DocumentRef(path)

    runBlocking {
        val contents = store.read(ref)
        val document =
            OpenDocument(
                store = store,
                editor = EditorState(DocumentSession(contents.text)),
                opened = DocumentSessionState.opened(ref, contents),
            )
        val keeper =
            SnapshotKeeper(
                document = document,
                snapshots = SnapshotStore(store, sessions),
                identity =
                    SessionIdentity(
                        documentId = documentId,
                        uri = "file://$path",
                        displayName = "victim.md",
                        kind = "markdown",
                    ),
            )

        while (true) {
            document.typeOneCharacter()
            keeper.edited(System.currentTimeMillis())
            keeper.snapshotOn(SnapshotTrigger.FocusLost, scrollOffset = 0)
        }
    }
}

/** One keystroke at the head of the document, through the editor's own edit path. */
private fun OpenDocument.typeOneCharacter() {
    val block = editor.blocks.first()
    editor.place(Caret(block.id, 0))
    editor.replace(requireNotNull(block.block.source), KEYSTROKE + editor.sourceOf(block.block), 1)
}

internal const val KEYSTROKE = "x"

/**
 * The rest of the document: large, so a snapshot takes long enough to be interrupted part-way, and
 * in many blocks, so the bounded incremental reparse is not re-parsing megabytes per keystroke.
 */
internal val victimBody: String =
    (1..BODY_PARAGRAPHS).joinToString("\n\n") {
        "Paragraph $it. ${"b".repeat(BODY_WIDTH)}"
    }

private const val BODY_PARAGRAPHS = 400
private const val BODY_WIDTH = 2_000
