# Performance gate log

`IMPLEMENTATION-PLAN.md` makes performance a recurring gate: *"End of every phase from 2 onward.
10,000-word fixture, slowest target device. Typing latency, scroll, reparse bounds. Regressions
block."*

Regressions cannot block against nothing. Until this file existed each phase measured, printed a
number to a test log, and forgot it — so "regressions block" was enforced only against the absolute
frame budget the tests assert, never against the phase before. This is the record that makes the
comparison possible.

## How to reproduce

```
./gradlew :editor-engine:jvmTest -i --tests '*GateReportTest*'      # reparse bounds
./gradlew :editor-ui:jvmTest -i --tests '*TypingLatencyTest*'       # typing latency
./gradlew :editor-ui:jvmTest -i --tests '*ScrollLatencyTest*'       # scroll
```

Each prints its own measurements. The numbers below are copied from those runs.

## What the numbers mean

The frame budget is 8,333 microseconds — one frame at 120Hz, which is the target in
`appthere-drafts.md` §10.3.

Two of the three measurements subtract a **harness floor**. A Skiko test composition costs most of
a frame doing nothing at all, so a raw figure compared against the budget measures the harness. The
floor is measured in the same run — a trivial recomposition for typing, a plain `LazyColumn` of the
same length for scroll — and subtracted. *Attributable* is the figure to compare across phases.

Scroll also discards two full sweeps before measuring. The first sweep through the document is
about three times slower than the third; comparing unwarmed early samples against warmed later ones
reports the editor slowing down as the reader scrolls, which is the reverse of what happens.

## Measurements

Machine: this development machine (Linux, JVM desktop target). Not "the slowest target device" the
gate asks for — that is a phone, and no phone has run this yet. These numbers are a baseline for
detecting regressions between phases, not evidence that the budget is met on the slowest hardware.

### Phase 4 — 2026-09-28

Fixture: 62,724 code units, 512 blocks, 10,000+ words.

| Measurement | Value | Budget | Margin |
|---|---|---|---|
| Reparse window per keystroke | 216 code units (0.3% of document) | bounded | — |
| Blocks rebuilt per keystroke | 3 | — | — |
| Engine time per edit (median) | 516 us | 8,333 us | 94% spare |
| Typing latency, attributable (median) | 8,011 us | 8,333 us | **4% spare** |
| Scroll per screen, attributable (median) | 2,916 us | 8,333 us | 65% spare |

Engine and reparse figures are unchanged from the Phase 2 spike, so Phases 3 and 4 cost the engine
nothing.

**Typing latency has 4% of the budget left**, and that is the number to watch. Of the 8,011
microseconds attributable, 6,347 are engine work plus recomposition with no text field involved, so
the cost is in reparse-and-recompose rather than in anything the field does. It passes; it would not
survive much being added to that path.

Scroll was measured for the first time in Phase 4. There is no earlier figure to compare against,
and the gate has been passing without it since Phase 2.

## Why these numbers are not assertions

The scroll test asserts only that the editor does not cost an *order* more than a plain list. It
cannot assert the budget, because these runs happen inside a parallel Gradle build: the same code
that measures 2,916 microseconds attributable on a quiet machine measures 17,377 under `check`, and
the harness floor does not scale with it, so neither a microsecond budget nor a ratio survives the
contention.

That is what this file is for. "Regressions block" is a human comparing this table against the next
phase's run on a quiet machine -- the same kind of human the gate already assumes when it says
"slowest target device", which is a phone that no automated check is holding.

The typing test *does* assert its budget, and passes with 4% to spare. That margin is thin enough
that it will eventually fail for reasons that have nothing to do with the change in front of it.
Worth watching.
