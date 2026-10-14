# Monitored scheduling comparison — October 8, 2026

Historical report. See [results.md](results.md) for the latest findings.


## Summary

The October 8 rerun confirms the snapshot-return-time regression. It repeated both candidate
matrices using their exact existing JARs and the same saved billion-record fixture as the
October 7 monitored baseline. No builds or fixture generation were needed. All samples are retained.

At the three higher budgets, tasks take 7.8–23.2% longer than the baseline and the fixed pool
takes 8.9–29.3% longer. Every one of these 15 configurations per candidate is slower by both mean
and median, and by the mean in each of the three blocks. Resource samples show no substantial
competing userspace workload, memory pressure or swapping during either candidate run. The
slowdown therefore survives a monitored rerun; background load is not a supported explanation
for dismissing it.

Mean snapshot-return times in seconds at one representative configured budget:

| Implementation | Baseline P=8 | Tasks B=24 | Fixed pool B=24 |
|---|---:|---:|---:|
| SEGMENT | 5.689 | 6.761 | 7.037 |
| DISK | 5.615 | 6.580 | 7.134 |
| HEAP | 5.176 | 5.772 | 6.178 |
| OFF_HEAP | 5.367 | 6.612 | 6.940 |
| DISK_SEGMENT | 7.517 | 8.586 | 8.642 |

`P` is writers per LongList in the baseline; `B` is total shared-pool size in the candidates.
`B = 3 × P` approximates the total configured writer budget, not equal actual concurrency.
In particular, the baseline caps writers on each individual index, while the candidates can
devote most of their pool to the large leaf index.

Tasks remain the faster of the two candidates: the fixed pool has a higher mean in all 25 cells,
with the same direction in every block for 20 cells. Several differences are small or have mixed
block signs, so this is not a claim of statistical significance in every cell. Neither candidate
meets the no-regression requirement against the range baseline.

## What was compared

| Approach | How index writes are scheduled |
|---|---|
| Range baseline | A limited number of writers per LongList, each writing a contiguous range of chunks; separate writer pool. |
| Tasks | One task per chunk using `AbstractTask` and a shared `ForkJoinPool`. |
| Fixed pool | One operation per chunk using a shared fixed `ExecutorService` and `CompletableFuture`. |

Both candidates use four logical groups: metadata, leaves, hashes and key-to-path mappings.
Index writes and data-file hard links are independent; hash-index writing and hash-file linking
wait for the hash-cache flush. Pool workers do not block waiting for their own child writes.
The candidates also share the chunk-writing refactor, so candidate-versus-baseline is not a
pool-type-only experiment. For example, HEAP now reads values directly from its assigned chunk
instead of repeatedly looking them up through the list.

The workload is a complete MerkleDB snapshot, not an isolated LongList microbenchmark:

- One billion records with 32-byte keys and 128-byte values; the saved fixture is about 258 GiB.
- Five index implementations: SEGMENT, DISK, HEAP, OFF_HEAP and DISK_SEGMENT. The selected
  implementation is used for all three indices.
- Baseline P=1/2/8/16/32; candidates B=3/6/24/48/96. Production defaults P=4 and B=12 are not measured.
- Three blocks with rotated configuration order; each cell has one warmup and three measured
  snapshots per block: nine measured samples per cell, 225 per approach.
- Hash-cache flush overlap stays enabled and final index-file forcing stays outside the timed
  snapshot call. Preparation, cache loading, validation and cleanup are also outside it.

These runs compare scheduling implementations. They do not remeasure the individual benefits
of removing force or overlapping the hash-cache flush, and they do not measure end-to-end
durable-snapshot latency.

## October 8 monitored candidate reruns

Tasks ran first, followed by the fixed pool, in one combined campaign lasting **3h 21m 18s**.
The baseline ran the previous day. This is a same-fixture comparison with resource monitoring,
not a randomized, interleaved experiment.

### Snapshot times and baseline differences

Arithmetic mean of all nine measurements per cell, in seconds. No samples were discarded.
[Task data](tasks-monitored-20261008.json) and [fixed-pool data](fixed-pool-monitored-20261008.json)
include raw samples, medians, sample standard deviations, block means, measured force times,
provenance and resource summaries.

Tasks:

| Implementation | B=3 | B=6 | B=24 | B=48 | B=96 |
|---|---:|---:|---:|---:|---:|
| SEGMENT | 7.736 | 7.615 | 6.761 | 6.723 | 6.407 |
| DISK | 7.722 | 7.351 | 6.580 | 6.571 | 6.707 |
| HEAP | 6.008 | 5.249 | 5.772 | 5.800 | 6.151 |
| OFF_HEAP | 7.802 | 7.072 | 6.612 | 6.661 | 6.597 |
| DISK_SEGMENT | 9.682 | 8.995 | 8.586 | 8.309 | 8.156 |

Fixed pool:

| Implementation | B=3 | B=6 | B=24 | B=48 | B=96 |
|---|---:|---:|---:|---:|---:|
| SEGMENT | 8.335 | 8.142 | 7.037 | 7.035 | 6.868 |
| DISK | 8.395 | 8.180 | 7.134 | 7.004 | 6.840 |
| HEAP | 6.532 | 5.919 | 6.178 | 6.410 | 6.210 |
| OFF_HEAP | 8.616 | 8.095 | 6.940 | 6.955 | 6.767 |
| DISK_SEGMENT | 10.799 | 10.192 | 8.642 | 8.348 | 8.498 |

Change versus the monitored shared-fixture baseline: `(candidate / baseline - 1) × 100`.
Positive means slower; negative means faster.

Tasks:

| Implementation | B=3 / P=1 | B=6 / P=2 | B=24 / P=8 | B=48 / P=16 | B=96 / P=32 |
|---|---:|---:|---:|---:|---:|
| SEGMENT | +5.6% | +16.5% | +18.8% | +22.0% | +17.4% |
| DISK | -21.1% | +16.0% | +17.2% | +15.0% | +20.0% |
| HEAP | -48.4% | -10.7% | +11.5% | +11.4% | +7.8% |
| OFF_HEAP | +8.4% | +11.3% | +23.2% | +20.5% | +20.8% |
| DISK_SEGMENT | +0.6% | +10.1% | +14.2% | +14.8% | +13.1% |

Fixed pool:

| Implementation | B=3 / P=1 | B=6 / P=2 | B=24 / P=8 | B=48 / P=16 | B=96 / P=32 |
|---|---:|---:|---:|---:|---:|
| SEGMENT | +13.8% | +24.6% | +23.7% | +27.7% | +25.8% |
| DISK | -14.2% | +29.1% | +27.1% | +22.6% | +22.4% |
| HEAP | -43.9% | +0.7% | +19.3% | +23.1% | +8.9% |
| OFF_HEAP | +19.7% | +27.4% | +29.3% | +25.8% | +23.9% |
| DISK_SEGMENT | +12.2% | +24.7% | +15.0% | +15.3% | +17.9% |

Tasks are slower in 22 of 25 pooled means and medians. The fixed pool is slower in 23 means and
22 medians; HEAP/B=6 is slightly slower by mean but faster by median, with mixed block signs.
Both candidates improve DISK and HEAP at the lowest budget; tasks also improve HEAP/B=6.
Those gains do not offset the consistent higher-budget regression.

Each candidate's best observed mean is still higher than the baseline's best observed mean
for every implementation. HEAP tasks are close (1.4% higher); the other task implementations
are 13.1–22.9% higher. Best fixed-pool means are 14.3–26.1% higher. These are observed minima,
not established optimal settings.

Against their own previous runs, task means change by -6.9% to +5.7% (14 of 25 lower) and fixed-pool
means by -6.2% to +3.7% (10 lower). The repeated overall ranking is more useful than small changes
in individual cells.

### Completion and resource monitoring

All 47 combined-archive checksums and all three frozen-input checksums per approach pass.
Each JAR matches its earlier campaign exactly. Settings match the shared-fixture baseline.
All four before/after inventories match the baseline's 357-entry inventory: names, sizes and
modification times, not full file-content hashes. All 150 source preparations and 600 snapshot
validation paths across both approaches completed without a benchmark failure or application
error; this includes 150 warmups.

| Observation | Tasks | Fixed pool |
|---|---:|---:|
| Block A | 34m 07s | 32m 41s |
| Block B | 34m 04s | 32m 39s |
| Block C | 34m 23s | 32m 54s |
| Whole approach, including orchestration | 1h 42m 46s | 1h 38m 26s |
| Process/CPU interval samples | 204 | 196 |
| Memory/swap/I/O interval samples | 205 | 196 |
| Minimum available RAM | 111.5 GiB | 111.5 GiB |
| Swap-in / swap-out | None | None |
| Largest other userspace CPU sample | 15.8% of one core | 12.3% of one core |

The largest other userspace consumer was the system-management agent in both runs; these peaks
are below 0.5% of the 32-logical-CPU machine. Sampled Java process lifetimes match the benchmark
drivers and forks, with no third simultaneous Java process. Kernel writeback activity accompanies
the benchmark's writes and is not itself evidence of another user's workload.

Samples were taken every 30 seconds; initial historical reports were excluded. Small scheduling
drift explains the different task-run sample counts. Sampling cannot rule out every short-lived
process or attribute every disk operation, but it provides no evidence of material background
interference. Average user-plus-system CPU was 11.3% for tasks and 12.0% for the fixed pool, versus
8.0% in the baseline. That is an observation, not proof of the slowdown's cause.

### Why the shorter fixed-pool run does not mean faster snapshots

File forcing happens after the timed snapshot returns. Pairing each measured snapshot with its
own force event, excluding warmups, gives the following equal-weight averages across all 25 cells:

| Approach | Snapshot call | Subsequent file forcing | Sum |
|---|---:|---:|---:|
| Monitored baseline | 6.674 s | 9.202 s | 15.877 s |
| Monitored tasks | 7.105 s | 8.577 s | 15.682 s |
| Monitored fixed pool | 7.603 s | 7.412 s | 15.015 s |

The fixed pool returns later but spends less time forcing files afterward in this run. Waiting
can move across the snapshot-return boundary, so campaign wall time and timed snapshot performance
must not be confused. The sum is only diagnostic: it excludes other lifecycle work and directory
durability, and is not an end-to-end durable-snapshot score. The agreed snapshot-return metric
still regresses.

### Interpretation and next step

There is no need to repeat the full matrices again merely to check for background load, or to
move this comparison to a Mac. The result has reproduced with matching artifacts and fixtures
and monitoring for all three approaches.

The code offers plausible causes, not a proven root cause: removing each list's writer cap can
increase contention on the large leaf-index file; per-chunk dispatch changes write ordering; and
the fixed pool's FIFO queue can delay later hash-index work behind already submitted leaf chunks.
Contiguous ranges did not combine chunks into larger writes in the baseline, so fewer/larger
physical writes must not be assumed as an explanation.

This result led to the [October 13 pre-sizing and bounded-chain experiments](f1-f2-20261013.md).
No production implementation was selected by this report.

### Artifact identity

- Combined run: `comparison-monitored-20261008T181709Z-4045424`, completed with exit code 0.
- Tasks reuse the October 6 JAR; fixed pool reuses the October 7 JAR.
- Archive SHA-256:
  `7c39f9068d4857891223975df4810e7f388f034c26ffd1a6177b42b519a60d5d`.
- Exact input hashes and all samples are in the linked sanitized JSON files.
  Raw logs, machine/user identifiers and operational paths remain local.
