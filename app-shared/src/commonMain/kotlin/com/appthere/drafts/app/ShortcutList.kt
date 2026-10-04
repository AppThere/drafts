package com.appthere.drafts.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appthere.drafts.design.LocalPalette
import com.appthere.drafts.design.Prose
import com.appthere.drafts.design.interfaceTextStyle
import com.appthere.drafts.editor.ui.EditorShortcuts
import com.appthere.drafts.editor.ui.Shortcut
import com.appthere.drafts.i18n.resources.Res
import com.appthere.drafts.i18n.resources.keyboard_shortcuts
import com.appthere.drafts.i18n.resources.said_after
import com.appthere.drafts.i18n.resources.shortcuts_document
import com.appthere.drafts.i18n.resources.shortcuts_on_a_mac
import com.appthere.drafts.i18n.resources.shortcuts_writing
import org.jetbrains.compose.resources.stringResource

/**
 * Every keyboard shortcut, listed: 10.2's "document the full shortcut map".
 *
 * Read from the same tables the handlers match against -- [EditorShortcuts], [WindowShortcuts] and
 * the [hostShortcuts] the platform answers outside the window, like the desktop's full screen -- so
 * a shortcut that is handled is listed, and one that is listed is handled.
 *
 * Each row is one node to a screen reader, the keys and then what they do ("Ctrl+S, Save"), which
 * is how it reads to someone looking at it. The keys are Ctrl throughout, with one line saying ⌘
 * does the same on a Mac: doubling every row to say so would bury the list in its own footnote.
 */
@Composable
internal fun ShortcutList(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    hostShortcuts: List<Shortcut> = emptyList(),
) {
    val palette = LocalPalette.current

    Column(
        modifier
            // A ceiling, as the reader controls have: a phone with a keyboard attached is still a
            // phone, and a fixed width would hang off it.
            .widthIn(max = panelWidth)
            .fillMaxWidth()
            .background(palette.background)
            .border(hairline, palette.muted, RoundedCornerShape(corner))
            .padding(panelPadding)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(rowGap),
    ) {
        PanelHeader(title = stringResource(Res.string.keyboard_shortcuts), onClose = onClose)

        BasicText(
            stringResource(Res.string.shortcuts_on_a_mac),
            style = interfaceTextStyle(color = palette.muted, fontSize = labelSize),
        )

        ShortcutSection(stringResource(Res.string.shortcuts_writing), EditorShortcuts.all)
        ShortcutSection(stringResource(Res.string.shortcuts_document), WindowShortcuts.all + hostShortcuts)
    }
}

@Composable
private fun ShortcutSection(
    title: String,
    shortcuts: List<Shortcut>,
) {
    val palette = LocalPalette.current

    Column(verticalArrangement = Arrangement.spacedBy(lineGap)) {
        BasicText(
            text = title,
            style = interfaceTextStyle(color = palette.ink, fontSize = labelSize, fontWeight = Prose.H5.weight),
            modifier = Modifier.semantics { heading() },
        )
        shortcuts.forEach { shortcut ->
            val keys = shortcut.keys()
            val action = stringResource(shortcut.action)
            val described = stringResource(Res.string.said_after, keys, action)

            Row(
                Modifier
                    .fillMaxWidth()
                    .clearAndSetSemantics { contentDescription = described },
                horizontalArrangement = Arrangement.spacedBy(rowGap),
            ) {
                BasicText(
                    text = keys,
                    style = interfaceTextStyle(color = palette.ink, fontSize = labelSize),
                    modifier = Modifier.widthIn(min = keysWidth),
                )
                BasicText(
                    text = action,
                    style = interfaceTextStyle(color = palette.muted, fontSize = labelSize),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

private val panelWidth = 480.dp
private val panelPadding = 16.dp
private val rowGap = 12.dp
private val lineGap = 6.dp
private val corner = 6.dp
private val hairline = 1.dp
private val keysWidth = 120.dp
private val labelSize = 14.sp
