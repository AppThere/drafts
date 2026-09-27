package com.appthere.drafts.app.desktop

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.appthere.drafts.app.DraftsApp
import com.appthere.drafts.i18n.Strings

/**
 * The desktop entry point.
 *
 * Phase 2 is a spike: desktop only, provisional styling, no persistence. There is no file open yet,
 * so the window comes up on a sample document -- the point of this build is to make the gate
 * criteria in `IMPLEMENTATION-PLAN.md` observable, and they are all about what happens when someone
 * types into a long document rather than about how it got there.
 */
fun main() =
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = Strings.WINDOW_TITLE,
        ) {
            DraftsApp(initialText = SampleDocument.TEXT)
        }
    }
