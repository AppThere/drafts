package com.appthere.drafts.app

import androidx.compose.ui.unit.sp
import com.appthere.drafts.design.FocusMode
import com.appthere.drafts.design.Palettes
import com.appthere.drafts.design.ReaderSettings
import com.appthere.drafts.design.Theme
import com.appthere.drafts.platform.files.DocumentRef
import com.appthere.drafts.platform.intents.DocumentKind
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 5.5: "Exposed in settings, **persisted per document type**."
 *
 * Per type and not per document, which is the spec's choice and a good one: a screenplay and a
 * novel want different measures and different weights, and nobody wants to set that up again for
 * every chapter file.
 */
class SettingsStoreTest {
    @Test
    fun `a document type with nothing saved has no settings`() =
        runTest {
            // First launch. It has to be null rather than an error, or the application could not open
            // a document until someone had been into settings.
            assertNull(store().settingsFor("markdown"))
        }

    @Test
    fun `what was saved comes back`() =
        runTest {
            val settings = store()
            val chosen = ReaderSettings(theme = Theme.Fixed(Palettes.Dark), base = BIGGER.sp, bodyWeight = HEAVIER)

            settings.remember("markdown", chosen)

            val restored = requireNotNull(settings.settingsFor("markdown"))
            assertEquals(Theme.Fixed(Palettes.Dark), restored.theme)
            assertEquals(BIGGER, restored.base.value)
            assertEquals(HEAVIER, restored.bodyWeight)
        }

    @Test
    fun `section 12's options are remembered too`() =
        runTest {
            // They are settings like any other, and a reader who turned focus mode on does not want to
            // turn it on again tomorrow.
            val settings = store()

            settings.remember("markdown", ReaderSettings(typewriterScrolling = true, focusMode = FocusMode.Block))

            val restored = requireNotNull(settings.settingsFor("markdown"))
            assertTrue(restored.typewriterScrolling)
            assertEquals(FocusMode.Block, restored.focusMode)
        }

    @Test
    fun `markdown and fountain keep separate settings`() =
        runTest {
            // The whole point of "per document type". A screenplay wants a different measure from a
            // novel, and setting one must not change the other.
            val settings = store()

            settings.remember("markdown", ReaderSettings(theme = Theme.Fixed(Palettes.Sepia)))
            settings.remember("fountain", ReaderSettings(theme = Theme.Fixed(Palettes.Dark)))

            assertEquals(Theme.Fixed(Palettes.Sepia), settings.settingsFor("markdown")?.theme)
            assertEquals(Theme.Fixed(Palettes.Dark), settings.settingsFor("fountain")?.theme)
        }

    @Test
    fun `saving twice keeps the second`() =
        runTest {
            val settings = store()
            settings.remember("markdown", ReaderSettings(theme = Theme.Fixed(Palettes.Dark)))

            settings.remember("markdown", ReaderSettings(theme = Theme.Fixed(Palettes.HighContrast)))

            assertEquals(Theme.Fixed(Palettes.HighContrast), settings.settingsFor("markdown")?.theme)
        }

    @Test
    fun `values outside 5 5's ranges are clamped on the way in`() =
        runTest {
            // A settings file is a file: it can be edited by hand, written by a version with different
            // limits, or corrupted into something that still parses. 5.5 gives every control a range,
            // and the ranges are what keep a document legible.
            val store = FakeDocumentStore(DocumentRef("$ROOT/markdown.json"), OUT_OF_RANGE)

            val restored = requireNotNull(SettingsStore(store, ROOT).settingsFor("markdown"))

            assertTrue(restored.base.value <= MAX_BASE, "Base size came back at ${restored.base}")
            assertTrue(restored.lineHeight <= ReaderSettings.MAXIMUM_LINE_HEIGHT)
        }

    @Test
    fun `an unreadable settings file reads as no settings`() =
        runTest {
            // Settings are a convenience. Losing them costs a reader some fiddling; refusing to open
            // their document over it would cost a great deal more.
            val store = FakeDocumentStore(DocumentRef("$ROOT/markdown.json"), "{ not json at all")

            assertNull(SettingsStore(store, ROOT).settingsFor("markdown"))
        }

    @Test
    fun `an unknown theme falls back rather than failing the load`() =
        runTest {
            // Palettes come and go. The rest of somebody's typography should not go with one.
            val store = FakeDocumentStore(DocumentRef("$ROOT/markdown.json"), UNKNOWN_PALETTE)

            val restored = requireNotNull(SettingsStore(store, ROOT).settingsFor("markdown"))

            assertEquals(Theme.Fixed(Palettes.Light), restored.theme)
            assertEquals(LINE_HEIGHT_IN_FILE, restored.lineHeight)
        }

    @Test
    fun `a file from a later version still loads`() =
        runTest {
            // 5.5 will gain controls. An older build that threw on an unknown key would discard every
            // setting the reader had chosen because of one it did not know.
            val store = FakeDocumentStore(DocumentRef("$ROOT/markdown.json"), FROM_THE_FUTURE)

            assertEquals(Theme.Fixed(Palettes.Dark), SettingsStore(store, ROOT).settingsFor("markdown")?.theme)
        }

    @Test
    fun `following the system is remembered as that, not as the palette it gave today`() =
        runTest {
            // Saved in the evening, a "system" theme resolves to Dark. Remembering Dark would leave
            // the reader in a dark document the next morning, which is not what they chose.
            val settings = store()

            settings.remember("markdown", ReaderSettings(theme = Theme.System))

            assertEquals(Theme.System, settings.settingsFor("markdown")?.theme)
        }

    @Test
    fun `paragraph spacing is remembered`() =
        runTest {
            val settings = store()

            settings.remember("markdown", ReaderSettings(paragraphSpacing = WIDER))

            assertEquals(WIDER, settings.settingsFor("markdown")?.paragraphSpacing)
        }

    @Test
    fun `a file written before paragraph spacing existed keeps everything else`() =
        runTest {
            // `record` has no paragraph spacing in it, which is exactly what every settings file
            // written before this control existed looks like. Failing the load over the missing
            // field would reset the theme and the typography a reader had already chosen.
            val store = FakeDocumentStore(DocumentRef("$ROOT/markdown.json"), BEFORE_PARAGRAPH_SPACING)

            val restored = requireNotNull(SettingsStore(store, ROOT).settingsFor("markdown"))

            assertEquals(Theme.Fixed(Palettes.Dark), restored.theme)
            assertEquals(LINE_HEIGHT_IN_FILE, restored.lineHeight)
            assertEquals(ReaderSettings().paragraphSpacing, restored.paragraphSpacing)
        }

    @Test
    fun `a first launch creates markdown`() =
        runTest {
            // 7.4: "the kind the reader last created -- Markdown on first launch".
            assertEquals(DocumentKind.Markdown, store().kindForNew())
        }

    @Test
    fun `the kind last created is the kind created next`() =
        runTest {
            val settings = store()

            settings.rememberKindForNew(DocumentKind.Fountain)

            assertEquals(DocumentKind.Fountain, settings.kindForNew())
        }

    @Test
    fun `a kind no version knows reads as a first launch`() =
        runTest {
            val store = FakeDocumentStore(DocumentRef("$ROOT/new-document-kind"), "sonnet")

            assertEquals(DocumentKind.Markdown, SettingsStore(store, ROOT).kindForNew())
        }

    private fun store() = SettingsStore(FakeDocumentStore(DocumentRef("$ROOT/unused"), ""), ROOT)

    private companion object {
        const val ROOT = "/settings"
        const val BIGGER = 22f
        const val HEAVIER = 500
        const val MAX_BASE = 28f
        const val LINE_HEIGHT_IN_FILE = 1.8f
        const val WIDER = 1.25f

        val OUT_OF_RANGE = record(baseSp = "999.0", lineHeight = "9.0")
        val UNKNOWN_PALETTE = record(palette = "Solarised", lineHeight = "1.8")
        val FROM_THE_FUTURE = record(palette = "Dark") + ""
        val BEFORE_PARAGRAPH_SPACING = record(palette = "Dark", lineHeight = "1.8")

        fun record(
            palette: String = "Light",
            baseSp: String = "18.0",
            lineHeight: String = "1.6",
        ) = """
            {
              "palette": "$palette",
              "baseSp": $baseSp,
              "lineHeight": $lineHeight,
              "letterSpacing": 0.0,
              "characters": 68.0,
              "bodyWeight": 400,
              "reducedMotion": false,
              "typewriterScrolling": false,
              "focusMode": "Off",
              "autoHideChrome": true,
              "somethingAddedLater": 42
            }
            """.trimIndent()
    }
}
