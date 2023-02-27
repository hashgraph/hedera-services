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

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.test.RandomAddressBookGenerator;
import com.swirlds.common.utility.AutoCloseableWrapper;
import com.swirlds.platform.components.state.output.StateHasEnoughSignaturesConsumer;
import com.swirlds.platform.components.state.output.StateLacksSignaturesConsumer;
import com.swirlds.platform.state.RandomSignedStateGenerator;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.state.signed.SignedStateManager;
import com.swirlds.test.framework.TestQualifierTags;
import java.util.HashMap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@DisplayName("SignedStateManager: Register States Without Signatures Test")
public class RegisterStatesWithoutSignaturesTest extends AbstractSignedStateManagerTest {

    // Note: this unit test was long and complex, so it was split into its own class.
    // As such, this test was designed differently than it would be designed if it were sharing
    // the class file with other tests.
    // DO NOT ADD ADDITIONAL UNIT TESTS TO THIS CLASS!

    private final AddressBook addressBook = new RandomAddressBookGenerator(random)
            .setSize(4)
            .setSequentialIds(false)
            .build();

    final long selfId = addressBook.getId(0);

    /**
     * Called on each state as it gets too old without collecting enough signatures.
     * <p>
     * This consumer is provided by the wiring layer, so it should release the resource when finished.
     */
    private StateLacksSignaturesConsumer stateLacksSignaturesConsumer() {
        return ssw -> {
            stateLacksSignaturesCount.getAndIncrement();

            assertEquals(highestRound.get() - roundsToKeepForSigning, ssw.get().getRound(), "unexpected round retired");
            assertSame(
                    signedStates.get(highestRound.get() - roundsToKeepForSigning),
                    ssw.get(),
                    "unexpected state was retired");
            ssw.release();
        };
    }

    /**
     * Called on each state as it gathers enough signatures to be complete.
     * <p>
     * This consumer is provided by the wiring layer, so it should release the resource when finished.
     */
    private StateHasEnoughSignaturesConsumer stateHasEnoughSignaturesConsumer() {
        return ssw -> {
            stateHasEnoughSignaturesCount.getAndIncrement();
            ssw.release();
        };
    }

    /**
     * Keep adding new states to the manager but never sign any of them (other than self signatures).
     */
    @Test
    @Tag(TestQualifierTags.TIME_CONSUMING)
    @DisplayName("Register States Without Signatures")
    void registerStatesWithoutSignatures() throws InterruptedException {
        final SignedStateManager manager = new SignedStateManagerBuilder(addressBook, stateConfig, selfId)
                .stateLacksSignaturesConsumer(stateLacksSignaturesConsumer())
                .stateHasEnoughSignaturesConsumer(stateHasEnoughSignaturesConsumer())
                .build();

        // Create a series of signed states. Don't add any signatures. Self signatures will be automatically added.
        final int count = 100;
        for (int round = 0; round < count; round++) {
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
                assertNull(lastCompletedState.get(), "no states should be completed in this test");
            }

            final int expectedUnsignedStates = Math.max(0, round - roundsToKeepForSigning + 1);

            validateCallbackCounts(expectedUnsignedStates, 0);
        }

        validateReservationCounts(round -> round < signedStates.size() - roundsToKeepForSigning);

        // We don't expect any further callbacks. But wait a little while longer in case there is something
        // unexpected.
        SECONDS.sleep(1);

        validateCallbackCounts(count - roundsToKeepForSigning, 0);
    }
}
