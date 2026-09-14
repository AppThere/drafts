// A plain JVM module. Used by the desktop host and by the tooling projects.

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("drafts.quality")
}

kotlin {
    jvmToolchain(21)
}
