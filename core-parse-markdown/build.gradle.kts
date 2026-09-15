plugins {
    id("drafts.kmp")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core-model"))

            // The CST this module lowers. `implementation`, not `api`: intellij-markdown's
            // ASTNode is an implementation detail of the lowering and must not leak into the IR,
            // or every consumer of :core-model ends up coupled to the parser
            // (export-pipeline.md: "backends never see Markdown concepts").
            implementation(libs.intellij.markdown)
        }
    }
}
