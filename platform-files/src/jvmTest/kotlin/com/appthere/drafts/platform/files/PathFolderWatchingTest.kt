package com.appthere.drafts.platform.files

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteExisting
import kotlin.io.path.moveTo
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * `projects-core` design D8 on a real filesystem: one signal for one change, however many events the
 * platform raised for it, anywhere in the tree -- and nothing at all for `.git`.
 *
 * Real time, not the test dispatcher's: the events come from the operating system.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PathFolderWatchingTest {
    private val directory: Path = createTempDirectory("drafts-watch")
    private val scope = CoroutineScope(Dispatchers.Default)

    @AfterTest
    fun cleanUp() {
        scope.cancel()
        directory.toFile().deleteRecursively()
    }

    @Test
    fun `a file created in a nested folder is one signal`() =
        watching(prepare = { directory.resolve("Act 1/Scenes").createDirectories() }) { signals ->
            directory.resolve("Act 1/Scenes/dock.fountain").writeText("INT. DOCK - NIGHT\n")

            assertOneSignal(signals)
        }

    @Test
    fun `a file deleted in a nested folder is one signal`() =
        watching(prepare = {
            directory.resolve("Notes").createDirectories()
            directory.resolve("Notes/premise.md").writeText("Premise.\n")
        }) { signals ->
            directory.resolve("Notes/premise.md").deleteExisting()

            assertOneSignal(signals)
        }

    @Test
    fun `a rename, which the platform reports twice, is one signal`() =
        watching(prepare = {
            directory.resolve("Screenplay").createDirectories()
            directory.resolve("Screenplay/the-dock.fountain").writeText("INT. DOCK - NIGHT\n")
        }) { signals ->
            directory.resolve("Screenplay/the-dock.fountain").moveTo(directory.resolve("Screenplay/02-dock.fountain"))

            assertOneSignal(signals)
        }

    @Test
    fun `a folder that appears is followed`() =
        watching { signals ->
            directory.resolve("Beats").createDirectories()
            assertOneSignal(signals)

            directory.resolve("Beats/one.md").writeText("Beat.\n")
            assertOneSignal(signals)
        }

    @Test
    fun `a change inside git is not a change to the project`() =
        watching(prepare = { directory.resolve(".git/objects").createDirectories() }) { signals ->
            directory.resolve(".git/objects/ab").writeText("blob")

            assertNull(withTimeoutOrNull(SETTLE) { signals.receive() })
        }

    @Test
    fun `a change to project toml is a change`() =
        watching(prepare = { directory.resolve(".drafts").createDirectories() }) { signals ->
            directory.resolve(".drafts/project.toml").writeText("[project]\n")

            assertOneSignal(signals)
        }

    private fun watching(
        prepare: () -> Unit = {},
        test: suspend (ReceiveChannel<FolderChange>) -> Unit,
    ) = runBlocking {
        prepare()
        val signals = PathFolderWatching().changes(DocumentRef(directory.toString())).produceIn(scope)
        // Registration happens as collection starts; give it a moment before changing anything.
        delay(REGISTERED)
        test(signals)
        signals.cancel()
    }

    /** One signal arrives, and no second one follows it. */
    private suspend fun assertOneSignal(signals: ReceiveChannel<FolderChange>) {
        assertEquals(FolderChange.Changed, withTimeoutOrNull(ARRIVES) { signals.receive() })
        assertNull(withTimeoutOrNull(SETTLE) { signals.receive() }, "A second signal followed the first")
    }

    private companion object {
        const val REGISTERED = 300L
        const val ARRIVES = 5_000L
        const val SETTLE = 1_000L
    }
}
