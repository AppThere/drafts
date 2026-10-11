# Spec Delta

## Purpose

Source: projects.md §9, §7 (deletion outside the app); exploration of 2026-10-10. A project's folder
can be changed outside the app and nothing breaks: the app notices what changed, shows it in place,
and offers back what moved, without writing to the writer's files to do it.

## ADDED Requirements

### Requirement: Reconciliation never writes to the writer's files
Reconciling a project SHALL NOT create, change, rename or delete any file or folder in the project,
including `.drafts/`. What it learns SHALL be kept in the application's own storage. Only an action
the writer takes, such as accepting a relink, MAY write to the project.

Rationale: a folder that is only browsed is never written to (projects.md §13.1).

#### Scenario: Folder with no sidecar
- **WHEN** a folder with no `.drafts/` is opened as a project and reconciled
- **THEN** the folder's contents are byte-for-byte unchanged and no `.drafts/` exists

### Requirement: Reconciliation runs on open, on focus and on change
The system SHALL reconcile a project when it is opened, when one of its windows gains focus, and when
the platform reports a change in the project's folder. Reconciling SHALL NOT run on the main thread
and SHALL NOT block editing.

#### Scenario: File added while the app is in the background
- **WHEN** a file is added to an open project's folder while the app is not focused, and a project window then gains focus
- **THEN** the binder shows the file without the writer doing anything

### Requirement: The first reconcile is a baseline
The first time a project is reconciled on a device, nothing in it SHALL be marked *New*.

Rationale: with nothing to compare against, calling every file new is noise.

#### Scenario: First open
- **WHEN** a folder of 40 files is opened as a project for the first time on this device
- **THEN** no file is marked *New*

### Requirement: A file added outside the app is New until seen
A file that was not in the project when it was last reconciled on this device SHALL be marked *New*
in the binder. The mark SHALL remain until the writer opens the file in an editor or dismisses the
mark. Selecting the file SHALL NOT clear it. Reconciling again SHALL NOT clear it.

#### Scenario: Mark survives a focus change
- **WHEN** a file is added outside the app, the project is reconciled, and the window loses and regains focus
- **THEN** the file is still marked *New*

#### Scenario: Opening clears the mark
- **WHEN** the writer opens a file marked *New* in an editor
- **THEN** the file is no longer marked *New*

### Requirement: New marks can be dismissed one at a time or by folder
The writer SHALL be able to dismiss the *New* mark on one file, and to dismiss every *New* mark in a
folder and the folders inside it in one action.

Rationale: a `git pull` can bring in dozens of files at once.

#### Scenario: Mark a folder as seen
- **WHEN** a folder holds 12 files marked *New* and the writer marks the folder as seen
- **THEN** none of the 12 files is marked *New*

### Requirement: New marks belong to the device
*New* marks and the record that reconciliation compares against SHALL be kept per device, and SHALL
NOT be stored in the project's folder. Opening or dismissing a file on one device SHALL NOT change
its mark on another. If the project's folder moves, the next reconcile there SHALL be a baseline.

#### Scenario: Project synced between two devices
- **WHEN** a file arrives in a synced project, and the writer opens it on one device
- **THEN** the file is still marked *New* on the other device

### Requirement: A file deleted outside the app is shown missing
A file that the last reconcile on this device recorded and that is now absent SHALL be shown in its
place in the binder as missing. The record of it, including its content digest, SHALL be kept for 30
days from when it went missing, and then forgotten.

#### Scenario: Deleted in a file manager
- **WHEN** a file is deleted outside the app and the project is reconciled
- **THEN** the binder shows it as missing in the position it held

### Requirement: An ordered entry with no file is kept
A name in a folder's order in `project.toml` whose file or folder does not exist SHALL be shown as
missing in its place for as long as the order holds it, and SHALL be removed from the order only by
the writer's action.

Rationale: projects.md §9 step 5, "never silently drop it".

#### Scenario: Order names a file that is gone
- **WHEN** `project.toml` orders `gone.md` and no such file exists, 60 days after it went
- **THEN** the binder still shows `gone.md` as missing, in its ordered place

### Requirement: A file that comes back is not new
A file that reappears at a path recorded as missing SHALL lose its missing state and SHALL NOT be
marked *New*.

#### Scenario: Switching git branches
- **WHEN** a file is removed by `git checkout other`, the project is reconciled, and `git checkout -` restores it
- **THEN** after the next reconcile the file is shown normally, neither missing nor *New*

### Requirement: A file is re-read only when it may have changed
Reconciling SHALL read a file's content only when nothing is recorded for it, when its modification
time differs from the one recorded, or when the platform reports no modification time for it.
Reconciling a project in which nothing has changed SHALL read no file's content.

#### Scenario: Unchanged project
- **WHEN** a project is reconciled twice with no change between
- **THEN** the second reconcile reads no file's content

#### Scenario: Provider reports no modification time
- **WHEN** the storage provider reports no modification time for a file whose content then changes
- **THEN** the next reconcile records the file's new content digest

### Requirement: The app's own changes are not reported as external
A change the app makes to the project, by saving, creating, renaming, moving, reordering in `prefix`
mode, deleting, or restoring from trash, SHALL NOT be reported by a later reconcile as new, missing,
or modified, and SHALL NOT produce a relink offer.

#### Scenario: Prefix reorder
- **WHEN** the writer reorders a `prefix`-mode folder, which renames its files
- **THEN** no file in it is marked *New* or missing, and no relink is offered

### Requirement: A missing file found under another name is offered back
When a missing file's content digest matches exactly one *New* file, and that file matches no other
missing file, the system SHALL offer to relink them. No offer SHALL be made for empty or
whitespace-only content. An offer SHALL remain until the writer accepts or declines it.

Rationale: a wrong relink attaches one document's history to another, so only unambiguous matches are offered.

#### Scenario: Renamed in a file manager
- **WHEN** `the-dock.fountain` is renamed `02-dock.fountain` outside the app and the project is reconciled twice
- **THEN** an offer to relink `the-dock.fountain` to `02-dock.fountain` is shown after both reconciles

#### Scenario: Two missing files with the same content
- **WHEN** two empty documents go missing and an empty document appears
- **THEN** no relink is offered

### Requirement: Accepting a relink moves what belonged to the missing file
Accepting a relink SHALL give the new file the missing file's order entry, sidecar metadata,
snapshots, and pending marks, and SHALL clear the missing entry. If both are in one folder, the file
SHALL take the missing file's place in the order; otherwise it SHALL stay where it is and the
missing file's place SHALL be removed.

Rationale: an order belongs to one folder, so a file in another folder cannot keep a slot in it.

#### Scenario: Same folder
- **WHEN** a relink is accepted for a missing file ordered second in `/Screenplay` and a new file in `/Screenplay`
- **THEN** the new file is second in `/Screenplay`'s order and is not marked *Unsorted*

#### Scenario: Different folder
- **WHEN** a relink is accepted for a missing file in `/Act 1` and a new file in `/Act 2`
- **THEN** `/Act 1`'s order no longer holds the missing name and the file stays in `/Act 2`

### Requirement: A relink is all or nothing
A relink SHALL either complete entirely or change nothing. If `project.toml` has changed since it
was read, the relink SHALL NOT proceed; the offer SHALL remain and the writer SHALL be told in
plain language that the project changed and to try again.

#### Scenario: Sync changes the order mid-relink
- **WHEN** a sync client rewrites `project.toml` after it was read and the writer accepts a relink
- **THEN** no file, sidecar, snapshot or order entry has moved, and the offer is still shown

### Requirement: Declining a relink keeps both
Declining a relink SHALL leave the missing entry missing and the *New* file *New*. The same missing
file SHALL NOT be offered again for the same content, under any name, for as long as it remains
missing.

#### Scenario: Declined offer stays declined
- **WHEN** the writer declines a relink and the *New* file is then renamed again outside the app
- **THEN** no relink is offered for that missing file

### Requirement: The writer can locate a missing file by hand
A missing file SHALL offer *Locate file…*, which lets the writer pick a file in the project and
relinks it as an accepted offer would, whatever its content. Only files in the project SHALL be
offered for picking.

Rationale: a file renamed and edited outside the app has no matching digest.

#### Scenario: Renamed and edited
- **WHEN** a file is renamed and edited outside the app, and the writer chooses *Locate file…* on the missing entry and picks the renamed file
- **THEN** the renamed file takes over the missing file's order entry and the missing entry is gone

### Requirement: A located file must have no history of its own
*Locate file…* SHALL refuse a file that has sidecar metadata or snapshots of its own, and SHALL say
that the file already has its own notes and versions. Front matter SHALL NOT count as history.

Rationale: relinking moves history; it never merges or replaces it.

#### Scenario: Target with its own snapshots
- **WHEN** the writer locates a missing file to `harbour.fountain`, which has snapshots of its own
- **THEN** nothing is relinked and the writer is told `harbour.fountain` already has its own notes and versions

### Requirement: A renamed folder is offered back as one relink
When every recorded file under a missing folder matches, uniquely and by content, a file under one
*New* folder, the system SHALL offer a single relink of the folder instead of one per file.
Accepting it SHALL carry the folder's order, and the orders of folders inside it, to the new folder.

Rationale: per-file relinks would leave the folder's order behind and fall back to alphabetical.

#### Scenario: Folder renamed outside the app
- **WHEN** `/Act 1`, ordered by the writer, is renamed `/Act One` outside the app and the relink is accepted
- **THEN** `/Act One`'s files are in the order `/Act 1` had, and one offer was shown, not one per file

#### Scenario: One file in the folder was also edited
- **WHEN** a folder is renamed outside the app and one file in it is edited
- **THEN** no folder relink is offered, and each unedited file is offered back on its own
