import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

// Baseline Kotlin Multiplatform module: the target set from appthere-drafts.md 2, minus the
// platform hosts that are apps rather than libraries.
//
// Everything from :core-* through :design-system is pure commonMain (appthere-drafts.md 3).
// The :platform-* modules are the only place expect/actual appears. This plugin does not enforce
// that -- tools/architecture-tests does, so the rule is a failing test rather than a convention
// someone has to remember.

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
    id("drafts.quality")
}

private val versions = extensions.getByType<VersionCatalogsExtension>().named("libs")

private fun sdk(alias: String) = versions.findVersion(alias).orElseThrow {
    IllegalStateException("Version catalog is missing version alias '$alias'")
}.requiredVersion.toInt()

configure<KotlinMultiplatformExtension> {
    // Toolchain 21 rather than 17: the Gradle daemon JVM criteria in
    // gradle/gradle-daemon-jvm.properties already provisions Azul Zulu 21, so reusing it avoids
    // making every clean clone download a second JDK. Bytecode target stays at 17, which is what
    // D8 and the desktop runtime both accept without argument.
    jvmToolchain(21)

    jvm()

    androidLibrary {
        namespace = "com.appthere.drafts." + project.name.replace('-', '.')
        compileSdk = sdk("android-compileSdk")
        minSdk = sdk("android-minSdk")

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }

        // Without this there is no Android test source set at all, and `check` runs commonTest on
        // the JVM target only -- which looks like passing tests and is actually one target's worth
        // of evidence. Phase 1's acceptance asks for the conformance corpus to pass "on JVM,
        // Android, iOS, and native", so the Android half has to be runnable before that lands.
        //
        // Host tests, not device tests: they run on the JVM against the Android variant, so they
        // catch Android-specific compilation and stdlib differences without needing an emulator in
        // CI. Instrumented tests come with the first code that needs a real device.
        withHostTest {
            // `android.util.Log` and its neighbours are stubs in a host test, and the default stub
            // throws rather than returning. Anything that logs on the way past -- Compose
            // Resources does, loading a font -- fails on Android and nowhere else, which reads as
            // an Android bug rather than as the test harness refusing to be an Android device.
            //
            // Returning defaults is the documented remedy. It is safe *because* these are host
            // tests: nothing here is asserting on logging, and anything that genuinely needs the
            // platform belongs in a device test.
            isReturnDefaultValues = true
        }

        // Device tests, for the things a host test cannot answer. Compose Resources reads through
        // an Android `Context`, so "are the fonts actually in the APK" is unanswerable on the JVM
        // -- it fails with "Android context is not initialized" whatever the resources contain.
        // That question, and whether the platform's font fallback covers the scripts 5.1 warns
        // about, need a real device.
        withDeviceTest {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
    }

    // Apple targets are declared unconditionally so that the build file is honest about the
    // product's target matrix. They cannot be *compiled* anywhere but macOS -- Kotlin/Native
    // needs the Xcode toolchain -- so CI runs the Apple half of `check` on a macOS runner.
    // See .github/workflows/ci.yml.
    //
    // iosX64 -- the Intel-Mac simulator -- is deliberately out of the target matrix. Two
    // independent reasons, either of which would be enough: Compose Multiplatform stopped
    // publishing it (`runtime-iosx64`'s last release is 1.11.0-alpha01 and 1.11.1 has no artifact
    // at all), and there is no Intel Mac to run it on. Decided 2026-09-27.
    iosArm64()
    iosSimulatorArm64()

    compilerOptions {
        // Warnings are not errors in Phase 0: there is no code to warn about, and turning this on
        // before the code exists would be a gate whose first real test is someone else's problem.
        // Revisit in Phase 1 alongside the first real module.
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    // A source set for the two targets that are both the JVM.
    //
    // Android and desktop share a standard library, a filesystem API and a `MessageDigest`; what
    // they do not share is a UI toolkit or an application lifecycle. Without somewhere to put the
    // overlap, code that is genuinely identical gets written twice -- `:platform-files` had two
    // copies of the same SHA-256 actual for exactly this reason -- and the second copy is the one
    // that drifts.
    //
    // Named `jvmAndroid` rather than `jvmCommon` so it cannot be mistaken for `commonMain`.
    applyDefaultHierarchyTemplate()

    sourceSets {
        val jvmAndroidMain = create("jvmAndroidMain") { dependsOn(commonMain.get()) }
        val jvmAndroidTest = create("jvmAndroidTest") { dependsOn(commonTest.get()) }

        jvmMain.get().dependsOn(jvmAndroidMain)
        androidMain.get().dependsOn(jvmAndroidMain)
        jvmTest.get().dependsOn(jvmAndroidTest)

        commonTest.dependencies {
            implementation(versions.findLibrary("kotlin-test").get())
        }
    }
}
