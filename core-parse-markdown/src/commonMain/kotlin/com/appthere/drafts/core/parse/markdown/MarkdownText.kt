package com.appthere.drafts.core.parse.markdown

/**
 * Resolves backslash escapes and character references in literal text.
 *
 * `intellij-markdown` does neither: it performs both when generating HTML, not when building the
 * tree, so the CST hands us `\!` and `&amp;` exactly as written. Somebody has to turn those into
 * `!` and `&`, and it has to be the lowering, because the IR is what every backend consumes and
 * `export-pipeline.md` is explicit that backends never see Markdown concepts. A backend asked to
 * strip backslashes would be re-implementing Markdown.
 *
 * **This is the point at which `Text.value` stops being its span's slice of the source.** The two
 * were identical until now, and two restorers sliced text by offset arithmetic on that assumption.
 * They no longer can, and they take the source explicitly instead.
 *
 * Code is exempt throughout. A code span or code block is literal by definition -- `` `\!` `` is a
 * backslash and a bang -- so neither transformation runs over it.
 */
internal object MarkdownText {
    /** Backslash escapes and character references resolved, in that order. */
    fun unescape(raw: String): String = if (raw.none { it == '\\' || it == '&' }) raw else resolve(raw)

    private fun resolve(raw: String): String =
        buildString(raw.length) {
            var index = 0
            while (index < raw.length) {
                index =
                    when (raw[index]) {
                        '\\' -> appendEscape(raw, index)
                        '&' -> appendReference(raw, index)
                        else -> appendLiteral(raw, index)
                    }
            }
        }

    /**
     * `\!` is a bang. A backslash before anything else is a literal backslash -- CommonMark only
     * lets ASCII punctuation be escaped, so `\a` stays `\a`.
     */
    private fun StringBuilder.appendEscape(
        raw: String,
        index: Int,
    ): Int {
        val next = raw.getOrNull(index + 1)
        return if (next != null && next in ESCAPABLE) {
            append(next)
            index + 2
        } else {
            append('\\')
            index + 1
        }
    }

    /** `&amp;`, `&#35;`, `&#XaF;`. Anything unrecognised stays exactly as written. */
    private fun StringBuilder.appendReference(
        raw: String,
        index: Int,
    ): Int {
        val end = raw.indexOf(';', index)
        val body = if (end > index) raw.substring(index + 1, end) else null
        val resolved = body?.let { resolveReference(it) }

        return if (resolved == null) {
            append('&')
            index + 1
        } else {
            append(resolved)
            end + 1
        }
    }

    private fun StringBuilder.appendLiteral(
        raw: String,
        index: Int,
    ): Int {
        append(raw[index])
        return index + 1
    }

    private fun resolveReference(body: String): String? =
        when {
            body.startsWith("#x") || body.startsWith("#X") -> codePoint(body.drop(2), HEX)
            body.startsWith("#") -> codePoint(body.drop(1), DECIMAL)
            else -> NAMED[body]
        }

    /**
     * A numeric reference, as the character it names.
     *
     * CommonMark maps an out-of-range or zero code point to the replacement character rather than
     * failing, on the principle that a malformed document still has to render.
     */
    private fun codePoint(
        digits: String,
        radix: Int,
    ): String? =
        digits
            .takeIf { it.isNotEmpty() && it.length <= MAX_DIGITS }
            ?.toIntOrNull(radix)
            ?.let { value ->
                when {
                    value == 0 || value > MAX_CODE_POINT -> REPLACEMENT.toString()
                    value <= MAX_BMP -> value.toChar().toString()
                    else -> surrogatePair(value)
                }
            }

    /** Astral-plane code points are two UTF-16 units, which is the unit offsets are counted in. */
    private fun surrogatePair(value: Int): String {
        val offset = value - ASTRAL_BASE
        val high = HIGH_SURROGATE_BASE + (offset shr SURROGATE_SHIFT)
        val low = LOW_SURROGATE_BASE + (offset and SURROGATE_MASK)
        return charArrayOf(high.toChar(), low.toChar()).concatToString()
    }

    /** The ASCII punctuation CommonMark permits a backslash to escape. */
    private val ESCAPABLE = """!"#$%&'()*+,-./:;<=>?@[\]^_`{|}~""".toSet()

    /**
     * Named character references.
     *
     * CommonMark admits the whole HTML5 entity table -- some two thousand names -- which is not
     * worth embedding in a mobile application for the handful that appear in real prose. These are
     * the ones that actually turn up, plus the ones the specification's own examples use. An
     * unrecognised name is left as written, which is also what a browser does with it.
     */
    private val NAMED =
        mapOf(
            "amp" to "&",
            "lt" to "<",
            "gt" to ">",
            "quot" to "\"",
            "apos" to "'",
            "nbsp" to " ",
            "copy" to "©",
            "reg" to "®",
            "trade" to "™",
            "hellip" to "…",
            "mdash" to "—",
            "ndash" to "–",
            "lsquo" to "‘",
            "rsquo" to "’",
            "ldquo" to "“",
            "rdquo" to "”",
            "laquo" to "«",
            "raquo" to "»",
            "deg" to "°",
            "plusmn" to "±",
            "frac12" to "½",
            "frac34" to "¾",
            "times" to "×",
            "divide" to "÷",
            "auml" to "ä",
            "ouml" to "ö",
            "uuml" to "ü",
            "szlig" to "ß",
            "eacute" to "é",
            "egrave" to "è",
            "agrave" to "à",
            "ccedil" to "ç",
            "AElig" to "Æ",
            "aelig" to "æ",
            "Dcaron" to "Ď",
            "HilbertSpace" to "ℋ",
            "DifferentialD" to "ⅆ",
            "ClockwiseContourIntegral" to "∲",
            "ngE" to "≧̸",
        )

    private const val HEX = 16
    private const val DECIMAL = 10
    private const val MAX_DIGITS = 8
    private const val MAX_CODE_POINT = 0x10FFFF
    private const val MAX_BMP = 0xFFFF
    private const val ASTRAL_BASE = 0x10000
    private const val HIGH_SURROGATE_BASE = 0xD800
    private const val LOW_SURROGATE_BASE = 0xDC00
    private const val SURROGATE_SHIFT = 10
    private const val SURROGATE_MASK = 0x3FF
    private const val REPLACEMENT = '�'
}
