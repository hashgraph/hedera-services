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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.test.RandomAddressBookGenerator;
import com.swirlds.common.test.RandomUtils;
import com.swirlds.common.utility.AutoCloseableWrapper;
import com.swirlds.platform.state.RandomSignedStateGenerator;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.state.signed.SignedStateManager;
import java.util.HashMap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("SignedStateManager: Emergency State Finder")
public class EmergencyStateFinderTests extends AbstractSignedStateManagerTest {

    private final AddressBook addressBook = new RandomAddressBookGenerator(random)
            .setSize(4)
            .setStakeDistributionStrategy(RandomAddressBookGenerator.StakeDistributionStrategy.BALANCED)
            .setSequentialIds(true)
            .build();

    @DisplayName("Emergency State Finder Test")
    @Test
    void testFind() {
        final SignedStateManager manager = new SignedStateManagerBuilder(stateConfig).build();

        final int roundAgeToSign = 3;

        // Create a series of signed states, none of which are ancient
        for (int round = 0; round < roundsToKeepForSigning - 1; round++) {
            final SignedState signedState = new RandomSignedStateGenerator(random)
                    .setAddressBook(addressBook)
                    .setRound(round)
                    .setSignatures(new HashMap<>())
                    .build();

            signedStates.put((long) round, signedState);
            highestRound.set(round);

            manager.addUnsignedState(signedState);

            // Add some signatures to one of the previous states
            final long roundToSign = round - roundAgeToSign;
            if (roundToSign >= 0) {
                addSignature(manager, roundToSign, 1);
                addSignature(manager, roundToSign, 2);
                addSignature(manager, roundToSign, 3);
            }
        }

        validateReservationCounts(round -> round < signedStates.size() - roundAgeToSign - 1);

        try (final AutoCloseableWrapper<SignedState> lastCompleteWrapper = manager.getLatestSignedState()) {
            final SignedState lastComplete = lastCompleteWrapper.get();

            // Search for a round and hash that match the last complete state exactly
            try (final AutoCloseableWrapper<SignedState> actualWrapper = manager.find(
                    lastComplete.getRound(), lastComplete.getState().getHash())) {
                final SignedState actual = actualWrapper.get();
                // the last complete state should always have 3 reservations
                // 1 for the reservation
                // 1 for the lastCompleteWrapper AutoCloseableWrapper
                // 1 for the actualWrapper AutoCloseableWrapper
                verifyFoundSignedState(
                        lastComplete,
                        actual,
                        3,
                        "Requesting the last complete round should return the last complete round");
            }

            // Search for a round earlier than the last complete state
            try (final AutoCloseableWrapper<SignedState> actualWrapper =
                    manager.find(lastComplete.getRound() - 1, RandomUtils.randomHash(random))) {
                final SignedState actual = actualWrapper.get();
                verifyFoundSignedState(
                        lastComplete,
                        actual,
                        3,
                        "Requesting a round earlier than the last complete round should return the last complete "
                                + "round");
            }
        }

        try (final AutoCloseableWrapper<SignedState> lastWrapper = manager.getLatestImmutableState()) {
            final SignedState last = lastWrapper.get();

            // Search for a round and hash that match the last state exactly
            try (final AutoCloseableWrapper<SignedState> actualWrapper =
                    manager.find(last.getRound(), last.getState().getHash())) {
                final SignedState actual = actualWrapper.get();
                // the last state should have 4 reservations:
                // 2 for being the last state held by the manager
                // 1 for the lastWrapper AutoCloseableWrapper
                // 1 for the actualWrapper AutoCloseableWrapper
                verifyFoundSignedState(last, actual, 4, "Requesting the last round should return the last round");
            }

            for (long i = manager.getLastCompleteRound() + 1; i <= last.getRound(); i++) {
                // Search for a round later than the last complete round with a hash that doesn't match any state
                try (final AutoCloseableWrapper<SignedState> actualWrapper =
                        manager.find(i, RandomUtils.randomHash(random))) {
                    final SignedState actual = actualWrapper.get();
                    assertNull(
                            actual,
                            "Requesting a round later than the last complete "
                                    + "round with an unknown hash should return null");
                }
            }
        }
    }

    private void verifyFoundSignedState(
            final SignedState lastComplete,
            final SignedState actual,
            final int numReservations,
            final String wrongStateMsg) {

        assertEquals(lastComplete, actual, wrongStateMsg);

        assertEquals(numReservations, actual.getReservationCount(), "Incorrect number of reservations");
    }
}
