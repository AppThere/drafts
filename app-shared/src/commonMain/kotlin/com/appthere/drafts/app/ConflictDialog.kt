package com.appthere.drafts.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
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
import com.appthere.drafts.i18n.resources.cancel
import com.appthere.drafts.i18n.resources.conflict
import com.appthere.drafts.i18n.resources.reload
import com.appthere.drafts.i18n.resources.save_copy
import org.jetbrains.compose.resources.stringResource

/**
 * 8.2's refusal, shown to the reader.
 *
 * It appears on an attempted write and only then, which is what 8.4 means by "Surface the state in
 * the window chrome -- quietly... Dialogs only on attempted write." The badge says `conflicted` for
 * as long as the conflict lasts; this appears once, because the reader asked to save and could not.
 *
 * Keying it off the state instead would make [onCancel] useless: the document is still conflicted
 * after cancelling, so the dialog would reappear the moment it was dismissed.
 *
 * Nothing here can destroy anything. Cancelling leaves the document exactly as it was, with the
 * edits unsaved and the other version on disk; reloading is the only destructive choice and it says
 * so in the button. There is no default action and no dismissal by clicking away, because both
 * would let a reader lose work by pressing Return at the wrong moment.
 *
 * [onSaveCopy] is 8.2's "Save a copy...": the reader's version goes to a new file through 7.4's
 * *Save As*, and the version on disk is left as it is. Keeping both is the answer that loses
 * nothing, which is why it comes first, as it does in the spec. Null where the platform has no save
 * dialog to offer.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConflictDialog(
    onReload: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    onSaveCopy: (() -> Unit)? = null,
) {
    val palette = LocalPalette.current
    val refusal = stringResource(Res.string.conflict)

    Column(
        modifier
            .widthIn(max = maxWidth)
            .raised(palette.panel, Corners.Container, Elevation.Floating)
            .padding(panelPadding)
            .semantics { contentDescription = refusal },
        verticalArrangement = Arrangement.spacedBy(rowGap),
    ) {
        BasicText(
            text = refusal,
            style = interfaceTextStyle(color = palette.ink, fontSize = messageSize, fontWeight = Prose.H5.weight),
        )

        // Wraps rather than overflowing: three answers do not fit one line at 200% text (10.2).
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(buttonGap),
            verticalArrangement = Arrangement.spacedBy(buttonGap),
        ) {
            // All drawn alike. An emphasised button here would be a recommendation, and there is no
            // right answer to recommend -- which version matters is something only the reader knows.
            onSaveCopy?.let { Choice(stringResource(Res.string.save_copy), it) }
            Choice(stringResource(Res.string.reload), onReload)
            Choice(stringResource(Res.string.cancel), onCancel)
        }
    }
}

@Composable
private fun Choice(
    label: String,
    onChoose: () -> Unit,
) = PanelButton(text = label, description = label, onClick = onChoose)

private val maxWidth = 480.dp
private val panelPadding = 20.dp
private val rowGap = 16.dp
private val buttonGap = 12.dp
private val messageSize = 15.sp
