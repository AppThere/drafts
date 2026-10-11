package com.appthere.drafts.app

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.appthere.drafts.platform.files.DocumentRef
import com.appthere.drafts.platform.files.PathDocumentStore
import com.appthere.drafts.platform.files.PathFolderEditing
import com.appthere.drafts.platform.files.PathFolderListing
import com.appthere.drafts.project.FolderOrder
import com.appthere.drafts.project.ManifestState
import com.appthere.drafts.project.ProjectFile
import com.appthere.drafts.project.ProjectFiles
import com.appthere.drafts.project.ProjectLocation
import com.appthere.drafts.project.ProjectManifest
import com.appthere.drafts.project.ProjectNode
import com.appthere.drafts.project.ProjectPath
import com.appthere.drafts.project.ProjectSession
import com.appthere.drafts.project.ProjectSettings
import com.appthere.drafts.project.ProjectStateStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** `projects/binder` as the writer sees and works it, on a real folder. */
@OptIn(ExperimentalTestApi::class)
class BinderTest {
    private val directory: Path = createTempDirectory("drafts-binder")
    private val appData: Path = createTempDirectory("drafts-binder-app")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @AfterTest
    fun cleanUp() {
        scope.cancel()
        directory.toFile().deleteRecursively()
        appData.toFile().deleteRecursively()
    }

    @Test
    fun `a mixed folder shows each item with its kind and hides dot-files`() =
        runSkikoComposeUiTest(size = DESKTOP) {
            file("notes.md", "Notes.")
            file("02-dock.fountain", "INT. DOCK")
            file("map.png", "PNG")
            file(".DS_Store", "")
            show(opened())

            row("notes.md, Markdown").assertExists()
            row("02-dock.fountain, Fountain").assertExists()
            row("map.png, File").assertExists()
            assertEquals(0, onAllNodesWithContentDescription(".DS_Store", substring = true).fetchSemanticsNodes().size)
        }

    @Test
    fun `a pulled file placed by a collaborator is new and not unsorted`() =
        runSkikoComposeUiTest(size = DESKTOP) {
            file("01-open.fountain", "OPEN")
            order("01-open.fountain")
            val controller = opened()
            file("02-pulled.fountain", "PULLED")
            order("01-open.fountain", "02-pulled.fountain")
            runBlocking { controller.session.reconcile() }
            show(controller)

            val described = description("02-pulled.fountain")
            assertTrue("New" in described, described)
            assertTrue("Unsorted" !in described, described)
        }

    @Test
    fun `move up from the keyboard moves the scene and says where`() =
        runSkikoComposeUiTest(size = DESKTOP) {
            file("01-open.fountain", "OPEN")
            file("02-dock.fountain", "DOCK")
            order("01-open.fountain", "02-dock.fountain")
            val controller = opened()
            show(controller)

            val dock = row("02-dock.fountain")
            dock.requestFocus()
            dock.performKeyInput {
                keyDown(Key.AltLeft)
                pressKey(Key.DirectionUp)
                keyUp(Key.AltLeft)
            }
            waitUntil(timeoutMillis = TIMEOUT) {
                controller.view.value!!
                    .project.root.children
                    .first()
                    .name ==
                    "02-dock.fountain"
            }

            onNodeWithText("Moved to 1 of 2").assertExists()
        }

    @Test
    fun `a screen reader hears a nested scene as one item, with its place`() =
        runSkikoComposeUiTest(size = DESKTOP) {
            listOf("a", "b", "c", "d", "e").forEach { file("Act 1/Scenes/$it.fountain", it) }
            val controller = opened()
            file("Act 1/Scenes/c.fountain", "c, rewritten")
            controller.expanded(setOf(ProjectPath("/Act 1"), ProjectPath("/Act 1/Scenes")))
            show(controller)

            val described = description("c.fountain")
            assertEquals("c.fountain, Fountain, level 3, 3 of 5", described)
        }

    @Test
    fun `assistive technology can move a row without dragging`() =
        runSkikoComposeUiTest(size = DESKTOP) {
            file("01-open.fountain", "OPEN")
            file("02-dock.fountain", "DOCK")
            order("01-open.fountain", "02-dock.fountain")
            val controller = opened()
            show(controller)

            val row = row("01-open.fountain")
            val actions =
                row
                    .fetchSemanticsNode()
                    .config
                    .getOrNull(SemanticsActions.CustomActions)
                    .orEmpty()
            assertTrue(actions.any { it.label == "Move Down" }, actions.map { it.label }.toString())

            actions.first { it.label == "Move Down" }.action()
            waitUntil(timeoutMillis = TIMEOUT) {
                controller.view.value!!
                    .project.root.children
                    .first()
                    .name ==
                    "02-dock.fountain"
            }
        }

    @Test
    fun `at twice the text size rows do not overlap`() =
        runSkikoComposeUiTest(size = PHONE, density = Density(1f, fontScale = 2f)) {
            listOf("a long scene name.fountain", "another long scene name.fountain").forEach { file(it, it) }
            show(opened())

            val first = row("a long scene name.fountain").getBoundsInRoot()
            val second = row("another long scene name.fountain").getBoundsInRoot()
            assertTrue(first.bottom <= second.top, "Rows overlap: $first and $second")
        }

    @Test
    fun `right to left, a nested row is indented from the right`() =
        runSkikoComposeUiTest(size = DESKTOP) {
            file("Act 1/open.md", "OPEN")
            file("top.md", "TOP")
            val controller = opened()
            controller.expanded(setOf(ProjectPath("/Act 1")))
            show(controller, LayoutDirection.Rtl)

            val top = onNodeWithText("top.md", useUnmergedTree = true).getBoundsInRoot()
            val nested = onNodeWithText("open.md", useUnmergedTree = true).getBoundsInRoot()
            assertTrue(nested.right < top.right, "Not indented from the right: $nested beside $top")
        }

    private fun SkikoComposeUiTest.show(
        controller: ProjectController,
        direction: LayoutDirection = LayoutDirection.Ltr,
    ) {
        setContent { CompositionLocalProvider(LocalLayoutDirection provides direction) { Binder(controller) } }
        waitForIdle()
    }

    /** The row for [name] -- its own description begins with it; its actions button only mentions it. */
    private fun SkikoComposeUiTest.row(name: String) =
        onNode(
            hasContentDescription(
                "$name, ",
                substring = true,
            ).and(hasContentDescription("Actions for", substring = true).not()),
        )

    private fun SkikoComposeUiTest.description(name: String): String =
        row(name)
            .fetchSemanticsNode()
            .config
            .getOrNull(SemanticsProperties.ContentDescription)
            .orEmpty()
            .joinToString()

    private fun opened(): ProjectController {
        val listing = PathFolderListing()
        val session =
            ProjectSession(
                ProjectLocation(DocumentRef(directory.toString()), "Salt"),
                ProjectFiles(listing, PathFolderEditing(), PathDocumentStore()),
                ProjectStateStore(PathDocumentStore(), appData.toString()),
            )
        runBlocking { session.reconcile(full = true) }
        return ProjectController(session, scope, NoHost)
    }

    private fun order(vararg names: String) {
        val store = PathDocumentStore()
        val manifest = ProjectManifest(PathFolderListing(), PathFolderEditing(), store)
        runBlocking {
            manifest.write(
                DocumentRef(directory.toString()),
                ProjectFile(ProjectSettings(order = listOf(FolderOrder("/", names.toList())))),
                manifest
                    .read(
                        DocumentRef(directory.toString()),
                    ).let { if (it is ManifestState.Read) it else ManifestState.Absent },
            )
        }
    }

    private fun file(
        path: String,
        text: String,
    ) {
        val file = directory.resolve(path)
        file.parent.createDirectories()
        file.writeText(text)
    }

    private object NoHost : ProjectHost {
        override fun openDocument(
            document: ProjectNode.Document,
            newWindow: Boolean,
        ) = Unit

        override fun openExternally(file: DocumentRef) = Unit

        override fun hasUnsavedEdits(file: DocumentRef): Boolean = false
    }

    private companion object {
        val DESKTOP = Size(1200f, 800f)
        val PHONE = Size(400f, 800f)
        const val TIMEOUT = 5_000L
    }
}
