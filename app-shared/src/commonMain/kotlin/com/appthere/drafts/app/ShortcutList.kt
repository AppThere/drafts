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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appthere.drafts.design.LocalPalette
import com.appthere.drafts.design.Prose
import com.appthere.drafts.editor.ui.EditorShortcuts
import com.appthere.drafts.editor.ui.Shortcut
import com.appthere.drafts.i18n.Strings

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
        PanelHeader(title = Strings.KEYBOARD_SHORTCUTS, onClose = onClose)

        BasicText(Strings.SHORTCUTS_ON_A_MAC, style = TextStyle(color = palette.muted, fontSize = labelSize))

        ShortcutSection(Strings.SHORTCUTS_WRITING, EditorShortcuts.all)
        ShortcutSection(Strings.SHORTCUTS_DOCUMENT, WindowShortcuts.all + hostShortcuts)
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
            style = TextStyle(color = palette.ink, fontSize = labelSize, fontWeight = Prose.H5.weight),
            modifier = Modifier.semantics { heading() },
        )
        shortcuts.forEach { shortcut ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clearAndSetSemantics { contentDescription = "${shortcut.keys}, ${shortcut.action}" },
                horizontalArrangement = Arrangement.spacedBy(rowGap),
            ) {
                BasicText(
                    text = shortcut.keys,
                    style = TextStyle(color = palette.ink, fontSize = labelSize),
                    modifier = Modifier.widthIn(min = keysWidth),
                )
                BasicText(
                    text = shortcut.action,
                    style = TextStyle(color = palette.muted, fontSize = labelSize),
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
