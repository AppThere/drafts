plugins {
    id("drafts.kmp")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core-model"))
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
