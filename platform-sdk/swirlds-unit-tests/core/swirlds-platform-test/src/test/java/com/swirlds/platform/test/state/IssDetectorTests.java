/*
 * Copyright (C) 2024-2025 Hedera Hashgraph, LLC
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

import static com.swirlds.common.test.fixtures.RandomUtils.randomHash;
import static com.swirlds.common.utility.Threshold.MAJORITY;
import static com.swirlds.common.utility.Threshold.SUPER_MAJORITY;
import static com.swirlds.platform.state.iss.IssDetector.DO_NOT_IGNORE_ROUNDS;
import static com.swirlds.platform.test.state.RoundHashValidatorTests.generateCatastrophicNodeHashes;
import static com.swirlds.platform.test.state.RoundHashValidatorTests.generateRegularNodeHashes;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.node.base.SignatureMap;
import com.hedera.hapi.node.state.roster.Roster;
import com.hedera.hapi.node.state.roster.RosterEntry;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.hapi.platform.event.StateSignatureTransaction;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.platform.NodeId;
import com.swirlds.common.test.fixtures.Randotron;
import com.swirlds.platform.components.transaction.system.SystemTransactionExtractionUtils;
import com.swirlds.platform.consensus.ConsensusConfig;
import com.swirlds.platform.internal.ConsensusRound;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.roster.RosterUtils;
import com.swirlds.platform.state.PlatformMerkleStateRoot;
import com.swirlds.platform.state.iss.DefaultIssDetector;
import com.swirlds.platform.state.iss.IssDetector;
import com.swirlds.platform.state.iss.internal.HashValidityStatus;
import com.swirlds.platform.state.signed.ReservedSignedState;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.system.state.notifications.IssNotification;
import com.swirlds.platform.system.state.notifications.IssNotification.IssType;
import com.swirlds.platform.test.PlatformTest;
import com.swirlds.platform.test.fixtures.addressbook.RandomRosterBuilder;
import com.swirlds.platform.test.fixtures.event.EventImplTestUtils;
import com.swirlds.platform.test.fixtures.event.TestingEventBuilder;
import com.swirlds.platform.wiring.components.StateAndRound;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("IssDetector Tests")
class IssDetectorTests extends PlatformTest {

    /**
     * Generates a list of events, with each event containing a signature transaction from a node for the given round.
     *
     * @param random             a source of randomness
     * @param roundNumber        the round that signature transactions will be for
     * @param hashGenerationData the data to use to generate the signature transactions
     * @return a list of events, each containing a signature transaction from a node for the given round
     */
    private static List<EventImpl> generateEventsContainingSignatures(
            @NonNull final Randotron random,
            final long roundNumber,
            @NonNull final RoundHashValidatorTests.HashGenerationData hashGenerationData) {

        return hashGenerationData.nodeList().stream()
                .map(nodeHashInfo -> {
                    final StateSignatureTransaction signatureTransaction = StateSignatureTransaction.newBuilder()
                            .round(roundNumber)
                            .signature(Bytes.EMPTY)
                            .hash(nodeHashInfo.nodeStateHash().getBytes())
                            .build();

                    final TestingEventBuilder event = new TestingEventBuilder(random)
                            .setCreatorId(nodeHashInfo.nodeId())
                            .setTransactionBytes(List.of(encodeSystemTransaction(signatureTransaction)))
                            .setSoftwareVersion(SemanticVersion.DEFAULT);

                    return EventImplTestUtils.createEventImpl(event, null, null);
                })
                .toList();
    }

    private static Bytes encodeSystemTransaction(@NonNull StateSignatureTransaction stateSignatureTransaction) {
        final var transactionBody = TransactionBody.newBuilder().stateSignatureTransaction(stateSignatureTransaction);

        final var transaction = com.hedera.hapi.node.base.Transaction.newBuilder()
                .bodyBytes(TransactionBody.PROTOBUF.toBytes(transactionBody.build()))
                .sigMap(SignatureMap.DEFAULT)
                .build();

        return com.hedera.hapi.node.base.Transaction.PROTOBUF.toBytes(transaction);
    }

    /**
     * Generates a list of events, with each event containing a signature transaction from a node for the given round.
     * <p>
     * One event will be created for each node in the address book, and all signatures will be made on a single
     * consistent hash.
     *
     * @param random      a source of randomness
     * @param roster      the roster to use to generate the signature transactions
     * @param roundNumber the round that signature transactions will be for
     * @param roundHash   the hash that all signature transactions will be made on
     * @return a list of events, each containing a signature transaction from a node for the given round
     */
    private static List<EventImpl> generateEventsWithConsistentSignatures(
            @NonNull final Randotron random,
            @NonNull final Roster roster,
            final long roundNumber,
            @NonNull final Hash roundHash) {
        final List<RoundHashValidatorTests.NodeHashInfo> nodeHashInfos = new ArrayList<>();

        roster.rosterEntries()
                .forEach(node -> nodeHashInfos.add(
                        new RoundHashValidatorTests.NodeHashInfo(NodeId.of(node.nodeId()), roundHash, roundNumber)));

        // create signature transactions for this round
        return generateEventsContainingSignatures(
                random, roundNumber, new RoundHashValidatorTests.HashGenerationData(nodeHashInfos, roundHash));
    }

    /**
     * Randomly selects ~50% of a collection of candidate events to include in a round, and removes them from the
     * candidate events collection.
     *
     * @param random          a source of randomness
     * @param candidateEvents the collection of candidate events to select from
     * @return a list of events to include in a round
     */
    private static List<EventImpl> selectRandomEvents(
            @NonNull final Random random, @NonNull final Collection<EventImpl> candidateEvents) {

        final List<EventImpl> eventsToInclude = new ArrayList<>();
        candidateEvents.forEach(event -> {
            if (random.nextBoolean()) {
                eventsToInclude.add(event);
            }
        });
        candidateEvents.removeAll(eventsToInclude);

        return eventsToInclude;
    }

    /**
     * Creates a mock consensus round, which includes a given list of events.
     *
     * @param roundNumber     the round number
     * @param eventsToInclude the events to include in the round
     * @return a mock consensus round
     */
    private static ConsensusRound createRoundWithSignatureEvents(
            final long roundNumber, @NonNull final List<EventImpl> eventsToInclude) {
        final ConsensusRound consensusRound = mock(ConsensusRound.class);
        when(consensusRound.getConsensusEvents())
                .thenReturn(
                        eventsToInclude.stream().map(EventImpl::getBaseEvent).toList());
        when(consensusRound.getRoundNum()).thenReturn(roundNumber);

        return consensusRound;
    }

    @Test
    @DisplayName("No ISSes Test")
    void noIss() {
        final Randotron random = Randotron.create();
        final Roster roster = RandomRosterBuilder.create(random)
                .withSize(100)
                .withAverageWeight(100)
                .withWeightStandardDeviation(50)
                .build();

        final PlatformContext platformContext = createDefaultPlatformContext();

        final IssDetector issDetector =
                new DefaultIssDetector(platformContext, roster, SemanticVersion.DEFAULT, false, DO_NOT_IGNORE_ROUNDS);
        final IssDetectorTestHelper issDetectorTestHelper = new IssDetectorTestHelper(issDetector);

        // signature events are generated for each round when that round is handled, and then are included randomly
        // in subsequent rounds
        final List<EventImpl> signatureEvents = new ArrayList<>();

        long currentRound = 0;

        issDetectorTestHelper.overridingState(mockState(currentRound, randomHash()));

        for (currentRound++; currentRound <= 1_000; currentRound++) {
            final Hash roundHash = randomHash(random);

            // create signature transactions for this round
            signatureEvents.addAll(generateEventsWithConsistentSignatures(random, roster, currentRound, roundHash));

            // randomly select half of unsubmitted signature events to include in this round
            final List<EventImpl> eventsToInclude = selectRandomEvents(random, signatureEvents);
            final ConsensusRound consensusRound = createRoundWithSignatureEvents(currentRound, eventsToInclude);

            final var systemTransactions =
                    SystemTransactionExtractionUtils.extractFromRound(consensusRound, StateSignatureTransaction.class);
            issDetectorTestHelper.handleStateAndRound(
                    new StateAndRound(mockState(currentRound, roundHash), consensusRound, systemTransactions));
        }

        // Add all remaining unsubmitted signature events
        final ConsensusRound consensusRound = createRoundWithSignatureEvents(currentRound, signatureEvents);
        final var systemTransactions =
                SystemTransactionExtractionUtils.extractFromRound(consensusRound, StateSignatureTransaction.class);
        issDetectorTestHelper.handleStateAndRound(
                new StateAndRound(mockState(currentRound, randomHash(random)), consensusRound, systemTransactions));

        assertEquals(0, issDetectorTestHelper.getSelfIssCount(), "there should be no ISS notifications");
        assertEquals(
                0,
                issDetectorTestHelper.getCatastrophicIssCount(),
                "there should be no catastrophic ISS notifications");
        assertEquals(0, issDetectorTestHelper.getIssNotificationList().size(), "there should be no ISS notifications");

        // verify marker files
        assertMarkerFile(IssType.CATASTROPHIC_ISS.toString(), false);
        assertMarkerFile(IssType.SELF_ISS.toString(), false);
        assertMarkerFile(IssType.OTHER_ISS.toString(), false);
    }

    /**
     * This test goes through a series of rounds, some of which experience ISSes. The test verifies that the expected
     * number of ISSes are registered by the ISS detector.
     */
    @Test
    @DisplayName("Mixed Order Test")
    void mixedOrderTest() {
        final Randotron random = Randotron.create();

        final Roster roster = RandomRosterBuilder.create(random)
                .withSize(Math.max(10, random.nextInt(1000)))
                .withAverageWeight(100)
                .withWeightStandardDeviation(50)
                .build();

        final PlatformContext platformContext = createDefaultPlatformContext();

        final NodeId selfId = NodeId.of(roster.rosterEntries().getFirst().nodeId());
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
                data = generateRegularNodeHashes(random, roster, round);

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
                data = generateCatastrophicNodeHashes(random, roster, round);
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

        final IssDetector issDetector =
                new DefaultIssDetector(platformContext, roster, SemanticVersion.DEFAULT, false, DO_NOT_IGNORE_ROUNDS);
        final IssDetectorTestHelper issDetectorTestHelper = new IssDetectorTestHelper(issDetector);

        long currentRound = 0;

        issDetectorTestHelper.overridingState(mockState(currentRound, selfHashes.getFirst()));

        // signature events are generated for each round when that round is handled, and then are included randomly
        // in subsequent rounds
        final List<EventImpl> signatureEvents =
                new ArrayList<>(generateEventsContainingSignatures(random, 0, roundData.getFirst()));

        for (currentRound++; currentRound < roundsNonAncient; currentRound++) {
            // create signature transactions for this round
            signatureEvents.addAll(
                    generateEventsContainingSignatures(random, currentRound, roundData.get((int) currentRound)));

            // randomly select half of unsubmitted signature events to include in this round
            final List<EventImpl> eventsToInclude = selectRandomEvents(random, signatureEvents);

            final ConsensusRound consensusRound = createRoundWithSignatureEvents(currentRound, eventsToInclude);
            final var systemTransactions =
                    SystemTransactionExtractionUtils.extractFromRound(consensusRound, StateSignatureTransaction.class);
            issDetectorTestHelper.handleStateAndRound(new StateAndRound(
                    mockState(currentRound, selfHashes.get((int) currentRound)), consensusRound, systemTransactions));
        }

        // Add all remaining signature events
        final ConsensusRound consensusRound = createRoundWithSignatureEvents(roundsNonAncient, signatureEvents);
        final var systemTransactions =
                SystemTransactionExtractionUtils.extractFromRound(consensusRound, StateSignatureTransaction.class);
        issDetectorTestHelper.handleStateAndRound(
                new StateAndRound(mockState(roundsNonAncient, randomHash(random)), consensusRound, systemTransactions));

        assertEquals(
                expectedSelfIssCount,
                issDetectorTestHelper.getSelfIssCount(),
                "unexpected number of self ISS notifications");
        assertEquals(
                expectedCatastrophicIssCount,
                issDetectorTestHelper.getCatastrophicIssCount(),
                "unexpected number of catastrophic ISS notifications");

        final Collection<Long> observedRounds = new HashSet<>();
        issDetectorTestHelper.getIssNotificationList().forEach(notification -> {
            assertTrue(
                    observedRounds.add(notification.getRound()), "rounds should trigger a notification at most once");

            final IssNotification.IssType expectedType =
                    switch (expectedRoundStatus.get((int) notification.getRound())) {
                        case SELF_ISS -> IssNotification.IssType.SELF_ISS;
                        case CATASTROPHIC_ISS -> IssNotification.IssType.CATASTROPHIC_ISS;
                            // if there was an other-ISS, then the round should still be valid
                        case VALID -> IssNotification.IssType.OTHER_ISS;
                        default -> throw new IllegalStateException(
                                "Unexpected value: " + expectedRoundStatus.get((int) notification.getRound()));
                    };
            assertEquals(
                    expectedType,
                    notification.getIssType(),
                    "Expected status for round %d to be %s but was %s"
                            .formatted(
                                    notification.getRound(),
                                    expectedRoundStatus.get((int) notification.getRound()),
                                    notification.getIssType()));
        });
        issDetectorTestHelper
                .getIssNotificationList()
                .forEach(notification ->
                        assertMarkerFile(notification.getIssType().toString(), true));
    }

    /**
     * Handles additional rounds after an ISS occurred, but before all signatures have been submitted. Validates that
     * the ISS is detected after enough signatures are submitted, and not before.
     */
    @Test
    @DisplayName("Decide hash for catastrophic ISS")
    void decideForCatastrophicIss() {
        final Randotron random = Randotron.create();
        final PlatformContext platformContext = createDefaultPlatformContext();

        final Roster roster = RandomRosterBuilder.create(random)
                .withSize(100)
                .withAverageWeight(100)
                .withWeightStandardDeviation(50)
                .build();
        final NodeId selfId = NodeId.of(roster.rosterEntries().getFirst().nodeId());

        final IssDetector issDetector =
                new DefaultIssDetector(platformContext, roster, SemanticVersion.DEFAULT, false, DO_NOT_IGNORE_ROUNDS);
        final IssDetectorTestHelper issDetectorTestHelper = new IssDetectorTestHelper(issDetector);

        long currentRound = 0;

        // start with an initial state
        issDetectorTestHelper.overridingState(mockState(currentRound, randomHash()));
        currentRound++;

        // the round after the initial state will have a catastrophic iss
        final RoundHashValidatorTests.HashGenerationData catastrophicHashData =
                generateCatastrophicNodeHashes(random, roster, currentRound);
        final Hash selfHashForCatastrophicRound = catastrophicHashData.nodeList().stream()
                .filter(info -> info.nodeId() == selfId)
                .findFirst()
                .map(RoundHashValidatorTests.NodeHashInfo::nodeStateHash)
                .orElseThrow();
        final List<EventImpl> signaturesOnCatastrophicRound =
                generateEventsContainingSignatures(random, currentRound, catastrophicHashData);

        // handle the catastrophic round, but don't submit any signatures yet, so it won't be detected
        final var catastrophicRound = createRoundWithSignatureEvents(currentRound, List.of());
        final var systemTransactionsForCatastrophicRound =
                SystemTransactionExtractionUtils.extractFromRound(catastrophicRound, StateSignatureTransaction.class);
        issDetectorTestHelper.handleStateAndRound(new StateAndRound(
                mockState(currentRound, selfHashForCatastrophicRound),
                catastrophicRound,
                systemTransactionsForCatastrophicRound));

        // handle some more rounds on top of the catastrophic round
        for (currentRound++; currentRound < 10; currentRound++) {
            // don't include any signatures
            final var anotherRound = createRoundWithSignatureEvents(currentRound, List.of());
            final var systemTransactionsForRoundWithoutSignatures =
                    SystemTransactionExtractionUtils.extractFromRound(anotherRound, StateSignatureTransaction.class);
            issDetectorTestHelper.handleStateAndRound(new StateAndRound(
                    mockState(currentRound, randomHash()), anotherRound, systemTransactionsForRoundWithoutSignatures));
        }

        final Map<Long, RosterEntry> nodesById = RosterUtils.toMap(roster);

        // submit signatures on the ISS round that represent a minority of the weight
        long submittedWeight = 0;
        final List<EventImpl> signaturesToSubmit = new ArrayList<>();
        for (final EventImpl signatureEvent : signaturesOnCatastrophicRound) {
            final long weight =
                    nodesById.get(signatureEvent.getCreatorId().id()).weight();
            if (MAJORITY.isSatisfiedBy(submittedWeight + weight, RosterUtils.computeTotalWeight(roster))) {
                // If we add less than a majority then we won't be able to detect the ISS no matter what
                break;
            }
            submittedWeight += weight;
            signaturesToSubmit.add(signatureEvent);
        }

        final var roundWithMajority = createRoundWithSignatureEvents(currentRound, signaturesToSubmit);
        final var systemTransactionsForRoundWithMajority =
                SystemTransactionExtractionUtils.extractFromRound(roundWithMajority, StateSignatureTransaction.class);
        issDetectorTestHelper.handleStateAndRound(new StateAndRound(
                mockState(currentRound, randomHash()), roundWithMajority, systemTransactionsForRoundWithMajority));
        assertEquals(
                0,
                issDetectorTestHelper.getIssNotificationList().size(),
                "there shouldn't have been enough data submitted to observe the ISS");

        currentRound++;

        // submit the remaining signatures in the next round
        final var remainingRound = createRoundWithSignatureEvents(currentRound, signaturesOnCatastrophicRound);
        final var remainingSystemTransactions =
                SystemTransactionExtractionUtils.extractFromRound(remainingRound, StateSignatureTransaction.class);

        issDetectorTestHelper.handleStateAndRound(
                new StateAndRound(mockState(currentRound, randomHash()), remainingRound, remainingSystemTransactions));

        assertEquals(
                1, issDetectorTestHelper.getCatastrophicIssCount(), "the catastrophic round should have caused an ISS");

        // verify marker files
        assertMarkerFile(IssType.CATASTROPHIC_ISS.toString(), true);
        assertMarkerFile(IssType.SELF_ISS.toString(), false);
        assertMarkerFile(IssType.OTHER_ISS.toString(), false);
    }

    /**
     * Generate data in an order that will cause a catastrophic ISS after the timeout, but without a supermajority of
     * signatures being on an incorrect hash.
     */
    private static List<RoundHashValidatorTests.NodeHashInfo> generateCatastrophicTimeoutIss(
            final Random random, final Roster roster, final long targetRound) {

        final List<RoundHashValidatorTests.NodeHashInfo> data = new LinkedList<>();

        // Almost add enough hashes to create a consensus hash, but not quite enough.
        // Put these at the beginning. Since we will need just a little extra weight to
        // cross the 1/3 threshold, the detection algorithm will not make a decision
        // once it reaches a >2/3 threshold

        final Hash almostConsensusHash = randomHash(random);
        long almostConsensusWeight = 0;
        for (final RosterEntry node : roster.rosterEntries()) {
            final NodeId nodeId = NodeId.of(node.nodeId());
            if (MAJORITY.isSatisfiedBy(almostConsensusWeight + node.weight(), RosterUtils.computeTotalWeight(roster))) {
                data.add(new RoundHashValidatorTests.NodeHashInfo(nodeId, randomHash(), targetRound));
            } else {
                almostConsensusWeight += node.weight();
                data.add(new RoundHashValidatorTests.NodeHashInfo(nodeId, almostConsensusHash, targetRound));
            }
        }

        return data;
    }

    /**
     * Causes a catastrophic ISS, but shifts the window before deciding on a consensus hash. Even though we don't get
     * enough signatures to "decide", there will be enough signatures to declare a catastrophic ISS when shifting the
     * window past the ISS round.
     */
    @Test
    @DisplayName("Catastrophic Shift Before Complete Test")
    void catastrophicShiftBeforeCompleteTest() {
        final Randotron random = Randotron.create();
        final PlatformContext platformContext = createDefaultPlatformContext();

        final int roundsNonAncient = platformContext
                .getConfiguration()
                .getConfigData(ConsensusConfig.class)
                .roundsNonAncient();
        final Roster roster = RandomRosterBuilder.create(random)
                .withSize(100)
                .withAverageWeight(100)
                .withWeightStandardDeviation(50)
                .build();
        final NodeId selfId = NodeId.of(roster.rosterEntries().getFirst().nodeId());

        final IssDetector issDetector =
                new DefaultIssDetector(platformContext, roster, SemanticVersion.DEFAULT, false, DO_NOT_IGNORE_ROUNDS);
        final IssDetectorTestHelper issDetectorTestHelper = new IssDetectorTestHelper(issDetector);

        long currentRound = 0;

        final List<RoundHashValidatorTests.NodeHashInfo> catastrophicData =
                generateCatastrophicTimeoutIss(random, roster, currentRound);
        final Hash selfHashForCatastrophicRound = catastrophicData.stream()
                .filter(info -> info.nodeId() == selfId)
                .findFirst()
                .map(RoundHashValidatorTests.NodeHashInfo::nodeStateHash)
                .orElseThrow();
        final List<EventImpl> signaturesOnCatastrophicRound = generateEventsContainingSignatures(
                random, currentRound, new RoundHashValidatorTests.HashGenerationData(catastrophicData, null));

        final Map<Long, RosterEntry> nodesById = RosterUtils.toMap(roster);
        long submittedWeight = 0;
        final List<EventImpl> signaturesToSubmit = new ArrayList<>();
        for (final EventImpl signatureEvent : signaturesOnCatastrophicRound) {
            final long weight =
                    nodesById.get(signatureEvent.getCreatorId().id()).weight();

            signaturesToSubmit.add(signatureEvent);

            // Stop once we have added >2/3. We should not have decided yet, but will
            // have gathered enough to declare a catastrophic ISS
            submittedWeight += weight;
            if (SUPER_MAJORITY.isSatisfiedBy(submittedWeight, RosterUtils.computeTotalWeight(roster))) {
                break;
            }
        }

        // handle the catastrophic round, but it won't be decided yet, since there aren't enough signatures
        final var catastrophicRound = createRoundWithSignatureEvents(currentRound, signaturesToSubmit);
        final var systemTransactionsForCatastrophicRound =
                SystemTransactionExtractionUtils.extractFromRound(catastrophicRound, StateSignatureTransaction.class);

        issDetectorTestHelper.handleStateAndRound(new StateAndRound(
                mockState(currentRound, selfHashForCatastrophicRound),
                catastrophicRound,
                systemTransactionsForCatastrophicRound));

        // shift through until the catastrophic round is almost ready to be cleaned up
        for (currentRound++; currentRound < roundsNonAncient; currentRound++) {
            final var round = createRoundWithSignatureEvents(currentRound, List.of());
            final var systemTransactions =
                    SystemTransactionExtractionUtils.extractFromRound(round, StateSignatureTransaction.class);

            issDetectorTestHelper.handleStateAndRound(
                    new StateAndRound(mockState(currentRound, randomHash()), round, systemTransactions));
        }

        assertEquals(
                0,
                issDetectorTestHelper.getIssNotificationList().size(),
                "no ISS should be detected prior to shifting");

        // Shift the window. Even though we have not added enough data for a decision, we will have added enough to lead
        // to a catastrophic ISS when the timeout is triggered.
        final var remainingRound = createRoundWithSignatureEvents(currentRound, List.of());
        final var systemTransactionsForRemainingRound =
                SystemTransactionExtractionUtils.extractFromRound(remainingRound, StateSignatureTransaction.class);

        issDetectorTestHelper.handleStateAndRound(new StateAndRound(
                mockState(currentRound, randomHash()), remainingRound, systemTransactionsForRemainingRound));

        assertEquals(1, issDetectorTestHelper.getIssNotificationList().size(), "shifting should have caused an ISS");
        assertEquals(
                1, issDetectorTestHelper.getCatastrophicIssCount(), "shifting should have caused a catastrophic ISS");

        // verify marker files
        assertMarkerFile(IssType.CATASTROPHIC_ISS.toString(), true);
        assertMarkerFile(IssType.SELF_ISS.toString(), false);
        assertMarkerFile(IssType.OTHER_ISS.toString(), false);
    }

    /**
     * Causes a catastrophic ISS, but shifts the window by a large amount past the ISS round. This causes the
     * catastrophic ISS to not be registered.
     */
    @Test
    @DisplayName("Big Shift Test")
    void bigShiftTest() {
        final Randotron random = Randotron.create();

        final PlatformContext platformContext = createDefaultPlatformContext();

        final int roundsNonAncient = platformContext
                .getConfiguration()
                .getConfigData(ConsensusConfig.class)
                .roundsNonAncient();
        final Roster roster = RandomRosterBuilder.create(random)
                .withSize(100)
                .withAverageWeight(100)
                .withWeightStandardDeviation(50)
                .build();
        final NodeId selfId = NodeId.of(roster.rosterEntries().getFirst().nodeId());

        final IssDetector issDetector =
                new DefaultIssDetector(platformContext, roster, SemanticVersion.DEFAULT, false, DO_NOT_IGNORE_ROUNDS);
        final IssDetectorTestHelper issDetectorTestHelper = new IssDetectorTestHelper(issDetector);

        long currentRound = 0;

        // start with an initial state
        issDetectorTestHelper.overridingState(mockState(currentRound, randomHash()));
        currentRound++;

        final List<RoundHashValidatorTests.NodeHashInfo> catastrophicData =
                generateCatastrophicTimeoutIss(random, roster, currentRound);
        final Hash selfHashForCatastrophicRound = catastrophicData.stream()
                .filter(info -> info.nodeId() == selfId)
                .findFirst()
                .map(RoundHashValidatorTests.NodeHashInfo::nodeStateHash)
                .orElseThrow();
        final List<EventImpl> signaturesOnCatastrophicRound = generateEventsContainingSignatures(
                random, currentRound, new RoundHashValidatorTests.HashGenerationData(catastrophicData, null));

        // handle the catastrophic round, but don't submit any signatures yet, so it won't be detected
        final var catastrophicRound = createRoundWithSignatureEvents(currentRound, List.of());
        final var systemTransactionsForCatastrophicRound =
                SystemTransactionExtractionUtils.extractFromRound(catastrophicRound, StateSignatureTransaction.class);

        issDetectorTestHelper.handleStateAndRound(new StateAndRound(
                mockState(currentRound, selfHashForCatastrophicRound),
                catastrophicRound,
                systemTransactionsForCatastrophicRound));

        final Map<Long, RosterEntry> nodesById = RosterUtils.toMap(roster);
        long submittedWeight = 0;
        final List<EventImpl> signaturesToSubmit = new ArrayList<>();
        for (final EventImpl signatureEvent : signaturesOnCatastrophicRound) {
            final long weight =
                    nodesById.get(signatureEvent.getCreatorId().id()).weight();

            // Stop once we have added >2/3. We should not have decided yet, but will have gathered enough to declare a
            // catastrophic ISS
            submittedWeight += weight;
            signaturesToSubmit.add(signatureEvent);
            if (SUPER_MAJORITY.isSatisfiedBy(submittedWeight + weight, RosterUtils.computeTotalWeight(roster))) {
                break;
            }
        }

        currentRound++;
        // submit the supermajority of signatures
        final var roundWithSupermajority = createRoundWithSignatureEvents(currentRound, signaturesToSubmit);
        final var systemTransactionsForRoundWithSupermajorityOfSignatures =
                SystemTransactionExtractionUtils.extractFromRound(
                        roundWithSupermajority, StateSignatureTransaction.class);

        issDetectorTestHelper.handleStateAndRound(new StateAndRound(
                mockState(currentRound, randomHash()),
                roundWithSupermajority,
                systemTransactionsForRoundWithSupermajorityOfSignatures));

        // Shifting the window a great distance should not trigger the ISS.
        issDetectorTestHelper.overridingState(mockState(roundsNonAncient + 100L, randomHash(random)));

        assertEquals(0, issDetectorTestHelper.getSelfIssCount(), "there should be no ISS notifications");

        // verify marker files
        assertMarkerFile(IssType.CATASTROPHIC_ISS.toString(), false);
        assertMarkerFile(IssType.SELF_ISS.toString(), false);
        assertMarkerFile(IssType.OTHER_ISS.toString(), false);
    }

    /**
     * Causes a catastrophic ISS, but specifies that round to be ignored. This should cause the ISS to not be detected.
     */
    @Test
    @DisplayName("Ignored Round Test")
    void ignoredRoundTest() {
        final Randotron random = Randotron.create();

        final Roster roster = RandomRosterBuilder.create(random)
                .withSize(100)
                .withAverageWeight(100)
                .withWeightStandardDeviation(50)
                .build();

        final PlatformContext platformContext = createDefaultPlatformContext();
        final int roundsNonAncient = platformContext
                .getConfiguration()
                .getConfigData(ConsensusConfig.class)
                .roundsNonAncient();

        final IssDetector issDetector =
                new DefaultIssDetector(platformContext, roster, SemanticVersion.DEFAULT, false, 1);
        final IssDetectorTestHelper issDetectorTestHelper = new IssDetectorTestHelper(issDetector);

        long currentRound = 0;

        issDetectorTestHelper.overridingState(mockState(currentRound, randomHash()));
        currentRound++;

        final List<RoundHashValidatorTests.NodeHashInfo> catastrophicData =
                generateCatastrophicTimeoutIss(random, roster, currentRound);
        final List<EventImpl> signaturesOnCatastrophicRound = generateEventsContainingSignatures(
                random, currentRound, new RoundHashValidatorTests.HashGenerationData(catastrophicData, null));

        // handle the round and all signatures.
        // The round has a catastrophic ISS, but should be ignored
        final var catastrophicRound = createRoundWithSignatureEvents(currentRound, signaturesOnCatastrophicRound);
        final var systemTransactionsForCatastrophicRound =
                SystemTransactionExtractionUtils.extractFromRound(catastrophicRound, StateSignatureTransaction.class);

        issDetectorTestHelper.handleStateAndRound(new StateAndRound(
                mockState(currentRound, randomHash()), catastrophicRound, systemTransactionsForCatastrophicRound));

        // shift through some rounds, to make sure nothing unexpected happens
        for (currentRound++; currentRound <= roundsNonAncient; currentRound++) {
            final var anotherRound = createRoundWithSignatureEvents(currentRound, List.of());
            final var systemTransactionsForAnotherRound =
                    SystemTransactionExtractionUtils.extractFromRound(anotherRound, StateSignatureTransaction.class);

            issDetectorTestHelper.handleStateAndRound(new StateAndRound(
                    mockState(currentRound, randomHash()), anotherRound, systemTransactionsForAnotherRound));
        }

        assertEquals(0, issDetectorTestHelper.getIssNotificationList().size(), "ISS should have been ignored");

        // verify marker files
        assertMarkerFile(IssType.CATASTROPHIC_ISS.toString(), false);
        assertMarkerFile(IssType.SELF_ISS.toString(), false);
        assertMarkerFile(IssType.OTHER_ISS.toString(), false);
    }

    private static ReservedSignedState mockState(final long round, final Hash hash) {
        final ReservedSignedState rs = mock(ReservedSignedState.class);
        final SignedState ss = mock(SignedState.class);
        final PlatformMerkleStateRoot s = mock(PlatformMerkleStateRoot.class);
        when(rs.get()).thenReturn(ss);
        when(ss.getState()).thenReturn(s);
        when(ss.getRound()).thenReturn(round);
        when(s.getHash()).thenReturn(hash);
        return rs;
    }
}
