package com.appthere.drafts.app

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable

/**
 * What a document adds to the window: its [status] at the start of the [ChromeBar], and its
 * [prompts] -- banners, 8.2's dialog -- over the page.
 *
 * A window over a buffer that came from a string has neither, and passes [None].
 */
internal class DocumentChrome(
    val status: @Composable () -> Unit,
    val prompts: @Composable BoxScope.() -> Unit,
) {
    companion object {
        val None = DocumentChrome(status = {}, prompts = {})
    }
}
