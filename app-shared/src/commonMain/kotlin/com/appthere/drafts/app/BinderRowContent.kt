package com.appthere.drafts.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.appthere.drafts.design.LocalPalette
import com.appthere.drafts.design.Lucide
import com.appthere.drafts.design.interfaceTextStyle
import com.appthere.drafts.i18n.resources.Res
import com.appthere.drafts.i18n.resources.binder_actions
import com.appthere.drafts.i18n.resources.binder_collapsed
import com.appthere.drafts.i18n.resources.binder_delete
import com.appthere.drafts.i18n.resources.binder_dismiss
import com.appthere.drafts.i18n.resources.binder_expanded
import com.appthere.drafts.i18n.resources.binder_kind_folder
import com.appthere.drafts.i18n.resources.binder_kind_missing
import com.appthere.drafts.i18n.resources.binder_kind_other
import com.appthere.drafts.i18n.resources.binder_level
import com.appthere.drafts.i18n.resources.binder_loading
import com.appthere.drafts.i18n.resources.binder_locate
import com.appthere.drafts.i18n.resources.binder_move_down
import com.appthere.drafts.i18n.resources.binder_move_to
import com.appthere.drafts.i18n.resources.binder_move_up
import com.appthere.drafts.i18n.resources.binder_nested
import com.appthere.drafts.i18n.resources.binder_new
import com.appthere.drafts.i18n.resources.binder_new_folder
import com.appthere.drafts.i18n.resources.binder_open
import com.appthere.drafts.i18n.resources.binder_open_window
import com.appthere.drafts.i18n.resources.binder_position
import com.appthere.drafts.i18n.resources.binder_remove_from_order
import com.appthere.drafts.i18n.resources.binder_rename
import com.appthere.drafts.i18n.resources.binder_seen_folder
import com.appthere.drafts.i18n.resources.binder_unreadable
import com.appthere.drafts.i18n.resources.binder_unsorted
import com.appthere.drafts.i18n.resources.kind_fountain
import com.appthere.drafts.i18n.resources.kind_markdown
import com.appthere.drafts.i18n.resources.new_fountain
import com.appthere.drafts.i18n.resources.new_markdown
import com.appthere.drafts.i18n.resources.said_after
import com.appthere.drafts.platform.intents.DocumentKind
import com.appthere.drafts.project.ProjectNode
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * What a row says to assistive technology, as one item: "02-dock.fountain, Fountain, New, level 2,
 * 3 of 5" (`projects/binder`, "The binder is navigable by assistive technology").
 */
@Composable
internal fun describe(row: BinderRow): String {
    val parts =
        listOfNotNull(
            row.node.name,
            stringResource(kindWord(row.kind)),
            stringResource(Res.string.binder_unsorted).takeIf { row.unsorted },
            stringResource(Res.string.binder_new).takeIf { row.new },
            condition(row.node)?.let { stringResource(it) },
            stringResource(Res.string.binder_level, row.level.toString()),
            stringResource(Res.string.binder_position, row.position.toString(), row.siblings.toString()),
        )
    return parts.reduce { said, next -> stringResource(Res.string.said_after, said, next) }
}

/** A folder is open or shut; nothing else has a state worth saying. */
@Composable
internal fun rowState(row: BinderRow): String? =
    if (row.isFolder) {
        stringResource(
            if (row.expanded) Res.string.binder_expanded else Res.string.binder_collapsed,
        )
    } else {
        null
    }

/** What the writer can do to [row]: only what makes sense for what it is. */
@Composable
internal fun rowActions(
    row: BinderRow,
    controller: ProjectController,
): List<RowAction> {
    val node = row.node
    val path = row.path
    val folder = node is ProjectNode.Folder
    return when {
        node is ProjectNode.Missing -> {
            listOf(
                RowAction(stringResource(Res.string.binder_locate)) { controller.ask(BinderPrompt.Locate(path)) },
                RowAction(stringResource(Res.string.binder_remove_from_order)) { controller.removeFromOrder(path) },
            )
        }

        else -> {
            listOfNotNull(
                RowAction(stringResource(Res.string.binder_open)) { controller.open(node) }.takeIf { !folder },
                RowAction(stringResource(Res.string.binder_open_window)) { controller.open(node, newWindow = true) }
                    .takeIf { node is ProjectNode.Document },
                RowAction(stringResource(Res.string.binder_rename)) { controller.ask(BinderPrompt.Rename(path)) },
                RowAction(stringResource(Res.string.binder_move_up)) { controller.step(path, -1) },
                RowAction(stringResource(Res.string.binder_move_down)) { controller.step(path, 1) },
                RowAction(stringResource(Res.string.binder_move_to)) { controller.ask(BinderPrompt.MoveTo(path)) },
                RowAction(stringResource(Res.string.new_markdown)) {
                    controller.ask(BinderPrompt.Create(path, DocumentKind.Markdown, 0))
                }.takeIf { folder },
                RowAction(stringResource(Res.string.new_fountain)) {
                    controller.ask(BinderPrompt.Create(path, DocumentKind.Fountain, 0))
                }.takeIf { folder },
                RowAction(
                    stringResource(Res.string.binder_new_folder),
                ) { controller.ask(BinderPrompt.Create(path, null, 0)) }
                    .takeIf { folder },
                RowAction(
                    stringResource(Res.string.binder_dismiss),
                ) { controller.answers.dismissNew(path) }.takeIf { row.new },
                RowAction(
                    stringResource(Res.string.binder_seen_folder),
                ) { controller.answers.seenUnder(path) }.takeIf { folder },
                RowAction(stringResource(Res.string.binder_delete)) { controller.delete(node) },
            )
        }
    }
}

/**
 * What a row shows: whether a folder is open, its name, what it is in words, its marks, and a
 * button for its actions. Marks are words, in the ink colour, with a border -- never colour alone.
 */
@Composable
internal fun RowScope.RowContent(
    row: BinderRow,
    actions: List<RowAction>,
) {
    val palette = LocalPalette.current
    val missing = row.kind == RowKind.Missing

    if (row.isFolder) Disclosure(row.expanded)
    BasicText(
        text = row.node.name,
        style = interfaceTextStyle(color = if (missing) palette.muted else palette.ink, fontSize = nameSize),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f),
    )
    Mark(stringResource(kindWord(row.kind)), bordered = false)
    if (row.unsorted) Mark(stringResource(Res.string.binder_unsorted))
    if (row.new) Mark(stringResource(Res.string.binder_new))
    condition(row.node)?.let { Mark(stringResource(it)) }
    ActionsButton(row.node.name, actions)
}

/** An open folder's ▾ and a shut one's ▸, which points the way the line reads. */
@Composable
private fun Disclosure(open: Boolean) {
    val palette = LocalPalette.current
    val rightToLeft = LocalLayoutDirection.current == LayoutDirection.Rtl
    BasicText(
        text = if (open) "▾" else "▸",
        style = interfaceTextStyle(color = palette.ink, fontSize = nameSize),
        modifier = Modifier.graphicsLayer { scaleX = if (rightToLeft && !open) -1f else 1f },
    )
}

@Composable
private fun Mark(
    text: String,
    bordered: Boolean = true,
) {
    val palette = LocalPalette.current
    BasicText(
        text = text,
        style = interfaceTextStyle(color = if (bordered) palette.ink else palette.muted, fontSize = markSize),
        maxLines = 1,
        modifier =
            if (bordered) {
                Modifier.border(hairline, palette.muted, RoundedCornerShape(corner)).padding(horizontal = markPadding)
            } else {
                Modifier
            },
    )
}

/** The row's actions as a list a pointer can reach -- the same ones the keyboard and a screen reader have. */
@Composable
private fun ActionsButton(
    name: String,
    actions: List<RowAction>,
) {
    val palette = LocalPalette.current
    var open by remember { mutableStateOf(false) }

    Box {
        PanelIconButton(icon = Lucide.Menu, description = stringResource(Res.string.binder_actions, name), onClick = {
            open =
                true
        })
        if (open) {
            Popup(alignment = Alignment.TopEnd, onDismissRequest = {
                open = false
            }, properties = PopupProperties(focusable = true)) {
                Column(
                    Modifier
                        .background(palette.background)
                        .border(hairline, palette.muted, RoundedCornerShape(corner))
                        .padding(menuPadding),
                    verticalArrangement = Arrangement.spacedBy(menuGap),
                ) {
                    actions.forEach { action ->
                        PanelButton(text = action.label, description = action.label, onClick = {
                            open = false
                            action.run()
                        })
                    }
                }
            }
        }
    }
}

private fun kindWord(kind: RowKind): StringResource =
    when (kind) {
        RowKind.Folder -> Res.string.binder_kind_folder
        RowKind.Markdown -> Res.string.kind_markdown
        RowKind.Fountain -> Res.string.kind_fountain
        RowKind.Other -> Res.string.binder_kind_other
        RowKind.Missing -> Res.string.binder_kind_missing
    }

/** A folder still being read, one that cannot be, or another project's -- said, not just shown empty. */
private fun condition(node: ProjectNode): StringResource? =
    (node as? ProjectNode.Folder)?.let {
        when {
            it.loading -> Res.string.binder_loading
            it.unreadable -> Res.string.binder_unreadable
            it.nestedProject -> Res.string.binder_nested
            else -> null
        }
    }

private val hairline = 1.dp
private val corner = 4.dp
private val markPadding = 6.dp
private val menuPadding = 8.dp
private val menuGap = 4.dp
private val nameSize = 15.sp
private val markSize = 12.sp
