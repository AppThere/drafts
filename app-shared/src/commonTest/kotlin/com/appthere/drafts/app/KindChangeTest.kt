package com.appthere.drafts.app

import com.appthere.drafts.core.model.BlockRole
import com.appthere.drafts.editor.engine.DocumentSession
import com.appthere.drafts.editor.ui.EditorState
import com.appthere.drafts.platform.files.DocumentRef
import com.appthere.drafts.platform.files.SessionIdentity
import com.appthere.drafts.platform.files.SessionRecord
import com.appthere.drafts.platform.files.SnapshotStore
import com.appthere.drafts.platform.files.SnapshotTrigger
import com.appthere.drafts.platform.intents.DocumentKind
import com.appthere.drafts.platform.windows.SessionList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * 7.4's choice of kind for an untitled document, carried to everything that records it.
 *
 * The session record, so the next launch restores what the reader chose; the keeper, so the next
 * autosave does not write the old kind back; and the kind the next new document starts as.
 */
class KindChangeTest {
    private val store = FakeDocumentStore(DocumentRef("/unused"), "")
    private val snapshots = SnapshotStore(store, "/sessions")
    private val sessions = SessionList(snapshots)
    private val settings = SettingsStore(store, "/settings")

    @Test
    fun `the session is restored as the kind chosen`() =
        runTest {
            val record = sessions.opened(SessionIdentity.untitled(kind = "markdown", displayName = "Untitled"))
            var shown: SessionRecord? = null

            KindChange(
                sessions,
                settings,
            ) { shown = it }.to(DocumentKind.Fountain, record, keeper = null, editor = blank())

            assertEquals("fountain", shown?.kind)
            assertEquals("fountain", sessions.restorable().single().kind)
        }

    @Test
    fun `the next autosave records the kind chosen`() =
        runTest {
            val identity = SessionIdentity.untitled(kind = "markdown", displayName = "Untitled")
            val record = sessions.opened(identity)
            val document = openUntitled(store, "INT. DOCK - NIGHT\n")
            val keeper = SnapshotKeeper(document, snapshots, identity)

            KindChange(sessions, settings) {}.to(DocumentKind.Fountain, record, keeper, document.editor)
            keeper.snapshotOn(SnapshotTrigger.FocusLost)

            assertEquals("fountain", snapshots.recordOf(identity.documentId)?.kind)
        }

    @Test
    fun `the open document is read again as the kind chosen`() =
        runTest {
            // 7.4: "choosing Fountain re-interprets the same text as Fountain". In Markdown a scene
            // heading is an ordinary paragraph; read as Fountain it is a scene heading.
            val record = sessions.opened(SessionIdentity.untitled(kind = "markdown", displayName = "Untitled"))
            val document = openUntitled(store, "INT. DOCK - NIGHT\n", record)
            val before = document.editor.text

            KindChange(sessions, settings) {}.to(DocumentKind.Fountain, record, keeper = null, editor = document.editor)

            assertEquals(before, document.editor.text)
            assertEquals(
                BlockRole.SCENE_HEADING,
                document.editor.blocks
                    .first()
                    .block.role,
            )
        }

    @Test
    fun `the next new document starts as the kind chosen`() =
        runTest {
            val record = sessions.opened(SessionIdentity.untitled(kind = "markdown", displayName = "Untitled"))

            KindChange(sessions, settings) {}.to(DocumentKind.Fountain, record, keeper = null, editor = blank())

            assertEquals(DocumentKind.Fountain, settings.kindForNew())
        }

    @Test
    fun `a document with a file keeps the kind its extension gives it`() =
        runTest {
            // 9.1: once there is a file, the extension says what it is.
            val record =
                sessions.opened(
                    SessionIdentity(
                        documentId = "note",
                        uri = "file:///note.md",
                        displayName = "note.md",
                        kind = "markdown",
                    ),
                )

            assertFailsWith<IllegalArgumentException> {
                KindChange(sessions, settings) {}.to(DocumentKind.Fountain, record, keeper = null, editor = blank())
            }
        }

    /** An editor for the tests that are about the records rather than the text. */
    private fun blank() = EditorState(DocumentSession(""))
}
