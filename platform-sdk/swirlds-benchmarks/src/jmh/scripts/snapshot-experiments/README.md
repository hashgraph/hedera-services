# Snapshot scheduling experiments

This is a measurement-only Linux campaign. It does not change the current checkout, make commits, generate a fixture, or run benchmarks remotely. It uses the existing saved 1-billion-leaf fixture under `platform-sdk/swirlds-benchmarks/build/tmp/merkledb-snapshot-1b-campaign/saved-fixture`.

From the repository root, with `JAVA_HOME` pointing to JDK 25.0.2:

```bash
bash platform-sdk/swirlds-benchmarks/src/jmh/scripts/snapshot-experiments/runner.sh
```

To leave it running after disconnecting and follow its console:

```bash
nohup bash platform-sdk/swirlds-benchmarks/src/jmh/scripts/snapshot-experiments/runner.sh > snapshot-experiments.log 2>&1 &
tail -f snapshot-experiments.log
```

Use `--dry-run` to print the entire schedule without writes or execution. Requirements are Bash, Git, JDK 25.0.2, Gradle's existing build prerequisites, `jq`, procps (`top`, `vmstat`), and standard Linux utilities including `flock` and `setsid`. The existing system-readiness checker runs before builds and immediately before measurement, never between measured cells. Keep the host exclusive; sampling records interference but does not automatically discard samples or stop on utilization thresholds.

## Combined-only follow-up

To measure only tasks with F1 and F2 enabled together:

```bash
nohup bash platform-sdk/swirlds-benchmarks/src/jmh/scripts/snapshot-experiments/runner.sh --combined > snapshot-combined.log 2>&1 &
tail -f snapshot-combined.log
```

This reuses the exact task JAR from the October 13 campaign's artifact cache, checked by SHA-256,
and the same saved fixture. It fails if either is missing or changed; it never rebuilds the JAR
or generates a fixture. No baseline or fixed-pool measurements repeat.

All five implementations run with B=24, F1 enabled and F2 K=8. Three blocks rotate implementation
order, with one warmup and three measurements per fork: 15 forks, 15 warmups and 45 measurements.
Five-second monitoring, validation, fixture checks and archive creation remain unchanged.

The October 13 task-F1 and task-F2 variants each took about 19 minutes for these 15 forks,
including setup and teardown. Expect roughly 20 minutes; allow 25–30 minutes overall. Compare
against the saved baseline-F1 and task-F1 results, noting that they ran on a different day.
Small differences may need a same-session control rerun.

Use `--combined --dry-run` to inspect the plan, or `--combined --resume RUN_ID` to resume this mode.
The mode and frozen plan must match on resume. Omit `--combined` for the original full matrix below.

## Original full matrix

| Historical source | Variants | Parallelism |
| --- | --- | --- |
| `bdfd70497908f02f46d754d556a01008332ac7e1` | Control, F1 presizing | P=8 writers per list |
| `6bb5bcc0b207093c5dda0ddefb6ffb09d4b6eca9` | Control, F1 presizing, F2 contiguous chains | B=24 shared-pool threads |
| `cce428fb84687da882c214d153cdee50428c5bc4` | Control, F1 presizing, F2 contiguous chains | B=24 shared-pool threads |

Every variant runs SEGMENT, DISK, HEAP, OFF_HEAP, and DISK_SEGMENT. Each selected implementation applies to all three snapshot indices. F2 uses K=8 cursors per list; F1 and F2 are not combined in this full matrix. The saved deterministic schedule rotates implementation and variant order across three interleaved blocks. Each cell/block has one fork, one warmup, and three measured snapshots: 40 cells, 120 forks, 360 measured samples, and 480 snapshots including warmups. P=8 versus B=24 approximates a writer budget, not identical actual concurrency.

Force stays off inside the timed snapshot call and hash-cache overlap remains enabled. Source restoration, cache loading, output forcing after return, validation, and cleanup stay outside the timed call. Measurements are snapshot return time, not durable-write latency.

## Builds and frozen inputs

The neighboring `baseline.patch`, `tasks.patch`, and `fixed-pool.patch` contain temporary experiment code and targeted tests. In full-matrix mode, the script creates one owned detached worktree per exact ref when a cached build is unavailable, applies its patch, and runs the following sequentially, with no persistent Gradle daemon:

```text
./gradlew --no-daemon --console=plain :swirlds-merkledb:test --tests '*LongList*ExperimentTest' :swirlds-benchmarks:jmhJar
```

All three JARs expose `merkledb.experiment.presizeIndexFiles` (default false). The two pool variants also expose `merkledb.experiment.cursorsPerList` (default 0); the baseline does not implement F2. Control cells explicitly disable both flags. The baseline patch also adds the missing-fixture guard outside measurement so a missing fixture fails instead of triggering generation.

Builds are cached by full source ref plus patch SHA-256 and checked before reuse. The exact `java -version` text is frozen and must match for cache reuse and resume. Every campaign gets private frozen copies of its required JARs, source patches, refs, build logs, test results, effective settings, readiness checker, runner, and schedule. Benchmark forks never execute mutable build outputs. Only this run's marked temporary worktrees and scratch directories are removed; the fixture and unrelated worktrees are untouched.

## Sampling, results, and resume

`top`, `vmstat`, and raw `/proc/diskstats` are recorded every five seconds with UTC timestamps and cell markers. Start/completion boundaries are also saved separately. Sampling covers the measurement session, not builds. The saved fixture's workload key, settings hash, and relative names/sizes/modification-time inventory are checked before and after the campaign. This is not a full-content hash of the fixture.

Results live under `platform-sdk/swirlds-benchmarks/build/results/jmh/merkledb-snapshot-1b-campaign/snapshot-experiments/<RUN_ID>`. The final console prints one neighboring `.tar.gz` archive containing frozen inputs, JSON samples, benchmark/application logs, commands, sampling, manifests, and checksums. The archive never includes fixture data. It is also produced on ordinary failure or interruption.

Resume an interrupted **measurement session** using its printed run ID:

```bash
bash platform-sdk/swirlds-benchmarks/src/jmh/scripts/snapshot-experiments/runner.sh --resume RUN_ID
```

Resume verifies frozen inputs and requires the same runner. It does not rebuild. A cell is skipped only when its completed attempt's checksums and three-sample JMH JSON validate. Completed attempts are finalized before an atomic completion pointer is published and are never rewritten by the interruption handler. Incomplete attempts remain available for inspection and the entire cell is rerun, including warmup. No partial samples are merged or silently discarded.

Interruption sends TERM only to owned process groups, waits a bounded grace period, then uses KILL if needed. Scratch is deleted only after those groups are gone; if any remains, its files are preserved and no potentially changing archive is produced. A failed or interrupted active Gradle build always retains its owned worktree because a single-use Gradle daemon can detach even with `--no-daemon`. The exact retained path is recorded in the manifest/archive and console. Inspect it before restarting; no global Gradle processes are killed. Build failure is not a measurement resume: start a new run, which reuses any successfully completed build cache entries.

After a hard kill or machine crash, the lock or readiness checks may reject leftover scratch, live JVMs, or orphaned samplers: inspect them before retrying; the script does not kill unrelated processes or delete abandoned directories automatically.

Historical selected B=24 log intervals predict about 2 hours 41 minutes for the 120 forks: the 15 FJP forks took 1,227.726 seconds, the 15 fixed-pool forks took 1,171.198 seconds, and baseline's 30 forks are estimated using its prior whole-matrix wall average. Budget approximately 2.5–3.5 hours for measurement, plus builds and archiving; experiment performance and host state can change that estimate.
