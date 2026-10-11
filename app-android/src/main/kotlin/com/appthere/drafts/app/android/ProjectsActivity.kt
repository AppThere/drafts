package com.appthere.drafts.app.android

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.appthere.drafts.app.ProjectEntry
import com.appthere.drafts.app.ProjectsHomeHost
import com.appthere.drafts.app.ProjectsScreen
import com.appthere.drafts.app.templateFolderNames
import com.appthere.drafts.design.DraftsTheme
import com.appthere.drafts.design.ReaderSettings
import com.appthere.drafts.platform.files.DocumentRef
import com.appthere.drafts.platform.files.SafDocumentStore
import com.appthere.drafts.platform.files.SafFolderEditing
import com.appthere.drafts.platform.files.SafFolderListing
import com.appthere.drafts.project.Outcome
import com.appthere.drafts.project.ProjectFiles
import com.appthere.drafts.project.ProjectLocation
import com.appthere.drafts.project.ProjectTemplate
import com.appthere.drafts.project.ProjectTemplates
import kotlinx.coroutines.launch

/**
 * The writer's projects on Android (`projects/project-windows`, "Recent projects"): recent ones, a
 * folder to open through the system's folder picker, and a new one to make from a template.
 *
 * A folder chosen here is kept: its grant is persisted, so the project opens again after a restart
 * without being chosen again.
 */
class ProjectsActivity : ComponentActivity() {
    private lateinit var chooseFolder: ActivityResultLauncher<Uri?>
    private lateinit var chooseParent: ActivityResultLauncher<Uri?>
    private var refreshed by mutableIntStateOf(0)
    private var newIn by mutableStateOf<Uri?>(null)
    private var problem by mutableStateOf<Outcome?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        chooseFolder =
            registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
                tree
                    ?.let {
                        keptFolder(this, it)
                    }?.let(::open)
            }
        chooseParent =
            registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
                newIn =
                    tree?.takeIf { keptFolder(this, it) != null }
            }
        if (savedInstanceState == null && intent.getBooleanExtra(EXTRA_CHOOSE, false)) chooseFolder.launch(null)
        setContent { DraftsTheme(ReaderSettings()) { Home() } }
    }

    @Composable
    private fun Home() {
        val scope = rememberCoroutineScope()
        val list = AndroidProjects.list(this)
        val entries by produceState(emptyList<ProjectEntry>(), refreshed) { value = list.entries() }

        ProjectsScreen(
            entries = entries,
            host = Host(),
            creating = newIn != null,
            onCreate = { name, template -> newIn?.let { parent -> scope.launch { create(parent, name, template) } } },
            onCancel = { newIn = null },
            problem = problem,
        )
    }

    private suspend fun create(
        parent: Uri,
        name: String,
        template: ProjectTemplate,
    ) {
        val files = ProjectFiles(SafFolderListing(this), SafFolderEditing(this), SafDocumentStore(this))
        val created =
            ProjectTemplates(
                files,
            ).create(DocumentRef(parent.toString()), name, template, templateFolderNames())
        problem = created.outcome.takeIf { it != Outcome.Done }
        created.location?.let {
            newIn = null
            open(it)
        }
    }

    private fun open(location: ProjectLocation) {
        lifecycleScope.launch {
            AndroidProjects.list(this@ProjectsActivity).opened(location.ref, location.name)
            startActivity(projectIntent(this@ProjectsActivity, location))
            refreshed++
        }
    }

    /** The projects screen's answers, each refreshing the list after. */
    private inner class Host : ProjectsHomeHost {
        private val list = AndroidProjects.list(this@ProjectsActivity)

        override fun open(entry: ProjectEntry) = open(ProjectLocation(DocumentRef(entry.token), entry.name))

        override fun chooseAgain(entry: ProjectEntry) =
            changed {
                list.remove(DocumentRef(entry.token))
                chooseFolder.launch(null)
            }

        override fun pin(
            entry: ProjectEntry,
            pinned: Boolean,
        ) = changed { list.pin(DocumentRef(entry.token), pinned) }

        override fun remove(entry: ProjectEntry) = changed { list.remove(DocumentRef(entry.token)) }

        override fun openFolder() = chooseFolder.launch(null)

        override fun newProject() = chooseParent.launch(null)

        override fun isAvailable(entry: ProjectEntry): Boolean =
            AndroidProjects.isAvailable(this@ProjectsActivity, entry)

        private fun changed(work: suspend () -> Unit) {
            lifecycleScope.launch {
                work()
                refreshed++
            }
        }
    }

    internal companion object {
        /** Opens the folder picker straight away: *Open Folder…* from a document. */
        const val EXTRA_CHOOSE = "com.appthere.drafts.CHOOSE_FOLDER"
    }
}
