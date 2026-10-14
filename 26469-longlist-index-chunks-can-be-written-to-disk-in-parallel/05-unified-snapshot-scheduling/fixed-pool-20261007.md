# Original fixed ExecutorService run (October 7)

Historical report. See [results.md](results.md) for the latest findings.


Historical run without continuous monitoring; superseded by the [monitored rerun](monitored-comparison-20261008.md).

The shared fixed-pool implementation completed the same matrix on October 7, 2026. It uses
CompletableFuture completion instead of the custom task classes, while keeping one operation per
index chunk and the four snapshot groups. `B` is the same `snapshotThreads` setting as in the task
run: 3, 6, 24, 48 and 96. The default of 12 was not measured.

The run reused the task campaign's exact saved fixture. Its 357-entry file inventory matches the
task run and is unchanged after this run. The input and effective settings match too. Inventories
check relative file names, sizes and modification times, not file-content hashes. All 225 measured
snapshots, 75 warmups and 75 restored sources passed the benchmark checks.

## Snapshot times

Arithmetic mean of all nine measurements per configuration, in **seconds**. Lower is better.
No samples were discarded. [The result data](fixed-pool-20261007.json) includes every raw sample,
medians, sample standard deviations, block means and both comparisons below.

| Implementation | B=3 | B=6 | B=24 | B=48 | B=96 |
|---|---:|---:|---:|---:|---:|
| SEGMENT | 8.881 | 7.898 | 7.137 | 6.805 | 6.833 |
| DISK | 8.738 | 8.251 | 7.109 | 6.827 | 6.860 |
| HEAP | 6.697 | 5.719 | 6.095 | 6.333 | 6.093 |
| OFF_HEAP | 8.501 | 7.917 | 7.130 | 6.707 | 6.545 |
| DISK_SEGMENT | 10.677 | 10.479 | 8.639 | 8.357 | 8.505 |

The lowest observed means are at B=48 for SEGMENT, DISK and DISK_SEGMENT, B=6 for HEAP, and B=96
for OFF_HEAP. Only HEAP/B=6 is the fastest setting in all three fixed-pool blocks. Other rankings
vary, so these are not established optimal settings.

## Comparison with tasks

Both campaigns use the same saved fixture, workload and pool-size values. They ran on different
days, not as interleaved paired trials, and neither records continuous machine utilization.
Pool type and task/completion code both changed; the results do not isolate the executor alone.

Change in mean snapshot time: `(fixed pool / task - 1) × 100`.
Positive means slower; negative means faster.

| Implementation | B=3 | B=6 | B=24 | B=48 | B=96 |
|---|---:|---:|---:|---:|---:|
| SEGMENT | +12.9% | +6.3% | +9.3% | +3.7% | +3.0% |
| DISK | +14.0% | +10.8% | +8.0% | +2.9% | +2.0% |
| HEAP | +10.9% | +6.1% | +7.4% | +8.4% | +4.7% |
| OFF_HEAP | +13.4% | +6.0% | +7.8% | +1.3% | -1.2% |
| DISK_SEGMENT | +6.8% | +8.5% | +3.5% | +2.9% | +4.3% |

Fixed-pool means are higher in 24 of 25 configurations, and medians are higher in 23 of 25.
Seventeen configurations are slower in all three block comparisons. All five B=24 configurations
are slower in every block. The sole lower pooled mean, OFF_HEAP/B=96, has block changes of
-3.1%, +2.8% and -3.5%; it does not establish a reliable advantage.

## What the phase logs show

The cached-hash file-write milestone is reached at roughly the same time in both implementations.
Most of the additional fixed-pool time follows that milestone. For example, SEGMENT/B=24 reaches
it after 0.950 seconds with tasks and 0.945 seconds with the fixed pool; the remaining logged
snapshot work takes 5.580 and 6.191 seconds respectively. A delayed hash flush is therefore not
supported as the explanation for that slowdown.

These are log timestamps, not a profiler trace. They do not identify which index finishes last or
prove the cause. JMH also includes the enclosing builder call: SEGMENT/B=3 has about 147 ms per
snapshot on average beyond its fixed-pool data-source log interval, concentrated in three samples.
Those samples remain in the results. Phase means and their scope are preserved in the result data.

## Historical comparison with the original baseline

This table preserves the earlier comparison with the separately generated October 2 fixture.
Use the [shared-fixture comparison](shared-fixture-baseline-20261007.md) for the later historical result.

The same approximate `B = 3 × P` mapping and baseline fixture limitations apply here.
Change in mean snapshot time: `(fixed pool / baseline - 1) × 100`.
Positive means slower; negative means faster.

| Implementation | B=3 / P=1 | B=6 / P=2 | B=24 / P=8 | B=48 / P=16 | B=96 / P=32 |
|---|---:|---:|---:|---:|---:|
| SEGMENT | +22.1% | +13.0% | +20.9% | +19.3% | +16.6% |
| DISK | -9.1% | +26.0% | +22.2% | +18.7% | +16.4% |
| HEAP | -42.5% | -4.4% | +16.2% | +21.3% | +4.5% |
| OFF_HEAP | +16.8% | +19.3% | +27.8% | +22.4% | +16.5% |
| DISK_SEGMENT | +8.6% | +22.4% | +14.8% | +11.5% | +15.0% |

All 15 configurations at B=24/48/96 are slower than their corresponding baseline in every block.
HEAP/B=3 remains a substantial improvement in all blocks. DISK/B=3 and HEAP/B=6 have lower pooled
means but mixed block signs.

## Runtime and machine state

| Phase | Wall time |
|---|---:|
| Build | 15s |
| Fixture preparation | None; saved fixture reused |
| Block A | 34m 49s |
| Block B | 35m 03s |
| Block C | 35m 42s |
| Three measurement blocks combined | 1h 45m 34s |
| Whole run, including build and orchestration | 1h 46m 08s |

The readiness check immediately before measurement showed 0.0% CPU busy and I/O wait, 121.4 GiB
available RAM, no swap activity and no disk traffic. No application errors, benchmark failures
or timeouts were found. All 29 archived-file checksums and four frozen-input checksums match.

Output forcing took about 8.6 seconds on average across 300 snapshots, including warmups, outside
the timed call. These results remain snapshot return times, not durable-write latency. The fixed
campaign took 6m 31s longer than the task retry; whole-run duration also includes source setup,
forcing, validation and cleanup, so it is not a separate snapshot-performance measurement.

## Artifact identity

- Run: `fixed-pool-20261007T123445Z-3339304`, completed with exit code 0.
- Recorded checkout: `cce428fb84687da882c214d153cdee50428c5bc4`; fresh build, no tracked source diff.
  Bytecode confirms the fixed ExecutorService snapshot implementation.
- Archive SHA-256:
  `2926690e94858f61e03839ebd5104d8bb3ec2233e2d2d455f7dd76157796361d`.
- JAR SHA-256:
  `37f6bc872abbedeeec1ea122798797044f7c4ad407016058e731364d0c7cc841`.
- [Sanitized result data](fixed-pool-20261007.json) preserves all 225 measurements and provenance.
  Raw archives, machine/user identifiers and operational paths stay local.
