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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.unit.dp
import com.appthere.drafts.design.DraftsTheme
import com.appthere.drafts.design.LocalPalette
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
 * The settings are held here. This overload has no document type to persist them under, so they
 * last as long as the window; the file-backed one below keeps them per type, as 5.5 asks.
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
        // Nowhere to keep them, which is not the same as failing to: nothing to tell the reader.
        onSettingsChange = { true },
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
    settingsStore: SettingsStore? = null,
    kind: String = DEFAULT_KIND,
) {
    val scope = rememberCoroutineScope()
    val scroll = rememberLazyListState()
    val autoHide = rememberAutoHide(document)

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

    // 8.1's other half of "caret and scroll survive with the text". Once, on open: a reader who has
    // scrolled since should not be dragged back by a recomposition.
    LaunchedEffect(document) {
        document.scrollOffset?.let { offset ->
            scroll.scrollToItem(offset / SCROLL_SCALE, offset % SCROLL_SCALE)
        }
    }

    DraftsWindow(
        editor = document.editor,
        initialSettings = initialSettings,
        // No store is a build without settings storage, not a failed write, and says nothing.
        onSettingsChange = { changed -> settingsStore?.remember(kind, changed) ?: true },
        scroll = scroll,
        onRouse = autoHide::rouse,
        chromeHidden = autoHide.hidden,
        onDocumentKey = { event ->
            // 12: "keypress of a modifier ... brings them back". Reaching for Ctrl is reaching for
            // something, whether or not the shortcut that follows is one this application knows.
            if (rouses(event)) autoHide.rouse()

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
                    scope.launch {
                        val outcome = document.save()
                        refusal = outcome as? WriteOutcome.Conflict

                        // 8.3's thirty days are counted from here. Only on a write that happened:
                        // stamping a refused save would make the snapshot prunable while it was
                        // still the only copy of the work.
                        if (outcome is WriteOutcome.Written) keeper?.noteSaved()
                    }
                    true
                }

                else -> {
                    false
                }
            }
        },
        modifier = modifier,
    ) {
        // 12: "The status indicator (8.4) is the only persistent chrome, and it's a dot" -- and
        // "**Chrome auto-hides.** On sustained typing, toolbars and rails fade out."
        //
        // The dot is what fades. The controls panel, the conflict dialog and the restore banner
        // are each summoned deliberately and stay until answered: fading something the reader just
        // asked for, or is about to Tab into, would be the interface taking it away from them.
        Box(Modifier.align(Alignment.TopStart).padding(controlsInset)) {
            FadingChrome(hidden = autoHide.hidden) { DocumentStateBadge(document.lifecycle.state) }
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
 * The settings are held here, and handed to [onSettingsChange] to be kept as the reader changes
 * them. It answers whether they were kept.
 *
 * `MaterialTheme` has gone. The surface here is a document, and the three things Material was
 * providing -- a type scale, a colour scheme and a background -- are exactly what the design
 * system now provides properly, from the spec's own numbers.
 *
 * The opt-in is for `BackHandler`, still marked experimental in Compose Multiplatform. It is the
 * common API for Android's Back; the alternative is an `expect`/`actual` pair around the same call.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun DraftsWindow(
    editor: EditorState,
    initialSettings: ReaderSettings,
    onDocumentKey: (KeyEvent) -> Boolean,
    onSettingsChange: suspend (ReaderSettings) -> Boolean,
    modifier: Modifier = Modifier,
    scroll: LazyListState = rememberLazyListState(),
    onRouse: () -> Unit = {},
    chromeHidden: Boolean = false,
    chrome: @Composable BoxScope.() -> Unit,
) {
    var settings by remember { mutableStateOf(initialSettings) }
    val scope = rememberCoroutineScope()

    // Whether the most recent change failed to reach disk. The *most recent*: a stepper pressed
    // three times is three writes in flight, and only the last one says what is on disk now. Each
    // write takes a ticket so an earlier one finishing late cannot overwrite a later answer.
    var settingsUnsaved by remember { mutableStateOf(false) }
    var settingsTicket by remember { mutableIntStateOf(0) }
    val panels = remember { Panels() }
    val root = remember { FocusRequester() }

    // A button pressed with a pointer takes focus, and the buttons that open and close panels are
    // then removed -- taking focus with them, and leaving no focus owner for key events to start
    // from. Escape and Ctrl+Comma would stop working until the reader clicked the document. So each
    // of these hands focus back to the root, where every shortcut is handled.
    val refocused: (() -> Unit) -> () -> Unit = { action ->
        {
            action()
            root.requestFocus()
        }
    }
    val clipboard = LocalClipboardManager.current

    DraftsTheme(settings) {
        Box(
            modifier
                .fillMaxSize()
                .background(LocalPalette.current.background)
                .focusRequester(root)
                .focusable()
                // 12: "Any pointer movement ... brings them back." Observed on the final pass and
                // never consumed, so this sees the event after the selection handling below has
                // had it rather than competing for it.
                //
                // There is no edge gesture here. 12 lists one and desktop has no such thing; it
                // arrives with the touch platforms.
                .pointerInput(onRouse) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Final)
                            if (event.type == PointerEventType.Move) onRouse()
                        }
                    }
                }
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
                            panels.toggleControls()
                            true
                        }

                        // Escape closes what is open, the way it does everywhere. Only when there
                        // is something: otherwise the key belongs to whatever else wants it.
                        dismisses(event) && panels.closeTopmost() -> {
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

            // Android's Back, and the edge swipe that stands for it. With nothing listening it
            // finishes the activity -- which, for a document that exists only in memory, is the
            // reader's text gone. While a panel is open, Back closes it instead.
            BackHandler(enabled = panels.anyOpen) { panels.closeTopmost() }

            BlockEditor(state = editor, scroll = scroll)

            // Whatever belongs to a document that came from a file: 8.4's badge, and 8.2's
            // refusal when there is one. Placed by the caller, because where they go depends on
            // what they are and this function does not know.
            chrome()

            // The way in that needs no keyboard. Hidden while the controls are open: the panel is
            // in the same corner, with its own Close.
            if (!panels.controls) {
                Box(Modifier.align(Alignment.TopEnd).padding(controlsInset)) {
                    FadingChrome(hidden = chromeHidden) {
                        ReaderControlsButton(enabled = !chromeHidden, onClick = refocused(panels::openControls))
                    }
                }
            }

            if (panels.controls) {
                ReaderControls(
                    settings = settings,
                    // 5.5: "persisted per document type". Written as the reader changes them, so
                    // closing the window is not a way to lose them -- which is what closing the
                    // window did until now.
                    onChange = { changed ->
                        settings = changed
                        val ticket = ++settingsTicket
                        scope.launch {
                            val kept = onSettingsChange(changed)
                            if (ticket == settingsTicket) settingsUnsaved = !kept
                        }
                    },
                    unsaved = settingsUnsaved,
                    modifier = Modifier.align(Alignment.TopEnd).padding(controlsInset),
                    onClose = refocused(panels::closeControls),
                    onShowLicences = panels::openLicences,
                )
            }

            if (panels.licences) {
                Licences(
                    onClose = refocused(panels::closeLicences),
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

/**
 * The document type a settings file is keyed by, when nobody has said which.
 *
 * Markdown, because that is what an untyped buffer is: the sample document, and anything opened
 * without an extension this application recognises.
 */
private const val DEFAULT_KIND = "markdown"

/** Far enough from the corner to read as a panel over the document rather than part of the frame. */
private val controlsInset = 16.dp

/**
 * A modifier going down, which 12 counts as reaching for the chrome.
 *
 * The key itself, not a shortcut using it: pressing Ctrl and thinking better of it is still the
 * reader looking for something.
 */
private fun rouses(event: KeyEvent): Boolean =
    event.type == KeyEventType.KeyDown &&
        event.key in
        setOf(
            Key.CtrlLeft,
            Key.CtrlRight,
            Key.ShiftLeft,
            Key.ShiftRight,
            Key.AltLeft,
            Key.AltRight,
            Key.MetaLeft,
            Key.MetaRight,
        )

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
