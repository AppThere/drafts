package com.appthere.drafts.app.desktop

import com.appthere.drafts.platform.files.PathDocumentStore
import com.appthere.drafts.platform.files.SnapshotStore
import com.appthere.drafts.platform.files.desktopIdentity
import com.appthere.drafts.platform.windows.SessionList
import kotlinx.coroutines.runBlocking
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * What a launch opens (`appthere-drafts.md` 7.3 and 7.4), against a real session directory.
 *
 * The three cases 7.4 separates: nothing to restore opens one untitled document; sessions to restore
 * are restored and nothing else opens; a document asked for opens alongside whatever was restored.
 */
class LaunchTest {
    private val directory: Path = createTempDirectory("drafts-launch")
    private val sessions = SessionList(SnapshotStore(PathDocumentStore(), directory.resolve("sessions").toString()))

    @AfterTest
    fun clean() {
        directory.toFile().deleteRecursively()
    }

    @Test
    fun `a first launch opens one untitled document`() =
        runBlocking {
            // 7.4: "Launching with nothing to restore opens one untitled document, ready to type
            // into." Not the sample, and not a window with no document in it.
            val opened = sessionsAtLaunch(sessions, path = null, untitledName = UNTITLED)

            val only = opened.single()
            assertNull(only.uri, "The new document claimed a file")
            assertEquals(UNTITLED, only.displayName)
            assertEquals("markdown", only.kind)
        }

    @Test
    fun `the untitled document is a session like any other`() =
        runBlocking {
            // Recorded as soon as it opens, so closing the application straight away still brings
            // it back -- its words, once there are any, live in its snapshot.
            val opened = sessionsAtLaunch(sessions, path = null, untitledName = UNTITLED).single()

            assertEquals(listOf(opened.documentId), sessions.restorable().map { it.documentId })
        }

    @Test
    fun `sessions to restore are restored and nothing else opens`() =
        runBlocking {
            // "A launch that always added a blank window would leave one to close every time."
            val chapter = file("chapter.md")
            sessions.opened(desktopIdentity(chapter.toString(), "markdown"))

            val opened = sessionsAtLaunch(sessions, path = null, untitledName = UNTITLED)

            assertEquals(listOf("chapter.md"), opened.map { it.displayName })
        }

    @Test
    fun `a document asked for opens alongside what was restored`() =
        runBlocking {
            val chapter = file("chapter.md")
            val scene = file("scene.fountain")
            sessions.opened(desktopIdentity(chapter.toString(), "markdown"))

            val opened = sessionsAtLaunch(sessions, path = scene.toString(), untitledName = UNTITLED)

            assertEquals(listOf("chapter.md", "scene.fountain"), opened.map { it.displayName })
            assertEquals("fountain", opened.last().kind)
        }

    @Test
    fun `a document asked for on a first launch opens without an untitled one beside it`() =
        runBlocking {
            val scene = file("scene.fountain")

            val opened = sessionsAtLaunch(sessions, path = scene.toString(), untitledName = UNTITLED)

            assertEquals(listOf("scene.fountain"), opened.map { it.displayName })
        }

    @Test
    fun `a file already open under another id is not opened twice`() =
        runBlocking {
            // A document saved from untitled keeps its UUID. Double-clicking its file must find
            // that window rather than open the same file in a second one.
            val draft = file("draft.md")
            val saved =
                desktopIdentity(draft.toString(), "markdown").copy(documentId = "6f1c2f7e-untitled-then-saved")
            val open = mutableListOf(sessions.opened(saved))

            open.show(draft.toString(), sessions)

            assertEquals(listOf(saved.documentId), open.map { it.documentId })
        }

    private fun file(name: String): Path = directory.resolve(name).also { it.writeText("Words.\n") }

    private companion object {
        const val UNTITLED = "Untitled"
    }
}
