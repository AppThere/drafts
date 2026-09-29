package com.appthere.drafts.platform.files

import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 7.3's desktop half: where app-private data lives, and what an access token is worth.
 *
 * "**Desktop:** absolute path, with existence re-checked on restore." The re-check is the whole of
 * it -- a path is not a permission, it is a guess that survived a restart, and between sessions
 * files get moved, renamed and deleted.
 */
class DesktopStorageTest {
    private val directory: Path = createTempDirectory("drafts-desktop")

    @AfterTest
    fun clean() {
        directory.toFile().deleteRecursively()
    }

    @Test
    fun `linux data goes under the XDG directory`() {
        val root = desktopDataRoot(home = "/home/writer", os = "Linux", xdg = null)

        assertEquals("/home/writer/.local/share/AppThere/Drafts", root)
    }

    @Test
    fun `an explicit XDG_DATA_HOME is honoured`() {
        // A reader who has moved their data directory has said where they want this. Ignoring it
        // would put snapshots outside whatever their backup covers.
        val root = desktopDataRoot(home = "/home/writer", os = "Linux", xdg = "/data/writer")

        assertEquals("/data/writer/AppThere/Drafts", root)
    }

    @Test
    fun `macOS data goes to Application Support`() {
        // Where a Mac's backup looks and where an uninstaller cleans up. A directory invented next
        // to the executable is in neither.
        val root = desktopDataRoot(home = "/Users/writer", os = "Mac OS X", xdg = null)

        assertEquals("/Users/writer/Library/Application Support/AppThere/Drafts", root)
    }

    @Test
    fun `the token of an existing file resolves`() {
        val file = directory.resolve("chapter.md")
        file.writeText("A chapter.\n")

        assertEquals(file.toString(), resolveDesktopToken(file.toString())?.token)
    }

    @Test
    fun `the token of a file that has gone resolves to nothing`() {
        // 7.3's re-check. The session file survived; the document did not. What happens next is
        // 7.3's business -- "opens read-only from its snapshot" -- and this is how it finds out.
        assertNull(resolveDesktopToken(directory.resolve("deleted.md").toString()))
    }

    @Test
    fun `a relative path is recorded as an absolute one`() {
        // A session recorded from one working directory has to resolve from another on the next
        // launch, and nothing guarantees the application starts where it started last time.
        val identity = desktopIdentity("chapter.md", "markdown")

        assertTrue(identity.accessToken.orEmpty().startsWith("/"), "Token was ${identity.accessToken}")
    }

    @Test
    fun `two different documents get different ids`() {
        assertNotEquals(
            desktopIdentity("/documents/one.md", "markdown").documentId,
            desktopIdentity("/documents/two.md", "markdown").documentId,
        )
    }

    @Test
    fun `the same document gets the same id every launch`() {
        // The id is how a snapshot is found again after a restart. If it varied, every session
        // would create a new directory and 8.3 would never find the work it saved.
        assertEquals(
            desktopIdentity("/documents/one.md", "markdown").documentId,
            desktopIdentity("/documents/./one.md", "markdown").documentId,
        )
    }

    @Test
    fun `the kind it is told is the kind it records`() {
        // Recognising the kind is 9.1's table in `:platform-intents`; this module only records
        // what it is handed. It used to guess, and guessed `.spmd` wrong.
        assertEquals("fountain", desktopIdentity("/documents/big-fish.fountain", "fountain").kind)
        assertEquals("markdown", desktopIdentity("/documents/chapter.md", "markdown").kind)
    }

    @Test
    fun `the single-instance socket lives in the user's runtime directory`() {
        // 9.4, one instance per user: XDG_RUNTIME_DIR is the user's own and is cleared at logout.
        assertEquals(
            "/run/user/1000/appthere-drafts.sock",
            desktopInstanceAddress(runtime = "/run/user/1000", dataRoot = "/unused"),
        )
    }

    @Test
    fun `without a runtime directory it lives in the user's own data`() {
        // macOS, Windows, and a Linux session with no runtime directory.
        assertEquals(
            "/home/writer/.local/share/AppThere/Drafts/appthere-drafts.sock",
            desktopInstanceAddress(runtime = null, dataRoot = "/home/writer/.local/share/AppThere/Drafts"),
        )
    }

    @Test
    fun `the socket's path fits the limit a unix socket has`() {
        // About a hundred bytes on Linux and macOS. The usual locations must fit with room to spare.
        val linux = desktopInstanceAddress(runtime = "/run/user/1000", dataRoot = "/unused")
        val mac =
            desktopInstanceAddress(
                runtime = null,
                dataRoot = desktopDataRoot(home = "/Users/a.writer.with.a.long.name", os = "Mac OS X", xdg = null),
            )

        assertTrue(linux.length < SOCKET_PATH_LIMIT, linux)
        assertTrue(mac.length < SOCKET_PATH_LIMIT, mac)
    }
}

/** A Unix-domain socket path is limited to about a hundred bytes on Linux and macOS. */
private const val SOCKET_PATH_LIMIT = 100
