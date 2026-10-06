package com.appthere.drafts.core.serialise

import com.appthere.drafts.core.parse.fountain.FountainDocumentParser
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Phase 7's acceptance, on files this project did not write: "Round-trip byte-identical on a corpus
 * of real `.fountain` files."
 *
 * The corpus is the Fountain reference implementation's own feature tests (see the README beside
 * them). The JVM only, because it reads files; the parser and serialiser it runs are the common
 * ones every target uses.
 */
class FountainCorpusTest {
    @Test
    fun `every file in the corpus round-trips byte for byte`() {
        val files = corpus()
        assertTrue(files.size >= MINIMUM_CORPUS, "The corpus is missing: ${files.size} files")

        files.forEach { file ->
            val source = file.readText()
            val written = FountainSerialiser().serialise(FountainDocumentParser().parse(source), source)
            assertEquals(source, written, "${file.name} came back different")
        }
    }

    private fun corpus(): List<File> {
        val directory = javaClass.classLoader.getResource(CORPUS)?.let { File(it.toURI()) }
        return directory
            ?.listFiles { file -> file.extension == "fountain" }
            ?.sortedBy { it.name }
            .orEmpty()
    }

    private companion object {
        const val CORPUS = "fountain-corpus"

        /** What the README lists, so a corpus that quietly failed to load is not a pass. */
        const val MINIMUM_CORPUS = 16
    }
}
