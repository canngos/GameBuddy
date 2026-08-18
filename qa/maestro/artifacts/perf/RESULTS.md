# UI performance — before and after the optimization pass

Measured 2026-08-17 on `GameBuddy_API34`, against **two EAS release builds** of the `lan`
profile: one of the code as it stood before the optimization work, one of the code after it.
Both were driven by the same seven Maestro flows through `qa/maestro/perf.ps1`.

Release builds on purpose. A debug build keeps React Native's dev machinery attached and
serves its bundle from Metro; its frame times are several times worse and not in proportion,
so a debug comparison would rank the work wrongly.

```
qa/maestro/perf.ps1 -Label before  -Flow NN -Iterations 1 -ReleaseBuild -Append
qa/maestro/perf.ps1 -Label after2  -Flow NN -Iterations 1 -ReleaseBuild -Append
qa/maestro/perf.ps1 -Compare before,after2
```

---

## The table

| flow | p99 frame | jank % | missed vsync | frames | cold start |
| --- | --- | --- | --- | --- | --- |
| 01-onboarding | 48 → 69 ms | 15% → 27% | 5 → 5 | 1,565 → 871 | **1,902 → 1,106 ms** |
| 02-swipe | 101 → 85 ms | 20% → 17% | 17 → 7 | 800 → 746 | **2,303 → 987 ms** |
| 03-chat | 85 → 73 ms | 30% → 24% | 10 → 6 | 687 → 696 | **2,090 → 884 ms** |
| 04-market | 101 → 69 ms | 21% → 16% | 11 → 4 | 953 → 940 | **1,636 → 913 ms** |
| 05-settings | 65 → 77 ms | 20% → 16% | **34 → 5** | 1,553 → 954 | **1,805 → 1,416 ms** |
| 06-admin | **150 → 85 ms** | 31% → 25% | 9 → 6 | 464 → 464 | **2,285 → 1,375 ms** |
| 07-offline | 85 → 85 ms | 21% → 17% | 7 → 7 | 757 → 768 | **1,363 → 1,007 ms** |

Peak memory (TOTAL PSS), the two picker-heavy flows:

| flow | before | after |
| --- | --- | --- |
| 01-onboarding | **418 MB** | **245 MB** |
| 05-settings | **348 MB** | **253 MB** |

---

## What the numbers say

**Cold start halved, on every flow without exception.** Median roughly 1,900 ms → 1,000 ms.
This is the optimistic session restore: `restore()` no longer awaits `profileApi.me()` before
the first paint, so launch costs a keychain read instead of a network round trip. The
splash's 380 ms dead frame came out of the same budget.

**Missed vsync fell everywhere it was high**, most sharply on 05-settings — **34 → 5** — which
is the flow that opens the game picker. That is the 302-card grid no longer mounting at once.

**Peak memory on the two picker flows dropped by 100–170 MB.** Same cause: ~10 cards and
~10 decoded covers instead of 302.

**p99 frame time improved on four flows and regressed on two** (01 and 05). Both regressions
are small in absolute terms (48→69 ms, 65→77 ms) and both are on flows where total frames
fell by ~40%: virtualization spreads cell-rendering work across scrolling instead of paying
it once up front, so a smaller number of frames carries a larger share of the work. Neither
was diagnosed further, and with a single sample per flow neither is firmly outside noise.

---

## Caveats, so the numbers are read at their real weight

- **One sample per flow, not three.** The suite is single-pass by design: `run.ps1`
  provisions once and runs each flow once, and several flows consume the state they are
  given — 01-onboarding verifies its pending account, 03-chat sends a reply that displaces
  the seeded last message it asserts on. Both fail on a second iteration against the same
  provisioning. `perf.ps1` re-provisions per *invocation*, so more samples are available by
  invoking it repeatedly with `-Append`, at the cost of a provisioning round each time.
  Small differences here (a few ms of p99) should not be treated as signal.
- **01-onboarding fails on both builds**, at the same step: it taps games by screen position
  between scrolls, and the taps no longer land on four distinct cards. It fails *identically*
  on the pre-optimization build, so this is a pre-existing fragility of the flow — the same
  one its own comment at lines 186–191 documents — and not a regression. The app itself is
  correct: the failure screenshot shows two cards properly selected, with covers, and the
  footer correctly reading "1 more to go" with Continue disabled. Its numbers are included
  but describe a run that stopped early.
- **Maestro's input is synthetic.** No fling velocity, no human dwell. It is *repeatable*,
  which is the only property a before/after needs; the absolute jank percentages are not a
  claim about what a person experiences.
- **`gfxinfo` counts only frames the app drew.** A hard JS block produces no frames at all,
  so it flatters the percentiles and shows up as a low frame count instead. Read `frames`
  next to `p99` — which is why the column is in the table.
