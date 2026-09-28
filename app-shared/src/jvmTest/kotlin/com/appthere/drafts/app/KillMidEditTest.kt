package com.appthere.drafts.app

import com.appthere.drafts.platform.files.PathDocumentStore
import com.appthere.drafts.platform.files.Recovery
import com.appthere.drafts.platform.files.SnapshotStore
import com.appthere.drafts.platform.files.sha256
import kotlinx.coroutines.runBlocking
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.exists
import kotlin.io.path.writeText
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `IMPLEMENTATION-PLAN.md`, Phase 4: "Kill the process mid-edit at 100 random points; work is
 * recoverable every time."
 *
 * A real editing session in a real child JVM, really killed, and the work recovered through the
 * same 8.3 path the application uses on launch. `SnapshotKillTest` proves the write survives
 * SIGKILL; this proves an editing session does -- which is the claim a reader cares about, and it
 * fails in ways the write test cannot see: a keeper that captured the wrong text, or a snapshot
 * written from a document the editor had already moved past.
 *
 * The invariant is exact. Every state the document can legitimately be in is `x`\*n + victimBody for
 * some n >= 1, so anything else is a document that was torn.
 */
class KillMidEditTest {
    private val directory: Path = createTempDirectory("drafts-mid-edit")
    private val snapshots = SnapshotStore(PathDocumentStore(), directory.resolve("sessions").toString())

    @AfterTest
    fun clean() {
        directory.toFile().deleteRecursively()
    }

    @Test
    fun `work is recoverable after a hundred kills at random moments`() {
        val random = Random(SEED)
        var recovered = 0

        repeat(ATTEMPTS) { attempt ->
            val documentId = "victim-$attempt"
            killMidEdit(documentId, random.nextLong(MIN_RUN_MILLIS, MAX_RUN_MILLIS))

            val recovery = runBlocking { snapshots.examine(documentId, sha256(victimBody.encodeToByteArray())) }
            val text = (recovery as? Recovery.UnsavedWork)?.text

            assertTrue(text != null, "Attempt $attempt recovered nothing at all")
            val typed = text.takeWhile { it.toString() == KEYSTROKE }
            assertTrue(typed.isNotEmpty(), "Attempt $attempt recovered a document with no edits in it")
            assertEquals(
                victimBody,
                text.removePrefix(typed),
                "Attempt $attempt recovered ${text.length} characters that are not a whole document",
            )
            recovered += 1
        }

        assertEquals(ATTEMPTS, recovered)
    }

    /** Starts an editing session on its own copy of the document, lets it run, then SIGKILLs it. */
    private fun killMidEdit(
        documentId: String,
        runMillis: Long,
    ) {
        val document = directory.resolve("$documentId.md")
        document.writeText(victimBody)

        val process =
            ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp",
                System.getProperty("java.class.path"),
                VICTIM,
                directory.resolve("sessions").toString(),
                documentId,
                document.toString(),
            ).redirectErrorStream(true).start()

        try {
            // Kill during editing, not during JVM startup -- otherwise most attempts would be
            // testing how fast a JVM starts.
            val snapshot = Path.of(snapshots.snapshotOf(documentId).token)
            val deadline = System.currentTimeMillis() + STARTUP_BUDGET_MILLIS
            while (!snapshot.exists() && System.currentTimeMillis() < deadline && process.isAlive) {
                Thread.sleep(POLL_MILLIS)
            }
            assertTrue(snapshot.exists(), "The editing session never produced a first snapshot")

            Thread.sleep(runMillis)
        } finally {
            process.destroyForcibly()
            process.waitFor()
        }
    }

    private companion object {
        const val VICTIM = "com.appthere.drafts.app.EditingVictimKt"

        /** The plan's number. */
        const val ATTEMPTS = 100

        /** Fixed, so a failure can be reproduced rather than merely reported. */
        const val SEED = 20260927L

        const val MIN_RUN_MILLIS = 1L
        const val MAX_RUN_MILLIS = 80L
        const val STARTUP_BUDGET_MILLIS = 60_000L
        const val POLL_MILLIS = 5L
    }
}
