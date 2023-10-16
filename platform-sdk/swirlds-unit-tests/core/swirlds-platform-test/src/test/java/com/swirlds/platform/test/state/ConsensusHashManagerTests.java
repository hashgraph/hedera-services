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

package com.swirlds.platform.test.state;

import static com.swirlds.common.test.fixtures.RandomUtils.getRandomPrintSeed;
import static com.swirlds.common.test.fixtures.RandomUtils.randomHash;
import static com.swirlds.common.utility.Threshold.MAJORITY;
import static com.swirlds.common.utility.Threshold.SUPER_MAJORITY;
import static com.swirlds.platform.state.iss.ConsensusHashManager.DO_NOT_IGNORE_ROUNDS;
import static com.swirlds.platform.test.DispatchBuilderUtils.getDefaultDispatchConfiguration;
import static com.swirlds.platform.test.state.RoundHashValidatorTests.generateCatastrophicNodeHashes;
import static com.swirlds.platform.test.state.RoundHashValidatorTests.generateNodeHashes;
import static com.swirlds.platform.test.state.RoundHashValidatorTests.generateRegularNodeHashes;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;

import com.swirlds.base.time.Time;
import com.swirlds.common.config.ConsensusConfig;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.Signature;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.SoftwareVersion;
import com.swirlds.common.system.address.Address;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.system.transaction.internal.StateSignatureTransaction;
import com.swirlds.common.test.fixtures.RandomAddressBookGenerator;
import com.swirlds.platform.dispatch.DispatchBuilder;
import com.swirlds.platform.dispatch.triggers.error.CatastrophicIssTrigger;
import com.swirlds.platform.dispatch.triggers.error.SelfIssTrigger;
import com.swirlds.platform.state.iss.ConsensusHashManager;
import com.swirlds.platform.state.iss.internal.HashValidityStatus;
import com.swirlds.test.framework.context.TestPlatformContextBuilder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ConsensusHashManager Tests")
class ConsensusHashManagerTests {
    /** the default epoch hash to use */
    private static final Hash DEFAULT_EPOCH_HASH = null;

    private static final SoftwareVersion DEFAULT_SOFTWARE_VERSION = null;

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

        final DispatchBuilder dispatchBuilder = new DispatchBuilder(getDefaultDispatchConfiguration());
        final ConsensusHashManager manager = new ConsensusHashManager(
                platformContext,
                Time.getCurrent(),
                dispatchBuilder,
                addressBook,
                DEFAULT_EPOCH_HASH,
                DEFAULT_SOFTWARE_VERSION,
                DO_NOT_IGNORE_ROUNDS);

        final AtomicBoolean fail = new AtomicBoolean(false);
        dispatchBuilder.registerObserver(this, SelfIssTrigger.class, (a, b, c) -> fail.set(true));
        dispatchBuilder.registerObserver(this, CatastrophicIssTrigger.class, (a, b) -> fail.set(true));

        dispatchBuilder.start();

        final int rounds = 1_000;
        for (long round = 1; round <= rounds; round++) {
            final Hash roundHash = randomHash(random);

            if (round == 1) {
                manager.overridingStateObserver(round, roundHash);
            } else {
                manager.roundCompleted(round);
                manager.stateHashedObserver(round, roundHash);
            }

            for (final Address address : addressBook) {
                manager.handlePostconsensusSignatureTransaction(
                        address.getNodeId(),
                        new StateSignatureTransaction(round, mock(Signature.class), roundHash),
                        null);
            }
        }

        assertFalse(fail.get(), "failure condition triggered");
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
        final List<Hash> consensusHashes = new ArrayList<>(roundsNonAncient);
        for (int round = 0; round < roundsNonAncient; round++) {
            final RoundHashValidatorTests.HashGenerationData data;

            if (random.nextDouble() < 2.0 / 3) {
                // Choose hashes so that there is a valid consensus hash
                data = generateRegularNodeHashes(random, addressBook, round);
                consensusHashes.add(data.consensusHash());

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
                consensusHashes.add(null);
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

        final DispatchBuilder dispatchBuilder = new DispatchBuilder(getDefaultDispatchConfiguration());
        final ConsensusHashManager manager = new ConsensusHashManager(
                platformContext,
                Time.getCurrent(),
                dispatchBuilder,
                addressBook,
                DEFAULT_EPOCH_HASH,
                DEFAULT_SOFTWARE_VERSION,
                DO_NOT_IGNORE_ROUNDS);

        final AtomicBoolean fail = new AtomicBoolean(false);
        final AtomicInteger issCount = new AtomicInteger(0);
        final AtomicInteger catastrophicIssCount = new AtomicInteger(0);
        final Set<Long> observedRounds = new HashSet<>();

        dispatchBuilder.registerObserver(
                this, SelfIssTrigger.class, (final Long round, final Hash selfStateHash, final Hash consensusHash) -> {
                    try {

                        assertTrue(observedRounds.add(round), "rounds should trigger a notification at most once");

                        final int roundIndex = (int) (long) round;
                        final HashValidityStatus expectedStatus = expectedRoundStatus.get(roundIndex);

                        assertEquals(selfHashes.get(roundIndex), selfStateHash, "invalid self hash");

                        if (expectedStatus == HashValidityStatus.SELF_ISS) {
                            assertEquals(consensusHashes.get(roundIndex), consensusHash, "unexpected consensus hash");
                            issCount.getAndIncrement();
                        } else {
                            fail("invalid status " + expectedStatus);
                        }
                    } catch (final Throwable t) {
                        t.printStackTrace();
                        fail.set(true);
                    }
                });

        dispatchBuilder.registerObserver(
                this, CatastrophicIssTrigger.class, (final Long round, final Hash selfStateHash) -> {
                    try {
                        final int roundIndex = (int) (long) round;

                        assertTrue(observedRounds.add(round), "rounds should trigger a notification at most once");

                        final HashValidityStatus expectedStatus = expectedRoundStatus.get(roundIndex);

                        assertEquals(selfHashes.get(roundIndex), selfStateHash, "invalid self hash");

                        if (expectedStatus == HashValidityStatus.CATASTROPHIC_ISS) {
                            catastrophicIssCount.getAndIncrement();
                        } else {
                            fail("invalid status " + expectedStatus);
                        }
                    } catch (final Throwable t) {
                        t.printStackTrace();
                        fail.set(true);
                    }
                });

        dispatchBuilder.start();

        manager.overridingStateObserver(0L, selfHashes.get(0));

        // Start collecting data for rounds.
        for (long round = 1; round < roundsNonAncient; round++) {
            manager.roundCompleted(round);
        }

        // Add all the self hashes.
        for (long round = 1; round < roundsNonAncient; round++) {
            manager.stateHashedObserver(round, selfHashes.get((int) round));
        }

        // Report hashes from the network in random order
        final List<RoundHashValidatorTests.NodeHashInfo> operations = new ArrayList<>();
        while (!roundData.isEmpty()) {
            final int index = random.nextInt(roundData.size());
            operations.add(roundData.get(index).nodeList().remove(0));
            if (roundData.get(index).nodeList().isEmpty()) {
                roundData.remove(index);
            }
        }

        assertEquals(roundsNonAncient * addressBook.getSize(), operations.size(), "unexpected number of operations");

        for (final RoundHashValidatorTests.NodeHashInfo nodeHashInfo : operations) {
            final NodeId nodeId = nodeHashInfo.nodeId();

            manager.handlePostconsensusSignatureTransaction(
                    nodeId,
                    new StateSignatureTransaction(
                            nodeHashInfo.round(), mock(Signature.class), nodeHashInfo.nodeStateHash()),
                    null);
        }

        // Shifting after completion should have no side effects
        for (long i = roundsNonAncient; i < 2L * roundsNonAncient - 1; i++) {
            manager.roundCompleted(i);
        }

        assertFalse(fail.get(), "exception thrown in ISS callback");
        assertEquals(expectedSelfIssCount, issCount.get(), "unexpected number of ISS callbacks");
        assertEquals(
                expectedCatastrophicIssCount,
                catastrophicIssCount.get(),
                "unexpected number of catastrophic ISS callbacks");
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

        final DispatchBuilder dispatchBuilder = new DispatchBuilder(getDefaultDispatchConfiguration());
        final ConsensusHashManager manager = new ConsensusHashManager(
                platformContext,
                Time.getCurrent(),
                dispatchBuilder,
                addressBook,
                DEFAULT_EPOCH_HASH,
                DEFAULT_SOFTWARE_VERSION,
                DO_NOT_IGNORE_ROUNDS);

        dispatchBuilder.registerObserver(
                this, CatastrophicIssTrigger.class, (a, b) -> fail("did not expect catastrophic ISS"));

        final AtomicInteger issCount = new AtomicInteger();
        dispatchBuilder.registerObserver(this, SelfIssTrigger.class, (a, b, c) -> issCount.getAndIncrement());

        dispatchBuilder.start();

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
                        () -> manager.stateHashedObserver(targetRound, info.nodeStateHash()),
                        "should not be able to add hash for round not being tracked");
            }
            manager.handlePostconsensusSignatureTransaction(
                    info.nodeId(),
                    new StateSignatureTransaction(targetRound, mock(Signature.class), info.nodeStateHash()),
                    null);
        }

        assertEquals(0, issCount.get(), "all data should have been ignored");

        // Move forward to the next round. Data should no longer be ignored.
        // Use a different data set so we can know if old data was fully ignored.
        final RoundHashValidatorTests.HashGenerationData data =
                generateDataWithSelfIss(random, addressBook, selfId, targetRound);
        manager.roundCompleted(targetRound);
        for (final RoundHashValidatorTests.NodeHashInfo info : data.nodeList()) {
            if (info.nodeId() == selfId) {
                manager.stateHashedObserver(targetRound, info.nodeStateHash());
            }
            manager.handlePostconsensusSignatureTransaction(
                    info.nodeId(),
                    new StateSignatureTransaction(targetRound, mock(Signature.class), info.nodeStateHash()),
                    null);
        }

        assertEquals(1, issCount.get(), "data should not have been ignored");
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

        final DispatchBuilder dispatchBuilder = new DispatchBuilder(getDefaultDispatchConfiguration());
        final ConsensusHashManager manager = new ConsensusHashManager(
                platformContext,
                Time.getCurrent(),
                dispatchBuilder,
                addressBook,
                DEFAULT_EPOCH_HASH,
                DEFAULT_SOFTWARE_VERSION,
                DO_NOT_IGNORE_ROUNDS);

        dispatchBuilder.registerObserver(
                this, CatastrophicIssTrigger.class, (a, b) -> fail("did not expect catastrophic ISS"));

        final AtomicInteger issCount = new AtomicInteger();
        dispatchBuilder.registerObserver(this, SelfIssTrigger.class, (a, b, c) -> issCount.getAndIncrement());

        dispatchBuilder.start();

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
                        () -> manager.stateHashedObserver(targetRound, info.nodeStateHash()),
                        "should not be able to add hash for round not being tracked");
            }
            manager.handlePostconsensusSignatureTransaction(
                    info.nodeId(),
                    new StateSignatureTransaction(targetRound, mock(Signature.class), info.nodeStateHash()),
                    null);
        }

        assertEquals(0, issCount.get(), "all data should have been ignored");
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

        final DispatchBuilder dispatchBuilder = new DispatchBuilder(getDefaultDispatchConfiguration());
        final ConsensusHashManager manager = new ConsensusHashManager(
                platformContext,
                Time.getCurrent(),
                dispatchBuilder,
                addressBook,
                DEFAULT_EPOCH_HASH,
                DEFAULT_SOFTWARE_VERSION,
                DO_NOT_IGNORE_ROUNDS);

        final AtomicInteger issCount = new AtomicInteger();
        dispatchBuilder.registerObserver(this, CatastrophicIssTrigger.class, (a, b) -> issCount.getAndIncrement());

        dispatchBuilder.registerObserver(this, SelfIssTrigger.class, (a, b, c) -> issCount.getAndIncrement());

        dispatchBuilder.start();

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
                manager.stateHashedObserver(0L, info.nodeStateHash());
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

            manager.handlePostconsensusSignatureTransaction(
                    info.nodeId(),
                    new StateSignatureTransaction(targetRound, mock(Signature.class), info.nodeStateHash()),
                    null);
        }

        // Shift the window even though we have not added enough data for a decision
        manager.roundCompleted((long) roundsNonAncient);

        assertEquals(0, issCount.get(), "there wasn't enough data submitted to observe the ISS");
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

        final DispatchBuilder dispatchBuilder = new DispatchBuilder(getDefaultDispatchConfiguration());
        final ConsensusHashManager manager = new ConsensusHashManager(
                platformContext,
                Time.getCurrent(),
                dispatchBuilder,
                addressBook,
                DEFAULT_EPOCH_HASH,
                DEFAULT_SOFTWARE_VERSION,
                DO_NOT_IGNORE_ROUNDS);

        final AtomicInteger issCount = new AtomicInteger();
        dispatchBuilder.registerObserver(this, CatastrophicIssTrigger.class, (a, b) -> issCount.getAndIncrement());

        dispatchBuilder.registerObserver(this, SelfIssTrigger.class, (a, b, c) -> fail("did not expect self ISS"));

        dispatchBuilder.start();

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
                manager.stateHashedObserver(0L, info.nodeStateHash());
            }
        }

        long submittedWeight = 0;
        for (final RoundHashValidatorTests.NodeHashInfo info : data) {
            final long weight = addressBook.getAddress(info.nodeId()).getWeight();

            manager.handlePostconsensusSignatureTransaction(
                    info.nodeId(),
                    new StateSignatureTransaction(targetRound, mock(Signature.class), info.nodeStateHash()),
                    null);

            // Stop once we have added >2/3. We should not have decided yet, but will
            // have gathered enough to declare a catastrophic ISS
            submittedWeight += weight;
            if (SUPER_MAJORITY.isSatisfiedBy(submittedWeight, addressBook.getTotalWeight())) {
                break;
            }
        }

        // Shift the window even though we have not added enough data for a decision.
        // But we will have added enough to lead to a catastrophic ISS when the timeout is triggered.
        manager.roundCompleted((long) roundsNonAncient);

        assertEquals(1, issCount.get(), "shifting should have caused an ISS");
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

        final DispatchBuilder dispatchBuilder = new DispatchBuilder(getDefaultDispatchConfiguration());
        final ConsensusHashManager manager = new ConsensusHashManager(
                platformContext,
                Time.getCurrent(),
                dispatchBuilder,
                addressBook,
                DEFAULT_EPOCH_HASH,
                DEFAULT_SOFTWARE_VERSION,
                DO_NOT_IGNORE_ROUNDS);

        final AtomicInteger issCount = new AtomicInteger();
        dispatchBuilder.registerObserver(this, CatastrophicIssTrigger.class, (a, b) -> issCount.getAndIncrement());

        dispatchBuilder.registerObserver(this, SelfIssTrigger.class, (a, b, c) -> fail("did not expect self ISS"));

        dispatchBuilder.start();

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
                manager.stateHashedObserver(0L, info.nodeStateHash());
            }
        }

        long submittedWeight = 0;
        for (final RoundHashValidatorTests.NodeHashInfo info : data) {
            final long weight = addressBook.getAddress(info.nodeId()).getWeight();

            manager.handlePostconsensusSignatureTransaction(
                    info.nodeId(),
                    new StateSignatureTransaction(targetRound, mock(Signature.class), info.nodeStateHash()),
                    null);

            // Stop once we have added >2/3. We should not have decided yet, but will
            // have gathered enough to declare a catastrophic ISS
            submittedWeight += weight;
            if (SUPER_MAJORITY.isSatisfiedBy(submittedWeight, addressBook.getTotalWeight())) {
                break;
            }
        }

        // Shifting the window a great distance should not trigger the ISS.
        manager.overridingStateObserver(roundsNonAncient + 100L, randomHash(random));

        assertEquals(0, issCount.get(), "there wasn't enough data submitted to observe the ISS");
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

        final DispatchBuilder dispatchBuilder = new DispatchBuilder(getDefaultDispatchConfiguration());
        final ConsensusHashManager manager = new ConsensusHashManager(
                platformContext,
                Time.getCurrent(),
                dispatchBuilder,
                addressBook,
                DEFAULT_EPOCH_HASH,
                DEFAULT_SOFTWARE_VERSION,
                1);

        final AtomicBoolean fail = new AtomicBoolean(false);
        dispatchBuilder.registerObserver(this, SelfIssTrigger.class, (a, b, c) -> fail.set(true));
        dispatchBuilder.registerObserver(this, CatastrophicIssTrigger.class, (a, b) -> fail.set(true));

        dispatchBuilder.start();

        final int rounds = 1_000;
        for (long round = 1; round <= rounds; round++) {
            final Hash roundHash = randomHash(random);

            if (round == 1) {
                manager.overridingStateObserver(round, roundHash);
            } else {
                manager.roundCompleted(round);
                manager.stateHashedObserver(round, roundHash);
            }

            for (final Address address : addressBook) {
                if (round == 1) {
                    // Intentionally send bad hashes in the first round. We are configured to ignore this round.
                    manager.handlePostconsensusSignatureTransaction(
                            address.getNodeId(),
                            new StateSignatureTransaction(round, mock(Signature.class), randomHash(random)),
                            null);
                } else {
                    manager.handlePostconsensusSignatureTransaction(
                            address.getNodeId(),
                            new StateSignatureTransaction(round, mock(Signature.class), roundHash),
                            null);
                }
            }
        }

        assertFalse(fail.get(), "failure condition triggered");
    }
}
