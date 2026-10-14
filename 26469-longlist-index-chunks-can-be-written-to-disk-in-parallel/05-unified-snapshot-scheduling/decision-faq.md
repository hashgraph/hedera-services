# Snapshot scheduling: questions for the team discussion

[Full results](results.md) · [Separate F1/F2 experiments](f1-f2-20261013.md) · [Combined experiment](f1-f2-combined-20261014.md)

## Recommendation in 30 seconds

I recommend keeping the range-based parallel writer and adding file pre-sizing, F1.
It improved the baseline on all five index implementations, with a smaller change than replacing
the scheduling model. The shared-pool alternatives improved too, but have not shown a clear
overall performance advantage over baseline + F1. There is an important exception: tasks + F1
was faster for DISK_SEGMENT. Combined tasks are also a credible near-baseline alternative.
This is a recommendation for this PR and its snapshot-return-time goal, not a claim that the
baseline is always fastest. [Measured comparisons](results.md#3-do-the-alternatives-beat-baseline--f1).

This document proposes a decision; it does not mean the branch has already been changed to it.

## How the implementations work

### 1. What do we mean by “baseline”? Is it the old single-threaded code?

No. Our baseline already writes indices in parallel, overlaps the cached-hash flush with independent
snapshot work, and does not force index files to storage inside the timed call.

For each index, it divides the chunks into contiguous ranges. Each task walks through one whole
range. In this experiment there are at most eight tasks per index, sharing a separate writer pool
of 24 threads across the three indices. Six top-level snapshot operations run on another executor;
the caller flushes cached hashes while independent operations run. The two hash-dependent operations
start after that flush. The writer pool is created and closed for each snapshot.
“Keep the baseline” means keep this scheduling design, not abandon the
earlier optimizations. [Range writer][range-writer] · [Baseline snapshot][range-snapshot].

### 2. How did we implement the task approach?

We used the project's `AbstractTask` on a shared `ForkJoinPool`, configured with parallelism 24
for this experiment. The pool belongs to the data source and is reused between snapshots.

Each LongList first schedules its file header, then one task for each chunk. A chunk task calculates
its source bounds and destination offset, writes that part, and reports completion. The common
LongList code handles partial chunks and missing chunks; subclasses read their particular kind of
chunk and write its values to the file.
Metadata, file-linking operations and the cached-hash flush also use this snapshot pool. Futures
connect the operations and keep each file open until its writers have stopped.
This is asynchronous scheduling, not asynchronous disk I/O: a worker still performs the actual
blocking write. [Task writer][task-writer] · [Task pool][task-pool] · [Task helper][task-helper].

### 3. How was the fixed-pool approach different?

It used an ordinary fixed `ExecutorService` with 24 workers, rather than `AbstractTask` and
`ForkJoinPool`. Chunk writes and other operations were submitted through ordinary operations and
`CompletableFuture`. It retained the same chunk-writing refactor, four logical groups and dependencies.
The pool also belongs to the data source and is reused. It was not four blocking parent tasks
waiting for children in the same pool. [Fixed-pool writer][pool-writer] · [Pool creation][fixed-pool]
· [Operation helper][pool-helper].

Both alternatives use tasks in the everyday sense. In the results, “Tasks” specifically names the
`AbstractTask`/ForkJoin version; “Fixed pool” names the plain-executor version.

### 4. What are the four groups? Do file links wait for index writes?

The groups organize the code; they are not four occupied threads waiting for their children.

| Group | Work and dependencies |
|---|---|
| Metadata | Write the data source metadata. |
| Leaves | Write leaf-index chunks and link leaf data files independently. |
| Hashes | Flush cached hashes first; then write hash-index chunks and link hash data files independently. |
| Key-to-path map | Create its directory; then write its bucket index, link bucket files and write metadata independently. |

The hash index and hash files must both wait for the hash flush because it changes them. File
linking does **not** wait for its corresponding index write. Completion callbacks start dependent
work; snapshot workers do not sit waiting for their own child snapshot tasks. The caller waits
for the complete snapshot. Workers can still wait on disk I/O.
[Group implementation][task-groups] · [Key-to-path group][key-group].

### 5. What exactly is F1?

F1 sets the index file's final length before chunk writes start. We write the header, calculate
the body length from the stored index range, call `RandomAccessFile.setLength()`, and then write
the chunks normally. Empty lists and ranges beyond the populated data remain header-only.

This establishes the file's logical length; it is not a guarantee that all physical disk space
has been reserved. It does not write the actual index contents, skip their writes, or add a force.
We used the Java resizing API, not a dummy write at the last byte. `FileChannel.truncate()` cannot
enlarge a file. [F1 implementation and tests][baseline-patch] · [Java resizing contract][set-length]
· [Java truncate contract][truncate].

### 6. What exactly is F2? How is it different from the baseline?

F2 limits each index to up to eight chains. Each chain owns a contiguous range, writes one chunk,
and only then submits the next chunk in that range. It avoids queuing every chunk at once and
restores a per-index writer limit.

For a simple example with 12 chunks and three ranges:

- **Baseline:** one task writes chunks 0–3, another 4–7, another 8–11.
- **Original pools:** submit 12 separate chunk tasks; the pool decides which run when.
- **F2:** start tasks for chunks 0, 4 and 8; each completion submits its own next chunk.

F2 still schedules a task for every chunk. Successive chunks can run on different workers; the
baseline keeps the loop inside one range task. Neither design guarantees that the whole file is
written in offset order, because several ranges run concurrently.
[Task F2 patch][tasks-patch] · [Fixed-pool F2 patch][pool-patch] · [Range writer][range-writer].

## What the results tell us

### 7. What did we actually benchmark?

Complete MerkleDB snapshot calls on the same saved one-billion-record state, not just index writes.
We used all five LongList implementations, applying each selected implementation to all three
indices. Baseline used eight writers per index; the shared pools used 24 workers; F2 used eight
chains per index. These are selected experimental settings, not a test of the production defaults.

October 13 measured eight variants: unchanged and F1 baseline; unchanged, F1 and F2 tasks;
unchanged, F1 and F2 fixed pool. October 14 added tasks with both fixes, reusing the exact task JAR
and fixture but not rerunning controls. Each configuration has three JVM forks with three measured
snapshots each: nine measurements, not nine independent forks.
[Separate-run setup](f1-f2-20261013.md#workload-and-timing) · [Combined-run setup](f1-f2-combined-20261014.md#workload-and-checks).

### 8. If F1 and F2 helped, why are we not simply choosing the pools?

“Helped” compares an approach with itself before the fix. The baseline can benefit too.
For SEGMENT, tasks + F1 took 5.567 s: 15.4% faster than unchanged tasks, 3.0% faster than the
unchanged baseline, but 5.5% slower than baseline + F1. Those statements use the same task result
and different references. F1 and F2 each improved their own approach on all five implementations,
with the direction repeating in all three October 13 blocks.
[All three comparisons](results.md).

### 9. Why recommend baseline + F1 when combined tasks are close?

Because the smaller change already gives a repeatable benefit, while the larger scheduling change
has not established a broad performance advantage. F1 reduced baseline mean time by 5.4–9.1%
across the five implementations. That supports adopting F1 independently of the scheduler decision.

Every result below is **mean seconds (change versus baseline + F1)**; negative means faster.

| Index | Baseline + F1 | Tasks + F1 | Tasks + F1 + F2 † |
|---|---:|---:|---:|
| SEGMENT | 5.279 s (0.0%) | 5.567 s (+5.5%) | 5.349 s (+1.3%) |
| DISK | 5.338 s (0.0%) | 5.325 s (-0.2%) | 5.284 s (-1.0%) |
| HEAP | 4.802 s (0.0%) | 5.497 s (+14.5%) | 4.862 s (+1.2%) |
| OFF_HEAP | 5.308 s (0.0%) | 5.509 s (+3.8%) | 5.295 s (-0.2%) |
| DISK_SEGMENT | 7.028 s (0.0%) | 6.683 s (-4.9%) | 6.856 s (-2.5%) |

† Combined tasks ran the following day, without fresh controls. These small differences do not
prove equivalence or a reliable winner. Fixed pool + F1 and fixed pool + F2 had higher means than
baseline + F1 on all five; fixed pool + both fixes was not measured.
[Complete tables](results.md#3-do-the-alternatives-beat-baseline--f1).

So this is a scope-and-maintenance decision supported by competitive performance, not proof that
baseline + F1 is fastest everywhere. If the team values the shared-pool architecture enough on
its own, the combined task approach remains a reasonable alternative.

### 10. Are we ignoring the DISK_SEGMENT win?

No. Tasks + F1 was 4.9% faster than baseline + F1 overall, with improvements of 7.0%, 1.5% and
6.1% in the three October 13 blocks. It should not be dismissed as noise. Choosing the range-based
implementation accepts this observed trade-off in exchange for the smaller change. If DISK_SEGMENT
is the main deployment target, that weakens the recommendation and deserves explicit discussion.
[Repeatability](f1-f2-20261013.md#repeatability-and-limits).

### 11. Why weren't the shared pools faster?

We have evidence about what helped, but not a proven low-level cause of the slowdown.

- Without F2, an index is no longer capped at eight writers. More of the shared pool can work on
  the same large file, instead of being spread across three indices.
- Per-chunk scheduling changes when writes run and their order. Work submitted later can also
  wait behind already queued work, particularly in the fixed pool.
- F1 helps without changing the scheduler, so growing the output file during concurrent writes
  is a useful optimization target.

These observations make contention, queueing and file growth plausible contributors. They do not
prove a particular filesystem lock, queue delay or task-allocation cost caused the measured gap.
F2 changes writer limits, queue depth and write order together, so it cannot separate those effects.
[Earlier investigation](monitored-comparison-20261008.md#interpretation-and-next-step)
· [F1/F2 interpretation](f1-f2-20261013.md#what-the-results-support).

### 12. Doesn't the baseline win because its writes are more sequential?

Each baseline task does move forward through several adjacent chunks without rescheduling between
them. That is a real difference and a plausible advantage. But several range tasks still write
concurrently, so the entire file is not written serially. Also, a range task is **not one larger
disk write**: it still performs chunk or buffer writes internally.

The alternative code changed more than scheduling. For example, HEAP changed its value-reading
path and buffer reuse. We compared complete implementations, not an isolated ForkJoinPool-versus-
Executor experiment. “More orderly progress may help” is supported as a hypothesis; “we proved
sequential I/O explains the result” is not. [Baseline HEAP writer][range-heap]
· [Refactored HEAP writer][task-heap] · [Comparison scope](monitored-comparison-20261008.md#what-was-compared).

### 13. Was eight writers versus a 24-worker pool a fair comparison?

It is an approximate comparison, not identical concurrency. The baseline allows eight writers for
each of three indices, hence up to 24 index writers. It also has separate top-level threads and
the caller doing the hash flush. The alternatives share 24 workers between index writes and other
snapshot operations. Without F2, one index can use more than eight of them.

F2 restores the per-index limit, but not the baseline's separate coordination threads or task
granularity. The results describe these actual designs at these settings; they do not establish
the best possible tuning of every design. [Budget explanation](f1-f2-20261013.md#what-changed)
· [Baseline orchestration][range-snapshot].

## Questions about confidence and the final decision

### 14. Could background activity have caused the result?

It could affect individual measurements, especially small differences. We sampled CPU/processes,
memory/swap and disk activity every five seconds in the F1/F2 runs. There was no evidence of
sustained competing load or memory pressure, but periodic system-management activity was present
around some measurements. All measurements were retained.

The original pool regression also survived the earlier monitored rerun. That makes “it was just
another user's workload” an unsupported reason to discard the broad result. Monitoring does not
make a 1% difference conclusive. [October 13 monitoring](f1-f2-20261013.md#validation-and-machine-activity)
· [October 14 monitoring](f1-f2-combined-20261014.md#machine-activity)
· [Earlier monitored rerun](monitored-comparison-20261008.md#summary).

### 15. Could we have compared different data or incomplete snapshots?

The F1/F2 runs reused the same saved fixture. Recorded names, file sizes and modification times
matched before and after; that is not a full-content hash of every fixture file. The benchmark
validated index lengths/headers, sampled values, metadata and store presence. It did not restore
and compare all one billion records.

The experiment builds also passed focused tests. For baseline F1, these included byte-for-byte
comparison and restoration for all five implementations, plus header-only and existing-file cases.
This is useful correctness evidence, not a substitute for testing the final cleaned-up implementation.
[Run validation](f1-f2-20261013.md#validation-and-machine-activity) · [F1 tests][baseline-patch].

### 16. Are we measuring disk speed or just returning sooner?

We measure how long `snapshot()` takes to return, which is the goal of this comparison. Writes
have completed from the code's perspective, but without forcing they need not all have reached
durable storage. Setup, validation, forcing and cleanup are outside the timed call for every variant.

A shorter whole benchmark run is therefore not necessarily a faster snapshot call: some waiting
can move between the call and the later force. We should defend the return-time improvement, not
claim the same improvement in disk throughput or crash-durable snapshot completion.
[Timing boundary](f1-f2-20261013.md#workload-and-timing)
· [Observed return/force difference](monitored-comparison-20261008.md#why-the-shorter-fixed-pool-run-does-not-mean-faster-snapshots).

### 17. Doesn't the shared pool have benefits beyond speed?

Yes: one snapshot-wide concurrency setting, reusable workers, and clearer grouping of dependent
operations. The common single-chunk writer also removes duplicated edge handling in subclasses.
Those are legitimate reasons to consider the design; the benchmark does not make them disappear.

The trade-off is a broader asynchronous API and more completion, failure and file-lifetime handling.
Baseline + F1 is my recommendation when the goal is a smaller performance-focused PR. If the team
wants the architectural changes even without a clear latency win, that should be an explicit
decision. [Task writer][task-writer] · [Groups][task-groups] · [Fixed-pool writer][pool-writer].

**Could we share a pool but keep range-loop writers?** That is another possible design: pool
management and task size are separate choices. We did not benchmark that hybrid, so we cannot
claim how it would perform. It would need its own safe completion handling; simply making blocking
parent tasks share their children's pool is not the design we tested.
[Measured variants](f1-f2-20261013.md#what-changed) · [Baseline waiting][range-writer].

### 18. What happens on failure? Would keeping the baseline lose useful fixes?

In the pool versions, a shared failure marker stops unstarted work from doing more writes. Already
running operations are allowed to stop before their files close and the caller receives the error.
That is not instant cancellation of disk I/O. The baseline also waits for accepted range writers
before closing their channel and preserves a submission error if a writer fails too; it does not
have the same snapshot-wide skip policy. [Task errors][task-helper] · [Fixed-pool errors][pool-helper]
· [Baseline errors][range-writer].

Choosing baseline scheduling must not mean blindly restoring an old revision. Any independent
correctness fixes found during the refactor need a separate review and regression coverage. They
should not be accepted or discarded just because we picked a different scheduling model.

### 19. Is F1 ready to ship exactly as the experiment implements it?

Not unchanged: the patch has a measurement-only switch and needs normal production integration.
The final version must keep safe length arithmetic, correct header-only handling, rejection of an
existing destination, and normal error propagation. A pre-sized file already has its final length
before its contents are complete, so file length alone must never be treated as snapshot success.
All required contents still have to be written successfully. [F1 implementation and tests][baseline-patch].

The Java API is standard; the measured speedup is still specific to the tested machine, filesystem
and workload. No claim of universal improvement follows from using a portable API.
[Java resizing contract][set-length] · [Workload](f1-f2-20261013.md#workload-and-timing).

### 20. Do we need another full matrix? Should we change the writer default?

Neither follows from these results. We have useful evidence for adding F1 and for keeping the
existing scheduler as the conservative choice. We do not have evidence that the selected eight-
writer setting is the right production default, or that the combined version wins by a small margin.

If the team needs to decide that close ranking, prioritize DISK_SEGMENT, or change defaults, test
that specific question at the intended settings. Do not repeat every matrix automatically. After
integrating the selected implementation, rerun its relevant correctness tests and verify the final
configuration before calling the PR ready. [Limits](results.md#limits-and-verification)
· [Combined-run uncertainty](f1-f2-combined-20261014.md#variation-between-forks).

## Claims to avoid on the call

- “The baseline wins everywhere.” DISK_SEGMENT is a counterexample.
- “ForkJoinPool is bad for snapshots.” We compared implementations, not the library in isolation.
- “We proved filesystem locks or random writes caused the slowdown.” We have hypotheses, not that proof.
- “F1 and F2 gains add together.” The combined result was measured separately and is not uniformly better than F1 alone.
- “The machine was perfectly idle” or “all billion records were verified.” Neither is what the evidence says.

[range-writer]: https://github.com/hiero-ledger/hiero-consensus-node/blob/bdfd70497908f02f46d754d556a01008332ac7e1/platform-sdk/swirlds-merkledb/src/main/java/com/swirlds/merkledb/collections/AbstractLongList.java#L489-L598
[range-snapshot]: https://github.com/hiero-ledger/hiero-consensus-node/blob/bdfd70497908f02f46d754d556a01008332ac7e1/platform-sdk/swirlds-merkledb/src/main/java/com/swirlds/merkledb/internal/MerkleDbDataSource.java#L957-L1060
[task-writer]: https://github.com/hiero-ledger/hiero-consensus-node/blob/6bb5bcc0b207093c5dda0ddefb6ffb09d4b6eca9/platform-sdk/swirlds-merkledb/src/main/java/com/swirlds/merkledb/collections/AbstractLongList.java#L495-L594
[task-pool]: https://github.com/hiero-ledger/hiero-consensus-node/blob/6bb5bcc0b207093c5dda0ddefb6ffb09d4b6eca9/platform-sdk/swirlds-merkledb/src/main/java/com/swirlds/merkledb/internal/MerkleDbDataSource.java#L334-L337
[task-helper]: https://github.com/hiero-ledger/hiero-consensus-node/blob/6bb5bcc0b207093c5dda0ddefb6ffb09d4b6eca9/platform-sdk/swirlds-merkledb/src/main/java/com/swirlds/merkledb/utilities/SnapshotTask.java
[pool-writer]: https://github.com/hiero-ledger/hiero-consensus-node/blob/cce428fb84687da882c214d153cdee50428c5bc4/platform-sdk/swirlds-merkledb/src/main/java/com/swirlds/merkledb/collections/AbstractLongList.java#L495-L576
[fixed-pool]: https://github.com/hiero-ledger/hiero-consensus-node/blob/cce428fb84687da882c214d153cdee50428c5bc4/platform-sdk/swirlds-merkledb/src/main/java/com/swirlds/merkledb/internal/MerkleDbDataSource.java#L336-L339
[pool-helper]: https://github.com/hiero-ledger/hiero-consensus-node/blob/cce428fb84687da882c214d153cdee50428c5bc4/platform-sdk/swirlds-merkledb/src/main/java/com/swirlds/merkledb/utilities/MerkleDbFileUtils.java#L20-L95
[task-groups]: https://github.com/hiero-ledger/hiero-consensus-node/blob/6bb5bcc0b207093c5dda0ddefb6ffb09d4b6eca9/platform-sdk/swirlds-merkledb/src/main/java/com/swirlds/merkledb/internal/MerkleDbDataSource.java#L1103-L1131
[key-group]: https://github.com/hiero-ledger/hiero-consensus-node/blob/6bb5bcc0b207093c5dda0ddefb6ffb09d4b6eca9/platform-sdk/swirlds-merkledb/src/main/java/com/swirlds/merkledb/files/hashmap/HalfDiskHashMap.java#L416-L428
[range-heap]: https://github.com/hiero-ledger/hiero-consensus-node/blob/bdfd70497908f02f46d754d556a01008332ac7e1/platform-sdk/swirlds-merkledb/src/main/java/com/swirlds/merkledb/collections/LongListHeap.java#L155-L180
[task-heap]: https://github.com/hiero-ledger/hiero-consensus-node/blob/6bb5bcc0b207093c5dda0ddefb6ffb09d4b6eca9/platform-sdk/swirlds-merkledb/src/main/java/com/swirlds/merkledb/collections/LongListHeap.java#L159-L181
[baseline-patch]: ../../platform-sdk/swirlds-benchmarks/src/jmh/scripts/snapshot-experiments/baseline.patch
[tasks-patch]: ../../platform-sdk/swirlds-benchmarks/src/jmh/scripts/snapshot-experiments/tasks.patch
[pool-patch]: ../../platform-sdk/swirlds-benchmarks/src/jmh/scripts/snapshot-experiments/fixed-pool.patch
[set-length]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/io/RandomAccessFile.html#setLength(long)
[truncate]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/nio/channels/FileChannel.html#truncate(long)
