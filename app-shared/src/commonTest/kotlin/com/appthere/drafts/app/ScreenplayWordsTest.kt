package com.appthere.drafts.app

import com.appthere.drafts.core.fountain.FountainKeywords
import com.appthere.drafts.core.fountain.KeywordPreset
import com.appthere.drafts.core.model.BlockRole
import com.appthere.drafts.platform.files.DocumentRef
import com.appthere.drafts.platform.files.SessionIdentity
import com.appthere.drafts.platform.files.SnapshotStore
import com.appthere.drafts.platform.intents.DocumentKind
import com.appthere.drafts.platform.windows.SessionList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 11.3's "per-document configurable prefix list and transition suffix": kept for each screenplay,
 * under its file, and read with it every way it opens.
 */
class ScreenplayWordsTest {
    private val store = FakeDocumentStore(DocumentRef("/unused"), "")
    private val settings = SettingsStore(store, "/settings")

    @Test
    fun `a screenplay with no words of its own is read in English`() =
        runTest {
            assertEquals(FountainKeywords.ENGLISH, settings.keywordsFor(file("/scripts/one.fountain")))
        }

    @Test
    fun `words chosen for one screenplay come back for it and not for another`() =
        runTest {
            assertTrue(settings.rememberKeywords(file("/scripts/one.fountain"), FRENCH))

            assertEquals(FRENCH, settings.keywordsFor(file("/scripts/one.fountain")))
            assertEquals(FountainKeywords.ENGLISH, settings.keywordsFor(file("/scripts/two.fountain")))
        }

    @Test
    fun `the words belong to the file whatever id the session had`() =
        runTest {
            // A screenplay saved from untitled keeps its untitled id while it is open, and is known by
            // its file when it is next opened. Both are the same file.
            settings.rememberKeywords(file("/scripts/one.fountain", id = "untitled-uuid"), FRENCH)

            assertEquals(FRENCH, settings.keywordsFor(file("/scripts/one.fountain", id = "from-the-path")))
        }

    @Test
    fun `save as carries an untitled screenplay's words to its file`() =
        runTest {
            val untitled = SessionIdentity.untitled(kind = "fountain", displayName = "Untitled")
            settings.rememberKeywords(untitled, FRENCH)

            val saved = file("/scripts/new.fountain", id = untitled.documentId)
            assertTrue(settings.keywordsMoved(untitled, saved))

            assertEquals(FRENCH, settings.keywordsFor(file("/scripts/new.fountain")))
        }

    @Test
    fun `save as with no words to carry writes nothing`() =
        runTest {
            store.failsToWrite = true

            assertTrue(settings.keywordsMoved(file("/a.fountain"), file("/b.fountain")))
        }

    @Test
    fun `words that could not be kept say so`() =
        runTest {
            store.failsToWrite = true

            assertFalse(settings.rememberKeywords(file("/scripts/one.fountain"), FRENCH))
        }

    @Test
    fun `a kept ending that is blank reads as English rather than as every line a transition`() =
        runTest {
            // A file can be edited by hand; what comes back is checked as typing is.
            settings.rememberKeywords(file("/scripts/one.fountain"), FountainKeywords(listOf("INT"), "TO:"))
            val kept = store.children(DocumentRef("/settings/screenplay-words")).single()
            store.changeOnDisk(kept, store.read(kept).text.replace("\"TO:\"", "\"  \""))

            assertEquals(FountainKeywords.ENGLISH, settings.keywordsFor(file("/scripts/one.fountain")))
        }

    @Test
    fun `an untitled screenplay opens with its own words`() =
        runTest {
            val document = openUntitled(store, SCRIPT, untitledRecord(), FRENCH)

            assertEquals(
                BlockRole.SCENE_HEADING,
                document.editor.blocks
                    .first()
                    .block.role,
            )
            assertEquals(FRENCH, document.editor.keywords)
        }

    @Test
    fun `prose has no words to read with`() {
        val document = openUntitled(store, SCRIPT)

        assertEquals(null, document.editor.keywords)
    }

    @Test
    fun `choosing Fountain for an untitled document reads it with its own words`() =
        runTest {
            val snapshots = SnapshotStore(store, "/sessions")
            val sessions = SessionList(snapshots)
            val record = sessions.opened(SessionIdentity.untitled(kind = "markdown", displayName = "Untitled"))
            settings.rememberKeywords(record.identity(), FRENCH)
            val document = openUntitled(store, SCRIPT, record)

            KindChange(sessions, settings) {}.to(DocumentKind.Fountain, record, keeper = null, editor = document.editor)

            assertEquals(
                BlockRole.SCENE_HEADING,
                document.editor.blocks
                    .first()
                    .block.role,
            )
        }

    private fun file(
        path: String,
        id: String = path,
    ) = SessionIdentity(
        documentId = id,
        uri = "file://$path",
        displayName = path.substringAfterLast('/'),
        kind = "fountain",
    )

    private suspend fun untitledRecord() =
        SessionList(SnapshotStore(store, "/sessions")).opened(
            SessionIdentity.untitled(kind = "fountain", displayName = "Untitled"),
        )

    private companion object {
        val FRENCH = KeywordPreset.ALL.single { it.name == "Français" }.keywords
        const val SCRIPT = "INTÉRIEUR CUISINE - JOUR\n\nElle entre.\n"
    }
}
