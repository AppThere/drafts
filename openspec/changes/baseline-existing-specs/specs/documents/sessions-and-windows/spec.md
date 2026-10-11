## Purpose

Source: appthere-drafts.md §7.1–7.3, §9.4 (a file already open); divergences.md "7.2", "7.3 — documentId". One window per document, and every window the writer left open comes back where they left it.

## ADDED Requirements

### Requirement: One window per document
Each open document SHALL have its own window: a desktop window, or on Android its own task in Recents. The system SHALL NOT use tabs.

#### Scenario: Two documents side by side
- **WHEN** the writer opens two documents
- **THEN** each is in its own window and they can be placed side by side

### Requirement: A file already open is not opened twice
Opening a file that already has a window SHALL NOT open a second window for it. On Android, it SHALL bring the existing task forward.

Rationale: on Android this is why documents launch with `FLAG_ACTIVITY_NEW_DOCUMENT` alone. §7.2 also asks for `MULTIPLE_TASK`, which would give one document two tasks.

#### Scenario: Double-clicking an open file
- **WHEN** the writer opens, from the file manager, a file that already has a window
- **THEN** no second window opens for it

### Requirement: Every opened document has a session
The system SHALL keep a session for each open document from the moment it opens, whether or not it has been edited. The session SHALL hold its location and access, display name, kind, caret (block index and offset), scroll position, base digest, and, on desktop, window geometry.

#### Scenario: Opened, read, app quit
- **WHEN** the writer opens a document, reads without editing, and quits
- **THEN** the document's session is kept and it is restored on the next launch

### Requirement: Launch restores the windows left open
On launch, the system SHALL restore every session whose window was open when the app last ran. A window the writer closed SHALL NOT be restored; closing it SHALL keep its session marked closed rather than delete it.

#### Scenario: Restart with three documents open
- **WHEN** the app quits with three document windows open and is launched again
- **THEN** three windows open, one per document

#### Scenario: Closed document
- **WHEN** the writer closes a document window and later relaunches the app
- **THEN** that document is not reopened

### Requirement: Position survives a restart
A restored document SHALL open at its recorded caret and scroll position. On desktop, its window SHALL open at its recorded position, size and placement.

#### Scenario: Reading position
- **WHEN** the writer quits while reading chapter nine of a document
- **THEN** after relaunch the document opens scrolled to chapter nine

### Requirement: Access survives a restart
On Android, the system SHALL take a persistable permission for a document's content URI when the document is opened, so it can be reopened on the next launch. On desktop, the absolute path SHALL be re-checked on restore.

#### Scenario: Android restart
- **WHEN** a document opened from a content provider is restored after the app was force-stopped
- **THEN** the document opens without the writer choosing it again

### Requirement: A session keeps its identity through Save As
A session's identity SHALL be derived from its file's location for a file-backed document, and SHALL be a new random identity for an untitled document. That identity SHALL be kept through *Save As*, so the document's snapshot and session stay with it.

Rationale: a location-derived identity lets recovery find a file's snapshot with nothing to consult. A file moved or renamed outside the app loses its snapshot association; the snapshot stays but is not offered back.

#### Scenario: First save of an untitled document
- **WHEN** the writer saves an untitled document for the first time
- **THEN** its session and snapshot remain associated with it at the new location
