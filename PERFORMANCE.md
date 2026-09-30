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

## A correction to how typing is measured

The Phase 4 figures below were taken at 40 keystrokes after 10 of warm-up. Repeated runs of
identical code spread across about 800 microseconds at that sample size, which straddles the frame
budget the test asserts -- so the same code passed and failed depending on the run, and the "4% of
the budget left" recorded for Phase 4 was mostly an under-warmed estimate rather than a real margin.

From Phase 5 the sample is 120 keystrokes after 60 of warm-up. The spread falls to about 600
microseconds and the median settles around 6,100. **The Phase 4 and Phase 5 typing figures are not
comparable**: the budget did not move and neither did the code, only the estimate.

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

### Phase 5 — 2026-09-30

Fixture: 62,724 code units, 512 blocks, 10,000+ words. Same machine.

| Measurement | Value | Budget | Margin |
|---|---|---|---|
| Reparse window per keystroke | 216 code units (0.3% of document) | bounded | — |
| Engine time per edit (median) | 516 us | 8,333 us | 94% spare |
| Typing latency, attributable (median of four runs) | 6,083 us | 8,333 us | **27% spare** |
| Scroll per screen, attributable (median) | 2,916 us | 8,333 us | 65% spare |

Typing measured at the new sample size; see the correction above before comparing with Phase 4.
Four runs gave 5,866 / 6,184 / 5,828 / 6,455.

Phase 5 added 4.2's reveal cross-fade, which is the first animation this application has. Measured
with and without it at the old sample size: 8,304 against 8,187 microseconds, a difference of about
120 microseconds inside a spread four times that. It costs nothing that can be distinguished from
noise.

Engine and scroll figures are unchanged.
