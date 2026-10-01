package com.appthere.drafts.app.android

import com.appthere.drafts.platform.files.PathDocumentStore
import com.appthere.drafts.platform.files.SessionIdentity
import com.appthere.drafts.platform.files.SnapshotStore
import com.appthere.drafts.platform.intents.DocumentKind
import com.appthere.drafts.platform.windows.SessionList
import kotlinx.coroutines.runBlocking
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What a launch opens on Android (`appthere-drafts.md` 7.3 and 7.4), against a real session
 * directory.
 *
 * The four cases the two sections separate, and the one that is easiest to get backwards: a launch
 * while the application is already running adds a document rather than restoring the ones that are
 * already on screen.
 */
class LaunchingTest {
    private val directory: Path = createTempDirectory("drafts-android-launch")
    private val sessions = SessionList(SnapshotStore(PathDocumentStore(), directory.resolve("sessions").toString()))

    @AfterTest
    fun clean() {
        directory.toFile().deleteRecursively()
    }

    @Test
    fun `a first launch opens one untitled document`() =
        runBlocking {
            // 7.4: "Launching with nothing to restore opens one untitled document, ready to type
            // into." Not the sample document, and not an empty screen.
            val opened = atLaunch()

            val only = opened.single()
            assertNull(only.uri, "The new document claimed a file")
            assertEquals(UNTITLED, only.displayName)
            assertEquals("markdown", only.kind)
        }

    @Test
    fun `the untitled document is the kind the reader last created`() =
        runBlocking {
            assertEquals("fountain", atLaunch(lastKind = DocumentKind.Fountain).single().kind)
        }

    @Test
    fun `sessions to restore are restored and nothing else opens`() =
        runBlocking {
            // 7.4: "a launch that always added a blank window would leave one to close every time".
            sessions.opened(SessionIdentity.untitled(kind = "markdown", displayName = "Chapter one"))

            val opened = atLaunch()

            assertEquals(listOf("Chapter one"), opened.map { it.displayName })
        }

    @Test
    fun `launching while already showing a document opens another untitled one`() =
        runBlocking {
            // 7.4: "the reader asked for the application again, and it is already showing
            // everything else they had open". Restoring here would shuffle Recents and open nothing.
            sessions.opened(SessionIdentity.untitled(kind = "markdown", displayName = "Chapter one"))

            val opened = atLaunch(showing = true)

            val only = opened.single()
            assertEquals(UNTITLED, only.displayName)
            assertNull(only.uri)
        }

    @Test
    fun `a shortcut opens its own kind whatever the reader last created`() =
        runBlocking {
            // 7.4's "New Fountain screenplay", from a launcher that was asked for a screenplay.
            val opened = atLaunch(requested = DocumentKind.Fountain, lastKind = DocumentKind.Markdown)

            assertEquals("fountain", opened.single().kind)
        }

    @Test
    fun `a shortcut on a cold launch opens beside whatever was restored`() =
        runBlocking {
            // The reader asked for a new screenplay and also has last week's work. Both.
            sessions.opened(SessionIdentity.untitled(kind = "markdown", displayName = "Chapter one"))

            val opened = atLaunch(requested = DocumentKind.Fountain)

            assertEquals(2, opened.size, "Opened ${opened.map { it.displayName }}")
            assertTrue(opened.any { it.displayName == "Chapter one" }, "The restored document was dropped")
            assertTrue(opened.any { it.kind == "fountain" }, "The shortcut's document was dropped")
        }

    @Test
    fun `every document opened has a session of its own`() =
        runBlocking {
            // A record is what DocumentActivity is handed and what the next launch restores from.
            // One without a snapshot path would be a document whose words have nowhere to go.
            val opened = atLaunch()

            assertTrue(opened.single().snapshotPath.isNotEmpty())
            assertEquals(opened.map { it.documentId }, sessions.restorable().map { it.documentId })
        }

    private suspend fun atLaunch(
        requested: DocumentKind? = null,
        showing: Boolean = false,
        lastKind: DocumentKind = DocumentKind.Markdown,
    ) = documentsAtLaunch(
        sessions = sessions,
        requested = requested,
        showing = showing,
        untitledName = UNTITLED,
        lastKind = lastKind,
    )

    private companion object {
        const val UNTITLED = "Untitled"
    }
}
