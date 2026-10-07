# Projects

A Scrivener-style project workflow for AppThere Drafts: user-defined folder structures holding
notes, character sheets, drafts, and compilable output, mixing Markdown and Fountain documents.

Companion: [`appthere-drafts.md`](appthere-drafts.md) (the app),
[`export-pipeline.md`](export-pipeline.md) (compile targets lower onto this).

---

## 1. The central decision: the filesystem is the binder

Scrivener stores a project as a `.scriv` package containing `Files/Data/<uuid>/content.rtf` —
opaque directories of UUID-named RTF files, indexed by a central `.scrivx` XML manifest. Outside
Scrivener the folder is unusable.

**Drafts does not do this.** A project is an **ordinary folder of ordinary files with their real
names and extensions.** The folder tree *is* the binder. What you see in the app is what you see
in Finder, Files, or `ls`.

```
The Salt Road/
├── .drafts/                      ← sidecar: order, metadata, compile targets
├── Notes/
│   ├── premise.md
│   ├── timeline.md
│   └── research/
│       └── 1890s-shipping.md
├── Characters/
│   ├── marla.md
│   └── ezra.md
├── Screenplay/
│   ├── 01-cold-open.fountain
│   ├── 02-the-dock.fountain
│   └── 03-marla-arrives.fountain
└── Treatment/
    └── treatment.md
```

Two reasons this matters more than convenience:

**It follows from the app's first principle.** "The source file is the document" is incompatible
with a UUID-keyed opaque store. A project that can only be opened by Drafts is a project the user
doesn't really own.

**Scrivener's sync problems are a direct consequence of its package format.** A bundle with a
central index syncs badly — Dropbox and iCloud see one composite object mutating, conflicts
land inside the package, and recovery is manual. Plain files with one small sidecar produce
per-file conflicts that any sync client handles, and that a human can resolve by reading them.

The cost is that filesystems don't store order. §3 handles that.

---

## 2. No mandatory structure

Scrivener imposes `Draft` (compiles), `Research` (doesn't), and `Trash`. Drafts imposes nothing.

Any folder can hold anything. What gets compiled is determined by **explicit compile targets**
(§6), not by a privileged folder name. This is what "users define their own project folder
structures" requires — the moment one folder is magic, the structure stops being the user's.

**Templates instead of rules.** Ship optional scaffolds that create a suggested tree and a
starter `project.toml`:

| Template | Structure |
|---|---|
| Novel | `Manuscript/`, `Notes/`, `Characters/`, `Places/` |
| Screenplay | `Screenplay/`, `Notes/`, `Characters/`, `Beats/` |
| Screenplay + Treatment | adds `Treatment/`, two compile targets |
| Research paper | `Draft/`, `Sources/`, `Figures/` |
| Empty | just `.drafts/` |

A template is a starting point the user immediately deviates from. Nothing in the app should
notice or care when they do.

---

## 3. Ordering

The one thing the filesystem can't express and a writer can't live without. Chapter 12 does not
come after Chapter 1 alphabetically.

**Three-tier resolution:**

1. **Explicit order** from `.drafts/project.toml`, for entries listed there.
2. **Numeric filename prefix** (`01-`, `02-`) if present — parsed and used, and kept in sync when
   the user reorders in the binder.
3. **Alphabetical** for anything else, appended after ordered entries.

A file that appears in the folder but not in the manifest is **never hidden**. It sorts to the end
of its folder and shows a subtle "new" marker until the user places it. This is what makes
external additions — a `git pull`, a file dropped in from Finder, a Dropbox sync — safe.

**Filename prefixes are a user preference, not the default.** Offer them per-project:

| Mode | Behaviour |
|---|---|
| `manifest` (default) | Clean filenames; order lives in `.drafts/project.toml` |
| `prefix` | Reordering renames files `01-`, `02-`; order is visible outside the app |

`prefix` mode is right for users who share the folder with collaborators who don't use Drafts, or
who care about the order being legible in a file manager. It costs renaming churn on every
reorder, which matters under version control. Say so in the setting's description rather than
picking for them.

---

## 4. Sidecar layout

```
.drafts/
├── project.toml           order, settings, compile targets, collections
├── meta/                  per-document metadata, mirroring the tree
│   └── Screenplay/
│       └── 02-the-dock.fountain.toml
├── snapshots/             manual versions (§8)
│   └── Screenplay/
│       └── 02-the-dock.fountain/
│           └── 2026-09-14T1032.fountain
├── trash/                 deleted items, restorable (§7)
└── cache/                 derived data — index, word counts, thumbnails. Disposable.
```

`cache/` should be in `.gitignore`; everything else is worth committing.

### `project.toml`

```toml
[project]
name = "The Salt Road"
schema = 1
orderMode = "manifest"        # or "prefix"
defaultKind = "markdown"

[[project.order]]
folder = "/"
entries = ["Notes", "Characters", "Treatment", "Screenplay"]

[[project.order]]
folder = "/Screenplay"
entries = ["01-cold-open.fountain", "02-the-dock.fountain", "03-marla-arrives.fountain"]

[[project.order]]
folder = "/Characters"
entries = ["marla.md", "ezra.md"]

[[labels]]
id = "revise"
name = "Needs revision"
color = "#C1554D"

[[labels]]
id = "locked"
name = "Locked"
color = "#4D7EA8"

[[statuses]]
id = "todo"
name = "To do"

[[statuses]]
id = "draft"
name = "First draft"

[[statuses]]
id = "done"
name = "Done"

[targets]
project = 90000               # words
session = 1000

[collections.needs-work]
name  = "Needs work"
type  = "saved-search"
query = "status:todo OR label:revise"
```

One line of entries per folder keeps the file diffable and mergeable — important, because this is
the only file in the project that two devices can contend over. Keep it small and keep entry
ordering stable on write.

The order is one `[[project.order]]` table per folder rather than a table keyed by path, and each
label and status has one key per line. Both are what the TOML library this is read with can read
and write back faithfully (ktoml, chosen 2026-10-06); a path used as a key does not survive it.

**What the application writes.** The file is written from its model in one fixed layout, which is
what keeps it line-stable; comments in it are not kept. A file holding anything the application
does not model — a later schema, a compile target added by hand before compile exists — is read
and never written over, since writing the model back would drop the rest. A write is checked
against the file as it was read (the app spec's §8.2), so one changed meanwhile by a pull or a sync
is not overwritten.

### Per-document metadata

Two sources, with front matter winning:

**Markdown — front matter, namespaced.** Portable, survives the file being moved or emailed, and
matches what Hugo and Obsidian users already expect.

```yaml
---
title: The Dock at Midnight
drafts:
  synopsis: Marla finds the manifest. Ezra lies about the cargo.
  label: revise
  status: draft
  target: 2500
  include: true
---
```

**Fountain and opt-out Markdown — sidecar.** `.drafts/meta/<path>.toml`:

```toml
synopsis = "Marla finds the manifest. Ezra lies about the cargo."
label = "revise"
status = "draft"
target = 2500
include = true
```

Fountain has no front matter — it has a title page, and putting per-scene metadata there would
emit a title page into every compiled scene. Sidecar is correct for Fountain. Offer Markdown users
the choice; some will want their files free of Drafts-specific keys.

**Sidecars follow moves.** Renaming or moving a document in the binder moves its sidecar,
snapshots, and order entry atomically. A move performed outside the app orphans the sidecar;
reconciliation (§9) offers to relink by content hash.

---

## 5. Mixed Markdown and Fountain

Per your constraint: a project mixes both freely; **a single document is one or the other**,
determined by extension.

| Extension | Kind | Editor mode | Typeface |
|---|---|---|---|
| `.md`, `.markdown`, `.mdown`, `.mkd` | Markdown | Prose | Atkinson Hyperlegible Next |
| `.fountain`, `.spmd` | Fountain | Screenplay | Atkinson Hyperlegible Mono |

Consequences:

- **Mode is per document, not per project.** Opening `marla.md` and `02-the-dock.fountain` in
  side-by-side windows gives you prose editing in one and screenplay editing in the other,
  simultaneously. This is the case that makes the mixing worth supporting: character notes open
  next to the scene you're writing.
- **The binder shows kind** with a small glyph, so a Fountain file in a folder of notes is
  obvious at a glance.
- **Word count semantics differ.** Markdown counts words; Fountain counts words *and* estimated
  page count. Aggregate project counts must not naively sum across kinds — report them
  separately, or the number is meaningless.
- **Compile targets are homogeneous** (§6). A target declares a kind and only accepts matching
  documents. A screenplay project with a Markdown treatment has two targets, not one mixed one.

Converting a document between kinds is a deliberate, destructive action with a confirmation and a
snapshot taken first. Markdown → Fountain in particular loses structure that Fountain can't carry.

---

## 6. Compile

Scrivener's compile is its most valuable feature and the hardest to get right. Fortunately the
architecture from `export-pipeline.md` already does most of the work:

> **Compile = concatenate several documents' IR into one `Document`, then hand it to a backend.**

Everything downstream — XHTML/EPUB, ODT, DOCX — already exists and needs no compile-specific code.

### Target definition

```toml
[[compile]]
id = "screenplay-pdf"
name = "Screenplay"
kind = "fountain"
include = ["/Screenplay/**"]
exclude = ["**/*.draft.fountain"]
separator = "pagebreak"
backend = "docx"              # then Word/LibreOffice → PDF
titlePage = "from-project"

[[compile]]
id = "treatment-epub"
name = "Treatment"
kind = "markdown"
include = ["/Treatment/**", "/Notes/premise.md"]
order = "binder"
separator = "blank"
headingOffset = "by-depth"    # folder depth → heading level
titleFromFilename = true
backend = "epub"
```

### Rules

- **Include/exclude are globs over project-relative paths**, resolved in binder order. Explicit
  path lists are also permitted for hand-curated sets.
- **`include: false` in a document's metadata removes it** from every target — the per-document
  opt-out Scrivener users expect.
- **Heading offset by depth** is what makes folder structure meaningful without being mandatory:
  a document at depth 1 contributes `h1`, depth 2 contributes `h2`, and headings inside the
  document shift down accordingly. Disable it and documents compile at their literal levels.
- **Titles** come from front matter `title`, else the filename with prefix and extension stripped
  and separators humanised (`02-the-dock` → "The Dock"). `titleFromFilename = false` omits them.
- **Separators:** `blank`, `rule` (thematic break), `pagebreak`, or a literal string.
- **Front matter is stripped** on compile. Project-level metadata (title, author, language) comes
  from `project.toml` and feeds the EPUB package document, the ODT `meta.xml`, and the DOCX
  `docProps/core.xml`.
- **Fountain compile is concatenation plus a title page.** Scene files join with blank lines; the
  title page is generated once from project metadata, not taken from any constituent file. Scene
  numbering, if enabled, is assigned across the whole compiled script rather than per file.

### Compile is not export

A single document exports on its own without any project involvement. Compile is the project-level
operation. Same backends, different assembly step.

---

## 7. Trash and deletion

"Never lose work" extends to project operations.

Deleting from the binder moves the file to `.drafts/trash/<original-path>/<timestamp>/`, preserving
the sidecar, snapshots, and enough of `project.toml`'s entry to restore position. Trash is
browsable and restorable in-app. Emptying trash is explicit, confirmed, and never automatic.

Files deleted *outside* the app are simply gone — the app notices on reconciliation and offers to
remove the orphaned metadata, keeping the sidecar for 30 days in case the deletion was a sync
accident.

---

## 8. Snapshots and version control

Scrivener's per-document snapshots are genuinely good and worth having. But a project of plain
text files in a folder is already a git repository waiting to happen, and reimplementing version
control would be a poor use of the effort.

**Both, with different jobs:**

**Snapshots** — manual, in-app, per document. "Take snapshot" before a big revision, browse and
restore, diff against current. Stored as complete files under `.drafts/snapshots/`, named by
timestamp, with an optional label. Deliberately simple: no branching, no merge. Prune policy
configurable, default keep-all.

**Git — supported, not required.** Because the format is plain text with no binaries, `git init`
in a project folder just works. Ship a sensible `.gitignore` in every template:

```gitignore
.drafts/cache/
.drafts/trash/
```

Don't build git integration into v1. Do make sure nothing in the design makes git painful — which
mainly means keeping `project.toml` line-stable and never rewriting files the user didn't edit.

Note the interaction with `prefix` ordering mode: reordering renames files, which git records as
delete-plus-add unless rename detection fires. Worth a line in the setting's description.

---

## 9. External changes and reconciliation

A direct consequence of §1, and a genuine advantage over Scrivener: **the folder can be edited
from outside the app and nothing breaks.**

On project open, on window focus, and on filesystem watch events:

1. Walk the tree (see the Android caveat below).
2. **New files** → appear at the end of their folder, marked new, with default metadata.
3. **Missing files** → shown greyed in the binder with a "missing" state; metadata retained for
   30 days. If a new file appears with matching content hash, offer to relink.
4. **Modified files** → if open, the digest check from the app spec's §8.2 fires. If not open,
   nothing to do.
5. **Manifest references a path that no longer exists** → keep the entry, mark missing, never
   silently drop it.

Reconciliation never writes to the user's files. It only updates `.drafts/`.

**Android SAF is the performance problem here.** `DocumentFile.listFiles()` over a tree URI is
notoriously slow — hundreds of milliseconds for a modest folder, and it degrades badly with depth.
Mitigations:

- Query with `DocumentsContract.buildChildDocumentsUriUsingTree` and a projection of exactly the
  columns needed, rather than `DocumentFile`.
- Cache the tree index in `.drafts/cache/` with per-directory mtimes; re-walk only directories
  whose mtime changed.
- Walk off the main thread with progressive binder population — never block on a full traversal.
- Persist the tree URI permission with `takePersistableUriPermission` at pick time, exactly as for
  single documents.

iOS uses a security-scoped bookmark of the directory; desktop uses a path with a watcher
(`WatchService` on JVM, with the usual caveat that it's polling-based and slow on macOS —
consider a native watcher there).

---

## 10. Structure views

Three ways to see a project, beyond the editor itself.

**Binder** — the tree. Always available. Drag to reorder and reparent, which writes to
`project.toml` (or renames, in `prefix` mode). Shows kind, label colour, and status.

**Outliner** — a table: title, synopsis, label, status, word count, target progress, modified
date. Sortable, filterable, columns configurable. Editable in place for synopsis, label, status.

**Corkboard** — index cards showing title and synopsis, arranged in binder order, draggable to
reorder. The classic Scrivener view and the one writers reach for when restructuring.

**Accessibility note:** the corkboard is a 2D spatial grid, which is hostile to screen readers and
to keyboard-only users. The outliner is its accessible equivalent — same data, linear, fully
navigable. Treat that as the design rationale for building the outliner, not as a fallback: both
views must expose identical data and identical operations, and switching between them must
preserve selection.

---

## 11. Targets and progress

- **Project target** (words, or pages for screenplays) with a progress indicator.
- **Session target** — words written since the session began, reset daily or manually.
- **Per-document targets** from metadata, shown in the outliner.
- **Deadline** with a derived words-per-day figure.

Per the distraction-free principle, none of this is ambient. Progress appears in the project
views, not in the editor. No live ticker, no count that updates per keystroke. Debounce word
counting to 1 second and compute it off the main thread.

Screenplay page estimation uses the standard ~1 page per minute convention derived from line
counts, and is always labelled as an estimate — the app has no pagination, deliberately.

---

## 12. Windows and sessions

Projects extend the app spec's §7 model rather than replacing it.

- A **project window** hosts the binder plus an editor pane. On Compact widths the binder is a
  drawer and the editor is the default destination.
- **Document windows** opened from a project remain associated with it (breadcrumb in chrome,
  project-aware navigation), but are independent windows — this is what makes "character sheet
  beside the scene" work on a tablet or in split-screen.
- A document opened directly from the file system, inside a folder that happens to be a project,
  offers to open the project too. It does not do so automatically.
- Session restoration persists open projects alongside open documents, restoring binder scroll
  position, selection, and expanded folders.

Recent projects list, pinnable, with the same persisted access tokens described in the app spec's
§7.3.

---

## 13. Settled questions

These were open until 2026-09-28. Each answer is recorded with its reason, so a later change can
tell what it would be giving up.

1. **A folder becomes a project implicitly.** Any folder opens as a project, and `.drafts/` is
   created only when the reader does something that needs it — reordering, per-document metadata
   in the sidecar, a compile target. A folder that is only browsed is never written to. This keeps
   every existing folder of notes usable at once without the app scattering sidecars through
   folders nobody asked it to manage.
2. **Nesting is unsupported; the nearest `.drafts/` wins.** A file belongs to the project whose
   `.drafts/` is its nearest ancestor. The app never creates a `.drafts/` inside an existing
   project, and when it finds one someone made by hand it says so rather than silently choosing.
   Supporting nesting would mean deciding whose order, whose trash and whose compile targets
   apply at every boundary; nothing in the use cases needs it.
3. **Non-text files are shown and opened externally.** PDFs, images and other reference material
   appear in the binder, take part in ordering, and open in the system's default application.
   Drafts never edits them. This covers what Scrivener's Research folder is for without touching
   the plain-text principle, and a binder that hid them would make reordering around invisible
   files confusing.
4. **Export style mappings live in `project.toml`.** The `[export.styles]` table from
   `export-pipeline.md` travels with the project, so a collaborator or a second machine compiles
   it identically. App preferences may hold a default that new projects copy; once copied, the
   project's own table is the one that applies.

---

## 14. Build order

Slots after step 5 of the app spec's build order — after the document lifecycle is solid, before
multi-window.

1. **Project model and folder walk.** Open a folder, build a tree, no metadata, no order.
2. **`project.toml`, ordering, reconciliation.** External-change handling from day one; retrofitting
   it is painful.
3. **Binder UI**, drag-reorder, create/rename/move/delete with trash.
4. **Per-document metadata**, front matter and sidecar, with move-following.
5. **Outliner.** Before the corkboard — it's the accessible view and the one that validates the
   metadata model.
6. **Corkboard.**
7. **Compile targets**, lowering onto the existing export backends.
8. **Snapshots**, targets, collections.

Steps 1–3 give a usable project browser. That's enough to tell whether the
filesystem-as-binder decision holds up in practice, and it's reversible up to that point.
