plugins {
    id("drafts.android.app")
}

dependencies {
    implementation(project(":app-shared"))
    implementation(project(":i18n"))
    implementation(libs.androidx.activity.compose)

    testImplementation(libs.kotlin.test)
    testImplementation(kotlin("test-junit"))
    testImplementation(libs.kotlinx.coroutines.test)
}

// `ShortcutsTest` reads the manifest, this module's `res/` and :i18n's Compose resources as text,
// so all three are inputs to it even though nothing it asserts about them goes through the compiler.
tasks.withType<Test>().configureEach {
    inputs.file("src/main/AndroidManifest.xml").withPropertyName("manifest")
    inputs.dir("src/main/res").withPropertyName("resources")
    inputs.dir("../i18n/src/commonMain/composeResources").withPropertyName("strings")
}
