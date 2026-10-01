package com.appthere.drafts.app.android

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import kotlinx.coroutines.CompletableDeferred

/** Where a save is being offered: the type the picker should create, and the name it opens on. */
internal data class SaveLocation(
    val mimeType: String,
    val name: String,
)

/**
 * `appthere-drafts.md` 7.4's picker on Android: "The first save is *Save As*, through the
 * platform's own picker (`ACTION_CREATE_DOCUMENT` on Android, followed by
 * `takePersistableUriPermission` as in 7.3 ...)".
 *
 * The grant is taken by the caller, where the kind is known; this is only the choosing.
 *
 * Its own contract rather than `ActivityResultContracts.CreateDocument`, which fixes the MIME type
 * when the launcher is registered. The type here depends on the document's kind, and the kind of an
 * untitled document can change while it is open (7.4) -- so it travels with the request instead.
 */
private class CreateDocument : ActivityResultContract<SaveLocation, Uri?>() {
    override fun createIntent(
        context: Context,
        input: SaveLocation,
    ): Intent =
        Intent(Intent.ACTION_CREATE_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType(input.mimeType)
            .putExtra(Intent.EXTRA_TITLE, input.name)

    override fun parseResult(
        resultCode: Int,
        intent: Intent?,
    ): Uri? = intent?.data?.takeIf { resultCode == Activity.RESULT_OK }
}

/**
 * The picker as something a `suspend` function can wait on.
 *
 * Everything above this -- `Saving`, `SaveAs`, 8.2's conflict dialog -- is written as "ask the
 * reader where, then save there", which is one suspending call. Android's result API is a callback
 * registered before the question is asked, so the two are bridged here rather than in each caller.
 *
 * Null is a cancel, which is what the rest of the application already expects from a picker.
 *
 * One thing it cannot survive: the process being killed while the picker is in front of the reader.
 * The waiting coroutine dies with the composition, and the result arrives to a new one that is not
 * waiting for anything, so the chosen location is dropped. The words are safe -- they are in the
 * snapshot (8.1) -- but the reader has to choose again. Carrying it across would mean putting the
 * pending save in saved instance state, which is a larger change than the case deserves until
 * someone sees it happen.
 */
@Composable
internal fun rememberSaveLocation(): suspend (SaveLocation) -> Uri? {
    val pending = remember { mutableStateOf<CompletableDeferred<Uri?>?>(null) }
    val picker =
        rememberLauncherForActivityResult(remember { CreateDocument() }) { chosen ->
            pending.value?.complete(chosen)
            pending.value = null
        }

    return remember(picker) {
        { location ->
            val answer = CompletableDeferred<Uri?>()
            pending.value = answer
            picker.launch(location)
            answer.await()
        }
    }
}
