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

import static com.swirlds.platform.state.manager.SignedStateManagerTestUtils.buildReallyFakeSignature;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.swirlds.common.test.fixtures.RandomAddressBookGenerator;
import com.swirlds.platform.components.state.output.StateHasEnoughSignaturesConsumer;
import com.swirlds.platform.components.state.output.StateLacksSignaturesConsumer;
import com.swirlds.platform.config.StateConfig;
import com.swirlds.platform.state.RandomSignedStateGenerator;
import com.swirlds.platform.state.StateSignatureCollectorTester;
import com.swirlds.platform.state.signed.ReservedSignedState;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.platform.system.transaction.StateSignatureTransaction;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("SignedStateManager: Early Signatures Test")
public class EarlySignaturesTest extends AbstractStateSignatureCollectorTest {

    // Note: this unit test was long and complex, so it was split into its own class.
    // As such, this test was designed differently than it would be designed if it were sharing
    // the class file with other tests.
    // DO NOT ADD ADDITIONAL UNIT TESTS TO THIS CLASS!

    private final int roundAgeToSign = 3;

    private final AddressBook addressBook = new RandomAddressBookGenerator(random)
            .setSize(4)
            .setWeightDistributionStrategy(RandomAddressBookGenerator.WeightDistributionStrategy.BALANCED)
            .build();

    /**
     * Called on each state as it gets too old without collecting enough signatures.
     * <p>
     * This consumer is provided by the wiring layer, so it should release the resource when finished.
     */
    private StateLacksSignaturesConsumer stateLacksSignaturesConsumer() {
        // No state is unsigned in this test. If this method is called then the test is expected to fail.
        return ss -> stateLacksSignaturesCount.getAndIncrement();
    }

    /**
     * Called on each state as it gathers enough signatures to be complete.
     * <p>
     * This consumer is provided by the wiring layer, so it should release the resource when finished.
     */
    private StateHasEnoughSignaturesConsumer stateHasEnoughSignaturesConsumer() {
        return ss -> {
            highestCompleteRound.accumulateAndGet(ss.getRound(), Math::max);
            stateHasEnoughSignaturesCount.getAndIncrement();
        };
    }

    @Test
    @DisplayName("Early Signatures Test")
    void earlySignaturesTest() throws InterruptedException {
        final int count = 100;
        final StateConfig stateConfig = buildStateConfig();
        final int futureSignatures = stateConfig.maxAgeOfFutureStateSignatures();
        final StateSignatureCollectorTester manager = new StateSignatureCollectorBuilder(stateConfig)
                .stateLacksSignaturesConsumer(stateLacksSignaturesConsumer())
                .stateHasEnoughSignaturesConsumer(stateHasEnoughSignaturesConsumer())
                .build();

        // Create a series of signed states.
        final List<SignedState> states = new ArrayList<>();
        for (int round = 0; round < count; round++) {
            final SignedState signedState = new RandomSignedStateGenerator(random)
                    .setAddressBook(addressBook)
                    .setRound(round)
                    .setSignatures(new HashMap<>())
                    .build();
            states.add(signedState);
        }

        // send out signatures super early. Many will be rejected.
        for (int round = 0; round < count; round++) {
            // All node 0 and 2 signatures are sent very early.
            manager.handlePreconsensusSignatureTransaction(
                    addressBook.getNodeId(0),
                    new StateSignatureTransaction(
                            round,
                            buildReallyFakeSignature(),
                            states.get(round).getState().getHash()));
            manager.handlePreconsensusSignatureTransaction(
                    addressBook.getNodeId(2),
                    new StateSignatureTransaction(
                            round,
                            buildReallyFakeSignature(),
                            states.get(round).getState().getHash()));

            // Even numbered rounds have 3 sent very early.
            if (round % 2 == 0) {
                manager.handlePreconsensusSignatureTransaction(
                        addressBook.getNodeId(3),
                        new StateSignatureTransaction(
                                round,
                                buildReallyFakeSignature(),
                                states.get(round).getState().getHash()));
            }
        }

        int expectedCompletedStateCount = 0;

        long lastExpectedCompletedRound = -1;

        for (int round = 0; round < count; round++) {
            final SignedState signedState = states.get(round);

            signedStates.put((long) round, signedState);
            highestRound.set(round);

            manager.addReservedState(signedState.reserve("test"));

            // Add some signatures to one of the previous states, but only if that round need signatures.
            final long roundToSign = round - roundAgeToSign;

            if (roundToSign > 0) {
                if (roundToSign >= futureSignatures) {
                    addSignature(manager, roundToSign, addressBook.getNodeId(0));
                    addSignature(manager, roundToSign, addressBook.getNodeId(1));
                    addSignature(manager, roundToSign, addressBook.getNodeId(2));
                    expectedCompletedStateCount++;
                } else if (roundToSign % 2 != 0) {
                    addSignature(manager, roundToSign, addressBook.getNodeId(0));
                    addSignature(manager, roundToSign, addressBook.getNodeId(1));
                    expectedCompletedStateCount++;
                }
            }

            final boolean currentRoundShouldBeComplete = round < futureSignatures && round % 2 == 0;
            if (currentRoundShouldBeComplete) {
                expectedCompletedStateCount++;
                lastExpectedCompletedRound = round;
            } else {
                lastExpectedCompletedRound = Math.max(lastExpectedCompletedRound, roundToSign);
            }

            try (final ReservedSignedState lastCompletedState =
                    manager.getLatestSignedState("test get lastCompletedState")) {
                assertSame(
                        signedStates.get(lastExpectedCompletedRound),
                        lastCompletedState.get(),
                        "unexpected last completed state");
            }

            validateCallbackCounts(0, expectedCompletedStateCount);
        }

        // Check reservation counts.
        validateReservationCounts(round -> round < signedStates.size() - roundAgeToSign - 1);

        // We don't expect any further callbacks. But wait a little while longer in case there is something unexpected.
        SECONDS.sleep(1);

        validateCallbackCounts(0, count - roundAgeToSign);
    }
}
