# Design

## Context

See proposal.md for why. The state this design starts from (mapped 2026-10-10):

- `project-model` has the walk, `project.toml`, ordering, prefix reorder, and an uncommitted
  `Reconciler` and `ProjectIndexStore`. **No module depends on it**; `app-shared` does not.
- `platform-files` can read and digest-check writes, rename without replacing (`FolderRenaming`),
  and list folders (`FolderListing`), but only through filesystem paths. There is **no SAF
  `FolderListing` or `FolderRenaming`**, and **no create, move or delete** for the writer's files.
  Konsist allows writes to disk only from `platform-files`.
- **There is no file watching, no tree UI, no drag and drop**, and no way to choose a folder: the
  desktop's `FileDialogs` and Android's contracts open files only.
- Desktop renders one `DocumentWindow` per `SessionRecord`. Android opens one task per document.
  Restoration goes through `SessionList`. There is no recent-items list; the session list does
  that job.
- App-private storage already has a pattern: `desktopDataRoot()` / `androidDataRoot()` with
  `sessions/` and `settings/` under them, and stores that take their root from the caller
  (`SnapshotStore`).
- Strings are Compose resources in `i18n`. IO dispatchers are injected per platform implementation.

## Goals / Non-Goals

**Goals:**
- One owner per open project, through which every reconcile and every binder operation passes, so
  the app's own changes never echo back as external ones.
- Moves that carry everything that belongs to a file, written once and used by rename, move, trash,
  restore and relink alike.
- The Android acceptance criterion: a 500-file SAF project fills in progressively without blocking.

**Non-Goals:**
- iOS. Apple builds are deferred to Phase 11; nothing here may make them harder, and the Apple side
  of new `expect`/`actual` declarations can be a stub.
- Writing sidecar metadata or snapshots (Phase 9). This change *carries* `.drafts/meta/<path>.toml`
  and `.drafts/snapshots/<path>/` wherever they exist, and checks for them on *Locate file…*, so the
  requirements that name them hold the day Phase 9 starts writing them.
- Compile targets (Phase 10). The *Screenplay + Treatment* template has `Treatment/` but no targets
  (D11).
- Changing a quality gate. `project-model` has no architecture rule; adding one is for the
  baseline's `quality-gate-review` change.

## Decisions

### D1. A `ProjectSession` per open project, process-wide

```
              +-----------------------------------------------+
 binder ops ->|  ProjectSession (one per project key/process) |<- focus, watch events
 own saves  ->|   mutex: one reconcile OR one operation       |
              |   holds: tree, ManifestState, index, pending  |
              +----+-------------+--------------+-------------+
                   |             |              |
              FolderListing  FolderEditing   ProjectStateStore (app-private)
              (walk)         (D5)            index.json, pending.json
```

Every reconcile and every operation runs under the session's mutex. An operation updates the index
for what it changed before releasing it, so a watch event that arrives during or after the operation
finds the index already agreeing with the disk. That is the echo problem solved by ordering, not by
filtering events. It also means a reconcile can never observe a `prefix` reorder half-done.

A registry keyed by project key gives each project one session per process. Desktop is single
instance, so that is one per device. Android's activities share a process.

*Alternative: suppress watch events for paths the app just wrote.* That is racy with event delivery
latency, and it does nothing for focus-triggered reconciles.

### D2. The `Reconciler` produces observations; the session turns them into pending state

The `Reconciler` stays pure: walk + previous index → `added`, `returned`, `missing`, `modified`,
candidate relinks, and the next index. It changes from the working tree in these ways:
- A path in `previous.missing` that is present again is *returned*, not *added*.
- An mtime of 0 means unknown, and the file is always hashed.
- A relink candidate needs a unique match in both directions, with non-empty, non-whitespace content.
  It also must not be in the declines.
- A folder relink is detected when every indexed file under a missing folder maps uniquely into one
  added folder.

The session merges observations into `PendingState { new: Set<path>, offers: List<Relink>,
folderOffers, declines: Set<(from, digest)> }`. Offers persist until answered. A decline lapses when
its missing entry leaves the index (30 days) or, for an ordered name, when it leaves the order.

### D3. Per-device state in app-private storage, keyed by location

The layout is `<dataRoot>/projects/<sha256(normalised root token)>/index.json` and `pending.json`.
They are written through a `writesAtomically` store, with the same `require` as `SnapshotStore`.
The root is supplied by the platform entry point.

`pending.json` is written before `index.json`. If the process dies between the two writes, the next
reconcile sees the new files as added again and marks them *New* again. That is idempotent. The
other order could index a file whose *New* mark was never recorded, and the mark would be lost.

The key comes from the location, not from an id in `project.toml`. An id would need a write to the
folder, which rules out projects with no sidecar. It would also be duplicated when someone copies a
project folder. Moving a folder costs one baseline.

### D4. The walk: progressive, and cached per folder on focus and change only

`ProjectWalk` becomes a flow of folder listings, so the binder renders each level as it arrives.
The per-folder listing cache (name, isFolder, mtime of each entry, plus the folder's own mtime) lives
in the same app-private project directory, not `.drafts/cache/`.

On open, every folder is listed. On focus and on watch, a folder is listed again only if its own
mtime changed or is unknown. A folder's mtime does not move when a file in it is edited, so a skipped
folder can hide an edit until the next open. The cost of that is a stale digest, which costs at most
a hash relink, and *Locate file…* covers it. Open documents keep their own §8.2 check.

Every folder's own time is asked for, one cheap single-row question each, through
`FolderListing.modifiedOf` (added during apply, 2026-10-10). Skipping a folder's *listing* must not
mean skipping its subfolders: their times are only known from the folder above. So the cache keeps
each folder's entries, and the walk asks every folder it knows of whether it changed.

The SAF `FolderListing` queries `buildChildDocumentsUriUsingTree` with exactly four columns
(document id, display name, MIME type, last modified). Null last-modified becomes 0, meaning
unknown.

### D5. `FolderRenaming` grows into `FolderEditing`

Rename, move, create file, create folder, and remove (for emptying the trash) all go through one
`platform-files` interface. There are implementations for paths (JVM, Android app-private) and for
SAF. Each operation **never replaces** and reports `FolderOutcome` (`Done(ref)` / `Taken` /
`Failed`), which replaces `RenameOutcome`. `FolderEditing` extends `FolderRenaming`, which stays as
the narrow interface `PrefixReorder` needs, since a reorder only renames.

`FolderListing.childOf` is removed (decided 2026-10-10, during apply). It built a child's reference
from its name, which Android's storage framework cannot do in general: a document's id is its
provider's to choose. A child is found by listing its folder, and what does not exist yet is made
through `FolderEditing`. `ProjectManifest` creates `.drafts/` and `project.toml` that way on its
first write, and treats a name that is already taken as a conflict.

*Alternative: a second interface beside `FolderRenaming`.* That would mean two parallel
abstractions over one capability, with every SAF quirk handled twice.

On SAF, a move uses `DocumentsContract.moveDocument` where the provider advertises
`FLAG_SUPPORTS_MOVE`. Otherwise it copies, verifies the copy's digest, and only then deletes the
source. A folder is copied recursively and verified per file. A rename on SAF can return a different
URI, and some providers alter the name. The returned name is checked, and a changed name is treated
as `Failed` and rolled back.

### D6. One `Carry` for everything that follows a file

Rename, move, trash, restore and relink all move the same set:
- the order entry
- `.drafts/meta/<path>.toml`
- `.drafts/snapshots/<path>/`
- pending marks
- open windows' document references

`Carry` plans the full list of steps, runs them in order, and undoes the steps already taken if one
fails. `project.toml` goes through `writeIfUnchanged`. A conflict there aborts before any file moves.
Snapshots are moved last, since they are the only part that cannot be recreated.

### D7. Trash layout and restore

The layout is `.drafts/trash/<original path>/<UTC timestamp>/`, holding:
- the item
- `.meta.toml` and `.snapshots/` when present
- `.entry.toml`, recording the folder, the name, and the neighbours it was between

The sidecar's three names begin with a full stop (changed during apply, 2026-10-10). The binder
never shows a dot-file, so nothing the writer can delete can share a name with them; plain
`snapshots/` would collide with a document of that name, and a snapshots folder, which is named
after its document, collides with the item itself on the way in unless it is renamed first.

Restore puts the item back between the same neighbours if they still exist. Otherwise it goes at the
end. Recording neighbours rather than an index survives reorders made while the item was in the
trash.

### D8. Watching

| Platform | Source | Notes |
|---|---|---|
| Desktop | `WatchService`, one registration per folder, 300 ms debounce | `OVERFLOW` → full reconcile; macOS polls (risk) |
| Android | `ContentObserver` on the tree's children URI, plus `onResume` | Providers notify inconsistently; resume is the guarantee |

A watch event only *schedules* a reconcile through the session. It never interprets the event
itself.

### D9. Windows

- **Desktop:** `DraftsApplication` renders a `ProjectWindow` per project record next to the existing
  `DocumentWindow`s. Session records gain a project kind with the binder state (expanded paths,
  selection, scroll). A document window opened from a project carries the project key, which gives
  it the breadcrumb and "Show project".
- **Android:** a `ProjectActivity` gets its own task, with a single-task-per-project launch. Opening
  "in its own window" launches the existing `DocumentActivity` adjacent.
- **Folder chooser:** on macOS, AWT `FileDialog` with `apple.awt.fileDialogForDirectories`; elsewhere,
  `JFileChooser` in directories-only mode. On Android, `OpenDocumentTree` with
  `takePersistableUriPermission`.

### D10. Android documents inside a project

A document opened from the binder has a tree-derived URI, and its access comes from the tree grant.
`resolveAndroidToken` currently checks for a persisted permission on the exact URI. It must also
accept a URI under a persisted tree. Otherwise restoring a project's document windows would fail.

### D11. Templates

Template folder names are resource strings, so a writer in Spanish gets `Manuscrito/`. Each template
writes `project.toml` with the top-level order and `orderMode = "manifest"`, plus the `.gitignore`.
*Screenplay + Treatment* omits its two compile targets until Phase 10. Writing them now would produce
a `project.toml` that this version reads as `NotWritable`.

### D12. The later-schema check

`ProjectManifest.decode` returns `NotWritable` when `schema > ProjectFile.SCHEMA`, even if the
strict decode succeeds. Every write path already refuses `NotWritable`, so the remaining work is the
message shown to the writer.

### D13. Specs and `specifications/`

The uncommitted §9 paragraph is removed from `projects.md`, which is frozen under baseline D4. Its
content is in `projects/reconciliation`. Code written in this change cites capability paths.

## Risks / Trade-offs

- **Racy modification times** (FAT and exFAT have 2 s granularity). An edit within the same tick as
  the recorded mtime goes unnoticed, which leaves a stale digest.
  → Accepted, and deferred. Open files are covered by §8.2, and relinks by *Locate file…*. If it ever
  matters, the fix is git's: record the reconcile time, and re-hash files whose mtime is within the
  granularity window of it.
- **SAF copy-then-delete moves** double the IO, and a provider can fail between copy and delete.
  → The copy is verified before the delete. A failed delete leaves two copies, which is reported.
  Work is never lost.
- **`WatchService` on macOS polls slowly.**
  → Focus reconciles cover it. A native watcher is follow-up work.
- **Cloud SAF providers may download a file to hash it.**
  → Hashing happens only on an unknown or changed mtime. The first open of a cloud project can be
  slow, and the progressive binder hides most of that.
- **iCloud eviction** (`.name.icloud`) will read as missing when iOS arrives.
  → Recorded for Phase 11. It does not affect this change.
- **Archive order.** Two of the deltas add to capabilities the baseline creates.
  → Archive `baseline-existing-specs` first.
- **Size.** This is the largest change in the plan.
  → The tasks are grouped so the model, platform and UI groups land and are reviewed separately.

## Migration Plan

Nothing has shipped. The uncommitted `.drafts/cache/index.json` store was never released, so no
index needs moving. Projects opened by an earlier build reconcile from a baseline.

## Open Questions

- Should *Locate file…* also work on a missing folder (same refusal rule as for files)? It can be
  added later without changing anything here.
- A native macOS watcher: which API, and is it worth the code? This can wait for real-world reports.
