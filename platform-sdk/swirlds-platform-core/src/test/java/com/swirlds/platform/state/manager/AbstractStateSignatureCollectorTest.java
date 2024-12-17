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

package com.swirlds.platform.state.manager;

import static com.swirlds.common.test.fixtures.AssertionUtils.assertEventuallyDoesNotThrow;
import static com.swirlds.common.test.fixtures.RandomUtils.getRandomPrintSeed;
import static com.swirlds.platform.test.fixtures.state.manager.SignatureVerificationTestUtils.buildFakeSignatureBytes;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.hedera.hapi.platform.event.StateSignatureTransaction;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.platform.NodeId;
import com.swirlds.config.api.Configuration;
import com.swirlds.config.extensions.test.fixtures.TestConfigBuilder;
import com.swirlds.platform.config.StateConfig_;
import com.swirlds.platform.state.StateSignatureCollectorTester;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.system.address.AddressBook;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import org.junit.jupiter.api.AfterEach;

/**
 * Boilerplate implementation for SignedStateManager tests.
 */
public class AbstractStateSignatureCollectorTest {

    protected final Random random = getRandomPrintSeed();

    protected AtomicInteger stateLacksSignaturesCount = new AtomicInteger();
    protected AtomicInteger stateHasEnoughSignaturesCount = new AtomicInteger();

    protected final Map<Long /* round */, SignedState> signedStates = new ConcurrentHashMap<>();
    protected final AtomicLong highestRound = new AtomicLong(-1);
    protected final AtomicLong highestCompleteRound = new AtomicLong(-1);
    protected final int roundsToKeepForSigning = 5;
    protected final int futureStateSignatureRounds = 16;
    protected int roundsToKeepAfterSigning = 0;

    /**
     * true if an error occurs on a notification thread
     */
    protected final AtomicBoolean error = new AtomicBoolean(false);

    @NonNull
    protected Configuration buildStateConfig() {
        return new TestConfigBuilder()
                .withValue(StateConfig_.ROUNDS_TO_KEEP_FOR_SIGNING, roundsToKeepForSigning)
                .withValue(StateConfig_.MAX_AGE_OF_FUTURE_STATE_SIGNATURES, futureStateSignatureRounds)
                .withValue(StateConfig_.ROUNDS_TO_KEEP_AFTER_SIGNING, roundsToKeepAfterSigning)
                .getOrCreateConfig();
    }

    @AfterEach
    protected void afterEach() {
        assertFalse(error.get(), "error detected");
    }

    /**
     * Add a signature for a node on a state from a given round.
     */
    protected void addSignature(
            @NonNull final StateSignatureCollectorTester manager, final long round, @NonNull final NodeId nodeId) {
        Objects.requireNonNull(manager, "manager must not be null");
        Objects.requireNonNull(nodeId, "nodeId must not be null");

        final SignedState signedState = signedStates.get(round);

        if (signedState == null) {
            // We are being asked to sign a non-existent round.
            return;
        }

        final AddressBook addressBook = signedState.getAddressBook();
        final Hash hash = signedState.getState().getHash();

        final StateSignatureTransaction transaction = StateSignatureTransaction.newBuilder()
                .round(round)
                .signature(
                        buildFakeSignatureBytes(addressBook.getAddress(nodeId).getSigPublicKey(), hash))
                .hash(hash.getBytes())
                .build();

        manager.handlePreconsensusSignatureTransaction(nodeId, transaction);
    }

    /**
     * Validate that callbacks were correctly invoked. Will wait up to 1 second for callbacks to properly be invoked.
     */
    protected void validateCallbackCounts(
            final int expectedStateLacksSignaturesCount, final int expectedStateHasEnoughSignaturesCount) {

        assertEventuallyDoesNotThrow(
                () -> {
                    assertEquals(
                            expectedStateLacksSignaturesCount,
                            stateLacksSignaturesCount.get(),
                            "unexpected number of callbacks");
                    assertEquals(
                            expectedStateHasEnoughSignaturesCount,
                            stateHasEnoughSignaturesCount.get(),
                            "unexpected number of callbacks");
                },
                Duration.ofSeconds(1),
                "callbacks not correctly invoked");
    }

    protected void validateReservationCounts(final Predicate<Long> shouldRoundBePresent) {
        // Check reservation counts. Only the 5 most recent states should have reservations.
        for (final SignedState signedState : signedStates.values()) {
            final long round = signedState.getRound();
            if (shouldRoundBePresent.test(round)) {
                assertEquals(-1, signedState.getReservationCount(), "state should have no reservations");
            } else {
                // the most recent complete state has a reservation held by the nexus
                // incomplete states are held by the collector
                final int expectedReservationCount =
                        round == highestCompleteRound.get() || !signedState.isComplete() ? 1 : -1;
                assertEquals(
                        expectedReservationCount,
                        signedState.getReservationCount(),
                        ("unexpected reservation count!%n"
                                        + "round: %d%n"
                                        + "highestRound: %d%n"
                                        + "highestCompleteRound: %d%n"
                                        + "history:%n%s")
                                .formatted(
                                        round,
                                        highestRound.get(),
                                        highestCompleteRound.get(),
                                        signedState.getHistory()));
            }
        }
    }
}
