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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.Signature;
import com.swirlds.common.system.address.Address;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.test.RandomAddressBookGenerator;
import com.swirlds.common.utility.AutoCloseableWrapper;
import com.swirlds.platform.components.state.output.StateLacksSignaturesConsumer;
import com.swirlds.platform.state.RandomSignedStateGenerator;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.state.signed.SignedStateManager;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("SignedStateManager: Old Complete State Eventually Released Test")
class OldCompleteStateEventuallyReleasedTest extends AbstractSignedStateManagerTest {

    // Note: this unit test was long and complex, so it was split into its own class.
    // As such, this test was designed differently than it would be designed if it were sharing
    // the class file with other tests.
    // DO NOT ADD ADDITIONAL UNIT TESTS TO THIS CLASS!

    private final AddressBook addressBook = new RandomAddressBookGenerator(random)
            .setSize(4)
            .setSequentialIds(false)
            .build();

    /**
     * Called on each state as it gets too old without collecting enough signatures.
     * <p>
     * This consumer is provided by the wiring layer, so it should release the resource when finished.
     */
    private StateLacksSignaturesConsumer stateLacksSignaturesConsumer() {
        // No state is unsigned in this test. If this method is called then the test is expected to fail.
        return ssw -> {
            stateLacksSignaturesCount.getAndIncrement();
            ssw.release();
        };
    }

    /**
     * Keep adding new states to the manager but never sign any of them (other than self signatures).
     */
    @Test
    @DisplayName("Old Complete State Eventually Released")
    void oldCompleteStateEventuallyReleased() throws InterruptedException {

        final SignedStateManager manager = new SignedStateManagerBuilder(stateConfig)
                .stateLacksSignaturesConsumer(stateLacksSignaturesConsumer())
                .build();

        final Hash stateHash = randomHash(random);
        final Map<Long, Signature> signatures = new HashMap<>();
        for (final Address address : addressBook) {
            signatures.put(address.getId(), buildFakeSignature(address.getSigPublicKey(), stateHash));
        }

        // Add a complete signed state. Eventually this will get released.
        final SignedState stateFromDisk = new RandomSignedStateGenerator(random)
                .setAddressBook(addressBook)
                .setRound(0)
                .setSignatures(signatures)
                .build();
        stateFromDisk.getState().setHash(stateHash);

        signedStates.put(0L, stateFromDisk);
        highestRound.set(0);
        manager.addCompleteSignedState(stateFromDisk);

        // Create a series of signed states. Don't add any signatures. Self signatures will be automatically added.
        final int count = roundsToKeepForSigning * 100;
        for (int round = 1; round < count; round++) {
            final SignedState signedState = new RandomSignedStateGenerator(random)
                    .setAddressBook(addressBook)
                    .setRound(round)
                    .setSignatures(new HashMap<>())
                    .build();

            signedStates.put((long) round, signedState);
            highestRound.set(round);

            manager.addUnsignedState(signedState);

            try (final AutoCloseableWrapper<SignedState> lastState = manager.getLatestImmutableState()) {
                assertSame(signedState, lastState.get(), "last signed state has unexpected value");
            }
            try (final AutoCloseableWrapper<SignedState> lastCompletedState = manager.getLatestSignedState()) {

                if (round >= roundsToKeepForSigning) {
                    assertNull(lastCompletedState.get(), "initial state should have been released");
                } else {
                    assertSame(lastCompletedState.get(), stateFromDisk);
                }
            }
        }

        validateReservationCounts(round -> round < signedStates.size() - roundsToKeepForSigning);

        // We don't expect any further callbacks. But wait a little while longer in case there is something unexpected.
        SECONDS.sleep(1);

        validateCallbackCounts(count - roundsToKeepForSigning - 1, 0);
    }
}
