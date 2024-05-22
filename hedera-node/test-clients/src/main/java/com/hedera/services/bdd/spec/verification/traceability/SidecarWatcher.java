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

import com.hedera.services.stream.proto.TransactionSidecarRecord;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.function.Predicate;

import edu.umd.cs.findbugs.annotations.NonNull;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Assertions;

import static org.junit.jupiter.api.Assertions.assertTrue;

// string literals should not be duplicated
@SuppressWarnings("java:S1192")
public class SidecarWatcher {
    private final Queue<ExpectedSidecar> expectedSidecars = new LinkedBlockingDeque<>();
    // LinkedHashMap lets us easily print mismatches _in the order added_. Important if the
    // records get out-of-sync at one particular test, then all the _rest_ of the tests fail
    // too: It's good to know the _first_ test which fails.
    private final LinkedHashMap<String, List<MismatchedSidecar>> failedSidecars = new LinkedHashMap<>();

    private boolean hasSeenFirstExpectedSidecar = false;

    /**
     * Adds a new expected sidecar to the queue.
     *
     * @param newExpectedSidecar the new expected sidecar
     */
    public void addExpectedSidecar(@NonNull final ExpectedSidecar newExpectedSidecar) {
        this.expectedSidecars.add(newExpectedSidecar);
    }

    /**
     * Asserts that there are no mismatched sidecars and no pending sidecars.
     *
     * @throws AssertionError if there are mismatched sidecars or pending sidecars
     */
    public void assertAllExpectations(@NonNull final List<TransactionSidecarRecord> actualSidecarRecords) {
        for (final var actualSidecar : actualSidecarRecords) {
            final boolean matchesConsensusTimestamp = Optional.ofNullable(expectedSidecars.peek())
                    .map(ExpectedSidecar::expectedSidecarRecord)
                    .map(expected -> expected.matchesConsensusTimestampOf(actualSidecar))
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

        assertTrue(thereAreNoMismatchedSidecars(), getMismatchErrors());
        assertTrue(
                thereAreNoPendingSidecars(),
                "There are some sidecars that have not been yet"
                        + " externalized in the sidecar files after all"
                        + " specs: " + getPendingErrors());
    }

    private void assertIncomingSidecar(final TransactionSidecarRecord actualSidecarRecord) {
        // there should always be an expected sidecar at this point,
        // if the queue is empty here, the specs have missed a sidecar
        // and must be updated to account for it
        if (expectedSidecars.isEmpty()) {
            Assertions.fail("No expected sidecar found for incoming sidecar: %s".formatted(actualSidecarRecord));
        }
        final var expectedSidecar = expectedSidecars.poll();
        final var expectedSidecarRecord = expectedSidecar.expectedSidecarRecord();

        if (!expectedSidecar.expectedSidecarRecord().matches(actualSidecarRecord)) {
            final var spec = expectedSidecar.spec();
            failedSidecars.computeIfAbsent(spec, k -> new ArrayList<>());
            failedSidecars.get(spec).add(new MismatchedSidecar(expectedSidecarRecord, actualSidecarRecord));
        }
    }

    private boolean thereAreNoMismatchedSidecars() {
        return failedSidecars.isEmpty();
    }

    private String getMismatchErrors() {
        return getMismatchErrors(pair -> true);
    }

    private String getMismatchErrors(Predicate<MismatchedSidecar> filter) {
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
                messageBuilder
                        .append("\n******FAILURE #")
                        .append(i++)
                        .append("******\n")
                        .append("***Expected sidecar***\n")
                        .append(pair.expectedSidecarRecordMatcher().toSidecarRecord())
                        .append("***Actual sidecar***\n")
                        .append(pair.actualSidecarRecord());
            }
        }
        return messageBuilder.toString();
    }

    private boolean thereAreNoPendingSidecars() {
        return expectedSidecars.isEmpty();
    }

    private String getPendingErrors() {
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
}
