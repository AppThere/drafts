# Spec Delta

## Purpose

Source: projects.md §7. "Never lose work" extends to project operations: deleting from the binder is
reversible, and only the writer, deliberately, makes it permanent.

## ADDED Requirements

### Requirement: Deleting from the binder moves to the project's trash
Deleting a file or folder from the binder SHALL move it into `.drafts/trash/<original path>/<time of
deletion>/`, together with its sidecar metadata, its snapshots, and its place in its folder's order.
Nothing SHALL be removed from storage by a delete.

#### Scenario: Delete a scene
- **WHEN** the writer deletes `/Screenplay/02-dock.fountain`
- **THEN** it is gone from `/Screenplay/` and the binder, and exists under `.drafts/trash/Screenplay/02-dock.fountain/`

### Requirement: Deleting a document with unsaved edits asks first
Deleting a document that is open with unsaved edits SHALL ask the writer before doing anything, and
SHALL do nothing if they cancel. Once deleted, the document's windows SHALL close.

#### Scenario: Cancel
- **WHEN** the writer deletes a document open with unsaved edits and cancels the question
- **THEN** the file, its window and its edits are as they were

### Requirement: The trash can be browsed and restored from
The writer SHALL be able to see what is in the project's trash, with each item's original location
and when it was deleted, and SHALL be able to restore any item. A restored item SHALL return to its
original folder, recreating it if it is gone, with its sidecar metadata, snapshots and order place.

#### Scenario: Restore to place
- **WHEN** the writer restores a scene that was third in its folder's order
- **THEN** it is back in that folder, third in the order, with its snapshots

### Requirement: Restoring never replaces
If an item now exists at a restored item's original path, restoring SHALL NOT replace it; the writer
SHALL be asked for another name, and nothing SHALL be restored until they give one.

#### Scenario: Name taken since deletion
- **WHEN** the writer restores `marla.md` and a new `marla.md` exists in its folder
- **THEN** the existing `marla.md` is unchanged and the writer is asked for a different name

### Requirement: Emptying the trash is explicit
Removing anything from the trash for good SHALL happen only when the writer asks, for one item or
for the whole trash, and SHALL be confirmed first. The trash SHALL never be emptied automatically.

#### Scenario: Old items stay
- **WHEN** an item has been in the trash for a year
- **THEN** it is still there and restorable

### Requirement: Deletion outside the app is not trashed
A file deleted outside the app SHALL NOT be put in the trash. The writer SHALL be offered the removal
of its sidecar metadata, which SHALL be removed only if they accept, and never within 30 days of
reconciliation first finding the file missing.

Rationale: projects.md §7; a deletion outside the app may be a sync accident.

#### Scenario: Deleted by a sync client
- **WHEN** a sync client deletes a file that has sidecar metadata
- **THEN** the binder shows it missing and its sidecar metadata still exists
