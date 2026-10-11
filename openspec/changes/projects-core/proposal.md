# Proposal

## Why

Phase 8's committed work opens a folder as a project, reads and writes `project.toml`, and orders a
folder. That is a model with no way in: there is no binder, nothing notices a change made outside
the app, and an Android project of any size cannot be walked quickly enough to show. The baseline
change (D5) left this remainder to one change, `projects-core`, which this is.

Exploration on 2026-10-10 also found that the reconciliation drafted in the working tree reports
"new" and relink offers for exactly one reconcile, calls a file that comes back "new", never
re-reads a file whose provider reports no modification time, and keeps its index where a sync
client shares it between devices. Those decisions are settled here before the binder is built on
them.

## What Changes

- **Reconciliation** (`projects.md` §9): new, missing, returned and modified files, worked out from
  a per-device index of what the folder looked like. Run on project open, on window focus, and on
  filesystem change. Never writes to the user's files.
- **Two markers, settling the baseline's D9.** *Unsorted*: not placed in an order the writer made
  (§3, shared, cleared by placing it). *New*: not yet seen on this device (§9, cleared by opening
  or dismissing it). They answer different questions and are cleared by different actions.
- **Relinking.** A missing file whose content turns up uniquely under another name is offered back.
  Declining keeps both and is remembered. *Locate file…* relinks by hand, and refuses a target that
  has history of its own. A renamed folder is offered back as one relink that keeps its order.
- **Per-device state, app-private.** The reconciliation index and the pending marks move out of
  `.drafts/cache/` into the application's own storage, keyed by the project's location. A folder
  without a sidecar is reconciled too, still without a write to it.
- **Android folder walking** with a minimal-projection query, a per-directory cache, and a binder
  that fills progressively off the main thread.
- **Filesystem watching** on desktop; on Android, reconciling on resume and on provider change
  notifications where the provider sends them.
- **Binder UI**: the tree, reorder and reparent by drag and by keyboard, create, rename, move and
  delete.
- **Trash** (§7): delete moves to `.drafts/trash/`, restorable to its place; emptying is explicit.
- **Project templates** (§2), each with a `.gitignore`.
- **Project windows** (§12): binder plus editor, document windows associated with their project,
  the offer to open a project when a file inside one is opened, restoration, and recent projects.
- **Fixes the baseline recorded for this change:** a `project.toml` whose `schema` is later than
  this version's is read-only; a folder opened inside an existing project says so.
- The uncommitted `projects.md` §9 paragraph is withdrawn: `specifications/` is frozen (baseline
  D4) and the paragraph's storage decision is reversed. Its requirements are in this change's specs.

## Capabilities

### New Capabilities

- `projects/reconciliation`: what counts as new, missing, returned and modified; when it runs; the
  per-device index and pending marks; relink offers, declines, *Locate file…*, and folder relinks.
- `projects/binder`: the tree as the writer sees and changes it; the *Unsorted* and *New* markers;
  reorder, reparent, create, rename, move and delete, by pointer and by keyboard.
- `projects/trash`: deleting from the binder, restoring to place, and emptying.
- `projects/templates`: the scaffolds a new project can start from.
- `projects/project-windows`: project windows, their association with document windows, opening a
  project from a file inside it, restoration, and recent projects.

### Modified Capabilities

None in `openspec/specs/`, which is empty until the baseline archives. This change also adds
requirements to two capabilities that the baseline introduces: `projects/folder-as-project`
(progressive walking; a folder opened inside a project) and `projects/manifest` (a later `schema`
is read-only). It must therefore archive **after** `baseline-existing-specs`.

## Impact

- `project-model`: reconciliation reworked (returned files, unknown mtimes, unique relinks, pending
  state), relink and folder relink, trash, templates, the schema check, and the enclosing-project
  check. `ProjectIndexStore` moves to app-private storage and loses its sidecar gate.
- `platform-files`: a SAF `FolderListing`; watching per platform; create, move and delete where they
  are missing; a project data root next to `sessions/` and `settings/`.
- `app-shared`, `app-desktop`, `app-android`, `platform-windows`: the binder, project windows,
  open-folder entry points, session restoration and recent projects.
- `i18n`: every new string.
- `specifications/projects.md`: the uncommitted §9 paragraph is removed.
- `baseline-existing-specs`: its D9 is answered here. Recording the answer in that change is left to
  whoever writes `projects/ordering`, since its marker requirement should say *Unsorted*.
- Out of scope: iOS (Phase 11), per-document metadata, manual snapshots and compile targets
  (Phases 9–10). Where this change's requirements name a sidecar or snapshots, they constrain what
  those phases build.
