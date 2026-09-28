# Where the code differs from these specifications

`IMPLEMENTATION-PLAN.md` makes spec reconciliation a recurring gate: *"End of every phase. Does the
code still match `specifications/`? Update whichever is wrong."*

Sometimes neither is wrong. A platform makes something impossible, or a piece of user interface
depends on machinery that a later phase builds. Those are divergences, and the failure mode is that
they live only in commit messages and code comments until somebody "fixes" the code back to the
spec and breaks something.

Each entry says what the spec asks for, what the code does, why, and what would close it.

---

## 8.2 — Atomic save is impossible on Android

**Spec:** "Explicit save uses the same atomic temp-and-rename, then updates `baseDigest` to the
newly written content."

**Code:** On desktop and Apple targets, exactly that. On Android, where the reader's documents
arrive as `content://` URIs through the Storage Access Framework, the write truncates the document
and refills it in place.

**Why:** SAF has no operation that replaces one document with another in one step.
`DocumentsContract.renameDocument` fails when the target name is taken, and there is no
rename-over. This is the shape of the API, not a shortcut.

**What holds anyway:** 8.2's digest check is a comparison and a refusal, so it works in full. 8.1's
snapshot goes to app-private storage through a real filesystem, so it is atomic in full. The words
survive a crash mid-save even where the file does not, and 8.3 restores them on the next launch.
`DocumentStore.writesAtomically` makes this a declared property, and `SnapshotStore` refuses to be
constructed on a store that lacks it, so snapshots cannot be routed through SAF by accident.

**Closes when:** it does not. This should be written into 8.2 as a platform reality.

---

## 8.2 — The conflict dialog offers two of four choices

**Spec:** "[ Save a copy… ] [ Reload and lose my changes ] [ Show differences ] [ Cancel ]"

**Code:** Reload and Cancel. Escape also cancels.

**Why:** "Save a copy…" needs a platform save dialog, which is `:platform-intents` in Phase 5.
"Show differences" needs a diff view, which nothing has built. A button that does nothing is worse
than one that is not offered.

**What holds anyway:** the refusal itself — the part that protects the file — does not depend on
any of the four.

**Closes when:** Phase 5 brings the file dialog, and whenever a diff view is built.

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

**Closes when:** 7.3's session list exists as a real index, which is Phase 5's "Sessions restore
across restart".

---

## 7.3 — `window` is not recorded

**Spec:** the session record carries `window: { x, y, width, height, placement }`.

**Code:** the field exists and is always null.

**Why:** window geometry belongs to `:platform-windows`, and `IMPLEMENTATION-PLAN.md` puts
"per-window `WindowState`, persisted" in Phase 5. Phase 4 built the record; Phase 5 fills this in.

**Closes when:** Phase 5.

---

## 8.1 — One of five snapshot triggers is not wired

**Spec triggers:** focus loss, 30 seconds of continuous editing, 3 seconds of idle, window close,
"explicit or implicit navigation away from the document".

**Code:** the first four. The fifth has nothing to hook — there is no navigation.

**Closes when:** navigation exists.
