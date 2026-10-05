# AppThere Drafts — Application Specification

A LyX-style What You See Is What You Mean editor for Markdown and Fountain documents, in a
distraction-free interface.

Companion documents: [`markdown-dialect.md`](markdown-dialect.md) (the flavour),
[`export-pipeline.md`](export-pipeline.md) (parsers, IR, backends),
[`fountain.md`](fountain.md), [`epub3.md`](epub3.md).

> **Naming note.** This app was previously scoped as standalone and deliberately unrelated to
> the AppThere suite — a simple, performance-focused editor rather than a suite member. The
> `AppThere Drafts` name reverses that. Worth confirming the intent: sharing branding tends to
> pull in shared expectations about feature parity, settings conventions, and release cadence.
> If the standalone character still matters, the name is the first thing that will erode it.

---

## 1. Principles

1. **The source file is the document.** Plain text in, plain text out. No proprietary container,
   no database, no lock-in. The app is a lens over a file the user owns.
2. **WYSIWYM, not WYSIWYG.** Show what the text *means* — heading hierarchy, dialogue vs action,
   emphasis — at an approximation of final formatting. Never promise print fidelity.
3. **The cursor's paragraph tells the truth.** Where you are editing, you see the markup. Where
   you are not, you see the result.
4. **Never lose work; never overwrite silently.** Two separate guarantees, satisfied by two
   separate mechanisms.
5. **Accessibility and internationalisation are load-bearing, not a later pass.** The typeface
   choice already commits to this; the architecture must match it.
6. **Distraction-free means the chrome gets out of the way, not that features are absent.**

---

## 2. Target matrix

| Target | Compose | Windowing | Status |
|---|---|---|---|
| Android phone | CMP | Task-per-document | Primary |
| Android tablet / foldable | CMP | Task-per-document + split-screen | Primary |
| ChromeOS (Android apps) | CMP | Free-form multi-window | Primary |
| iOS | CMP (`ComposeUIViewController`) | Single scene | Primary |
| iPadOS | CMP | Multi-scene (see §7) | Primary |
| macOS | CMP Desktop (JVM) | Native multi-window | Primary |
| Windows | CMP Desktop (JVM) | Native multi-window | Primary |
| Linux | CMP Desktop (JVM) | Native multi-window | Primary |
| Android XR | Jetpack Compose for XR | Spatial panels | Secondary |

### On the XR target

XR is **not a separate rendering target**. It is the Android target with an optional spatial
layer. `androidx.xr.compose` (1.0.0-alpha14 as of May 2026) provides `Subspace`, `SpatialPanel`,
`SpatialCurvedRow`, and `Orbiter`. Critically, **subspace content is ignored on non-XR devices
and in Home Space**, so the spatial code can live in the same Android build and simply not render
elsewhere.

Practical consequence: on a Quest running the 2D Android runtime, Drafts is a large-canvas tablet
app and needs no XR work at all. On Android XR in Full Space, each open document becomes a
`SpatialPanel` inside a `SpatialCurvedRow` — which is, conveniently, the same "documents as
windows" model used everywhere else.

Constraints to respect:
- `androidx.xr.compose` is **not** a Kotlin Multiplatform library. XR code lives in `androidMain`
  behind a capability check, or in a separate `:app-android-xr` module.
- The library is still alpha. Treat XR as a feature flag, not a launch requirement.
- Text entry in a headset is slow and error-prone. XR is a *reading and revising* posture more
  than a drafting one — bias the XR layout toward preview, larger type, and voice/keyboard input.

---

## 3. Module architecture

```
:core-model              Document IR (from export-pipeline.md), block roles, attributes
:core-parse-markdown     intellij-markdown + the four dialect extensions
:core-parse-fountain     hand-written Fountain 1.1 parser
:core-serialise          IR → Markdown / Fountain, source-preserving
:core-export-*           XHTML/EPUB, ODF, OOXML backends (write-only), and the ZIP/XML
                         container writer they share

:editor-engine           Document session, block list, incremental reparse, undo, selection
:editor-ui               Compose editing surface, block composables, focus model
:design-system           Typography, colour, spacing, motion, theming
:i18n                    String resources, locale handling, script/font fallback
:a11y                    Semantics helpers, announcement policy, preference plumbing

:platform-files          Document access, URIs, bookmarks, permissions, app-private storage
:platform-windows        Multi-window and session restoration
:platform-intents        OS document-open handling, type identification, single instance

:app-shared              Navigation, settings, composition root
:app-android             Activity, manifest, intent filters
:app-android-xr          Spatial layer (optional)
:app-ios                 SwiftUI/UIKit scene host
:app-desktop             JVM main, file associations
```

Everything from `:core-*` through `:design-system` is pure `commonMain`. The `:platform-*`
modules are the only ones with platform source sets — file access, windowing, and OS document
handoff are the three genuinely divergent areas. They use `expect`/`actual` for thin primitives
(a digest, a clock, a storage root) and per-platform implementations of a common interface where
the platform code is more than adaptation, as the document stores are. A module whose logic is
common stays common: `:platform-windows` has no platform source set at all today.

---

## 4. The editing model

This is the heart of the app and the largest technical risk. Specify it carefully.

### 4.1 Focus-block reveal

The document renders as a vertical list of **blocks**. Each block is in one of two states:

| State | Rendering |
|---|---|
| **Preview** (cursor elsewhere) | Formatted. Markup characters hidden. Emphasis italicised, bold bolded, links styled, footnote refs as superscripts. |
| **Reveal** (cursor inside) | Raw source, markup characters visible, *not* visually formatted. |

**What reveal state preserves** (per the requirement — these never toggle):

- **Heading size.** An `## H2` in reveal shows `## Heading text` at H2 size. The line does not
  jump in size when you click into it.
- **Typeface.** Code blocks and all Fountain content stay monospace in both states. Body prose
  stays proportional in both states.
- **Block-level indentation and alignment.** A dialogue block stays indented; a transition stays
  right-aligned.

**What reveal state drops:**

- Inline emphasis rendering. `*word*` shows as literal `*word*` in upright type.
- Link rendering. `[text](url)` shows in full, unstyled.
- Inline code styling (the backticks appear; the span is not tinted or boxed).
- Footnote reference superscripting.
- Typographic substitution preview.

The rule is: **block identity is stable, inline decoration is what reveals.** That keeps the
line from reflowing vertically when focus enters it, which is what makes the effect feel calm
rather than twitchy.

### 4.2 Transition behaviour

Reveal/preview switches on **block change**, not on every cursor movement. Moving the caret
within a paragraph changes nothing.

Cross-fade inline decoration over 120ms with no layout animation. Because block metrics are
identical in both states (§4.1), nothing moves — only glyph styling changes. Respect
`prefers-reduced-motion`: at reduced motion the switch is instantaneous.

**Vertical stability is a hard requirement.** If revealing a block changes its height, the
document below it jumps while the user is typing. Since markup characters are *added* in reveal
state, a paragraph near a wrap boundary can gain a line. Mitigation: every block reserves its
reveal-state height — measure both states, use the larger, and let preview state have a little
slack at the bottom. *Every* block, not only the focused one: the growth is the transition, so
slack that arrived with focus would arrive too late. The cost is bounded by the viewport, since
only composed blocks are measured, and it buys a completely still page.

### 4.3 Implementation approach

Three options were considered:

| Approach | Verdict |
|---|---|
| One `BasicTextField` over the whole document with a `VisualTransformation` | **No.** Offset mapping between raw and transformed text becomes intractable with hidden markup, and layout cost is O(document) per keystroke. |
| `LazyColumn` of blocks; focused block is a `BasicTextField`, others are `Text` with `AnnotatedString` | **Yes.** Cost is O(viewport). Block boundaries align with incremental reparse boundaries. Each block is a natural accessibility node. |
| Fully custom text layout on `Canvas` | **No.** Maximum control, but you rewrite IME, selection, and accessibility from scratch. |

**Chosen: per-block fields in a `LazyColumn`.**

Consequences to design around:

- **Only one editable field exists at a time.** Blocks render as `Text` until focused.
- **Caret movement across boundaries needs explicit handling.** Up-arrow on the first line of a
  block, down-arrow on the last, Home/End, and backspace at offset 0 (which merges blocks) all
  require the engine to move focus and place the caret at the correct offset in the neighbour.
- **Typing can change block structure.** Typing `## ` at the start of a paragraph promotes it to
  a heading; pressing Enter splits; backspacing at offset 0 merges. The engine reparses the
  affected range and reconciles the block list, preserving focus and caret offset across the
  structural change.
- **Incremental reparse.** Reparse the dirty block plus enough context to catch constructs that
  span blocks (list continuation, fenced code, footnote definitions, Fountain's
  character-then-dialogue coupling). A conservative window of "the enclosing container block, or
  the paragraph before through the paragraph after" is correct and fast enough.

### 4.4 The hard problem: cross-block selection

Compose's `SelectionContainer` does not compose with editable fields, and per-block fields mean
no single text layout spans the selection. This is the single largest unknown in the project.

Staged approach:

1. **v1: within-block selection only**, plus whole-block selection via gutter click or
   `Shift+Up/Down` at block granularity. Copy of a multi-block selection yields the raw source
   of those blocks. This covers the large majority of real editing.
2. **v2: custom selection layer.** Maintain selection as `(blockId, offset)` anchor and focus in
   the engine, draw highlight rectangles per block from each block's `TextLayoutResult`, and
   implement copy/cut/delete against the IR rather than against any text field. Drag handles and
   platform selection menus hook into this layer.

**Pointer and touch want different things from the same drag.** A mouse dragged across the
document selects. A finger dragged across it scrolls — on a phone there is no other way through
a document — and a tap places the caret. A stylus is treated as touch. Selecting across blocks by
touch is the v2 drag handles, never a drag of the text itself.

Prototype the v2 layer **before** committing to the per-block architecture, on a 10,000-word
fixture, on the slowest target device. If it can't be made to feel right, the architecture
decision needs revisiting, and that is much cheaper to learn in month one than month nine.

### 4.5 Fountain specifics

Fountain's block semantics are positional (a Character line is defined partly by the line that
follows it), so the reveal/preview distinction differs:

| Element | Preview | Reveal |
|---|---|---|
| Scene heading | `INT. KITCHEN - DAY` in caps, bold, full width | same, plus a leading `.` if forced |
| Character | Indented 36.7%, caps | same, plus `@` if forced |
| Dialogue | Indented 16.7%, 25% right inset | same |
| Parenthetical | Indented 26.7% | same |
| Transition | Right-aligned, caps | same, plus `>` if forced |
| Emphasis | `*italic*` rendered | markers visible |
| Notes `[[ ]]`, Boneyard `/* */` | Dimmed, collapsible | Full source |
| Sections `#`, Synopses `=` | Dimmed, outline-only styling | Full source |

Because Fountain block roles are driven by position and case, the indentation must not shift
while a character name is being typed. Debounce role reclassification: hold the previous role
until the user leaves the block or a blank line settles the ambiguity.

---

## 5. Typography

### 5.1 Typeface

**Atkinson Hyperlegible Next** (proportional) and **Atkinson Hyperlegible Mono** (monospace),
both from the Braille Institute, both **SIL Open Font License 1.1** — so bundling is
unambiguously fine, including commercially.

Both ship as **variable fonts** with a `wght` axis and a matching italic variable font.
Bundle the variable files rather than static instances: two files per family instead of fourteen,
and arbitrary weight interpolation for free.

```
fonts/
  AtkinsonHyperlegibleNext-VariableFont_wght.ttf
  AtkinsonHyperlegibleNext-Italic-VariableFont_wght.ttf
  AtkinsonHyperlegibleMono-VariableFont_wght.ttf
  AtkinsonHyperlegibleMono-Italic-VariableFont_wght.ttf
```

Load via Compose Multiplatform Resources; declare a `FontFamily` with variation settings per
weight. Ship the OFL text in an in-app licences screen.

**Script coverage is a real constraint.** Atkinson Hyperlegible Next covers 150+ languages — which
means Latin, Cyrillic, Greek and their extensions. It does **not** cover CJK, Arabic, Hebrew,
Devanagari, Thai, or the other major non-Latin scripts. See §10.3 for the fallback chain. Do not
ship believing the typeface is universal; a Japanese user would see tofu for every glyph.

### 5.2 Prose scale

Base size is user-adjustable (14–28sp, default 18sp) and respects the OS font-scale setting.
All values below are ratios of base, so the whole system scales coherently. Modular ratio **1.2**.

| Role | Size | Weight | Line height | Space before | Space after |
|---|---|---|---|---|---|
| H1 | 2.07× (37sp) | 700 | 1.2 | 1.6em | 0.5em |
| H2 | 1.72× (31sp) | 700 | 1.22 | 1.5em | 0.45em |
| H3 | 1.44× (26sp) | 700 | 1.25 | 1.4em | 0.4em |
| H4 | 1.22× (22sp) | 600 | 1.3 | 1.3em | 0.35em |
| H5 | 1.11× (20sp) | 600 | 1.35 | 1.2em | 0.3em |
| H6 | 1.00× (18sp) | 600, +2% tracking, caps | 1.4 | 1.2em | 0.3em |
| Body | 1.00× (18sp) | 400 | **1.6** | 0 | 0.75em |
| Blockquote | 1.00× | 400, italic | 1.6 | 0.75em | 0.75em |
| Code block | 0.94× mono | 400 | 1.45 | 0.75em | 0.75em |
| Caption / footnote | 0.85× | 400 | 1.5 | — | — |

Paragraphs are separated by space, not first-line indent — indentation reads as manuscript
convention and fights the WYSIWYM preview.

Headings use `keep-with-next` semantics: a heading never renders as the last visible line of a
scroll position without at least two lines of its body following.

### 5.3 Measure

The content column clamps to **68 characters** of body text, computed as `34 × bodySize`, with a
floor so that on narrow screens it simply fills available width minus gutters.

```
contentWidth = min(availableWidth - 2 × gutter, 34em)
gutter = max(16dp, 4% of availableWidth)
```

At the 18sp default this is roughly 612dp — comfortable on a tablet, full-bleed on a phone,
centred with generous margins on a desktop window.

### 5.4 Screenplay metrics

Fountain uses a monospace face and print-derived proportions, expressed as **percentages of the
content column** rather than absolute measurements — the same approach already settled for
Fountain → EPUB export, reused here so screen and export agree.

Standard US Letter screenplay geometry is a 6.0″ text area (1.5″ left margin, 1.0″ right).
Within that:

| Role | Left inset | Right inset | Alignment |
|---|---|---|---|
| Scene heading | 0% | 0% | Left, caps |
| Action | 0% | 0% | Left |
| Character | 36.7% | 0% | Left, caps |
| Parenthetical | 26.7% | 30% | Left |
| Dialogue | 16.7% | 25% | Left |
| Transition | — | 0% | Right, caps |
| Centered | — | — | Centre |

**Set the mono size so exactly 61 monospace characters fit the content column.** Screenplay
action at 12pt Courier and 10 characters per inch gives ~60 characters per line, so matching that
count means on-screen line breaks approximate what a printed page would do — without implementing
pagination, which is explicitly out of scope.

The content column additionally carries a **US Letter width ceiling**: however wide the window,
the screenplay body never exceeds the width a printed page would give it.

Dual dialogue is a two-column layout at the 16.7%/25% insets, splitting the available width. On a
compact window it stacks vertically with a connecting rule and a "simultaneous" marker.

### 5.5 Reader controls

Exposed in settings, persisted per document type:

- Base size (14–28sp)
- Line height multiplier (1.3–2.0) — dyslexia support
- Letter spacing (0 to +0.08em) — dyslexia support
- Measure (55–85 characters)
- Paragraph spacing (0.5–1.5em, default 0.75em — §5.2's body space-after). Every role's space
  before and after scales in proportion, so headings keep more air than the paragraphs around
  them. Never zero: §5.2 separates paragraphs by space alone.
- Theme: light, dark, sepia, high contrast, system
- Font weight for body (300–500) — low-vision support

§12's options — typewriter scrolling, focus mode, and whether the chrome auto-hides — sit with
these controls and are persisted with them, as is an explicit reduced-motion choice (§10.2).

---

## 6. Responsive design

Use Material 3 adaptive `WindowSizeClass`. Three canonical layouts plus the spatial case.

| Class | Width | Layout |
|---|---|---|
| **Compact** | < 600dp | Single document, full bleed. Chrome auto-hides on typing. Bottom sheet for actions, FAB-free. Outline and settings as modal sheets. |
| **Medium** | 600–839dp | Single document, centred column. Collapsible navigation rail. Outline as a dismissible side sheet. |
| **Expanded** | ≥ 840dp | Optional two-pane: document + outline, or two documents side by side. Persistent rail. |

Height classes matter too — a phone in landscape is Compact-height, where auto-hiding chrome
is worth more than anywhere else.

Foldables: use Jetpack WindowManager's `FoldingFeature` to avoid rendering text across a hinge.
On a book-posture fold, place the content column entirely on one side or split into two panes at
the hinge — never let the fold bisect the measure.

ChromeOS and desktop windows can be resized to any size; the layout responds continuously rather
than snapping, with the content column doing the accommodating.

---

## 7. Multi-window and session

### 7.1 Model

**One window = one document.** No tabs. This matches the "documents as things you place" mental
model that side-by-side use implies, and it maps cleanly onto every platform's native windowing.

### 7.2 Per platform

**Desktop (JVM).** Compose Desktop's `application { }` scope hosts multiple `Window` composables.
Maintain a `List<DocumentSession>` in the application state and emit one `Window` per entry, each
with its own `WindowState` (position, size, placement) persisted.

**Android.** Give the document Activity `android:launchMode="standard"`,
`android:documentLaunchMode="intoExisting"`, and `android:resizeableActivity="true"`. Launch each
document with `FLAG_ACTIVITY_NEW_DOCUMENT or FLAG_ACTIVITY_MULTIPLE_TASK`. Each document then
appears as its own entry in Recents and can be dragged into split-screen against another instance
of the app — which is what makes side-by-side work on tablets, foldables, and ChromeOS. Set a
distinct `taskAffinity` per document and supply `ActivityManager.TaskDescription` with the
document title so Recents is legible.

**iOS/iPadOS.** Multi-scene requires a `UISceneDelegate` and `UIApplicationSceneManifest` with
`UIApplicationSupportsMultipleScenes`. Compose Multiplatform hosts per-scene via a
`ComposeUIViewController` inside each scene's root view controller. This is genuine UIKit work
outside Compose and should be scoped as such; it is the least Compose-shaped part of the project.
iPhone remains single-scene.

**Android XR.** Each document is a `SpatialPanel` in a `SpatialCurvedRow`, with `MovePolicy` and
`ResizePolicy` so the user can arrange them. Same session list, different presentation.

### 7.3 Session restoration

Persist, per open document:

```json
{
  "documentId": "uuid",
  "uri": "content://…",
  "accessToken": "<persisted URI permission | security-scoped bookmark | path>",
  "displayName": "chapter-3.md",
  "kind": "markdown | fountain",
  "caret": { "blockIndex": 42, "offset": 17 },
  "scrollOffset": 8123,
  "window": { "x": 120, "y": 80, "width": 900, "height": 1100, "placement": "floating" },
  "baseDigest": "sha256:…",
  "snapshotPath": "…/sessions/uuid/snapshot.md"
}
```

`accessToken` is the crux and differs per platform:

- **Android:** `takePersistableUriPermission` on the content URI at open time. Without this, the
  URI is dead on next launch and restoration silently fails.
- **iOS:** security-scoped bookmark data, resolved with `startAccessingSecurityScopedResource`.
- **Desktop:** absolute path, with existence re-checked on restore.

On launch, restore every session. A document whose file has vanished opens from its snapshot,
marked *File missing* (§8.4), with a clear banner offering *Save As*; its next save chooses where.
Like a `readOnly` document it can still be edited — the reader may be about to save these words
somewhere new, and the snapshot keeps them meanwhile. A vanished file with no snapshot, and a file
that is there but cannot be read, are each said plainly, in words about the reader's situation
rather than the error.

### 7.4 New documents

**Launching with nothing to restore opens one untitled document**, ready to type into. Launching
with sessions to restore restores them (§7.3) and opens nothing else — a launch that always added
a blank window would leave one to close every time. **Launching while the application is already
running opens a new untitled document in a new window**: the reader asked for the application
again, and it is already showing everything else they had open.

**The kind is chosen, not asked for.** An untitled document starts as the kind the reader last
created — Markdown on first launch — and the chrome shows it: *Untitled · Markdown*. Until the
first save that label is a control; choosing Fountain re-interprets the same text as Fountain and
applies the Fountain reader settings (§5.5 persists them per kind). It fades with the rest of the
chrome (§12). Nothing stands between launching the app and writing.

**Every platform's launcher offers both kinds directly**, routed through the same path as opening
a file:

| Platform | Entry points |
|---|---|
| Android | Static app shortcuts: *New Markdown document*, *New Fountain screenplay* |
| Linux | `.desktop` `Actions=` entries, which appear in the launcher's context menu |
| macOS | Dock menu (`applicationDockMenu`), and *File > New* |
| Windows | Jump list tasks, and *File > New* |

**An untitled document is protected like any other.** It is snapshotted on the §8.1 triggers,
has a session record whose `uri` is null, and is restored on the next launch. Closing it does not
ask whether to save: the snapshot already holds the words, and §8.4 allows dialogs only on an
attempted write. An untitled document that is still empty when closed is discarded — there is
nothing in it to lose.

**The first save is *Save As*,** through the platform's own picker (`ACTION_CREATE_DOCUMENT` on
Android, followed by `takePersistableUriPermission` as in §7.3; the export picker on iOS; a save
dialog on desktop). The suggested name comes from the first heading, or from a Fountain title
page's `Title:`, and the extension from the kind (§9.1). From then on the document is an ordinary
file-backed session: it has a `baseDigest`, its kind is its extension, and the label is gone.

---

## 8. Document lifecycle and safety

Two distinct guarantees, two distinct mechanisms. Keeping them separate is what makes both
trustworthy.

### 8.1 Never lose work — the snapshot

**Autosave never touches the user's file.** It writes a snapshot to app-private storage.

Triggers:
- Application or window loses focus (backgrounded, blurred, scene deactivated)
- 30 seconds of continuous editing since last snapshot
- 3 seconds of idle after an edit
- Window close, before teardown
- Explicit or implicit navigation away from the document

Written atomically: write `snapshot.md.tmp`, flush and fsync, `rename` over `snapshot.md`. Rename
is atomic on every target filesystem; a crash mid-write leaves the previous snapshot intact.

Snapshot directory also holds `meta.json` (§7.3) so caret and scroll survive with the text.

### 8.2 Never overwrite silently — the digest check

At open, record `baseDigest` (SHA-256 of file contents), size, and mtime.

**Before any write to the user's file**, re-read the file and recompute the digest. If it differs
from `baseDigest`, **do not write**. Present:

> *This file has changed on disk since you opened it.*
> [ Save a copy… ] [ Reload and lose my changes ] [ Show differences ] [ Cancel ]

Explicit save uses the same atomic temp-and-rename, then updates `baseDigest` to the newly
written content.

**Except on Android's Storage Access Framework, where atomic replacement does not exist.**
A `content://` document has no rename-over — `DocumentsContract.renameDocument` fails when the name
is taken — so a save truncates the document and refills it in place. The digest check still
holds in full, being a comparison and a refusal. What survives a crash mid-save is the snapshot
(§8.1), which lives in app-private storage on a real filesystem and *is* atomic, and §8.3
restores from it. Snapshots are never routed through SAF.

This matters more than it sounds on mobile, where cloud-sync providers rewrite files under you
without warning, and on desktop where the same file may be open in another editor.

### 8.3 Recovery

On launch, for each restored session, compare snapshot digest against the file's current digest.
If they differ, the snapshot holds unsaved work. Open the document with the snapshot content and
an unobtrusive banner:

> *Unsaved changes from your last session have been restored.* [ Compare ] [ Discard ]

Never auto-discard a snapshot. Retain snapshots for 30 days after a successful save, then prune.

### 8.4 Conflict is a first-class state

A document session is in exactly one of: `untitled` (no file yet, §7.4), `clean`, `dirty`,
`conflicted`, `orphaned` (file deleted or permission lost), or `readOnly`. Surface the state in the
window chrome — quietly, as a dot or a short label, not a dialog. Dialogs only on attempted write.

`untitled` is shown rather than left blank. A reader whose words exist only in a snapshot should
be able to see that they are not in a file anywhere yet.

---

## 9. OS integration

### 9.1 Type identifiers

| | Markdown | Fountain |
|---|---|---|
| Extensions | `.md`, `.markdown`, `.mdown`, `.mkd` | `.fountain`, `.spmd` |
| MIME | `text/markdown` (RFC 7763) | none registered |
| UTI | `net.daringfireball.markdown` | **must be declared** — export `io.fountain.fountain` |

Fountain has no registered MIME type and no public UTI, so you must declare an exported UTI on
Apple platforms and fall back to `text/plain` plus extension matching on Android.

### 9.2 Android

```xml
<activity android:name=".DocumentActivity"
          android:documentLaunchMode="intoExisting"
          android:resizeableActivity="true"
          android:exported="true">

  <intent-filter android:priority="1">
    <action android:name="android.intent.action.VIEW"/>
    <action android:name="android.intent.action.EDIT"/>
    <category android:name="android.intent.category.DEFAULT"/>
    <category android:name="android.intent.category.BROWSABLE"/>
    <data android:scheme="content"/>
    <data android:scheme="file"/>
    <data android:mimeType="text/markdown"/>
    <data android:mimeType="text/x-markdown"/>
  </intent-filter>

  <!-- Extension matching for types with no registered MIME -->
  <intent-filter android:priority="1">
    <action android:name="android.intent.action.VIEW"/>
    <category android:name="android.intent.category.DEFAULT"/>
    <data android:scheme="content" android:host="*"
          android:mimeType="*/*"
          android:pathPattern=".*\\.fountain"/>
    <!-- repeat for .md, .markdown, .spmd -->
  </intent-filter>

  <intent-filter>
    <action android:name="android.intent.action.SEND"/>
    <category android:name="android.intent.category.DEFAULT"/>
    <data android:mimeType="text/plain"/>
  </intent-filter>
</activity>
```

Three traps worth stating plainly:

- **Downloads and messaging apps frequently hand over `application/octet-stream`** regardless of
  the real type. Include a permissive filter matched on extension, and sniff content on open.
- **`pathPattern` does not support alternation** and matches greedily from the start. You need one
  `<data>` element per extension, and `.*\\.md` will also match `notes.mdx` — validate after
  receiving.
- **Call `takePersistableUriPermission` immediately** on receiving a content URI, or session
  restoration will fail silently on next launch.

### 9.3 Apple

`Info.plist` needs `CFBundleDocumentTypes` for both kinds, `UTExportedTypeDeclarations` for
Fountain (conforming to `public.plain-text`), and `LSSupportsOpeningDocumentsInPlace` set true so
files open in place rather than being copied into the app container. Handle
`scene(_:openURLContexts:)` and create a bookmark at open time.

### 9.4 Desktop

- **macOS:** `CFBundleDocumentTypes` in the app bundle; handle the open-document Apple Event via
  `java.awt.Desktop.setOpenFileHandler`. Reachable from Compose Desktop, but needs care with the
  JVM packaging step. Fountain's UTI is exported (§9.1); Markdown's `net.daringfireball.markdown`
  is *imported* — it belongs to its author, and macOS may already know it.
- **Windows:** file-association registry entries written by the installer; the document path
  arrives as `argv[1]`. Fountain has no registered MIME type, so its associations name
  `text/plain`.
- **Linux:** a `.desktop` file with `MimeType=text/markdown;text/x-markdown;text/x-fountain;` and
  a shared-mime-info XML package declaring the Fountain type as `text/x-fountain` — unregistered,
  and claimed by nothing else. Markdown is the system's own type and is not redeclared. `%f` in
  `Exec` delivers the path.

In all three cases, an already-running instance should open the document in a **new window**,
not replace the current one. Route a second launch to it through a single-instance lock and a
local socket or named pipe rather than running a second process — **one instance per user**, at an
address only that user can reach, so a second person signed in to the same machine neither blocks
nor reaches the first person's application.

---

## 10. Accessibility

Target: **WCAG 2.2 AA**, with the editing surface treated as a custom widget requiring explicit
semantics rather than inherited ones.

### 10.1 Screen readers

The per-block architecture (§4.3) pays off here: every block is already a discrete node.

- Each block exposes a role via Compose `semantics`. Headings use `heading()`. Other blocks get
  a `contentDescription` prefix naming the type: "Scene heading", "Dialogue, Marla",
  "Block quote", "Code block, Kotlin".
- The focused block's text field exposes the **raw source**, because that is what the user is
  editing and what the caret indexes into. Never let the announced text and the editable text
  disagree — this is the most common accessibility failure in rich-text editors.
- Reveal/preview transitions are **not** announced. They are visual affordances, not content
  changes, and announcing them would make every caret movement chatty.
- Structural edits (block promoted to heading, blocks merged) get a `liveRegion` polite
  announcement: "Heading level 2."
- An **outline view** exposing the heading/scene hierarchy as a navigable list is
  disproportionately valuable for screen reader users, who cannot skim. Treat it as an
  accessibility feature, not a convenience feature.

### 10.2 Vision and motor

- **Respect OS font scaling.** Use `sp` for all text. The §5 scale is ratio-based precisely so
  that a 200% system scale produces a coherent document rather than a broken one. Test at 200%.
- The typeface itself is a low-vision design — lean into it. Offer the weight, line-height, and
  letter-spacing controls from §5.5 prominently, not buried.
- Contrast: 4.5:1 minimum for body text, 3:1 for large text and UI affordances, in every theme.
  The high-contrast theme targets 7:1.
- **Complete keyboard operation.** Every action reachable without pointer. No focus traps. Visible
  focus indicators meeting WCAG 2.2's focus-appearance criterion. Document the full shortcut map
  and make it user-remappable.
- Touch targets ≥ 48dp. Dragging (selection handles, spatial panel movement) always has a
  non-drag alternative.
- Honour `prefers-reduced-motion`: no cross-fades, no scroll animation, instant state changes.
- Never encode meaning in colour alone — the conflict/dirty indicators need shape or text too.

### 10.3 Font fallback

A single hard-coded family will fail for most of the world's writers. Define a fallback chain:

```kotlin
FontFamily(
    atkinsonNextVariable,      // Latin, Cyrillic, Greek
    systemFallbackForScript,   // platform default: Noto CJK, etc.
)
```

Compose's `FontFamily` accepts an ordered list and falls through per-glyph. Verify behaviour on
each platform — fallback quality varies, and Compose Desktop in particular needs explicit
attention. Offer a setting to override the family entirely, for users who need a specific
dyslexia typeface or a particular CJK face.

---

## 11. Internationalisation

### 11.1 Strings and resources

Compose Multiplatform Resources (`org.jetbrains.compose.resources`) with `stringResource` and
per-locale directories. No string concatenation; use positional parameters. Pluralisation via
plural resources, not `if (n == 1)`.

Extract every user-facing string from day one. Retrofitting i18n is the single most expensive
thing to defer in an app like this.

### 11.2 Bidirectional text

- Use logical `start`/`end` for all padding and alignment, never `left`/`right`.
- `LocalLayoutDirection` drives the chrome.
- **The document's direction is independent of the UI's direction.** An Arabic speaker may write
  English Markdown, or the reverse. Detect per-paragraph base direction from first strong
  character, with a per-document override.
- **Markdown syntax is a bidi hazard.** `[نص](url)` mixes an RTL run with LTR punctuation and the
  Unicode Bidirectional Algorithm will reorder the brackets visually. In reveal state this looks
  broken and is genuinely hard to edit. Isolate markup runs with `U+2066 LRI` / `U+2069 PDI` when
  rendering reveal state so the delimiters stay put. This is subtle and worth a dedicated test
  fixture set.
- Screenplay indentation mirrors under RTL.

### 11.3 Fountain is English-shaped

Fountain 1.1 detects scene headings by the prefixes `INT`, `EXT`, `EST`, `I/E`, and transitions by
a trailing `TO:`. These are English. A French screenwriter writing `INT. CUISINE - JOUR` works by
accident; one writing `INTÉRIEUR` does not.

Provide a **per-document configurable prefix list** and transition suffix, defaulting to the
Fountain 1.1 set, with presets for common languages. Files remain valid Fountain — the forcing
characters (`.`, `>`, `@`) already provide an escape hatch, and the app should offer to insert
them automatically when it detects an unrecognised heading pattern.

The list is kept by the application, under the document's file, and not in the file: the file
stays exactly the writer's, and other Fountain tools read it correctly once its headings are
forced. The cost is that the choice stays on one machine, and a file moved or renamed outside the
application leaves it behind. An untitled screenplay keeps its list under its own id until *Save As*
carries it to the file. (Decided 2026-10-05.)

### 11.4 Input methods

IME composition (CJK, Vietnamese, Indic) interacts with per-block fields. Composition is confined
to a single block, which is the easy case — but block-structure changes must never fire mid-composition.
Suspend structural reparse while a composition is active and reconcile on commit. Test with
Japanese, Korean, and Vietnamese IMEs on every platform; this is where subtle input bugs hide.

### 11.5 Locale-sensitive content

Title-page dates, word counts, and number formatting follow the document locale, not the UI
locale. Word counting differs fundamentally by script — whitespace segmentation is wrong for
Chinese, Japanese, and Thai. Use ICU-style grapheme and word segmentation, not `split(" ")`.

---

## 12. Distraction-free interface

- **Chrome auto-hides.** On sustained typing — about a second and a half without interruption —
  toolbars and rails fade out. Any pointer movement, keypress of a modifier, or edge gesture brings
  them back. Pausing does not: a writer who stops to think has not asked for the furniture back.
- **Typewriter scrolling** as an option: keep the caret at a fixed vertical position.
- **Focus mode** as an option: dim all blocks except the current one, or the current sentence.
- **Nothing blinks, badges, or notifies.** No unsaved-changes asterisk animation, no word-count
  ticker that updates per keystroke (debounce to 1s).
- **Word and page targets** shown only on request, never ambiently.
- **Full-screen** is a first-class mode on every platform that has one.
- The only chrome is the status indicator (§8.4) — a dot, with a short label whenever the
  document is anything but clean, since colour alone may not carry meaning (§10.2) — and one
  control that opens the reader settings, which a reader without a keyboard could not otherwise
  reach. Both fade with the rest of the chrome.

---

## 13. Risks and open questions

| Risk | Severity | Mitigation |
|---|---|---|
| Cross-block selection (§4.4) | **High** | Prototype the v2 selection layer before committing to the architecture |
| Compose Multiplatform iOS multi-scene | Medium | Scope as UIKit work; iPhone ships single-scene regardless |
| `androidx.xr.compose` is alpha | Low | Feature-flagged; XR is not a launch requirement |
| Font fallback quality varies by platform | Medium | Test non-Latin rendering on all targets early; allow family override |
| Bidi in reveal state (§11.2) | Medium | Dedicated fixtures; isolate markup runs |
| Content URI permission loss on Android | Medium | Persist permissions at open; degrade to snapshot-backed read-only |
| Vertical stability on reveal (§4.2) | Medium | Dual measurement; verify on long paragraphs at large font scales |

**Open questions for you:**

1. Does the standalone-vs-suite reversal in the name reflect a real change of intent?
2. Is EPUB export still in scope for v1, or does it follow ODT/DOCX? It's the most involved of
   the three backends and the one with a hard validator.
3. ~~Should Drafts open a *folder* as a project?~~ **Answered** — see
   [`projects.md`](projects.md). Scrivener-style projects over plain folder trees, with
   user-defined structure and mixed Markdown/Fountain documents.

---

## 14. Build order

1. **Core IR, Markdown parse and serialise, round-trip tests.** No UI. `commonMain` only.
2. **Design system:** typography scale, fonts, themes, at every window size class.
3. **Editing surface, single window, desktop only.** Per-block fields, reveal/preview,
   within-block selection. The riskiest work, on the easiest platform to debug.
4. **Selection layer prototype.** Gate the architecture on this.
5. **Document lifecycle:** snapshot, digest check, recovery, session persistence.
6. **Multi-window desktop.**
7. **Android:** intents, permissions, task-per-document, responsive layouts.
8. **Fountain parser and screenplay rendering.** Reuses everything above.
9. **Accessibility pass with real assistive technology** — TalkBack, VoiceOver, NVDA, Orca.
   Not a checklist review; actual use by someone who uses these daily.
10. **iOS**, then iPadOS multi-scene.
11. **Export backends**, in the order given in `export-pipeline.md`.
12. **Android XR**, feature-flagged.

Steps 1–5 constitute a usable single-window Markdown editor. That's the point at which the
design either feels right or doesn't, and it's worth reaching before building anything else.
