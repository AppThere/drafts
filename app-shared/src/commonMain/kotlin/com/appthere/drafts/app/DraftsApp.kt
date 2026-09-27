package com.appthere.drafts.app

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.unit.dp
import com.appthere.drafts.design.DraftsTheme
import com.appthere.drafts.design.ReaderSettings
import com.appthere.drafts.editor.engine.DocumentSession
import com.appthere.drafts.editor.ui.BlockEditor
import com.appthere.drafts.editor.ui.EditorState
import com.appthere.drafts.editor.ui.handleShortcut
import kotlinx.coroutines.launch

/**
 * The composition root (`appthere-drafts.md` 3: ":app-shared  Navigation, settings, composition
 * root").
 *
 * The settings are held here and not persisted. 5.5 says they are "persisted per document type",
 * which needs somewhere to persist to -- the document lifecycle of Phase 4.
 *
 * `MaterialTheme` has gone. The surface here is a document, and the three things Material was
 * providing -- a type scale, a colour scheme and a background -- are exactly what the design
 * system now provides properly, from the spec's own numbers.
 */
@Composable
fun DraftsApp(
    initialText: String,
    modifier: Modifier = Modifier,
    initialSettings: ReaderSettings = ReaderSettings(),
) {
    val editor = remember(initialText) { EditorState(DocumentSession(initialText)) }

    // No badge. A buffer that came from a string is in none of 8.4's five states, because there is
    // no file for it to be clean, dirty, conflicted, orphaned or read-only with respect to. Showing
    // "Saved" over a document that has never been anywhere would be a lie in the window chrome.
    DraftsWindow(editor = editor, initialSettings = initialSettings, onSave = null, modifier = modifier) {}
}

/**
 * The same window, for a document that came from a file.
 *
 * The difference the [document] makes is the badge 8.4 asks for and a save that can happen at all.
 */
@Composable
fun DraftsApp(
    document: OpenDocument,
    modifier: Modifier = Modifier,
    initialSettings: ReaderSettings = ReaderSettings(),
) {
    val scope = rememberCoroutineScope()

    DraftsWindow(
        editor = document.editor,
        initialSettings = initialSettings,
        onSave = { scope.launch { document.save() } },
        modifier = modifier,
    ) {
        DocumentStateBadge(document.lifecycle.state)
    }
}

/**
 * The composition root (`appthere-drafts.md` 3: ":app-shared  Navigation, settings, composition
 * root").
 *
 * The settings are held here and not persisted. 5.5 says they are "persisted per document type",
 * which needs somewhere to persist to -- still ahead, in 7.3's session file.
 *
 * `MaterialTheme` has gone. The surface here is a document, and the three things Material was
 * providing -- a type scale, a colour scheme and a background -- are exactly what the design
 * system now provides properly, from the spec's own numbers.
 */
@Composable
private fun DraftsWindow(
    editor: EditorState,
    initialSettings: ReaderSettings,
    onSave: (() -> Unit)?,
    modifier: Modifier = Modifier,
    badge: @Composable () -> Unit,
) {
    var settings by remember { mutableStateOf(initialSettings) }
    var showControls by remember { mutableStateOf(false) }
    var showLicences by remember { mutableStateOf(false) }
    val root = remember { FocusRequester() }
    val clipboard = LocalClipboardManager.current

    DraftsTheme(settings) {
        Box(
            modifier
                .fillMaxSize()
                .background(settings.palette.background)
                .focusRequester(root)
                .focusable()
                // Every shortcut in the app, in one place.
                //
                // Key events travel from the focus owner outwards, so a handler anywhere below
                // this only sees events already on their way to it -- which is none at all until
                // the reader has clicked something. The root takes focus when a document opens and
                // remains an ancestor of whatever takes it next, so this is the only position that
                // sees every keystroke. 10.2 asks for "complete keyboard operation"; that has to
                // include the first keystroke after opening a file.
                .onPreviewKeyEvent { event ->
                    when {
                        togglesControls(event) -> {
                            showControls = !showControls
                            true
                        }

                        saves(event) -> {
                            onSave?.invoke()
                            // Consumed either way. A document with nowhere to save to should not
                            // pass Ctrl+S down to the text field, which would insert nothing and
                            // leave the reader thinking the keystroke did something.
                            true
                        }

                        else -> {
                            editor.handleShortcut(event, clipboard)
                        }
                    }
                },
        ) {
            LaunchedEffect(Unit) { root.requestFocus() }

            BlockEditor(state = editor)

            // 8.4: "in the window chrome -- quietly". Top-start, away from the controls, and
            // outside the measure so it never sits on top of a line of prose.
            Box(Modifier.align(Alignment.TopStart).padding(controlsInset)) { badge() }

            if (showControls) {
                ReaderControls(
                    settings = settings,
                    onChange = { settings = it },
                    modifier = Modifier.align(Alignment.TopEnd).padding(controlsInset),
                    onShowLicences = { showLicences = true },
                )
            }

            if (showLicences) {
                Licences(
                    onClose = { showLicences = false },
                    modifier = Modifier.align(Alignment.Center).padding(controlsInset),
                )
            }
        }
    }
}

/** Far enough from the corner to read as a panel over the document rather than part of the frame. */
private val controlsInset = 16.dp

/** 8.2's explicit save, on the shortcut every editor uses for it. */
private fun saves(event: KeyEvent): Boolean =
    event.type == KeyEventType.KeyDown &&
        (event.isCtrlPressed || event.isMetaPressed) &&
        event.key == Key.S

/** 5.5's settings, on the shortcut every editor uses for them. */
private fun togglesControls(event: KeyEvent): Boolean =
    event.type == KeyEventType.KeyDown &&
        (event.isCtrlPressed || event.isMetaPressed) &&
        event.key == Key.Comma
