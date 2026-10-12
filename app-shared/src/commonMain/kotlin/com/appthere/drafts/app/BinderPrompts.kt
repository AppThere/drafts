package com.appthere.drafts.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appthere.drafts.design.Corners
import com.appthere.drafts.design.Elevation
import com.appthere.drafts.design.LocalPalette
import com.appthere.drafts.design.Prose
import com.appthere.drafts.design.interfaceTextStyle
import com.appthere.drafts.design.raised
import com.appthere.drafts.i18n.resources.Res
import com.appthere.drafts.i18n.resources.apply
import com.appthere.drafts.i18n.resources.binder_locate_title
import com.appthere.drafts.i18n.resources.binder_move_to_title
import com.appthere.drafts.i18n.resources.binder_name
import com.appthere.drafts.i18n.resources.binder_untitled_folder
import com.appthere.drafts.i18n.resources.binder_untitled_fountain
import com.appthere.drafts.i18n.resources.binder_untitled_markdown
import com.appthere.drafts.i18n.resources.cancel
import com.appthere.drafts.i18n.resources.close
import com.appthere.drafts.i18n.resources.delete_unsaved
import com.appthere.drafts.i18n.resources.move_to_trash
import com.appthere.drafts.i18n.resources.new_project_create
import com.appthere.drafts.i18n.resources.said_after
import com.appthere.drafts.i18n.resources.trash
import com.appthere.drafts.i18n.resources.trash_confirm_all
import com.appthere.drafts.i18n.resources.trash_confirm_one
import com.appthere.drafts.i18n.resources.trash_delete_forever
import com.appthere.drafts.i18n.resources.trash_empty
import com.appthere.drafts.i18n.resources.trash_empty_all
import com.appthere.drafts.i18n.resources.trash_from
import com.appthere.drafts.i18n.resources.trash_restore
import com.appthere.drafts.i18n.resources.trash_restore_as
import com.appthere.drafts.platform.intents.DocumentKind
import com.appthere.drafts.project.ProjectNode
import com.appthere.drafts.project.ProjectPath
import com.appthere.drafts.project.ProjectView
import com.appthere.drafts.project.Trashed
import com.appthere.drafts.project.files
import com.appthere.drafts.project.folders
import org.jetbrains.compose.resources.stringResource

/**
 * What the binder asks the writer, one question at a time: a name, a place, a file, or whether
 * they are sure. Every question can be put aside with Cancel, which changes nothing; none has a
 * default that Return would choose, because the destructive ones must be chosen on purpose.
 */
@Composable
internal fun BinderPrompts(
    controller: ProjectController,
    view: ProjectView,
    prompt: BinderPrompt,
    modifier: Modifier = Modifier,
) {
    val dismiss = { controller.ask(null) }
    val nameOf = { path: ProjectPath -> path.name }

    when (prompt) {
        is BinderPrompt.Rename, is BinderPrompt.Create, is BinderPrompt.RestoreAs -> {
            NamingPrompt(controller, prompt, dismiss, modifier)
        }

        is BinderPrompt.MoveTo -> {
            val folders =
                view.project.root.folders().map { it.path }.filterNot {
                    it.isWithin(prompt.path) ||
                        it == prompt.path.parent
                }
            ChoosePrompt(
                title = stringResource(Res.string.binder_move_to_title, nameOf(prompt.path)),
                choices = folders,
                onCancel = dismiss,
                onChoose = { controller.moveTo(prompt.path, it) },
                modifier = modifier,
            )
        }

        is BinderPrompt.Locate -> {
            val files =
                view.project.root
                    .files()
                    .filter { it !is ProjectNode.Missing }
                    .map { it.path }
            ChoosePrompt(
                title = stringResource(Res.string.binder_locate_title, nameOf(prompt.missing)),
                choices = files,
                onCancel = dismiss,
                onChoose = { controller.answers.locate(prompt.missing, it) },
                modifier = modifier,
            )
        }

        is BinderPrompt.ConfirmDelete -> {
            Confirm(
                message = stringResource(Res.string.delete_unsaved, nameOf(prompt.path)),
                confirm = stringResource(Res.string.move_to_trash),
                onCancel = dismiss,
                onConfirm = { controller.deleteNow(prompt.path) },
                modifier = modifier,
            )
        }

        BinderPrompt.Trash -> {
            TrashPrompt(controller, dismiss, modifier)
        }

        is BinderPrompt.ConfirmEmpty -> {
            Confirm(
                message =
                    prompt.item?.let { stringResource(Res.string.trash_confirm_one, it.entry.name) }
                        ?: stringResource(Res.string.trash_confirm_all),
                confirm = stringResource(Res.string.trash_delete_forever),
                onCancel = dismiss,
                onConfirm = { controller.trashCan.empty(prompt.item) },
                modifier = modifier,
            )
        }
    }
}

/** The three questions that ask for a name: a rename, a new item, and a restore onto a name now taken. */
@Composable
private fun NamingPrompt(
    controller: ProjectController,
    prompt: BinderPrompt,
    dismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val nameOf = { path: ProjectPath -> path.name }
    when (prompt) {
        is BinderPrompt.Rename -> {
            NamePrompt(
                initial = nameOf(prompt.path),
                confirm = stringResource(Res.string.apply),
                onCancel = dismiss,
                onName = { controller.rename(prompt.path, it) },
                modifier = modifier,
            )
        }

        is BinderPrompt.Create -> {
            NamePrompt(
                initial = untitled(prompt.kind),
                confirm = stringResource(Res.string.new_project_create),
                onCancel = dismiss,
                onName = { name ->
                    if (prompt.kind == null) {
                        controller.createFolder(prompt.folder, name, prompt.at)
                    } else {
                        controller.createDocument(prompt.folder, name, prompt.at)
                    }
                },
                modifier = modifier,
            )
        }

        is BinderPrompt.RestoreAs -> {
            NamePrompt(
                initial = prompt.item.entry.name,
                confirm = stringResource(Res.string.trash_restore),
                onCancel = dismiss,
                onName = { controller.trashCan.restore(prompt.item, it) },
                modifier = modifier,
                message = stringResource(Res.string.trash_restore_as, prompt.item.entry.name),
            )
        }

        else -> {}
    }
}

@Composable
private fun untitled(kind: DocumentKind?): String =
    stringResource(
        when (kind) {
            DocumentKind.Markdown -> Res.string.binder_untitled_markdown
            DocumentKind.Fountain -> Res.string.binder_untitled_fountain
            null -> Res.string.binder_untitled_folder
        },
    )

/** A name to give: prefilled, focused, and only taken when the writer says so. */
@Composable
private fun NamePrompt(
    initial: String,
    confirm: String,
    onCancel: () -> Unit,
    onName: (String) -> Unit,
    modifier: Modifier = Modifier,
    message: String? = null,
) {
    val palette = LocalPalette.current
    val label = stringResource(Res.string.binder_name)
    var name by remember(initial) { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }

    PromptCard(message ?: label, modifier) {
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
                    .padding(fieldPadding)
                    .focusRequester(focus)
                    .semantics { contentDescription = label },
        )
        Choices {
            PanelButton(text = confirm, description = confirm, enabled = name.isNotBlank(), onClick = {
                onCancel()
                onName(name.trim())
            })
            CancelButton(onCancel)
        }
    }
    LaunchedEffect(Unit) { focus.requestFocus() }
}

/** One of [choices] to pick -- a folder to move to, a file to relink -- by its path in the project. */
@Composable
private fun ChoosePrompt(
    title: String,
    choices: List<ProjectPath>,
    onCancel: () -> Unit,
    onChoose: (ProjectPath) -> Unit,
    modifier: Modifier = Modifier,
) {
    PromptCard(title, modifier) {
        Column(
            Modifier.heightIn(max = listHeight).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(gap),
        ) {
            choices.forEach { path ->
                PanelButton(text = path.value, description = path.value, modifier = Modifier.fillMaxWidth(), onClick = {
                    onCancel()
                    onChoose(path)
                })
            }
        }
        Choices { CancelButton(onCancel) }
    }
}

@Composable
private fun Confirm(
    message: String,
    confirm: String,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PromptCard(message, modifier) {
        Choices {
            PanelButton(text = confirm, description = confirm, onClick = {
                onCancel()
                onConfirm()
            })
            CancelButton(onCancel)
        }
    }
}

/** The project's trash: what is in it, where each came from, and the way back -- or out for good. */
@Composable
private fun TrashPrompt(
    controller: ProjectController,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    var items by remember { mutableStateOf<List<Trashed>?>(null) }
    LaunchedEffect(Unit) { items = controller.trashCan.items() }

    PromptCard(stringResource(Res.string.trash), modifier) {
        val held = items.orEmpty()
        if (items != null &&
            held.isEmpty()
        ) {
            BasicText(
                stringResource(Res.string.trash_empty),
                style = interfaceTextStyle(color = palette.muted, fontSize = bodySize),
            )
        }
        Column(
            Modifier.heightIn(max = listHeight).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(gap),
        ) {
            held.forEach { item -> TrashedItem(item, controller) }
        }
        Choices {
            if (held.isNotEmpty()) {
                val empty = stringResource(Res.string.trash_empty_all)
                PanelButton(
                    text = empty,
                    description = empty,
                    onClick = { controller.ask(BinderPrompt.ConfirmEmpty(null)) },
                )
            }
            val close = stringResource(Res.string.close)
            PanelButton(text = close, description = close, onClick = onClose)
        }
    }
}

@Composable
private fun TrashedItem(
    item: Trashed,
    controller: ProjectController,
) {
    val palette = LocalPalette.current
    val restore = stringResource(Res.string.trash_restore)
    val forever = stringResource(Res.string.trash_delete_forever)
    Column(verticalArrangement = Arrangement.spacedBy(gap)) {
        BasicText(item.entry.name, style = interfaceTextStyle(color = palette.ink, fontSize = bodySize))
        BasicText(
            stringResource(Res.string.trash_from, item.entry.folder),
            style = interfaceTextStyle(color = palette.muted, fontSize = smallSize),
        )
        Choices {
            PanelButton(
                text = restore,
                description =
                    stringResource(
                        Res.string.said_after,
                        restore,
                        item.entry.name,
                    ),
                onClick = {
                    controller.ask(null)
                    controller.trashCan.restore(item)
                },
            )
            PanelButton(
                text = forever,
                description = stringResource(Res.string.said_after, forever, item.entry.name),
                onClick = { controller.ask(BinderPrompt.ConfirmEmpty(item)) },
            )
        }
    }
}

/**
 * The shape every question takes: a raised panel, like the conflict dialog, named as a pane so its
 * arrival is announced.
 */
@Composable
private fun PromptCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val palette = LocalPalette.current
    Column(
        modifier
            .widthIn(max = maxWidth)
            .raised(palette.panel, Corners.Container, Elevation.Floating)
            .padding(cardPadding)
            .semantics { paneTitle = title },
        verticalArrangement = Arrangement.spacedBy(rowGap),
    ) {
        BasicText(
            title,
            style = interfaceTextStyle(color = palette.ink, fontSize = titleSize, fontWeight = Prose.H5.weight),
        )
        content()
    }
}

/** Wraps rather than overflowing: two answers do not always fit one line at 200% text (10.2). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Choices(content: @Composable () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(gap), verticalArrangement = Arrangement.spacedBy(gap)) {
        content()
    }
}

@Composable
private fun CancelButton(onCancel: () -> Unit) {
    val cancel = stringResource(Res.string.cancel)
    PanelButton(text = cancel, description = cancel, onClick = onCancel)
}

private val maxWidth = 480.dp
private val listHeight = 320.dp
private val cardPadding = 20.dp
private val fieldPadding = 12.dp
private val rowGap = 16.dp
private val gap = 8.dp
private val corner = 6.dp
private val hairline = 1.dp
private val target = 48.dp
private val bodySize = 15.sp
private val smallSize = 13.sp
private val titleSize = 16.sp
