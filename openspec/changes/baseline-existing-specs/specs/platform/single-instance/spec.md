## Purpose

Source: appthere-drafts.md §9.4, §7.4. On desktop, one running instance per user: a later launch hands what it was asked to do to the running application, which opens it in a new window.

## ADDED Requirements

### Requirement: A later launch is handed to the running instance
On desktop, when Drafts is launched while it is already running for the same user, the new launch SHALL hand its request to the running instance and exit. The request SHALL be either a file to open or a new document of a given kind.

#### Scenario: Double-clicking a second file
- **WHEN** Drafts is running and the writer double-clicks another document in the file manager
- **THEN** the running instance opens it in a new window
- **AND** no second process remains running

#### Scenario: Launcher action
- **WHEN** Drafts is running and the writer chooses *New Fountain screenplay* from the launcher
- **THEN** the running instance opens a new untitled Fountain document in a new window

### Requirement: One instance per user
The running instance SHALL be reachable only by the user who started it. A second user signed in to the same machine SHALL get an instance of their own, and SHALL NOT be able to hand documents to the first user's instance.

#### Scenario: Two users on one machine
- **WHEN** two users on the same machine each launch Drafts
- **THEN** each has their own running instance, and neither blocks the other

### Requirement: A killed instance does not block the next launch
When the previous instance ended without releasing its claim, as when it was killed, the next launch SHALL become the running instance.

#### Scenario: Launch after a crash
- **WHEN** Drafts was killed and is launched again
- **THEN** it starts normally as the running instance

### Requirement: Never unlaunchable
When anything other than a running Drafts instance answers, or the running instance does not answer in time, the launch SHALL proceed as the first instance rather than fail.

#### Scenario: Unresponsive instance
- **WHEN** a launch finds a claim whose owner does not reply
- **THEN** the launch starts Drafts normally
