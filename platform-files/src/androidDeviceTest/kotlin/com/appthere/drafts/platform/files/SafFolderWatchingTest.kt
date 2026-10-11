package com.appthere.drafts.platform.files

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * `projects-core` task 3.2: a provider's announcements become one signal per change, against
 * [TestDocumentsProvider], which announces as Android's own storage provider does.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SafFolderWatchingTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val listing = SafFolderListing(context)
    private val editing = SafFolderEditing(context, listing)
    private val tree = DocumentRef(TestDocumentsProvider.tree.toString())
    private val scope = CoroutineScope(Dispatchers.Default)

    @BeforeTest
    fun setUp() {
        TestDocumentsProvider.reset(context)
    }

    @AfterTest
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun aFileCreatedInTheFolderIsOneSignal() =
        watching { signals ->
            assertIs<FolderOutcome.Done>(editing.createFile(tree, "scene.md"))

            assertOneSignal(signals)
        }

    @Test
    fun aChangeInANestedFolderIsOneSignal() =
        watching { signals ->
            val act = assertIs<FolderOutcome.Done>(editing.createFolder(tree, "Act 1")).ref
            assertOneSignal(signals)

            assertIs<FolderOutcome.Done>(editing.createFile(act, "dock.fountain"))
            assertOneSignal(signals)
        }

    @Test
    fun aRenameIsOneSignal() =
        watching { signals ->
            val scene = assertIs<FolderOutcome.Done>(editing.createFile(tree, "the-dock.fountain")).ref
            assertOneSignal(signals)

            assertIs<FolderOutcome.Done>(editing.rename(scene, "02-the-dock.fountain"))
            assertOneSignal(signals)
        }

    /** On a real-time dispatcher: the announcements come from the system, not the test's clock. */
    private fun watching(test: suspend (ReceiveChannel<FolderChange>) -> Unit) =
        runTest {
            withContext(Dispatchers.Default) {
                val signals = SafFolderWatching(context).changes(tree).produceIn(scope)
                delay(REGISTERED)
                test(signals)
                signals.cancel()
            }
        }

    private suspend fun assertOneSignal(signals: ReceiveChannel<FolderChange>) {
        assertEquals(FolderChange.Changed, assertNotNull(withTimeoutOrNull(ARRIVES) { signals.receive() }))
        assertNull(withTimeoutOrNull(SETTLE) { signals.receive() }, "A second signal followed the first")
    }

    private companion object {
        const val REGISTERED = 300L
        const val ARRIVES = 5_000L
        const val SETTLE = 1_000L
    }
}
