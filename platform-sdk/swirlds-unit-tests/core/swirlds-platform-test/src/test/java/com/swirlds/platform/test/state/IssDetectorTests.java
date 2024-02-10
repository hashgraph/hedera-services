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

package com.swirlds.platform.test.state;

import static com.swirlds.common.test.fixtures.RandomUtils.getRandomPrintSeed;
import static com.swirlds.common.test.fixtures.RandomUtils.randomHash;
import static com.swirlds.common.utility.Threshold.MAJORITY;
import static com.swirlds.common.utility.Threshold.SUPER_MAJORITY;
import static com.swirlds.platform.state.iss.IssDetector.DO_NOT_IGNORE_ROUNDS;
import static com.swirlds.platform.test.state.RoundHashValidatorTests.generateCatastrophicNodeHashes;
import static com.swirlds.platform.test.state.RoundHashValidatorTests.generateNodeHashes;
import static com.swirlds.platform.test.state.RoundHashValidatorTests.generateRegularNodeHashes;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.Signature;
import com.swirlds.common.platform.NodeId;
import com.swirlds.common.test.fixtures.platform.TestPlatformContextBuilder;
import com.swirlds.platform.components.transaction.system.ScopedSystemTransaction;
import com.swirlds.platform.consensus.ConsensusConfig;
import com.swirlds.platform.state.State;
import com.swirlds.platform.state.iss.internal.HashValidityStatus;
import com.swirlds.platform.state.signed.ReservedSignedState;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.system.BasicSoftwareVersion;
import com.swirlds.platform.system.address.Address;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.platform.system.state.notifications.IssNotification.IssType;
import com.swirlds.platform.system.transaction.StateSignatureTransaction;
import com.swirlds.platform.test.fixtures.addressbook.RandomAddressBookGenerator;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

@DisplayName("ConsensusHashManager Tests")
class IssDetectorTests {

    @Test
    @DisplayName("Valid Signatures After Hash Test")
    void validSignaturesAfterHashTest() {
        final Random random = getRandomPrintSeed();

        final AddressBook addressBook = new RandomAddressBookGenerator(random)
                .setSize(100)
                .setAverageWeight(100)
                .setWeightStandardDeviation(50)
                .build();

        final PlatformContext platformContext =
                TestPlatformContextBuilder.create().build();

        final IssDetectorTestHelper manager =
                new IssDetectorTestHelper(platformContext, addressBook, DO_NOT_IGNORE_ROUNDS);

        final int rounds = 1_000;
        for (long round = 1; round <= rounds; round++) {
            final Hash roundHash = randomHash(random);

            if (round == 1) {
                manager.overridingState(mockState(round, roundHash));
            } else {
                manager.roundCompleted(round);
                manager.newStateHashed(mockState(round, roundHash));
            }
            final long r = round;
            StreamSupport.stream(addressBook.spliterator(), false)
                    .map(a -> new ScopedSystemTransaction<>(
                            a.getNodeId(),
                            new BasicSoftwareVersion(1),
                            new StateSignatureTransaction(r, mock(Signature.class), roundHash)))
                    .forEach(t -> manager.handlePostconsensusSignatures(List.of(t)));
        }
        assertTrue(manager.getIssList().isEmpty(), "there should be no ISS notifications");
    }

    @Test
    @DisplayName("Mixed Order Test")
    void mixedOrderTest() {
        final Random random = getRandomPrintSeed();

        final AddressBook addressBook = new RandomAddressBookGenerator(random)
                .setSize(Math.max(10, random.nextInt(1000)))
                .setAverageWeight(100)
                .setWeightStandardDeviation(50)
                .build();

        final PlatformContext platformContext =
                TestPlatformContextBuilder.create().build();

        final NodeId selfId = addressBook.getNodeId(0);
        final int roundsNonAncient = platformContext
                .getConfiguration()
                .getConfigData(ConsensusConfig.class)
                .roundsNonAncient();

        // Build a roadmap for this test. Generate the hashes that will be sent to the manager, and determine
        // the expected result of adding these hashes to the manager.
        final List<RoundHashValidatorTests.HashGenerationData> roundData = new ArrayList<>(roundsNonAncient);
        final List<HashValidityStatus> expectedRoundStatus = new ArrayList<>(roundsNonAncient);
        int expectedSelfIssCount = 0;
        int expectedCatastrophicIssCount = 0;
        final List<Hash> selfHashes = new ArrayList<>(roundsNonAncient);
        for (int round = 0; round < roundsNonAncient; round++) {
            final RoundHashValidatorTests.HashGenerationData data;

            if (random.nextDouble() < 2.0 / 3) {
                // Choose hashes so that there is a valid consensus hash
                data = generateRegularNodeHashes(random, addressBook, round);

                HashValidityStatus expectedStatus = null;

                // Find this node's hash to figure out if we ISSed
                for (final RoundHashValidatorTests.NodeHashInfo nodeInfo : data.nodeList()) {
                    if (nodeInfo.nodeId() == selfId) {
                        final Hash selfHash = nodeInfo.nodeStateHash();
                        if (selfHash.equals(data.consensusHash())) {
                            expectedStatus = HashValidityStatus.VALID;
                        } else {
                            expectedStatus = HashValidityStatus.SELF_ISS;
                            expectedSelfIssCount++;
                        }
                        break;
                    }
                }

                assertNotNull(expectedRoundStatus, "expected status should have been set");

                roundData.add(data);
                expectedRoundStatus.add(expectedStatus);
            } else {
                // Choose hashes that will result in a catastrophic ISS
                data = generateCatastrophicNodeHashes(random, addressBook, round);
                roundData.add(data);
                expectedRoundStatus.add(HashValidityStatus.CATASTROPHIC_ISS);
                expectedCatastrophicIssCount++;
            }

            // Figure out self hashes
            for (final RoundHashValidatorTests.NodeHashInfo nodeHashInfo : data.nodeList()) {
                if (nodeHashInfo.nodeId() == selfId) {
                    final Hash selfHash = nodeHashInfo.nodeStateHash();
                    selfHashes.add(selfHash);
                    break;
                }
            }
        }

        final IssDetectorTestHelper manager =
                new IssDetectorTestHelper(platformContext, addressBook, DO_NOT_IGNORE_ROUNDS);

        manager.overridingState(mockState(0L, selfHashes.getFirst()));

        // Start collecting data for rounds.
        for (long round = 1; round < roundsNonAncient; round++) {
            manager.roundCompleted(round);
        }

        // Add all the self hashes.
        for (long round = 1; round < roundsNonAncient; round++) {
            manager.newStateHashed(mockState(round, selfHashes.get((int) round)));
        }

        // Report hashes from the network in random order
        final List<RoundHashValidatorTests.NodeHashInfo> operations = new ArrayList<>();
        while (!roundData.isEmpty()) {
            final int index = random.nextInt(roundData.size());
            operations.add(roundData.get(index).nodeList().removeFirst());
            if (roundData.get(index).nodeList().isEmpty()) {
                roundData.remove(index);
            }
        }

        assertEquals(roundsNonAncient * addressBook.getSize(), operations.size(), "unexpected number of operations");

        operations.stream()
                .map(nhi -> new ScopedSystemTransaction<>(
                        nhi.nodeId(),
                        new BasicSoftwareVersion(1),
                        new StateSignatureTransaction(nhi.round(), mock(Signature.class), nhi.nodeStateHash())))
                .forEach(t -> manager.handlePostconsensusSignatures(List.of(t)));

        // Shifting after completion should have no side effects
        for (long i = roundsNonAncient; i < 2L * roundsNonAncient - 1; i++) {
            manager.roundCompleted(i);
        }

        assertEquals(
                expectedSelfIssCount,
                manager.getIssList().stream()
                        .filter(n -> n.getIssType() == IssType.SELF_ISS)
                        .count(),
                "unexpected number of ISS callbacks");
        assertEquals(
                expectedCatastrophicIssCount,
                manager.getIssList().stream()
                        .filter(n -> n.getIssType() == IssType.CATASTROPHIC_ISS)
                        .count(),
                "unexpected number of catastrophic ISS callbacks");
        manager.getIssList().forEach(n -> {
            final IssType expectedType =
                    switch (expectedRoundStatus.get((int) n.getRound())) {
                        case SELF_ISS -> IssType.SELF_ISS;
                        case CATASTROPHIC_ISS -> IssType.CATASTROPHIC_ISS;
                            // if there was an other-ISS, then the round should still be valid
                        case VALID -> IssType.OTHER_ISS;
                        default -> throw new IllegalStateException(
                                "Unexpected value: " + expectedRoundStatus.get((int) n.getRound()));
                    };
            assertEquals(
                    expectedType,
                    n.getIssType(),
                    "Expected status for round %d to be %s but was %s"
                            .formatted(n.getRound(), expectedRoundStatus.get((int) n.getRound()), n.getIssType()));
        });
        final Set<Long> observedRounds = new HashSet<>();
        manager.getIssList()
                .forEach(n -> assertTrue(
                        observedRounds.add(n.getRound()), "rounds should trigger a notification at most once"));
    }

    /**
     * The method generateNodeHashes() doesn't account for self ID, and therefore doesn't guarantee that any particular
     * node will have an ISS. Regenerate data until we find a data set that results in a self ISS.
     */
    private static RoundHashValidatorTests.HashGenerationData generateDataWithSelfIss(
            final Random random, final AddressBook addressBook, final NodeId selfId, final long targetRound) {

        int triesRemaining = 1000;

        while (triesRemaining > 0) {
            triesRemaining--;

            final RoundHashValidatorTests.HashGenerationData data =
                    generateNodeHashes(random, addressBook, HashValidityStatus.SELF_ISS, targetRound);

            for (final RoundHashValidatorTests.NodeHashInfo info : data.nodeList()) {
                if (info.nodeId() == selfId) {
                    if (!info.nodeStateHash().equals(data.consensusHash())) {
                        return data;
                    }
                    break;
                }
            }
        }
        throw new IllegalStateException("unable to generate data with a self ISS");
    }

    @Test
    @SuppressWarnings("UnnecessaryLocalVariable")
    @DisplayName("Early Add Test")
    void earlyAddTest() {
        final Random random = getRandomPrintSeed();

        final PlatformContext platformContext =
                TestPlatformContextBuilder.create().build();

        final int roundsNonAncient = platformContext
                .getConfiguration()
                .getConfigData(ConsensusConfig.class)
                .roundsNonAncient();
        final AddressBook addressBook = new RandomAddressBookGenerator(random)
                .setSize(100)
                .setAverageWeight(100)
                .setWeightStandardDeviation(50)
                .build();
        final NodeId selfId = addressBook.getNodeId(0);

        final IssDetectorTestHelper manager =
                new IssDetectorTestHelper(platformContext, addressBook, DO_NOT_IGNORE_ROUNDS);

        // Start collecting data for rounds.
        for (long round = 0; round < roundsNonAncient; round++) {
            manager.roundCompleted(round);
        }

        // We are not yet collecting data for this round
        final long targetRound = roundsNonAncient;

        // Add data. Should be ignored since we are not processing data for this round yet.
        final RoundHashValidatorTests.HashGenerationData ignoredData =
                generateCatastrophicNodeHashes(random, addressBook, targetRound);
        for (final RoundHashValidatorTests.NodeHashInfo info : ignoredData.nodeList()) {
            if (info.nodeId() == selfId) {
                assertThrows(
                        IllegalStateException.class,
                        () -> manager.newStateHashed(mockState(targetRound, info.nodeStateHash())),
                        "should not be able to add hash for round not being tracked");
            }
            manager.handlePostconsensusSignatures(List.of(new ScopedSystemTransaction<>(
                    info.nodeId(),
                    new BasicSoftwareVersion(1),
                    new StateSignatureTransaction(targetRound, mock(Signature.class), info.nodeStateHash()))));
        }

        assertEquals(0, manager.getIssList().size(), "all data should have been ignored");

        // Move forward to the next round. Data should no longer be ignored.
        // Use a different data set so we can know if old data was fully ignored.
        final RoundHashValidatorTests.HashGenerationData data =
                generateDataWithSelfIss(random, addressBook, selfId, targetRound);
        manager.roundCompleted(targetRound);
        for (final RoundHashValidatorTests.NodeHashInfo info : data.nodeList()) {
            if (info.nodeId() == selfId) {
                manager.newStateHashed(mockState(targetRound, info.nodeStateHash()));
            }
            manager.handlePostconsensusSignatures(List.of(new ScopedSystemTransaction<>(
                    info.nodeId(),
                    new BasicSoftwareVersion(1),
                    new StateSignatureTransaction(targetRound, mock(Signature.class), info.nodeStateHash()))));
        }

        assertEquals(1, manager.getIssList().size(), "data should not have been ignored");
    }

    @Test
    @DisplayName("Late Add Test")
    void lateAddTest() {
        final Random random = getRandomPrintSeed();

        final PlatformContext platformContext =
                TestPlatformContextBuilder.create().build();

        final int roundsNonAncient = platformContext
                .getConfiguration()
                .getConfigData(ConsensusConfig.class)
                .roundsNonAncient();
        final AddressBook addressBook = new RandomAddressBookGenerator(random)
                .setSize(100)
                .setAverageWeight(100)
                .setWeightStandardDeviation(50)
                .build();
        final NodeId selfId = addressBook.getNodeId(0);

        final IssDetectorTestHelper manager =
                new IssDetectorTestHelper(platformContext, addressBook, DO_NOT_IGNORE_ROUNDS);

        // Start collecting data for rounds.
        // After this method, round 0 will be too old and will not be tracked.
        for (long round = 0; round <= roundsNonAncient; round++) {
            manager.roundCompleted(round);
        }

        final long targetRound = 0;

        // Add data. Should be ignored since we are not processing data for this round anymore.
        final RoundHashValidatorTests.HashGenerationData ignoredData =
                generateCatastrophicNodeHashes(random, addressBook, targetRound);
        for (final RoundHashValidatorTests.NodeHashInfo info : ignoredData.nodeList()) {
            if (info.nodeId() == selfId) {
                assertThrows(
                        IllegalStateException.class,
                        () -> manager.newStateHashed(mockState(targetRound, info.nodeStateHash())),
                        "should not be able to add hash for round not being tracked");
            }
            manager.handlePostconsensusSignatures(List.of(new ScopedSystemTransaction<>(
                    info.nodeId(),
                    new BasicSoftwareVersion(1),
                    new StateSignatureTransaction(targetRound, mock(Signature.class), info.nodeStateHash()))));
        }

        assertEquals(0, manager.getIssCount(), "all data should have been ignored");
    }

    @Test
    @DisplayName("Shift Before Complete Test")
    void shiftBeforeCompleteTest() {
        final Random random = getRandomPrintSeed();

        final PlatformContext platformContext =
                TestPlatformContextBuilder.create().build();

        final int roundsNonAncient = platformContext
                .getConfiguration()
                .getConfigData(ConsensusConfig.class)
                .roundsNonAncient();
        final AddressBook addressBook = new RandomAddressBookGenerator(random)
                .setSize(100)
                .setAverageWeight(100)
                .setWeightStandardDeviation(50)
                .build();
        final NodeId selfId = addressBook.getNodeId(0);

        final IssDetectorTestHelper manager =
                new IssDetectorTestHelper(platformContext, addressBook, DO_NOT_IGNORE_ROUNDS);

        // Start collecting data for rounds.
        for (long round = 0; round < roundsNonAncient; round++) {
            manager.roundCompleted(round);
        }

        final long targetRound = 0;

        // Add data, but not enough to be certain of an ISS.
        final RoundHashValidatorTests.HashGenerationData data =
                generateCatastrophicNodeHashes(random, addressBook, targetRound);

        for (final RoundHashValidatorTests.NodeHashInfo info : data.nodeList()) {
            if (info.nodeId() == selfId) {
                manager.newStateHashed(mockState(0L, info.nodeStateHash()));
            }
        }

        long submittedWeight = 0;
        for (final RoundHashValidatorTests.NodeHashInfo info : data.nodeList()) {
            final long weight = addressBook.getAddress(info.nodeId()).getWeight();
            if (MAJORITY.isSatisfiedBy(submittedWeight + weight, addressBook.getTotalWeight())) {
                // If we add less than a majority then we won't be able to detect the ISS no matter what
                break;
            }
            submittedWeight += weight;

            manager.handlePostconsensusSignatures(List.of(new ScopedSystemTransaction<>(
                    info.nodeId(),
                    new BasicSoftwareVersion(1),
                    new StateSignatureTransaction(targetRound, mock(Signature.class), info.nodeStateHash()))));
        }

        // Shift the window even though we have not added enough data for a decision
        manager.roundCompleted(roundsNonAncient);

        System.out.println(manager.getIssList());

        assertEquals(0, manager.getIssCount(), "there wasn't enough data submitted to observe the ISS");
    }

    /**
     * Generate data in an order that will cause a catastrophic ISS after the timeout, assuming the bare minimum to meet
     * &ge;2/3 has been met.
     */
    @SuppressWarnings("SameParameterValue")
    private static List<RoundHashValidatorTests.NodeHashInfo> generateCatastrophicTimeoutIss(
            final Random random, final AddressBook addressBook, final long targetRound) {

        final List<RoundHashValidatorTests.NodeHashInfo> data = new LinkedList<>();

        // Almost add enough hashes to create a consensus hash, but not quite enough.
        // Put these at the beginning. Since we will need just a little extra weight to
        // cross the 1/3 threshold, the detection algorithm will not make a decision
        // once it reaches a >2/3 threshold

        final Hash almostConsensusHash = randomHash(random);
        long almostConsensusWeight = 0;
        for (final Address address : addressBook) {
            if (MAJORITY.isSatisfiedBy(almostConsensusWeight + address.getWeight(), addressBook.getTotalWeight())) {
                data.add(new RoundHashValidatorTests.NodeHashInfo(address.getNodeId(), randomHash(), targetRound));
            } else {
                almostConsensusWeight += address.getWeight();
                data.add(new RoundHashValidatorTests.NodeHashInfo(
                        address.getNodeId(), almostConsensusHash, targetRound));
            }
        }

        return data;
    }

    @Test
    @DisplayName("Catastrophic Shift Before Complete Test")
    void catastrophicShiftBeforeCompleteTest() {
        final Random random = getRandomPrintSeed();

        final PlatformContext platformContext =
                TestPlatformContextBuilder.create().build();

        final int roundsNonAncient = platformContext
                .getConfiguration()
                .getConfigData(ConsensusConfig.class)
                .roundsNonAncient();
        final AddressBook addressBook = new RandomAddressBookGenerator(random)
                .setSize(100)
                .setAverageWeight(100)
                .setWeightStandardDeviation(50)
                .build();
        final NodeId selfId = addressBook.getNodeId(0);

        final IssDetectorTestHelper manager =
                new IssDetectorTestHelper(platformContext, addressBook, DO_NOT_IGNORE_ROUNDS);

        // Start collecting data for rounds.
        for (long round = 0; round < roundsNonAncient; round++) {
            manager.roundCompleted(round);
        }

        final long targetRound = 0;

        // Add data, but not enough to be certain of an ISS.
        final List<RoundHashValidatorTests.NodeHashInfo> data =
                generateCatastrophicTimeoutIss(random, addressBook, targetRound);

        for (final RoundHashValidatorTests.NodeHashInfo info : data) {
            if (info.nodeId() == selfId) {
                manager.newStateHashed(mockState(0L, info.nodeStateHash()));
            }
        }

        long submittedWeight = 0;
        for (final RoundHashValidatorTests.NodeHashInfo info : data) {
            final long weight = addressBook.getAddress(info.nodeId()).getWeight();

            manager.handlePostconsensusSignatures(List.of(new ScopedSystemTransaction<>(
                    info.nodeId(),
                    new BasicSoftwareVersion(1),
                    new StateSignatureTransaction(targetRound, mock(Signature.class), info.nodeStateHash()))));

            // Stop once we have added >2/3. We should not have decided yet, but will
            // have gathered enough to declare a catastrophic ISS
            submittedWeight += weight;
            if (SUPER_MAJORITY.isSatisfiedBy(submittedWeight, addressBook.getTotalWeight())) {
                break;
            }
        }

        // Shift the window even though we have not added enough data for a decision.
        // But we will have added enough to lead to a catastrophic ISS when the timeout is triggered.
        manager.roundCompleted(roundsNonAncient);

        assertEquals(1, manager.getIssCount(), "shifting should have caused an ISS");
    }

    @Test
    @DisplayName("Big Shift Test")
    void bigShiftTest() {
        final Random random = getRandomPrintSeed();

        final PlatformContext platformContext =
                TestPlatformContextBuilder.create().build();

        final int roundsNonAncient = platformContext
                .getConfiguration()
                .getConfigData(ConsensusConfig.class)
                .roundsNonAncient();
        final AddressBook addressBook = new RandomAddressBookGenerator(random)
                .setSize(100)
                .setAverageWeight(100)
                .setWeightStandardDeviation(50)
                .build();
        final NodeId selfId = addressBook.getNodeId(0);

        final IssDetectorTestHelper manager =
                new IssDetectorTestHelper(platformContext, addressBook, DO_NOT_IGNORE_ROUNDS);

        // Start collecting data for rounds.
        for (long round = 0; round < roundsNonAncient; round++) {
            manager.roundCompleted(round);
        }

        final long targetRound = 0;

        // Add data, but not enough to be certain of an ISS.
        final List<RoundHashValidatorTests.NodeHashInfo> data =
                generateCatastrophicTimeoutIss(random, addressBook, targetRound);

        for (final RoundHashValidatorTests.NodeHashInfo info : data) {
            if (info.nodeId() == selfId) {
                manager.newStateHashed(mockState(0L, info.nodeStateHash()));
            }
        }

        long submittedWeight = 0;
        for (final RoundHashValidatorTests.NodeHashInfo info : data) {
            final long weight = addressBook.getAddress(info.nodeId()).getWeight();

            manager.handlePostconsensusSignatures(List.of(new ScopedSystemTransaction<>(
                    info.nodeId(),
                    new BasicSoftwareVersion(1),
                    new StateSignatureTransaction(targetRound, mock(Signature.class), info.nodeStateHash()))));

            // Stop once we have added >2/3. We should not have decided yet, but will
            // have gathered enough to declare a catastrophic ISS
            submittedWeight += weight;
            if (SUPER_MAJORITY.isSatisfiedBy(submittedWeight, addressBook.getTotalWeight())) {
                break;
            }
        }

        // Shifting the window a great distance should not trigger the ISS.
        manager.overridingState(mockState(roundsNonAncient + 100L, randomHash(random)));

        assertEquals(0, manager.getIssCount(), "there wasn't enough data submitted to observe the ISS");
    }

    @Test
    @DisplayName("Ignored Round Test")
    void ignoredRoundTest() {
        final Random random = getRandomPrintSeed();

        final AddressBook addressBook = new RandomAddressBookGenerator(random)
                .setSize(100)
                .setAverageWeight(100)
                .setWeightStandardDeviation(50)
                .build();

        final PlatformContext platformContext =
                TestPlatformContextBuilder.create().build();

        final IssDetectorTestHelper manager = new IssDetectorTestHelper(platformContext, addressBook, 1);

        final int rounds = 1_000;
        for (long round = 1; round <= rounds; round++) {
            final Hash roundHash = randomHash(random);

            if (round == 1) {
                manager.overridingState(mockState(round, roundHash));
            } else {
                manager.roundCompleted(round);
                manager.newStateHashed(mockState(round, roundHash));
            }

            for (final Address address : addressBook) {
                if (round == 1) {
                    // Intentionally send bad hashes in the first round. We are configured to ignore this round.
                    manager.handlePostconsensusSignatures(List.of(new ScopedSystemTransaction<>(
                            address.getNodeId(),
                            new BasicSoftwareVersion(1),
                            new StateSignatureTransaction(round, mock(Signature.class), randomHash(random)))));
                } else {
                    manager.handlePostconsensusSignatures(List.of(new ScopedSystemTransaction<>(
                            address.getNodeId(),
                            new BasicSoftwareVersion(1),
                            new StateSignatureTransaction(round, mock(Signature.class), roundHash))));
                }
            }
        }
        assertEquals(0, manager.getIssCount(), "ISS should have been ignored");
    }

    private static ReservedSignedState mockState(final long round, final Hash hash) {
        final ReservedSignedState rs = mock(ReservedSignedState.class);
        final SignedState ss = mock(SignedState.class);
        final State s = mock(State.class);
        Mockito.when(rs.get()).thenReturn(ss);
        Mockito.when(ss.getState()).thenReturn(s);
        Mockito.when(ss.getRound()).thenReturn(round);
        Mockito.when(s.getHash()).thenReturn(hash);
        return rs;
    }
}
