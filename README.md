# AppThere Drafts

A LyX-style What You See Is What You Mean editor for Markdown and Fountain documents, in a
distraction-free interface. Kotlin Multiplatform with Compose UI on every target: desktop,
Android, iOS/iPadOS, and Android XR.

Implementation follows [`IMPLEMENTATION-PLAN.md`](IMPLEMENTATION-PLAN.md). **Phase 0 (Foundation)
is complete**: the module skeleton and every quality gate are in place, and the modules are
deliberately empty. Phase 1 adds the first code, to `:core-model`.

---

## Building

Requires a JDK to launch Gradle; the daemon JVM (Azul Zulu 21) is provisioned automatically from
`gradle/gradle-daemon-jvm.properties`, and the Android SDK location comes from `local.properties`
or `ANDROID_HOME`.

```sh
./gradlew check          # detekt, Spotless, Konsist, tests -- the gate
./gradlew detektWarn     # warn-threshold report; never fails
./gradlew spotlessApply  # fix formatting
./gradlew buildHealth    # unused / misdeclared dependencies
```

The iOS targets (`iosArm64`, `iosSimulatorArm64`) are declared everywhere they belong but
can only be **compiled on macOS** -- Kotlin/Native needs the Xcode toolchain. CI runs them on a
macOS runner; `./gradlew check` on Linux or Windows covers the JVM and Android halves.

### Layout

| Path | What it is |
|---|---|
| `core-*`, `editor-*`, `design-system`, `i18n`, `a11y`, `platform-*`, `app-*` | The product modules, per [`appthere-drafts.md` 3](specifications/appthere-drafts.md) |
| `build-logic/` | Convention plugins. One place where the target set and the quality gates are configured |
| `config/detekt/` | `detekt.yml` (fail thresholds, gates the build) and `detekt-warn.yml` (report only) |
| `tools/detekt-rules/` | The project's own detekt rules, each with a test that deliberately violates it |
| `tools/architecture-tests/` | Konsist architecture assertions, plus fixtures proving they fire |

There is no `detekt-baseline.xml`, and CI fails if one appears.
[`engineering-conventions.md` 3](specifications/engineering-conventions.md) permits exactly one
baseline; Phase 0 was done first so that it would never be spent.

---

## Document set

### Start here

| Document | What it covers |
|---|---|
| [`IMPLEMENTATION-PLAN.md`](IMPLEMENTATION-PLAN.md) | Thirteen phases, gates, acceptance criteria, and how to drive it with Claude Code |
| [`AGENTS.md`](AGENTS.md) | The quality pass every coding session runs before reporting work complete |

### Normative — what this project is

| Document | What it covers |
|---|---|
| [`specifications/appthere-drafts.md`](specifications/appthere-drafts.md) | The application. Editing model, typography, windowing, document lifecycle, OS integration, accessibility, i18n |
| [`specifications/projects.md`](specifications/projects.md) | Scrivener-style projects over plain folder trees. Binder, ordering, metadata, compile |
| [`specifications/markdown-dialect.md`](specifications/markdown-dialect.md) | The Markdown flavour: CommonMark 0.31.2 plus seven extensions, and the round-trip contract |
| [`specifications/export-pipeline.md`](specifications/export-pipeline.md) | Parser → IR → backend architecture. Export-only ODT and DOCX |
| [`specifications/engineering-conventions.md`](specifications/engineering-conventions.md) | Size limits, static analysis, the code-smell catalogue, architecture assertions |

### Reference — formats this project reads or writes

Condensed implementer's references, in original words, with canonical links and licence terms.
Not copies of the upstream specifications.

| Document | Format | Role |
|---|---|---|
| [`specifications/gfm.md`](specifications/gfm.md) | GitHub Flavored Markdown | Background for the dialect |
| [`specifications/hugo-markdown.md`](specifications/hugo-markdown.md) | Hugo / Goldmark | Background for the dialect |
| [`specifications/fountain.md`](specifications/fountain.md) | Fountain 1.1 | Read and written |
| [`specifications/epub3.md`](specifications/epub3.md) | EPUB 3.4 / 3.3 | Export target |
| [`specifications/odf-text.md`](specifications/odf-text.md) | OpenDocument Text 1.4 | Export target |
| [`specifications/ooxml-docx.md`](specifications/ooxml-docx.md) | OOXML WordprocessingML | Export target |

[`specifications/README.md`](specifications/README.md) holds the licence table for the upstream
specifications and commands for fetching the originals.

---

## The shape of it

**The source file is the document.** Plain text in, plain text out. A project is an ordinary
folder of ordinary files with real names — the filesystem *is* the binder. Nothing in this app
produces a file that only this app can open.

**WYSIWYM, not WYSIWYG.** The document renders at an approximation of final formatting. The
paragraph holding the cursor shows its markup; everything else shows the result. Heading sizes and
typeface stay constant across that switch, so nothing moves while you type.

**Two guarantees, two mechanisms.** Autosave writes snapshots to app-private storage and never
touches your file. Explicit saves check a content digest first and refuse to overwrite a file that
changed underneath them.

**Markdown and Fountain side by side.** Mixed freely within a project, one kind per document.
Character notes open next to the scene you're writing.

---

## Key decisions already made

| Decision | Where |
|---|---|
| CommonMark 0.31.2 + tables, strikethrough, linkify, footnotes, definition lists, typographer, attributes | `markdown-dialect.md` §1 |
| Typographer is an input method, not a parse step — so the file keeps what you typed | `markdown-dialect.md` §6 |
| Build on `intellij-markdown` plus four custom extensions; evaluate KMP Markdown in parallel | `markdown-dialect.md` §"Implementation" |
| One IR, N backends — parsers never see output concepts | `export-pipeline.md` §1 |
| ODT and DOCX are write-only handoff targets, not round-trip formats | `export-pipeline.md` §"Scope" |
| Use built-in office style IDs so TOC, navigation, and PDF bookmarks work | `export-pipeline.md` §"DOCX" |
| Per-block text fields in a `LazyColumn`, not one field with a `VisualTransformation` | `appthere-drafts.md` §4.3 |
| Atkinson Hyperlegible Next + Mono, OFL 1.1, variable fonts, with a script fallback chain | `appthere-drafts.md` §5.1 |
| One window = one document. No tabs. | `appthere-drafts.md` §7.1 |
| Projects are plain folders with a `.drafts/` sidecar, not an opaque bundle | `projects.md` §1 |
| No privileged folder names — compile targets are explicit glob sets | `projects.md` §2 |
| Size limits are warn/fail bands that demand a decision, not an instruction to split | `engineering-conventions.md` §1 |

---

## Known risks

| Risk | Severity | Handling |
|---|---|---|
| Cross-block text selection in the per-block architecture | **High** | Gate at Phase 2; failing it changes the architecture |
| Android SAF traversal performance at project scale | Medium | Mtime-cached incremental walk, Phase 8 |
| Compose Multiplatform iOS multi-scene | Medium | Scoped as UIKit work, Phase 11 |
| Non-Latin font fallback quality varies by platform | Medium | Verified per target in Phase 3 |
| Bidi rendering of Markdown syntax in reveal state | Medium | Isolate markup runs; dedicated fixtures |
| `androidx.xr.compose` is alpha | Low | Feature-flagged, Phase 12 |

---

## Open questions

Listed with the phase that needs them settled in `IMPLEMENTATION-PLAN.md` §"Decision points".
The first one is worth answering before anything else: the `AppThere Drafts` name places this in
the suite, reversing an earlier decision that it be standalone and deliberately simple. Shared
branding tends to pull in shared expectations about feature parity and release cadence. If the
standalone character still matters, the name is the first thing that will erode it.
