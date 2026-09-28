package com.appthere.drafts.platform.files

import kotlinx.coroutines.runBlocking
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `IMPLEMENTATION-PLAN.md`, Phase 4: "Snapshot writes are atomic under `kill -9` during write",
 * and "Kill the process mid-edit at 100 random points; work is recoverable every time."
 *
 * A real child JVM, really killed. SIGKILL cannot be caught, deferred, or cleaned up after, so
 * nothing the program does on the way out can help it -- which is the point. The guarantee 8.1
 * claims belongs to the filesystem ("Rename is atomic on every target filesystem; a crash mid-write
 * leaves the previous snapshot intact"), and an in-process test can only ever show that the code
 * *intends* it.
 *
 * `PathDocumentStoreTest` has a concurrent-reader test that catches a torn write from the same
 * process. This is the stronger claim: torn by a process that stopped existing mid-syscall.
 */
class SnapshotKillTest {
    private val directory: Path = createTempDirectory("drafts-kill")
    private val snapshots = SnapshotStore(PathDocumentStore(), directory.toString())

    @AfterTest
    fun clean() {
        directory.toFile().deleteRecursively()
    }

    @Test
    fun `a snapshot survives the writer being killed at a hundred random moments`() {
        // The two texts differ in every byte, so anything that is neither is a torn write. Length
        // alone would not catch a rename that copied half a file over another of the same size.
        val whole = setOf(textOf('A'), textOf('B'))
        val random = Random(SEED)
        var recovered = 0

        repeat(ATTEMPTS) { attempt ->
            val documentId = "victim-$attempt"
            killMidWrite(documentId, random.nextLong(MIN_RUN_MILLIS, MAX_RUN_MILLIS))

            val text =
                snapshots
                    .snapshotOf(documentId)
                    .asPath()
                    .takeIf { it.exists() }
                    ?.readText()
            if (text != null) {
                assertTrue(
                    text in whole,
                    "Attempt $attempt left ${text.length} characters that are not a whole snapshot",
                )
                recovered += 1
            }
        }

        // Every attempt is killed after at least one write has landed, so every one should have
        // something to recover. A run where most attempts found nothing would satisfy the loop
        // above while proving nothing at all.
        assertEquals(ATTEMPTS, recovered, "Only $recovered of $ATTEMPTS kills left a snapshot behind")
    }

    @Test
    fun `the surviving snapshot is readable as unsaved work`() {
        // "work is recoverable every time" -- not merely present, but recoverable through the same
        // 8.3 path the application uses on launch.
        val documentId = "recoverable"
        killMidWrite(documentId, MAX_RUN_MILLIS)

        val recovery = runBlocking { snapshots.examine(documentId, sha256(ByteArray(0))) }

        val text = (recovery as? Recovery.UnsavedWork)?.text
        assertTrue(text == textOf('A') || text == textOf('B'), "Recovered ${text?.length} characters")
    }

    /** Starts a writer, lets it run for [runMillis], then SIGKILLs it. */
    private fun killMidWrite(
        documentId: String,
        runMillis: Long,
    ) {
        val process =
            ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp",
                System.getProperty("java.class.path"),
                VICTIM,
                directory.toString(),
                documentId,
            ).redirectErrorStream(true).start()

        try {
            // Wait for the first snapshot to land, so the kill is during a write rather than during
            // JVM startup -- otherwise most attempts would test nothing.
            val snapshot = snapshots.snapshotOf(documentId).asPath()
            val deadline = System.currentTimeMillis() + STARTUP_BUDGET_MILLIS
            while (!snapshot.exists() && System.currentTimeMillis() < deadline && process.isAlive) {
                Thread.sleep(POLL_MILLIS)
            }
            assertTrue(snapshot.exists(), "The writer never produced a first snapshot")

            Thread.sleep(runMillis)
        } finally {
            // destroyForcibly is SIGKILL on Unix, which is what the criterion says.
            process.destroyForcibly()
            process.waitFor()
        }
    }

    private fun DocumentRef.asPath(): Path = Path.of(token)

    private companion object {
        const val VICTIM = "com.appthere.drafts.platform.files.SnapshotVictimKt"

        /** The plan's number. */
        const val ATTEMPTS = 100

        /** Fixed, so a failure can be reproduced rather than merely reported. */
        const val SEED = 20260927L

        const val MIN_RUN_MILLIS = 1L
        const val MAX_RUN_MILLIS = 60L
        const val STARTUP_BUDGET_MILLIS = 30_000L
        const val POLL_MILLIS = 5L
    }
}
