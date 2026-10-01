package com.appthere.drafts.app.android

import com.appthere.drafts.i18n.Strings
import com.appthere.drafts.platform.intents.DocumentKind
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * `appthere-drafts.md` 7.4's Android entry points, as the manifest and resources declare them.
 *
 * "Every platform's launcher offers both kinds directly ... | Android | Static app shortcuts: *New
 * Markdown document*, *New Fountain screenplay*."
 *
 * Three things here cannot be checked by the compiler and would each fail silently on a device: a
 * shortcut naming an Activity that is not there, a shortcut carrying a kind id `DocumentKind` does
 * not know, and the labels drifting from the ones the other platforms' launchers show. The last is
 * the awkward one -- Android's static shortcuts can only name a string resource, and the Linux and
 * macOS entries are built from `Strings`, so there are two copies of each label by necessity.
 */
class ShortcutsTest {
    @Test
    fun `both of 7_4's kinds are offered`() {
        assertEquals(listOf("new-markdown", "new-fountain"), shortcuts().map { it.getAttribute("android:shortcutId") })
    }

    @Test
    fun `each shortcut carries a kind the application knows`() {
        val carried = shortcuts().map { kindOf(it) }

        assertEquals(DocumentKind.entries.map { it.id }, carried)
    }

    @Test
    fun `each shortcut starts the launcher rather than a second way in`() {
        // 7.4: "routed through the same path as opening a file". A shortcut that went straight to a
        // document Activity would skip the decision every other launch goes through.
        shortcuts().forEach { shortcut ->
            val intent =
                assertNotNull(shortcut.element("intent"), "No intent in ${shortcut.getAttribute("android:shortcutId")}")

            assertEquals(LAUNCHER, intent.getAttribute("android:targetClass"))
            assertEquals(PACKAGE, intent.getAttribute("android:targetPackage"))
        }
    }

    @Test
    fun `the launcher the shortcuts name is the one in the manifest`() {
        val activities = manifest().elements("activity").map { it.getAttribute("android:name") }

        assertTrue(".LauncherActivity" in activities, "The manifest declares $activities")
        assertEquals(LAUNCHER, PACKAGE + ".app.android.LauncherActivity")
    }

    @Test
    fun `the labels say what the other platforms' launchers say`() {
        // Linux's `.desktop` actions and macOS's Dock menu are built from these same two strings.
        assertEquals(Strings.NEW_MARKDOWN, string("new_markdown"))
        assertEquals(Strings.NEW_FOUNTAIN, string("new_fountain"))
    }

    @Test
    fun `the extra the shortcuts set is the one the launcher reads`() {
        // A typo on either side is a shortcut that opens the last kind instead of the named one,
        // which looks like the application ignoring the reader rather than like a bug.
        shortcuts().forEach { shortcut ->
            val extra = assertNotNull(shortcut.element("intent")?.element("extra"))

            assertEquals(LauncherActivity.EXTRA_NEW_KIND, extra.getAttribute("android:name"))
        }
    }

    private fun kindOf(shortcut: Element): String =
        shortcut
            .element("intent")
            ?.element("extra")
            ?.getAttribute("android:value")
            .orEmpty()

    private fun shortcuts(): List<Element> = parse(File("src/main/res/xml/shortcuts.xml")).elements("shortcut")

    private fun manifest(): Element = parse(File("src/main/AndroidManifest.xml"))

    private fun string(name: String): String =
        parse(File("src/main/res/values/strings.xml"))
            .elements("string")
            .single { it.getAttribute("name") == name }
            .textContent

    private fun parse(file: File): Element =
        DocumentBuilderFactory
            .newInstance()
            .newDocumentBuilder()
            .parse(file)
            .documentElement

    private fun Element.elements(tag: String): List<Element> =
        getElementsByTagName(tag).let { nodes ->
            (0 until nodes.length).mapNotNull { nodes.item(it) as? Element }
        }

    private fun Element.element(tag: String): Element? = elements(tag).firstOrNull()

    private companion object {
        const val PACKAGE = "com.appthere.drafts"
        const val LAUNCHER = "com.appthere.drafts.app.android.LauncherActivity"
    }
}
