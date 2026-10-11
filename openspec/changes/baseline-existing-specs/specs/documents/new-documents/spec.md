## Purpose

Source: appthere-drafts.md §7.4; divergences.md "7.4". Nothing stands between launching the app and writing, and a document with no file yet is protected like any other.

## ADDED Requirements

### Requirement: Launching with nothing to restore opens a document
When the app launches with no sessions to restore, it SHALL open one untitled document with the caret ready to type. When it launches with sessions to restore, it SHALL restore them and open nothing else.

#### Scenario: First launch
- **WHEN** the app is launched for the first time
- **THEN** one untitled document opens, ready to type into

#### Scenario: Sessions to restore
- **WHEN** the app is launched with two documents to restore
- **THEN** those two open and no untitled document is added

### Requirement: Launching while running opens a new document
Launching the app while it is already running, with no file to open, SHALL open a new untitled document in a new window.

#### Scenario: Second launch
- **WHEN** the app is running and the writer launches it again from the launcher
- **THEN** a new window opens with a new untitled document

### Requirement: The kind is chosen in the chrome
An untitled document SHALL start as the kind the writer last created, Markdown on first launch. Until its first save, the chrome SHALL show its kind as a control. Choosing the other kind SHALL reinterpret the same text as that kind, apply that kind's reader settings, and be remembered for the next new document.

#### Scenario: Switching to Fountain
- **WHEN** the writer chooses Fountain for an untitled Markdown document
- **THEN** the same text is read as a screenplay with the Fountain reader settings
- **AND** the next new document starts as Fountain

### Requirement: Launcher entry points
The system SHALL offer *New Markdown document* and *New Fountain screenplay* from the platform launcher where built: Android static app shortcuts, Linux `.desktop` actions, and the macOS Dock menu. Each SHALL open a window through the same route as any other launch.

Rationale: no Windows jump list (needs a native bridge) and no *File > New* menu bar, which §12 does not allow on Windows and Linux. The Dock menu has never been run.

#### Scenario: Android shortcut
- **WHEN** the writer chooses *New Fountain screenplay* from the app icon's shortcuts on Android
- **THEN** a new untitled Fountain document opens

### Requirement: New and Open from every window
Every document window SHALL offer *New document* and *Open…* from its single control, and through the shortcuts Ctrl+N and Ctrl+O, on desktop and Android. Each SHALL open a window of its own.

#### Scenario: Ctrl+N
- **WHEN** the writer presses Ctrl+N in a document window
- **THEN** a new untitled document opens in a new window

### Requirement: An untitled document is protected like any other
An untitled document SHALL be snapshotted on the same triggers as a file-backed one, SHALL have a session with no file location, and SHALL be restored on the next launch if its window was open. Closing it SHALL NOT ask whether to save.

#### Scenario: Quit with an untitled document open
- **WHEN** the app quits with an untitled document holding text
- **THEN** on the next launch the document is restored with that text, still untitled

#### Scenario: Closing an untitled document
- **WHEN** the writer closes an untitled document holding text
- **THEN** no save prompt appears and its snapshot is kept

### Requirement: A closed untitled document's words can be reopened
When an untitled document holding text is closed, the system SHALL keep its words and SHALL give the writer a way to reopen them later.

Rationale: closing does not ask to save because the snapshot already holds the words. That is only a protection if the writer can get them back.

#### Scenario: Reopening closed words
- **WHEN** the writer closes an untitled document holding text and later relaunches the app
- **THEN** the writer can reopen that document with its text

### Requirement: An empty untitled document is discarded on close
When an untitled document is closed with no text in it, the system SHALL delete its snapshot and session.

#### Scenario: Opened and closed without typing
- **WHEN** the writer opens a new document and closes it without typing
- **THEN** it is not restored on the next launch

### Requirement: The first save is Save As
The first save of an untitled document SHALL go through the platform's own save picker. The suggested name SHALL come from the first heading, or from a Fountain title page's `Title:`, with the extension of the document's kind. After saving, the document SHALL be an ordinary file-backed document whose kind is its extension.

#### Scenario: Saving a new screenplay
- **WHEN** the writer saves an untitled Fountain document whose title page has `Title: The Salt Road`
- **THEN** the picker suggests a name derived from "The Salt Road" ending in `.fountain`
- **AND** once saved, the chrome no longer offers a kind control
