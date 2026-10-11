## Purpose

Source: appthere-drafts.md §8.3, §7.3 (vanished files); divergences.md "8.3". On launch, work that never reached a file is found and offered back. A snapshot is deleted only when the writer says so, or long after its words were safely saved.

## ADDED Requirements

### Requirement: Unsaved work is detected by digest
On launch, for each restored file-backed session, the system SHALL compare the snapshot's digest with the file's current digest. If they differ, the document SHALL open with the snapshot's text. If they match, or there is no snapshot, it SHALL open with the file's text.

Rationale: digests, not timestamps. A snapshot is almost always newer than the file even when it says the same thing.

#### Scenario: Unsaved work from last session
- **WHEN** the app was quit with edits that were snapshotted but not saved
- **THEN** on the next launch the document opens with the edited text and the restore banner

#### Scenario: Snapshot matches the file
- **WHEN** the document's snapshot holds exactly the file's contents
- **THEN** the document opens from the file with no banner

### Requirement: The restore banner
A document opened with restored work SHALL show an unobtrusive banner, "Unsaved changes from your last session have been restored.", offering *Keep* and *Discard*. *Keep* SHALL dismiss the banner. *Discard* SHALL reload the file and delete the snapshot.

Rationale: §8.3's *Compare* needs a diff view, which does not exist. *Keep* is there because a banner that cannot be dismissed stops being unobtrusive.

#### Scenario: Discard
- **WHEN** the writer chooses Discard
- **THEN** the file's text replaces the restored text
- **AND** the snapshot is deleted, so the next launch does not offer it again

#### Scenario: Keep
- **WHEN** the writer chooses Keep
- **THEN** the banner goes away and the restored text remains, unsaved

### Requirement: A snapshot is never discarded automatically
The system SHALL delete a snapshot only when the writer chooses *Discard*, when an untitled document is closed empty, or when pruning finds it more than 30 days past a successful save. A snapshot whose work never reached a file, or whose record cannot be read, SHALL NOT be pruned.

#### Scenario: Old work never saved
- **WHEN** a snapshot is 90 days old and its document was never saved after it
- **THEN** pruning keeps it

#### Scenario: Saved long ago
- **WHEN** a snapshot's document was successfully saved more than 30 days ago
- **THEN** pruning deletes the snapshot and its record

### Requirement: Pruning runs on every platform
The system SHALL prune expired snapshots at launch, before any session is restored, on every platform.

#### Scenario: Android launch
- **WHEN** the app launches on Android with a snapshot saved more than 30 days ago
- **THEN** that snapshot is pruned

### Requirement: Damaged records do not cost the words
When a session's record cannot be read, the system SHALL still restore the snapshot's text. When a recorded caret position lies outside the restored text, the system SHALL ignore it rather than fail to open.

#### Scenario: Corrupt record
- **WHEN** a snapshot's text is intact and its record is unreadable
- **THEN** the document opens with the snapshot's text and no restored caret

### Requirement: A vanished file reopens from its snapshot
When a restored session's file no longer exists and a snapshot is kept, the system SHALL open the snapshot's text, mark the session orphaned (*File missing*), allow editing, and show a banner offering *Save As*. The next save SHALL go through *Save As*.

#### Scenario: File deleted while the app was closed
- **WHEN** the app launches and a restored document's file has been deleted
- **THEN** the document opens from its snapshot, labelled *File missing*, with a banner offering *Save As*

### Requirement: Failures to reopen are said plainly
The system SHALL tell the writer, in words about their situation rather than the error, when a restored document's file is gone and no snapshot was kept, and separately when the file is there but cannot be read.

#### Scenario: Unreachable drive
- **WHEN** a restored document's file exists but cannot be read
- **THEN** the writer is told the document could not be read and may be somewhere the app cannot reach just now

#### Scenario: Gone with nothing kept
- **WHEN** a restored document's file is gone and it has no snapshot
- **THEN** the writer is told the file is no longer there and no copy of its words was kept
