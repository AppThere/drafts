# Proposal

## Why

The project's requirements live in `specifications/`, written as intended-product documents that
run through Phase 12. They mix behaviour with rationale, implementation detail, build order, and
reference material about external formats. Divergences from them are kept by hand in
`divergences.md`. A conformance audit at Phase 8 (2026-10-07) found that this arrangement drifts:
roughly 25 gaps between code and spec were recorded nowhere, several phases closed with acceptance
criteria unmet, and the specs contradict one another in about a dozen places.

OpenSpec separates *what the system does* (main specs) from *what is proposed* (changes). Adopting
it, with the main specs describing the system as built, makes spec reconciliation structural
instead of a recurring manual gate.

## What Changes

- Baseline the behaviour that is built today (Phases 0–7, and Phase 8's committed work) as OpenSpec
  capabilities, one delta spec per capability, archived into `openspec/specs/`.
- Sort every requirement and audit finding by one rule (detailed in `design.md`):
  - conforming behaviour becomes a requirement;
  - a deliberate divergence becomes a requirement written as built, keeping its reason;
  - a bug keeps the *correct* requirement in the baseline and gets a fix change;
  - behaviour not yet built stays out of the baseline and lives in an open change;
  - a contradiction between specs is settled by the code where the code has chosen, and by the
    product owner otherwise.
- Each spec names its source sections in `specifications/` so that existing code citations
  (about 300, in the form `appthere-drafts.md 8.2`) continue to resolve.
- `specifications/` stays in place, frozen as rationale and reference. The external-format
  documents (`fountain.md`, `gfm.md`, `hugo-markdown.md`, `epub3.md`, `odf-text.md`,
  `ooxml-docx.md`) and `engineering-conventions.md` are not migrated: they are reference and
  process, not capabilities of this system.
- `divergences.md` is dissolved by the sort. Every entry becomes either a requirement written as
  built or an open change.
- No code changes.

## Capabilities

### New Capabilities

Editor (`appthere-drafts.md` §4, §10.1):
- `editor/reveal-preview`: preview and reveal states per block, what reveal keeps and drops, the switch on block change, vertical stability.
- `editor/block-editing`: caret movement across blocks, split and merge, structural edits that keep focus and caret, undo.
- `editor/selection`: within-block and cross-block selection as built, select-all, copy, cut and delete against the source.
- `editor/incremental-reparse`: an edit reparses a bounded window, and the result equals a full parse.
- `editor/outline`: the navigable heading and scene outline.

Markdown (`markdown-dialect.md`, `export-pipeline.md`):
- `markdown/dialect`: CommonMark 0.31.2 plus the enabled extensions, and what is not enabled; front matter and shortcodes outside the grammar.
- `markdown/round-trip`: untouched regions byte-identical, edited blocks re-serialised canonically, unknown constructs preserved.

Fountain (`fountain.md`, `appthere-drafts.md` §4.5, §5.4, §11.3):
- `fountain/parsing`: elements, forcing characters, inference rules, and parse order for Fountain 1.1.
- `fountain/round-trip`: byte-identical untouched regions, and what the canonical path writes.
- `fountain/screenplay-layout`: role insets, the 61-character mono size, the US Letter ceiling, dual dialogue, the line being edited, dimmed notes and boneyard.
- `fountain/scene-heading-keywords`: per-document heading prefixes and transition suffix, presets, and the offer to force or learn.

Documents (`appthere-drafts.md` §7, §8):
- `documents/snapshots`: autosave to app-private storage, its triggers, and atomic writes.
- `documents/save-safety`: the digest check before any write to a user file, atomic replacement, and the SAF exception.
- `documents/recovery`: restoring unsaved work on launch, and snapshot retention and pruning.
- `documents/session-state`: the six session states and how the chrome shows them.
- `documents/sessions-and-windows`: one window per document, and session restoration with per-platform access.
- `documents/new-documents`: untitled documents, choosing their kind, launcher entry points, and the first save.

Platform (`appthere-drafts.md` §9):
- `platform/file-types`: extensions, MIME types and UTIs for each kind.
- `platform/document-open`: receiving documents from the OS on each platform, including content sniffing.
- `platform/single-instance`: one instance per user, with a later launch routed to it as a new window.

Presentation (`appthere-drafts.md` §5, §6, §10.3, §12):
- `presentation/typography`: typefaces, script fallback, the prose scale, and the measure.
- `presentation/reader-settings`: reader controls and themes, persisted per document kind.
- `presentation/responsive-layout`: window size classes and fold avoidance.
- `presentation/distraction-free`: auto-hiding chrome, the single control, typewriter scrolling, focus mode, and full screen.

Accessibility (`appthere-drafts.md` §10):
- `accessibility/screen-readers`: block roles, announced text equal to edited text, live announcements for structural edits.
- `accessibility/vision-and-motor`: font scale, contrast per theme, keyboard reach, reduced motion, meaning never in colour alone.

Internationalisation (`appthere-drafts.md` §11):
- `i18n/strings`: user-facing strings in resources, with positional parameters.
- `i18n/bidi`: logical start/end layout, and the document's direction independent of the UI's.

Projects (`projects.md`, committed Phase 8 work only):
- `projects/folder-as-project`: any folder opens as a project, no mandatory structure, lazy `.drafts/`, nearest `.drafts/` wins, non-text files shown.
- `projects/ordering`: three-tier order, the numeric prefix grammar, the "new" marker, and prefix-mode reorder.
- `projects/manifest`: `project.toml`, written line-stably, never overwriting what the app does not model, and digest-checked.

### Modified Capabilities

None. `openspec/specs/` is empty.

## Impact

- Creates `openspec/specs/` when this change is archived. No source, build or test files change.
- `AGENTS.md` and `IMPLEMENTATION-PLAN.md` call `specifications/` normative. Once this change is
  archived, they will need to point at `openspec/specs/`. That edit is a follow-up and is not part
  of this change.
- Phase 8's reconciliation work (uncommitted when this change was opened) and every behaviour not
  yet built are deliberately excluded. They are carried by the open changes listed in `design.md`.
