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
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.Signature;
import com.swirlds.common.merkle.crypto.MerkleCryptoFactory;
import com.swirlds.common.metrics.noop.NoOpMetrics;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.system.status.PlatformStatus;
import com.swirlds.common.system.status.PlatformStatusGetter;
import com.swirlds.common.system.status.StatusActionSubmitter;
import com.swirlds.common.system.transaction.internal.StateSignatureTransaction;
import com.swirlds.common.test.fixtures.AssertionUtils;
import com.swirlds.common.test.fixtures.RandomAddressBookGenerator;
import com.swirlds.common.test.fixtures.RandomAddressBookGenerator.WeightDistributionStrategy;
import com.swirlds.common.test.fixtures.RandomUtils;
import com.swirlds.common.threading.manager.AdHocThreadManager;
import com.swirlds.platform.crypto.PlatformSigner;
import com.swirlds.platform.dispatch.DispatchBuilder;
import com.swirlds.platform.dispatch.DispatchConfiguration;
import com.swirlds.platform.event.preconsensus.PreconsensusEventWriter;
import com.swirlds.platform.state.RandomSignedStateGenerator;
import com.swirlds.platform.state.signed.ReservedSignedState;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.state.signed.SourceOfSignedState;
import com.swirlds.test.framework.config.TestConfigBuilder;
import com.swirlds.test.framework.context.TestPlatformContextBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * This class contains basic sanity checks for the {@code StateManagementComponent}. Not all inputs and outputs are
 * testable from the component level due to operations like writing states to disk being dependent on wall clock time
 * which is not able to be manipulated. These operations are tested in targeted class tests, not here.
 */
class StateManagementComponentTests {

    private static final String MAIN = "main";
    private static final String SWIRLD = "swirld123";
    private static final NodeId NODE_ID = new NodeId(0L);
    private static final int NUM_NODES = 4;

    private final int roundsToKeepForSigning = 5;
    private final TestPrioritySystemTransactionConsumer systemTransactionConsumer =
            new TestPrioritySystemTransactionConsumer();
    private final TestSignedStateWrapperConsumer newLatestCompleteStateConsumer = new TestSignedStateWrapperConsumer();
    private final TestSignedStateWrapperConsumer stateHasEnoughSignaturesConsumer =
            new TestSignedStateWrapperConsumer();
    private final TestSignedStateWrapperConsumer stateLacksSignaturesConsumer = new TestSignedStateWrapperConsumer();
    private final TestIssConsumer issConsumer = new TestIssConsumer();
    private final TestStateToDiskAttemptConsumer stateToDiskAttemptConsumer = new TestStateToDiskAttemptConsumer();

    @TempDir
    private Path tmpDir;

    @BeforeEach
    protected void beforeEach() {
        systemTransactionConsumer.reset();
        newLatestCompleteStateConsumer.reset();
        stateHasEnoughSignaturesConsumer.reset();
        stateLacksSignaturesConsumer.reset();
        issConsumer.reset();
    }

    /**
     * Verify that when the component is provided a new signed state from transactions, it submits a state signature
     * system transaction.
     * <p>
     * Also verify that states are passed to the lacks signatures consumer, since no states are signed in this test.
     */
    @Test
    @DisplayName("New signed state from transactions produces signature system transaction")
    void newStateFromTransactionsSubmitsSystemTransaction() {
        final Random random = RandomUtils.getRandomPrintSeed();
        final int numSignedStates = 100;
        final AddressBook addressBook = new RandomAddressBookGenerator(random)
                .setSize(NUM_NODES)
                .setWeightDistributionStrategy(WeightDistributionStrategy.BALANCED)
                .build();
        final DefaultStateManagementComponent component = newStateManagementComponent(addressBook);

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
                verifyStateLacksSignaturesConsumer(stateEjected, roundEjected);
            }
        }

        assertEquals(
                numSignedStates,
                systemTransactionConsumer.getNumSubmitted(),
                "Invalid number of system transactions submitted");

        component.stop();
    }

    /**
     * Verify that when the component is provided a complete signed state to load, it is returned when asked for the
     * latest complete signed state.
     */
    @Test
    @DisplayName("Signed state to load becomes the latest complete signed state")
    void signedStateToLoadIsLatestComplete() {
        final Random random = RandomUtils.getRandomPrintSeed();
        final AddressBook addressBook = new RandomAddressBookGenerator(random)
                .setSize(NUM_NODES)
                .setWeightDistributionStrategy(WeightDistributionStrategy.BALANCED)
                .build();
        final DefaultStateManagementComponent component = newStateManagementComponent(addressBook);

        component.start();

        final int firstRound = 1;
        final int lastRound = 100;

        // Send a bunch of signed states for the component to load, in order
        for (int roundNum = firstRound; roundNum <= lastRound; roundNum++) {
            final SignedState signedState =
                    new RandomSignedStateGenerator(random).setRound(roundNum).build();

            final SignedState signedStateSpy = spy(signedState);
            when(signedStateSpy.isComplete()).thenReturn(true);

            component.stateToLoad(signedStateSpy, SourceOfSignedState.DISK);

            // Some basic assertions on the signed state provided to the new latest complete state consumer
            verifyNewLatestCompleteStateConsumer(roundNum, signedStateSpy);

            verifyLatestCompleteState(signedStateSpy, component);
        }

        // Send a bunch of signed states that are older than the latest complete signed state
        for (int roundNum = firstRound; roundNum < lastRound; roundNum++) {
            final SignedState signedState =
                    new RandomSignedStateGenerator(random).setRound(roundNum).build();

            final SignedState signedStateSpy = spy(signedState);
            when(signedStateSpy.isComplete()).thenReturn(true);

            component.stateToLoad(signedStateSpy, SourceOfSignedState.DISK);

            // The signed state provided is old, so the consumer should not be invoked again
            assertEquals(
                    lastRound,
                    newLatestCompleteStateConsumer.getNumInvocations(),
                    "The new latest complete state consumer should not be invoked for states that are older than the "
                            + "current latest complete state");

            // The latest complete signed state should still be the same as before and not the one just provided
            verifyLatestCompleteState(newLatestCompleteStateConsumer.getLastSignedState(), component);
        }

        component.stop();
    }

    private void verifyLatestCompleteState(
            final SignedState expectedSignedState, final StateManagementComponent component) {
        // Check that the correct signed state is provided when the latest complete state is requested
        try (final ReservedSignedState wrapper = component.getLatestSignedState("test")) {
            assertEquals(expectedSignedState, wrapper.get(), "Incorrect latest signed state provided");

            // 1 for being the latest complete signed state
            // 1 for being the latest signed state
            // 1 for the AutoCloseableWrapper
            assertEquals(3, wrapper.get().getReservationCount(), "Incorrect number of reservations");
        }
        assertEquals(
                expectedSignedState.getRound(), component.getLastCompleteRound(), "Incorrect latest complete round");
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
        final AddressBook addressBook = new RandomAddressBookGenerator(random)
                .setSize(NUM_NODES)
                .setWeightDistributionStrategy(WeightDistributionStrategy.BALANCED)
                .build();
        final DefaultStateManagementComponent component = newStateManagementComponent(addressBook);

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
                        () -> {
                            verifyNewLatestCompleteStateConsumer(finalRoundNum / 2, signedState);
                            verifyLatestCompleteState(signedState, component);
                        },
                        Duration.ofSeconds(2),
                        "The unit test failed.");

                // Check that the consumer for state has enough signatures is invoked correctly
                verifyStateHasEnoughSignaturesConsumer(signedState, roundNum / 2);
            }
        }

        component.stop();
    }

    @Test
    @DisplayName("Signed States For Old Rounds Are Not Processed")
    void signedStateFromTransactionsCodePath() {
        final Random random = RandomUtils.getRandomPrintSeed();
        final AddressBook addressBook = new RandomAddressBookGenerator(random)
                .setSize(NUM_NODES)
                .setWeightDistributionStrategy(WeightDistributionStrategy.BALANCED)
                .build();
        final DefaultStateManagementComponent component = newStateManagementComponent(addressBook);

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
    void testReconnectStateSaved() throws InterruptedException {
        final Random random = RandomUtils.getRandomPrintSeed();
        final AddressBook addressBook = new RandomAddressBookGenerator(random)
                .setSize(NUM_NODES)
                .setWeightDistributionStrategy(WeightDistributionStrategy.BALANCED)
                .build();

        final DefaultStateManagementComponent component = newStateManagementComponent(addressBook);

        component.start();

        final List<NodeId> majorityWeightNodes =
                IntStream.range(0, NUM_NODES - 1).mapToObj(NodeId::new).toList();
        final SignedState signedState = new RandomSignedStateGenerator(random)
                .setRound(10)
                .setSigningNodeIds(majorityWeightNodes)
                .build();
        component.stateToLoad(signedState, SourceOfSignedState.RECONNECT);
        final StateToDiskAttempt attempt =
                stateToDiskAttemptConsumer.getAttemptQueue().poll(5, TimeUnit.SECONDS);
        assertNotNull(attempt, "The state should be saved to disk.");
        assertEquals(
                attempt.signedState(), signedState, "The state saved to disk should be the same as the state loaded.");
        assertTrue(attempt.success(), "The state saved to disk should be marked as a success.");

        component.stop();
    }

    private void verifyStateLacksSignaturesConsumer(final SignedState signedState, final int numInvocations) {
        assertEquals(
                numInvocations,
                stateLacksSignaturesConsumer.getNumInvocations(),
                "Incorrect number of invocations to the state lacks signatures consumer");
        // The state should be announced as having enough signatures
        assertEquals(
                signedState,
                stateLacksSignaturesConsumer.getLastSignedState(),
                "Last state to state lacks signatures consumer is not correct.");
    }

    private void verifyStateHasEnoughSignaturesConsumer(final SignedState signedState, final int numInvocations) {
        assertEquals(
                numInvocations,
                stateHasEnoughSignaturesConsumer.getNumInvocations(),
                "Incorrect number of invocations to the state has enough signatures consumer");
        // The state should be announced as having enough signatures
        assertEquals(
                signedState,
                stateHasEnoughSignaturesConsumer.getLastSignedState(),
                "Last state to collect enough signatures is not correct.");
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
                .withValue("state.roundsToKeepForSigning", roundsToKeepForSigning)
                .withValue("state.saveStatePeriod", 1)
                .withValue("state.savedStateDirectory", tmpDir.toFile().toString());
    }

    @NonNull
    private DefaultStateManagementComponent newStateManagementComponent(@NonNull final AddressBook addressBook) {
        return newStateManagementComponent(addressBook, defaultConfigBuilder());
    }

    @NonNull
    private DefaultStateManagementComponent newStateManagementComponent(
            @NonNull final AddressBook addressBook, @NonNull final TestConfigBuilder configBuilder) {

        configBuilder.withValue("state.savedStateDirectory", tmpDir.toFile().toString());

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
                addressBook,
                signer,
                MAIN,
                NODE_ID,
                SWIRLD,
                systemTransactionConsumer::consume,
                stateToDiskAttemptConsumer,
                newLatestCompleteStateConsumer::consume,
                stateLacksSignaturesConsumer::consume,
                stateHasEnoughSignaturesConsumer::consume,
                issConsumer::consume,
                (msg) -> {},
                (msg, t, code) -> {},
                mock(PreconsensusEventWriter.class),
                platformStatusGetter,
                mock(StatusActionSubmitter.class));

        dispatchBuilder.start();

        return stateManagementComponent;
    }
}
