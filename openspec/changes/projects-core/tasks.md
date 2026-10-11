# Tasks

## 1. Spec housekeeping

- [x] 1.1 Remove the uncommitted reconciliation-index paragraph from `specifications/projects.md` §9 (design D13); verify `git diff specifications/projects.md` is empty
- [x] 1.2 Validate the change with `openspec validate projects-core --strict` and verify it reports valid

## 2. platform-files: folder editing

- [x] 2.1 Widen `FolderRenaming` into `FolderEditing` (rename, move, create file, create folder, remove), each never replacing, with outcomes `Done(ref)` / `Taken` / `Failed` (design D5); move `PrefixReorder` onto it, and verify the existing `PrefixReorder` tests pass unchanged
- [x] 2.2 Implement `FolderEditing` for paths (`jvmAndroidMain`), and verify with tests on a temp directory: every operation refuses a taken name and leaves both items untouched
- [x] 2.3 Implement `FolderEditing` over SAF: `moveDocument` where the provider advertises `FLAG_SUPPORTS_MOVE`, otherwise copy, verify the digest, then delete; reject a rename whose returned name differs. Verify with an `androidDeviceTest` covering move, the copy-verify fallback (using a provider without move support), and a refused rename
- [x] 2.4 Implement a SAF `FolderListing` using `buildChildDocumentsUriUsingTree` with four columns, where a null last-modified becomes 0 (design D4); verify with an `androidDeviceTest` that lists a tree with nested folders and that a missing mtime reads as 0
- [x] 2.5 Make `resolveAndroidToken` accept a document URI under a persisted tree grant (design D10); verify with `androidDeviceTest`s that a tree-derived document is covered by its tree's grant and not by another tree's (a persisted grant cannot be created without the system picker, so the rule is tested as the pure `isCoveredBy`)
- [x] 2.6 Add a project data root (`projects/`) beside `sessions/` and `settings/` on desktop and Android; verify `DesktopStorageTest` and `AndroidStorageTest` cover the new path
- [ ] 2.7 Add folder choosers: desktop directory mode (macOS `FileDialog` property, `JFileChooser` elsewhere), and Android `OpenDocumentTree` with a persistable permission; verify by opening a folder on each platform and confirming the returned token resolves after restart

## 3. platform-files: watching

- [x] 3.1 Desktop watcher: `WatchService` with one registration per folder, a 300 ms debounce, and `OVERFLOW` mapped to a full-reconcile signal (design D8); verify with a JVM test that a create, a delete and a rename in a nested folder each produce exactly one debounced signal
- [x] 3.2 Android watcher: a `ContentObserver` on the tree's children URI, emitting the same signal; verify with an `androidDeviceTest` against the test documents provider (decided 2026-10-10; the external-storage provider is checked in the manual pass)
- [ ] 3.3 Provide an Apple stub `actual` that emits nothing, and verify `./gradlew allTests` compiles every target

## 4. project-model: reconciliation and per-device state

- [x] 4.1 Rework `Reconciler` per design D2: returned versus added, mtime 0 always hashed, unique non-empty relink matches, declines excluded, folder relink detection. Extend `ReconciliationTest` with a scenario for each `projects/reconciliation` requirement that the reconciler owns, including branch switching, two empty files, and an edited file inside a renamed folder; verify they pass
- [x] 4.2 Add `PendingState` (new, offers, folder offers, declines) and the merge from observations, with expiry of declines tied to the missing entry or the order (design D2); verify with tests that the mark survives a second reconcile, that dismissing one or a folder clears the right marks, and that a declined offer is not re-offered after a further rename
- [x] 4.3 Replace `ProjectIndexStore` with a `ProjectStateStore` in the app-private project directory, keyed by `sha256` of the normalised root token, writing `pending.json` before `index.json` through a store that must write atomically (design D3); verify with tests that a project with no sidecar gets state without anything written to its folder, and that a crash between the two writes re-marks rather than loses *New*
- [x] 4.4 Make the walk progressive (a flow of folder listings) with the per-folder listing cache: everything is listed on open, and only changed or unknown-mtime folders on focus or watch (design D4); verify with tests counting listings: a full walk on open, and one listing when one folder changed

## 5. project-model: operations

- [x] 5.1 Add `ProjectSession` and its process-wide registry: one mutex for reconciles and operations, with each operation updating the index before it releases the mutex (design D1); verify with a test in which a reorder in `prefix` mode, followed by a reconcile, reports nothing new, missing or offered
- [x] 5.2 Implement `Carry` (order entry, `.drafts/meta/<path>.toml`, `.drafts/snapshots/<path>/`, pending marks, open window references) with all-or-nothing undo, snapshots moved last, and `project.toml` through `writeIfUnchanged` (design D6); verify with tests that inject a failure at each step and assert the project is unchanged
- [x] 5.3 Binder operations on the session: create (document and folder, placed where created), rename, move (to the end of the destination's order), reorder in both modes, remove from order, and lazy `.drafts/` creation on the first write. Verify with tests for each `projects/binder` scenario that sits below the UI, including *name already taken* and *first reorder creates `.drafts/project.toml`*
- [x] 5.4 Relink, *Locate file…* and folder relink: same-folder slot versus different-folder rule, refusal of a target with a sidecar or snapshots, and the folder's order tables carried; verify with tests for every relink scenario in `projects/reconciliation`
- [x] 5.5 Trash: delete into `.drafts/trash/<path>/<UTC timestamp>/` with `entry.toml` recording the neighbours, restore between the neighbours or at the end, restore that never replaces, and an explicit empty for one item or all (design D7); verify with tests for each `projects/trash` scenario
- [x] 5.6 Templates: the five scaffolds with localised folder names, top-level order, `orderMode = "manifest"` and a `.gitignore`, with refusal when the name is taken or the location is inside a project (design D11); verify with tests for each `projects/templates` scenario
- [x] 5.7 Read-only for a later schema: `NotWritable` when `schema > SCHEMA` (design D12); verify with a test that a `schema = 2` file holding only known keys refuses a reorder and is byte-identical afterwards
- [x] 5.8 Enclosing-project check on paths: walk up the parents for `.drafts/`, and on SAF report "cannot tell"; verify with a test that opening `Project/Notes` reports `Project`

## 6. App: project windows and binder

- [x] 6.1 Add `project-model` to `app-shared`'s dependencies; verify `./gradlew :tools-architecture-tests:test` and `./gradlew buildHealth` pass
- [x] 6.2 Add every new user-facing string to `i18n` (markers, operations, refusals, trash, templates and their folder names, the newer-schema message, the inside-a-project message), worded for a writer; verify there are no hard-coded strings with a grep of the new UI files for string literals in `Text(`
- [x] 6.3 Binder UI: a lazy tree with stable keys per path, kind and *Unsorted*/*New*/missing markers in words or symbols with accessible names, and items that fill in progressively; verify with Compose UI tests for the mixed-folder and both-marker scenarios
- [x] 6.4 Binder interactions: drag to reorder and reparent; *Move up*, *Move down* and *Move to…* from the keyboard and accessibility actions with announcements; create, rename, delete, *Remove from order*, *Locate file…*, dismiss and *Mark folder as seen*; relink and folder-relink offers. Verify with Compose UI tests for the keyboard reorder and the screen-reader scenario
- [ ] 6.5 Binder accessibility and layout checks: tree semantics (level, position, expanded), 200% font scale and RTL; verify with UI tests at 200% scale and in an RTL locale, and a manual TalkBack pass recorded in the PR
- [ ] 6.6 Desktop: a `ProjectWindow` (binder beside the editor pane), one window per project, *Open folder…* and *New project…* in the menus, document windows with project association (breadcrumb, *Show project*), and session records for projects with their binder state (design D9); verify by relaunching with a project open, two folders expanded and a scene selected, and confirming they are restored
- [ ] 6.7 Android: `ProjectActivity` with one task per project, the binder as a drawer on Compact widths, *Open in new window* launching `DocumentActivity` adjacent, and reconciling on resume; verify on a device or emulator against the phone scenario and the restart scenario of `projects/project-windows`
- [ ] 6.8 Recent projects: listed newest first, pinnable, with access tokens, and shown as unavailable with *Choose again* and *Remove* when they can't be reached; verify with a test of the list model and a manual check after moving a project folder
- [ ] 6.9 Opening a document inside a project offers the project (desktop, and on Android only when the document is under a granted tree); a folder opened inside a project offers the enclosing one; verify manually on desktop with `The Salt Road/Notes/premise.md`
- [ ] 6.10 Wire focus and the watchers into `ProjectSession` reconciles off the main thread; verify by adding a file in a file manager while the app is in the background and seeing it marked *New* on focus

## 7. Integration and quality

- [ ] 7.1 Phase 8 acceptance on Android: open a 500-file project over SAF and confirm the top level shows before the walk finishes and input is never blocked; record timings in `PERFORMANCE.md`
- [x] 7.2 Phase 8 acceptance on git: reorder in `prefix` mode in a git repository and confirm `git status` shows renames (`git diff -M --stat`)
- [x] 7.3 Run the `AGENTS.md` §2 quality pass (both phases), and confirm the detekt baseline is unchanged or smaller and no file crosses a fail threshold; report warn-threshold crossings and the Phase 2 walk

## Workflow follow-up

- Archive `baseline-existing-specs` before this change. Two deltas here add to capabilities it creates.
- When `projects/ordering` is written in the baseline, record D9's answer there: its marker is *Unsorted*.
- Propose adding `project-model` to the architecture rules through `quality-gate-review`.
