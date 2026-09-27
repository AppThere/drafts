package com.appthere.drafts.editor.ui

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalClipboardManager

/**
 * The editor with the shortcut wiring a host application provides.
 *
 * Shortcuts are not the editor's own: key events travel outwards from the focus owner, so a
 * handler inside the editor only ever sees events already on their way into it. `:app-shared`
 * installs them at the composition root, where they see everything. Tests that press Ctrl+A or
 * Ctrl+Z need that same wiring, and putting it here rather than in each test keeps them honest
 * about what they are relying on.
 */
@Composable
internal fun HostedEditor(state: EditorState) {
    val clipboard = LocalClipboardManager.current
    val root = remember { FocusRequester() }

    Box(
        Modifier
            .fillMaxSize()
            .focusRequester(root)
            .focusable()
            .onPreviewKeyEvent { event -> state.handleShortcut(event, clipboard) },
    ) {
        LaunchedEffect(Unit) { root.requestFocus() }
        BlockEditor(state = state)
    }
}
