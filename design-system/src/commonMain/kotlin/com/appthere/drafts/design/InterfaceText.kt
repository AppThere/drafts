package com.appthere.drafts.design

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit

/**
 * The style for text that is not the document: panel labels, buttons, notices, the status badge.
 *
 * [proseStyleOf] sets the document in 5.1's typeface. Everything around it had been set with a bare
 * `TextStyle`, which names no family -- so the chrome took the platform's default face while the
 * page beside it was Atkinson, and a reader who chose the app partly for its legibility got it only
 * in half the window. This is the one place the interface's face is decided: Atkinson Hyperlegible
 * Next, and Atkinson Hyperlegible Mono where [monospace] text -- a licence, a key -- needs columns.
 *
 * The parameters are `TextStyle`'s own names, for exactly the subset the interface uses, so a call
 * site reads the same as the constructor it replaces. The `drafts>TextStyleOutsideDesignSystem`
 * rule is what keeps a bare `TextStyle` from coming back.
 */
@Composable
fun interfaceTextStyle(
    color: Color,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontWeight: FontWeight? = null,
    textAlign: TextAlign = TextAlign.Unspecified,
    monospace: Boolean = false,
): TextStyle =
    TextStyle(
        color = color,
        fontSize = fontSize,
        fontWeight = fontWeight,
        textAlign = textAlign,
        fontFamily = if (monospace) monoFontFamily() else proseFontFamily(),
    )
