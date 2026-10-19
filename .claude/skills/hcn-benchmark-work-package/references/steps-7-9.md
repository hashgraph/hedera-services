# Steps 7–9: Calibration, First Nightly Result, Dashboard

Rules: [calibration.md](/platform-sdk/docs/consensus-layer/benchmarks/calibration.md) and
[operations.md](/platform-sdk/docs/consensus-layer/benchmarks/operations.md), sections "The benchmark machine" and
"Publishing and the dashboard". Variables as in [steps-1-3.md](steps-1-3.md). The scripts need Python 3.10 or newer.

The benchmark machine is one shared machine, `hl-cn-benchmark-lin`. A run can wait in GitHub's queue for hours, and
the nightly job starts there at 17:00 UTC; a calibration run queued then delays it. Ask the developer before starting a
run.

## Step 7: calibrate

Apply [the calibration run](/platform-sdk/docs/consensus-layer/benchmarks/calibration.md#the-calibration-run). The
workflow checks out `ref`, the pull request's branch, and takes its scripts from there too, so the branch must be
pushed and contain the calibration tooling.

```bash
BRANCH=<the pull request's branch>
gh workflow run 007-user-benchmark-calibration.yaml --ref main \
  -f module="$MODULE" -f benchmark="$BENCH" -f ref="$BRANCH" -f jmh-args="-f 10 -wi 0 -i 20 -r 5s"
gh run list --workflow 007-user-benchmark-calibration.yaml --limit 1     # the run's id
gh run watch <run id> --exit-status
gh run download <run id> -n benchmark-calibration -D "$OUT/calibration/<run id>"
```

- **The default arguments** take about 40 minutes for the example's two parameter sets, after the queue. The profiler
  input defaults to `LatencyProfiler`; the other inputs (`max-load`, `attempts`, `deadline-minutes`) rarely change.
- **Repeat with a new workflow run,** never `gh run rerun`. Analyze several runs together; the pilot used three to four
  quiet runs. The noise between runs comes from the nightly history later, not from calibration
  ([the noise between runs](/platform-sdk/docs/consensus-layer/benchmarks/calibration.md#the-noise-between-runs)).
- **Keep the downloaded runs:** the artifacts expire after 90 days, and the block links them.

**Analyze** all runs together
([the analysis](/platform-sdk/docs/consensus-layer/benchmarks/calibration.md#the-analysis)):

```bash
python3 .github/workflows/support/scripts/benchmarks/analyze_calibration.py "$OUT"/calibration/*
```

The output has, per parameter set, the warmup, the noise of iterations, forks, and runs, the alloc.norm modes, and a
recommendation; then one configuration for all parameter sets with its runtime and the calibration block.

- **Choose the configuration** with the developer
  ([choosing the configuration](/platform-sdk/docs/consensus-layer/benchmarks/calibration.md#choosing-the-configuration)).
  The recommendation trades noise for nightly time; the time agreed in the issue sets the limit. Don't argue for fewer
  or more forks with anything but the predicted noise and the runtime.
- **Fork modes** (more than one alloc.norm mode): stop and tell the developer
  ([fork modes](/platform-sdk/docs/consensus-layer/benchmarks/calibration.md#fork-modes)).
- **Discarded passes:** raise the heap and calibrate again
  ([heap](/platform-sdk/docs/consensus-layer/benchmarks/calibration.md#heap)).

**Record** the choice ([the calibration record](/platform-sdk/docs/consensus-layer/benchmarks/calibration.md#the-calibration-record)):
print the block for it and paste it unchanged into the class Javadoc; set the annotations to the same values.

```bash
python3 .github/workflows/support/scripts/benchmarks/analyze_calibration.py \
  --forks 5 --iterations 2 --length 15 --warmup-iterations 1 "$OUT"/calibration/*
```

```java
@Fork(value = 5, jvmArgsAppend = {"-Xms2g", "-Xmx2g", "-XX:+AlwaysPreTouch"})
@Warmup(iterations = 1, time = 15)
@Measurement(iterations = 2, time = 15)
```

**Check:** the annotations and the block equal the script's output for the chosen configuration, the smoke run passes
again ([step 5](steps-4-6.md#step-5-smoke-run)), and, after the push, so do the pull request's checks. Then the pull
request goes to review.

## Step 8: first nightly result

After the merge, the next run of `905-cron-nightly-benchmarks.yaml` measures the benchmark and publishes it.

```bash
gh run list --workflow 905-cron-nightly-benchmarks.yaml --limit 3 --json databaseId,headSha,status,conclusion,createdAt
gh run download <run id> -n jmh-results -D "$OUT/nightly/<run id>"
jq -r '.attempts[] | "\(.benchmark) \(.params) attempt=\(.attempt) quiet=\(.quiet) others=\(.others_cpus) \((.finished - .started) / 60 | floor) min"' \
  "$OUT/nightly/<run id>/runs.json"
python3 .github/workflows/support/scripts/benchmarks/publish_results.py --results "$OUT/nightly/<run id>" --dry-run \
  | grep "$BENCH"
```

**Check:**

- the run's commit (`headSha`) contains the merge;
- every parameter set of the benchmark has a quiet attempt;
- the dry run shows `jmh_latency_seconds` for every statistic, `jmh_allocation_bytes_per_op`, and `jmh_run_seconds`
  for every parameter set;
- `latency.mean` is close to the calibration runs' and `jmh_run_seconds` to the block's runtime, because the machine
  and the configuration are the same.

A night without a quiet result publishes no latency; that is a gap, not a failure
([publishing](/platform-sdk/docs/consensus-layer/benchmarks/operations.md#publishing-and-the-dashboard)). Then check
the next night.

## Step 9: dashboard

The dashboard "ConsensusLayer Benchmarks" (uid `consensuslayer-benchmarks`, from
`.github/workflows/support/scripts/benchmarks/grafana-dashboard.json`) takes its benchmark and parameter drop-downs
from the data, so a new benchmark needs no change to it. Claude cannot reach Grafana: ask the developer to select the
benchmark and each parameter set.

**Check:** the developer sees the night of step 8 in the panels for latency, allocation, percentiles, load, attempts,
and discarded passes, and in the runs log. A change to the dashboard goes into its own pull request and keeps the uid,
so that an import overwrites it.
