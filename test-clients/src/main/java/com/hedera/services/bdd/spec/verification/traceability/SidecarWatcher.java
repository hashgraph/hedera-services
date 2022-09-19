/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.hedera.services.recordstreaming.RecordStreamingUtils;
import com.hedera.services.stream.proto.SidecarFile;
import com.hedera.services.stream.proto.TransactionSidecarRecord;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.regex.Pattern;
import org.apache.commons.io.monitor.FileAlterationListenerAdaptor;
import org.apache.commons.io.monitor.FileAlterationMonitor;
import org.apache.commons.io.monitor.FileAlterationObserver;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class SidecarWatcher {

    public SidecarWatcher(final Path recordStreamFolderPath) {
        this.recordStreamFolderPath = recordStreamFolderPath;
    }

    private static final Logger log = LogManager.getLogger(SidecarWatcher.class);
    private static final Pattern SIDECAR_FILE_REGEX =
            Pattern.compile("\\d{4}-\\d{2}-\\d{2}T\\d{2}_\\d{2}_\\d{2}\\.\\d{9}Z_\\d{2}.rcd");
    private static final int POLLING_INTERVAL_MS = 250;

    private final Queue<ExpectedSidecar> expectedSidecars = new LinkedBlockingDeque<>();
    private final Multimap<String, MismatchedSidecar> failedSidecars = HashMultimap.create();
    private final Path recordStreamFolderPath;

    private boolean hasSeenFirstExpectedSidecar = false;
    private FileAlterationMonitor monitor;

    public void watch() throws Exception {
        final var observer = new FileAlterationObserver(recordStreamFolderPath.toFile());
        final var listener =
                new FileAlterationListenerAdaptor() {
                    @Override
                    public void onFileCreate(File file) {
                        final var newFilePath = file.getPath();
                        if (SIDECAR_FILE_REGEX.matcher(newFilePath).find()) {
                            log.info("New sidecar file: {}", newFilePath);
                            final SidecarFile sidecarFile;
                            try {
                                sidecarFile = RecordStreamingUtils.readSidecarFile(newFilePath);
                            } catch (IOException e) {
                                log.fatal(
                                        "An error occurred trying to parse sidecar file {} - {}",
                                        newFilePath,
                                        e);
                                throw new IllegalStateException();
                            }
                            onNewSidecarFile(sidecarFile);
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
            if (hasSeenFirstExpectedSidecar) {
                assertIncomingSidecar(actualSidecar);
            } else {
                // sidecar records from different suites can be present in the sidecar
                // files before our first expected sidecar so skip sidecars until we reach
                // the first expected one in the queue
                if (expectedSidecars.isEmpty()) {
                    continue;
                }
                if (expectedSidecars
                        .peek()
                        .expectedSidecarRecord()
                        .getConsensusTimestamp()
                        .equals(actualSidecar.getConsensusTimestamp())) {
                    hasSeenFirstExpectedSidecar = true;
                    assertIncomingSidecar(actualSidecar);
                }
            }
        }
    }

    private void assertIncomingSidecar(final TransactionSidecarRecord actualSidecar) {
        // there should always be an expected sidecar at this point;
        // if a NPE is thrown here, the specs have missed a sidecar
        // and must be updated to account for it
        final var expectedSidecar = expectedSidecars.poll();
        final var expectedSidecarRecord = expectedSidecar.expectedSidecarRecord();

        if (!actualSidecar.equals(expectedSidecarRecord)) {
            final var spec = expectedSidecar.spec();
            failedSidecars.put(spec, new MismatchedSidecar(expectedSidecarRecord, actualSidecar));
        }
    }

    public void waitUntilFinished() {
        if (!expectedSidecars.isEmpty()) {
            log.info("Waiting a maximum of 10 seconds for expected sidecars");
            var retryCount = 20;
            while (!expectedSidecars.isEmpty() && retryCount >= 0) {
                try {
                    Thread.sleep(POLLING_INTERVAL_MS);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                    log.warn("Interrupted while waiting for sidecars.");
                    return;
                }
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

    public String getErrors() {
        final var messageBuilder = new StringBuilder();
        messageBuilder.append("Mismatch(es) between actual/expected sidecars present: ");
        for (final var key : failedSidecars.keySet()) {
            final var faultySidecars = failedSidecars.get(key);
            messageBuilder
                    .append("\n\n")
                    .append(faultySidecars.size())
                    .append(" SIDECAR MISMATCH(ES) in SPEC {")
                    .append(key)
                    .append("}:");
            int i = 1;
            for (final var pair : faultySidecars) {
                messageBuilder
                        .append("\n******FAILURE #")
                        .append(i++)
                        .append("******\n")
                        .append("***Expected sidecar***\n")
                        .append(pair.expectedSidecarRecord())
                        .append("***Actual sidecar***\n")
                        .append(pair.actualSidecarRecord());
            }
        }
        return messageBuilder.toString();
    }

    public boolean thereAreNoPendingSidecars() {
        return expectedSidecars.isEmpty();
    }

    public void tearDown() {
        try {
            monitor.stop();
        } catch (Exception e) {
            log.warn("Exception thrown when closing monitor.");
        }
    }
}
