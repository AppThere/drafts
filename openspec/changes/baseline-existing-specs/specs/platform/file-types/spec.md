## Purpose

Source: appthere-drafts.md §9.1, §9.3, §9.4; projects.md §5. Which files are Markdown and which are Fountain, and how each kind is declared to the operating system so that files open in Drafts.

## ADDED Requirements

### Requirement: A document's kind comes from its extension
The system SHALL treat `.md`, `.markdown`, `.mdown` and `.mkd` as Markdown, and `.fountain` and `.spmd` as Fountain. Only the last extension SHALL count, and it SHALL be compared without regard to case. A document SHALL be one kind or the other, never both.

#### Scenario: Upper-case extension
- **WHEN** the writer opens `CHAPTER.MD`
- **THEN** it is opened as Markdown

#### Scenario: Screenplay Markdown extension
- **WHEN** the writer opens `scene.spmd`
- **THEN** it is opened as Fountain

#### Scenario: Not a recognised extension
- **WHEN** a file is named `notes.mdx`
- **THEN** its name does not make it Markdown; its kind is decided as for an unnamed document (platform/document-open)

### Requirement: Markdown is declared by its registered type
The system SHALL identify Markdown by the MIME type `text/markdown`, and SHALL also accept the unregistered `text/x-markdown` that some senders use. On Apple platforms it SHALL import the UTI `net.daringfireball.markdown` rather than export it.

Rationale: Markdown's type belongs to its author, and the system may already know it.

#### Scenario: Mail client sends text/x-markdown
- **WHEN** a document arrives typed `text/x-markdown`
- **THEN** it is treated as Markdown

### Requirement: Fountain is declared by the app
Fountain has no registered MIME type or public UTI. The system SHALL export the UTI `io.fountain.fountain`, conforming to `public.plain-text`, on Apple platforms. On Linux it SHALL declare the type `text/x-fountain`, and on Windows it SHALL associate Fountain extensions as `text/plain`.

#### Scenario: Installed on Linux
- **WHEN** the Linux package is installed
- **THEN** `.fountain` and `.spmd` files are recognised as `text/x-fountain` and offered to Drafts

### Requirement: The desktop registers both kinds
Each desktop package SHALL register Drafts as an application that opens both kinds: by document types on macOS, by one registry association per extension on Windows, and by a desktop entry naming `text/markdown`, `text/x-markdown` and `text/x-fountain` on Linux. On Linux, the Markdown type SHALL NOT be redeclared.

Rationale: the Linux packaging is built and used. The macOS and Windows packaging is configured but has not been run.

#### Scenario: Double-click in a Linux file manager
- **WHEN** Drafts is installed on Linux and the writer double-clicks a `.md` file
- **THEN** Drafts is offered to open it
