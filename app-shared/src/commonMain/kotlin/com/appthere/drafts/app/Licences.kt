package com.appthere.drafts.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appthere.drafts.design.FontLicence
import com.appthere.drafts.design.FontLicences
import com.appthere.drafts.design.LocalPalette
import com.appthere.drafts.design.Prose
import com.appthere.drafts.i18n.Strings

/**
 * The in-app licences screen `appthere-drafts.md` 5.1 requires.
 *
 * "Ship the OFL text in an in-app licences screen." Section 4 of the SIL Open Font License makes
 * this an obligation rather than a nicety: the licence and its copyright notices have to travel
 * with the fonts, and a file in the artifact that nothing can display is not something a reader
 * can be said to have received.
 *
 * Both licences are shown in full. Their bodies are identical and only the copyright lines differ,
 * so showing one and implying it covers the other would quietly drop an attribution.
 *
 * The text is set in the monospace face at a fixed size, like the legal document it is -- it is
 * not prose, the prose scale is not for it, and its line breaks are its own.
 */
@Composable
fun Licences(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current

    Column(
        modifier
            .width(panelWidth)
            .sizeIn(maxHeight = panelHeight)
            .background(palette.background)
            .border(hairline, palette.muted, RoundedCornerShape(corner))
            .padding(panelPadding),
        verticalArrangement = Arrangement.spacedBy(rowGap),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            BasicText(
                text = Strings.LICENCES,
                style = TextStyle(color = palette.ink, fontSize = headingSize, fontWeight = Prose.H4.weight),
                modifier = Modifier.width(headingWidth),
            )
            BasicText(
                text = Strings.CLOSE,
                style = TextStyle(color = palette.ink, fontSize = labelSize, textAlign = TextAlign.Center),
                modifier =
                    Modifier
                        .sizeIn(minWidth = target, minHeight = target)
                        .border(hairline, palette.muted, RoundedCornerShape(corner))
                        .clickable { onClose() }
                        .padding(buttonPadding)
                        .semantics { contentDescription = Strings.CLOSE },
            )
        }

        Column(
            Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(rowGap),
        ) {
            FontLicences.all.forEach { licence -> LicenceText(licence) }
        }
    }
}

/**
 * One family's licence, loaded from the artifact.
 *
 * Read with [produceState] rather than held in a constant: the file in the artifact is the one
 * that ships, and a copy in the source is a copy that can drift from it. Until it arrives the
 * family name is shown on its own, which is honest -- the licence is loading, not absent.
 */
@Composable
private fun LicenceText(licence: FontLicence) {
    val palette = LocalPalette.current
    val text by produceState(initialValue = "", licence) { value = FontLicences.textOf(licence) }

    Column(verticalArrangement = Arrangement.spacedBy(lineGap)) {
        BasicText(
            text = licence.family,
            style = TextStyle(color = palette.ink, fontSize = labelSize, fontWeight = Prose.H5.weight),
        )
        BasicText(
            text = text,
            style =
                TextStyle(
                    color = palette.muted,
                    fontSize = licenceSize,
                    fontFamily = FontFamily.Monospace,
                ),
        )
    }
}

private val panelWidth = 560.dp
private val panelHeight = 600.dp
private val panelPadding = 16.dp
private val headingWidth = 420.dp
private val rowGap = 12.dp
private val lineGap = 4.dp
private val buttonPadding = 12.dp
private val corner = 6.dp
private val hairline = 1.dp
private val target = 48.dp
private val labelSize = 14.sp
private val headingSize = 18.sp
private val licenceSize = 11.sp
