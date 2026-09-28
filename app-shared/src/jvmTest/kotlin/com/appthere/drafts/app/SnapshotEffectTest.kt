package com.appthere.drafts.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import com.appthere.drafts.editor.engine.DocumentSession
import com.appthere.drafts.editor.ui.EditorState
import com.appthere.drafts.platform.files.DocumentRef
import com.appthere.drafts.platform.files.DocumentSessionState
import com.appthere.drafts.platform.files.PathDocumentStore
import com.appthere.drafts.platform.files.SessionIdentity
import com.appthere.drafts.platform.files.SnapshotSchedule
import com.appthere.drafts.platform.files.SnapshotStore
import kotlinx.coroutines.runBlocking
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The glue that makes 8.1 actually run.
 *
 * `SnapshotScheduleTest` proves the timing and `SnapshotKeeperTest` proves the capture. Neither can
 * tell whether anything ever calls them -- an effect that never fired would leave every unit test
 * green and no snapshot on disk, which is the whole feature silently absent.
 *
 * Time is injected and driven by the test clock. With a real monotonic source, `delay` would obey
 * the virtual clock while the deadline arithmetic obeyed the wall clock, the deadline would never
 * arrive, and the test would prove only that the two disagree.
 */
@OptIn(ExperimentalTestApi::class)
class SnapshotEffectTest {
    private val directory: Path = createTempDirectory("drafts-effect")
    private val store = PathDocumentStore()

    @AfterTest
    fun clean() {
        directory.toFile().deleteRecursively()
    }

    @Test
    fun `an edit followed by an idle pause writes a snapshot`() {
        // 8.1's "3 seconds of idle after an edit", from a keystroke through to bytes on disk.
        runSkikoComposeUiTest {
            val keeper = keeper()
            var revision by mutableIntStateOf(0)
            setContent {
                SnapshotEffect(keeper, revision, scrollOffset = { SCROLL }, now = { mainClock.currentTime })
            }

            revision = 1
            mainClock.advanceTimeBy(SnapshotSchedule.IDLE_AFTER_MILLIS + FRAME)
            // The write lands on a real IO thread, so the condition is the thing to wait for --
            // `waitForIdle` only settles the composition, which finished the moment it launched it.
            waitUntil(timeoutMillis = TIMEOUT) { snapshot().exists() }

            assertEquals(TEXT, snapshot().readText())
        }
    }

    @Test
    fun `nothing is written before the idle pause has elapsed`() {
        // The deadline has to bite. An effect that snapshotted on every edit would write on every
        // keystroke, which on a long document is the editor stuttering once per character.
        runSkikoComposeUiTest {
            val keeper = keeper()
            var revision by mutableIntStateOf(0)
            setContent {
                SnapshotEffect(keeper, revision, scrollOffset = { SCROLL }, now = { mainClock.currentTime })
            }

            revision = 1
            mainClock.advanceTimeBy(SnapshotSchedule.IDLE_AFTER_MILLIS / 2)
            waitForIdle()

            assertFalse(snapshot().exists(), "A snapshot was written before the reader had paused")
        }
    }

    @Test
    fun `a document nobody edited is never snapshotted`() {
        // Opening a file is not an edit. Revision zero is the document as it came off disk, and
        // snapshotting it would put a copy of every file the reader opens into app-private storage.
        runSkikoComposeUiTest {
            val keeper = keeper()
            setContent {
                SnapshotEffect(keeper, revision = 0, scrollOffset = { SCROLL }, now = { mainClock.currentTime })
            }

            mainClock.advanceTimeBy(SnapshotSchedule.CONTINUOUS_AFTER_MILLIS * 2)
            waitForIdle()

            assertFalse(snapshot().exists())
        }
    }

    @Test
    fun `continuous typing is snapshotted without ever pausing`() {
        // 8.1's second timed rule, which the idle rule cannot reach: an edit arriving every second
        // means the three-second deadline is never met, and without this the work would sit
        // uncaptured for as long as the reader keeps going.
        runSkikoComposeUiTest {
            val keeper = keeper()
            var revision by mutableIntStateOf(0)
            setContent {
                SnapshotEffect(keeper, revision, scrollOffset = { SCROLL }, now = { mainClock.currentTime })
            }

            repeat(KEYSTROKES) {
                revision += 1
                mainClock.advanceTimeBy(BETWEEN_KEYSTROKES)
                waitForIdle()
            }
            waitUntil(timeoutMillis = TIMEOUT) { snapshot().exists() }

            assertTrue(snapshot().exists(), "Continuous typing was never snapshotted")
        }
    }

    @Test
    fun `the snapshot never reaches the document it came from`() {
        // 8.1's first sentence, through the real store and a real directory.
        runSkikoComposeUiTest {
            val keeper = keeper()
            var revision by mutableIntStateOf(0)
            setContent {
                SnapshotEffect(keeper, revision, scrollOffset = { SCROLL }, now = { mainClock.currentTime })
            }

            revision = 1
            mainClock.advanceTimeBy(SnapshotSchedule.IDLE_AFTER_MILLIS + FRAME)
            waitUntil(timeoutMillis = TIMEOUT) { snapshot().exists() }

            assertEquals(TEXT, document().readText(), "Autosave wrote to the reader's own file")
        }
    }

    private fun keeper(): SnapshotKeeper {
        document().writeText(TEXT)
        val ref = DocumentRef(document().toString())
        val open =
            runBlocking {
                val contents = store.read(ref)
                OpenDocument(
                    store,
                    EditorState(DocumentSession(contents.text)),
                    DocumentSessionState.opened(ref, contents),
                )
            }
        return SnapshotKeeper(open, SnapshotStore(store, sessions().toString()), IDENTITY)
    }

    private fun document(): Path = directory.resolve("chapter.md")

    private fun sessions(): Path = directory.resolve("sessions")

    private fun snapshot(): Path = sessions().resolve("$ID/snapshot.md")

    private companion object {
        const val ID = "chapter"
        const val TEXT = "As opened.\n"
        const val SCROLL = 8_123
        const val FRAME = 32L
        const val TIMEOUT = 5_000L
        const val KEYSTROKES = 40
        const val BETWEEN_KEYSTROKES = 1_000L
        val IDENTITY =
            SessionIdentity(
                documentId = ID,
                uri = "file:///chapter.md",
                displayName = "chapter.md",
                kind = "markdown",
            )
    }
}
