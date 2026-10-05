package com.appthere.drafts.core.fountain

/**
 * One of 11.3's "presets for common languages": a language's scene-heading words and transition
 * ending, under the language's own name for itself.
 *
 * The name is the language's, in that language -- *Français*, not *French* -- because it is what a
 * reader of it looks for, whatever language the application is in. It is data, like the words, and
 * is not translated.
 */
data class KeywordPreset(
    val name: String,
    val keywords: FountainKeywords,
) {
    companion object {
        /**
         * 11.3: "Provide a per-document configurable prefix list and transition suffix,
         * defaulting to the Fountain 1.1 set, with presets for common languages."
         *
         * Each language keeps Fountain 1.1's English prefixes as well as its own. `INT.` and
         * `EXT.` are what scripts in all of these languages are commonly written with, and a
         * preset that dropped them would turn a reader's existing headings into action the moment
         * they chose their own language. The long forms are listed without their accents as well,
         * since capitals are often typed without them.
         *
         * The transition ending is one string, as 11.3 has it, so a preset's replaces `TO:`; an
         * English `CUT TO:` in such a script is forced with `>`.
         */
        val ALL: List<KeywordPreset> =
            listOf(
                KeywordPreset("English", FountainKeywords.ENGLISH),
                KeywordPreset(
                    "Français",
                    FountainKeywords(
                        sceneHeadingPrefixes =
                            FountainKeywords.DEFAULT_SCENE_PREFIXES +
                                listOf("INTÉRIEUR", "EXTÉRIEUR", "INTERIEUR", "EXTERIEUR"),
                        transitionSuffix = "À :",
                    ),
                ),
                KeywordPreset(
                    "Español",
                    FountainKeywords(
                        sceneHeadingPrefixes = FountainKeywords.DEFAULT_SCENE_PREFIXES + listOf("INTERIOR", "EXTERIOR"),
                        transitionSuffix = "A:",
                    ),
                ),
                KeywordPreset(
                    "Português",
                    FountainKeywords(
                        sceneHeadingPrefixes = FountainKeywords.DEFAULT_SCENE_PREFIXES + listOf("INTERIOR", "EXTERIOR"),
                        transitionSuffix = "PARA:",
                    ),
                ),
            )
    }
}
