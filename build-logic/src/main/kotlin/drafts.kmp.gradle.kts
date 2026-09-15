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
        withHostTest {}
    }

    // Apple targets are declared unconditionally so that the build file is honest about the
    // product's target matrix. They cannot be *compiled* anywhere but macOS -- Kotlin/Native
    // needs the Xcode toolchain -- so CI runs the Apple half of `check` on a macOS runner.
    // See .github/workflows/ci.yml.
    iosArm64()
    iosSimulatorArm64()
    iosX64()

    compilerOptions {
        // Warnings are not errors in Phase 0: there is no code to warn about, and turning this on
        // before the code exists would be a gate whose first real test is someone else's problem.
        // Revisit in Phase 1 alongside the first real module.
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    sourceSets {
        commonTest.dependencies {
            implementation(versions.findLibrary("kotlin-test").get())
        }
    }
}
