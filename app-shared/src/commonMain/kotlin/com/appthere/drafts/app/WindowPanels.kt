package com.appthere.drafts.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.appthere.drafts.design.LocalWindowSize
import com.appthere.drafts.design.ReaderSettings
import com.appthere.drafts.design.WidthClass
import com.appthere.drafts.editor.ui.Shortcut
import com.appthere.drafts.i18n.resources.Res
import com.appthere.drafts.i18n.resources.keyboard_shortcuts
import com.appthere.drafts.i18n.resources.licences
import org.jetbrains.compose.resources.stringResource

/**
 * The reader's panels, and the way in to them that needs no keyboard.
 *
 * Together because they are one thing from the reader's side -- the button and what it opens --
 * and because they share 6's placement rule: a sheet along the bottom on a phone, a panel beside
 * the document anywhere wider. 12's fade applies to the button and not to the panels: a panel is
 * summoned deliberately and stays until answered, and fading something the reader just asked for
 * would be the interface taking it away from them.
 *
 * [refocused] hands focus back to the window root after each of these, because a button pressed
 * with a pointer takes focus and is then removed -- leaving no focus owner for 10.2's shortcuts to
 * arrive at.
 */
@Composable
internal fun BoxScope.WindowPanels(
    panels: Panels,
    settings: WindowSettings,
    hidden: Boolean,
    hostShortcuts: List<Shortcut>,
    onSettingsChange: suspend (ReaderSettings) -> Boolean,
    refocused: (() -> Unit) -> () -> Unit,
) {
    val scope = rememberCoroutineScope()

    // Hidden while the controls are open: the panel is in the same corner, with its own Close.
    if (!panels.controls) {
        Box(Modifier.align(Alignment.TopEnd).padding(controlsInset)) {
            FadingChrome(hidden = hidden) {
                ReaderControlsButton(enabled = !hidden, onClick = refocused(panels::openControls))
            }
        }
    }

    if (panels.controls) {
        ReaderControls(
            settings = settings.current,
            // 5.5: "persisted per document type". Written as the reader changes them, so closing
            // the window is not a way to lose them.
            onChange = { changed -> settings.change(changed, scope, onSettingsChange) },
            unsaved = settings.unsaved,
            modifier = panelPlacement(Alignment.TopEnd),
            onClose = refocused(panels::closeControls),
        ) {
            PanelLink(stringResource(Res.string.keyboard_shortcuts), onClick = panels::openShortcuts)
            PanelLink(stringResource(Res.string.licences), onClick = panels::openLicences)
        }
    }

    if (panels.licences) {
        Licences(
            onClose = refocused(panels::closeLicences),
            modifier = panelPlacement(Alignment.Center),
        )
    }

    if (panels.shortcuts) {
        ShortcutList(
            onClose = refocused(panels::closeShortcuts),
            modifier = panelPlacement(Alignment.Center),
            hostShortcuts = hostShortcuts,
        )
    }
}

/**
 * Where a panel sits, which 6 makes a question about how wide the window is.
 *
 * Compact: "Outline and settings as modal sheets", anchored to the bottom edge and reaching both
 * sides. A phone held one-handed has its thumb at the bottom of the screen and a corner panel puts
 * every control at the far end of it; a sheet puts them where the hand already is.
 *
 * Medium and Expanded: where the panel was, which is beside the thing it belongs to. There is room
 * for a panel not to cover the document, and a sheet that covered the foot of a wide window would
 * be hiding text for no reason.
 */
@Composable
private fun BoxScope.panelPlacement(roomy: Alignment): Modifier =
    if (LocalWindowSize.current.width == WidthClass.Compact) {
        Modifier.align(Alignment.BottomCenter).fillMaxWidth()
    } else {
        Modifier.align(roomy).padding(controlsInset)
    }
