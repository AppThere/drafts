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

### On a device

The three above run on this machine's JVM. From Phase 6 there is a second measurement, on Android,
because the gate has always said "slowest target device" and the desktop is not one. It uses the
platform's own frame accounting rather than a test harness, so what it reports is what the reader
sees: whole frames, missed or not.

Put the same 10,000-word fixture on the device and open it from a file manager — the fixture is
`GateFixture.tenThousandWords()`, 62,724 code units — then, with the caret in a paragraph near the
top:

```
WARM=$(python3 -c "print('abcdefghij'*6)")      # 60 keystrokes of warm-up
BURST=$(python3 -c "print('abcdefghij'*12)")    # 120 measured, as the JVM gate uses

adb shell input text "$WARM"
adb shell dumpsys gfxinfo com.appthere.drafts reset
adb shell input text "$BURST"
adb shell dumpsys gfxinfo com.appthere.drafts | grep -E "Total frames|Janky|percentile"
```

Repeat it in a three-line document. That pairing is the measurement: the absolute figures belong to
the device's refresh rate and its GPU, and the *difference* between a long document and a short one
belongs to this application.

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

### Phase 6 — 2026-10-02

Fixture: 62,724 code units, 512 blocks, 10,000+ words. Same machine, quiet (no emulator running).

| Measurement | Value | Budget | Margin |
|---|---|---|---|
| Reparse window per keystroke | 216 code units (0.3% of document) | bounded | — |
| Blocks rebuilt per keystroke | 3 | — | — |
| Engine time per edit (median) | 520 us | 8,333 us | 94% spare |
| Typing latency, attributable (median) | 6,385 us | 8,333 us | **23% spare** |
| Scroll per screen, attributable (median of four runs) | 4,520 us | 8,333 us | 46% spare |

Reparse, blocks rebuilt and engine time are unchanged from Phase 5. Typing is 300 microseconds
above Phase 5's 6,083, which is inside the 600-microsecond spread that measurement has.

**The scroll figure needs a correction, not an investigation.** Phase 6 reads 4,270 / 4,520 / 4,612
/ 4,936 across four runs, against 2,916 recorded for Phase 5 — a 55% jump with nothing to attribute
it to: `:editor-ui` has no source change at all since Phase 5 (`git diff 989ecbf..HEAD -- editor-ui`
is empty), and `ScrollLatencyTest` exercises `BlockEditor` directly rather than through anything
Phase 6 touched. Phase 4 and Phase 5 both record *exactly* 2,916, which for a wall-clock median
across two separate runs is not plausible: the Phase 5 entry was carried over, under the heading
"Engine and scroll figures are unchanged", rather than re-measured. So the comparison to make is
Phase 6 against Phase 4's machine state, which is not recoverable. From here the four-run spread is
recorded so there is something a later phase can actually be compared against.

### Phase 6 — on an Android device, 2026-10-02

The first time any of this has been measured on Android. Device: the `pixel_fold` emulator
(android-36, x86_64, 2208x1840 at 420dpi), which is **not** the slowest supported device — it is
the fastest thing available here, with a software GPU and a 60Hz display. The acceptance criterion
asks for a real phone and is not met by this.

What a 60Hz display means for these numbers: 16ms is one frame and the floor. The 120Hz budget the
JVM gates assert against is invisible underneath it.

| Measurement | 10,000-word document | Three-line document |
|---|---|---|
| Frames rendered during the burst | 64 | 39 |
| Janky frames | 20.3% | 23.1% |
| 50th percentile frame | 20 ms | 16 ms |
| 90th percentile frame | 32 ms | 16 ms |
| 99th percentile frame | 32 ms | 16 ms |

The short document never misses a frame: every percentile is 16ms, the refresh period. The long one
is at one frame on the median and two at the 90th. So on this hardware a 10,000-word document costs
about one extra 60Hz frame in the worst tenth of keystrokes, and nothing on the median.

The janky percentage is the same for both and therefore says nothing about document length; it is
the emulator's own rendering, confirmed by its GPU percentiles, which report 4,950ms at the 95th
for both documents and are plainly an artefact.

**What this does and does not establish.** It establishes that the architecture's O(viewport) claim
survives contact with Android: a document 200 times longer than the other costs one extra frame at
the 90th percentile, not 200 times anything. It does not establish that §10.3's budget is met on the
slowest supported device, because no such device has run it.
### Scrolling, 2026-10-03 — a different machine, and a cheaper row

**A new machine, so not comparable with the figures above.** From here the JVM gates run on an
aarch64 Chromebook (Crostini, 6.6 GB). The Phase 6 figures came from the machine that ran the
`pixel_fold` emulator, which this one cannot run. Its floor is about 11,000–14,000 microseconds
against the earlier machine's few thousand, so only comparisons within this section mean anything.
Measured with a fresh Gradle daemon (`./gradlew --stop` first): a daemon left running all day had
grown to 4.7 GB and a full core, and inflated every timing.

`ScrollLatencyTest` now composes the editor inside `DraftsTheme`, as the application does.

| Scroll per screen, attributable | Four runs | Median |
|---|---|---|
| Before | 31,488 / 34,085 / 33,516 / 33,655 | ~33,600 us |
| Measure only the state not on screen | 20,069 / 31,892 / 21,772 / 31,327 | ~26,500 us |
| … and build the font families once per window | 14,310 / 17,908 / 18,202 / 14,728 | ~16,300 us |
| … and one reveal cross-fade for the editor, not one per row | 10,458 / 12,654 / 10,028 / 11,951 | ~11,200 us |
| As committed: a measurer per row again, not one shared | 18,458 / 9,899 / 19,776 / 29,432 / 11,593 / 10,161 / 14,111 / 12,369 | **~13,000 us** |

The rows above the last shared one text measurer across the editor; it took a `CompositionLocal`
the lint allowlist does not permit, and it only helped blocks scrolled back into view, which the
gate never measures. The last row is the code as committed. Its first four runs scatter from 9,899
to 29,432 -- the floor moved from 10,114 to 16,505 between them -- so eight are recorded, and the
difference from ~11,200 is within that spread.

Found with a JFR profile of the gate at 1ms sampling. Of the time spent composing a row, the
per-row `Crossfade` was 36% and building font families 26% (ten composable resource lookups per
`proseStyleOf`, three or four of those per row); both were also paid again tearing the row down,
because each was state to deactivate. `reservedHeightOf` measured both states on every row, two text
layouts on top of the row's own.

**On the device it made no difference a reader would see.** Release builds compiled ahead of time
(`pm compile -m speed`), on ARC on the same Chromebook (API 33, 60Hz), scrolling the 788-line
`appthere-drafts.md` by hand:

| | Before | After |
|---|---|---|
| Janky frames | 31% | 36% |
| 50th / 90th / 99th percentile | 21 / 32 / 61 ms | 22 / 32 / 89 ms |
| Frames slow on the UI thread | 19% | 17% |
| Frames slow issuing draw commands | 20% | 26% |
| GPU 90th percentile | 6 ms | 6 ms |

The UI thread got cheaper, as the gate says. What holds frames back on ARC is issuing draw commands
on the render thread, which this change did not touch and the JVM gate does not measure. One hand
scroll each, so the differences in the janky share and the 99th percentile are within what two
scrolls differ by; neither is evidence of a regression. A debug build is worse than either (90th
percentile around 93ms), so the lag a reader notices in a development build is mostly the build.

Not yet tried: a render layer per row, so a scroll moves recorded rows rather than re-issuing their
text. And ARC's virtualised GPU is not a phone; the slowest supported device has still not run this.
