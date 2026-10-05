package com.appthere.drafts.app

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.appthere.drafts.core.fountain.FountainKeywords
import com.appthere.drafts.core.fountain.KeywordPreset
import com.appthere.drafts.design.LocalPalette
import com.appthere.drafts.i18n.resources.Res
import com.appthere.drafts.i18n.resources.apply
import com.appthere.drafts.i18n.resources.scene_headings
import com.appthere.drafts.i18n.resources.scene_headings_explained
import com.appthere.drafts.i18n.resources.scene_headings_language
import com.appthere.drafts.i18n.resources.scene_headings_not_saved
import com.appthere.drafts.i18n.resources.scene_headings_words
import com.appthere.drafts.i18n.resources.transition_ending
import com.appthere.drafts.i18n.resources.transition_ending_needed
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/**
 * 11.3's scene-heading words for one screenplay: "a per-document configurable prefix list and
 * transition suffix, defaulting to the Fountain 1.1 set, with presets for common languages."
 *
 * A preset applies as soon as it is chosen. The words themselves apply on *Apply* rather than as
 * they are typed: every change reads the whole screenplay again, and a list half typed -- `INT`
 * on its way to `INTÉRIEUR` -- would turn headings into action and back with each letter.
 *
 * [onChange] reads the screenplay with the new words and keeps them, and says whether they were
 * kept. They are applied either way; a failure to keep them is said here, where they were chosen.
 */
@Composable
internal fun SceneHeadings(
    keywords: FountainKeywords,
    onChange: suspend (FountainKeywords) -> Boolean,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    val scope = rememberCoroutineScope()
    val title = stringResource(Res.string.scene_headings)

    var words by remember(keywords) { mutableStateOf(keywords.sceneHeadingPrefixes.joinToString(", ")) }
    var ending by remember(keywords) { mutableStateOf(keywords.transitionSuffix) }
    var unsaved by remember { mutableStateOf(false) }
    var incomplete by remember(keywords) { mutableStateOf(false) }

    val apply: (FountainKeywords) -> Unit = { chosen -> scope.launch { unsaved = !onChange(chosen) } }

    Column(modifier.panelFrame(title), verticalArrangement = Arrangement.spacedBy(gap)) {
        PanelHeader(title = title, onClose = onClose)
        BasicText(stringResource(Res.string.scene_headings_explained), style = body(palette))

        Choice(
            label = stringResource(Res.string.scene_headings_language),
            options = KeywordPreset.ALL.map { Option(it.keywords, it.name) },
            selected = keywords,
            onSelect = apply,
        )

        WordsField(stringResource(Res.string.scene_headings_words), words) { words = it }
        WordsField(stringResource(Res.string.transition_ending), ending) { ending = it }

        PanelButton(
            text = stringResource(Res.string.apply),
            description = stringResource(Res.string.apply),
            onClick = {
                val chosen = FountainKeywords.of(words.split(','), ending)
                incomplete = chosen == null
                chosen?.let(apply)
            },
        )

        // Said where the reader is, in a polite live region so a screen reader hears it without
        // being cut off mid-announcement of the button just pressed.
        listOfNotNull(
            stringResource(Res.string.transition_ending_needed).takeIf { incomplete },
            stringResource(Res.string.scene_headings_not_saved).takeIf { unsaved },
        ).forEach { notice ->
            BasicText(
                notice,
                style = body(palette),
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

/**
 * A labelled line of text to type in.
 *
 * The label is above the field rather than beside it, so a long list of words has the panel's
 * whole width, and is the field's description, so a screen reader names the field it is in.
 */
@Composable
private fun WordsField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
) {
    val palette = LocalPalette.current

    Column(verticalArrangement = Arrangement.spacedBy(labelGap)) {
        BasicText(label, style = body(palette))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = body(palette),
            cursorBrush = SolidColor(palette.ink),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .sizeIn(minHeight = fieldHeight)
                    .border(1.dp, palette.muted, RoundedCornerShape(fieldCorner))
                    .padding(fieldPadding)
                    .semantics { contentDescription = label },
        )
    }
}

private val gap = 12.dp
private val labelGap = 4.dp

/** 10.2: "Touch targets >= 48dp." */
private val fieldHeight = 48.dp
private val fieldPadding = 12.dp
private val fieldCorner = 6.dp
