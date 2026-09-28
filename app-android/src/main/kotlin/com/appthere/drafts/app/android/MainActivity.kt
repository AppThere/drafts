package com.appthere.drafts.app.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.appthere.drafts.app.DraftsApp
import com.appthere.drafts.app.SampleDocument

/**
 * The launcher entry point, and nothing more yet.
 *
 * It opens on the sample document, in memory and belonging to no file -- the same as the desktop
 * with no path argument. The document Activity of `appthere-drafts.md` 9.2, with its intent
 * filters, `takePersistableUriPermission` and per-document tasks, is Phase 6. This exists so the
 * editor can be run on a device before then, and should be replaced by that Activity rather than
 * grown into it.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DraftsApp(initialText = SampleDocument.TEXT)
        }
    }
}
