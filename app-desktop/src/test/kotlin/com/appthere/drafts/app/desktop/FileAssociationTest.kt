package com.appthere.drafts.app.desktop

import com.appthere.drafts.platform.intents.DocumentKind
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The installers declare the same types the application opens.
 *
 * `appthere-drafts.md` 9.1 is one table, and it ends up in two places: `DocumentKind`, which the
 * running application decides with, and the packaging, which tells the operating system what to
 * send it. A build script cannot call the code it builds, so nothing makes those one list. An
 * extension added to one and not the other is a file that double-clicks to nothing, or one that
 * opens as the wrong kind -- and neither shows up until someone tries it on that platform.
 */
class FileAssociationTest {
    @Test
    fun `the build script associates every Markdown extension`() {
        assertEquals(DocumentKind.Markdown.extensions, listIn(buildScript, "markdownExtensions"))
    }

    @Test
    fun `the build script associates every Fountain extension`() {
        assertEquals(DocumentKind.Fountain.extensions, listIn(buildScript, "fountainExtensions"))
    }

    @Test
    fun `the macOS declarations are well-formed`() {
        // Spliced into Info.plist unchecked, on a platform this project's build machine cannot
        // package for. A stray tag here would first be found as a Mac build that will not launch.
        declarations()
    }

    @Test
    fun `macOS imports Markdown's type with every Markdown extension`() {
        val markdown = typeDeclaration("UTImportedTypeDeclarations", "net.daringfireball.markdown")

        assertEquals(DocumentKind.Markdown.extensions, extensionsOf(markdown))
    }

    @Test
    fun `macOS exports Fountain's type with every Fountain extension`() {
        // 9.1: Fountain's UTI "must be declared -- export io.fountain.fountain". Imported, macOS
        // would treat it as someone else's type that it may never have heard of.
        val fountain = typeDeclaration("UTExportedTypeDeclarations", "io.fountain.fountain")

        assertEquals(DocumentKind.Fountain.extensions, extensionsOf(fountain))
    }

    @Test
    fun `macOS says the application edits both types`() {
        val handled =
            valueOf(declarations(), "CFBundleDocumentTypes")
                .children("dict")
                .flatMap { valueOf(it, "LSItemContentTypes").children("string") }
                .map { it.textContent }

        assertTrue("net.daringfireball.markdown" in handled, "Markdown is not a document type")
        assertTrue("io.fountain.fountain" in handled, "Fountain is not a document type")
    }

    private val buildScript: String get() = File("build.gradle.kts").readText()

    /** The strings in `private val [name] = listOf(...)`. */
    private fun listIn(
        script: String,
        name: String,
    ): List<String> {
        val list = Regex("""val $name = listOf\(([^)]*)\)""").find(script) ?: error("No list called $name")
        return Regex(""""([^"]+)"""").findAll(list.groupValues[1]).map { it.groupValues[1] }.toList()
    }

    /** The fragment, inside the dictionary it is spliced into. */
    private fun declarations(): Element {
        val fragment = File("packaging/macos/document-types.xml").readText()
        val plist = "<plist><dict>$fragment</dict></plist>"
        val parsed = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(plist.byteInputStream())
        return parsed.documentElement.children("dict").single()
    }

    private fun typeDeclaration(
        list: String,
        identifier: String,
    ): Element =
        valueOf(declarations(), list).children("dict").single {
            valueOf(it, "UTTypeIdentifier").textContent == identifier
        }

    private fun extensionsOf(declaration: Element): List<String> =
        valueOf(valueOf(declaration, "UTTypeTagSpecification"), "public.filename-extension")
            .children("string")
            .map { it.textContent }

    /** In a plist dictionary, the element after `<key>[key]</key>`. */
    private fun valueOf(
        dict: Element,
        key: String,
    ): Element {
        val entries = dict.children()
        val at = entries.indexOfFirst { it.tagName == "key" && it.textContent == key }
        check(at >= 0) { "No $key" }
        return entries[at + 1]
    }

    private fun Element.children(tag: String? = null): List<Element> =
        (0 until childNodes.length)
            .map { childNodes.item(it) }
            .filterIsInstance<Element>()
            .filter { tag == null || it.tagName == tag }
}
