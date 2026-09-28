# AGENTS.md

Instructions for coding agents working in this repository. Read this before finalising any
implementation.

Normative thresholds and the full smell catalogue live in
[`specifications/engineering-conventions.md`](specifications/engineering-conventions.md).
This file is the procedure.

---

## 1. Before you start

- Read the relevant spec in `specifications/` first. The specs are normative; if the code and the
  spec disagree, that is a finding to report, not a discrepancy to silently resolve in either
  direction.
- Check which module the work belongs in. The dependency direction is
  `app-*` → `editor-*` → `core-*`, and `core-model` depends on nothing. Putting code in the wrong
  module is harder to undo later than it looks now.
- Prefer editing an existing file over creating a new one. Prefer extending an existing
  abstraction over introducing a parallel one.

---

## 2. The quality pass

Run this when you believe the implementation is complete, **before** reporting it as done.
It has two halves, in order: mechanical, then judgment. Neither substitutes for the other.

### Phase 1 — mechanical

```sh
./gradlew spotlessApply
./gradlew detekt
./gradlew :tools-architecture-tests:test
./gradlew allTests
./gradlew check
```

Then verify:

- [ ] `detekt` reports no new findings
- [ ] `detekt-baseline.xml` is **unchanged or smaller** — never larger
- [ ] No new `@Suppress` annotations, or each has a comment stating why
- [ ] No file crossed a **fail** threshold (600 lines, 60-line function, complexity 15, nesting 4)
- [ ] Files that crossed a **warn** threshold are listed in your report with a justification
- [ ] Tests pass on **every** target, not just JVM
- [ ] `./gradlew buildHealth` reports no unused or misdeclared dependencies

### Phase 2 — judgment

Tools cannot find these. Walk the list deliberately. For each, either state that it doesn't
apply or describe what you found.

**Waste**
- [ ] Any interface with exactly one implementation that isn't a platform boundary or a test seam?
- [ ] Any configuration option, parameter, or extension point that nothing uses?
- [ ] Any abstraction introduced for a second case that doesn't exist yet?

**Structure**
- [ ] Does any new class do more than one thing? Name it in a sentence without "and."
- [ ] Does any function mostly manipulate another object's data? It belongs on that object.
- [ ] Would a small conceptual change require edits in several files? The concept isn't located.
- [ ] Were two things unified because they look alike rather than because they change together?

**Correctness in this codebase specifically**
- [ ] Does any code path parse or reparse the whole document on an edit?
- [ ] Does any `LazyColumn` over blocks lack a stable `key`?
- [ ] Does any write to a user file or snapshot skip the atomic temp-and-rename path?
- [ ] Does any write to a user file bypass the digest check?
- [ ] Does any export backend build XML with string concatenation?
- [ ] Does any parse, IO, or serialisation run on the main dispatcher?
- [ ] Are block indices, block offsets, and document offsets kept in distinct types?
- [ ] Does any `actual` implementation contain logic rather than adaptation?

**Failure handling**
- [ ] Does any `catch` log and continue, leaving the user unaware something failed?
- [ ] Is any user-facing error message written for a developer rather than a novelist?
- [ ] Given the "never lose work" guarantee, what happens to in-progress edits on each new
      failure path you introduced?

**Requirements**
- [ ] Are all new user-facing strings in resources?
- [ ] Does new interactive UI expose semantics for screen readers?
- [ ] Does new UI hold up at 200% font scale, and in RTL?
- [ ] Does new layout use `start`/`end` rather than `left`/`right`?

**Tests**
- [ ] Do the tests assert behaviour, or would they break on a pure refactor?
- [ ] Does each new branch have a test?
- [ ] Is there a test for the failure case, not just the success case?

---

## 3. How to fix findings

The intent of a finding is to improve the code. There are cheaper ways to make a finding
disappear, and all of them are prohibited.

**Do:**
- Extract a cohesive unit with a name that describes what it is
- Move a function to the type whose data it uses
- Replace a branching block with polymorphism or a lookup table where that genuinely simplifies
- Delete code that isn't used

**Do not:**
- Split a file at an arbitrary seam to get under a line count. A limit is a signal to reconsider
  the file's cohesion, not an instruction to cut it in half. `FooUtils2.kt` is a worse outcome
  than the original file.
- Extract a function used once, from one place, purely to shorten its caller — unless the
  extracted function has a name that earns its existence.
- Add `@Suppress` to silence a rule
- Raise a threshold in `detekt.yml`
- Add a path to the exemption list
- Regenerate or add entries to `detekt-baseline.xml`
- Disable or skip a failing test
- Widen an exemption's glob to cover your file

**If a finding is genuinely wrong** — the rule is misfiring, or the threshold is wrong for a
legitimate reason — say so explicitly in your report and propose the config change as a separate,
reviewable decision. Do not make the change and mention it in passing. A config change that
weakens a gate is a decision for a human.

The same applies to the specs. If implementing a spec faithfully produces bad code, that is
important information about the spec. Report it; don't quietly diverge.

---

## 4. Failure modes to watch for in yourself

These are the ways agent-assisted work degrades a codebase. They are not hypothetical.

**Monotonic growth.** Appending to a file is always locally easier than restructuring it. Over
many sessions this is how a 200-line file becomes a 2,000-line file with no single change ever
looking unreasonable. When adding to a file already near a warn threshold, restructure first.

**Baseline erosion.** Every gate has an escape hatch, and using the escape hatch always "works."
A baseline that grows, a threshold that creeps, an exemption list that lengthens — each step is
small and the end state is a quality system that reports nothing. This is why §3 lists those
actions as prohibited rather than discouraged.

**Speculative generality.** Building the flexible version of something that has one use. The
plugin architecture nothing plugs into, the strategy interface with one strategy. It reads as
thorough and costs real money in maintenance.

**Plausible-looking tests.** Tests that exercise the code and assert something true but trivial —
that a function returns non-null, that a list has the length you just gave it. They raise
coverage and catch nothing. Assert the behaviour the spec describes.

**Confident wrongness on the specific.** General patterns are well-represented in training data;
this project's particulars are not. Compose's recomposition rules, Android SAF's behaviour,
`intellij-markdown`'s CST shape, and OOXML's schema ordering requirements all have sharp edges
that plausible-sounding code gets wrong. When working near those, verify against the actual API
rather than reasoning from the pattern.

---

## 5. Reporting

When you report work as complete, include:

1. **What changed**, by module.
2. **Phase 1 results** — clean, or the specific findings and how they were resolved.
3. **Phase 2 findings** — what you looked for and what you found. "Walked the list, nothing
   applies" is an acceptable answer; silence is not.
4. **Warn-threshold crossings**, with justification or a follow-up issue.
5. **Anything you were unsure about** — spec ambiguity, an API you couldn't verify, a decision
   that could reasonably have gone the other way.

Item 5 is the most valuable thing in the report. An agent that reports uncertainty is more useful
than one that reports confidence, and the cost of an unflagged wrong guess is much higher than the
cost of a flagged one.
