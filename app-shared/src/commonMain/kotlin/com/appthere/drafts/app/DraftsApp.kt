package com.appthere.drafts.app

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
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
import com.appthere.drafts.platform.files.WriteOutcome
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
    // Ctrl+S is consumed and does nothing. A buffer that came from a string has nowhere to save
    // to, and passing the keystroke down to the text field would insert nothing while leaving the
    // reader thinking it had done something.
    DraftsWindow(
        editor = editor,
        initialSettings = initialSettings,
        onDocumentKey = ::saves,
        modifier = modifier,
    ) {}
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
    keeper: SnapshotKeeper? = null,
) {
    val scope = rememberCoroutineScope()
    val scroll = rememberLazyListState()

    // 8.1's autosave, when the platform has somewhere app-private to put it. Null rather than a
    // no-op keeper so that a build without snapshot storage is visibly without it.
    if (keeper != null) {
        SnapshotEffect(
            keeper = keeper,
            revision = document.editor.revision,
            scrollOffset = { scroll.firstVisibleItemIndex * SCROLL_SCALE + scroll.firstVisibleItemScrollOffset },
        )
    }

    // A refusal, not a state. 8.4 puts the state in the chrome and allows "dialogs only on
    // attempted write", so the dialog is driven by the outcome of a save and cleared by answering
    // it. Driving it from `conflicted` instead would make Cancel do nothing -- the document is
    // still conflicted afterwards, so the dialog would come straight back.
    var refusal: WriteOutcome.Conflict? by remember(document) { mutableStateOf(null) }

    // 8.3's banner. Separate state from `restoredFromSnapshot`, which is a fact about how the
    // document opened and does not stop being true once the reader has answered.
    var announceRestored by remember(document) { mutableStateOf(document.restoredFromSnapshot) }

    DraftsWindow(
        editor = document.editor,
        initialSettings = initialSettings,
        scroll = scroll,
        onDocumentKey = { event ->
            when {
                // Escape answers the dialog the way Escape answers every dialog.
                //
                // The `refusal != null` guard is deliberate and currently untested: the editor
                // does nothing with Escape yet, so claiming the key unconditionally would have no
                // visible effect and no test can tell the difference. It stays because the day the
                // editor does want Escape -- clearing a selection is the obvious candidate -- a
                // handler that had been swallowing it since now would be a silent dead key.
                refusal != null && dismisses(event) -> {
                    refusal = null
                    true
                }

                saves(event) -> {
                    scope.launch { refusal = document.save() as? WriteOutcome.Conflict }
                    true
                }

                else -> {
                    false
                }
            }
        },
        modifier = modifier,
    ) {
        Box(Modifier.align(Alignment.TopStart).padding(controlsInset)) {
            DocumentStateBadge(document.lifecycle.state)
        }

        if (announceRestored) {
            RestoredBanner(
                onKeep = { announceRestored = false },
                onDiscard = {
                    // Back to the file, and the snapshot goes with it. Reloading without discarding
                    // would leave the snapshot to be restored again on the next launch, which is
                    // the reader being asked the same question until they answer it differently.
                    scope.launch {
                        document.reload()
                        keeper?.discard()
                        announceRestored = false
                    }
                },
                modifier = Modifier.align(Alignment.BottomCenter).padding(controlsInset),
            )
        }

        if (refusal != null) {
            ConflictDialog(
                onReload = {
                    scope.launch {
                        document.reload()
                        refusal = null
                    }
                },
                onCancel = { refusal = null },
                modifier = Modifier.align(Alignment.Center).padding(controlsInset),
            )
        }
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
    onDocumentKey: (KeyEvent) -> Boolean,
    modifier: Modifier = Modifier,
    scroll: LazyListState = rememberLazyListState(),
    chrome: @Composable BoxScope.() -> Unit,
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

                        // Anything that depends on there being a file: saving, and answering
                        // 8.2's refusal. The window does not know whether it has one, so it asks.
                        onDocumentKey(event) -> {
                            true
                        }

                        else -> {
                            editor.handleShortcut(event, clipboard)
                        }
                    }
                },
        ) {
            LaunchedEffect(Unit) { root.requestFocus() }

            BlockEditor(state = editor, scroll = scroll)

            // Whatever belongs to a document that came from a file: 8.4's badge, and 8.2's
            // refusal when there is one. Placed by the caller, because where they go depends on
            // what they are and this function does not know.
            chrome()

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

/**
 * 7.3 stores `scrollOffset` as a single number, and a LazyColumn's position is an index plus an
 * offset within that item. Folding them together keeps the field one number, at the cost of
 * assuming no block is taller than this -- which restores to the right block and, for a very tall
 * one, somewhere inside it.
 */
private const val SCROLL_SCALE = 100_000

/** Far enough from the corner to read as a panel over the document rather than part of the frame. */
private val controlsInset = 16.dp

/** Escape, which cancels whatever is being asked. */
private fun dismisses(event: KeyEvent): Boolean = event.type == KeyEventType.KeyDown && event.key == Key.Escape

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
