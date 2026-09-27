# AppThere Drafts — Implementation Plan

A phased plan for building the app described in `specifications/`. Written to be handed to
Claude Code.

---

## How to use this with Claude Code

**Point it at the specs, not at this file alone.** Every phase below names the spec sections it
implements. The specs are normative; this plan is only sequencing.

**One task per session, not one phase per session.** A phase is weeks of work and a dozen or more
sessions. Decompose each phase's deliverables into tasks that fit in a single context window —
roughly "one module, one capability, with its tests." Each phase below lists its deliverables at
approximately that granularity.

**`AGENTS.md` at the repository root governs every session.** It defines the quality pass that
runs before any work is reported complete. Don't restate it in task prompts; Claude Code reads it
automatically.

**A good task prompt looks like:**

> Implement the footnote extension for the Markdown parser, per
> `specifications/markdown-dialect.md` §4 and the parser architecture in
> `specifications/export-pipeline.md`. Add conformance fixtures under
> `core-parse-markdown/src/commonTest/fixtures/footnotes/`. Run the quality pass in `AGENTS.md`
> before reporting.

**Gates are real.** Phases 2 and 10 contain decisions that can invalidate work downstream. Don't
let a session route around a failing gate by relaxing it.

---

## Repository layout

```
/
├── AGENTS.md                       ← agent protocol, read every session
├── README.md
├── IMPLEMENTATION-PLAN.md          ← this file
├── specifications/                 ← normative specs
│   ├── README.md
│   ├── appthere-drafts.md
│   ├── projects.md
│   ├── markdown-dialect.md
│   ├── export-pipeline.md
│   ├── engineering-conventions.md
│   ├── gfm.md
│   ├── hugo-markdown.md
│   ├── fountain.md
│   ├── epub3.md
│   ├── odf-text.md
│   └── ooxml-docx.md
├── gradle/libs.versions.toml
├── config/detekt/detekt.yml
└── <modules per specifications/appthere-drafts.md §3>
```

---

## Standing requirements

These are not phases. They are conditions on every phase, and treating them as a phase is the
failure mode to avoid.

**Accessibility and internationalisation are per-phase acceptance criteria**, per
`appthere-drafts.md` §10–11. Every phase that adds UI ships with semantics, resource-backed
strings, `start`/`end` layout, and verification at 200% font scale. The audit milestones below
are for finding what was missed — not for doing the work.

**The quality pass in `AGENTS.md` §2 runs before any work is reported complete.** Both phases:
mechanical and judgment.

**Specs are living.** When implementation reveals a spec is wrong, update the spec in the same
change. A spec that drifts from the code is worse than no spec.

---

## Recurring gates

| Gate | When | What |
|---|---|---|
| **Performance** | End of every phase from 2 onward | 10,000-word fixture, slowest target device. Typing latency, scroll, reparse bounds. Regressions block. |
| **Accessibility audit** | After phases 5, 9, 12 | Real assistive technology, used by someone who uses it daily. TalkBack, VoiceOver, NVDA, Orca. Not a checklist review. |
| **i18n audit** | After phases 5, 9, 12 | RTL layout, CJK IME composition, 200% font scale, non-Latin font fallback on every target. |
| **Spec reconciliation** | End of every phase | Does the code still match `specifications/`? Update whichever is wrong. |

---

## Phase 0 — Foundation

**Goal:** A repository where the quality gates work before there is any code to gate.

Doing this first means the detekt baseline starts empty and stays empty. Introducing tooling to
an existing codebase forces a baseline, and `engineering-conventions.md` §3 permits exactly one.
Don't spend it.

**Deliverables**
- Gradle multi-module KMP skeleton per `appthere-drafts.md` §3. Empty modules with correct
  dependency directions.
- `gradle/libs.versions.toml` version catalog.
- Targets configured: JVM desktop, Android, iOS (arm64, simulator arm64).
  `iosX64` -- the Intel-Mac simulator -- is deliberately out of the matrix: Compose Multiplatform
  no longer publishes that target, and there is no Intel Mac to run it on.
- detekt + `io.nlopez.compose.rules` + detekt-formatting, configured to the thresholds in
  `engineering-conventions.md` §2.
- Spotless/ktlint, Konsist, Kover, dependency-analysis.
- Konsist architecture assertions from `engineering-conventions.md` §5, all passing trivially.
- `AGENTS.md` at root; `specifications/` committed.
- CI: `./gradlew check` on every target, plus the baseline-size assertion.
- Custom detekt rules: `runBlocking` outside tests, hardcoded user-facing strings, `dp` for text,
  directional padding, XML-by-concatenation in `:core-export-*`.

**Acceptance**
- `./gradlew check` passes on a clean clone, on all targets.
- `detekt-baseline.xml` does not exist.
- A deliberately-introduced violation of each custom rule fails the build. Verify this; an
  unverified custom rule is usually a rule that never fires.

**Spec refs:** `appthere-drafts.md` §3, `engineering-conventions.md` §2–5

---

## Phase 1 — Core text model

**Goal:** Markdown in, IR, Markdown out, byte-identical. No UI.

**Deliverables**
- `:core-model` — the IR from `export-pipeline.md`: `Block`, `Inline`, `BlockRole`, `Attributes`,
  `Document`. Value classes for `BlockIndex`, `BlockOffset`, `DocumentOffset`.
- `:core-parse-markdown` — `intellij-markdown` integration; CST → IR lowering.
- The four dialect extensions: footnotes, definition lists, attribute syntax, and linkify with
  `https` default. (Typographer is not a parser feature — see `markdown-dialect.md` §6.)
- Front matter extraction (TOML/YAML/JSON), preserved verbatim and format-tagged.
- Shortcode tokenisation into opaque `RawPassthrough` spans.
- `:core-serialise` — IR → Markdown, source-span preserving.
- CommonMark 0.31.2 conformance harness running all 652 examples from `commonTest`, on every
  target.
- Fixture sets for the four extensions. No published suite exists for definition lists or
  attributes; write them.
- Round-trip property tests: parse → serialise is byte-identical for unedited documents.

**Acceptance**
- 652/652 CommonMark examples pass on JVM, Android, iOS, and native.
- Round-trip is byte-identical across a corpus of real-world documents, including files with
  front matter, shortcodes, and mixed emphasis delimiters.
- Editing one block and re-serialising leaves every other byte untouched.

**Notes for the agent**
`intellij-markdown` produces a *concrete* syntax tree — emphasis nodes contain the `*` marker
tokens as children, alongside `TEXT`, `WHITE_SPACE`, and `EOL`. The lowering step is where marker
filtering happens, once. Do not filter markers at each backend.

**Spec refs:** `markdown-dialect.md` (all), `export-pipeline.md` §2–3, `gfm.md`

---

## Phase 2 — Editor spike ⚠️ **ARCHITECTURE GATE**

**Goal:** Prove the per-block editing architecture, or discover it doesn't work — cheaply.

This is the riskiest phase and it comes second for that reason. Desktop only; provisional
styling; no persistence. The deliverable is knowledge.

**Deliverables**
- `:editor-engine` — block list, focus model, incremental reparse with bounded dirty range,
  undo/redo.
- `:editor-ui` — `LazyColumn` of blocks with stable keys. Focused block is a `BasicTextField`
  showing raw source; unfocused blocks are `Text` with `AnnotatedString` preview.
- Reveal/preview semantics per `appthere-drafts.md` §4.1: block identity stable, inline decoration
  reveals. Heading size and typeface constant across states.
- Vertical stability: dual measurement so revealing a block never shifts the page (§4.2).
- Caret movement across block boundaries; Enter splits; Backspace at offset 0 merges; structural
  reparse preserves focus and caret offset.
- **Cross-block selection layer prototype** (§4.4): selection as `(blockId, offset)` anchor/focus
  in the engine, highlight rectangles drawn per block from each `TextLayoutResult`, copy/cut/delete
  against the IR.

**Gate criteria — all must hold on a 10,000-word fixture, on the slowest target device:**
- Typing latency stays below one frame at 120Hz.
- Reparse range is provably bounded — asserted in a test, not observed.
- Selection drag across 50+ blocks is smooth and the highlight is correct.
- Select-all → copy yields correct source text.
- Focus and caret survive every structural edit.
- Nothing shifts vertically when focus enters or leaves a block.

**If the gate fails:** stop. Report which criterion failed and why. The fallback is a custom text
layout surface (`appthere-drafts.md` §4.3, option 3), which is a much larger project and changes
the accessibility approach entirely. That decision is a human's, and it is far cheaper now than
in phase 9.

**Spec refs:** `appthere-drafts.md` §4 (all)

---

## Phase 3 — Design system

**Goal:** The typography and theming that make it feel like a writing tool.

**Deliverables**
- `:design-system` — Atkinson Hyperlegible Next and Mono variable fonts via Compose Resources,
  with the per-glyph fallback chain from `appthere-drafts.md` §10.3.
- Prose scale (§5.2) as ratios of a user-adjustable base, honouring OS font scale.
- Measure calculation (§5.3), gutters, responsive content column.
- Themes: light, dark, sepia, high contrast. Contrast ratios computed and tested, not eyeballed.
- Reader controls (§5.5): size, line height, letter spacing, measure, weight.
- Window size class handling (§6) — Compact / Medium / Expanded, height classes included.
- Motion tokens honouring reduced-motion.
- OFL licence screen.

**Acceptance**
- Coherent at 100% and 200% system font scale.
- All themes meet 4.5:1 body / 3:1 large; high contrast meets 7:1, verified by test.
- Non-Latin text renders via fallback on every target — verify Japanese, Arabic, and Devanagari
  specifically. Atkinson covers Latin, Cyrillic, and Greek only.

**Spec refs:** `appthere-drafts.md` §5–6, §10.2–10.3

---

## Phase 4 — Document lifecycle

**Goal:** Never lose work; never overwrite silently. Two mechanisms, kept separate.

**Deliverables**
- `:platform-files` — `expect`/`actual` document access. Thin adapters only.
- Atomic write primitive: temp + fsync + rename. **Nothing outside this module may write.**
  Enforced by Konsist.
- Snapshot system (§8.1): triggers, app-private storage, `meta.json` with caret and scroll.
- Digest check (§8.2): `baseDigest` at open; re-read and compare before every write; conflict UI.
- Recovery on launch (§8.3) with the compare/discard banner. Never auto-discard.
- Session state model (§8.4): `clean` / `dirty` / `conflicted` / `orphaned` / `readOnly`.
- Session persistence (§7.3) including per-platform access tokens.

**Acceptance**
- Kill the process mid-edit at 100 random points; work is recoverable every time.
- Modify the file externally while open; the digest check fires and no write occurs.
- Snapshot writes are atomic under `kill -9` during write.
- Session restores caret, scroll, and window bounds.

**Spec refs:** `appthere-drafts.md` §7.3, §8

---

## Phase 5 — Desktop alpha

**Goal:** A shippable single-format desktop Markdown editor. First real usability signal.

**Deliverables**
- `:platform-windows` desktop — multiple `Window` composables, per-window `WindowState`, persisted.
- `:platform-intents` desktop — macOS `setOpenFileHandler`, Windows registry associations +
  single-instance routing, Linux `.desktop` + shared-mime-info.
- Distraction-free chrome (§12): auto-hide, typewriter scrolling, focus mode, full screen.
- Settings UI.
- Packaging for macOS, Windows, Linux.

**Acceptance**
- Double-clicking a `.md` file opens it in a new window of the running instance.
- Multiple documents side by side.
- Sessions restore across restart.
- **Milestone: accessibility and i18n audits** (see Recurring gates).

**Spec refs:** `appthere-drafts.md` §7.2, §9.4, §12

---

## Phase 6 — Android

**Goal:** Surface platform risk early rather than after the architecture has set.

Android before iOS: SAF, intents, and task-per-document are the sharpest edges in the project, and
they constrain the project model in phase 8.

**Deliverables**
- Document Activity with `documentLaunchMode="intoExisting"`, `resizeableActivity`, per-document
  `taskAffinity`, `TaskDescription`.
- Intent filters per `appthere-drafts.md` §9.2: MIME-based, extension-based `pathPattern`, and
  `SEND`. One `<data>` element per extension — `pathPattern` has no alternation.
- `takePersistableUriPermission` at open, always. Without it, session restore fails silently.
- Content sniffing for `application/octet-stream` handoffs from downloads and messaging apps.
- Responsive layouts across Compact/Medium/Expanded.
- Foldable support via `FoldingFeature` — never render text across a hinge.
- Split-screen with a second instance of the app.

**Acceptance**
- Opens from Files, Downloads, Drive, Gmail, and a messaging app.
- Two documents side by side in split-screen on a tablet and on ChromeOS.
- Sessions restore after force-stop.
- Performance gate on the slowest supported device.

**Spec refs:** `appthere-drafts.md` §7.2, §9.1–9.2

---

## Phase 7 — Fountain

**Goal:** Screenplay editing, reusing everything built so far.

**Deliverables**
- `:core-parse-fountain` — hand-written Fountain 1.1 parser, ~500 lines. Parse order per
  `fountain.md`: boneyard, notes, title page, blocks, forcing characters, inference, inlines.
- Lowering to IR using the Fountain `BlockRole` values.
- `:core-serialise` Fountain output, source-preserving. Fountain is its own canonical
  serialisation, so byte-preservation is nearly free.
- Screenplay rendering (`appthere-drafts.md` §5.4): percentage insets, mono sized so 61 characters
  fit the measure, US Letter width ceiling, dual dialogue with compact stacking.
- Role reclassification debounce (§4.5) so indentation doesn't shift mid-typing.
- Configurable scene-heading prefixes and transition suffix (§11.3) — Fountain 1.1's keywords are
  English.
- Notes, boneyard, sections, synopses: dimmed and collapsible in preview, full source in reveal.

**Acceptance**
- Round-trip byte-identical on a corpus of real `.fountain` files.
- Typing a character name doesn't cause indentation to jump.
- Fixtures for the ambiguous cases: uppercase action vs character, `CUT TO: ` with trailing space,
  `..` vs forced scene heading, blank-line-with-space inside dialogue.

**Spec refs:** `fountain.md`, `appthere-drafts.md` §4.5, §5.4, §11.3

---

## Phase 8 — Projects: core

**Goal:** Folder-as-binder, with external changes safe.

**Deliverables**
- `:project-model` — tree walk, project detection, implicit `.drafts/` creation on first need.
- `project.toml`: schema, order, labels, statuses, targets, collections. Line-stable on write.
- Three-tier ordering (`projects.md` §3): manifest → numeric prefix → alphabetical. Both
  `manifest` and `prefix` modes.
- Reconciliation (§9): new / missing / modified / orphaned, with content-hash relinking. Never
  writes to user files.
- **Android SAF performance work**: `DocumentsContract.buildChildDocumentsUriUsingTree` with a
  minimal projection, per-directory mtime cache in `.drafts/cache/`, off-main-thread walk with
  progressive population. `DocumentFile.listFiles()` is too slow at project scale.
- Filesystem watching per platform.
- Binder UI: tree, drag reorder and reparent, create/rename/move/delete.
- Trash (§7) with restore.
- Project templates (§2).
- Project windows and document-window association (§12).

**Acceptance**
- A file added externally appears, marked new, without disturbing anything.
- A file deleted externally is marked missing; metadata is retained.
- Reorder in `prefix` mode renames correctly and git detects the renames.
- Binder populates progressively on a 500-file project over SAF without blocking.

**Spec refs:** `projects.md` §1–4, §7, §9, §12

---

## Phase 9 — Projects: metadata and views

**Deliverables**
- Per-document metadata (§4): front matter for Markdown (namespaced under `drafts:`), sidecar for
  Fountain. Front matter wins. Sidecars follow moves atomically.
- Outliner — table view, sortable, filterable, in-place editing. **Build before the corkboard**;
  it's the accessible equivalent and it validates the metadata model.
- Corkboard — index cards, drag to reorder. Identical data and operations to the outliner;
  selection preserved when switching.
- Collections: manual and saved-search.
- Targets and progress (§11), project views only — never ambient in the editor.
- Snapshots (§8): manual, per document, browse/restore/diff.
- `.gitignore` in templates.

**Acceptance**
- Outliner and corkboard expose identical data and operations.
- Corkboard is fully keyboard-navigable; screen reader users have the outliner as an equal path.
- Word counts reported separately per document kind — never summed across Markdown and Fountain.
- **Milestone: accessibility and i18n audits.**

**Spec refs:** `projects.md` §4–5, §8, §10–11

---

## Phase 10 — Export and compile

**Goal:** Documents leave the app intact.

Order matters: XHTML is the substrate for EPUB, and FODT is the first point the office handoff
story works end to end.

**10a — XHTML**
- XML serialiser. Void elements self-closed, XML escaping, no string templating.
- Footnotes as `epub:type="noteref"` / `<aside epub:type="footnote">`.
- Structural semantics: `epub:type` plus DPUB-ARIA `role`.
- Raw HTML from Markdown source reserialised as XML or escaped — never passed through.
- Fountain → XHTML with percentage insets and the US Letter ceiling.

**10b — EPUB 3**
- OCF packaging: `mimetype` stored first and uncompressed, `META-INF/container.xml`.
- Package document: metadata with mandatory `dcterms:modified`, manifest, spine.
- Navigation document; legacy `toc.ncx` alongside.
- Accessibility metadata per EPUB Accessibility 1.2.
- **EPUBCheck in CI.** Target 3.3; watch 3.4.

**10c — FODT, then ODT**
- Flat ODF first — single XML file, diffable fixtures, opens directly in LibreOffice.
- Whitespace encoding: `text:s`, `text:tab`, `text:line-break`. Non-negotiable.
- Automatic vs common styles; built-in style names (`Heading_20_1`) with `text:outline-level`.
- Package to `.odt`: split parts, manifest, ZIP with `mimetype` stored first.

**10d — DOCX**
- OPC writer: `[Content_Types].xml`, relationships.
- `xml:space="preserve"` on every `w:t`, unconditionally.
- `w:pPr` / `w:rPr` first-child ordering — schema requirement.
- Units module: twips, half-points, eighths, EMU. Tested in isolation.
- **Built-in style IDs** (`Heading1`…`Heading9`, `Title`, `Quote`, `ListParagraph`) with
  `w:outlineLvl` — this is what gives the user a working TOC, navigation pane, and PDF bookmarks.
- Unevaluated `TOC`, `PAGE`, `NUMPAGES` fields with `<w:updateFields w:val="true"/>`.
- `.dotx` template carrying styles.
- Unmapped `{.class}` → character style named after the class; log, don't drop.

**10e — Compile**
- Compile targets in `project.toml` (`projects.md` §6): globs, order, separators, heading offset
  by depth, title derivation.
- Concatenate constituent documents' IR into one `Document`, hand to a backend. No backend
  changes needed.
- Fountain compile: join scenes, single generated title page, script-wide scene numbering.

**Acceptance**
- EPUBCheck clean on every fixture.
- `soffice --headless --convert-to pdf` succeeds on every DOCX and ODT fixture, in CI.
- Manual: styles appear in Word's and LibreOffice's gallery; Insert → TOC populates; PDF export
  produces a bookmark tree.

**Spec refs:** `export-pipeline.md` (all), `epub3.md`, `odf-text.md`, `ooxml-docx.md`,
`projects.md` §6

---

## Phase 11 — iOS and iPadOS

**Goal:** The Apple targets, including the least Compose-shaped work in the project.

**Deliverables**
- `:app-ios` — `ComposeUIViewController` host.
- `CFBundleDocumentTypes`, `UTExportedTypeDeclarations` for Fountain (conforming to
  `public.plain-text`), `LSSupportsOpeningDocumentsInPlace`.
- Security-scoped bookmarks for documents and project directories.
- iPadOS multi-scene: `UISceneDelegate`, `UIApplicationSupportsMultipleScenes`, one
  `ComposeUIViewController` per scene. Scope this as UIKit work.
- iPhone single-scene.

**Acceptance**
- Opens in place from Files without copying into the container.
- Two documents side by side on iPad.
- Bookmarks resolve after reboot.
- **Milestone: accessibility and i18n audits** — VoiceOver, Dynamic Type, RTL.

**Spec refs:** `appthere-drafts.md` §7.2, §9.3

---

## Phase 12 — Android XR

**Goal:** Spatial presentation, feature-flagged.

Genuinely small, because subspace content is ignored on non-XR devices and in Home Space — the
code ships in the Android build and doesn't render elsewhere. On a Quest's 2D runtime the app is
already a large tablet app with no XR work at all.

**Deliverables**
- `:app-android-xr` — `Subspace`, one `SpatialPanel` per open document in a `SpatialCurvedRow`,
  `MovePolicy` / `ResizePolicy`, `Orbiter` for chrome.
- Capability detection and feature flag.
- XR-tuned layout: larger type, preview-biased, given that headset text entry is slow.

**Acceptance**
- No behaviour change on non-XR Android devices, verified.
- Flag off by default until `androidx.xr.compose` leaves alpha.

**Spec refs:** `appthere-drafts.md` §2

---

## Decision points

Unresolved questions that should be settled before the phase that depends on them.

| Question | Decide before | Source |
|---|---|---|
| Does the AppThere name reverse the standalone intent? | Phase 0 | `appthere-drafts.md` §13 |
| Implicit vs explicit project creation | Phase 8 | `projects.md` §13.1 |
| Nested projects: supported or nearest-ancestor-wins? | Phase 8 | `projects.md` §13.2 |
| Non-text files in the binder (PDFs, images) | Phase 8 | `projects.md` §13.3 |
| `{.class}` style map in `project.toml` or app prefs? | Phase 10 | `projects.md` §13.4 |

---

## Sequencing rationale

**Why the editor spike is second.** Cross-block selection is the one decision that can invalidate
everything downstream. Learning it fails in phase 2 costs weeks; learning it in phase 9 costs
months and a rewrite of the accessibility approach.

**Why Android precedes iOS.** SAF, intents, and task-per-document are the sharpest platform
edges, and SAF performance constrains the project model built in phase 8. Better to hit that
before the project model sets.

**Why Fountain comes after the desktop alpha.** It reuses the entire editing stack. Building it
earlier would mean building it twice.

**Why export is late.** It depends on the IR being stable and on projects for compile, and none of
it is needed to evaluate whether the editor feels right.

**Why phases 1–5 are the real milestone.** They constitute a usable single-window Markdown editor.
That is the point at which the design either works or doesn't, and everything after it is
elaboration. Reach it before building anything else.
