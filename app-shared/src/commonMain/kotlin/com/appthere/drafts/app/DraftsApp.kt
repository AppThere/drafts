package com.appthere.drafts.app

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.unit.dp
import com.appthere.drafts.design.DraftsTheme
import com.appthere.drafts.design.Fold
import com.appthere.drafts.design.LocalPalette
import com.appthere.drafts.design.LocalWindowSize
import com.appthere.drafts.design.ReaderSettings
import com.appthere.drafts.design.WindowSize
import com.appthere.drafts.design.clearanceWithin
import com.appthere.drafts.editor.engine.DocumentSession
import com.appthere.drafts.editor.ui.BlockEditor
import com.appthere.drafts.editor.ui.EditorState
import com.appthere.drafts.editor.ui.Shortcut
import com.appthere.drafts.editor.ui.handleShortcut
import com.appthere.drafts.i18n.Strings
import com.appthere.drafts.platform.files.WriteOutcome
import com.appthere.drafts.platform.intents.DocumentKind
import com.appthere.drafts.platform.windows.currentFold
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
    fold: Fold? = currentFold(),
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
        onDocumentKey = WindowShortcuts.Save::matches,
        // Nowhere to keep them, which is not the same as failing to: nothing to tell the reader.
        onSettingsChange = { true },
        fold = fold,
        modifier = modifier,
    ) {}
}

/**
 * The same window, for a document with a session: one from a file, or an untitled one (7.4).
 *
 * The difference the [document] makes is the badge 8.4 asks for and a save that can happen at all.
 *
 * [saveAs] is the host's *Save As*: it asks the reader where, through the platform's own picker,
 * saves there, and returns what happened -- null if the reader cancelled. It belongs to the host
 * because the picker does; without one, an untitled document has nowhere to go and Ctrl+S does
 * nothing, which is the honest answer on a platform that has no picker yet.
 *
 * [onKindChange] is 7.4's choice of kind while the document is untitled. The host records it; the
 * window only offers it, and only while there is no file whose extension already says.
 *
 * [hostShortcuts] are the keys the host answers outside the window -- full screen, on the desktop
 * -- listed with the window's own so that 10.2's shortcut list is the whole of it.
 */
@Composable
fun DraftsApp(
    document: OpenDocument,
    modifier: Modifier = Modifier,
    initialSettings: ReaderSettings = ReaderSettings(),
    keeper: SnapshotKeeper? = null,
    settingsStore: SettingsStore? = null,
    kind: String = DEFAULT_KIND,
    saveAs: (suspend () -> WriteOutcome?)? = null,
    onKindChange: ((DocumentKind) -> Unit)? = null,
    hostShortcuts: List<Shortcut> = emptyList(),
    fold: Fold? = currentFold(),
) {
    val scope = rememberCoroutineScope()
    val scroll = rememberLazyListState()
    val autoHide = rememberAutoHide(document)

    SessionEffects(document, keeper, scroll)

    // A refusal, not a state. 8.4 puts the state in the chrome and allows "dialogs only on
    // attempted write", so the dialog is driven by the outcome of a save and cleared by answering
    // it. Driving it from `conflicted` instead would make Cancel do nothing -- the document is
    // still conflicted afterwards, so the dialog would come straight back.
    val saving = remember(document, keeper) { Saving(document, keeper) }

    DraftsWindow(
        editor = document.editor,
        initialSettings = initialSettings,
        // No store is a build without settings storage, not a failed write, and says nothing.
        onSettingsChange = { changed -> settingsStore?.remember(kind, changed) ?: true },
        scroll = scroll,
        hostShortcuts = hostShortcuts,
        fold = fold,
        autoHide = autoHide,
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
                saving.refusal != null && WindowShortcuts.Dismiss.matches(event) -> {
                    saving.answered()
                    true
                }

                WindowShortcuts.SaveAs.matches(event) -> {
                    scope.launch { saving.saveAs(saveAs) }
                    true
                }

                WindowShortcuts.Save.matches(event) -> {
                    scope.launch { saving.save(saveAs) }
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
        StatusChrome(
            document = document,
            kind = kind,
            hidden = autoHide.hidden,
            onKindChange = onKindChange,
            modifier = Modifier.align(Alignment.TopStart).padding(controlsInset),
            // The same save the Ctrl+S shortcut reaches, for the readers who have no Ctrl. An
            // untitled document goes through Save As, which `Saving` already decides.
            onSave = { scope.launch { saving.save(saveAs) } },
        )

        DocumentPrompts(document, keeper, saving, saveAs)
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
 * [fold] is the hinge across this window, if there is one. Null on everything that does not fold,
 * which is every platform but Android and most Android devices.
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
    hostShortcuts: List<Shortcut> = emptyList(),
    fold: Fold? = null,
    autoHide: AutoHide? = null,
    chrome: @Composable BoxScope.() -> Unit,
) {
    // Null is a window with no auto-hide rather than one that never hides: a buffer built from a
    // string has no document whose edits 12's timer could be watching.
    val chromeHidden = autoHide?.hidden == true

    // Keyed on what the host hands in, so a document whose kind changes -- chosen while untitled,
    // or given by Save As's extension -- takes that kind's settings (5.5 keeps them per kind)
    // rather than wearing the old kind's until it is reopened.
    val settings = remember(initialSettings) { WindowSettings(initialSettings) }
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

    DraftsTheme(settings.current) {
        // 6's bands, measured rather than assumed. Only the window knows how big it is; the
        // theme below it does not, so the size is read here and provided to everything inside.
        //
        // `BoxWithConstraints` subcomposes, which is a cost paid when the window changes size
        // rather than per frame. 6 asks for the layout to respond "continuously rather than
        // snapping" as a Chromebook or desktop window is dragged, and this is what allows both:
        // the band changes as the window crosses a boundary while 5.3's column keeps moving
        // smoothly through it.
        BoxWithConstraints(modifier.fillMaxSize()) {
            // 6's hinge rule, which needs the window's width and so belongs here with the bands:
            // the space to leave empty so the measure ends up on one side of a fold, not across it.
            val hinge = fold.clearanceWithin(maxWidth)

            // The band is the width the page actually gets, not the width of the glass. On a
            // book-posture fold those differ by half: 6's table asks what layout fits, and the
            // answer for a 411dp half is the Compact one however wide the device is unfolded.
            // Sizing the chrome for 841dp while the document has 411 would be the two halves of
            // 6 contradicting each other.
            val pageWidth = maxWidth - hinge.start - hinge.end

            CompositionLocalProvider(LocalWindowSize provides WindowSize.of(pageWidth, maxHeight)) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(LocalPalette.current.background)
                        // The panels first, then whatever depends on there being a file -- saving,
                        // and answering 8.2's refusal, which the window cannot know about and so
                        // asks -- and then the editor.
                        .windowInput(root, autoHide) { event ->
                            panels.answer(event) || onDocumentKey(event) || editor.handleShortcut(event, clipboard)
                        },
                ) {
                    LaunchedEffect(Unit) { root.requestFocus() }

                    // Android's Back, and the edge swipe that stands for it. With nothing listening it
                    // finishes the activity -- which, for a document that exists only in memory, is the
                    // reader's text gone. While a panel is open, Back closes it instead.
                    BackHandler(enabled = panels.anyOpen) { panels.closeTopmost() }

                    // 6: "On a book-posture fold, place the content column entirely on one side
                    // ... never let the fold bisect the measure." Everything the reader reads or
                    // reaches for goes on one side of the hinge -- the text, the status dot, the
                    // panels -- so that the half with the document on it is the whole interface
                    // rather than a document with its controls stranded across a crease.
                    //
                    // The background and the pointer and key handling stay on the box outside this
                    // one. The other half is still screen: it should be page rather than a bar of
                    // some other colour, and a pointer moved over there should still bring the
                    // chrome back (12).
                    //
                    // On everything that does not fold this is zero on both sides.
                    Box(Modifier.fillMaxSize().padding(start = hinge.start, end = hinge.end)) {
                        BlockEditor(state = editor, scroll = scroll)

                        // Whatever belongs to a document that came from a file: 8.4's badge, and 8.2's
                        // refusal when there is one. Placed by the caller, because where they go depends on
                        // what they are and this function does not know.
                        chrome()

                        WindowPanels(
                            panels = panels,
                            settings = settings,
                            hidden = chromeHidden,
                            hostShortcuts = hostShortcuts,
                            onSettingsChange = onSettingsChange,
                            refocused = refocused,
                        )
                    }
                }
            }
        }
    }
}

/**
 * What the window as a whole listens for.
 *
 * Focus first: the root takes it when a document opens and remains an ancestor of whatever takes
 * it next, which is what makes this the one position that sees every keystroke. Key events travel
 * from the focus owner outwards, so a handler anywhere below only sees events already on their way
 * to it -- none at all until the reader has clicked something. 10.2 asks for "complete keyboard
 * operation"; that has to include the first keystroke after opening a file.
 *
 * Then 12's "Any pointer movement ... brings them back". Observed on the final pass and never
 * consumed, so it sees the event after the selection handling below has had it rather than
 * competing for it. There is no edge gesture here: 12 lists one and desktop has no such thing; it
 * arrives with the touch platforms.
 */
private fun Modifier.windowInput(
    root: FocusRequester,
    autoHide: AutoHide?,
    onKey: (KeyEvent) -> Boolean,
): Modifier =
    focusRequester(root)
        .focusable()
        .pointerInput(autoHide) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Final)
                    if (event.type == PointerEventType.Move) autoHide?.rouse()
                }
            }
        }.onPreviewKeyEvent(onKey)

/**
 * Keeping the session: 8.1's autosave, and 7.3's scroll put back where the reader left it.
 *
 * The autosave runs only when the platform has somewhere app-private to put snapshots. [keeper] is
 * null rather than a no-op, so a build without snapshot storage is visibly without it.
 *
 * The scroll is restored once, on open: a reader who has scrolled since should not be dragged back
 * by a recomposition. (The caret was already placed as the document was built.)
 */
@Composable
private fun SessionEffects(
    document: OpenDocument,
    keeper: SnapshotKeeper?,
    scroll: LazyListState,
) {
    if (keeper != null) {
        SnapshotEffect(
            keeper = keeper,
            revision = document.editor.revision,
            scrollOffset = { scroll.firstVisibleItemIndex * SCROLL_SCALE + scroll.firstVisibleItemScrollOffset },
        )
    }

    LaunchedEffect(document) {
        document.scrollOffset?.let { offset ->
            scroll.scrollToItem(offset / SCROLL_SCALE, offset % SCROLL_SCALE)
        }
    }
}

/**
 * The chrome at the window's top start: 8.4's status, and 7.4's kind while the document is untitled
 * -- *Untitled · Markdown*, a control "until the first save".
 *
 * All of it fades together on sustained typing (12), and the kind cannot be changed while faded.
 */
@Composable
private fun StatusChrome(
    document: OpenDocument,
    kind: String,
    hidden: Boolean,
    onKindChange: ((DocumentKind) -> Unit)?,
    modifier: Modifier = Modifier,
    onSave: (() -> Unit)? = null,
) {
    Box(modifier) {
        FadingChrome(hidden = hidden) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(chromeGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Faded chrome cannot be pressed, for the same reason the controls button cannot:
                // a tap on an empty-looking corner should not save a document.
                DocumentStateBadge(document.lifecycle.state, onSave = onSave.takeIf { !hidden })

                if (document.isUntitled && onKindChange != null) {
                    KindSwitch(kind = kindOf(kind), enabled = !hidden, onChange = onKindChange)
                }
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
internal val controlsInset = 16.dp

/** Between the status badge and the kind beside it. */
private val chromeGap = 12.dp

private fun kindOf(id: String): DocumentKind = DocumentKind.entries.firstOrNull { it.id == id } ?: DocumentKind.Markdown
