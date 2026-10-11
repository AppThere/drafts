## Purpose

Source: appthere-drafts.md §8.1, §7.3; divergences.md "8.1", "7.3 — On Android a closing document outlives the Activity". Autosave: unsaved words are kept in app-private storage, never in the user's file, so that no crash, kill or power loss costs the writer their work.

## ADDED Requirements

### Requirement: Autosave never writes the user's file
The system SHALL write autosaved text only to a snapshot in app-private storage. Autosave SHALL NOT write, rename, truncate or otherwise touch the user's document file.

Rationale: the user's file is protected by the digest check (documents/save-safety), which refuses writes. A snapshot has nothing to refuse, so it can run as often as it likes without ever being able to destroy anything.

#### Scenario: Edits are autosaved
- **WHEN** the writer edits a file-backed document and a snapshot trigger fires
- **THEN** the edited text is written to the document's snapshot in app-private storage
- **AND** the bytes of the user's file are unchanged

### Requirement: Snapshot triggers
While a document has edits not yet captured, the system SHALL write a snapshot when any of these occurs: 3 seconds pass with no further edit; 30 seconds pass of continuous editing since the edits began; the window or application loses focus; the window is closing, before teardown.

Rationale: §8.1's fifth trigger, navigation away from the document, is not built because the app has no in-window navigation.

#### Scenario: Idle after an edit
- **WHEN** the writer types and then makes no edit for 3 seconds
- **THEN** a snapshot holding the typed text is written

#### Scenario: Continuous editing
- **WHEN** the writer edits without a 3-second pause for 30 seconds
- **THEN** a snapshot is written without waiting for a pause

#### Scenario: Focus lost
- **WHEN** the document has uncaptured edits and its window loses focus
- **THEN** a snapshot is written

#### Scenario: Window closing
- **WHEN** the document has uncaptured edits and its window is closed
- **THEN** a snapshot is written before the window's state is torn down

### Requirement: Closing snapshot on Android may finish after the window
On Android, the system SHALL start the closing snapshot when the document's Activity finishes, and SHALL let it complete after the Activity is gone rather than block the main thread.

Rationale: the Activity's own scope is cancelled at teardown. A process killed in the few milliseconds before the write lands loses only the closing snapshot; the idle and focus-lost snapshots still hold the work.

#### Scenario: Activity finishes with uncaptured edits
- **WHEN** the writer closes a document on Android with uncaptured edits
- **THEN** the closing snapshot is written after the Activity is destroyed
- **AND** the main thread is not blocked waiting for it

### Requirement: Nothing to capture, nothing written
The system SHALL NOT write a snapshot of a document that has no uncaptured edits. A document opened and not edited SHALL NOT be snapshotted. An event trigger that fires with no uncaptured edits SHALL record only the caret and scroll position.

#### Scenario: Opened and only read
- **WHEN** the writer opens a document, reads it without editing, and switches to another application
- **THEN** no snapshot text is written
- **AND** the document's session records the caret and scroll position

### Requirement: Snapshots are written atomically
The system SHALL write a snapshot by writing a temporary file in the same directory, flushing and syncing it, renaming it over the previous snapshot, and syncing the directory. A crash at any point SHALL leave either the previous snapshot or the new one intact.

#### Scenario: Killed mid-write
- **WHEN** the process is killed while a snapshot is being written
- **THEN** on the next launch the snapshot holds either the previous complete text or the new complete text, never a partial one

### Requirement: Snapshots only on storage that replaces atomically
The system SHALL refuse to keep snapshots on storage that cannot replace a file atomically. On Android, snapshots SHALL be kept in app-private storage and SHALL NOT be written through the Storage Access Framework.

#### Scenario: Android document from a content provider
- **WHEN** the writer edits a document opened through the Storage Access Framework
- **THEN** its snapshot is written to app-private storage, not through the content provider

### Requirement: The text before the record
Each snapshot SHALL be accompanied by a session record holding the caret (block index and offset), the scroll position, and the file's base digest. The snapshot text SHALL be written before the record, so a crash between the two leaves the right words with a possibly stale position.

#### Scenario: Crash between text and record
- **WHEN** the process dies after the snapshot text is written and before its record is
- **THEN** the next launch restores the new text
- **AND** the caret may be placed where the previous record left it

### Requirement: A failed snapshot is retried
When a snapshot write fails, the system SHALL keep the edits marked as uncaptured and SHALL retry. It SHALL NOT treat the edits as captured until a write has succeeded.

#### Scenario: App-private storage briefly full
- **WHEN** a snapshot write fails because storage is full
- **THEN** the edits remain uncaptured
- **AND** the snapshot is attempted again after a short delay
