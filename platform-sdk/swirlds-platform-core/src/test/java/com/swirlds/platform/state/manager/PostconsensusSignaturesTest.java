package com.swirlds.platform.state.manager;

import com.swirlds.common.config.StateConfig;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.system.transaction.internal.StateSignatureTransaction;
import com.swirlds.common.test.fixtures.RandomAddressBookGenerator;
import com.swirlds.platform.components.state.output.StateHasEnoughSignaturesConsumer;
import com.swirlds.platform.components.state.output.StateLacksSignaturesConsumer;
import com.swirlds.platform.state.RandomSignedStateGenerator;
import com.swirlds.platform.state.signed.ReservedSignedState;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.state.signed.SignedStateManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static com.swirlds.platform.state.manager.SignedStateManagerTestUtils.buildReallyFakeSignature;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Tests for {@link SignedStateManager#handlePostconsensusSignatureTransaction}
 */
class PostconsensusSignaturesTest extends AbstractSignedStateManagerTest {

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
     */
    private StateHasEnoughSignaturesConsumer stateHasEnoughSignaturesConsumer() {
        return ss -> stateHasEnoughSignaturesCount.getAndIncrement();
    }

    @Test
    @DisplayName("Postconsensus signatures")
    void postconsensusSignatureTests() throws InterruptedException {
        final int count = 100;
        final StateConfig stateConfig = buildStateConfig();

        final SignedStateManager manager = new SignedStateManagerBuilder(stateConfig)
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

        for (int round = 0; round < count; round++) {
            final SignedState signedState = states.get(round);

            signedStates.put((long) round, signedState);
            highestRound.set(round);

            manager.addState(signedState);

            for (int node = 0; node < addressBook.getSize(); node++) {
                manager.handlePostconsensusSignatureTransaction(
                        addressBook.getNodeId(node),
                        new StateSignatureTransaction(
                                round,
                                buildReallyFakeSignature(),
                                states.get(round).getState().getHash()));
            }

            try (final ReservedSignedState lastState = manager.getLatestImmutableState("test")) {
                assertSame(signedState, lastState.get(), "last signed state has unexpected value");
            }
            try (final ReservedSignedState lastCompletedState = manager.getLatestSignedState("test")) {
                assertSame(
                        signedStates.get((long)round),
                        lastCompletedState.get(),
                        "unexpected last completed state");
            }

            validateCallbackCounts(0, round + 1);
        }

        // Check reservation counts.
        validateReservationCounts(round -> round < signedStates.size() - 1);

        // We don't expect any further callbacks. But wait a little while longer in case there is something unexpected.
        SECONDS.sleep(1);

        validateCallbackCounts(0, count);
    }
}
