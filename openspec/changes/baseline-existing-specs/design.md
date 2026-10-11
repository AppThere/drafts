# Design

## Context

`openspec/specs/` is empty. The requirements are in twelve documents under `specifications/`, and
those documents are of different kinds:

```
appthere-drafts.md  projects.md  markdown-dialect.md  export-pipeline.md
    behaviour   --> capability specs (this change)
    rationale   --> stays in specifications/, cited from each spec
    build order --> dissolves into the set of open changes

fountain.md gfm.md hugo-markdown.md epub3.md odf-text.md ooxml-docx.md
    reference material on external formats; not requirements of this system

engineering-conventions.md     process and quality gates; not a capability
divergences.md                 sorted into requirements and open changes (D2)
IMPLEMENTATION-PLAN.md         phases; the unbuilt ones become open changes (D7)
```

The 2026-10-07 conformance audit is the input to the sort. Its findings are listed against the
open changes in D7.

## Goals / Non-Goals

**Goals:**
- Main specs that state what the code does today, so a gap between spec and code is always either
  a defect or an open change, never an unrecorded drift.
- Keep the reasons. The existing specs explain why, and a list of SHALL statements on its own
  loses that.
- Every existing citation of `specifications/*.md` keeps resolving.

**Non-Goals:**
- Fixing anything. Every defect the audit found goes to a fix change (D7).
- Changing a quality gate. `AGENTS.md` §3 makes that a human decision. Gate findings are recorded
  as a change proposal and nothing more.
- Rewriting the external-format reference documents.
- Updating `AGENTS.md` and `IMPLEMENTATION-PLAN.md` to point at `openspec/specs/`. That follows
  archive as its own edit.

## Decisions

### D1. Main specs describe the system as built

`openspec/specs/` holds what the code does today. Everything intended but not built is carried by
an open change, and archiving that change is what moves it into the specs.

*Alternative: migrate the intended specs as written.* That is less work, but it fights the tool:
a change archived onto a spec that already claims its behaviour records nothing. Divergences would
also have to live on in a separate register, which is the arrangement that drifted.

### D2. One sorting rule for every requirement and finding

| Category | Example | Baseline | Elsewhere |
|---|---|---|---|
| Conforms | Per-block `LazyColumn` with stable keys | Requirement | — |
| Deliberate divergence | Focus mode dims by block; one Android task flag | Requirement written as built, with its reason | — |
| Bug: the code tries and fails | A save leaves the file `0600`; reorder temp names are dotfiles | The **correct** requirement | Fix change |
| Not built | LRI/PDI isolation, IME suspension, touch handles, binder | Absent | Open change |
| Spec contradiction | §6 nav rail vs §12 single control | The code's choice, if it made one | Product owner, if it did not |

The bug row is the important one. A defect does not lower the requirement. "A save preserves the
file's permissions" stays in `documents/save-safety` while the code breaks it, and the fix change
is what closes the gap. Without this rule, an as-built spec would quietly absorb every bug.

Several contradictions are settled by this rule without a decision:
- The three disagreeing build orders disappear, because specs no longer carry build order.
- §6 against §12: the code follows §12, so there is one control and no rail.
- The Markdown reparse window: §4.3 says the "paragraph before through paragraph after" window "is
  correct", yet it also lists fenced code as a construct that spans blocks. The requirement
  becomes *"a windowed reparse yields the same blocks as a full parse"*. Unclosed fences and
  out-of-window references are defects against it.
- CommonMark: the requirement stays at 652/652. The current 463/652 is a defect, and the ratcheted
  floor is the fix change's progress measure.

### D3. Shape of each spec

```
## Purpose
Source: appthere-drafts.md §8.2; divergences.md "8.2". One or two sentences of purpose.

## ADDED Requirements
### Requirement: ...      one behaviour, SHALL/MUST
<normative text>

Rationale: <the why, in a sentence or two>     optional, inline

#### Scenario: ...        WHEN / THEN, testable
```

- The `Source:` line goes inside `## Purpose`, and rationale goes inline in the requirement body.
  A trial archive in a scratch project (2026-10-07) showed that archive copies `## Purpose` and the
  requirement blocks into the main spec, and **drops any other section of a delta**. A separate
  `## Rationale` section would be lost.
- The inline rationale counts toward the 500-character limit on a requirement's text, so it is a
  sentence or two. The full reasoning stays in `specifications/`, reached through the `Source:`
  line.
- Requirements state observable behaviour, as the OpenSpec template asks. Library names, class
  names and manifest XML stay in `specifications/`. The exception is where the library or format
  *is* the behaviour, such as "CommonMark 0.31.2", the `project.toml` keys, or the Android
  persistable-permission guarantee.
- A requirement that came from a divergence entry names that entry in its spec's `Source:` line and
  says why in its inline rationale.
- A requirement the audit added, rather than one found in the source documents, says so in its
  rationale.

### D4. `specifications/` stays, frozen

About 200 files cite `specifications/*.md` by section number. Moving or renaming those documents
would break every citation, and rewriting the citations is churn with no behavioural value. So:

- The documents stay where they are and stop being edited for requirements. A header added after
  archive will say that `openspec/specs/` is normative and this document is rationale.
- Each spec's `Source:` line maps back to these documents. A reader following a code citation
  lands on the rationale and can go on to the requirement.
- New code cites the capability path, for example `documents/save-safety`.

### D5. Phase 8's split

- **Baseline:** what is committed at `4185097`, namely folder-as-project, `project.toml`, ordering,
  and prefix-mode reorder.
- **`projects-core` change:** reconciliation (uncommitted when this change was opened, including the
  new `projects.md` §9 paragraph), SAF walking, filesystem watching, binder UI, trash, templates,
  and project windows.

This keeps the baseline independent of the working tree. `projects-core` becomes the change that
Phase 8 work continues under.

### D6. Accessibility and i18n requirements live where they apply

A requirement goes in the capability whose behaviour it constrains. For example, "reveal is not
announced" goes in `editor/reveal-preview`, and "dialogue blocks name their role" goes in
`fountain/screenplay-layout`. Only cross-cutting rules sit in `accessibility/*` and `i18n/*`, such
as the WCAG target, contrast per theme, keyboard reach, and strings in resources. This follows the
existing standing requirement that accessibility and i18n are per-phase acceptance criteria rather
than a separate pass.

### D7. Open changes, created after this one

Each is created separately, through `/opsx:propose`, when its work comes up. The numbered findings
come from the 2026-10-07 audit.

**Unbuilt phases**

| Change | Covers |
|---|---|
| `projects-core` | Phase 8 remainder (D5). Also: the nested-`.drafts/` upward check; a `project.toml` with a later `schema` must be read-only; reconciliation being run on open, focus and watch. |
| `project-views` | Phase 9: per-document metadata, outliner, corkboard, collections, targets, snapshots. |
| `export-and-compile` | Phase 10: XHTML, EPUB, FODT/ODT, DOCX, compile. Carries the open typographer-on-export question, and the Fountain canonical-path gaps (lyric in a speech; space-only blank line). |
| `apple-platforms` | Phase 11: iOS and iPadOS. |
| `android-xr` | Phase 12. |

**Fixes and closures**

| Change | Findings |
|---|---|
| `save-preserves-file` | Permissions and symlinks lost on save. *Save As* and *Save a copy* skip the digest check. Snapshots are never pruned on Android. |
| `closed-untitled-recovery` | An untitled document closed with words in it keeps its snapshot, but nothing offers the words back (found while writing `documents/new-documents`). |
| `reorder-crash-safety` | Prefix-reorder temporary names are hidden dotfiles. Missing entries are left out of the numbering. |
| `reparse-equals-full-parse` | Markdown: unclosed fences, out-of-window references, front matter at the window start. Fountain: a boneyard opened partway through a line. Equivalence tests for both kinds. |
| `commonmark-conformance` | 463/652 passing, against 652. Unknown blocks such as GFM alerts and `$$` math are dropped from the IR. No real-world Markdown corpus or property tests. Canonical-path defects: `www.` rewritten to `https://`; indented code instead of fenced; missing escapes; heading ids losing combining marks. |
| `android-io-off-main` | Provider queries and the first parse run on Main. `readNBytes` needs API 33, but `minSdk` is 26. |
| `complete-selection` | Shift+Up/Down and gutter block selection (v1). Touch drag handles and platform selection menus (v2). |
| `bidi-and-input-methods` | LRI/PDI isolation in reveal; suspending structural reparse during IME composition; a per-document direction override. |
| `typography-gaps` | Font-family override; heading keep-with-next; fallback verified on every target. |
| `diff-view` | Closes the 8.2 *Show differences* and 8.3 *Compare* divergences. |
| `remappable-shortcuts` | The 10.2 divergence, due by Phase 11. |
| `typed-offsets-in-editor` | The editor uses raw `Int` where `BlockIndex`, `BlockOffset` and `DocumentOffset` exist. This has no spec delta, so the change carries `skip_specs: true`. |
| `quality-gate-review` | No spec delta (`skip_specs: true`). A proposal for human decision, not an implementation: the import-only Konsist write rule; architecture tests that pass when a scope throws; `project-model` unconstrained; type-resolution rules never running; missing custom rules; the `MagicNumber` loosenings; the detekt-formatting contradiction. |

The remaining divergence closures each wait on something outside the project. Each stays recorded
as a requirement written as built until its trigger arrives, rather than as an empty change:

- sentence focus mode, which waits on ICU segmentation;
- the Linux system theme;
- the Windows jump list and the macOS menu bar;
- the `NavigatedAway` snapshot trigger;
- the notes-across-a-blank-line case.

### D8. Order of writing, and review

The specs are written one domain at a time, in this order. The product owner reviews each domain
before the next is started.

```
documents/ -> platform/ -> editor/ -> markdown/ -> fountain/
           -> presentation/ -> accessibility/ -> i18n/ -> projects/
```

`documents/` comes first because it carries the most findings to sort.

Each domain is validated with `openspec validate --strict` before review. Every requirement in it
is checked against the code at a cited `file:line`, not against the audit summary alone.

### D9. Decision needed before `projects/ordering`

- **What "new" means.** `projects.md` §3 means *not placed in an order the writer made*. §9 means
  *not in the reconciliation index*. The committed ordering code implements §3. Whether the binder
  shows one marker or two is the product owner's decision.

`BlockRole.DUAL_DIALOGUE_*` is an internal enum and not observable behaviour, so it stays out of
the specs. It is recorded in `export-and-compile`, which is where the model question matters.

## Risks / Trade-offs

- **Specs read as "less" than the product.** Someone reading only `openspec/specs/` sees what
  exists, not the vision.
  → `openspec list` shows the open changes, and D7 maps the whole remaining plan onto them.
- **Mis-sorting a bug as a deliberate divergence enshrines it.**
  → Anything the audit called undocumented is treated as a bug unless the product owner calls it
  deliberate. Each domain review lists these explicitly.
- **Two places to read.** Requirements live in `openspec/`, and the reasons behind them partly in
  `specifications/`.
  → The `Source:` line, the inline rationale, and the frozen-document header.
- **Rationale is thinner than in the source documents.** Archive keeps only Purpose and
  requirements (D3), so each requirement carries a sentence or two of reasons rather than the
  source's full argument.
  → The `Source:` line leads to the full argument, and `specifications/` is frozen rather than
  deleted (D4).
- **Archive behaviour was checked once.** The trial ran on OpenSpec 1.14.1, with a new capability
  and ADDED requirements only. Whether a MODIFIED delta keeps inline rationale was not tried.
  → Check that on the first fix change to archive onto these specs.
- **The audit is a snapshot of 2026-10-07.** Code keeps moving while the baseline is written.
  → The baseline is pinned to commit `4185097` (D5). Re-check against that commit when each domain
  is written.
- **Volume.** About 31 capabilities is a lot to write and review.
  → Writing by domain (D8) keeps each review to a handful of specs. The archive happens once, at
  the end.
