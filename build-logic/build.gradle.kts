plugins {
    `kotlin-dsl`
}

dependencies {
    // Convention plugins apply these, so they must be on the build-logic compile classpath.
    implementation(libs.plugin.kotlin.gradle)
    implementation(libs.plugin.android.gradle)
    implementation(libs.plugin.compose.gradle)
    implementation(libs.plugin.composeCompiler.gradle)
    implementation(libs.plugin.detekt.gradle)
    implementation(libs.plugin.spotless.gradle)
}
