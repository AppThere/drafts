package com.appthere.drafts.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appthere.drafts.design.LocalPalette
import com.appthere.drafts.i18n.Strings
import com.appthere.drafts.platform.intents.DocumentKind

/**
 * 7.4's kind, as a control, while a document is untitled: *Untitled · Markdown*.
 *
 * Both kinds shown, the current one filled, rather than a menu behind a single label. There are two
 * of them, and a choice that can be seen is one a reader does not have to go looking for. Each is a
 * radio button to a screen reader, announced with what it chooses: "Kind, Fountain".
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
    val label = labelOf(kind)
    val shape = RoundedCornerShape(corner)

    BasicText(
        text = label,
        style = TextStyle(color = if (selected) palette.background else palette.ink, fontSize = labelSize),
        modifier =
            Modifier
                .sizeIn(minWidth = target, minHeight = target)
                .background(if (selected) palette.accent else Color.Transparent, shape)
                .border(hairline, palette.muted, shape)
                .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onChoose)
                .padding(optionPadding)
                .semantics { contentDescription = "${Strings.KIND}, $label" },
    )
}

private fun labelOf(kind: DocumentKind): String =
    when (kind) {
        DocumentKind.Markdown -> Strings.KIND_MARKDOWN
        DocumentKind.Fountain -> Strings.KIND_FOUNTAIN
    }

/** 10.2: "Touch targets >= 48dp." */
private val target = 48.dp
private val gap = 6.dp
private val corner = 6.dp
private val hairline = 1.dp
private val optionPadding = 12.dp
private val labelSize = 12.sp
