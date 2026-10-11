# Spec Delta

## Purpose

Source: projects.md §12; appthere-drafts.md §7. Projects extend the app's window and session model:
a project window holds the binder and an editor, documents opened from it stay associated with it,
and open projects come back the way they were left.

## ADDED Requirements

### Requirement: A folder can be opened as a project
The writer SHALL be able to choose a folder and open it as a project. Access to the folder SHALL be
kept so the project can be reopened later without choosing it again, on every platform that requires
such access to be granted.

#### Scenario: Android
- **WHEN** the writer opens a folder as a project on Android and later restarts the device
- **THEN** the project reopens from the recent projects list without the folder being chosen again

### Requirement: A project window holds the binder and an editor
A project window SHALL show the binder beside an editor pane. On Compact widths the binder SHALL be a
drawer and the editor the default destination. A document opened from the binder SHALL open in the
project window's editor pane unless the writer asks for its own window.

#### Scenario: Phone
- **WHEN** a project is open on a Compact-width window and the writer opens a document from the binder
- **THEN** the drawer closes and the document fills the window

### Requirement: An open project has one project window
Opening a project that already has a project window SHALL bring that window forward instead of
opening a second one.

#### Scenario: Opened twice
- **WHEN** the writer opens a project from recent projects while it is already open
- **THEN** its existing project window comes forward and no other opens

### Requirement: Document windows stay associated with their project
A document opened in its own window from a project SHALL show the project's name and the document's
place in it, and SHALL be able to bring the project window forward. It SHALL otherwise behave as an
independent document window.

#### Scenario: Character sheet beside the scene
- **WHEN** the writer opens `Characters/marla.md` in its own window
- **THEN** that window shows *The Salt Road › Characters* and can bring the project window forward

### Requirement: Opening a file inside a project offers the project
When a document is opened directly and the app can tell it is inside a project, the app SHALL offer
to open the project too, and SHALL NOT open it unasked. Where the platform does not let the app see
the document's enclosing folders, no offer SHALL be made.

#### Scenario: Desktop
- **WHEN** the writer opens `The Salt Road/Notes/premise.md` from the desktop's file manager
- **THEN** the document opens and the app offers to open *The Salt Road* as a project

### Requirement: Open projects are restored
When the app restores a session, it SHALL reopen the project windows that were open, each with its
binder's expanded folders, selection and scroll position.

#### Scenario: Relaunch
- **WHEN** the app is quit with a project open, two folders expanded and a scene selected, and relaunched
- **THEN** the project window reopens with the same two folders expanded and the same scene selected

### Requirement: Recent projects
The app SHALL list recently opened projects, newest first, and SHALL let the writer pin and unpin
them; pinned projects SHALL stay listed. A project whose folder can no longer be reached SHALL be
shown as unavailable, with a way to choose the folder again or remove it from the list.

#### Scenario: Folder moved away
- **WHEN** a recent project's folder has been moved and the writer chooses it from the list
- **THEN** it is shown as unavailable and the writer can choose its new location
