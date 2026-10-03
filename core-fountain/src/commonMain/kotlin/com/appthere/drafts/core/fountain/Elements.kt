package com.appthere.drafts.core.fountain

/*
 * What Fountain's syntax is, kept apart from both reading and writing it.
 *
 * `fountain.md` has no separate grammar for output: Fountain is its own canonical serialisation, so
 * the rules below are read in both directions. "A line in all uppercase ending in `TO:`" is how the
 * parser recognises a transition *and* how the serialiser knows it can write one without a `>` in
 * front. If those two answers ever disagreed the round-trip would stop being byte-identical, in the
 * quietest possible way -- so there is one answer, in one place, and neither module owns it.
 *
 * That is also why these are not `internal`. Being shared is the whole point of the module.
 */

/**
 * What a chunk turned out to be, before it becomes blocks.
 *
 * Only the kinds a *chunk* can be. Dialogue and parentheticals are lines inside a character chunk
 * rather than chunks of their own, so they are not here.
 */
enum class Element {
    PAGE_BREAK,
    SCENE_HEADING,
    ACTION,
    CHARACTER,
    TRANSITION,
    CENTERED,
    LYRIC,
    SECTION,
    SYNOPSIS,
}

/**
 * How a chunk's first line announced itself, if it did.
 *
 * `fountain.md`: "Every element type has a **forcing character** that removes ambiguity. When
 * implementing, check forcing characters first -- they short-circuit all inference."
 */
data class Forced(
    val element: Element,
    /** How many characters of the line are the marker rather than the words. */
    val markerLength: Int,
    /** Section depth, for the one element that has one. */
    val depth: Int = 0,
)

/**
 * The scene-heading prefixes, which `appthere-drafts.md` 11.3 makes configurable.
 *
 * "Configurable scene-heading prefixes and transition suffix -- Fountain 1.1's keywords are
 * English." A screenwriter working in another language writes `INT.`'s equivalent, and a parser
 * that only knows the English one reads their every scene heading as action. The default is
 * Fountain 1.1's own list; nothing here assumes it is the only one.
 */
data class FountainKeywords(
    val sceneHeadingPrefixes: List<String> = DEFAULT_SCENE_PREFIXES,
    val transitionSuffix: String = DEFAULT_TRANSITION_SUFFIX,
) {
    companion object {
        /** `fountain.md`: "begins with one of: INT, EXT, EST, INT./EXT, INT/EXT, I/E". */
        val DEFAULT_SCENE_PREFIXES = listOf("INT./EXT", "INT/EXT", "EXT./INT", "EXT/INT", "I/E", "INT", "EXT", "EST")

        /** "A line in all uppercase ending in `TO:`". */
        const val DEFAULT_TRANSITION_SUFFIX = "TO:"

        val ENGLISH = FountainKeywords()
    }
}

/**
 * The forcing character on [line], or null if it has none.
 *
 * Order matters in two places. A line beginning with two or more periods is action starting with
 * an ellipsis, not a forced scene heading -- "that reserves `..` for action text starting with an
 * ellipsis". And a line beginning with `>` is centred text if it also ends with `<`, which is why
 * the two are decided together rather than in sequence.
 */
fun forcingOf(line: String): Forced? {
    val trimmed = line.trimStart()
    val marker = line.length - trimmed.length

    return when {
        trimmed.startsWith("..") -> null
        trimmed.startsWith(".") -> Forced(Element.SCENE_HEADING, marker + 1)
        trimmed.startsWith("!") -> Forced(Element.ACTION, marker + 1)
        trimmed.startsWith("@") -> Forced(Element.CHARACTER, marker + 1)
        trimmed.startsWith("~") -> Forced(Element.LYRIC, marker + 1)
        trimmed.startsWith("#") -> section(trimmed, marker)
        isPageBreak(trimmed) -> Forced(Element.PAGE_BREAK, 0)
        trimmed.startsWith("=") -> Forced(Element.SYNOPSIS, marker + 1 + leadingSpaceAfter(trimmed, 1))
        trimmed.startsWith(">") && trimmed.trimEnd().endsWith("<") -> Forced(Element.CENTERED, marker + 1)
        trimmed.startsWith(">") -> Forced(Element.TRANSITION, marker + 1 + leadingSpaceAfter(trimmed, 1))
        else -> null
    }
}

/**
 * Whether [line] is a page break: "three or more consecutive `=` characters and nothing else".
 *
 * The three is what keeps it apart from a synopsis, which `fountain.md` names as a conflict to
 * resolve: "disambiguate by requiring three or more `=` with no other content for a page break."
 */
fun isPageBreak(line: String): Boolean {
    val trimmed = line.trim()

    return trimmed.length >= PAGE_BREAK_MINIMUM && trimmed.all { it == '=' }
}

/**
 * Whether every letter in [line] is a capital, and there is at least one.
 *
 * "The uppercase requirement applies to letters only -- numbers, spaces, and punctuation are
 * permitted." A line of digits is not a character name, which is why the count matters as well as
 * the case.
 */
fun isUppercase(line: String): Boolean {
    val letters = line.filter { it.isLetter() }

    return letters.isNotEmpty() && letters.all { it.isUpperCase() }
}

/**
 * Whether [line] could be a character's name.
 *
 * The uppercase rule stops at the extension. "May end with a character extension in parentheses",
 * and the spec's own examples include `HANS (on the radio)` -- lowercase, deliberately, because an
 * extension is a stage direction rather than a name. Testing the whole line would read that as
 * action and silently lose the speech under it.
 */
fun isCharacter(line: String): Boolean = isUppercase(withoutExtension(line))

/** [line] without a trailing parenthesised extension, which is not part of the name. */
private fun withoutExtension(line: String): String {
    val trimmed = line.trimEnd()
    if (!trimmed.endsWith(")")) return trimmed

    val open = trimmed.lastIndexOf('(')

    return if (open > 0) trimmed.substring(0, open).trimEnd() else trimmed
}

/** Whether [line] begins with one of [keywords]'s scene prefixes, at a word boundary. */
fun isSceneHeading(
    line: String,
    keywords: FountainKeywords,
): Boolean {
    val trimmed = line.trimStart()

    return keywords.sceneHeadingPrefixes.any { prefix ->
        trimmed.startsWith(prefix, ignoreCase = true) &&
            trimmed.length > prefix.length &&
            trimmed[prefix.length] in SCENE_PREFIX_BOUNDARY
    }
}

/**
 * Whether [line] is a transition: uppercase, ending in the suffix, with nothing after it.
 *
 * "Adding a space after the colon (`CUT TO: `) makes the line parse as Action -- a documented
 * escape." So the end of the line is tested exactly, while the start is trimmed: transitions are
 * conventionally typed far to the right and the leading whitespace means nothing.
 */
fun isTransition(
    line: String,
    keywords: FountainKeywords,
): Boolean = isUppercase(line) && line.trimStart().endsWith(keywords.transitionSuffix)

/**
 * The scene number at the end of [line], and where it starts, or null if there is none.
 *
 * `fountain.md`: "Scene numbers: appended in `#...#` at end of line. Content may be alphanumeric
 * with hyphens and periods." They are the one production feature Fountain keeps, "because they
 * matter for archival", so they are read out of the heading rather than left in its words.
 */
fun sceneNumberIn(line: String): Pair<String, Int>? {
    val trimmed = line.trimEnd()
    val open =
        trimmed
            .takeIf { it.endsWith("#") && it.length >= SCENE_NUMBER_MINIMUM }
            ?.lastIndexOf('#', trimmed.length - 2)
            ?.takeIf { it > 0 }
            ?: return null

    return trimmed
        .substring(open + 1, trimmed.length - 1)
        .takeIf { number ->
            number.isNotEmpty() && number.all { it.isLetterOrDigit() || it in SCENE_NUMBER_PUNCTUATION }
        }?.let { it to open }
}

/** Whether [line] is a parenthetical: "a line wrapped in parentheses". */
fun isParenthetical(line: String): Boolean {
    val trimmed = line.trim()

    return trimmed.length >= PARENTHETICAL_MINIMUM && trimmed.startsWith("(") && trimmed.endsWith(")")
}

private fun section(
    trimmed: String,
    marker: Int,
): Forced {
    val depth = trimmed.takeWhile { it == '#' }.length

    return Forced(Element.SECTION, marker + depth + leadingSpaceAfter(trimmed, depth), depth)
}

/** The run of spaces between a marker and the words after it, which is marker rather than words. */
private fun leadingSpaceAfter(
    line: String,
    marker: Int,
): Int = line.drop(marker).takeWhile { it == ' ' }.length

/** What may follow a scene prefix: a full stop, a space, or the slash of `INT/EXT`. */
private const val SCENE_PREFIX_BOUNDARY = ". /"

private const val PAGE_BREAK_MINIMUM = 3

/** `#1#` is three characters, and there has to be something before it for it to be appended to. */
private const val SCENE_NUMBER_MINIMUM = 3

/** "Content may be alphanumeric with hyphens and periods." */
private const val SCENE_NUMBER_PUNCTUATION = "-."

/** `()` is two characters, and anything shorter cannot be wrapped in them. */
private const val PARENTHETICAL_MINIMUM = 2
