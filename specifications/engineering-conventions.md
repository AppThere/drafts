# Engineering Conventions

Size limits, static analysis configuration, and the code-smell catalogue for AppThere Drafts.

Companion: [`AGENTS.md`](AGENTS.md) — the agent-facing quality pass protocol.

---

## 1. On size limits — read this before setting thresholds

Line counts are a **proxy for cohesion, and a mediocre one.** A 700-line file that does one thing
coherently is healthier than three 230-line files with a circular dependency and a shared mutable
bag of state. Enforcing a hard limit in isolation reliably produces the second outcome, because
the cheapest way to satisfy a line count is to cut a file in half at an arbitrary seam and name
the remainder `EditorStateUtils2.kt`.

So the limits below are configured as **thresholds that demand a decision**, not thresholds that
demand a split:

| Level | Meaning |
|---|---|
| **Warn** | Build succeeds. A comment appears in the PR. Someone should look. |
| **Fail** | Build fails. Either restructure, or add a documented, reviewed exemption. |

The gap between warn and fail is deliberately wide. Crossing the warn line is information, not a
verdict.

**That said, limits earn their keep in this codebase specifically**, for a reason that has nothing
to do with human readability: agent-assisted development degrades sharply with file size. Large
files consume context, edits within them are less accurate, and agents systematically prefer
appending to restructuring — so files grow monotonically unless something pushes back. The limit
is the thing that pushes back.

**Function and complexity limits correlate with real quality far better than file limits.** Weight
attention accordingly. A 600-line file of 20 focused functions is fine. A 200-line file with one
120-line function and a cyclomatic complexity of 30 is not.

---

## 2. Size and complexity thresholds

### Kotlin source

| Metric | Warn | Fail | Notes |
|---|---|---|---|
| File length | 400 | 600 | Excluding imports, licence header, and blank lines |
| Class / object body | 200 | 300 | |
| Function length | 40 | 60 | |
| **Composable function length** | 60 | 100 | Composables legitimately run longer; see §2.1 |
| Cyclomatic complexity | 10 | 15 | |
| Cognitive complexity | 12 | 20 | Better signal than cyclomatic; prefer it |
| Nesting depth | 3 | 4 | |
| Parameter count | 5 | 7 | |
| **Composable parameter count** | 7 | 10 | State hoisting legitimately widens signatures |
| Functions per class | 15 | 25 | |
| Type/constructor parameters | 3 | 5 | |

### Exemptions

Exempt by path, in configuration, not by per-file suppression:

```
**/build/**                          generated
**/generated/**                      generated
**/*.g.kt                            generated
**/test/**/fixtures/**               conformance corpora (the 652 CommonMark examples, etc.)
**/commonTest/**/*Spec*.kt           table-driven spec tests are legitimately long
:core-export-ooxml/**/StyleMap.kt    large flat mapping tables — see §2.2
```

An exemption is a config entry with a comment explaining why. An exemption is never a
`@Suppress` added to make a build pass.

### 2.1 Why composables get their own numbers

Compose UI code has a legitimately different shape from other Kotlin. A screen-level composable
nests `Scaffold` → `LazyColumn` → `items` → block composable, and each level adds a brace. Applying
generic thresholds produces constant false positives, which trains everyone — human and agent — to
ignore the tool. That's a worse outcome than having no tool.

Use `io.nlopez.compose.rules` (the Compose-specific detekt/ktlint ruleset) rather than tuning the
generic rules. It understands `@Composable` and checks the things that actually matter for Compose.

### 2.2 Flat data is not complexity

Long `when` blocks and large mapping tables — `BlockRole` → ODF style name, Unicode typographer
substitutions, the `epub:type` vocabulary — are flat, obvious, and entirely unlike a 300-line
method with branching logic. Cyclomatic complexity metrics cannot tell them apart and will flag
them loudly.

Put flat data in its own file, exempt that file by path, and keep the exemption narrow. Do not
relax the global threshold to accommodate it.

---

## 3. Toolchain

```kotlin
plugins {
    id("io.gitlab.arturbosch.detekt")            // static analysis
    id("com.diffplug.spotless")                  // formatting (wraps ktlint)
    id("com.lemonappdev.konsist")                // architecture assertions
    id("org.jetbrains.kotlinx.kover")            // coverage
    id("com.autonomousapps.dependency-analysis") // unused / misdeclared dependencies
}

dependencies {
    detektPlugins("io.nlopez.compose.rules:detekt:<version>")
    detektPlugins("io.gitlab.arturbosch.detekt:detekt-formatting:<version>")
}
```

| Tool | Job |
|---|---|
| **detekt** | Size, complexity, smells, custom rules. Primary gate. |
| **compose-rules** | Compose-specific correctness: modifier conventions, state hoisting, stability |
| **Spotless / ktlint** | Formatting. Auto-fixed, never argued about. |
| **Konsist** | Architecture assertions as tests (§5) |
| **Android Lint** | Android-target-specific issues |
| **Kover** | Coverage, reported not gated |
| **dependency-analysis** | Unused and undeclared dependencies, `api` vs `implementation` |

### Baselines

A detekt baseline is acceptable **once**, at the moment the tooling is introduced to existing
code, and it shrinks monotonically thereafter. CI asserts the baseline file has not grown. See
`AGENTS.md` §4 — regenerating the baseline is the single most common way an agent makes a quality
gate meaningless.

---

## 4. Code smell catalogue

Organised by whether a tool can catch it. The second list is where the review effort goes.

### 4.1 Mechanically detectable

Configure detekt to fail on these. Non-negotiable.

| Smell | Rule |
|---|---|
| `!!` not-null assertion | `UnsafeCallOnNullableType` |
| Swallowed exception (`catch { }`) | `SwallowedException`, `EmptyCatchBlock` |
| Catching `Exception` or `Throwable` broadly | `TooGenericExceptionCaught` |
| `runBlocking` outside tests | Custom rule |
| `GlobalScope` | `GlobalCoroutineUsage` |
| `println` / `print` | `ForbiddenMethodCall` — use the logging abstraction |
| Hardcoded user-facing string | Custom rule over `Text(`, see §4.4 |
| Magic numbers | `MagicNumber`, excluding 0/1/-1 and Compose dimension literals |
| Mutable top-level state | `TopLevelPropertyNaming` + custom rule |
| Unused private members | `UnusedPrivateMember` |
| Wildcard imports | `NoWildcardImports` |
| Platform type in `commonMain` | Konsist assertion (§5) |
| `Modifier` not the first optional parameter | compose-rules `ModifierWithoutDefault`, `ModifierNotUsedAtRoot` |
| Mutable state as a composable parameter | compose-rules `MutableParams` |
| Composable not `PascalCase` / returning a value | compose-rules naming rules |
| `remember` without keys where keys are needed | compose-rules `RememberMissing` |
| Coroutine launched in composition without `LaunchedEffect` | compose-rules |

### 4.2 Project-specific antipatterns

These are the mistakes this architecture invites. Write custom detekt rules or Konsist assertions
where possible; review for the rest.

**Editor engine**

- **God object.** `DocumentSession` will attract everything: parsing, undo, selection, autosave,
  file access. Keep it a coordinator that owns collaborators; if it exceeds 300 lines it has
  started absorbing them.
- **Primitive obsession on offsets.** Block indices, character offsets within a block, and
  absolute document offsets are three different things with the same underlying `Int`. Mixing them
  produces bugs that survive review. Use value classes: `BlockIndex`, `BlockOffset`,
  `DocumentOffset`.
- **Parsing on the main dispatcher.** Every parse and reparse goes through a dispatcher that isn't
  `Main`. Assert it in tests rather than trusting review.
- **Full-document reparse.** Any code path that reparses the whole document on an edit is a
  defect, not a slow path. Guard with a test on a 10,000-word fixture that asserts a bounded
  reparse range.

**Compose**

- **`LazyColumn` without stable keys.** In the block editor this causes focus and caret loss on
  structural edits — a correctness bug, not a performance one. `key = { it.id }`, always.
- **Unstable parameters causing recomposition.** Data classes holding `List` are unstable. Use
  `ImmutableList` from kotlinx.collections.immutable, or `@Immutable`, and verify with
  recomposition counts in tests rather than by assertion.
- **Reading state too high in the tree.** Defer state reads into lambdas (`Modifier.offset { }`,
  not `Modifier.offset(x)`) so recomposition scope stays narrow. Relevant in the editor's scroll
  and caret paths.
- **Business logic in composables.** Composables render; they don't parse, serialise, or decide.

**Multiplatform**

- **Fat `expect`/`actual`.** Platform declarations are thin adapters. Logic in an `actual` is
  logic written four times. If an `actual` exceeds ~50 lines, the abstraction is at the wrong
  level.
- **`commonMain` reaching for `java.*` or platform APIs.** Konsist assertion.
- **`Dispatchers.IO` in `commonMain`.** It doesn't exist on native. Inject dispatchers.

**Files and documents**

- **Non-atomic writes.** Every write to a user file or snapshot is temp-write plus `fsync` plus
  rename. A direct write is a defect. Custom rule on the file-writing API, plus code review.
- **Digest check bypassed.** Writes to user files go through one function that performs the §8.2
  check. Konsist: nothing outside `:platform-files` may call the raw write primitive.
- **XML built by string concatenation.** Every export backend uses a real XML writer. Custom rule
  forbidding `"<"` in string templates within `:core-export-*`.

### 4.3 Requires judgment

No tool finds these. They are the content of the review pass.

- **Speculative generality.** Interfaces with one implementation, extension points nothing extends,
  configuration nobody sets. The most common form of agent-generated waste.
- **Wrong abstraction.** Two things unified because they look alike, not because they change
  together. More expensive than duplication.
- **Feature envy.** A function that mostly manipulates another object's data belongs on that
  object.
- **Shotgun surgery.** One conceptual change requiring edits in six files means the concept isn't
  located anywhere.
- **Comments explaining *what*.** If the code needs a what-comment, rename things. Comments should
  explain *why*, and especially why-not.
- **Tests asserting implementation rather than behaviour.** A test that breaks on every refactor
  is a liability with a green checkmark.
- **Silent failure.** A `catch` that logs and continues, leaving the user with no indication
  anything went wrong. Given the "never lose work" guarantee, this is a severe category here.
- **Error messages written for the developer.** "IllegalStateException in reconcileTree" is not a
  thing to show a novelist.

### 4.4 Accessibility and i18n as lintable concerns

These are requirements, so they get gates rather than good intentions.

| Check | Mechanism |
|---|---|
| Hardcoded user-facing string | Custom detekt rule: string literal passed to `Text(`, `contentDescription =`, or a title parameter |
| Interactive element without semantics | Review checklist; partially catchable via Konsist on custom components |
| `left`/`right` padding instead of `start`/`end` | detekt `ForbiddenMethodCall` on the directional overloads |
| Touch target below 48dp | Compose UI test over the component catalogue |
| Contrast below 4.5:1 | Test over the theme token set, computed not eyeballed |
| Text sized in `dp` instead of `sp` | Custom rule |

---

## 5. Architecture assertions (Konsist)

Module boundaries stated as tests, so they fail at build time rather than in review.

```kotlin
@Test
fun `core model does not depend on Compose`() {
    Konsist.scopeFromModule("core-model")
        .files
        .assertFalse { it.hasImport { imp -> imp.name.startsWith("androidx.compose") } }
}

@Test
fun `commonMain does not use JVM APIs`() {
    Konsist.scopeFromProject(sourceSetName = "commonMain")
        .files
        .assertFalse { it.hasImport { imp -> imp.name.startsWith("java.") } }
}

@Test
fun `only platform-files writes to disk`() { /* … */ }

@Test
fun `export backends do not build XML by concatenation`() { /* … */ }

@Test
fun `composables that take a Modifier take it as the first optional parameter`() { /* … */ }
```

The dependency direction is: `app-*` → `editor-*` → `core-*`, and `core-model` depends on nothing.
Assert it; don't hope for it.

---

## 6. Definition of done

A change is finished when all of the following hold. This is the list `AGENTS.md` operationalises.

1. `./gradlew check` passes — detekt, Spotless, Konsist, tests, on every target.
2. No new detekt baseline entries. The baseline file is unchanged or smaller.
3. No new `@Suppress` without a comment giving the reason and, where applicable, the issue link.
4. Tests cover the behaviour, not the implementation. New branches have new tests.
5. No file crossed a **fail** threshold. Files that crossed a **warn** threshold are noted in the
   PR description with a one-line justification or a follow-up issue.
6. User-facing strings are in resources. New UI has semantics and has been checked at 200% font
   scale.
7. The §4.3 judgment list has been walked deliberately, not skimmed.
8. Public API changes are reflected in the relevant spec document in this directory.
