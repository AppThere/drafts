import com.appthere.drafts.buildlogic.DesktopEntry
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

// The desktop host (appthere-drafts.md 3: ":app-desktop  JVM main, file associations").
//
// A plain JVM module rather than a multiplatform one: macOS, Windows and Linux are all the same
// Compose Desktop JVM target (2, target matrix), so there is nothing here to be multiplatform
// about. It consumes :app-shared, which resolves to that module's jvm variant.

plugins {
    id("drafts.jvm")
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

dependencies {
    implementation(project(":app-shared"))
    implementation(project(":editor-ui"))
    implementation(project(":i18n"))
    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutines.swing)

    testImplementation(libs.kotlin.test)
    testImplementation(kotlin("test-junit5"))
}

// `FileAssociationTest` reads this script and `packaging/` as text, so they are inputs to it even
// though nothing compiles them into the test.
tasks.test {
    useJUnitPlatform()
    inputs.file("build.gradle.kts").withPropertyName("buildScript")
    inputs.dir("packaging").withPropertyName("packaging")
}

/**
 * 9.1's type table, as the installers declare it (9.4).
 *
 * The same table as `DocumentKind` in `:platform-intents`, which is what the running application
 * decides with. A build script cannot call into the code it builds, so the two are kept in step by
 * `FileAssociationTest` rather than by being one list.
 *
 * Declared above `compose.desktop` because a script initialises its properties in order, and that
 * block reads these while it runs.
 */
private val markdownExtensions = listOf("md", "markdown", "mdown", "mkd")
private val fountainExtensions = listOf("fountain", "spmd")

/**
 * Fountain has no registered MIME type (9.1). Linux's shared-mime-info needs a name to declare, so
 * it gets the unregistered `text/x-fountain`, which nothing else claims.
 */
private val fountainMime = "text/x-fountain"

private val packaging = layout.projectDirectory.dir("packaging")

compose.desktop {
    application {
        mainClass = "com.appthere.drafts.app.desktop.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Deb, TargetFormat.Dmg, TargetFormat.Msi)
            packageName = "Drafts"
            packageVersion = "1.0.0"

            // Fountain only. Each association becomes an entry in the package's shared-mime-info
            // file, and text/markdown is already the system's -- redeclaring it would install a
            // second definition of a type the reader's desktop already has. The desktop entry,
            // corrected at the end of this file, names both kinds as what the application opens.
            linux {
                packageName = "appthere-drafts"
                appCategory = "Office"
                menuGroup = "Office"
                fountainExtensions.forEach { fileAssociation(fountainMime, it, "Fountain screenplay") }
            }

            // One registry association per extension, which is the shape Windows wants. The path
            // arrives as argv[1] and goes through the single-instance routing in `Main.kt`.
            //
            // The upgrade code is permanent. It is how Windows Installer knows a new version replaces
            // an old one rather than installing beside it, so it must never change.
            windows {
                menuGroup = "AppThere"
                upgradeUuid = "6b2d81f6-794e-4d66-922e-478c28f4cc03"
                markdownExtensions.forEach { fileAssociation("text/markdown", it, "Markdown document") }
                fountainExtensions.forEach { fileAssociation("text/plain", it, "Fountain screenplay") }
            }

            // Declared in Info.plist directly rather than through file associations, which cannot
            // export a type -- and 9.1 says Fountain's "must be declared -- export
            // io.fountain.fountain". The open-document event itself is `Desktop.setOpenFileHandler`,
            // in `:platform-intents`.
            macOS {
                bundleID = "com.appthere.drafts"
                infoPlist {
                    extraKeysRawXml = packaging.file("macos/document-types.xml").asFile.readText()
                }
            }
        }
    }
}

// 9.4's `.desktop` entry: `%f` so a double-clicked document is passed in, and the MIME types this
// application opens. jpackage's own has neither right; `DesktopEntry` says why it is fixed after
// the fact.
tasks.withType<org.jetbrains.compose.desktop.application.tasks.AbstractJPackageTask>().configureEach {
    if (targetFormat == TargetFormat.Deb) {
        val opens = listOf("text/markdown", "text/x-markdown", fountainMime)
        // 7.4's launcher entry points on Linux, in the launcher's context menu. English here: the
        // desktop entry has its own `Name[lang]=` keys for translations, and nothing in the build
        // can reach the application's strings.
        val actions =
            listOf(
                DesktopEntry.Action("new-markdown", "New Markdown document", "--new markdown"),
                DesktopEntry.Action("new-fountain", "New Fountain screenplay", "--new fountain"),
            )
        doLast {
            destinationDir.get().asFile.listFiles { file -> file.extension == "deb" }.orEmpty().forEach {
                DesktopEntry.fixDeb(it, opens, actions)
            }
        }
    }
}
