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
    implementation(project(":i18n"))
    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutines.swing)
}

compose.desktop {
    application {
        mainClass = "com.appthere.drafts.app.desktop.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Deb, TargetFormat.Dmg, TargetFormat.Msi)
            packageName = "Drafts"
            packageVersion = "1.0.0"
        }
    }
}
