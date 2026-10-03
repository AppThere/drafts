package com.appthere.drafts.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.appthere.drafts.design.LocalPalette
import com.appthere.drafts.design.Lucide
import com.appthere.drafts.i18n.resources.Res
import com.appthere.drafts.i18n.resources.kind
import com.appthere.drafts.i18n.resources.kind_fountain
import com.appthere.drafts.i18n.resources.kind_markdown
import com.appthere.drafts.i18n.resources.said_after
import com.appthere.drafts.platform.intents.DocumentKind
import org.jetbrains.compose.resources.stringResource

/**
 * 7.4's kind, as a control, while a document is untitled: *Untitled · Markdown*.
 *
 * Both kinds shown, the current one filled, rather than a menu behind a single label. There are two
 * of them, and a choice that can be seen is one a reader does not have to go looking for. Each is
 * an icon -- a page for Markdown, a camera for Fountain -- and a radio button to a screen reader,
 * announced with what it chooses: "Kind, Fountain".
 *
 * [enabled] is false while the chrome has faded (12): a tap on what looks like empty page should
 * not change what the document is.
 */
@Composable
internal fun KindSwitch(
    kind: DocumentKind,
    enabled: Boolean,
    onChange: (DocumentKind) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(gap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DocumentKind.entries.forEach { option ->
            KindOption(option, selected = option == kind, enabled = enabled, onChoose = { onChange(option) })
        }
    }
}

@Composable
private fun KindOption(
    kind: DocumentKind,
    selected: Boolean,
    enabled: Boolean,
    onChoose: () -> Unit,
) {
    val palette = LocalPalette.current
    val shape = RoundedCornerShape(corner)

    // Read here rather than inside `semantics`, which is not a composition. The kind's name is
    // what a screen reader hears; the icon is what everyone else sees.
    val described = stringResource(Res.string.said_after, stringResource(Res.string.kind), labelOf(kind))

    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier
                .sizeIn(minWidth = target, minHeight = target)
                .background(if (selected) palette.accent else Color.Transparent, shape)
                .border(hairline, palette.muted, shape)
                .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onChoose)
                .semantics { contentDescription = described },
    ) {
        Image(
            painter = rememberVectorPainter(iconOf(kind)),
            contentDescription = null,
            colorFilter = ColorFilter.tint(if (selected) palette.background else palette.ink),
            modifier = Modifier.size(iconSize),
        )
    }
}

@Composable
private fun labelOf(kind: DocumentKind): String =
    when (kind) {
        DocumentKind.Markdown -> stringResource(Res.string.kind_markdown)
        DocumentKind.Fountain -> stringResource(Res.string.kind_fountain)
    }

/** A page of prose for Markdown; a camera for Fountain, which is written to be filmed. */
private fun iconOf(kind: DocumentKind): ImageVector =
    when (kind) {
        DocumentKind.Markdown -> Lucide.FileText
        DocumentKind.Fountain -> Lucide.Video
    }

/** 10.2: "Touch targets >= 48dp." */
private val target = 48.dp
private val gap = 6.dp
private val corner = 6.dp
private val hairline = 1.dp
