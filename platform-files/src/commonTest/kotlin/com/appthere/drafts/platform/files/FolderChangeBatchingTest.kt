package com.appthere.drafts.platform.files

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** `projects-core` design D8: one reconcile for a burst of changes, and none of them forgotten. */
class FolderChangeBatchingTest {
    @Test
    fun `a burst of changes is one signal`() =
        runTest {
            val signals =
                flow {
                    repeat(BURST) {
                        emit(FolderChange.Changed)
                        delay(QUIET / 10)
                    }
                }.batched(QUIET).toList()

            assertEquals(listOf(FolderChange.Changed), signals)
        }

    @Test
    fun `a loss anywhere in a burst makes the burst a loss`() =
        runTest {
            val signals =
                flow {
                    emit(FolderChange.Changed)
                    emit(FolderChange.Lost)
                    emit(FolderChange.Changed)
                }.batched(QUIET).toList()

            assertEquals(listOf(FolderChange.Lost), signals)
        }

    @Test
    fun `changes apart from one another are separate signals`() =
        runTest {
            val signals =
                flow {
                    emit(FolderChange.Changed)
                    delay(QUIET * 3)
                    emit(FolderChange.Changed)
                }.batched(QUIET).toList()

            assertEquals(listOf(FolderChange.Changed, FolderChange.Changed), signals)
        }

    private companion object {
        const val QUIET = 300L
        const val BURST = 20
    }
}
