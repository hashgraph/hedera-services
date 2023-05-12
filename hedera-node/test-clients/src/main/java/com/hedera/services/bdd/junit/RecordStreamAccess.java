/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hedera.services.bdd.junit;

import static com.hedera.node.app.hapi.utils.exports.recordstreaming.RecordStreamingUtils.orderedRecordFilesFrom;
import static com.hedera.node.app.hapi.utils.exports.recordstreaming.RecordStreamingUtils.orderedSidecarFilesFrom;
import static com.hedera.node.app.hapi.utils.exports.recordstreaming.RecordStreamingUtils.parseRecordFileConsensusTime;
import static com.hedera.node.app.hapi.utils.exports.recordstreaming.RecordStreamingUtils.parseSidecarFileConsensusTimeAndSequenceNo;
import static com.hedera.node.app.hapi.utils.exports.recordstreaming.RecordStreamingUtils.readMaybeCompressedRecordStreamFile;
import static com.hedera.node.app.hapi.utils.keys.Ed25519Utils.TEST_CLIENTS_PREFIX;
import static com.hedera.node.app.hapi.utils.keys.Ed25519Utils.relocatedIfNotPresentWithCurrentPathPrefix;

import com.hedera.node.app.hapi.utils.exports.recordstreaming.RecordStreamingUtils;
import com.hedera.services.stream.proto.RecordStreamFile;
import com.hedera.services.stream.proto.SidecarFile;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.apache.commons.io.monitor.FileAlterationMonitor;
import org.apache.commons.io.monitor.FileAlterationObserver;

/**
 * A singleton that provides near real-time access to the record stream files for all concurrently
 * executing {@link com.hedera.services.bdd.spec.HapiSpec}'s.
 */
public enum RecordStreamAccess {
    RECORD_STREAM_ACCESS;

    private static final int MONITOR_INTERVAL_MS = 250;

    /**
     * A map of record stream file locations to the listeners that are watching them. (In general we
     * only validate records from the single node0, but this could change?)
     */
    private final Map<String, BroadcastingRecordStreamListener> validatingListeners = new ConcurrentHashMap<>();

    /** A bit of infrastructure that runs the polling loop for all the listeners. */
    private final FileAlterationMonitor monitor = new FileAlterationMonitor(MONITOR_INTERVAL_MS);

    record Data(List<RecordWithSidecars> records, List<RecordStreamFile> files) {}

    /**
     * Stops the polling loop for record stream access if there are no listeners for any location.
     */
    public synchronized void stopMonitorIfNoSubscribers() {
        // Count the number of subscribers (could derive from more than one concurrent HapiSpec)
        final var numSubscribers = validatingListeners.values().stream()
                .mapToInt(BroadcastingRecordStreamListener::numListeners)
                .sum();
        if (numSubscribers == 0) {
            try {
                // Will throw ISE if already stopped, ignore that
                monitor.stop();
            } catch (Exception ignore) {
            }
        }
    }

    /**
     * If the given location is not already being watched, starts a new listener for it and returns
     * the listener.
     *
     * @param loc the record stream file location to watch
     * @return the listener for the given location
     * @throws Exception if there is an error starting the listener
     */
    public synchronized BroadcastingRecordStreamListener getValidatingListener(final String loc) throws Exception {
        if (!validatingListeners.containsKey(loc)) {
            // In most cases should let us run HapiSpec#main() from both the root and test-clients/
            // directories
            var fAtLoc = relocatedIfNotPresentWithCurrentPathPrefix(new File(loc), "..", TEST_CLIENTS_PREFIX);
            if (!fAtLoc.exists()) {
                throw new IllegalArgumentException("No such record stream file location: " + fAtLoc.getAbsolutePath());
            }
            validatingListeners.put(loc, newValidatingListener(fAtLoc.getAbsolutePath()));
        }
        return validatingListeners.get(loc);
    }

    /**
     * Reads the record and sidecar stream files from a given directory.
     *
     * @param loc the directory to read from
     * @param relativeSidecarLoc the relative location of the sidecar files
     * @return the list of record and sidecar files
     * @throws IOException if there is an error reading the files
     */
    public Data readStreamDataFrom(String loc, final String relativeSidecarLoc) throws IOException {
        final var fAtLoc = relocatedIfNotPresentWithCurrentPathPrefix(new File(loc), "..", TEST_CLIENTS_PREFIX);
        loc = fAtLoc.getAbsolutePath();
        final var recordFiles = orderedRecordFilesFrom(loc, f -> true);
        final var sidecarLoc = loc + File.separator + relativeSidecarLoc;
        final List<String> sidecarFiles;
        if (new File(sidecarLoc).exists()) {
            sidecarFiles = orderedSidecarFilesFrom(sidecarLoc);
        } else {
            sidecarFiles = List.of();
        }
        final var sidecarFilesByRecordFile = sidecarFiles.stream()
                .collect(Collectors.groupingBy(
                        f -> parseSidecarFileConsensusTimeAndSequenceNo(f).getLeft(), Collectors.toList()));
        final List<RecordStreamFile> fullRecordFiles = new ArrayList<>();
        final var recordsWithSideCars = recordFiles.stream()
                .map(f -> {
                    final var recordFile = ensurePresentRecordFile(f);
                    fullRecordFiles.add(recordFile);
                    return new RecordWithSidecars(
                            recordFile,
                            sidecarFilesByRecordFile
                                    .getOrDefault(parseRecordFileConsensusTime(f), Collections.emptyList())
                                    .stream()
                                    .map(this::ensurePresentSidecarFile)
                                    .toList());
                })
                .toList();
        return new Data(recordsWithSideCars, fullRecordFiles);
    }

    static RecordStreamFile ensurePresentRecordFile(final String f) {
        try {
            final var contents = readMaybeCompressedRecordStreamFile(f);
            if (contents.getRight().isEmpty()) {
                throw new IllegalArgumentException("No record found in " + f);
            }
            return contents.getRight().get();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read record stream file " + f, e);
        }
    }

    private SidecarFile ensurePresentSidecarFile(final String f) {
        try {
            return RecordStreamingUtils.readMaybeCompressedSidecarFile(f);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read record stream file " + f, e);
        }
    }

    private BroadcastingRecordStreamListener newValidatingListener(final String loc) throws Exception {
        final var observer = new FileAlterationObserver(loc);
        final var listener = new BroadcastingRecordStreamListener();
        observer.addListener(listener);
        monitor.addObserver(observer);
        try {
            // Will throw ISE if already started, ignore that
            monitor.start();
        } catch (IllegalStateException ignore) {
        }
        return listener;
    }
}
