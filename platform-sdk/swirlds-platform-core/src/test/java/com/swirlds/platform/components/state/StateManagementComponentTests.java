/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.components.state;

import static com.swirlds.platform.state.manager.SignedStateManagerTestUtils.buildFakeSignature;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.swirlds.common.config.StateConfig_;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.Signature;
import com.swirlds.common.merkle.crypto.MerkleCryptoFactory;
import com.swirlds.common.metrics.noop.NoOpMetrics;
import com.swirlds.common.platform.NodeId;
import com.swirlds.common.test.fixtures.AssertionUtils;
import com.swirlds.common.test.fixtures.RandomUtils;
import com.swirlds.common.threading.manager.AdHocThreadManager;
import com.swirlds.platform.crypto.PlatformSigner;
import com.swirlds.platform.dispatch.DispatchBuilder;
import com.swirlds.platform.dispatch.DispatchConfiguration;
import com.swirlds.platform.state.RandomSignedStateGenerator;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.state.signed.SourceOfSignedState;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.platform.system.status.PlatformStatus;
import com.swirlds.platform.system.status.PlatformStatusGetter;
import com.swirlds.platform.system.transaction.StateSignatureTransaction;
import com.swirlds.test.framework.config.TestConfigBuilder;
import com.swirlds.test.framework.context.TestPlatformContextBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * This class contains basic sanity checks for the {@code StateManagementComponent}. Not all inputs and outputs are
 * testable from the component level due to operations like writing states to disk being dependent on wall clock time
 * which is not able to be manipulated. These operations are tested in targeted class tests, not here.
 */
class StateManagementComponentTests {
    private static final int NUM_NODES = 4;

    private final int roundsToKeepForSigning = 5;
    private final TestPrioritySystemTransactionConsumer systemTransactionConsumer =
            new TestPrioritySystemTransactionConsumer();
    private final TestSignedStateWrapperConsumer newLatestCompleteStateConsumer = new TestSignedStateWrapperConsumer();
    private final TestSavedStateController controller = new TestSavedStateController();

    @BeforeEach
    protected void beforeEach() {
        systemTransactionConsumer.reset();
        newLatestCompleteStateConsumer.reset();
        controller.getStatesQueue().clear();
    }

    /**
     * Verify that when the component is provided a new signed state from transactions, it submits a state signature
     * system transaction.
     */
    @Test
    @DisplayName("New signed state from transactions produces signature system transaction")
    void newStateFromTransactionsSubmitsSystemTransaction() {
        final Random random = RandomUtils.getRandomPrintSeed();
        final int numSignedStates = 100;
        final DefaultStateManagementComponent component = newStateManagementComponent();

        component.start();

        final Map<Integer, SignedState> signedStates = new HashMap<>();

        for (int roundNum = 1; roundNum <= numSignedStates; roundNum++) {
            final SignedState signedState =
                    new RandomSignedStateGenerator(random).setRound(roundNum).build();
            signedStates.put(roundNum, signedState);
            final Hash hash = getHash(signedState);
            signedState.getState().setHash(null); // we expect this to trigger hashing the state.

            component.newSignedStateFromTransactions(signedState.reserve("test"));
            final Hash hash2 = getHash(signedState);
            assertEquals(hash, hash2, "The same hash must be computed and added to the state.");

            verifySystemTransaction(roundNum, hash);

            if (roundNum > roundsToKeepForSigning) {
                final int roundEjected = roundNum - roundsToKeepForSigning;
                final SignedState stateEjected = signedStates.get(roundEjected);
            }
        }

        assertEquals(
                numSignedStates,
                systemTransactionConsumer.getNumSubmitted(),
                "Invalid number of system transactions submitted");

        component.stop();
    }

    private void verifyNewLatestCompleteStateConsumer(final int roundNum, final SignedState signedState) {
        final SignedState lastCompleteSignedState = newLatestCompleteStateConsumer.getLastSignedState();
        assertEquals(
                roundNum,
                newLatestCompleteStateConsumer.getNumInvocations(),
                "Invalid number of new latest complete signed state consumer invocations.");
        assertEquals(signedState, lastCompleteSignedState, "Incorrect new latest signed state provided to consumer");

        // 1 for being the latest complete signed state
        // 1 for being the latest signed state
        assertEquals(
                2,
                lastCompleteSignedState.getReservationCount(),
                "Incorrect number of reservations for state round " + lastCompleteSignedState.getRound());
    }

    /**
     * Verify that as signatures are sent to the component, various consumers are invoked
     */
    @Test
    @DisplayName("State signatures are applied and consumers are invoked")
    void stateSignaturesAppliedAndTracked() {
        final Random random = RandomUtils.getRandomPrintSeed();
        final DefaultStateManagementComponent component = newStateManagementComponent();

        component.start();

        final int firstRound = 1;
        final int lastRound = 100;

        for (int roundNum = firstRound; roundNum < lastRound; roundNum++) {
            final SignedState signedState = new RandomSignedStateGenerator(random)
                    .setRound(roundNum)
                    .setSigningNodeIds(List.of())
                    .build();

            component.newSignedStateFromTransactions(signedState.reserve("test"));

            if (roundNum % 2 == 0) {
                // Send signatures from all nodes for this signed state
                allNodesSign(signedState, component);

                // This state should be sent out as the latest complete state
                final int finalRoundNum = roundNum;
                AssertionUtils.assertEventuallyDoesNotThrow(
                        () -> verifyNewLatestCompleteStateConsumer(finalRoundNum / 2, signedState),
                        Duration.ofSeconds(2),
                        "The unit test failed.");
            }
        }

        component.stop();
    }

    @Test
    @DisplayName("Signed States For Old Rounds Are Not Processed")
    void signedStateFromTransactionsCodePath() {
        final Random random = RandomUtils.getRandomPrintSeed();
        final DefaultStateManagementComponent component = newStateManagementComponent();

        systemTransactionConsumer.reset();
        component.start();

        final SignedState signedStateRound1 = new RandomSignedStateGenerator(random)
                .setRound(1)
                .setSigningNodeIds(List.of())
                .build();
        signedStateRound1.getState().setHash(null);

        final SignedState signedStateRound2 = new RandomSignedStateGenerator(random)
                .setRound(2)
                .setSigningNodeIds(List.of())
                .build();
        signedStateRound2.getState().setHash(null);

        final SignedState signedStateRound3 = new RandomSignedStateGenerator(random)
                .setRound(3)
                .setSigningNodeIds(List.of())
                .build();
        signedStateRound3.getState().setHash(null);

        // Transaction proceeds, state is hashed, signature of hash sent, and state is set as last state.
        component.newSignedStateFromTransactions(signedStateRound2.reserve("test"));
        assertNotNull(
                signedStateRound2.getState().getHash(),
                "The hash for transaction states that are processed will not be null.");
        assertEquals(
                systemTransactionConsumer.getNumSubmitted(),
                1,
                "The transaction could should be 1 for processing a valid state.");
        assertEquals(
                component.getLatestImmutableState("test").get(),
                signedStateRound2,
                "The last state should be the same as the signed state for round 2.");

        // Transaction fails to be signed due to lower round.
        component.newSignedStateFromTransactions(signedStateRound1.reserve("test"));
        assertNull(
                signedStateRound1.getState().getHash(),
                "The states with older rounds will not have their hash computed.");
        assertEquals(
                systemTransactionConsumer.getNumSubmitted(),
                1,
                "The states with older rounds will not have hash signatures transmitted.");
        assertEquals(
                component.getLatestImmutableState("test").get(),
                signedStateRound2,
                "The states with older rounds will not be saved as the latest state.");

        // Transaction proceeds, state is hashed, signature of hash sent, and state is set as last state.
        component.newSignedStateFromTransactions(signedStateRound3.reserve("test"));
        assertNotNull(
                signedStateRound3.getState().getHash(),
                "The state should be processed and have a hash computed and set.");
        assertEquals(
                systemTransactionConsumer.getNumSubmitted(),
                2,
                "The signed hash for processed states will be transmitted.");
        assertEquals(
                component.getLatestImmutableState("test").get(),
                signedStateRound3,
                "The processed state should be set as the latest state in teh signed state manager.");

        component.stop();
    }

    @Test
    @DisplayName("Test that the state is saved to disk when it is received via reconnect")
    void testReconnectStateSaved() {
        final Random random = RandomUtils.getRandomPrintSeed();
        final DefaultStateManagementComponent component = newStateManagementComponent();

        component.start();

        final List<NodeId> majorityWeightNodes =
                IntStream.range(0, NUM_NODES - 1).mapToObj(NodeId::new).toList();
        final SignedState signedState = new RandomSignedStateGenerator(random)
                .setRound(10)
                .setSigningNodeIds(majorityWeightNodes)
                .build();
        component.stateToLoad(signedState, SourceOfSignedState.RECONNECT);
        final SignedState stateSentForWriting = controller.getStatesQueue().poll();
        assertNotNull(stateSentForWriting, "The state should be saved to disk.");
        assertEquals(
                stateSentForWriting, signedState, "The state saved to disk should be the same as the state loaded.");

        component.stop();
    }

    private void allNodesSign(final SignedState signedState, final DefaultStateManagementComponent component) {
        final AddressBook addressBook = signedState.getAddressBook();
        IntStream.range(0, NUM_NODES).forEach(index -> component
                .getSignedStateManager()
                .handlePreconsensusSignatureTransaction(
                        addressBook.getNodeId(index),
                        stateSignatureTransaction(addressBook.getNodeId(index), signedState)));
    }

    @NonNull
    private static StateSignatureTransaction stateSignatureTransaction(
            @NonNull final NodeId signingNodeId, @Nullable final SignedState stateToSign) {

        if (stateToSign == null) {
            // We are being asked to sign a non-existent round.
            return null;
        }

        final AddressBook addressBook = stateToSign.getAddressBook();
        final Hash hash = stateToSign.getState().getHash();

        final Signature signature =
                buildFakeSignature(addressBook.getAddress(signingNodeId).getSigPublicKey(), hash);

        return new StateSignatureTransaction(stateToSign.getRound(), signature, hash);
    }

    private static StateSignatureTransaction issStateSignatureTransaction(
            final SignedState stateToSign, final NodeId signingNodeId, final Hash hash) {

        if (stateToSign == null) {
            // We are being asked to sign a non-existent round.
            return null;
        }

        final AddressBook addressBook = stateToSign.getAddressBook();

        final Signature signature =
                buildFakeSignature(addressBook.getAddress(signingNodeId).getSigPublicKey(), hash);

        return new StateSignatureTransaction(stateToSign.getRound(), signature, hash);
    }

    private Hash getHash(final SignedState signedState) {
        try {
            return MerkleCryptoFactory.getInstance()
                    .digestTreeAsync(signedState.getState())
                    .get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    private void verifySystemTransaction(final int roundNum, final Hash hash) {
        assertTrue(
                StateSignatureTransaction.class.isAssignableFrom(
                        systemTransactionConsumer.getLastSubmitted().getClass()),
                "Unexpected system transaction type submitted");
        final StateSignatureTransaction signatureTransaction =
                (StateSignatureTransaction) systemTransactionConsumer.getLastSubmitted();
        assertEquals(roundNum, signatureTransaction.getRound(), "Incorrect round in state signature transaction");
        assertEquals(hash, signatureTransaction.getStateHash(), "Incorrect hash in state signature transaction");
    }

    @NonNull
    private TestConfigBuilder defaultConfigBuilder() {
        return new TestConfigBuilder()
                .withValue(StateConfig_.ROUNDS_TO_KEEP_FOR_SIGNING, roundsToKeepForSigning)
                .withValue(StateConfig_.SAVE_STATE_PERIOD, 1);
    }

    @NonNull
    private DefaultStateManagementComponent newStateManagementComponent() {
        return newStateManagementComponent(defaultConfigBuilder());
    }

    @NonNull
    private DefaultStateManagementComponent newStateManagementComponent(
            @NonNull final TestConfigBuilder configBuilder) {

        final PlatformContext platformContext = TestPlatformContextBuilder.create()
                .withMetrics(new NoOpMetrics())
                .withConfiguration(configBuilder.getOrCreateConfig())
                .build();

        final PlatformSigner signer = mock(PlatformSigner.class);
        when(signer.sign(any(Hash.class))).thenReturn(mock(Signature.class));

        final PlatformStatusGetter platformStatusGetter = mock(PlatformStatusGetter.class);
        when(platformStatusGetter.getCurrentStatus()).thenReturn(PlatformStatus.ACTIVE);

        final DispatchConfiguration dispatchConfiguration =
                platformContext.getConfiguration().getConfigData(DispatchConfiguration.class);

        final DispatchBuilder dispatchBuilder = new DispatchBuilder(dispatchConfiguration);

        final DefaultStateManagementComponent stateManagementComponent = new DefaultStateManagementComponent(
                platformContext,
                AdHocThreadManager.getStaticThreadManager(),
                dispatchBuilder,
                signer,
                systemTransactionConsumer::consume,
                newLatestCompleteStateConsumer::consume,
                (msg, t, code) -> {},
                platformStatusGetter,
                controller,
                r -> {});

        dispatchBuilder.start();

        return stateManagementComponent;
    }
}
