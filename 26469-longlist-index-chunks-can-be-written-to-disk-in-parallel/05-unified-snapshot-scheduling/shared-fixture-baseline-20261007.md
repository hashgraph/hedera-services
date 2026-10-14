# October 7 shared-fixture baseline rerun

Historical report. See [results.md](results.md) for the latest findings.


This run supplied the baseline for the [October 8 monitored comparison](monitored-comparison-20261008.md).
The candidate percentages on this page use the original October 6/7 runs, before their monitored reruns.

The original range-based JAR was rerun on October 7, 2026, without rebuilding or generating a new
fixture. It uses the exact saved fixture used by both candidates: about 258 GiB, with 150 hash,
174 leaf and 25 bucket data files. All 357 inventory entries match both campaigns and remain
unchanged after the run. Inventories cover relative names, sizes and modification times, not
file-content hashes.

Workload, effective settings, source/cache preparation, forcing and the three-block matrix match
the earlier campaigns. Each configuration has nine measured samples, with none discarded.
`P` is writers per LongList; `B` is shared-pool parallelism. `B = 3 × P` is only an approximate
configured budget: the baseline's top-level work runs outside its writer pool, P=1 writes on the
calling threads, and the shared pools also perform hash flushing and other snapshot operations.
The production defaults P=4 and B=12 were not measured.

## Snapshot times

Arithmetic mean of all nine measurements per configuration, in **seconds**. Lower is better.
[The result data](baseline-shared-fixture-20261007.json) preserves every sample, medians, sample
standard deviations, block means and comparisons with all three earlier campaigns.

| Implementation | P=1 | P=2 | P=8 | P=16 | P=32 |
|---|---:|---:|---:|---:|---:|
| SEGMENT | 7.327 | 6.534 | 5.689 | 5.511 | 5.458 |
| DISK | 9.787 | 6.339 | 5.615 | 5.714 | 5.588 |
| HEAP | 11.654 | 5.877 | 5.176 | 5.207 | 5.704 |
| OFF_HEAP | 7.200 | 6.355 | 5.367 | 5.527 | 5.460 |
| DISK_SEGMENT | 9.626 | 8.172 | 7.517 | 7.238 | 7.211 |

The rerun is broadly similar to the original baseline: 21 of 25 means are lower, with changes
from -6.9% to +1.8%. This does not support the original fixture being the reason for the baseline's
advantage.
Small differences among the higher P settings remain subject to variation, not proof of an
optimal writer count.

## Tasks versus the shared-fixture baseline

Change in mean snapshot time: `(task / shared-fixture baseline - 1) × 100`.
Positive means slower; negative means faster.

| Implementation | B=3 / P=1 | B=6 / P=2 | B=24 / P=8 | B=48 / P=16 | B=96 / P=32 |
|---|---:|---:|---:|---:|---:|
| SEGMENT | +7.3% | +13.7% | +14.8% | +19.0% | +21.6% |
| DISK | -21.7% | +17.5% | +17.2% | +16.1% | +20.4% |
| HEAP | -48.2% | -8.3% | +9.6% | +12.2% | +2.0% |
| OFF_HEAP | +4.1% | +17.5% | +23.2% | +19.8% | +21.3% |
| DISK_SEGMENT | +3.9% | +18.2% | +11.0% | +12.2% | +13.0% |

Tasks are slower in 22 means and medians, with the same direction in all three block comparisons
for 20 configurations. DISK/B=3 and HEAP/B=3/B=6 are faster in every block. DISK_SEGMENT/B=3 and
HEAP/B=96 have mixed block signs. Blocks from different campaigns are descriptive comparisons,
not paired experiments under identical conditions.

## Fixed pool versus the shared-fixture baseline

Change in mean snapshot time: `(fixed pool / shared-fixture baseline - 1) × 100`.
Positive means slower; negative means faster.

| Implementation | B=3 / P=1 | B=6 / P=2 | B=24 / P=8 | B=48 / P=16 | B=96 / P=32 |
|---|---:|---:|---:|---:|---:|
| SEGMENT | +21.2% | +20.9% | +25.5% | +23.5% | +25.2% |
| DISK | -10.7% | +30.2% | +26.6% | +19.5% | +22.8% |
| HEAP | -42.5% | -2.7% | +17.7% | +21.6% | +6.8% |
| OFF_HEAP | +18.1% | +24.6% | +32.9% | +21.3% | +19.9% |
| DISK_SEGMENT | +10.9% | +28.2% | +14.9% | +15.5% | +17.9% |

The fixed pool is slower in 22 means and medians, and all 22 slower configurations have the same
direction in every block. DISK/B=3 and HEAP/B=3 are faster in every block; HEAP/B=6's small lower
mean has mixed block signs.

## Completion, runtime and machine state

All 31 archive checksums and four frozen-input checksums pass. All 75 restored-source and 300
snapshot validation paths completed without failure, including warmups. These check index
structure and sampled values, metadata and store presence, not a second full restoration.
There were no application errors, benchmark failures or timeouts.

| Phase | Wall time |
|---|---:|
| Build and fixture preparation | None; frozen JAR and saved fixture reused |
| Block A | 34m 11s |
| Block B | 34m 30s |
| Block C | 34m 06s |
| Three measurement blocks combined | 1h 42m 47s |
| Whole run, including orchestration | 1h 43m 07s |

CPU/process and memory/swap/I/O data were recorded every 30 seconds through the measurement
blocks. No substantial competing userspace workload appeared. Available RAM stayed above
111 GiB and no swap-in or swap-out occurred. The sampled disk traffic and kernel writeback
activity are consistent with the benchmark's writes, not evidence of another workload.
Sampling is not a reservation or complete process-level I/O attribution, and the earlier two
candidate runs were not continuously monitored.

Forcing output files after return took 9.252 seconds on average across 300 snapshots, including
warmups. It remains outside the timed score. Whole campaign duration also includes source setup,
forcing, validation and cleanup, so the task campaign's shorter wall time does not mean its
snapshot calls were faster.

## Artifact identity

- Run: `baseline-shared-fixture-20261007T173414Z-3465266`, completed with exit code 0.
- Exact original baseline JAR reused; its build revision was not independently verified. The
  launcher checkout `cce428fb84687da882c214d153cdee50428c5bc4` is not the baseline code revision.
- Archive SHA-256:
  `a1421670815dfc93b80cb483b9714824bfff4850e6b86d4172b358a17dab317f`.
- JAR SHA-256:
  `11080a9a688df3f177eb78a84d2ac26fbb85c8ea095757a08c417bf0730ccc17`.
- [Sanitized result data](baseline-shared-fixture-20261007.json) contains all samples and provenance.
  Raw archives, machine/user identifiers and operational paths stay local.
