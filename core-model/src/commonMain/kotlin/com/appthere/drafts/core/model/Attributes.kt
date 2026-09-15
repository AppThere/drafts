package com.appthere.drafts.core.model

/**
 * The id, classes and key-values attached to a node.
 *
 * Populated from the attribute syntax in `markdown-dialect.md` 7 -- `{#custom-id .highlight}`
 * after a heading, or on the line after a standalone image -- and from the automatic heading ids
 * that the same section switches on.
 *
 * Empty for most nodes. [EMPTY] is shared rather than allocated per node, because the overwhelming
 * majority of blocks in a manuscript have no attributes at all.
 */
data class Attributes(
    val id: String? = null,
    val classes: List<String> = emptyList(),
    val keyValues: Map<String, String> = emptyMap(),
) {
    val isEmpty: Boolean get() = id == null && classes.isEmpty() && keyValues.isEmpty()

    operator fun get(key: String): String? = keyValues[key]

    fun hasClass(name: String): Boolean = name in classes

    /**
     * Merges [other] over this, with [other] winning.
     *
     * The asymmetry is the point, and it implements one specific sentence of
     * `markdown-dialect.md` 7: "An explicit `{#id}` overrides the generated one." Heading ids are
     * generated first, then the author's attribute block is merged over the top.
     *
     * Classes accumulate rather than replace -- 7 says `.class` *appends* a class -- and
     * duplicates are collapsed, since a class list is a set in every output format.
     */
    operator fun plus(other: Attributes): Attributes =
        Attributes(
            id = other.id ?: id,
            classes = (classes + other.classes).distinct(),
            keyValues = keyValues + other.keyValues,
        )

    companion object {
        val EMPTY = Attributes()
    }
}
