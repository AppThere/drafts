package com.appthere.drafts.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.appthere.drafts.design.LocalPalette
import com.appthere.drafts.i18n.Strings

/**
 * A message on its own in the window, for when there is no document to show.
 *
 * Opening a document is I/O and can fail: a network share that has not answered yet, a restored
 * session pointing at a file that has since gone, a share that carried nothing. A reader who asked
 * for a document and got an empty editor has been told nothing, which is worse than being told
 * there is a problem.
 *
 * [name] is the document, on its own line, because the message says what happened and the name says
 * which of the three open documents it happened to.
 */
@Composable
fun Notice(
    message: String,
    modifier: Modifier = Modifier,
    name: String? = null,
) {
    val palette = LocalPalette.current

    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(noticeGap),
        ) {
            BasicText(text = message, style = TextStyle(color = palette.ink))
            name?.let { BasicText(text = it, style = TextStyle(color = palette.muted)) }
        }
    }
}

private val noticeGap = 8.dp

/**
 * Why a document is not showing, in words about the reader's situation rather than the exception
 * (11.1).
 *
 * A file that is missing with a snapshot never gets here: it opens from the snapshot (7.3).
 */
fun noticeFor(reason: DocumentOpening.Reason): String =
    when (reason) {
        DocumentOpening.Reason.Unreadable -> Strings.COULD_NOT_READ
        DocumentOpening.Reason.Missing, DocumentOpening.Reason.NothingKept -> Strings.FILE_GONE_NOTHING_KEPT
    }
