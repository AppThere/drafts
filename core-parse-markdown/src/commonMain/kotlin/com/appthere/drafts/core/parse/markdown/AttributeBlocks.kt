package com.appthere.drafts.core.parse.markdown

import com.appthere.drafts.core.model.Attributes

/**
 * Parses `{#id .class key=value}` blocks (`markdown-dialect.md` 7).
 *
 * The dialect allows these on **headings and images only**. Generic block attributes are switched
 * off, so a `{...}` after a paragraph is literal text -- which is why this returns null rather than
 * throwing on anything it does not recognise. 7 is explicit: "Malformed attribute blocks are
 * literal text, never a parse error."
 *
 * That tolerance is the whole design. An author typing a brace mid-sentence should see a brace, not
 * an error, and should certainly not lose their paragraph to a half-typed attribute block.
 */
internal object AttributeBlocks {
    /**
     * The attributes in [text], or null if it is not a well-formed block.
     *
     * All-or-nothing: one unrecognised item makes the whole thing literal text, because a block
     * that silently dropped the item it could not read would be worse than one that stayed visible.
     */
    fun parse(text: String): Attributes? {
        val body = bodyOf(text) ?: return null
        val items = splitItems(body)

        return if (items.isEmpty()) null else collect(items)
    }

    /** The content between the braces, or null if [text] is not brace-delimited. */
    private fun bodyOf(text: String): String? =
        text
            .trim()
            .takeIf { it.length > MINIMUM_LENGTH && it.startsWith('{') && it.endsWith('}') }
            ?.let { it.substring(1, it.length - 1).trim() }

    /**
     * All-or-nothing: one unrecognised item makes the whole block literal text, because a block
     * that silently dropped the item it could not read would be worse than one that stayed visible.
     *
     * Validated before anything is built, so the decision is made once rather than unwound halfway
     * through.
     */
    private fun collect(items: List<String>): Attributes? =
        if (items.any { !it.isValidItem() }) {
            null
        } else {
            Attributes(
                id = items.lastOrNull { it.startsWith('#') }?.drop(1),
                classes = items.filter { it.startsWith('.') }.map { it.drop(1) },
                keyValues = items.filter { it.isKeyValue() }.mapNotNull { keyValue(it) }.toMap(),
            )
        }

    private fun String.isValidItem(): Boolean =
        when {
            startsWith('#') || startsWith('.') -> length > 1
            contains('=') -> keyValue(this) != null
            else -> false
        }

    private fun String.isKeyValue(): Boolean = !startsWith('#') && !startsWith('.') && contains('=')

    /** True when [text] is nothing but an attribute block. */
    fun isBlock(text: String): Boolean = parse(text) != null

    private fun keyValue(item: String): Pair<String, String>? {
        val key = item.substringBefore('=')
        val value = item.substringAfter('=')

        return if (key.isEmpty() || value.isEmpty()) null else key to value.removeSurrounding("\"")
    }

    /**
     * Splits on whitespace, except inside double quotes.
     *
     * `key="quoted value"` is one item, and 7 lists it as a supported form, so a naive split on
     * spaces would break exactly the case the syntax exists for.
     */
    private fun splitItems(body: String): List<String> {
        val items = mutableListOf<String>()
        val current = StringBuilder()
        var quoted = false

        body.forEach { char ->
            quoted = quoted != (char == '"')
            when {
                char.isWhitespace() && !quoted -> items.addNonEmpty(current)
                else -> current.append(char)
            }
        }
        items.addNonEmpty(current)

        // An unclosed quote is malformed, not a best guess at what was meant.
        return if (quoted) emptyList() else items
    }

    private fun MutableList<String>.addNonEmpty(buffer: StringBuilder) {
        if (buffer.isNotEmpty()) add(buffer.toString())
        buffer.clear()
    }

    /** `{}` is the shortest thing that could be a block, and it is empty, so two is the floor. */
    private const val MINIMUM_LENGTH = 2
}
