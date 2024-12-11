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

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.test.fixtures.platform.TestPlatformContextBuilder;
import com.swirlds.merkledb.MerkleDb;
import com.swirlds.platform.components.state.output.StateHasEnoughSignaturesConsumer;
import com.swirlds.platform.components.state.output.StateLacksSignaturesConsumer;
import com.swirlds.platform.state.StateSignatureCollectorTester;
import com.swirlds.platform.state.signed.ReservedSignedState;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.platform.test.fixtures.addressbook.RandomAddressBookBuilder;
import com.swirlds.platform.test.fixtures.state.RandomSignedStateGenerator;
import java.util.HashMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("SignedStateManager: Sequential Signatures Test")
public class SequentialSignaturesTest extends AbstractStateSignatureCollectorTest {

    // Note: this unit test was long and complex, so it was split into its own class.
    // As such, this test was designed differently than it would be designed if it were sharing
    // the class file with other tests.
    // DO NOT ADD ADDITIONAL UNIT TESTS TO THIS CLASS!

    private final int roundAgeToSign = 3;

    private final AddressBook addressBook = RandomAddressBookBuilder.create(random)
            .withSize(4)
            .withWeightDistributionStrategy(RandomAddressBookBuilder.WeightDistributionStrategy.BALANCED)
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
            assertEquals(highestRound.get() - roundAgeToSign, ss.getRound(), "unexpected round completed");
            stateHasEnoughSignaturesCount.getAndIncrement();
            highestCompleteRound.accumulateAndGet(ss.getRound(), Math::max);
        };
    }

    @BeforeEach
    void setUp() {
        MerkleDb.resetDefaultInstancePath();
    }

    @AfterEach
    void tearDown() {
        RandomSignedStateGenerator.releaseAllBuiltSignedStates();
    }

    @Test
    @DisplayName("Sequential Signatures Test")
    void sequentialSignaturesTest() throws InterruptedException {
        this.roundsToKeepAfterSigning = 4;
        final PlatformContext platformContext = TestPlatformContextBuilder.create()
                .withConfiguration(buildStateConfig())
                .build();
        final StateSignatureCollectorTester manager = new StateSignatureCollectorBuilder(platformContext)
                .stateLacksSignaturesConsumer(stateLacksSignaturesConsumer())
                .stateHasEnoughSignaturesConsumer(stateHasEnoughSignaturesConsumer())
                .build();

        // Create a series of signed states.
        final int count = 100;
        for (int round = 0; round < count; round++) {
            MerkleDb.resetDefaultInstancePath();
            final SignedState signedState = new RandomSignedStateGenerator(random)
                    .setAddressBook(addressBook)
                    .setRound(round)
                    .setSignatures(new HashMap<>())
                    .build();

            signedStates.put((long) round, signedState);
            highestRound.set(round);

            manager.addReservedState(signedState.reserve("test"));

            // Add some signatures to one of the previous states
            final long roundToSign = round - roundAgeToSign;
            addSignature(manager, roundToSign, addressBook.getNodeId(0));
            addSignature(manager, roundToSign, addressBook.getNodeId(1));
            addSignature(manager, roundToSign, addressBook.getNodeId(2));
            if (random.nextBoolean()) {
                addSignature(manager, roundToSign, addressBook.getNodeId(1));
                addSignature(manager, roundToSign, addressBook.getNodeId(1));
            }

            try (final ReservedSignedState lastCompletedState = manager.getLatestSignedState("test")) {
                if (roundToSign >= 0) {
                    assertSame(
                            signedStates.get(roundToSign), lastCompletedState.get(), "unexpected last completed state");
                } else {
                    assertNull(lastCompletedState, "no states should be completed yet");
                }
            }

            validateCallbackCounts(0, Math.max(0, round - roundAgeToSign + 1));
        }

        // Check reservation counts.
        validateReservationCounts(round -> round < signedStates.size() - roundsToKeepAfterSigning - 1);

        // We don't expect any further callbacks. But wait a little while longer in case there is something unexpected.
        SECONDS.sleep(1);

        validateCallbackCounts(0, count - roundAgeToSign);
    }
}
