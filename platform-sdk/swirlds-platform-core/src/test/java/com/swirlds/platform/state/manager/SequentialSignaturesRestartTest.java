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

package com.swirlds.platform.state.manager;

import static com.swirlds.common.test.RandomUtils.randomHash;
import static com.swirlds.platform.state.manager.SignedStateManagerTestUtils.buildFakeSignature;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.Signature;
import com.swirlds.common.system.address.Address;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.test.RandomAddressBookGenerator;
import com.swirlds.platform.components.state.output.StateHasEnoughSignaturesConsumer;
import com.swirlds.platform.components.state.output.StateLacksSignaturesConsumer;
import com.swirlds.platform.state.RandomSignedStateGenerator;
import com.swirlds.platform.state.signed.ReservedSignedState;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.state.signed.SignedStateManager;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("SignedStateManager: Sequential Signatures After Restart Test")
public class SequentialSignaturesRestartTest extends AbstractSignedStateManagerTest {

    // Note: this unit test was long and complex, so it was split into its own class.
    // As such, this test was designed differently than it would be designed if it were sharing
    // the class file with other tests.
    // DO NOT ADD ADDITIONAL UNIT TESTS TO THIS CLASS!

    private final int roundAgeToSign = 3;

    private final AddressBook addressBook = new RandomAddressBookGenerator(random)
            .setSize(4)
            .setStakeDistributionStrategy(RandomAddressBookGenerator.StakeDistributionStrategy.BALANCED)
            .setSequentialIds(true)
            .build();

    private final long firstRound = 50;

    /**
     * Called on each state as it gets too old without collecting enough signatures.
     * <p>
     * This consumer is provided by the wiring layer, so it should release the resource when finished.
     */
    private StateLacksSignaturesConsumer stateLacksSignaturesConsumer() {
        // No state is unsigned in this test. If this method is called then the test is expected to fail.
        return ssw -> {
            stateLacksSignaturesCount.getAndIncrement();
            ssw.close();
        };
    }

    /**
     * Called on each state as it gathers enough signatures to be complete.
     * <p>
     * This consumer is provided by the wiring layer, so it should release the resource when finished.
     */
    private StateHasEnoughSignaturesConsumer stateHasEnoughSignaturesConsumer() {
        return ssw -> {
            assertEquals(highestRound.get() - roundAgeToSign, ssw.get().getRound(), "unexpected round completed");

            stateHasEnoughSignaturesCount.getAndIncrement();
            ssw.close();
        };
    }

    @Test
    @DisplayName("Sequential Signatures After Restart Test")
    void sequentialSignaturesAfterRestartTest() throws InterruptedException {

        final SignedStateManager manager = new SignedStateManagerBuilder(buildStateConfig())
                .stateLacksSignaturesConsumer(stateLacksSignaturesConsumer())
                .stateHasEnoughSignaturesConsumer(stateHasEnoughSignaturesConsumer())
                .build();

        // Simulate a restart (i.e. loading a state from disk)
        final Hash stateHash = randomHash(random);
        final Map<Long, Signature> signatures = new HashMap<>();
        for (final Address address : addressBook) {
            signatures.put(address.getId(), buildFakeSignature(address.getSigPublicKey(), stateHash));
        }

        final SignedState stateFromDisk = new RandomSignedStateGenerator(random)
                .setAddressBook(addressBook)
                .setRound(firstRound)
                .setSignatures(signatures)
                .build();
        stateFromDisk.getState().setHash(stateHash);

        signedStates.put(firstRound, stateFromDisk);
        highestRound.set(firstRound);
        manager.addState(stateFromDisk);

        // Create a series of signed states.
        final int count = 100;
        for (int round = (int) firstRound + 1; round < count + firstRound; round++) {
            final SignedState signedState = new RandomSignedStateGenerator(random)
                    .setAddressBook(addressBook)
                    .setRound(round)
                    .setSignatures(new HashMap<>())
                    .build();

            signedStates.put((long) round, signedState);
            highestRound.set(round);

            manager.addState(signedState);

            // Add some signatures to one of the previous states
            final long roundToSign = round - roundAgeToSign;
            addSignature(manager, roundToSign, 0);
            addSignature(manager, roundToSign, 1);
            addSignature(manager, roundToSign, 2);
            if (random.nextBoolean()) {
                addSignature(manager, roundToSign, 1);
            }

            try (final ReservedSignedState lastState = manager.getLatestImmutableState("test")) {
                assertSame(signedState, lastState.get(), "last signed state has unexpected value");
            }
            try (final ReservedSignedState lastCompletedState = manager.getLatestSignedState("test")) {
                if (roundToSign >= firstRound) {
                    assertSame(
                            signedStates.get(roundToSign), lastCompletedState.get(), "unexpected last completed state");
                } else {
                    assertSame(stateFromDisk, lastCompletedState.get(), "state from disk should be complete");
                }
            }

            final int roundsAfterRestart = (int) (round - firstRound);

            validateCallbackCounts(0, Math.max(0, roundsAfterRestart - roundAgeToSign));
        }

        // Check reservation counts.
        validateReservationCounts(round -> round - firstRound < signedStates.size() - roundAgeToSign - 1);

        // We don't expect any further callbacks. But wait a little while longer in case there is something unexpected.
        SECONDS.sleep(1);

        validateCallbackCounts(0, count - roundAgeToSign - 1);
    }
}
