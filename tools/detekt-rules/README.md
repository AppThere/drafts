# Custom detekt rules

The project's own rules, for the parts of `engineering-conventions.md` the stock ruleset cannot
express. Each has tests in both directions in `src/test/kotlin` — a violation that must be
reported, and a clean case that must not be.

## Editing a rule: stop the Gradle daemon first

```sh
./gradlew --stop && ./gradlew detekt
```

**A running Gradle daemon caches these rule classes and will keep applying the previous version of
a rule after you edit it.** The daemon's worker classloader is keyed on the plugin classpath
*paths*, and the jar path does not change when its contents do — so `detekt` happily reruns,
against stale bytecode.

This is worth knowing because of how it presents: the unit tests pass (they run in the test JVM,
not the daemon's worker), the jar on disk is demonstrably current, `--rerun-tasks` changes
nothing, and `detekt` keeps reporting findings from a version of the rule that no longer exists.
Every signal says the code is right, and it is — it just is not the code being run.

Symptom checklist, in the order they mislead you:

| What you see | What it means |
|---|---|
| `:tools-detekt-rules:test` passes | The rule logic is correct |
| The jar's timestamp is newer than the source | The jar really was rebuilt |
| `--rerun-tasks` reports the same findings | The task reran; the classes did not reload |
| A fresh daemon reports nothing | It was the classloader all along |

## Why the rules avoid type resolution

detekt's type-resolved tasks are not wired for Kotlin Multiplatform source sets, so these rules
match on syntax alone. That is why `HardcodedUserFacingString` requires a `@Composable` ancestor
rather than trusting a parameter name: `:core-model` has `CodeBlock(text = …)`, `Link(title = …)`
and an inline IR node called `Text`, and by short name alone those are indistinguishable from the
Compose declarations of the same spelling.
