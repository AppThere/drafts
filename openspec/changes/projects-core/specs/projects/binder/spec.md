# Spec Delta

## Purpose

Source: projects.md §1, §3, §10 (Binder), §13.1, §13.3; baseline design D9. The binder is the
project's folder tree as the writer sees and changes it: what is in it, in what order, what has
changed, and the operations that rearrange it.

## ADDED Requirements

### Requirement: The binder shows the folder in the project's order
The binder SHALL show the project's folders and files as a tree, each folder's children in the order
the project resolves (projects.md §3), with dot-files and `.drafts/` left out. Each item SHALL show
its name and whether it is a folder, a Markdown document, a Fountain document, or another file, in
text or an icon with an accessible name, not by colour alone.

#### Scenario: Mixed folder
- **WHEN** a folder holds `notes.md`, `02-dock.fountain`, `map.png` and `.DS_Store`
- **THEN** the binder shows the first three, each with its kind, and not `.DS_Store`

### Requirement: Unsorted and New are separate markers
An item not placed in its folder's order SHALL be marked *Unsorted* until the writer places it. An
item marked *New* by reconciliation SHALL show that mark separately. An item MAY carry both. Each
mark SHALL have a visible word or symbol and an accessible name.

Rationale: settles baseline D9. *Unsorted* is shared through `project.toml`; *New* is per device.

#### Scenario: Pulled file already placed by a collaborator
- **WHEN** a `git pull` brings in a file and a `project.toml` that orders it
- **THEN** the file is marked *New* and not *Unsorted*

#### Scenario: Placing clears Unsorted only
- **WHEN** the writer drags an item marked both *Unsorted* and *New* into place
- **THEN** it is marked *New* and not *Unsorted*

### Requirement: Missing items are shown in place
An item reconciliation reports missing SHALL be shown in its place, visibly and accessibly marked
missing, and SHALL offer *Locate file…* and *Remove from order*. *Remove from order* SHALL be the
only way a missing ordered name leaves the order.

#### Scenario: Remove from order
- **WHEN** the writer chooses *Remove from order* on a missing item
- **THEN** the name is gone from its folder's order in `project.toml` and from the binder

### Requirement: Opening an item
Opening a Markdown or Fountain document from the binder SHALL open it in the editor. Opening any
other file SHALL hand it to the system's default application. The app SHALL NOT edit such files.

#### Scenario: Reference image
- **WHEN** the writer opens `map.png` from the binder
- **THEN** it opens in the system's default image viewer and no editor opens

### Requirement: Reordering within a folder
The writer SHALL be able to move an item to another position in its folder by dragging. In
`manifest` mode this SHALL rewrite the folder's order in `project.toml`; in `prefix` mode it SHALL
rename the folder's entries (projects.md §3). Either SHALL complete entirely or change nothing.

#### Scenario: Manifest mode
- **WHEN** the writer drags the third scene above the first in a `manifest`-mode folder
- **THEN** `project.toml` lists that scene first and no file is renamed

### Requirement: Every drag has a keyboard equivalent
Every move that dragging can make SHALL also be available without a pointer: *Move up*, *Move down*,
and *Move to…* (a folder chooser), reachable from the keyboard and from assistive technology, with
the result announced.

Rationale: drag-and-drop alone excludes keyboard and screen-reader users (appthere-drafts.md §10).

#### Scenario: Keyboard reorder
- **WHEN** a keyboard-only writer focuses an item and invokes *Move up*
- **THEN** the item moves up one place and the new position is announced

### Requirement: Moving to another folder
The writer SHALL be able to move an item into another folder of the project by dragging or by *Move
to…*. The item SHALL take its order entry, sidecar metadata, snapshots and pending marks with it,
and SHALL arrive at the end of the destination's order. A move SHALL NOT replace an existing item.

#### Scenario: Name already taken
- **WHEN** the writer moves `marla.md` into a folder that already has `marla.md`
- **THEN** nothing moves and the writer is told a file with that name is already there

### Requirement: Creating documents and folders
The writer SHALL be able to create a Markdown document, a Fountain document, or a folder in any
folder of the project. A new item SHALL be placed where the writer created it and SHALL NOT be
marked *New* or *Unsorted*. Creation SHALL NOT replace an existing item.

#### Scenario: New scene after the second
- **WHEN** the writer creates a Fountain document after the second scene of an ordered folder
- **THEN** the document exists on disk and is third in the folder's order

### Requirement: Renaming
The writer SHALL be able to rename an item. A rename SHALL NOT replace an existing item, SHALL keep
the item's place in the order, and SHALL take its sidecar metadata, snapshots and pending marks.
Windows showing a renamed document SHALL keep showing it, under its new name.

#### Scenario: Rename an open document
- **WHEN** the writer renames a document that is open with unsaved edits
- **THEN** the window shows the new name, the edits are kept, and the next save writes the renamed file

### Requirement: The first change that needs a sidecar creates it
In a folder with no `.drafts/`, browsing SHALL write nothing. The first action that has to record
something in the project, such as a reorder in `manifest` mode, SHALL create `.drafts/`.

Rationale: projects.md §13.1.

#### Scenario: First reorder
- **WHEN** the writer first reorders a folder that has no `.drafts/`
- **THEN** `.drafts/project.toml` exists and holds that folder's order

### Requirement: A failed binder operation is reported and changes nothing
If a create, rename, move, reorder or delete cannot be completed, the project SHALL be left as it was
before it began, and the writer SHALL be told in plain language what did not happen. Open documents
and their unsaved edits SHALL be unaffected.

#### Scenario: Storage refuses a rename
- **WHEN** the storage provider refuses one rename in a `prefix`-mode reorder of five files
- **THEN** all five files keep their names and the writer is told the reorder could not be made

### Requirement: The binder is navigable by assistive technology
The binder SHALL expose each item's name, kind, markers, nesting level, position among its siblings,
and, for folders, whether it is expanded. It SHALL be fully operable from the keyboard, SHALL hold up
at 200% font scale, and SHALL mirror its indentation in right-to-left layouts.

#### Scenario: Screen reader on a nested scene
- **WHEN** a screen reader reaches a *New* Fountain document, third of five in an expanded folder at the second level
- **THEN** it announces the name, Fountain, New, level 2, and 3 of 5
