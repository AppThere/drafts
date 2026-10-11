package com.appthere.drafts.project

import com.appthere.drafts.platform.files.DocumentRef
import com.appthere.drafts.platform.files.FolderOutcome
import com.appthere.drafts.platform.files.WriteOutcome

/**
 * The scaffolds a new project can start from (`projects/templates`, `projects.md` 2). A starting
 * point and nothing more: once made, nothing in the app knows or cares which one a project came
 * from, and no folder in it is special by name.
 *
 * *Screenplay + Treatment* has its `Treatment/` folder and not yet its two compile targets, which
 * are Phase 10's: written now, they would make a `project.toml` this version could not write back
 * (`projects-core` design D11).
 */
enum class ProjectTemplate(
    val folders: List<TemplateFolder>,
) {
    Novel(listOf(TemplateFolder.Manuscript, TemplateFolder.Notes, TemplateFolder.Characters, TemplateFolder.Places)),
    Screenplay(
        listOf(TemplateFolder.Screenplay, TemplateFolder.Notes, TemplateFolder.Characters, TemplateFolder.Beats),
    ),
    ScreenplayAndTreatment(
        listOf(
            TemplateFolder.Screenplay,
            TemplateFolder.Treatment,
            TemplateFolder.Notes,
            TemplateFolder.Characters,
            TemplateFolder.Beats,
        ),
    ),
    ResearchPaper(listOf(TemplateFolder.Draft, TemplateFolder.Sources, TemplateFolder.Figures)),
    Empty(emptyList()),
}

/** A folder a template makes. Its name is the writer's language's, which the app supplies. */
enum class TemplateFolder {
    Manuscript,
    Notes,
    Characters,
    Places,
    Screenplay,
    Treatment,
    Beats,
    Draft,
    Sources,
    Figures,
}

/** Makes new projects from [ProjectTemplate]s. */
class ProjectTemplates(
    private val files: ProjectFiles,
) {
    /**
     * A new project called [name] in [parent], from [template], with each template folder called
     * what [named] says. All of it is made, or nothing is: a project half made is removed, which is
     * safe because every part of it is new. Refused if [name] is taken in [parent], or if [parent]
     * is inside a project.
     */
    suspend fun create(
        parent: DocumentRef,
        name: String,
        template: ProjectTemplate,
        named: (TemplateFolder) -> String,
    ): Created =
        when (val made = if (isInsideProject(parent)) null else files.editing.createFolder(parent, name)) {
            null -> Created(Outcome.InsideProject)
            FolderOutcome.Taken -> Created(Outcome.Taken)
            is FolderOutcome.Failed -> Created(Outcome.Failed(made.detail))
            is FolderOutcome.Done -> scaffolded(ProjectLocation(made.ref, name), template.folders.map(named))
        }

    /** The new, empty project folder filled in -- or, if any of it fails, removed again. */
    private suspend fun scaffolded(
        project: ProjectLocation,
        folders: List<String>,
    ): Created {
        val manifest = ProjectManifest(files.listing, files.editing, files.store)
        val file = ProjectFile(ProjectSettings(name = project.name, order = order(folders)))
        val written = manifest.write(project.ref, file, ManifestState.Absent) is ManifestWrite.Written
        val made = written && folders.all { files.editing.createFolder(project.ref, it) is FolderOutcome.Done }
        val ignore = if (made) files.editing.createFile(project.ref, GITIGNORE) as? FolderOutcome.Done else null
        val done = ignore != null && files.store.writeAtomically(ignore.ref, IGNORED) is WriteOutcome.Written

        if (!done) files.editing.remove(project.ref)
        return if (done) {
            Created(
                Outcome.Done,
                project,
            )
        } else {
            Created(Outcome.Failed("${project.name} could not be made"))
        }
    }

    /** Inside a project: [parent] is one, or one is above it, where the platform can tell. */
    private suspend fun isInsideProject(parent: DocumentRef): Boolean =
        files.listing
            .entriesOf(parent)
            .orEmpty()
            .any { it.isFolder && it.name == SIDECAR } ||
            EnclosingProject(files.listing).of(parent) is Enclosing.Inside

    private fun order(folders: List<String>): List<FolderOrder> =
        if (folders.isEmpty()) emptyList() else listOf(FolderOrder(ProjectPath.Root.value, folders))

    private companion object {
        const val SIDECAR = ".drafts"
        const val GITIGNORE = ".gitignore"

        /** `projects.md` 8: "Ship a sensible `.gitignore` in every template." */
        const val IGNORED = ".drafts/cache/\n.drafts/trash/\n"
    }
}

/** What [ProjectTemplates.create] did, and where the project is if it made one. */
data class Created(
    val outcome: Outcome,
    val location: ProjectLocation? = null,
)
