package com.appthere.drafts.app

import com.appthere.drafts.platform.files.DocumentRef
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** `projects/project-windows`' recent projects and what an open one's window showed. */
class ProjectListTest {
    private val list = ProjectList(FakeDocumentStore(DocumentRef("/data/recent.json"), ""), "/data")

    @Test
    fun `newest first, with pinned ones before them`() =
        runTest {
            list.opened(SALT, "The Salt Road", now = 1)
            list.opened(MARLA, "Marla", now = 2)
            list.opened(DOCK, "The Dock", now = 3)
            list.pin(SALT, pinned = true)

            assertEquals(listOf("The Salt Road", "The Dock", "Marla"), list.entries().map { it.name })
        }

    @Test
    fun `a pinned project stays listed however many come after it`() =
        runTest {
            list.opened(SALT, "The Salt Road", now = 0)
            list.pin(SALT, pinned = true)
            (1..20).forEach { list.opened(DocumentRef("/p/$it"), "Project $it", now = it.toLong()) }

            assertTrue(list.entries().any { it.name == "The Salt Road" })
            assertEquals(11, list.entries().size)
        }

    @Test
    fun `an open project remembers what its window showed, and forgets it when closed`() =
        runTest {
            list.opened(SALT, "The Salt Road")
            list.remember(
                SALT,
                expanded = listOf("/Screenplay"),
                selected = "/Screenplay/dock.fountain",
                document = "doc-1",
            )

            val open = list.open().single()
            assertEquals(listOf("/Screenplay"), open.expanded)
            assertEquals("doc-1", open.document)

            list.closed(SALT)
            assertTrue(list.open().isEmpty())
            assertEquals(listOf("The Salt Road"), list.entries().map { it.name })
        }

    @Test
    fun `a removed project is gone from the list`() =
        runTest {
            list.opened(SALT, "The Salt Road")

            list.remove(SALT)

            assertTrue(list.entries().isEmpty())
        }

    private companion object {
        val SALT = DocumentRef("/home/writer/The Salt Road")
        val MARLA = DocumentRef("/home/writer/Marla")
        val DOCK = DocumentRef("/home/writer/The Dock")
    }
}
