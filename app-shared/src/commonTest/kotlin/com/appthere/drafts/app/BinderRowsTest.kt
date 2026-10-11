package com.appthere.drafts.app

import com.appthere.drafts.platform.files.DocumentRef
import com.appthere.drafts.platform.intents.DocumentKind
import com.appthere.drafts.project.ManifestState
import com.appthere.drafts.project.PendingState
import com.appthere.drafts.project.Project
import com.appthere.drafts.project.ProjectNode
import com.appthere.drafts.project.ProjectPath
import com.appthere.drafts.project.ProjectView
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** `projects/binder`'s rows: what is shown, in what order, and what each says about itself. */
class BinderRowsTest {
    @Test
    fun `a collapsed folder shows itself and not what is in it`() {
        val rows = binderRows(view(), expanded = emptySet())

        assertEquals(listOf("/Screenplay", "/marla.md", "/map.png"), rows.map { it.path.value })
    }

    @Test
    fun `an expanded folder shows its children under it, a level deeper`() {
        val rows = binderRows(view(), expanded = setOf(SCREENPLAY))

        assertEquals(
            listOf(
                "/Screenplay",
                "/Screenplay/01-open.fountain",
                "/Screenplay/02-dock.fountain",
                "/marla.md",
                "/map.png",
            ),
            rows.map {
                it.path.value
            },
        )
        assertEquals(listOf(1, 2, 2, 1, 1), rows.map { it.level })
        assertTrue(rows.first().expanded)
    }

    @Test
    fun `each row knows its place among its siblings`() {
        val rows = binderRows(view(), expanded = setOf(SCREENPLAY))

        val dock = rows.single { it.node.name == "02-dock.fountain" }
        assertEquals(2 to 2, dock.position to dock.siblings)
        val map = rows.single { it.node.name == "map.png" }
        assertEquals(3 to 3, map.position to map.siblings)
    }

    @Test
    fun `kind is a document's own, and other files are other`() {
        val kinds = binderRows(view(), expanded = setOf(SCREENPLAY)).associate { it.node.name to it.kind }

        assertEquals(RowKind.Folder, kinds["Screenplay"])
        assertEquals(RowKind.Fountain, kinds["02-dock.fountain"])
        assertEquals(RowKind.Markdown, kinds["marla.md"])
        assertEquals(RowKind.Other, kinds["map.png"])
    }

    @Test
    fun `new and unsorted are separate marks`() {
        val rows = binderRows(view(new = setOf("/marla.md")), expanded = emptySet())

        val marla = rows.single { it.node.name == "marla.md" }
        val map = rows.single { it.node.name == "map.png" }
        assertTrue(marla.new && !marla.unsorted)
        assertTrue(map.unsorted && !map.new)
    }

    @Test
    fun `a missing file is a row that is not present`() {
        val rows = binderRows(view(missing = true), expanded = emptySet())

        val gone = rows.single { it.kind == RowKind.Missing }
        assertEquals(false, gone.present)
    }

    @Test
    fun `move up and move down give the folder's new order`() {
        val rows = binderRows(view(), expanded = setOf(SCREENPLAY))

        assertEquals(
            listOf("02-dock.fountain", "01-open.fountain"),
            reorderedBy(rows, SCREENPLAY.child("02-dock.fountain"), -1),
        )
        assertEquals(listOf("Screenplay", "map.png", "marla.md"), reorderedBy(rows, ProjectPath("/marla.md"), 1))
        assertNull(reorderedBy(rows, ProjectPath("/Screenplay"), -1))
    }

    @Test
    fun `dropping on the upper edge of a sibling puts it before`() {
        val rows = binderRows(view(), expanded = setOf(SCREENPLAY))

        val drop = dropOf(rows, row(rows, "02-dock.fountain"), row(rows, "01-open.fountain"), DropZone.Before)

        assertEquals(Drop.Reorder(SCREENPLAY, listOf("02-dock.fountain", "01-open.fountain")), drop)
    }

    @Test
    fun `dropping on the lower edge of a later sibling puts it after`() {
        val rows = binderRows(view(), expanded = emptySet())

        val drop = dropOf(rows, row(rows, "Screenplay"), row(rows, "marla.md"), DropZone.After)

        assertEquals(Drop.Reorder(ProjectPath.Root, listOf("marla.md", "Screenplay", "map.png")), drop)
    }

    @Test
    fun `dropping on the middle of a folder moves into it`() {
        val rows = binderRows(view(), expanded = emptySet())

        assertEquals(
            Drop.Move(SCREENPLAY, at = null),
            dropOf(rows, row(rows, "marla.md"), row(rows, "Screenplay"), DropZone.Into),
        )
    }

    @Test
    fun `dropping beside a row in another folder moves to that place`() {
        val rows = binderRows(view(), expanded = setOf(SCREENPLAY))

        assertEquals(
            Drop.Move(SCREENPLAY, at = 1),
            dropOf(rows, row(rows, "marla.md"), row(rows, "02-dock.fountain"), DropZone.Before),
        )
    }

    @Test
    fun `a folder cannot be dropped into itself`() {
        val rows = binderRows(view(), expanded = setOf(SCREENPLAY))

        assertNull(dropOf(rows, row(rows, "Screenplay"), row(rows, "01-open.fountain"), DropZone.Before))
        assertNull(dropOf(rows, row(rows, "Screenplay"), row(rows, "Screenplay"), DropZone.Into))
    }

    private fun row(
        rows: List<BinderRow>,
        name: String,
    ) = rows.single { it.node.name == name }

    private fun view(
        new: Set<String> = emptySet(),
        missing: Boolean = false,
    ): ProjectView {
        val scenes =
            listOf("01-open.fountain", "02-dock.fountain").map {
                ProjectNode.Document(it, SCREENPLAY.child(it), ref(it), DocumentKind.Fountain)
            }
        val children =
            listOfNotNull(
                ProjectNode.Folder("Screenplay", SCREENPLAY, ref("Screenplay"), scenes),
                ProjectNode.Document("marla.md", ProjectPath("/marla.md"), ref("marla.md"), DocumentKind.Markdown),
                ProjectNode.Missing("gone.md", ProjectPath("/gone.md")).takeIf { missing },
                ProjectNode.Attachment("map.png", ProjectPath("/map.png"), ref("map.png"), unplaced = true),
            )
        val project =
            Project("Salt", ProjectNode.Folder("Salt", ProjectPath.Root, ref(""), children), hasSidecar = true)
        return ProjectView(project, ManifestState.Absent, PendingState(new = new), complete = true)
    }

    private fun ref(name: String) = DocumentRef("/salt/$name")

    private companion object {
        val SCREENPLAY = ProjectPath("/Screenplay")
    }
}
