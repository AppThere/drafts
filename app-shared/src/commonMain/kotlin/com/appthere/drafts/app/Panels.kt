package com.appthere.drafts.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appthere.drafts.design.Corners
import com.appthere.drafts.design.LocalPalette
import com.appthere.drafts.design.Lucide
import com.appthere.drafts.design.Prose
import com.appthere.drafts.design.interfaceTextStyle
import com.appthere.drafts.i18n.resources.Res
import com.appthere.drafts.i18n.resources.close
import com.appthere.drafts.i18n.resources.open_reader_controls
import org.jetbrains.compose.resources.stringResource

/**
 * The panels that open over a document -- the reader controls, the licences, the keyboard
 * shortcuts and a screenplay's scene-heading words -- and which of them is showing.
 *
 * One rule for closing, whatever asked: the one on top goes first. The licences, the shortcuts and
 * the scene headings open from the controls, so they are on top whenever the controls are showing too, and Escape or
 * Back takes them away and leaves the reader where they were. The shortcuts are drawn last, so they
 * go first.
 */
@Stable
internal class Panels {
    var controls by mutableStateOf(false)
        private set

    var licences by mutableStateOf(false)
        private set

    var shortcuts by mutableStateOf(false)
        private set

    /** 11.3's scene-heading words, opened from the controls of a screenplay. */
    var headings by mutableStateOf(false)
        private set

    /**
     * 10.1's outline. Unlike the other three it is not always over the document: on a window wide
     * enough for 6's two panes it sits beside it and the document narrows. Open is open either way.
     */
    var outline by mutableStateOf(false)
        private set

    val anyOpen: Boolean get() = controls || licences || shortcuts || headings || outline

    fun toggleControls() {
        controls = !controls
    }

    fun openControls() {
        controls = true
    }

    fun closeControls() {
        controls = false
    }

    fun openLicences() {
        licences = true
    }

    fun closeLicences() {
        licences = false
    }

    fun openHeadings() {
        headings = true
    }

    fun closeHeadings() {
        headings = false
    }

    fun toggleShortcuts() {
        shortcuts = !shortcuts
    }

    fun openShortcuts() {
        shortcuts = true
    }

    fun closeShortcuts() {
        shortcuts = false
    }

    fun toggleOutline() {
        outline = !outline
    }

    fun openOutline() {
        outline = true
    }

    fun closeOutline() {
        outline = false
    }

    /**
     * The keys that open and close panels, answered; false for any other key.
     *
     * Escape closes what is open, the way it does everywhere -- only when there is something, so
     * that otherwise the key goes on to whatever else wants it.
     */
    fun answer(event: KeyEvent): Boolean =
        when {
            WindowShortcuts.ReaderControls.matches(event) -> {
                toggleControls()
                true
            }

            WindowShortcuts.KeyboardShortcuts.matches(event) -> {
                toggleShortcuts()
                true
            }

            WindowShortcuts.Outline.matches(event) -> {
                toggleOutline()
                true
            }

            WindowShortcuts.Dismiss.matches(event) -> {
                closeTopmost()
            }

            else -> {
                false
            }
        }

    /** Closes whichever panel is on top, and says whether there was one. */
    fun closeTopmost(): Boolean =
        when {
            shortcuts -> {
                shortcuts = false
                true
            }

            licences -> {
                licences = false
                true
            }

            headings -> {
                headings = false
                true
            }

            controls -> {
                controls = false
                true
            }

            // Last, because it is the one that can be beside the document rather than over it: a
            // reader with the outline open and the controls over the top of it means to close the
            // controls.
            outline -> {
                outline = false
                true
            }

            else -> {
                false
            }
        }
}

/**
 * A panel's title with its Close button.
 *
 * Every panel needs a way out that does not depend on a keyboard. A reader on a phone has no
 * Escape key, and 10.2's "complete keyboard operation" is only half of it: the other half is that
 * nothing is reachable *only* by keyboard.
 */
@Composable
internal fun PanelHeader(
    title: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current

    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier.fillMaxWidth()) {
        BasicText(
            text = title,
            style = interfaceTextStyle(color = palette.ink, fontSize = headingSize, fontWeight = Prose.H4.weight),
            modifier = Modifier.weight(1f),
        )
        PanelButton(
            text = stringResource(Res.string.close),
            description = stringResource(Res.string.close),
            onClick = onClose,
        )
    }
}

/**
 * What opens the reader controls without a keyboard.
 *
 * In the corner the controls open into, so the button and the panel it stands for are in the same
 * place. It is chrome, so it fades with the rest of the chrome on sustained typing (12) and cannot
 * be pressed while faded -- a tap on an empty-looking corner should not open anything.
 */
@Composable
internal fun ReaderControlsButton(
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PanelIconButton(
        icon = Lucide.Menu,
        description = stringResource(Res.string.open_reader_controls),
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
    )
}

/**
 * A way from one panel to another, set as a link in the panel's own text.
 *
 * A real target rather than a line of small print: 48dp tall, and a button to a screen reader.
 */
@Composable
fun PanelLink(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current

    BasicText(
        text = text,
        style = interfaceTextStyle(color = palette.accent, fontSize = labelSize),
        modifier =
            modifier
                .sizeIn(minHeight = target)
                .clickable(onClick = onClick)
                .padding(vertical = linkPadding)
                .semantics {
                    contentDescription = text
                    role = Role.Button
                },
    )
}

/**
 * A filled pill at 10.2's 48dp target: the one shape of text button the interface uses, in panels,
 * banners and 8.2's dialog alike.
 *
 * Filled in the palette's control tone rather than outlined: a button is a thing on the panel, not
 * a box drawn on it. Every button is drawn the same, so none is presented as the one to press.
 */
@Composable
internal fun PanelButton(
    text: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val palette = LocalPalette.current

    BasicText(
        text = text,
        style = interfaceTextStyle(color = palette.ink, fontSize = labelSize, textAlign = TextAlign.Center),
        modifier =
            modifier
                .sizeIn(minWidth = target, minHeight = target)
                .clip(Corners.Pill)
                .background(palette.control)
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = buttonPaddingAcross, vertical = buttonPadding)
                .semantics {
                    contentDescription = description
                    role = Role.Button
                },
    )
}

/**
 * An icon in place of the word, for the chrome bar, where a phone has room for four of these and
 * not for four words.
 *
 * The word is not lost: it is the [description], which is what a screen reader announces and what
 * the button is found by. The icon takes the ink colour, so it follows the reader's theme (5.6).
 *
 * No fill of its own: these sit together in the chrome bar's raised pill, and a fill on each would
 * be a row of buttons in a box.
 *
 * [toggled] is for a button that stands for a panel, which it opens and closes -- the outline beside
 * the text, the binder's drawer -- and is null for every other button. While the panel is open it
 * is filled with the accent and says so to a screen reader as a toggle that is on. A toggle, not
 * only `selected`: Compose on Android reports `selected` for tabs and options and drops it for a
 * button, so TalkBack heard nothing about the outline being open (found on a Chromebook, 2026-10-11).
 * Null rather than false for the rest, so an ordinary button is not announced as a switch that is off.
 */
@Composable
internal fun PanelIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    toggled: Boolean? = null,
) {
    val palette = LocalPalette.current
    val on = toggled == true

    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .sizeIn(minWidth = target, minHeight = target)
                .clip(Corners.Pill)
                .background(if (on) palette.accent else Color.Transparent)
                .clickable(enabled = enabled, onClick = onClick)
                .semantics {
                    contentDescription = description
                    role = Role.Button
                    toggled?.let {
                        selected = it
                        toggleableState = ToggleableState(it)
                    }
                },
    ) {
        Image(
            painter = rememberVectorPainter(icon),
            contentDescription = null,
            colorFilter = ColorFilter.tint(if (on) palette.background else palette.ink),
            modifier = Modifier.size(iconSize),
        )
    }
}

/** Lucide's own size, and half of 10.2's target: the rest is padding a thumb can land on. */
internal val iconSize = 24.dp

/** 10.2: "Touch targets >= 48dp." */
private val target = 48.dp
private val buttonPadding = 12.dp
private val buttonPaddingAcross = 20.dp
private val labelSize = 14.sp
private val headingSize = 18.sp
private val linkPadding = 10.dp
