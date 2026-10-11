## Purpose

Source: appthere-drafts.md §8.4, §12 (status indicator), §10.2 (meaning not in colour alone). Every open document is in exactly one lifecycle state, and the writer can always see which, without being interrupted by it.

## ADDED Requirements

### Requirement: Exactly one of six states
A document session SHALL be in exactly one of: untitled, clean, dirty, conflicted, orphaned, or readOnly. Where several apply, the system SHALL choose the first of: untitled (no file yet), orphaned (file deleted or permission lost), conflicted (file changed on disk), readOnly, dirty, clean.

Rationale: the least recoverable situation comes first. A writer whose file has been deleted needs to know that before they need to know they have unsaved edits.

#### Scenario: Deleted file with unsaved edits
- **WHEN** a document has unsaved edits and its file has been deleted
- **THEN** its state is orphaned

#### Scenario: Read-only with edits
- **WHEN** a read-only document is edited
- **THEN** its state remains readOnly

### Requirement: The state is shown quietly
The system SHALL show the state in the window chrome as a dot, with a short label for every state except clean: *Not saved yet*, *Unsaved*, *Changed on disk*, *File missing*, *Read-only*. Showing a state SHALL NOT open a dialog; dialogs appear only on an attempted write.

Rationale: colour alone may not carry meaning (accessibility/vision-and-motor), so every state that needs attention has words.

#### Scenario: First keystroke
- **WHEN** the writer types into a clean document
- **THEN** the chrome shows *Unsaved* without any dialog

#### Scenario: Clean document
- **WHEN** a document matches its file
- **THEN** the chrome shows the dot with no label

### Requirement: The state is announced as one item
The state indicator SHALL expose a single accessible description naming the document state, such as "Document, Unsaved", rather than separate items for the dot and the label.

#### Scenario: Screen reader
- **WHEN** a screen reader reaches the state indicator of a dirty document
- **THEN** it reads one item naming the document's state as unsaved

### Requirement: Untitled is shown, not left blank
An untitled document SHALL show its state, so a writer whose words exist only in a snapshot can see they are not in a file anywhere yet.

#### Scenario: New document
- **WHEN** the writer starts a new untitled document
- **THEN** the chrome shows *Not saved yet*

### Requirement: Read-only and orphaned documents remain editable
A readOnly or orphaned document SHALL accept edits, and its edits SHALL be snapshotted as for any other document.

Rationale: the writer may be about to save these words somewhere new.

#### Scenario: Editing a read-only file
- **WHEN** the writer edits a document whose file cannot be written
- **THEN** the edits are accepted and snapshotted

### Requirement: Leaving orphaned
A successful reload of an orphaned document's file SHALL clear the orphaned state, leaving the document clean, or readOnly if the file cannot be written. A successful *Save As* SHALL make it an ordinary file-backed document at the new location.

#### Scenario: Sync client restores the folder
- **WHEN** an orphaned document's file reappears and the writer reloads
- **THEN** the document is clean without being closed and reopened
