package com.appthere.drafts.app.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.appthere.drafts.i18n.Strings
import com.appthere.drafts.platform.files.SessionRecord
import com.appthere.drafts.platform.intents.DocumentKind
import kotlinx.coroutines.launch

/**
 * The launcher icon, and 7.4's two app shortcuts.
 *
 * It shows nothing. Its whole job is to decide what this launch opens -- see [documentsAtLaunch] --
 * and start a [DocumentActivity] task for each, which is where documents live on Android. A
 * translucent theme and no content mean the reader sees the launcher they tapped from until the
 * document's own window appears, rather than a window of this application's that exists only to be
 * replaced.
 *
 * It finishes itself in every case. Left on the back stack it would be a blank screen between the
 * document and the launcher, reachable with Back.
 */
class LauncherActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val requested = kindRequested(intent)
        val showing = OpenDocuments.any
        val storage = Storage(this)

        lifecycleScope.launch {
            val open =
                documentsAtLaunch(
                    sessions = storage.sessions,
                    requested = requested,
                    showing = showing,
                    untitledName = Strings.UNTITLED,
                    lastKind = storage.settings.kindForNew(),
                )

            // The kind a shortcut asked for becomes the kind the next new document starts as (7.4).
            requested?.let { storage.settings.rememberKindForNew(it) }

            open.forEach(::show)
            finish()
        }
    }

    /**
     * One document, in its own task.
     *
     * `FLAG_ACTIVITY_NEW_DOCUMENT` without `FLAG_ACTIVITY_MULTIPLE_TASK`, though 7.2 names both.
     * The two together create a *second* task for a document that already has one, which is
     * precisely what 9.4 forbids -- "a file already open gets its existing window". With
     * `documentLaunchMode="intoExisting"` in the manifest and one session id per document in the
     * data, the first flag alone gives one task per document and brings an existing one forward.
     * Recorded in `divergences.md`.
     */
    private fun show(record: SessionRecord) {
        startActivity(
            Intent(this, DocumentActivity::class.java)
                .setData(sessionUri(record.documentId))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT),
        )
    }

    /**
     * Which kind 7.4's shortcuts asked for: *New Markdown document*, *New Fountain screenplay*.
     *
     * Null for a plain tap on the icon, which does not say -- and must not be read as Markdown, or
     * the reader who writes screenplays would get a prose document every time they launched the
     * application from its icon.
     */
    private fun kindRequested(intent: Intent): DocumentKind? =
        intent.getStringExtra(EXTRA_NEW_KIND)?.let { id -> DocumentKind.entries.firstOrNull { it.id == id } }

    internal companion object {
        /** Set by the static shortcuts in `res/xml/shortcuts.xml`, and by nothing else. */
        const val EXTRA_NEW_KIND = "com.appthere.drafts.NEW_KIND"
    }
}
