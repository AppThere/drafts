package fixtures.compliant

/**
 * Source that talks about the banned spellings without using them.
 *
 * Two of the architecture rules match text rather than imports, because what they forbid is a
 * spelling and not a dependency. That makes them able to fire on prose -- and the first thing they
 * fired on was a KDoc comment explaining why the rule exists.
 *
 * So: `Dispatchers.IO` must not appear in commonMain, and export backends must not build markup by
 * writing `<w:p>` into a string. Both of those spellings are in this file, in comments, and neither
 * may be reported. A rule that punishes documenting itself gets worked around rather than obeyed.
 */
fun described(): Int = 1

// Dispatchers.IO again, in a line comment this time, and one more `</w:p>` for the XML rule.
fun alsoDescribed(): Int = 2

// A URL in a comment: https://example.org/a//b -- the comment stripper must not choke on it.
const val WHERE = "https://example.org/reference"
