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
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.regex.Pattern;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class SidecarWatcher {

    private final Path recordStreamFolderPath;
    private WatchService watchService;

    public SidecarWatcher(final Path recordStreamFolderPath) {
        this.recordStreamFolderPath = recordStreamFolderPath;
    }

    private static final Logger log = LogManager.getLogger(SidecarWatcher.class);
    private static final Pattern SIDECAR_FILE_REGEX =
            Pattern.compile("\\d{4}-\\d{2}-\\d{2}T\\d{2}_\\d{2}_\\d{2}\\.\\d{9}Z_\\d{2}.rcd");

    private final Queue<ExpectedSidecar> expectedSidecars = new LinkedBlockingDeque<>();
    private final Multimap<String, MismatchedSidecar> failedSidecars = HashMultimap.create();

    private boolean shouldTerminateAfterNextSidecar = false;
    private boolean hasSeenFirst = false;

    public void prepareInfrastructure() throws IOException {
        watchService = FileSystems.getDefault().newWatchService();
        recordStreamFolderPath.register(watchService, StandardWatchEventKinds.ENTRY_CREATE);
    }

    public void watch() throws IOException {
        for (; ; ) {
            // wait for key to be signaled
            WatchKey key;
            try {
                key = watchService.take();
            } catch (InterruptedException x) {
                Thread.currentThread().interrupt();
                return;
            }
            for (final var event : key.pollEvents()) {
                final var kind = event.kind();
                // This key is registered only
                // for ENTRY_CREATE events,
                // but an OVERFLOW event can
                // occur regardless if events
                // are lost or discarded.
                if (kind == StandardWatchEventKinds.OVERFLOW) {
                    continue;
                }
                // The filename is the
                // context of the event.
                final var ev = (WatchEvent<Path>) event;
                final var filename = ev.context();
                final var child = recordStreamFolderPath.resolve(filename);
                log.debug("Record stream file created -> {} ", child.getFileName());
                final var newFilePath = child.toAbsolutePath().toString();
                if (SIDECAR_FILE_REGEX.matcher(newFilePath).find()) {
                    log.info("We have a new sidecar.");
                    final var sidecarFile = RecordStreamingUtils.readSidecarFile(newFilePath);
                    onNewSidecarFile(sidecarFile);
                    if (shouldTerminateAfterNextSidecar) {
                        return;
                    }
                }
            }
            // Reset the key -- this step is critical if you want to
            // receive further watch events.  If the key is no longer valid,
            // the directory is inaccessible so exit the loop.
            final var valid = key.reset();
            if (!valid) {
                log.fatal("Error occurred in WatchServiceAPI. Exiting now.");
                return;
            }
        }
    }

    public void tearDown() {
        try {
            watchService.close();
        } catch (IOException e) {
            log.warn("Watch service couldn't be closed.");
        }
    }

    private void onNewSidecarFile(final SidecarFile sidecarFile) {
        for (final var actualSidecar : sidecarFile.getSidecarRecordsList()) {
            if (hasSeenFirst) {
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
                    hasSeenFirst = true;
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

    public void finishWatchingAfterNextSidecar() {
        shouldTerminateAfterNextSidecar = true;
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
}
