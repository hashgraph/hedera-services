#!/usr/bin/env bash
# SPDX-License-Identifier: Apache-2.0
# Measurement-only F1/F2 campaign. Run on Linux; --dry-run is read-only.
set -euo pipefail
export LC_ALL=C TZ=UTC

# Internal sampler modes run in their own process groups, owned by this runner.
if [[ "${1:-}" == --sample ]]; then
    (( $# == 3 )) || exit 2
    case "$2" in
        top)
            top -b -d 5 -o %CPU -w 180 | awk -v marker="$3" '
                $1 == "top" {
                    cell="unknown"; getline cell < marker; close(marker)
                    print "# sample_utc=" strftime("%Y-%m-%dT%H:%M:%SZ") " cell=" cell
                }
                { print; fflush() }'
            ;;
        vmstat)
            vmstat -w -t 5 | awk -v marker="$3" '
                NR > 2 {
                    cell="unknown"; getline cell < marker; close(marker)
                    print "# sample_utc=" strftime("%Y-%m-%dT%H:%M:%SZ") " cell=" cell
                }
                { print; fflush() }'
            ;;
        diskstats)
            while true; do
                cell=unknown
                if [[ -r "$3" ]]; then
                    read -r cell <"$3" || cell=unknown
                fi
                printf '# sample_utc=%s cell=%s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "${cell}"
                cat /proc/diskstats
                sleep 5
            done
            ;;
        *) exit 2 ;;
    esac
    exit 0
fi

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
SCRIPT_PATH="${SCRIPT_DIR}/$(basename -- "${BASH_SOURCE[0]}")"
REPO_ROOT="$(git -C "${SCRIPT_DIR}" rev-parse --show-toplevel)"
MODULE_DIR="${REPO_ROOT}/platform-sdk/swirlds-benchmarks"
SCRATCH_PARENT="${MODULE_DIR}/build/tmp/merkledb-snapshot-1b-campaign"
FIXTURE_ROOT="${SCRATCH_PARENT}/saved-fixture"
FIXTURE_NAME=fixture-1000000000-k32-r128-cap1000000000-h6-dSHA_384
FIXTURE_DIR="${FIXTURE_ROOT}/${FIXTURE_NAME}"
RESULTS_PARENT="${MODULE_DIR}/build/results/jmh/merkledb-snapshot-1b-campaign/snapshot-experiments"
CACHE_PARENT="${RESULTS_PARENT}/artifact-cache"
PREFLIGHT_SOURCE="${SCRIPT_DIR}/../check-merkledb-snapshot-benchmark-system.sh"
SETTINGS_HASH=6e1d8e86eb196b08b3e2bbdf98493a1360593447235e404584ced9b28350a9c3
INVENTORY_HASH=22d3977ec917456de77b618143c4e765d0a939450e5bc2c6830ef509b00f9c60
BASES=(baseline tasks fixed-pool)
REFS=(bdfd70497908f02f46d754d556a01008332ac7e1 6bb5bcc0b207093c5dda0ddefb6ffb09d4b6eca9 cce428fb84687da882c214d153cdee50428c5bc4)
VARIANTS=(baseline-control baseline-presize tasks-control tasks-presize tasks-chains fixed-pool-control fixed-pool-presize fixed-pool-chains)
IMPLEMENTATIONS=(SEGMENT DISK HEAP OFF_HEAP DISK_SEGMENT)
JAVA="${JAVA_HOME:+${JAVA_HOME}/bin/}java"
JAVA_VERSION_TEXT=""
BENCHMARK='com.swirlds.benchmark.MerkleDbSnapshotBenchmark.snapshot$'
JVM_ARGS='-Xms4g -Xmx32g -XX:MaxDirectMemorySize=16g -Dbenchmark.requireExistingFixture=true'
DRY_RUN=false
RESUME_RUN=""
if (( $# == 1 )) && [[ "$1" == --dry-run ]]; then
    DRY_RUN=true
elif (( $# == 2 )) && [[ "$1" == --resume && "$2" =~ ^snapshot-experiments-[A-Za-z0-9._-]+$ ]]; then
    RESUME_RUN="$2"
elif (( $# != 0 )); then
    echo "Usage: bash $0 [--dry-run | --resume RUN_ID]" >&2
    exit 2
fi
RUN_ID="${RESUME_RUN:-snapshot-experiments-$(date -u +%Y%m%dT%H%M%SZ)-$$}"
RESULTS_DIR="${RESULTS_PARENT}/${RUN_ID}"
FROZEN_DIR="${RESULTS_DIR}/frozen"
ARCHIVE="${RESULTS_DIR}.tar.gz"
SCRATCH_DIR=""
BUILD_DIR=""
ACTIVE_PID=""
ACTIVE_BUILD=false
CONSOLE_PID=""
MONITOR_PIDS=()
STOP_GRACE_SECONDS=5
STOP_KILL_SECONDS=2
SCRATCH_PRESERVED=false
BUILD_PRESERVED=false
CURRENT_CELL=""
CURRENT_CELL_DIR=""
PHASE=initialization
COMPLETED_CELLS=0
CAMPAIGN_DONE=false
INTERRUPTED=false

fail() { echo "ERROR: $*" >&2; exit 1; }
timestamp() { date -u +%Y-%m-%dT%H:%M:%SZ; }
file_hash() { sha256sum -- "$1" | awk '{print $1}'; }
verify_hash() {
    [[ -f "$1" && -r "$1" ]] || fail "Required file is missing: $1"
    [[ "$(file_hash "$1")" == "$2" ]] || fail "Unexpected checksum: $1"
}
hash_directory() {
    (cd -- "$1" && find . -type f ! -path ./SHA256SUMS -print0 | sort -z |
        while IFS= read -r -d '' file; do sha256sum -- "${file}" || exit 1; done) >"$1/SHA256SUMS"
}
fixture_inventory() {
    (cd -- "${FIXTURE_DIR}" && find . -type f -printf '%P\t%s\t%T@\n' | sort)
}
validate_fixture() {
    local destination="$1"
    [[ -d "${FIXTURE_ROOT}" && ! -L "${FIXTURE_ROOT}" && -d "${FIXTURE_DIR}" ]] \
        || fail "The saved fixture is missing. This script never generates a fixture."
    [[ -z "$(find "${FIXTURE_ROOT}" -type l -print -quit)" ]] \
        || fail "The saved fixture must not contain symbolic links."
    verify_hash "${FIXTURE_ROOT}/settings.txt" "${SETTINGS_HASH}"
    verify_hash "${FIXTURE_ROOT}/inventory.txt" "${INVENTORY_HASH}"
    printf '%s\n' layout_version=1 leaf_count=1000000000 num_files=10000 num_records=100000 \
        key_size=32 record_size=128 num_threads=32 | cmp -s - "${FIXTURE_ROOT}/fixture-key.txt" \
        || fail "The saved fixture workload key differs."
    fixture_inventory >"${destination}"
    verify_hash "${destination}" "${INVENTORY_HASH}"
}
write_manifest() {
    {
        echo "status=$1"
        echo "exit_code=$2"
        echo "run_id=${RUN_ID}"
        echo "updated_at=$(timestamp)"
        echo "phase=${PHASE}"
        echo "current_cell=${CURRENT_CELL}"
        echo "completed_forks=${COMPLETED_CELLS}"
        echo "expected_configurations=40"
        echo "expected_forks=120"
        echo "expected_measured_samples=360"
        echo "expected_warmup_snapshots=120"
        echo "fixture_mode=reuse"
        echo "snapshot_force=false"
        echo "hash_cache_overlap=true"
        echo "baseline_writers_per_list=8"
        echo "shared_pool_parallelism=24"
        echo "f2_cursors_per_list=8"
        echo "sampling_interval_seconds=5"
        echo "scratch_preserved=${SCRATCH_PRESERVED}"
        echo "build_worktree_preserved=${BUILD_PRESERVED}"
        [[ "${SCRATCH_PRESERVED}" != true ]] || echo "retained_scratch=${SCRATCH_DIR}"
        [[ "${BUILD_PRESERVED}" != true ]] || echo "retained_build_worktree=${BUILD_DIR}/worktree"
    } >"${RESULTS_DIR}/manifest.txt"
}
write_plan() {
    local block_index block impl_offset variant_offset impl_index variant_index variant base presize cursors
    for block_index in 0 1 2; do
        block="${block_index}"
        case "${block_index}" in 0) block=A ;; 1) block=B ;; 2) block=C ;; esac
        for impl_offset in 0 1 2 3 4; do
            impl_index=$(( (impl_offset + 2 * block_index) % 5 ))
            for variant_offset in 0 1 2 3 4 5 6 7; do
                variant_index=$(( (variant_offset + 3 * block_index + impl_offset) % 8 ))
                variant="${VARIANTS[${variant_index}]}"
                base="${variant%-*}"
                presize=false
                cursors=0
                case "${variant}" in *-presize) presize=true ;; *-chains) cursors=8 ;; esac
                printf '%s\t%s\t%s\t%s\t%s\t%s\n' "${block}" "${variant}" \
                    "${IMPLEMENTATIONS[${impl_index}]}" "${base}" "${presize}" "${cursors}"
            done
        done
    done
}
remove_owned_directory() {
    local directory="$1" prefix="$2"
    [[ -n "${directory}" ]] || return 0
    [[ -d "${directory}" && ! -L "${directory}" \
        && "$(dirname -- "${directory}")" == "${SCRATCH_PARENT}" \
        && "$(basename -- "${directory}")" == "${prefix}".* \
        && -f "${directory}/.campaign-owner" \
        && "$(<"${directory}/.campaign-owner")" == "${RUN_ID}" \
        && "$(cd -- "${directory}" && pwd -P)" == "${directory}" ]] \
        || { echo "Refusing to remove unverified scratch: ${directory}" >&2; return 1; }
    rm -rf -- "${directory}"
}
remove_owned_build() {
    [[ -n "${BUILD_DIR}" ]] || return 0
    [[ -d "${BUILD_DIR}" && ! -L "${BUILD_DIR}" \
        && "$(dirname -- "${BUILD_DIR}")" == "${SCRATCH_PARENT}" \
        && "$(basename -- "${BUILD_DIR}")" == experiment-build.* \
        && -f "${BUILD_DIR}/.campaign-owner" \
        && "$(<"${BUILD_DIR}/.campaign-owner")" == "${RUN_ID}" \
        && "$(cd -- "${BUILD_DIR}" && pwd -P)" == "${BUILD_DIR}" ]] \
        || { echo "Refusing to remove an unverified build worktree: ${BUILD_DIR}" >&2; return 1; }
    if [[ -d "${BUILD_DIR}/worktree" ]]; then
        # This exact detached worktree was created inside the marked directory by this run.
        git -C "${REPO_ROOT}" worktree remove --force "${BUILD_DIR}/worktree" || return 1
    fi
    remove_owned_directory "${BUILD_DIR}" experiment-build || return 1
    BUILD_DIR=""
}
collect_cell_logs() {
    [[ -n "${SCRATCH_DIR}" && -d "${SCRATCH_DIR}" && -n "${CURRENT_CELL_DIR}" ]] || return 0
    [[ ! -f "${SCRATCH_DIR}/settingsUsed.txt" ]] \
        || cp -- "${SCRATCH_DIR}/settingsUsed.txt" "${CURRENT_CELL_DIR}/settingsUsed.txt"
    if [[ -d "${SCRATCH_DIR}/output" ]]; then
        mkdir -p -- "${CURRENT_CELL_DIR}/logs"
        cp -R -- "${SCRATCH_DIR}/output/." "${CURRENT_CELL_DIR}/logs/"
    fi
}
owned_group_alive() {
    # Include the direct child during the brief window before setsid establishes its group.
    kill -0 -- "-$1" 2>/dev/null || kill -0 "$1" 2>/dev/null
}
stop_owned_group() {
    local pid="$1" label="$2" deadline
    # Callers pass only group leaders created by this runner with setsid.
    [[ "${pid}" =~ ^[0-9]+$ && "${pid}" -gt 1 && "${pid}" != "$$" ]] || return 1
    if owned_group_alive "${pid}"; then
        kill -TERM -- "-${pid}" 2>/dev/null || true
        kill -TERM "${pid}" 2>/dev/null || true
        deadline=$((SECONDS + STOP_GRACE_SECONDS))
        while owned_group_alive "${pid}" && (( SECONDS < deadline )); do sleep 0.1; done
        if owned_group_alive "${pid}"; then
            echo "Stopping remaining owned ${label} processes with KILL (group ${pid})." >&2
            kill -KILL -- "-${pid}" 2>/dev/null || true
            kill -KILL "${pid}" 2>/dev/null || true
            deadline=$((SECONDS + STOP_KILL_SECONDS))
            while owned_group_alive "${pid}" && (( SECONDS < deadline )); do sleep 0.1; done
        fi
    fi
    if owned_group_alive "${pid}"; then
        echo "Owned ${label} process group ${pid} is still present; preserving its files." >&2
        return 1
    fi
    wait "${pid}" 2>/dev/null || true
}
stop_monitoring() {
    local pid rc=0
    local -a remaining=()
    for pid in "${MONITOR_PIDS[@]}"; do
        if ! stop_owned_group "${pid}" sampler; then
            remaining+=("${pid}")
            rc=1
        fi
    done
    MONITOR_PIDS=("${remaining[@]}")
    return "${rc}"
}
finish_run() {
    local rc=$? status=failed processes_stopped=true
    trap - EXIT INT TERM
    set +e
    if [[ -n "${ACTIVE_PID}" ]]; then
        stop_owned_group "${ACTIVE_PID}" command || { rc=1; processes_stopped=false; }
    fi
    if [[ -n "${CONSOLE_PID}" ]]; then
        stop_owned_group "${CONSOLE_PID}" console || { rc=1; processes_stopped=false; }
    fi
    stop_monitoring || { rc=1; processes_stopped=false; }
    cd -- "${REPO_ROOT}" || rc=1
    if [[ "${processes_stopped}" == true ]]; then
        collect_cell_logs || rc=1
        remove_owned_directory "${SCRATCH_DIR}" run || rc=1
    elif [[ -n "${SCRATCH_DIR}" ]]; then
        SCRATCH_PRESERVED=true
        echo "Scratch retained for inspection: ${SCRATCH_DIR}" >&2
    fi
    if [[ -n "${BUILD_DIR}" && ( "${ACTIVE_BUILD}" == true || "${processes_stopped}" != true ) ]]; then
        # Even --no-daemon may use a detached single-use Gradle daemon outside our group.
        BUILD_PRESERVED=true
        echo "Build worktree retained for inspection: ${BUILD_DIR}/worktree" >&2
        echo "A detached Gradle worker may still be using it; no unrelated process was stopped." >&2
    else
        remove_owned_build || rc=1
    fi
    if [[ "${INTERRUPTED}" == true ]]; then
        status=interrupted
    elif [[ "${CAMPAIGN_DONE}" == true && "${rc}" == 0 ]]; then
        status=complete
        PHASE=complete
    elif (( rc == 0 )); then
        rc=1
    fi
    if [[ -n "${CURRENT_CELL_DIR}" && -d "${CURRENT_CELL_DIR}" ]]; then
        printf 'status=%s\nexit_code=%s\nupdated_at=%s\n' "${status}" "${rc}" "$(timestamp)" \
            >"${CURRENT_CELL_DIR}/attempt-status.txt"
    fi
    write_manifest "${status}" "${rc}"
    printf '%s\t%s\t%s\n' "$(timestamp)" "${status}" "${rc}" >>"${RESULTS_DIR}/sessions.tsv"
    if [[ "${processes_stopped}" != true ]]; then
        echo "Live owned processes remain. Results were retained at ${RESULTS_DIR}; no unstable archive was created." >&2
        exit "${rc}"
    fi
    if ! hash_directory "${RESULTS_DIR}"; then
        rc=1
        status=failed
        write_manifest "${status}" "${rc}"
    fi
    if tar -C "${RESULTS_PARENT}" -czf "${ARCHIVE}.partial.$$" "${RUN_ID}" \
        && mv -- "${ARCHIVE}.partial.$$" "${ARCHIVE}"; then
        echo "Results archive (${status}): ${ARCHIVE}"
    else
        echo "Archive creation failed; results remain at ${RESULTS_DIR}" >&2
        rc=1
        write_manifest failed "${rc}"
        hash_directory "${RESULTS_DIR}"
    fi
    exit "${rc}"
}
run_logged() {
    local log="$1" rc=0
    shift
    setsid "$@" >"${log}" 2>&1 &
    ACTIVE_PID=$!
    setsid tail --pid="${ACTIVE_PID}" -n +1 -f "${log}" &
    CONSOLE_PID=$!
    wait "${ACTIVE_PID}" || rc=$?
    # A reaped leader alone is insufficient: descendants can keep this group alive.
    stop_owned_group "${ACTIVE_PID}" command || return 1
    ACTIVE_PID=""
    wait "${CONSOLE_PID}" || rc=1
    CONSOLE_PID=""
    return "${rc}"
}
build_artifact() {
    local base="$1" ref="$2" patch="${FROZEN_DIR}/$1.patch" cache_key cache destination worktree artifact
    cache_key="${ref}-$(file_hash "${patch}")"
    cache="${CACHE_PARENT}/${cache_key}"
    destination="${FROZEN_DIR}/${base}"
    PHASE="build-${base}"
    write_manifest running 0
    if [[ -e "${cache}" ]]; then
        [[ -d "${cache}" && ! -L "${cache}" && -f "${cache}/SHA256SUMS" ]] \
            || fail "Invalid artifact cache: ${cache}"
        (cd -- "${cache}" && sha256sum --check SHA256SUMS) || fail "Artifact cache checksum failure."
        printf '%s\n' "${JAVA_VERSION_TEXT}" | cmp -s - "${cache}/java-version.txt" \
            || fail "The cached build uses a different JDK. Select its recorded JDK before reuse."
        echo "Reusing frozen build: ${base} (${cache_key})"
    else
        BUILD_DIR="$(mktemp -d "${SCRATCH_PARENT}/experiment-build.XXXXXX")"
        printf '%s\n' "${RUN_ID}" >"${BUILD_DIR}/.campaign-owner"
        worktree="${BUILD_DIR}/worktree"
        artifact="${BUILD_DIR}/artifact"
        mkdir -- "${artifact}"
        git -C "${REPO_ROOT}" worktree add --detach "${worktree}" "${ref}"
        git -C "${worktree}" apply --check "${patch}"
        git -C "${worktree}" apply "${patch}"
        cp -- "${patch}" "${artifact}/source.patch"
        printf '%s\n' "${JAVA_VERSION_TEXT}" >"${artifact}/java-version.txt"
        printf '%s\n' "${ref}" >"${artifact}/base-ref.txt"
        git -C "${worktree}" diff --binary HEAD >"${artifact}/tracked-source.diff"
        git -C "${worktree}" status --short >"${artifact}/source-status.txt"
        local -a command=("${worktree}/gradlew" -p "${worktree}" --no-daemon --console=plain
            :swirlds-merkledb:test --tests '*LongList*ExperimentTest' :swirlds-benchmarks:jmhJar)
        printf '%q ' "${command[@]}" >"${artifact}/build-command.txt"
        printf '\n' >>"${artifact}/build-command.txt"
        cp -- "${artifact}/build-command.txt" "${RESULTS_DIR}/build-${base}-command.txt"
        echo "Building and testing ${base} from ${ref} (temporary detached worktree)."
        ACTIVE_BUILD=true
        run_logged "${RESULTS_DIR}/build-${base}.log" "${command[@]}"
        ACTIVE_BUILD=false
        cp -- "${RESULTS_DIR}/build-${base}.log" "${artifact}/build.log"
        local -a jars=()
        shopt -s nullglob
        jars=("${worktree}/platform-sdk/swirlds-benchmarks/build/libs/"*-jmh.jar)
        shopt -u nullglob
        (( ${#jars[@]} == 1 )) || fail "Expected exactly one newly built JMH JAR for ${base}."
        cp -- "${jars[0]}" "${artifact}/jmh.jar"
        if [[ -d "${worktree}/platform-sdk/swirlds-merkledb/build/test-results/test" ]]; then
            cp -R -- "${worktree}/platform-sdk/swirlds-merkledb/build/test-results/test" "${artifact}/test-results"
        fi
        "${JAVA}" -jar "${artifact}/jmh.jar" "${BENCHMARK}" -lp >"${artifact}/benchmark-parameters.txt" 2>&1
        local parameter=snapshotThreads
        [[ "${base}" != baseline ]] || parameter=threadsPerLongList
        awk -v name="\"${parameter}\"" '$1 == "param" && $2 == name { found=1 } END { exit !found }' \
            "${artifact}/benchmark-parameters.txt" || fail "The ${base} JAR is missing ${parameter}."
        hash_directory "${artifact}"
        find "${artifact}" -type f -exec chmod a-w {} +
        mv -- "${artifact}" "${cache}"
        remove_owned_build
    fi
    mkdir -- "${destination}"
    cp -R -- "${cache}/." "${destination}/"
    printf '%s\t%s\t%s\t%s\n' "${base}" "${ref}" "$(file_hash "${patch}")" \
        "$(file_hash "${destination}/jmh.jar")" >>"${FROZEN_DIR}/artifacts.tsv"
}
validate_cell_json() {
    local json="$1" implementation="$2" parameter="$3" value="$4" presize="$5" cursors="$6"
    jq -e --arg implementation "${implementation}" --arg parameter "${parameter}" --arg value "${value}" \
        --arg presize "${presize}" --arg cursors "${cursors}" '
        type == "array" and length == 1 and
        (.[0] as $r |
            $r.benchmark == "com.swirlds.benchmark.MerkleDbSnapshotBenchmark.snapshot" and
            $r.params.longListImplementation == $implementation and $r.params[$parameter] == $value and
            $r.params.prepareFixtureOnly == "false" and $r.params.numFiles == "10000" and
            $r.params.numRecords == "100000" and $r.params.maxKey == "1000000000" and
            $r.params.keySize == "32" and $r.params.recordSize == "128" and
            $r.params.numThreads == "32" and
            ($r.jvmArgs | index("-Dbenchmark.requireExistingFixture=true")) != null and
            ($r.jvmArgs | index("-Dmerkledb.experiment.presizeIndexFiles=" + $presize)) != null and
            ($r.jvmArgs | index("-Dmerkledb.experiment.cursorsPerList=" + $cursors)) != null and
            $r.mode == "ss" and $r.threads == 1 and $r.forks == 1 and
            $r.warmupIterations == 1 and $r.measurementIterations == 3 and
            $r.primaryMetric.scoreUnit == "ms/op" and
            ($r.primaryMetric.rawData | length) == 1 and
            ($r.primaryMetric.rawData[0] | length) == 3 and
            all($r.primaryMetric.rawData[0][]; type == "number" and isfinite and . > 0))
    ' "${json}" >/dev/null
}
start_monitoring() {
    local sampler
    mkdir -p -- "${RESULTS_DIR}/sampling"
    printf '%s\n' between-cells >"${RESULTS_DIR}/sampling/current-cell.txt"
    for sampler in top vmstat diskstats; do
        printf '# session_started_utc=%s\n' "$(timestamp)" >>"${RESULTS_DIR}/sampling/${sampler}.log"
        setsid bash "${FROZEN_DIR}/runner.sh" --sample "${sampler}" \
            "${RESULTS_DIR}/sampling/current-cell.txt" >>"${RESULTS_DIR}/sampling/${sampler}.log" 2>&1 &
        MONITOR_PIDS+=("$!")
    done
    sleep 1
    check_monitoring
}
check_monitoring() {
    local pid
    for pid in "${MONITOR_PIDS[@]}"; do
        kill -0 "${pid}" 2>/dev/null || fail "A sampler stopped; inspect sampling logs."
    done
}
publish_completed_attempt() {
    local cell_root="$1" attempt_dir="${CURRENT_CELL_DIR}"
    hash_directory "${attempt_dir}"
    find "${attempt_dir}" -type f -exec chmod a-w {} +
    # The exit trap must never rewrite a finalized, checksummed attempt.
    CURRENT_CELL_DIR=""
    printf '%s\n' "$(basename -- "${attempt_dir}")" >"${cell_root}/complete.pending.$$"
    mv -- "${cell_root}/complete.pending.$$" "${cell_root}/complete"
}
run_cell() {
    local block="$1" variant="$2" implementation="$3" base="$4" presize="$5" cursors="$6"
    local parameter=snapshotThreads value=24 cell_root attempt
    [[ "${base}" != baseline ]] || { parameter=threadsPerLongList; value=8; }
    CURRENT_CELL="${block}-${variant}-${implementation}"
    cell_root="${RESULTS_DIR}/cells/${CURRENT_CELL}"
    mkdir -p -- "${cell_root}"
    if [[ -f "${cell_root}/complete" ]]; then
        attempt="$(<"${cell_root}/complete")"
        [[ "${attempt}" =~ ^attempt\.[A-Za-z0-9]+$ ]] || fail "Invalid completed attempt for ${CURRENT_CELL}."
        (cd -- "${cell_root}/${attempt}" && sha256sum --check SHA256SUMS) \
            || fail "Completed cell checksum failure: ${CURRENT_CELL}"
        validate_cell_json "${cell_root}/${attempt}/result.json" "${implementation}" "${parameter}" "${value}" "${presize}" "${cursors}" \
            || fail "Completed cell JSON is invalid: ${CURRENT_CELL}"
        COMPLETED_CELLS=$((COMPLETED_CELLS + 1))
        echo "Already complete: ${CURRENT_CELL}"
        return 0
    fi
    CURRENT_CELL_DIR="$(mktemp -d "${cell_root}/attempt.XXXXXX")"
    printf 'status=running\ncell=%s\nstarted_at=%s\n' "${CURRENT_CELL}" "$(timestamp)" \
        >"${CURRENT_CELL_DIR}/attempt-status.txt"
    SCRATCH_DIR="$(mktemp -d "${SCRATCH_PARENT}/run.XXXXXX")"
    printf '%s\n' "${RUN_ID}" >"${SCRATCH_DIR}/.campaign-owner"
    printf '%s\n' "${SCRATCH_DIR}" >"${CURRENT_CELL_DIR}/scratch-path.txt"
    cp -- "${FROZEN_DIR}/settings.txt" "${SCRATCH_DIR}/settings.txt"
    mkdir -p -- "${SCRATCH_DIR}/data/MerkleDbSnapshotBenchmark"
    cp -al -- "${FIXTURE_DIR}" "${SCRATCH_DIR}/data/MerkleDbSnapshotBenchmark/"
    [[ -d "${SCRATCH_DIR}/data/MerkleDbSnapshotBenchmark/${FIXTURE_NAME}" ]] \
        || fail "The fixture link was not installed; refusing to launch JMH."
    local -a command=("${JAVA}" "-Djava.io.tmpdir=${SCRATCH_PARENT}/jmh-tmp"
        -jar "${FROZEN_DIR}/${base}/jmh.jar" "${BENCHMARK}"
        -p "longListImplementation=${implementation}" -p "${parameter}=${value}"
        -p prepareFixtureOnly=false -p numFiles=10000 -p numRecords=100000 -p maxKey=1000000000
        -p keySize=32 -p recordSize=128 -p numThreads=32
        -t 1 -bm ss -tu ms -wi 1 -i 3 -f 1 -to 60m -foe true -rf json
        -rff "${CURRENT_CELL_DIR}/result.json"
        -jvmArgs "${JVM_ARGS} -Dmerkledb.experiment.presizeIndexFiles=${presize} -Dmerkledb.experiment.cursorsPerList=${cursors}")
    printf '%q ' "${command[@]}" >"${CURRENT_CELL_DIR}/command.txt"
    printf '\n' >>"${CURRENT_CELL_DIR}/command.txt"
    cat "${CURRENT_CELL_DIR}/command.txt" >>"${RESULTS_DIR}/commands.txt"
    printf '%s\tstart\t%s\n' "$(timestamp)" "${CURRENT_CELL}" >>"${RESULTS_DIR}/sampling/cell-boundaries.tsv"
    printf '%s\n' "${CURRENT_CELL}" >"${RESULTS_DIR}/sampling/current-cell.txt"
    PHASE="cell-${CURRENT_CELL}"
    write_manifest running 0
    echo "Running ${CURRENT_CELL} ($((COMPLETED_CELLS + 1))/120) — $(timestamp)"
    cd -- "${SCRATCH_DIR}"
    run_logged "${CURRENT_CELL_DIR}/console.log" "${command[@]}"
    cd -- "${REPO_ROOT}"
    validate_cell_json "${CURRENT_CELL_DIR}/result.json" "${implementation}" "${parameter}" "${value}" "${presize}" "${cursors}" \
        || fail "Invalid result JSON for ${CURRENT_CELL}."
    collect_cell_logs
    remove_owned_directory "${SCRATCH_DIR}" run
    SCRATCH_DIR=""
    printf 'status=complete\nexit_code=0\nupdated_at=%s\n' "$(timestamp)" \
        >"${CURRENT_CELL_DIR}/attempt-status.txt"
    publish_completed_attempt "${cell_root}"
    COMPLETED_CELLS=$((COMPLETED_CELLS + 1))
    printf '%s\tcomplete\t%s\n' "$(timestamp)" "${CURRENT_CELL}" >>"${RESULTS_DIR}/sampling/cell-boundaries.tsv"
    printf '%s\n' between-cells >"${RESULTS_DIR}/sampling/current-cell.txt"
    check_monitoring
}
main() {
    if [[ "${DRY_RUN}" == true ]]; then
        echo "Plan only: no writes, worktrees, builds, fixture generation, or benchmarks."
        echo "Build three exact historical refs, with experiment patches, in temporary detached worktrees."
        echo "Run targeted experiment tests and jmhJar sequentially with Gradle --no-daemon."
        echo "Cache frozen builds by full source ref and patch SHA-256; resume never rebuilds."
        echo "Saved fixture: ${FIXTURE_DIR}"
        echo "All five implementations; baseline P=8, pools B=24, F2 K=8; force off, overlap on."
        echo "Eight variants, no F1+F2 combination, three rotated/interleaved blocks."
        echo "120 forks: 120 warmups + 360 measured snapshots. top/vmstat/diskstats every 5 seconds."
        echo "Expect about 2.5–3.5 hours for measurement, plus builds and archiving."
        echo "Plan columns: block, variant, implementation, source, F1 presize, F2 cursors per list."
        write_plan
        return 0
    fi
    [[ "$(uname -s)" == Linux ]] || fail "Run on Linux; --dry-run is available elsewhere."
    local tool i version
    for tool in "${JAVA}" git sha256sum jq top vmstat setsid tail flock sync tar find sort cmp; do
        command -v "${tool}" >/dev/null || fail "Required command is missing: ${tool}"
    done
    JAVA_VERSION_TEXT="$("${JAVA}" -version 2>&1)"
    version="$(printf '%s\n' "${JAVA_VERSION_TEXT}" | awk -F'"' 'NR == 1 {print $2}')"
    [[ "${version}" == 25.0.2* ]] || fail "Java 25.0.2 is required; set JAVA_HOME."
    [[ -d "${FIXTURE_ROOT}" && ! -L "${FIXTURE_ROOT}" ]] || fail "No saved fixture; nothing will be generated."
    SCRATCH_PARENT="$(cd -- "${SCRATCH_PARENT}" && pwd -P)"
    mkdir -p -- "${RESULTS_PARENT}" "${CACHE_PARENT}" "${SCRATCH_PARENT}/jmh-tmp"
    exec 9>"${SCRATCH_PARENT}/snapshot-experiments.lock"
    flock -n 9 || fail "Another experiment runner owns the campaign lock. Nothing was stopped."
    if [[ -n "${RESUME_RUN}" ]]; then
        [[ -d "${RESULTS_DIR}" && ! -L "${RESULTS_DIR}" && -f "${FROZEN_DIR}/SHA256SUMS" ]] \
            || fail "No frozen inputs for run: ${RUN_ID}"
        (cd -- "${FROZEN_DIR}" && sha256sum --check SHA256SUMS) || fail "Frozen input checksum failure."
        cmp -s "${SCRIPT_PATH}" "${FROZEN_DIR}/runner.sh" || fail "Resume with the unchanged runner recorded by this run."
        printf '%s\n' "${JAVA_VERSION_TEXT}" | cmp -s - "${FROZEN_DIR}/java-version.txt" \
            || fail "The current JDK differs from this run's frozen JDK. Select its recorded JDK before resuming."
    else
        for i in 0 1 2; do
            git -C "${REPO_ROOT}" cat-file -e "${REFS[${i}]}^{commit}" \
                || fail "Required historical ref is unavailable: ${REFS[${i}]}"
            [[ -f "${SCRIPT_DIR}/${BASES[${i}]}.patch" ]] || fail "Missing experiment patch: ${BASES[${i}]}.patch"
        done
        mkdir -- "${RESULTS_DIR}"
        mkdir -- "${FROZEN_DIR}"
    fi
    trap finish_run EXIT
    trap 'INTERRUPTED=true; exit 130' INT
    trap 'INTERRUPTED=true; exit 143' TERM
    printf '%s\tstarted\t%s\n' "$(timestamp)" "${RESUME_RUN:-new}" >>"${RESULTS_DIR}/sessions.tsv"
    write_manifest running 0
    if [[ -z "${RESUME_RUN}" ]]; then
        validate_fixture "${RESULTS_DIR}/fixture-inventory-before.txt"
        cp -- "${FIXTURE_ROOT}/settings.txt" "${FIXTURE_ROOT}/fixture-key.txt" "${FIXTURE_ROOT}/origin.txt" "${FROZEN_DIR}/"
        cp -- "${SCRIPT_PATH}" "${FROZEN_DIR}/runner.sh"
        printf '%s\n' "${JAVA_VERSION_TEXT}" >"${FROZEN_DIR}/java-version.txt"
        cp -- "${PREFLIGHT_SOURCE}" "${FROZEN_DIR}/preflight.sh"
        for i in 0 1 2; do
            cp -- "${SCRIPT_DIR}/${BASES[${i}]}.patch" "${FROZEN_DIR}/"
        done
        write_plan >"${FROZEN_DIR}/plan.tsv"
        {
            echo "Recorded at: $(timestamp)"
            echo "Launcher checkout (not artifact source): $(git -C "${REPO_ROOT}" rev-parse HEAD)"
            "${JAVA}" -version
            uname -sr
            lscpu
            lsblk -o NAME,MODEL,SIZE,ROTA,TRAN,TYPE,MOUNTPOINTS
            findmnt -T "${FIXTURE_DIR}" -o TARGET,SOURCE,FSTYPE,OPTIONS
        } >"${FROZEN_DIR}/environment.txt" 2>&1
        # Check only before builds and before measurement, never during benchmark load.
        bash "${FROZEN_DIR}/preflight.sh" "${MODULE_DIR}" "" reuse \
            | tee "${RESULTS_DIR}/pre-build-system-check.txt"
        for i in 0 1 2; do build_artifact "${BASES[${i}]}" "${REFS[${i}]}"; done
        hash_directory "${FROZEN_DIR}"
        find "${FROZEN_DIR}" -type f -exec chmod a-w {} +
    fi
    validate_fixture "${RESULTS_DIR}/fixture-inventory-session-before.txt"
    PHASE=measurement-system-check
    write_manifest running 0
    sync -f -- "${RESULTS_DIR}"
    bash "${FROZEN_DIR}/preflight.sh" "${MODULE_DIR}" "" reuse \
        | tee -a "${RESULTS_DIR}/measurement-system-check.txt"
    start_monitoring
    local block variant implementation base presize cursors
    while IFS=$'\t' read -r block variant implementation base presize cursors; do
        run_cell "${block}" "${variant}" "${implementation}" "${base}" "${presize}" "${cursors}"
    done <"${FROZEN_DIR}/plan.tsv"
    stop_monitoring
    CURRENT_CELL=""
    PHASE=fixture-verification
    validate_fixture "${RESULTS_DIR}/fixture-inventory-after.txt"
    (( COMPLETED_CELLS == 120 )) || fail "Expected 120 completed cell forks; found ${COMPLETED_CELLS}."
    CAMPAIGN_DONE=true
}

main
