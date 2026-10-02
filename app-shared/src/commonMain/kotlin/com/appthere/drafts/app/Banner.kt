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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appthere.drafts.design.LocalPalette
import com.appthere.drafts.i18n.resources.Res
import com.appthere.drafts.i18n.resources.close
import com.appthere.drafts.i18n.resources.could_not_save
import com.appthere.drafts.i18n.resources.discard
import com.appthere.drafts.i18n.resources.file_gone
import com.appthere.drafts.i18n.resources.keep
import com.appthere.drafts.i18n.resources.restored
import com.appthere.drafts.i18n.resources.save_as_choice
import org.jetbrains.compose.resources.stringResource

/**
 * A message at the foot of the window, with the reader's possible answers beside it.
 *
 * Unobtrusive is the spec's word and the design constraint. It does not take focus, and the
 * document underneath stays editable -- the reader can carry on typing without answering it, which
 * is the difference between a banner and a dialog. 8.4 reserves dialogs for a write that was
 * refused, and a banner is for everything the reader should know that is not that.
 *
 * [announce] makes it a polite live region, for a message that arrives while the reader is doing
 * something else and would otherwise reach a screen reader only if its user went looking.
 */
@Composable
private fun Banner(
    message: String,
    choices: List<Pair<String, () -> Unit>>,
    modifier: Modifier = Modifier,
    announce: Boolean = false,
) {
    val palette = LocalPalette.current

    Row(
        modifier
            .widthIn(max = maxWidth)
            .background(palette.background)
            .border(hairline, palette.muted, RoundedCornerShape(corner))
            .padding(bannerPadding)
            .semantics {
                contentDescription = message
                if (announce) liveRegion = LiveRegionMode.Polite
            },
        horizontalArrangement = Arrangement.spacedBy(gap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(
            text = message,
            style = TextStyle(color = palette.ink, fontSize = messageSize),
            modifier = Modifier.widthIn(max = messageWidth),
        )

        choices.forEach { (label, onChoose) -> Choice(label = label, onChoose = onChoose) }
    }
}

/**
 * 8.3's banner: "*Unsaved changes from your last session have been restored.*"
 *
 * Restoring has already happened by the time this appears. That is deliberate and is what "Never
 * auto-discard a snapshot" means in practice: the safe default is the reader's words on screen, and
 * the banner exists to say so rather than to ask permission for it.
 *
 * `Compare` from the spec's `[ Compare ] [ Discard ]` is absent: it needs a diff view that does not
 * exist, and a button that does nothing is worse than one that is not offered. Discard is last: it
 * is the destructive one, and the spec's own ordering puts the non-destructive choice nearer the
 * message.
 */
@Composable
fun RestoredBanner(
    onDiscard: () -> Unit,
    onKeep: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Banner(
        message = stringResource(Res.string.restored),
        choices = listOf(stringResource(Res.string.keep) to onKeep, stringResource(Res.string.discard) to onDiscard),
        modifier = modifier,
    )
}

/**
 * 7.3's vanished file: "A document whose file has vanished opens ... from its snapshot with a clear
 * banner offering *Save As*."
 *
 * Said as what it means: the words are here, and the file they came from is not. The one answer is
 * the one that fixes it -- there is no Dismiss, because a reader who does not choose a file for
 * these words is a reader whose words are in a snapshot and nowhere else.
 */
@Composable
fun FileGoneBanner(
    onSaveAs: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Banner(
        message = stringResource(Res.string.file_gone),
        choices = listOf(stringResource(Res.string.save_as_choice) to onSaveAs),
        modifier = modifier,
        announce = true,
    )
}

/**
 * A save that did not happen, said out loud.
 *
 * In words about what it means for the reader, not what went wrong on disk: the words are not in
 * the file, and they are safe where they are. A polite live region, because the failure arrives
 * after the keystroke that asked for it and a screen reader user would otherwise never hear of it.
 */
@Composable
fun SaveFailedBanner(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Banner(
        message = stringResource(Res.string.could_not_save),
        choices = listOf(stringResource(Res.string.close) to onDismiss),
        modifier = modifier,
        announce = true,
    )
}

/** One of the answers. Drawn alike, so none is presented as the one to press. */
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
