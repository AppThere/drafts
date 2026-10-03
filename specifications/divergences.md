# Where the code differs from these specifications

`IMPLEMENTATION-PLAN.md` makes spec reconciliation a recurring gate: *"End of every phase. Does the
code still match `specifications/`? Update whichever is wrong."*

Sometimes neither is wrong. A platform makes something impossible, or a piece of user interface
depends on machinery that a later phase builds. Those are divergences, and the failure mode is that
they live only in commit messages and code comments until somebody "fixes" the code back to the
spec and breaks something.

Each entry says what the spec asks for, what the code does, why, and what would close it.

---

## 8.2 — The conflict dialog offers three of four choices

**Spec:** "[ Save a copy… ] [ Reload and lose my changes ] [ Show differences ] [ Cancel ]"

**Code:** Save a copy, Reload, and Cancel; Escape also cancels. "Save a copy…" is §7.4's *Save As*,
offering the name marked as the reader's own version so the dialog does not open on the file
being kept.

**Why:** "Show differences" needs a diff view, which nothing has built. A button that does nothing
is worse than one that is not offered.

**What holds anyway:** the refusal itself — the part that protects the file — does not depend on
any of the four, and the answer that loses nothing, keeping both versions, is there.

**Closes when:** a diff view is built.

---

## 8.3 — The restore banner offers Keep rather than Compare

**Spec:** "*Unsaved changes from your last session have been restored.* [ Compare ] [ Discard ]"

**Code:** Keep and Discard.

**Why:** Compare needs the same diff view as "Show differences" above. Keep is not in the spec and
is there because the banner has to be dismissible; an unobtrusive banner that cannot be got rid of
stops being unobtrusive by the second document.

**Closes when:** a diff view exists. Keep should probably stay regardless.

---

## 7.3 — `documentId` is derived from the location, not a UUID

**Spec:** `"documentId": "uuid"`

**Code:** the SHA-256 of the document's absolute path (desktop) or content URI (Android).

**Why:** a UUID needs an index mapping it back to a file. There is no index yet, and a
content-addressed id finds its own snapshot with nothing to consult — which is what 8.3's recovery
needs on launch.

**Cost:** renaming or moving a file outside the application orphans its snapshot. The work is not
lost, but nothing will offer it back.

**Partly closed:** untitled documents (§7.4) now get a real UUID, since they have no location to
derive one from. Phase 5 restored sessions without an index — each snapshot directory is found by
its own id — so the index this was once waiting for was never needed.

**Closes when:** file-backed documents use the same scheme. The first save of an untitled document
is the natural point to decide it: the session already has a UUID, and deriving a new id from the
file it was saved to would orphan its own snapshot.

---

## 8.1 — One of five snapshot triggers is not wired

**Spec triggers:** focus loss, 30 seconds of continuous editing, 3 seconds of idle, window close,
"explicit or implicit navigation away from the document".

**Code:** the first four. The fifth has nothing to hook — there is no navigation.

**Closes when:** navigation exists.

---

## 5.5 — The "system" theme is always Light on Linux desktop

**Spec:** "Theme: light, dark, sepia, high contrast, system".

**Code:** "System" follows the operating system's light or dark preference through Compose's
`isSystemInDarkTheme()`, re-read on every composition so a change while a document is open is
followed. On Android that is the real setting. On Linux desktop it is always Light.

**Why:** Compose Desktop asks Skiko, and Skiko's native lookup returns `UNKNOWN` on Linux, which
Compose treats as "not dark". Measured on this project's Linux build machine, not inferred. macOS
and Windows go through the same lookup, which Skiko implements for them; neither has been run.

**Closes when:** a Linux `actual` reads the preference itself — the XDG Settings portal's
`org.freedesktop.appearance color-scheme`, which GNOME and KDE both publish — or Skiko learns to.

---

## 7.4 — Choosing Fountain does not yet re-interpret the text

**Spec:** "choosing Fountain re-interprets the same text as Fountain and applies the Fountain reader
settings."

**Code:** choosing Fountain changes the session's kind, the extension *Save As* offers, the reader
settings the window wears, and the kind the next new document starts as. The text is still parsed
as Markdown, as every `.fountain` file is today.

**Why:** there is no Fountain parser yet; `:core-parse-fountain` is Phase 7. Everything the choice
can honestly change, it changes.

**Closes when:** Phase 7's parser exists and the editor chooses its parser by kind.

---

## 7.4 — No Windows jump list, and no *File > New* anywhere

**Spec:** the launcher entry points table — Linux `.desktop` actions; macOS Dock menu and *File >
New*; Windows jump list tasks and *File > New*.

**Code:** the Linux actions, built and used from an installed package. The macOS Dock menu,
written and never run (no Mac here). No Windows jump list, and no *File > New* on any platform.
Every route shares one request type (`--new <kind>`), so each missing one is a caller, not a
mechanism.

**Why:** a jump list is Windows' `ICustomDestinationList`, a COM API that neither `jpackage` nor
Compose exposes; it needs a native bridge. *File > New* needs a menu bar, and the application has
none. On Windows and Linux a menu bar is chrome that sits in the window permanently, which §12 does
not allow ("The only chrome is the status indicator … and one control that opens the reader
settings"). macOS's menu bar is outside the window and would not conflict.

**Closes when:** a human decides whether §12 or §7.4 gives way on Windows and Linux — a macOS-only
menu bar is the obvious first step either way — and a native bridge for the jump list is judged
worth its weight.

---

## 12 — Focus mode dims by block, not by sentence

**Spec:** "**Focus mode** as an option: dim all blocks except the current one, or the current
sentence."

**Code:** blocks only. `FocusMode` has `Off` and `Block`.

**Why:** a sentence boundary is a question about the language, not the punctuation. A full stop
ends "end." and does not end "Dr. Smith"; Japanese ends a sentence with `。`, and Greek asks a
question with `;`. 11.1 requires the application to work in those languages, and a naive split would
dim the wrong half of a sentence for most of the world.

**Closes when:** 11.5's ICU-style segmentation exists. The sentence option belongs with it.

---

## 10.1 — The outline lists headings, not scenes

**Spec:** "An **outline view** exposing the heading/scene hierarchy as a navigable list."

**Code:** it is built. Headings, in document order, indented by level, each one a 48dp button
announced with its level -- "Heading level 2, Chapter one" -- because the indentation that says so
to the eye says nothing to anything else. Choosing one scrolls the document there and puts the
caret in it. 6's two shapes are both there: a pane beside the document on an Expanded window, which
the document gives up width for, and a sheet over it on anything narrower.

What is missing is the other half of "heading/scene". Fountain's scene headings are a different
block type and arrive with Fountain in Phase 7; `outlineOf` gets another branch then rather than a
second list.

**Closes when:** Phase 7 lands.

---

## 7 (Fountain) — Two roles the parser does not use

**Spec:** `export-pipeline.md`'s `BlockRole` enum, which lists `DUAL_DIALOGUE_LEFT` and
`DUAL_DIALOGUE_RIGHT` among the Fountain roles.

**Code:** a dual-dialogue speech keeps the roles it had -- `CHARACTER`, `PARENTHETICAL`,
`DIALOGUE` -- and carries a `dual` class instead. What the `^` marker decides is a *layout*:
`appthere-drafts.md` 5.4 calls dual dialogue "a two-column layout at the 16.7%/25% insets", and on
a compact window it "stacks vertically". Spending the role on the column would lose what each block
actually is, and a renderer would have to work out from the text that the first line of a
right-hand column is a character name.

The two roles are therefore unused. They become right if the renderer turns out to want them, which
is a question 5.4's deliverable will answer.

**Also:** a Fountain title page has no role at all -- neither 5.4's inset table nor the enum has
one -- so it is a `BODY` paragraph holding its own source, with its `Title:` and `Author:` lifted
into `DocMetadata`. It round-trips exactly; what it does not yet do is look like a title page.

**Closes when:** 5.4's screenplay rendering lands and says what it needs.

---

## 11.1 — Nothing has been translated yet

**Spec:** "Compose Multiplatform Resources with `stringResource` and **per-locale directories**. No
string concatenation; use positional parameters. Pluralisation via plural resources."

**Code:** all 110 strings are in `values/strings.xml` in `:i18n`, read with `stringResource` in a
composition and `getString` outside one. Everything user-facing that was a joined string is a
positional-parameter resource: the "Document, Saved" pattern behind every content description, the
units in the reader controls, and the mark between a number and its fraction.

**What is left is the thing a second language would prove.** There is one directory, `values`, and
no translation — so nothing demonstrates that `values-fr` beside it resolves, and nothing can tell
a resource lookup from a literal that happens to match the English. Changing a string in the file
does change what is on screen, which is checked; that a *different locale* would is not.

Inventing a translation to test the mechanism would be worse than the gap: a half-translated
language shows a reader their own words for five things and English for the rest, and a
machine-translated whole one is a quality claim nobody here can stand behind.

**Two joins stay as they are, with reasons.** A key chord ("Ctrl+Shift+S") is a notation rather
than a sentence, the number of parts varies, and the plus is the same everywhere; the key *names*
in it are resources. A file name is not prose, and only the words inside one come from resources.

**Pluralisation** has nothing to fix: nothing in the application counts anything out loud. The
first thing that does should reach for a plural resource rather than an `if`.

**Closes when:** there is a real translation, which needs a translator rather than a commit.

---

## Markdown round-trip gaps no reader can reach yet

**Spec:** `markdown-dialect.md`'s round-trip contract.

**Code:** footnote references are dropped by the inline writer; heading and image attributes and
loose definition-list entries are not written back; the attribute, footnote and definition-list
restorers walk top-level blocks only; a `[^x]:` line inside a
fenced code block is read as a footnote. Definition lists are one line per definition and one
entry per list.

**Why they are not urgent:** the serialiser writes only blocks the reader edited, and nothing in
the editor yet creates these constructs.

**Closes when:** before any of them can be produced by editing — at the latest, Phase 10's export
work, which leans on the same IR.

---

## 10.2 — The shortcut map is fixed

**Spec:** "Document the full shortcut map and make it user-remappable."

**Code:** documented. Every shortcut is listed in a panel opened with Ctrl+/ or from the reader
controls, read from the same tables (`EditorShortcuts`, `WindowShortcuts`, and the host's full
screen) the handlers match against, so the list and the keys cannot disagree. They are not yet
remappable: the tables are constants.

**Closes when:** remappable by Phase 11, before a second platform's conventions have to be
reconciled with the first's. The tables are the one place a remapping has to change.

---

## engineering-conventions.md — The detekt configuration is looser than the spec

**Spec:** the thresholds and path exemptions of §2.

**Code:** `MagicNumber` also ignores `2`, property declarations, and all of `:design-system`;
composables are exempt from both complexity rules with nothing in their place; fixtures,
`**/resources/**` and test source sets are exempt by path beyond the spec's list. Two rules,
`UnsafeCallOnNullableType` and `ForbiddenMethodCall`, need type resolution, which is not wired, so
they never run. dependency-analysis still only warns, for a reason ("the modules are empty") that
stopped being true in Phase 1.

**Why this is not simply fixed:** each is a gate configuration, and loosening or tightening one is
a human decision (`AGENTS.md` §3). Some may be right — a design system is mostly numbers — and
belong in the spec; the rest belong back in the config.

**Closes when:** decided, item by item.

---

## 6 — Only a vertical hinge moves the measure

**Spec:** "Foldables: use Jetpack WindowManager's `FoldingFeature` to avoid rendering text across a
hinge. On a book-posture fold, place the content column entirely on one side or split into two
panes at the hinge — never let the fold bisect the measure."

**Code:** the fold is read (`currentFold()` in `:platform-windows`, Jetpack WindowManager on
Android, null everywhere else) and a separating **vertical** hinge moves the whole page — text,
status dot and panels — onto the wider side of it. Three things are narrower than the sentence:

- A **horizontal** hinge is ignored. It divides the window top from bottom, which does not bisect
  the measure: a line of text crosses it at a point rather than along its length. The remedy that
  would apply — confining a scrolling document to half the height of an already short window —
  costs the reader more than the crease does.
- The **first** folding feature is used. A window can report more than one, and a device with two
  hinges exists; this handles the single-hinge case the spec describes.
- Of the spec's two options, only the first. **Two panes at the hinge** needs a second thing to put
  in the second pane, and the only one 6 offers is the outline, which does not exist (see above).

**Verified** on the `pixel_fold` AVD: flat, the measure spans the inner display; half-opened, it
sits entirely left of the hinge WindowManager reports at x=1104.

**Closes when:** possibly never for the vertical case. The outline has since landed, so 6's other
option -- "split into two panes at the hinge" -- is now buildable: the outline on one side of the
fold and the document on the other. Whether that is *better* than a column on one side is a
question about reading on a half-folded device that nobody here has done enough of to answer. The
horizontal case closes if a flip-phone-shaped device turns up to test it on.

---

## 7.2 — One task per document, with one flag rather than two

**Spec:** "Launch each document with `FLAG_ACTIVITY_NEW_DOCUMENT or FLAG_ACTIVITY_MULTIPLE_TASK`."

**Code:** `FLAG_ACTIVITY_NEW_DOCUMENT` alone. `MULTIPLE_TASK` is what tells Android to make a *new*
task even when one already exists for that data, so the two together open a second window for a
document that already has one — which 9.4 forbids in as many words: "a file already open gets its
existing window". With `documentLaunchMode="intoExisting"` in the manifest and one session id in
each intent's data, the first flag alone gives exactly one task per document and brings an existing
one forward.

**Verified** on a device: four restored sessions came back as four tasks, and launching again while
they were open added one new document rather than a fifth copy of an old one.

**Closes when:** never, unless the spec sentence changes. This is the spec's two sentences
disagreeing, and 9.4 is the one about what the reader sees.

---

## 7.3 — On Android a closing document outlives the Activity that closed it

**Spec:** a session is open until it is closed, and 8.1 snapshots on "window close, before
teardown".

**Code:** both happen, from `onStop` when the Activity is finishing — but they are *started* there
and finish afterwards, in a process-scoped coroutine. They cannot be done in the callback itself:
`lifecycleScope` is cancelled at DESTROYED, which is immediately after, and `runBlocking` parks the
main thread and is banned by `engineering-conventions.md` 4.1.

**What that costs:** a process killed in the window between the Activity going and the write
landing loses the closing snapshot and leaves the session open, so the document reopens next launch
holding whatever the last idle snapshot had. An Android process is not killed the instant its last
Activity goes — it becomes a cached process — and the write is a few kilobytes to app-private
storage, so the window is milliseconds. It is the same exposure 8.1's other four triggers exist to
cover, which is what makes it tolerable.

**Closes when:** there is a reason to think it has been hit. A `Service` or `WorkManager` would
close it and would cost more than the case is worth until then.
