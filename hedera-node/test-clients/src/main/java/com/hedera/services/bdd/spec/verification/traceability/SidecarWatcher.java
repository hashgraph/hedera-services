/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.spec.verification.traceability;

import static java.util.Objects.requireNonNull;

import com.hedera.node.app.hapi.utils.exports.recordstreaming.RecordStreamingUtils;
import com.hedera.services.stream.proto.SidecarFile;
import com.hedera.services.stream.proto.TransactionSidecarRecord;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import org.apache.commons.io.monitor.FileAlterationListenerAdaptor;
import org.apache.commons.io.monitor.FileAlterationMonitor;
import org.apache.commons.io.monitor.FileAlterationObserver;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.testcontainers.shaded.org.hamcrest.Description;
import org.testcontainers.shaded.org.hamcrest.Matcher;
import org.testcontainers.shaded.org.hamcrest.StringDescription;

@SuppressWarnings("java:S1192") // "String literals should not be duplicated" - would impair readability here
public class SidecarWatcher {

    public SidecarWatcher(final Path recordStreamFolderPath) {
        this.recordStreamFolderPath = recordStreamFolderPath;
    }

    private static final Logger log = LogManager.getLogger(SidecarWatcher.class);
    private static final Pattern SIDECAR_FILE_REGEX =
            Pattern.compile("\\d{4}-\\d{2}-\\d{2}T\\d{2}_\\d{2}_\\d{2}\\.\\d{9}Z_\\d{2}.rcd");
    private static final int POLLING_INTERVAL_MS = 500;

    private final Queue<ExpectedSidecar> expectedSidecars = new LinkedBlockingDeque<>();

    // LinkedHashMap lets us easily print mismatches _in the order added_. Important if the
    // records get out-of-sync at one particular test, then all the _rest_ of the tests fail
    // too: It's good to know the _first_ test which fails.
    private final LinkedHashMap<String, List<MismatchedSidecar>> failedSidecars = new LinkedHashMap<>();
    private final Path recordStreamFolderPath;

    private boolean hasSeenFirstExpectedSidecar = false;
    private FileAlterationMonitor monitor;
    private FileAlterationObserver observer;

    public void watch() throws Exception {
        observer = new FileAlterationObserver(recordStreamFolderPath.toFile());
        final var listener = new FileAlterationListenerAdaptor() {
            @Override
            public void onFileCreate(File file) {
                final var newFilePath = file.getPath();
                if (SIDECAR_FILE_REGEX.matcher(newFilePath).find()) {
                    log.info("New sidecar file: {}", file.getAbsolutePath());
                    var retryCount = 0;
                    while (true) {
                        retryCount++;
                        try {
                            final var sidecarFile = RecordStreamingUtils.readMaybeCompressedSidecarFile(newFilePath);
                            onNewSidecarFile(sidecarFile);
                            return;
                        } catch (IOException e) {
                            // Some number of retries are expected to be necessary due to incomplete files on disk
                            if (retryCount < 8) {
                                try {
                                    Thread.sleep(POLLING_INTERVAL_MS);
                                } catch (InterruptedException ignored) {
                                    Thread.currentThread().interrupt();
                                }
                            } else {
                                log.error("Could not read sidecar file {}, exiting now.", newFilePath, e);
                                throw new IllegalStateException();
                            }
                        }
                    }
                }
            }

            @Override
            public void onFileDelete(File file) {
                // no-op
            }

            @Override
            public void onFileChange(File file) {
                // no-op
            }
        };
        observer.addListener(listener);
        monitor = new FileAlterationMonitor(POLLING_INTERVAL_MS);
        monitor.addObserver(observer);
        monitor.start();
    }

    private void onNewSidecarFile(final SidecarFile sidecarFile) {
        for (final var actualSidecar : sidecarFile.getSidecarRecordsList()) {
            boolean matchesConsensusTimestamp = Optional.ofNullable(expectedSidecars.peek())
                    .map(ExpectedSidecar::expectedSidecarRecord)
                    .map(expected -> expected.matchesConsensusTimestampOf(actualSidecar, Description.NONE))
                    .orElse(false);

            if (hasSeenFirstExpectedSidecar && matchesConsensusTimestamp) {
                assertIncomingSidecar(actualSidecar);
            } else {
                // sidecar records from different suites can be present in the sidecar
                // files before our first expected sidecar so skip sidecars until we reach
                // the first expected one in the queue
                if (expectedSidecars.isEmpty()) {
                    continue;
                }
                if (matchesConsensusTimestamp) {
                    hasSeenFirstExpectedSidecar = true;
                    assertIncomingSidecar(actualSidecar);
                }
            }
        }
    }

    private void assertIncomingSidecar(final TransactionSidecarRecord actualSidecarRecord) {
        // there should always be an expected sidecar at this point,
        // if the queue is empty here, the specs have missed a sidecar
        // and must be updated to account for it
        if (expectedSidecars.isEmpty()) {
            throw new IllegalStateException(
                    "No expected sidecar found for incoming sidecar: %s".formatted(actualSidecarRecord));
        }
        final var expectedSidecar = expectedSidecars.poll();
        final var expectedSidecarRecord = expectedSidecar.expectedSidecarRecord();

        if (!areEqualUpToIntrinsicGasVariation(expectedSidecarRecord, actualSidecarRecord)) {
            final var spec = expectedSidecar.spec();
            failedSidecars.computeIfAbsent(spec, k -> new ArrayList<>());
            failedSidecars.get(spec).add(new MismatchedSidecar(expectedSidecarRecord, actualSidecarRecord));
        }
    }

    private boolean areEqualUpToIntrinsicGasVariation(
            @NonNull final Matcher<TransactionSidecarRecord> expected, @NonNull final TransactionSidecarRecord actual) {
        requireNonNull(expected, "Expected sidecar");
        requireNonNull(actual, "Actual sidecar");
        return expected.matches(actual);
    }

    public void waitUntilFinished() {
        if (!expectedSidecars.isEmpty()) {
            log.info("Waiting a maximum of 10 seconds for expected sidecars");
            var retryCount = 40;
            while (!expectedSidecars.isEmpty() && retryCount >= 0) {
                try {
                    Thread.sleep(POLLING_INTERVAL_MS);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                    log.warn("Interrupted while waiting for sidecars.");
                    return;
                }
                observer.checkAndNotify();
                retryCount--;
            }
        }
    }

    public void addExpectedSidecar(final ExpectedSidecar newExpectedSidecar) {
        this.expectedSidecars.add(newExpectedSidecar);
    }

    public boolean thereAreNoMismatchedSidecars() {
        return failedSidecars.isEmpty();
    }

    public boolean containsAllExpectedSidecarRecords() {
        return containsAllExpectedSidecarRecords(sidecarRecord -> true);
    }

    public boolean containsAllExpectedSidecarRecords(Predicate<MismatchedSidecar> filter) {
        for (final var entry : failedSidecars.entrySet()) {
            final var specName = entry.getKey();
            final var faultySidecars = entry.getValue();

            for (final MismatchedSidecar pair : faultySidecars) {
                if (!filter.test(pair)) {
                    continue;
                }

                if (!pair.expectedSidecarRecord().matches(pair.actualSidecarRecord())) {
                    StringDescription expectedDescription = new StringDescription();
                    pair.expectedSidecarRecord().describeTo(expectedDescription);

                    StringDescription actualDescription = new StringDescription();
                    pair.expectedSidecarRecord().describeMismatch(pair.actualSidecarRecord(), actualDescription);

                    log.error(
                            "Some expected state changes are missing for spec {}: \nExpected: {}\nActual: {}",
                            specName,
                            expectedDescription,
                            actualDescription);
                    return false;
                }
            }
        }
        return true;
    }

    public String getMismatchErrors() {
        return getMismatchErrors(pair -> true);
    }

    public String getMismatchErrors(Predicate<MismatchedSidecar> filter) {
        final var messageBuilder = new StringBuilder();
        messageBuilder.append("Mismatch(es) between actual/expected sidecars present: ");
        for (final var kv : failedSidecars.entrySet()) {
            final var faultySidecars = kv.getValue().stream().filter(filter).toList();
            messageBuilder
                    .append("\n\n")
                    .append(faultySidecars.size())
                    .append(" SIDECAR MISMATCH(ES) in SPEC {")
                    .append(kv.getKey())
                    .append("}:");
            int i = 1;
            for (final var pair : faultySidecars) {
                StringDescription expectedDescription = new StringDescription();
                pair.expectedSidecarRecord().describeTo(expectedDescription);
                messageBuilder
                        .append("\n******FAILURE #")
                        .append(i++)
                        .append("******\n")
                        .append("***Expected sidecar***\n")
                        .append(expectedDescription)
                        .append("***Actual sidecar***\n")
                        .append(pair.actualSidecarRecord());
            }
        }
        return messageBuilder.toString();
    }

    public boolean thereAreNoPendingSidecars() {
        return expectedSidecars.isEmpty();
    }

    public String getPendingErrors() {
        final var messageBuilder = new StringBuilder();
        messageBuilder.append("Pending sidecars not yet seen: ");
        int i = 1;
        for (final var pendingSidecar : expectedSidecars) {
            messageBuilder
                    .append("\n****** PENDING #")
                    .append(i++)
                    .append("******\n")
                    .append("*** Pending sidecar***\n")
                    .append(pendingSidecar.spec())
                    .append(": ")
                    .append(pendingSidecar.expectedSidecarRecord());
        }
        return messageBuilder.toString();
    }

    public void tearDown() {
        try {
            monitor.stop();
        } catch (Exception e) {
            log.warn("Exception thrown when closing monitor.");
        }
    }
}
