package com.hedera.services.bdd.suites.contract.classiccalls;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the saving and loading of call result snapshots.
 */
public enum CallResultsSnapshot {
    CALL_RESULTS_SNAPSHOT;

    private static final String CALL_RESULT_KEY = "callResults";
    private static final String STATIC_CALL_RESULT_KEY = "staticCallResults";
    private static final String SNAPSHOT_LOC = "hedera-node/test-clients/src/main/resource/call-results-snapshot.json";
    private static final Map<String, Map<ClassicFailureMode, FailableCallResult>> CALL_RESULTS = new ConcurrentHashMap<>();
    private static final Map<String, Map<ClassicFailureMode, FailableStaticCallResult>> STATIC_CALL_RESULTS = new ConcurrentHashMap<>();

    public void begin() {
        CALL_RESULTS.clear();
        STATIC_CALL_RESULTS.clear();
    }

    public void load() {
        CALL_RESULTS.clear();
        STATIC_CALL_RESULTS.clear();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public void commit() {
        final var writer = new ObjectMapper().writerWithDefaultPrettyPrinter();
        final Map<String, Map<String, Map<ClassicFailureMode, Object>>> snapshot = new HashMap<>();
        snapshot.put(CALL_RESULT_KEY, (Map) CALL_RESULTS);
        snapshot.put(STATIC_CALL_RESULT_KEY, (Map) STATIC_CALL_RESULTS);
        try {
            writer.writeValue(Files.newOutputStream(Paths.get(SNAPSHOT_LOC)), snapshot);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void recordResult(final String callName, final ClassicFailureMode mode, final FailableCallResult result) {
        CALL_RESULTS.computeIfAbsent(callName, ignore -> new HashMap<>()).put(mode, result);
    }

    public void recordStaticResult(final String callName, final ClassicFailureMode mode, final FailableStaticCallResult result) {
        STATIC_CALL_RESULTS.computeIfAbsent(callName, ignore -> new HashMap<>()).put(mode, result);
    }

    public FailableCallResult expectedResultOf(final String callName, final ClassicFailureMode mode) {
        return CALL_RESULTS.get(callName).get(mode);
    }

    public FailableStaticCallResult expectedStaticCallResultOf(final String callName, final ClassicFailureMode mode) {
        return STATIC_CALL_RESULTS.get(callName).get(mode);
    }
}
