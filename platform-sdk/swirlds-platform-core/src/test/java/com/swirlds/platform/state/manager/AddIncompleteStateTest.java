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
import static com.swirlds.platform.reconnect.emergency.EmergencyReconnectTeacher.emergencyStateCriteria;
import static com.swirlds.platform.state.manager.SignedStateManagerTestUtils.buildFakeSignature;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

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

@DisplayName("SignedStateManager: Add Incomplete State Test")
class AddIncompleteStateTest extends AbstractSignedStateManagerTest {

    // Note: this unit test was long and complex, so it was split into its own class.
    // As such, this test was designed differently than it would be designed if it were sharing
    // the class file with other tests.
    // DO NOT ADD ADDITIONAL UNIT TESTS TO THIS CLASS!

    private final int roundAgeToSign = 3;

    private final AddressBook addressBook = new RandomAddressBookGenerator(random)
            .setSize(4)
            .setWeightDistributionStrategy(RandomAddressBookGenerator.WeightDistributionStrategy.BALANCED)
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
        };
    }

    @Test
    @DisplayName("Add Incomplete State Test")
    void addIncompleteStateTest() {

        SignedStateManager manager = new SignedStateManagerBuilder(buildStateConfig())
                .stateLacksSignaturesConsumer(stateLacksSignaturesConsumer())
                .stateHasEnoughSignaturesConsumer(stateHasEnoughSignaturesConsumer())
                .build();

        // Simulate a restart (i.e. loading a state from disk)
        final Hash signedHash = randomHash(random);
        final Map<Long, Signature> signatures = new HashMap<>();
        for (final Address address : addressBook) {
            signatures.put(address.getId(), buildFakeSignature(address.getSigPublicKey(), signedHash));
        }

        final SignedState stateFromDisk = new RandomSignedStateGenerator(random)
                .setAddressBook(addressBook)
                .setRound(firstRound)
                .setSignatures(signatures)
                .build();

        // This is intentionally a different hash than the signed hash!
        final Hash stateHash = randomHash();
        stateFromDisk.getState().setHash(stateHash);

        // The manager should store this state but not assigned it to the last complete signed state
        manager.addState(stateFromDisk);

        assertNull(manager.getLatestSignedState("test").get());
        assertEquals(-1, manager.getLastCompleteRound());

        try (final ReservedSignedState wrapper =
                manager.find(emergencyStateCriteria(stateFromDisk.getRound(), stateHash), "test")) {
            assertNotNull(wrapper.get(), "Should have returned a state");
            assertEquals(stateFromDisk, wrapper.get(), "Should have returned the state from disk");
        }

        assertEquals(
                2,
                stateFromDisk.getReservationCount(),
                "Two reservations expected, one for the freshSignedStates, one for lastState");

        validateCallbackCounts(0, 0);
    }
}
