package com.appthere.drafts.platform.files

import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import kotlin.io.path.createTempDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The desktop store against a real directory.
 *
 * Real files rather than an in-memory filesystem: the guarantees under test are the filesystem's
 * own -- that a rename replaces in one step, that fsync means the bytes are down -- and a fake
 * would be asserting that the fake behaves the way it was written to behave.
 */
class PathDocumentStoreTest {
    private val directory: Path = createTempDirectory("drafts-store")
    private val store = PathDocumentStore()

    @AfterTest
    fun clean() {
        directory.toFile().deleteRecursively()
    }

    @Test
    fun `a file that is there exists even when it cannot be read`() =
        runTest {
            // 7.3: a vanished file and one that cannot be read need different answers, and the
            // file's facts cannot tell them apart -- an unreadable file has none.
            val locked = directory.resolve("locked.md").also { it.writeText("Words.\n") }
            Files.setPosixFilePermissions(locked, PosixFilePermissions.fromString("---------"))

            try {
                assertTrue(store.exists(DocumentRef(locked.toString())), "An unreadable file was reported gone")
                assertNull(store.facts(DocumentRef(locked.toString())))
            } finally {
                Files.setPosixFilePermissions(locked, PosixFilePermissions.fromString("rw-r--r--"))
            }
        }

    @Test
    fun `a file that is gone does not exist`() =
        runTest {
            assertFalse(store.exists(DocumentRef(directory.resolve("never.md").toString())))
        }

    @Test
    fun `a written document reads back as it was written`() =
        runTest {
            val ref = ref("note.md")

            val written = store.writeAtomically(ref, "# Chapter\n\nA line.\n")

            assertIs<WriteOutcome.Written>(written)
            assertEquals("# Chapter\n\nA line.\n", store.read(ref).text)
        }

    @Test
    fun `the recorded facts describe what actually landed on disk`() =
        runTest {
            // 8.2 keeps `baseDigest` from the write so the next save can compare against it. If the
            // returned digest described the old contents, every save would be followed by a phantom
            // conflict.
            val ref = ref("note.md")

            val written = assertIs<WriteOutcome.Written>(store.writeAtomically(ref, TEXT))

            assertEquals(sha256(TEXT.encodeToByteArray()), written.facts.digest)
            assertEquals(store.read(ref).facts.digest, written.facts.digest)
            assertEquals(TEXT.encodeToByteArray().size.toLong(), written.facts.size)
        }

    @Test
    fun `a file changed on disk is refused rather than overwritten`() =
        runTest {
            // The rule 8.2 exists for. Another editor -- or a sync client -- has rewritten the file
            // since it was opened, and writing now would destroy that edit without ever showing it.
            val ref = ref("shared.md")
            val opened = assertIs<WriteOutcome.Written>(store.writeAtomically(ref, "As opened.\n"))
            path("shared.md").writeText("Someone else's edit.\n")

            val outcome = store.writeIfUnchanged(ref, "My edit.\n", expected = opened.facts.digest)

            assertIs<WriteOutcome.Conflict>(outcome)
            assertEquals("Someone else's edit.\n", path("shared.md").readText())
        }

    @Test
    fun `the refusal names both digests so the reader can be shown a difference`() =
        runTest {
            // 8.2 offers "[ Show differences ]". That needs to know which two versions differ, and the
            // store is the only thing that saw them both.
            val ref = ref("shared.md")
            val opened = assertIs<WriteOutcome.Written>(store.writeAtomically(ref, "As opened.\n"))
            path("shared.md").writeText("Changed.\n")

            val conflict = assertIs<WriteOutcome.Conflict>(store.writeIfUnchanged(ref, "Mine.\n", opened.facts.digest))

            assertEquals(opened.facts.digest, conflict.expected)
            assertEquals(sha256("Changed.\n".encodeToByteArray()), conflict.found)
        }

    @Test
    fun `an unchanged file is written and the digest moves on`() =
        runTest {
            // The other half: the check has to let real saves through, or the app can never write at
            // all. And 8.2 says the write "then updates `baseDigest` to the newly written content".
            val ref = ref("mine.md")
            val opened = assertIs<WriteOutcome.Written>(store.writeAtomically(ref, "First.\n"))

            val saved = assertIs<WriteOutcome.Written>(store.writeIfUnchanged(ref, "Second.\n", opened.facts.digest))

            assertEquals("Second.\n", path("mine.md").readText())
            assertEquals(sha256("Second.\n".encodeToByteArray()), saved.facts.digest)
        }

    @Test
    fun `a deleted file is reported as unavailable rather than recreated`() =
        runTest {
            // 8.4's `orphaned`. Silently recreating the file would hide that the reader moved or
            // deleted it, and would resurrect a document they may have meant to be gone.
            val ref = ref("gone.md")
            val opened = assertIs<WriteOutcome.Written>(store.writeAtomically(ref, TEXT))
            Files.delete(path("gone.md"))

            val outcome =
                assertIs<WriteOutcome.Unavailable>(store.writeIfUnchanged(ref, "Back.\n", opened.facts.digest))
            assertEquals(WriteOutcome.Reason.Missing, outcome.reason)
            assertFalse(Files.exists(path("gone.md")))
        }

    @Test
    fun `a successful write leaves no temporary file behind`() =
        runTest {
            // The temp file is an implementation detail of the atomic write. One left in the reader's
            // documents folder is visible litter, and one left in a project directory would be picked
            // up as a document by Phase 7's binder.
            val ref = ref("note.md")

            store.writeAtomically(ref, TEXT)
            store.writeAtomically(ref, "$TEXT again\n")

            assertEquals(listOf("note.md"), directory.listDirectoryEntries().map { it.fileName.toString() }.sorted())
        }

    @Test
    fun `a concurrent reader never sees a half-written document`() =
        runTest {
            // What "atomic" means in practice, tested as the property rather than the mechanism. A
            // reader -- the reader here being a sync client, a backup tool, or the same document open
            // elsewhere -- polls the file while it is rewritten from a large document to a different
            // large one. Every observation must be one whole version or the other.
            val ref = ref("busy.md")
            val before = "before\n".repeat(LINES)
            val after = "after\n".repeat(LINES)
            store.writeAtomically(ref, before)

            val seen = mutableSetOf<String>()
            val watcher =
                Thread {
                    repeat(OBSERVATIONS) {
                        runCatching { path("busy.md").readText() }.onSuccess { seen += it }
                    }
                }
            watcher.start()
            repeat(REWRITES) { round -> store.writeAtomically(ref, if (round % 2 == 0) after else before) }
            watcher.join()

            val torn = seen - setOf(before, after)
            assertTrue(torn.isEmpty(), "Saw ${torn.size} partial reads, of lengths ${torn.map { it.length }}")
            assertTrue(seen.isNotEmpty(), "The watcher never read the file, so it proved nothing")
        }

    @Test
    fun `a read-only file is reported as read-only when opened`() =
        runTest {
            // 8.4 decides `readOnly` at open, so the store has to be able to answer the question.
            val ref = ref("locked.md")
            store.writeAtomically(ref, TEXT)
            assertTrue(path("locked.md").toFile().setReadOnly(), "The test could not make the file read-only")

            assertFalse(store.read(ref).writable)
        }

    @Test
    fun `a read-only file is not overwritten by the atomic write`() =
        runTest {
            // The trap this guards. A rename needs permission on the directory, not on the file it
            // replaces -- so without an explicit check the atomic write silently overwrites a file the
            // reader cannot write to, and the `readOnly` state 8.4 shows them would be a decoration.
            val ref = ref("locked.md")
            val opened = assertIs<WriteOutcome.Written>(store.writeAtomically(ref, TEXT))
            assertTrue(path("locked.md").toFile().setReadOnly(), "The test could not make the file read-only")

            val outcome = store.writeIfUnchanged(ref, "Overwritten.\n", opened.facts.digest)

            assertIs<WriteOutcome.Unavailable>(outcome)
            assertEquals(WriteOutcome.Reason.Denied, outcome.reason)
            assertEquals(TEXT, path("locked.md").readText())
        }

    private fun ref(name: String) = DocumentRef(path(name).toString())

    private fun path(name: String) = directory.resolve(name)

    private companion object {
        const val TEXT = "# Chapter\n\nA line.\n"

        /** Big enough that a non-atomic write would take more than one syscall to complete. */
        const val LINES = 20_000
        const val REWRITES = 40
        const val OBSERVATIONS = 400
    }
}
