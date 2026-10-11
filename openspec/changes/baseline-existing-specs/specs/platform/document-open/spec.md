## Purpose

Source: appthere-drafts.md §9.2, §9.4; engineering-conventions.md §4 (parsing on the main dispatcher). How documents reach Drafts from the operating system and other apps, and how a handed-over file whose type was not given is recognised or refused.

## ADDED Requirements

### Requirement: The OS can hand a document to Drafts
The system SHALL accept documents handed over by the OS: on Android, as the data of a view or edit request and as the stream of a share; on Linux and Windows, as a path on the command line; on macOS, through the open-document event.

#### Scenario: Shared from a messaging app
- **WHEN** the writer shares a `.fountain` attachment to Drafts from a messaging app on Android
- **THEN** it opens in Drafts as a screenplay

#### Scenario: Opened from a file manager on Windows
- **WHEN** the writer double-clicks a `.md` file associated with Drafts on Windows
- **THEN** it opens in Drafts

### Requirement: Android accepts documents whose senders mislabel them
On Android, the system SHALL accept documents typed `text/markdown`, `text/x-markdown`, `text/plain` or `application/octet-stream`, and SHALL accept content URIs ending in any recognised extension whatever type they carry.

Rationale: downloads and messaging apps often send `application/octet-stream` whatever the file is, and a mail attachment's URI carries no name to match on.

#### Scenario: Download typed as octet-stream
- **WHEN** a downloaded `.md` file is handed over as `application/octet-stream`
- **THEN** Drafts is offered to open it

### Requirement: Kind from name, then type, then content
For a handed-over document, the system SHALL decide the kind from its name; failing that, from its MIME type; failing that, from its opening. An opening with a scene heading or a title-page key SHALL be read as Fountain, and anything else as Markdown. A desktop file with no recognised extension SHALL open as Markdown.

#### Scenario: Unnamed screenplay attachment
- **WHEN** a mail attachment with no file name, typed `text/plain`, begins with `INT. KITCHEN - DAY`
- **THEN** it opens as Fountain

#### Scenario: The name decides
- **WHEN** a file named `notes.md` begins with a scene heading
- **THEN** it opens as Markdown

### Requirement: Something that is not text is refused
When a handed-over document's name does not settle its kind and its opening is not UTF-8 text, the system SHALL NOT open it. It SHALL tell the writer "This does not look like a text document, so Drafts has not opened it." An empty file SHALL count as text.

Rationale: accepting `application/octet-stream` means any binary can be pointed at Drafts. UTF-16 is refused too, since nothing in the app reads it.

#### Scenario: A photo shared to Drafts
- **WHEN** a photo is shared to Drafts as `application/octet-stream`
- **THEN** no document opens and the writer sees why

### Requirement: Content is examined on every supported Android version
Examining a handed-over document's opening SHALL work on every Android version the app supports, so a non-text file is refused on all of them.

#### Scenario: Older Android device
- **WHEN** a binary file is handed to Drafts as `application/octet-stream` on Android 8
- **THEN** it is refused as not a text document, as on a current version

### Requirement: Opening a document does not freeze the window
Recognising, reading and parsing a document as it opens SHALL NOT run on the main thread. The window SHALL stay responsive while a document whose provider must fetch it over the network is being opened.

Rationale: engineering-conventions.md §4. A document from a cloud provider can take seconds to arrive.

#### Scenario: Document on a cloud drive
- **WHEN** the writer opens a document that its provider has to download first
- **THEN** the window stays responsive while the document arrives
