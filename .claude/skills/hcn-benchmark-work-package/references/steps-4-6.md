# Steps 4–6: Tracking, Smoke Run, Pull Request

Rules: [operations.md](/platform-sdk/docs/consensus-layer/benchmarks/operations.md), sections "Tracking a benchmark",
"The smoke run", and "The nightly run". Variables as in [steps-1-3.md](steps-1-3.md). The driver needs Python 3.10 or
newer (macOS ships 3.9) and `JAVA_HOME` set to the build's JDK.

## Step 4: track

Apply [tracking a benchmark](/platform-sdk/docs/consensus-layer/benchmarks/operations.md#tracking-a-benchmark). In the
module's `build.gradle.kts`, as in `consensus-hashgraph-impl`:

```kotlin
plugins {
    id("org.hiero.gradle.feature.benchmark")
    id("org.hiero.gradle.feature.benchmark-tracking")
}

jmh {
    // the benchmarks that are tracked; -PjmhTests=<pattern> runs others instead
    includes.convention(listOf("ConsensusImplBenchmark"))
}
```

- A module that already applies the plugin only adds the benchmark to the list.
- The smoke run runs every benchmark of the module, tracked or not. If another benchmark of the module fails or hangs
  in it, tell the developer before applying the plugin.
- A benchmark with an expensive setup, or one that needs more than the plugin's default smoke heap, sets
  `benchmarkTracking { smokeJvmArgs = "..."; smokeArgs = listOf("-p", "<one representative parameter set>") }`.

**Check:** the manifest names the benchmark, and the nightly driver runs every parameter set from it.

```bash
./gradlew ":$MODULE:jmhTracked"
cat "platform-sdk/$MODULE/build/jmh/tracked.properties"      # includes=... names the benchmark
python3 .github/workflows/support/scripts/benchmarks/run_benchmarks.py \
  --manifest "platform-sdk/$MODULE/build/jmh/tracked.properties" --out "$OUT/driver" -- -f 1 -wi 0 -i 1 -r 1s
jq -r '.attempts[] | "\(.benchmark) \(.params) exit=\(.exit_code)"' "$OUT/driver/runs.json"
```

Every parameter set has an attempt with exit code 0, and the forks' `jvmArgs` in its JSON start with
`-XX:ActiveProcessorCount=12`, followed by the benchmark's heap. Outside Linux the driver has no load monitor: it waits
about 20 seconds per parameter set and reports the slot as unknown, which is expected locally.

## Step 5: smoke run

Apply [the smoke run](/platform-sdk/docs/consensus-layer/benchmarks/operations.md#the-smoke-run).

**Check:** it passes, a rerun is up to date, and without its output it comes from the build cache. `--rerun` makes the
first run execute even when the cache already holds a result for the same jar.

```bash
./gradlew ":$MODULE:jmhSmoke" --rerun --console=plain | grep -E "jmhSmoke|BUILD"   # executes, BUILD SUCCESSFUL
./gradlew ":$MODULE:jmhSmoke" --console=plain | grep jmhSmoke                     # UP-TO-DATE
rm -rf "platform-sdk/$MODULE/build/results/jmh-smoke"
./gradlew ":$MODULE:jmhSmoke" --console=plain | grep jmhSmoke                     # FROM-CACHE
jq -r '.[] | "\(.benchmark) \(.params) " + (.secondaryMetrics | keys | join(","))' \
  "platform-sdk/$MODULE/build/results/jmh-smoke/results.json"
```

The results hold the benchmark's metrics for every parameter set it ran with. A smoke run checks only what the output
check of step 1 checks.

## Step 6: pull request

- **One pull request for the work package**, on a branch named after the issue, from `origin/main`:
  `git switch --no-track -c <issue>-<slug> origin/main`. The developer commits and pushes.
- **Open it as a draft.** Its description holds the decisions from the issue, what each "thing to check" turned out to
  be, and the output of every check so far. It goes to review once step 7 has calibrated the branch.
- **On merge, the nightly job picks the benchmark up:** it finds every module that applies the plugin
  ([the nightly run](/platform-sdk/docs/consensus-layer/benchmarks/operations.md#the-nightly-run)). No workflow
  changes.

**Check:** the smoke job passes on the pull request. It runs when the pull request changes code, not for documentation
only.

```bash
gh pr checks <pr> | grep "Benchmark Smoke"      # MATS / Benchmark Smoke / Benchmark Smoke Run  pass
```
