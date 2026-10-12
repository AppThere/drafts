package com.appthere.drafts.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appthere.drafts.design.Corners
import com.appthere.drafts.design.Elevation
import com.appthere.drafts.design.LocalPalette
import com.appthere.drafts.design.Prose
import com.appthere.drafts.design.interfaceTextStyle
import com.appthere.drafts.design.raised
import com.appthere.drafts.i18n.resources.Res
import com.appthere.drafts.i18n.resources.binder_name
import com.appthere.drafts.i18n.resources.cancel
import com.appthere.drafts.i18n.resources.new_project
import com.appthere.drafts.i18n.resources.new_project_create
import com.appthere.drafts.i18n.resources.new_project_title
import com.appthere.drafts.i18n.resources.open_folder
import com.appthere.drafts.i18n.resources.open_project
import com.appthere.drafts.i18n.resources.recent_choose_again
import com.appthere.drafts.i18n.resources.recent_pin
import com.appthere.drafts.i18n.resources.recent_pinned
import com.appthere.drafts.i18n.resources.recent_projects
import com.appthere.drafts.i18n.resources.recent_remove
import com.appthere.drafts.i18n.resources.recent_unavailable
import com.appthere.drafts.i18n.resources.recent_unpin
import com.appthere.drafts.i18n.resources.said_after
import com.appthere.drafts.i18n.resources.template
import com.appthere.drafts.i18n.resources.template_empty
import com.appthere.drafts.i18n.resources.template_folder_beats
import com.appthere.drafts.i18n.resources.template_folder_characters
import com.appthere.drafts.i18n.resources.template_folder_draft
import com.appthere.drafts.i18n.resources.template_folder_figures
import com.appthere.drafts.i18n.resources.template_folder_manuscript
import com.appthere.drafts.i18n.resources.template_folder_notes
import com.appthere.drafts.i18n.resources.template_folder_places
import com.appthere.drafts.i18n.resources.template_folder_screenplay
import com.appthere.drafts.i18n.resources.template_folder_sources
import com.appthere.drafts.i18n.resources.template_folder_treatment
import com.appthere.drafts.i18n.resources.template_novel
import com.appthere.drafts.i18n.resources.template_research
import com.appthere.drafts.i18n.resources.template_screenplay
import com.appthere.drafts.i18n.resources.template_screenplay_treatment
import com.appthere.drafts.project.Outcome
import com.appthere.drafts.project.ProjectTemplate
import com.appthere.drafts.project.TemplateFolder
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/**
 * The writer's projects (`projects/project-windows`, "Recent projects"): pinned ones first, then the
 * rest newest first, each openable -- or, if its folder can no longer be reached, said so in words,
 * with a way to choose it again or take it off the list.
 */
@Composable
fun ProjectsHome(
    entries: List<ProjectEntry>,
    host: ProjectsHomeHost,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    val title = stringResource(Res.string.recent_projects)

    Column(
        modifier
            .semantics { paneTitle = title }
            .background(palette.background)
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(gap),
    ) {
        BasicText(
            title,
            style = interfaceTextStyle(color = palette.ink, fontSize = titleSize, fontWeight = Prose.H4.weight),
        )
        Buttons {
            Button(stringResource(Res.string.open_folder), onClick = host::openFolder)
            Button(stringResource(Res.string.new_project), onClick = host::newProject)
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(gap)) {
            items(entries, key = { it.token }) { entry -> Entry(entry, host) }
        }
    }
}

@Composable
private fun Entry(
    entry: ProjectEntry,
    host: ProjectsHomeHost,
) {
    val palette = LocalPalette.current
    val available = host.isAvailable(entry)
    val marks =
        listOfNotNull(
            stringResource(Res.string.recent_pinned).takeIf {
                entry.pinned
            },
            stringResource(Res.string.recent_unavailable).takeIf { !available },
        )

    Column(
        // Each project on the panel tone, set apart from the page by tone rather than a ruled line.
        Modifier.fillMaxWidth().background(palette.panel, Corners.Container).padding(entryPadding),
        verticalArrangement = Arrangement.spacedBy(gap),
    ) {
        val described = marks.fold(entry.name) { said, mark -> stringResource(Res.string.said_after, said, mark) }
        Buttons {
            BasicText(
                text = entry.name,
                style = interfaceTextStyle(color = if (available) palette.ink else palette.muted, fontSize = bodySize),
                modifier = Modifier.semantics { contentDescription = described },
            )
            marks.forEach { mark ->
                BasicText(
                    text = mark,
                    style = interfaceTextStyle(color = palette.ink, fontSize = smallSize),
                    modifier =
                        Modifier
                            .background(
                                palette.control,
                                Corners.Pill,
                            ).padding(horizontal = gap, vertical = markPadding),
                )
            }
        }
        Buttons {
            if (available) {
                Button(
                    stringResource(Res.string.open_project),
                    described = entry.name,
                    onClick = { host.open(entry) },
                )
            }
            if (!available) {
                Button(
                    stringResource(Res.string.recent_choose_again),
                    described = entry.name,
                    onClick = { host.chooseAgain(entry) },
                )
            }
            val pin = stringResource(if (entry.pinned) Res.string.recent_unpin else Res.string.recent_pin)
            Button(pin, described = entry.name, onClick = { host.pin(entry, !entry.pinned) })
            Button(stringResource(Res.string.recent_remove), described = entry.name, onClick = { host.remove(entry) })
        }
    }
}

/**
 * The projects screen as a whole: the list, the new-project prompt over it while one is being made
 * ([creating]), and what went wrong with the last one, if anything did.
 */
@Composable
fun ProjectsScreen(
    entries: List<ProjectEntry>,
    host: ProjectsHomeHost,
    creating: Boolean,
    onCreate: (String, ProjectTemplate) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    problem: Outcome? = null,
) {
    Box(modifier.fillMaxSize().background(LocalPalette.current.background)) {
        ProjectsHome(entries, host, Modifier.fillMaxSize())
        if (creating) {
            NewProjectPrompt(
                onCreate = onCreate,
                onCancel = onCancel,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        problem?.let { Notice(message = problemText(Problem(it, ""), null)) }
    }
}

/**
 * A new project's name and template (`projects/templates`). Where it goes was chosen first, with
 * the platform's own folder picker.
 */
@Composable
fun NewProjectPrompt(
    onCreate: (String, ProjectTemplate) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    val title = stringResource(Res.string.new_project_title)
    val label = stringResource(Res.string.binder_name)
    var name by remember { mutableStateOf("") }
    var template by remember { mutableStateOf(ProjectTemplate.Novel) }

    Column(
        modifier
            .semantics { paneTitle = title }
            .raised(palette.panel, Corners.Container, Elevation.Floating)
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(gap),
    ) {
        BasicText(
            title,
            style = interfaceTextStyle(color = palette.ink, fontSize = titleSize, fontWeight = Prose.H5.weight),
        )
        BasicText(label, style = interfaceTextStyle(color = palette.ink, fontSize = bodySize))
        BasicTextField(
            value = name,
            onValueChange = { name = it },
            singleLine = true,
            textStyle = interfaceTextStyle(color = palette.ink, fontSize = bodySize),
            cursorBrush = SolidColor(palette.ink),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .sizeIn(minHeight = target)
                    .border(hairline, palette.muted, RoundedCornerShape(corner))
                    .padding(entryPadding)
                    .semantics { contentDescription = label },
        )
        BasicText(
            stringResource(Res.string.template),
            style = interfaceTextStyle(color = palette.ink, fontSize = bodySize),
        )
        Buttons {
            ProjectTemplate.entries.forEach { choice ->
                TemplateOption(stringResource(templateWord(choice)), chosen = choice == template) { template = choice }
            }
        }
        Buttons {
            Button(
                stringResource(Res.string.new_project_create),
                onClick = { onCreate(name.trim(), template) },
                enabled = name.isNotBlank(),
            )
            Button(stringResource(Res.string.cancel), onClick = onCancel)
        }
    }
}

/** What a template's folders are called in the writer's language, for `ProjectTemplates.create`. */
suspend fun templateFolderNames(): (TemplateFolder) -> String {
    val names = TemplateFolder.entries.associateWith { getString(folderWord(it)) }
    return names::getValue
}

private fun templateWord(template: ProjectTemplate): StringResource =
    when (template) {
        ProjectTemplate.Novel -> Res.string.template_novel
        ProjectTemplate.Screenplay -> Res.string.template_screenplay
        ProjectTemplate.ScreenplayAndTreatment -> Res.string.template_screenplay_treatment
        ProjectTemplate.ResearchPaper -> Res.string.template_research
        ProjectTemplate.Empty -> Res.string.template_empty
    }

private fun folderWord(folder: TemplateFolder): StringResource =
    when (folder) {
        TemplateFolder.Manuscript -> Res.string.template_folder_manuscript
        TemplateFolder.Notes -> Res.string.template_folder_notes
        TemplateFolder.Characters -> Res.string.template_folder_characters
        TemplateFolder.Places -> Res.string.template_folder_places
        TemplateFolder.Screenplay -> Res.string.template_folder_screenplay
        TemplateFolder.Treatment -> Res.string.template_folder_treatment
        TemplateFolder.Beats -> Res.string.template_folder_beats
        TemplateFolder.Draft -> Res.string.template_folder_draft
        TemplateFolder.Sources -> Res.string.template_folder_sources
        TemplateFolder.Figures -> Res.string.template_folder_figures
    }

/** Wraps rather than overflowing, for 200% text (10.2). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Buttons(content: @Composable () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(gap), verticalArrangement = Arrangement.spacedBy(gap)) {
        content()
    }
}

/** A [PanelButton] whose description names what it acts on, when there is a list of them. */
@Composable
private fun Button(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    described: String? = null,
    enabled: Boolean = true,
) {
    val description = described?.let { stringResource(Res.string.said_after, text, it) } ?: text
    PanelButton(text = text, description = description, enabled = enabled, onClick = onClick, modifier = modifier)
}

/**
 * One template to choose, drawn as the reader controls draw an option: chosen is said three ways,
 * the accent fill, the weight of the word, and to a screen reader as a selected radio button.
 */
@Composable
private fun TemplateOption(
    word: String,
    chosen: Boolean,
    onChoose: () -> Unit,
) {
    val palette = LocalPalette.current
    val style = interfaceTextStyle(color = if (chosen) palette.background else palette.ink, fontSize = bodySize)
    BasicText(
        text = word,
        style = if (chosen) style.copy(fontWeight = FontWeight.SemiBold) else style,
        modifier =
            Modifier
                .sizeIn(minWidth = target, minHeight = target)
                .clip(Corners.Pill)
                .background(if (chosen) palette.accent else palette.control)
                .selectable(selected = chosen, role = Role.RadioButton, onClick = onChoose)
                .padding(entryPadding),
    )
}

private val padding = 16.dp
private val entryPadding = 12.dp
private val gap = 8.dp
private val markPadding = 2.dp
private val corner = 6.dp
private val hairline = 1.dp
private val target = 48.dp
private val bodySize = 15.sp
private val smallSize = 12.sp
private val titleSize = 18.sp
