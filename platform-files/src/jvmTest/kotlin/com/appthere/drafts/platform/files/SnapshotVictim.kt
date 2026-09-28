package com.appthere.drafts.platform.files

import kotlinx.coroutines.runBlocking

/**
 * A process that writes snapshots until something kills it.
 *
 * Run as a real child JVM by `SnapshotKillTest`, which is the only way to test what
 * `IMPLEMENTATION-PLAN.md` asks for: "Snapshot writes are atomic under `kill -9` during write."
 * SIGKILL cannot be caught, deferred or cleaned up after, so the guarantee has to come from the
 * filesystem rather than from anything this program does on the way out -- and nothing short of
 * actually being killed proves that.
 *
 * It alternates between two texts that differ in every byte, so a torn write is recognisable: a
 * file that is neither of them is one the reader would have lost.
 */
fun main(args: Array<String>) {
    val root = args[0]
    val documentId = args[1]
    val snapshots = SnapshotStore(PathDocumentStore(), root)

    runBlocking {
        var round = 0
        while (true) {
            val text = if (round % 2 == 0) textOf('A') else textOf('B')
            snapshots.write(recordFor(documentId, round, snapshots), text)
            round += 1
        }
    }
}

/** Big enough that a write takes long enough to be interrupted part-way through. */
internal fun textOf(letter: Char): String = "$letter".repeat(SNAPSHOT_CHARACTERS) + "\n"

private fun recordFor(
    documentId: String,
    round: Int,
    snapshots: SnapshotStore,
) = SessionRecord(
    documentId = documentId,
    uri = "file:///victim.md",
    displayName = "victim.md",
    kind = "markdown",
    caret = CaretRecord(blockIndex = round, offset = 0),
    scrollOffset = round,
    baseDigest = sha256(ByteArray(0)).toString(),
    snapshotPath = snapshots.snapshotOf(documentId).token,
)

internal const val SNAPSHOT_CHARACTERS = 4 * 1024 * 1024
