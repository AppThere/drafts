package com.appthere.drafts.app

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import com.appthere.drafts.core.fountain.FountainKeywords

/**
 * What a document adds to the window: its [status] at the start of the [ChromeBar], its [prompts]
 * -- banners, 8.2's dialog -- over the page, and, for a screenplay, [keywordsChange]: how 11.3's
 * scene-heading words it is read with are changed and kept. It answers whether they were kept.
 *
 * A window over a buffer that came from a string has none of them, and passes [None].
 */
internal class DocumentChrome(
    val status: @Composable () -> Unit,
    val prompts: @Composable BoxScope.() -> Unit,
    val keywordsChange: (suspend (FountainKeywords) -> Boolean)? = null,
) {
    companion object {
        val None = DocumentChrome(status = {}, prompts = {})
    }
}
