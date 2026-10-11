# Spec Delta

## ADDED Requirements

### Requirement: A project is shown while it is still being walked
Opening a project SHALL NOT wait for the whole folder tree to be read. The binder SHALL show the top
level as soon as it is read and fill in deeper folders as they are read, without blocking editing or
input. Walking SHALL NOT run on the main thread.

Rationale: projects.md §9; a folder listing through Android's storage framework is slow at depth.

#### Scenario: 500 files over Android storage
- **WHEN** a 500-file project is opened through Android's storage framework
- **THEN** the binder shows the top level before the walk finishes and stays responsive throughout

### Requirement: Unchanged folders are not walked again on focus
When a project is opened, every folder SHALL be listed. A reconcile on focus or on a change
notification SHALL NOT list a folder again when the platform reports a modification time for it and
that time is unchanged since it was last listed on this device.

Rationale: a folder's time changes when its entries do, not when a file in it is edited, so a full
walk on open bounds what a skipped folder can hide to one session.

#### Scenario: Opening lists everything
- **WHEN** a project is opened and no folder's modification time has changed since it was last open
- **THEN** every folder is listed

#### Scenario: Focus with one folder changed
- **WHEN** a project window regains focus and only one folder has changed since the last walk
- **THEN** only that folder is listed again

### Requirement: A folder inside a project says so
When the writer opens a folder that is inside an existing project, the app SHALL say so and offer to
open the enclosing project instead. Where the platform does not let the app see the folder's
enclosing folders, no check SHALL be made.

Rationale: projects.md §13.2, the nearest `.drafts/` wins; an audit finding recorded for this change.

#### Scenario: Opening a subfolder on desktop
- **WHEN** the writer opens `The Salt Road/Notes` and `The Salt Road/.drafts/` exists
- **THEN** the app says *Notes* is part of *The Salt Road* and offers to open *The Salt Road*
