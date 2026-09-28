plugins {
    id("drafts.android.app")
}

dependencies {
    implementation(project(":app-shared"))
    implementation(libs.androidx.activity.compose)
}
