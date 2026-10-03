package com.appthere.drafts.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appthere.drafts.design.LocalPalette
import com.appthere.drafts.design.Prose
import com.appthere.drafts.editor.ui.OutlineEntry
import com.appthere.drafts.i18n.resources.Res
import com.appthere.drafts.i18n.resources.block_heading_level
import com.appthere.drafts.i18n.resources.outline
import com.appthere.drafts.i18n.resources.outline_empty_heading
import com.appthere.drafts.i18n.resources.outline_no_headings
import com.appthere.drafts.i18n.resources.said_after
import org.jetbrains.compose.resources.stringResource

/**
 * `appthere-drafts.md` 10.1's outline: the document's headings as a navigable list.
 *
 * "Disproportionately valuable for screen reader users, who cannot skim. Treat it as an
 * accessibility feature, not a convenience feature." So every entry is a button at 10.2's 48dp,
 * reachable by keyboard, and announced with its level -- "Heading level 2, Chapter one" -- because
 * the indentation that says so to the eye says nothing to anything else.
 *
 * A `LazyColumn`, for the same reason the document is one: a book's outline is hundreds of lines.
 *
 * [onGo] is handed the entry the reader chose. What going there means belongs to the window, which
 * owns the scroll position and the caret.
 */
@Composable
internal fun Outline(
    entries: List<OutlineEntry>,
    onGo: (OutlineEntry) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current

    // A pane, and named as one, like the reader controls: assistive technology announces a pane by
    // its title when it appears, which is what says the outline has opened rather than that focus
    // has moved somewhere unexplained.
    val title = stringResource(Res.string.outline)

    Column(
        modifier
            .semantics { paneTitle = title }
            .background(palette.background)
            .border(hairline, palette.muted, RoundedCornerShape(corner))
            .padding(panelPadding),
        verticalArrangement = Arrangement.spacedBy(rowGap),
    ) {
        PanelHeader(title = title, onClose = onClose)

        if (entries.isEmpty()) {
            BasicText(
                text = stringResource(Res.string.outline_no_headings),
                style = TextStyle(color = palette.muted, fontSize = labelSize),
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(rowGap)) {
                items(entries, key = { it.id.value }) { entry -> Entry(entry, onGo) }
            }
        }
    }
}

/**
 * One heading in the list.
 *
 * Indented by level for the eye and named by level for everything else. The text is one line: an
 * outline whose entries wrap stops being skimmable, which is the whole point of it.
 */
@Composable
private fun Entry(
    entry: OutlineEntry,
    onGo: (OutlineEntry) -> Unit,
) {
    val palette = LocalPalette.current
    val words = entry.text.ifEmpty { stringResource(Res.string.outline_empty_heading) }
    val described =
        stringResource(
            Res.string.said_after,
            stringResource(Res.string.block_heading_level, entry.level.toString()),
            words,
        )

    BasicText(
        text = words,
        style =
            TextStyle(
                color = if (entry.text.isEmpty()) palette.muted else palette.ink,
                fontSize = labelSize,
                fontWeight = if (entry.level == 1) Prose.H5.weight else null,
            ),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier =
            Modifier
                .fillMaxWidth()
                .sizeIn(minHeight = target)
                .clickable { onGo(entry) }
                .padding(start = indent * (entry.level - 1), top = textPadding, bottom = textPadding)
                .semantics {
                    contentDescription = described
                    role = Role.Button
                },
    )
}

/** 10.2: "Touch targets >= 48dp." */
private val target = 48.dp
private val hairline = 1.dp
private val corner = 6.dp
private val panelPadding = 16.dp
private val rowGap = 4.dp
private val textPadding = 12.dp

/** One step of indentation per heading level, enough to read as a hierarchy and no more. */
private val indent = 12.dp
private val labelSize = 14.sp
