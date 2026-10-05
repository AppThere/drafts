package com.appthere.drafts.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.appthere.drafts.core.fountain.FountainKeywords
import com.appthere.drafts.design.LocalWindowSize
import com.appthere.drafts.design.Lucide
import com.appthere.drafts.design.ReaderSettings
import com.appthere.drafts.design.WidthClass
import com.appthere.drafts.i18n.resources.Res
import com.appthere.drafts.i18n.resources.keyboard_shortcuts
import com.appthere.drafts.i18n.resources.licences
import com.appthere.drafts.i18n.resources.new_document
import com.appthere.drafts.i18n.resources.open_document
import com.appthere.drafts.i18n.resources.open_outline
import com.appthere.drafts.i18n.resources.scene_headings
import org.jetbrains.compose.resources.stringResource

/**
 * The ways in to the reader's panels that need no keyboard, at the end of the chrome bar.
 *
 * They fade with the rest of the chrome (12), and cannot be pressed while faded. Gone while the
 * controls are open: on a wide window the panel is in the same corner with its own Close, and on a
 * phone it is a sheet that has one.
 *
 * Icons, so that with 7.4's kind switch the whole bar fits one line on a phone. They still wrap
 * rather than overflow, for a phone at 200% text.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PanelButtons(
    panels: Panels,
    hidden: Boolean,
    refocused: (() -> Unit) -> () -> Unit,
) {
    if (panels.controls) return

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(buttonGap, Alignment.End),
        verticalArrangement = Arrangement.spacedBy(buttonGap),
    ) {
        // 10.1 calls the outline an accessibility feature rather than a convenience, so it gets a
        // way in that needs no keyboard, beside the one the controls have.
        PanelIconButton(
            icon = Lucide.ListTree,
            description = stringResource(Res.string.open_outline),
            onClick = refocused(panels::toggleOutline),
            enabled = !hidden,
        )
        ReaderControlsButton(enabled = !hidden, onClick = refocused(panels::openControls))
    }
}

/**
 * The reader's panels, placed by 6's rule: a sheet along the bottom on a phone, a panel beside the
 * document anywhere wider. They do not fade: a panel is summoned deliberately and stays until
 * answered, and fading something the reader just asked for would be the interface taking it away
 * from them.
 *
 * [refocused] hands focus back to the window root after each of these, because a button pressed
 * with a pointer takes focus and is then removed -- leaving no focus owner for 10.2's shortcuts to
 * arrive at.
 */
@Composable
internal fun BoxScope.WindowPanels(
    panels: Panels,
    settings: WindowSettings,
    host: HostActions,
    onSettingsChange: suspend (ReaderSettings) -> Boolean,
    refocused: (() -> Unit) -> () -> Unit,
    keywords: FountainKeywords? = null,
    onKeywordsChange: (suspend (FountainKeywords) -> Boolean)? = null,
) {
    // 11.3's panel, for a screenplay whose words can be changed: prose has no scene headings.
    val headings = onKeywordsChange.takeIf { keywords != null }
    val scope = rememberCoroutineScope()

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
            // 7.1's other windows, here because 12 allows no other chrome to put them in. The
            // panel closes first: the reader is going to another window, not staying in this one.
            host.newDocument?.let { create ->
                PanelLink(
                    stringResource(Res.string.new_document),
                    onClick =
                        refocused {
                            panels.closeControls()
                            create()
                        },
                )
            }
            host.openDocument?.let { open ->
                PanelLink(
                    stringResource(Res.string.open_document),
                    onClick =
                        refocused {
                            panels.closeControls()
                            open()
                        },
                )
            }
            headings?.let { PanelLink(stringResource(Res.string.scene_headings), onClick = panels::openHeadings) }
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

    if (panels.headings && headings != null && keywords != null) {
        SceneHeadings(
            keywords = keywords,
            onChange = headings,
            onClose = refocused(panels::closeHeadings),
            modifier = panelPlacement(Alignment.TopEnd),
        )
    }

    if (panels.shortcuts) {
        ShortcutList(
            onClose = refocused(panels::closeShortcuts),
            modifier = panelPlacement(Alignment.Center),
            hostShortcuts = host.listed,
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
internal fun BoxScope.panelPlacement(roomy: Alignment): Modifier =
    if (LocalWindowSize.current.width == WidthClass.Compact) {
        Modifier.align(Alignment.BottomCenter).fillMaxWidth()
    } else {
        Modifier.align(roomy).padding(controlsInset)
    }

/** Between the outline button and the reader controls button. */
private val buttonGap = 8.dp
