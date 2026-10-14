# MerkleDB snapshot scheduling results

[Team discussion Q&A and recommendation](decision-faq.md)

## What the results mean

F1 and F2 each helped when tested separately. That statement compares each modified approach
with **the same approach without either fix**. It does not mean every modified approach beats
the baseline, especially after the baseline also receives F1.

For example, tasks + F1 on SEGMENT has the same reported mean of **5.567 s** in all three comparisons:

- **15.4% faster than tasks without fixes**, which took 6.583 s.
- **3.0% faster than the unchanged baseline**, which took 5.742 s.
- **5.5% slower than baseline + F1**, which took 5.279 s.

The previous summary made these different references too easy to confuse. Rechecking all
405 measurements against the original JMH files found no numerical correction needed.
The tables below show the complete F1/F2 comparison, with the reference stated for each table.

## What was measured

- **Baseline:** one task per contiguous chunk range, at most eight writers per index.
- **Tasks:** one task per chunk in a shared ForkJoinPool with parallelism 24.
- **Fixed pool:** one task per chunk in a shared fixed thread pool with 24 workers.
- **F1 — pre-sizing:** set each index file's final logical length before chunk writes.
  This does not reserve all physical storage in advance.
- **F2 — bounded chains:** up to eight chains per index; each writes one chunk, then submits the next
  chunk in its contiguous range. It still schedules one task per chunk, unlike the baseline.

Names without F1/F2 mean that approach's unchanged control. **All variants, including the
baseline, already have hash-flush overlap enabled and forcing disabled inside the snapshot call.**
These experiments do not remeasure those two earlier optimizations.

All runs reused the same saved one-billion-record fixture, with 32-byte keys and 128-byte values.
The selected LongList implementation is used for all three indices. Each result is the arithmetic
mean of nine measurements: three JVM forks with three measured snapshots each.

Every result cell below is **mean seconds (percentage change)**. Percentages use unrounded means:
`(candidate mean / reference mean - 1) × 100`. **Negative is faster; positive is slower.**
Compare within an implementation's column, not across different implementations.

**† Tasks + F1 + F2 was measured on October 14; every other variant on October 13.**
The combined run reused the exact task JAR and fixture, but did not rerun the controls.
Comparisons involving † are therefore cross-day, not same-run comparisons.

## 1. All variants versus the unchanged baseline

**Reference for every percentage: Baseline, with neither F1 nor F2.**
This table answers whether each version beats the implementation we started with.

| Variant | SEGMENT | DISK | HEAP | OFF_HEAP | DISK_SEGMENT |
|---|---:|---:|---:|---:|---:|
| Baseline | 5.742 s (0.0%) | 5.735 s (0.0%) | 5.283 s (0.0%) | 5.756 s (0.0%) | 7.428 s (0.0%) |
| Baseline + F1 | 5.279 s (-8.1%) | 5.338 s (-6.9%) | 4.802 s (-9.1%) | 5.308 s (-7.8%) | 7.028 s (-5.4%) |
| Tasks | 6.583 s (+14.6%) | 6.636 s (+15.7%) | 5.796 s (+9.7%) | 6.646 s (+15.5%) | 8.290 s (+11.6%) |
| Tasks + F1 | 5.567 s (-3.0%) | 5.325 s (-7.1%) | 5.497 s (+4.1%) | 5.509 s (-4.3%) | 6.683 s (-10.0%) |
| Tasks + F2 | 5.695 s (-0.8%) | 5.659 s (-1.3%) | 5.131 s (-2.9%) | 5.741 s (-0.3%) | 7.100 s (-4.4%) |
| Tasks + F1 + F2 † | 5.349 s (-6.8%) | 5.284 s (-7.9%) | 4.862 s (-8.0%) | 5.295 s (-8.0%) | 6.856 s (-7.7%) |
| Fixed pool | 7.003 s (+22.0%) | 6.975 s (+21.6%) | 6.254 s (+18.4%) | 7.062 s (+22.7%) | 8.509 s (+14.6%) |
| Fixed pool + F1 | 5.704 s (-0.7%) | 5.662 s (-1.3%) | 5.656 s (+7.1%) | 5.544 s (-3.7%) | 7.187 s (-3.2%) |
| Fixed pool + F2 | 5.733 s (-0.2%) | 5.598 s (-2.4%) | 5.324 s (+0.8%) | 5.661 s (-1.7%) | 7.177 s (-3.4%) |

Without fixes, both shared-pool approaches are slower than the baseline on every implementation.
Tasks + F1 has a lower mean than the unchanged baseline on four implementations; HEAP is the
exception. Tasks + F2 and the combined version have lower means on all five, though some F2
differences are too small to call reliable wins.

## 2. Did the fixes help each approach?

**Reference for each row: the same approach without F1 or F2, named in the second column.**
These percentages measure improvement within an approach, not its position against the baseline.

| Variant | Reference for % | SEGMENT | DISK | HEAP | OFF_HEAP | DISK_SEGMENT |
|---|---|---:|---:|---:|---:|---:|
| Baseline + F1 | Baseline | 5.279 s (-8.1%) | 5.338 s (-6.9%) | 4.802 s (-9.1%) | 5.308 s (-7.8%) | 7.028 s (-5.4%) |
| Tasks + F1 | Tasks | 5.567 s (-15.4%) | 5.325 s (-19.8%) | 5.497 s (-5.1%) | 5.509 s (-17.1%) | 6.683 s (-19.4%) |
| Tasks + F2 | Tasks | 5.695 s (-13.5%) | 5.659 s (-14.7%) | 5.131 s (-11.5%) | 5.741 s (-13.6%) | 7.100 s (-14.4%) |
| Tasks + F1 + F2 † | Tasks | 5.349 s (-18.7%) | 5.284 s (-20.4%) | 4.862 s (-16.1%) | 5.295 s (-20.3%) | 6.856 s (-17.3%) |
| Fixed pool + F1 | Fixed pool | 5.704 s (-18.6%) | 5.662 s (-18.8%) | 5.656 s (-9.6%) | 5.544 s (-21.5%) | 7.187 s (-15.5%) |
| Fixed pool + F2 | Fixed pool | 5.733 s (-18.1%) | 5.598 s (-19.7%) | 5.324 s (-14.9%) | 5.661 s (-19.8%) | 7.177 s (-15.7%) |

Yes: F1 helps all three approaches, and F2 helps both shared-pool approaches. Each separate-fix
improvement also appears in all three October 13 block means, across all five implementations.
The combined row is a separate October 14 measurement, not the sum of the individual gains.

## 3. Do the alternatives beat baseline + F1?

**Reference for every percentage: Baseline + F1.**
This is the stronger comparison because the baseline receives the pre-sizing fix too.

| Variant | SEGMENT | DISK | HEAP | OFF_HEAP | DISK_SEGMENT |
|---|---:|---:|---:|---:|---:|
| Baseline + F1 | 5.279 s (0.0%) | 5.338 s (0.0%) | 4.802 s (0.0%) | 5.308 s (0.0%) | 7.028 s (0.0%) |
| Tasks + F1 | 5.567 s (+5.5%) | 5.325 s (-0.2%) | 5.497 s (+14.5%) | 5.509 s (+3.8%) | 6.683 s (-4.9%) |
| Tasks + F2 | 5.695 s (+7.9%) | 5.659 s (+6.0%) | 5.131 s (+6.8%) | 5.741 s (+8.2%) | 7.100 s (+1.0%) |
| Tasks + F1 + F2 † | 5.349 s (+1.3%) | 5.284 s (-1.0%) | 4.862 s (+1.2%) | 5.295 s (-0.2%) | 6.856 s (-2.5%) |
| Fixed pool + F1 | 5.704 s (+8.1%) | 5.662 s (+6.1%) | 5.656 s (+17.8%) | 5.544 s (+4.5%) | 7.187 s (+2.3%) |
| Fixed pool + F2 | 5.733 s (+8.6%) | 5.598 s (+4.9%) | 5.324 s (+10.9%) | 5.661 s (+6.6%) | 7.177 s (+2.1%) |

- **Tasks + F1:** lower mean for DISK_SEGMENT, effectively tied for DISK, slower for the other three.
- **Tasks + F2 and both fixed-pool fixes:** higher means than baseline + F1 on all five.
- **Tasks + F1 + F2:** close to baseline + F1 on all five. It has not established a clear overall win.
  Compared with tasks + F1 alone, combining the fixes lowers four means but raises DISK_SEGMENT
  from 6.683 s to 6.856 s (+2.6%). It is not uniformly better than F1 alone.

In short: **the fixes helped, but so did fixing the baseline.** The combined task version closes
most of the gap to that improved baseline; the evidence does not yet pick a clear winner between them.

## Limits and verification

- All 405 measurements from 135 original JMH result files were rechecked. No samples were removed.
  Times and percentages above are calculated from those raw measurements, not rounded table values.
- Only snapshot return time is measured. Setup, validation, forcing files to storage and cleanup
  are outside it. This is not a durable-write timing comparison.
- The combined comparisons cross days. Small mean differences can reverse when comparing medians:
  SEGMENT versus baseline + F1, for example. Treat differences of a few percent cautiously.
- Both runs were monitored. Neither shows sustained competing load or memory pressure, but periodic
  system-management activity was present. Sampling cannot rule out every short-lived interference.
- Fixed pool + F1 + F2 was **not measured**. F2 is a shared-pool change, so there is no baseline + F2
  variant. This experiment tests the selected worker budgets above, not every budget or the defaults.

## Detailed evidence and earlier runs

The tables above use only the October 13/14 experiments. Earlier full-matrix runs remain below
for context; their baseline values are not mixed into these percentages.

- [October 14: combined F1 + F2 results and machine activity](f1-f2-combined-20261014.md)
  · [raw measurements and calculated comparisons](f1-f2-combined-20261014.json)
- [October 13: separate F1/F2 experiments](f1-f2-20261013.md)
  · [raw measurements and calculated comparisons](f1-f2-20261013.json)
- [October 8: monitored full-matrix comparison](monitored-comparison-20261008.md)
- [October 7: baseline rerun on the shared fixture](shared-fixture-baseline-20261007.md)
- [October 7: original fixed-pool run](fixed-pool-20261007.md)
- [October 6: original task run and retry](tasks-20261006.md)
- [October 2: original range baseline](baseline-20261002.md)

The dated reports retain each run's evidence and limitations. Raw logs and operational
identifiers remain local. Saved fixtures have not been removed.
