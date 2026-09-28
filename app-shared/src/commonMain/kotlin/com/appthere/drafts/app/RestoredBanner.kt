package com.appthere.drafts.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appthere.drafts.design.LocalPalette
import com.appthere.drafts.i18n.Strings

/**
 * 8.3's banner: "*Unsaved changes from your last session have been restored.*"
 *
 * Unobtrusive is the spec's word and the design constraint. It sits at the foot of the window, it
 * does not take focus, and the document underneath is already editable -- the reader can carry on
 * typing without answering it, which is the difference between a banner and a dialog. 8.4 reserves
 * dialogs for attempted writes, and nothing has been attempted here.
 *
 * Restoring has already happened by the time this appears. That is deliberate and is what "Never
 * auto-discard a snapshot" means in practice: the safe default is the reader's words on screen, and
 * the banner exists to say so rather than to ask permission for it.
 *
 * `Compare` from the spec's `[ Compare ] [ Discard ]` is absent: it needs a diff view that does not
 * exist, and a button that does nothing is worse than one that is not offered.
 */
@Composable
fun RestoredBanner(
    onDiscard: () -> Unit,
    onKeep: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current

    Row(
        modifier
            .widthIn(max = maxWidth)
            .background(palette.background)
            .border(hairline, palette.muted, RoundedCornerShape(corner))
            .padding(bannerPadding)
            .semantics { contentDescription = Strings.RESTORED },
        horizontalArrangement = Arrangement.spacedBy(gap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(
            text = Strings.RESTORED,
            style = TextStyle(color = palette.ink, fontSize = messageSize),
            modifier = Modifier.widthIn(max = messageWidth),
        )

        Choice(label = Strings.KEEP, onChoose = onKeep)
        Choice(label = Strings.DISCARD, onChoose = onDiscard)
    }
}

/**
 * One of the two answers.
 *
 * Drawn alike, and Discard last rather than first. It is the destructive one, and the spec's own
 * ordering puts the non-destructive choice nearer the message.
 */
@Composable
private fun Choice(
    label: String,
    onChoose: () -> Unit,
) {
    val palette = LocalPalette.current

    BasicText(
        text = label,
        style = TextStyle(color = palette.ink, fontSize = labelSize, textAlign = TextAlign.Center),
        modifier =
            Modifier
                .sizeIn(minWidth = target, minHeight = target)
                .border(hairline, palette.muted, RoundedCornerShape(corner))
                .clickable { onChoose() }
                .padding(buttonPadding)
                .semantics { contentDescription = label },
    )
}

private val maxWidth = 620.dp
private val messageWidth = 360.dp
private val bannerPadding = 12.dp
private val buttonPadding = 12.dp
private val gap = 12.dp
private val corner = 6.dp
private val hairline = 1.dp
private val target = 48.dp
private val labelSize = 14.sp
private val messageSize = 14.sp
