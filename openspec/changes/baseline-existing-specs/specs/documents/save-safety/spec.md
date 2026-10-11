## Purpose

Source: appthere-drafts.md §8.2, §7.4 (Save As); divergences.md "8.2". An explicit save never silently overwrites a file that changed on disk, and a write to the user's file never leaves it half-written.

## ADDED Requirements

### Requirement: The file's state is recorded at open
When a file-backed document is opened, the system SHALL record the SHA-256 digest, size and modification time of the file's contents as its base. When the text on screen is restored from a snapshot, the base SHALL still be the file's, not the snapshot's.

#### Scenario: Opened with restored unsaved work
- **WHEN** a document opens with unsaved work restored from its snapshot
- **THEN** its base digest is the digest of the file as it is on disk, so a later save is checked against the file

### Requirement: Re-read and compare before every save
Before writing to a document's file, the system SHALL re-read the file and recompute its digest. If the digest differs from the base, the system SHALL NOT write, and the session SHALL become conflicted.

Rationale: on mobile, cloud-sync providers rewrite files without warning; on desktop, the same file may be open in another editor.

#### Scenario: File changed by another program
- **WHEN** the file is modified outside the app and the writer then saves
- **THEN** nothing is written to the file
- **AND** the session is conflicted and the conflict dialog is shown

#### Scenario: File unchanged
- **WHEN** the file is unchanged since open or the last save and the writer saves
- **THEN** the text is written

### Requirement: The conflict dialog
When a save is refused because the file changed, the system SHALL say "This file has changed on disk since you opened it." and offer *Save a copy…*, *Reload and lose my changes*, and *Cancel*. Escape SHALL cancel.

Rationale: §8.2's fourth choice, *Show differences*, is not offered because there is no diff view, and a button that does nothing is worse than none.

#### Scenario: Cancel
- **WHEN** the writer chooses Cancel or presses Escape
- **THEN** nothing is written and the edits remain on screen and in the snapshot

#### Scenario: Save a copy
- **WHEN** the writer chooses *Save a copy…*
- **THEN** a save picker opens on a name that is not the conflicted file's own
- **AND** a successful save there settles the conflict

### Requirement: Reload discards the edits completely
*Reload and lose my changes* SHALL replace the text with the file's current contents, discard the undo history, and make the file's current state the new base. It SHALL clear the conflicted and orphaned states.

#### Scenario: Undo after reload
- **WHEN** the writer reloads and then presses Undo
- **THEN** the discarded edits do not return

### Requirement: Atomic replacement where the filesystem allows it
On a filesystem, the system SHALL save by writing a temporary file in the target's directory, flushing and syncing it, renaming it over the target atomically, and syncing the directory. A failed save SHALL leave the previous file intact and no temporary file behind.

#### Scenario: Crash during save
- **WHEN** the process is killed while saving
- **THEN** the file holds either its previous contents or the new contents in full

### Requirement: A save preserves the file's identity
Saving SHALL leave the file's permissions unchanged. Saving a document opened through a symbolic link SHALL write the link's target and leave the link in place.

Rationale: added by the 2026-10-07 audit; implied by §1 "the source file is the document" rather than stated in §8.2.

#### Scenario: Shared-readable file
- **WHEN** the writer saves a file whose permissions allow others to read it
- **THEN** after the save the file has the same permissions

#### Scenario: Symbolic link
- **WHEN** the writer saves a document opened through a symbolic link
- **THEN** the link still exists and points where it did, and the target holds the new text

### Requirement: Storage Access Framework saves are checked but not atomic
On Android's Storage Access Framework, the digest check SHALL apply in full. Because a content document cannot be replaced atomically, the system SHALL save by truncating and refilling it in place, and SHALL rely on the snapshot to survive a crash mid-save.

#### Scenario: Crash mid-save on Android
- **WHEN** the process dies while saving through the Storage Access Framework
- **THEN** the next launch restores the words from the snapshot (documents/recovery)

### Requirement: A successful save moves the base
After a successful save, the system SHALL make the written contents the new base and the session clean, so the next save is checked against what was just written.

#### Scenario: Two saves in a row
- **WHEN** the writer saves, edits again, and saves again with no outside change
- **THEN** both saves succeed

### Requirement: Save As replaces only what the writer confirmed
*Save As* SHALL write to the location chosen in the platform's own picker. It SHALL replace an existing file there only when the writer has confirmed replacing it in that picker.

Rationale: a newly chosen file was never opened, so there is no base digest to check; the picker's replace confirmation stands in for the check.

#### Scenario: Name already taken
- **WHEN** the writer chooses the name of an existing file in the Save As picker
- **THEN** the file is replaced only after the writer confirms the replacement

### Requirement: A failed save says so
When a save fails for a reason other than a conflict, the system SHALL tell the writer that the document could not be saved and that their words are safe. The edits SHALL remain unsaved and in the snapshot. A file found missing or inaccessible SHALL make the session orphaned.

#### Scenario: Disk full
- **WHEN** a save fails because the disk is full
- **THEN** the writer sees that the document could not be saved and their words are safe
- **AND** the session stays dirty, not orphaned
