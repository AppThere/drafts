# Where the code differs from these specifications

`IMPLEMENTATION-PLAN.md` makes spec reconciliation a recurring gate: *"End of every phase. Does the
code still match `specifications/`? Update whichever is wrong."*

Sometimes neither is wrong. A platform makes something impossible, or a piece of user interface
depends on machinery that a later phase builds. Those are divergences, and the failure mode is that
they live only in commit messages and code comments until somebody "fixes" the code back to the
spec and breaks something.

Each entry says what the spec asks for, what the code does, why, and what would close it.

---

## 8.2 — The conflict dialog offers two of four choices

**Spec:** "[ Save a copy… ] [ Reload and lose my changes ] [ Show differences ] [ Cancel ]"

**Code:** Reload and Cancel. Escape also cancels.

**Why:** "Show differences" needs a diff view, which nothing has built. "Save a copy…" needed a
platform save dialog, which now exists (§7.4's *Save As*); the button is not wired to it yet. A button that does nothing is worse
than one that is not offered.

**What holds anyway:** the refusal itself — the part that protects the file — does not depend on
any of the four.

**Closes when:** "Save a copy…" is wired to the save dialog, which is a small change now; and
whenever a diff view is built, for "Show differences".

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

## 7.3 — A vanished file shows an error instead of its snapshot

**Spec:** "A document whose file has vanished opens read-only from its snapshot with a clear
banner offering *Save As*."

**Code:** the window shows *Could not open this document*, the path, and the underlying error
text (`OpenDocument.kt`, `Main.kt`). The snapshot is not offered back.

**Why:** *Save As* needs the save dialog, which does not exist yet. The error text is a
developer's message, not a novelist's, and is joined by concatenation rather than coming from
resources (§11.1) — that half is simply unfinished.

**Closes when:** §7.4's *Save As* lands (Phase 5).

---

## 9.4 — Every user on a machine shares the single-instance port

**Spec:** "Route to an existing instance via a single-instance lock and a local socket or named
pipe."

**Code:** a fixed loopback port, 51317, with no user in it (`SingleInstance.kt`). On a machine with
several signed-in users, one user's double-click can hand the path to another user's running
application, and the second launch exits.

**Closes when:** the port or pipe is scoped per user — a Unix-domain socket in the user's runtime
directory, a named pipe with the user's SID. Phase 5.

---

## 10.1, 10.2, 4.2 — The editor has no screen-reader semantics, and motion ignores the OS

**Spec:** §10.1's semantics for the editing surface; §10.2 "Honour `prefers-reduced-motion`";
§4.2's 120ms cross-fade.

**Code:** no `semantics`, `heading()` or live region anywhere in `:editor-ui`, and `:a11y` is
empty. Reduced motion is only the reader's own toggle; the operating system's setting is never
read. The cross-fade does not exist — `Motion.revealMillis` is read nowhere — so reveal is
instant for everyone.

**Closes when:** before the Phase 5 accessibility audit, which would otherwise spend its time on
things a review could have found.

---

## 11.1 — Strings are constants, and theme names cannot be translated

**Spec:** "User-facing strings live in resources."

**Code:** `Strings` is a Kotlin object of constants, provisional since Phase 0. Theme names are
`Palette.name` and `"System"`, which are also the keys settings are saved under, so translating
them would lose every saved theme. Several labels are built by concatenation.

**Closes when:** before the Phase 5 i18n audit. Separate the saved key from the displayed name
first; moving to resources is then mechanical.

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

## 10.2 — The shortcut map is fixed and undocumented

**Spec:** "Document the full shortcut map and make it user-remappable."

**Code:** Ctrl+S, Ctrl+Comma, Escape, F11 / Ctrl+Cmd+F and the editor's chords are fixed, and
written down nowhere a reader would look.

**Closes when:** documented with the Phase 5 settings; remappable by Phase 11, before a second
platform's conventions have to be reconciled with the first's.

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
