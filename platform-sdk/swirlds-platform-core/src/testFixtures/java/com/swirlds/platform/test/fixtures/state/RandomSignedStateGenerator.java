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

package com.swirlds.platform.test.fixtures.state;

import static com.swirlds.common.test.fixtures.RandomUtils.getRandomPrintSeed;
import static com.swirlds.common.test.fixtures.RandomUtils.randomHash;
import static com.swirlds.common.test.fixtures.RandomUtils.randomSignature;
import static com.swirlds.platform.test.fixtures.state.FakeSwirldsStateLifecycles.FAKE_MERKLE_STATE_LIFECYCLES;
import static com.swirlds.platform.test.fixtures.state.FakeSwirldsStateLifecycles.registerMerkleStateRootClassIds;

import com.hedera.hapi.node.state.roster.Roster;
import com.hedera.hapi.node.state.roster.RosterEntry;
import com.swirlds.base.time.Time;
import com.swirlds.common.Reservable;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.Signature;
import com.swirlds.common.merkle.crypto.MerkleCryptoFactory;
import com.swirlds.common.platform.NodeId;
import com.swirlds.common.test.fixtures.RandomUtils;
import com.swirlds.common.test.fixtures.platform.TestPlatformContextBuilder;
import com.swirlds.config.api.Configuration;
import com.swirlds.config.extensions.test.fixtures.TestConfigBuilder;
import com.swirlds.platform.config.StateConfig;
import com.swirlds.platform.consensus.ConsensusSnapshot;
import com.swirlds.platform.crypto.SignatureVerifier;
import com.swirlds.platform.roster.RosterUtils;
import com.swirlds.platform.state.MinimumJudgeInfo;
import com.swirlds.platform.state.PlatformMerkleStateRoot;
import com.swirlds.platform.state.PlatformStateModifier;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.system.BasicSoftwareVersion;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.platform.test.fixtures.addressbook.RandomRosterBuilder;
import com.swirlds.platform.test.fixtures.addressbook.RandomRosterBuilder.WeightDistributionStrategy;
import com.swirlds.platform.test.fixtures.state.manager.SignatureVerificationTestUtils;
import com.swirlds.state.State;
import com.swirlds.state.merkle.MerkleStateRoot;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * A utility for generating random signed states.
 */
public class RandomSignedStateGenerator {

    /**
     * Signed states now use virtual maps which are heavy RAM consumers. They need to be released
     * in order to avoid producing OOMs when running tests. This list tracks all signed states
     * built on a given thread.
     */
    private static final ThreadLocal<List<SignedState>> builtSignedStates = ThreadLocal.withInitial(ArrayList::new);

    final Random random;

    private PlatformMerkleStateRoot state;
    private Long round;
    private Hash legacyRunningEventHash;
    private Roster roster;
    private Instant consensusTimestamp;
    private Boolean freezeState = false;
    private SoftwareVersion softwareVersion;
    private List<NodeId> signingNodeIds;
    private Map<NodeId, Signature> signatures;
    private Hash stateHash = null;
    private Integer roundsNonAncient = null;
    private Hash epoch = null;
    private ConsensusSnapshot consensusSnapshot;
    private SignatureVerifier signatureVerifier;
    private boolean deleteOnBackgroundThread;
    private boolean pcesRound;
    private boolean useBlockingState = false;

    /**
     * Create a new signed state generator with a random seed.
     */
    public RandomSignedStateGenerator() {
        random = getRandomPrintSeed();
    }

    /**
     * Create a new signed state generator with a specific seed.
     */
    public RandomSignedStateGenerator(final long seed) {
        random = new Random(seed);
    }

    /**
     * Create a new signed state generator with a random object.
     */
    public RandomSignedStateGenerator(final Random random) {
        this.random = random;
    }

    /**
     * Build a new signed state.
     *
     * @return a new signed state
     */
    public SignedState build() {
        final Roster rosterInstance;
        if (roster == null) {
            rosterInstance = RandomRosterBuilder.create(random)
                    .withWeightDistributionStrategy(WeightDistributionStrategy.BALANCED)
                    .build();
        } else {
            rosterInstance = roster;
        }

        final SoftwareVersion softwareVersionInstance;
        if (softwareVersion == null) {
            softwareVersionInstance = new BasicSoftwareVersion(random.nextInt(1, 100));
        } else {
            softwareVersionInstance = softwareVersion;
        }

        final PlatformMerkleStateRoot stateInstance;
        registerMerkleStateRootClassIds();
        if (state == null) {
            if (useBlockingState) {
                stateInstance = new BlockingSwirldState();
            } else {
                stateInstance = new PlatformMerkleStateRoot(
                        FAKE_MERKLE_STATE_LIFECYCLES, version -> new BasicSoftwareVersion(version.major()));
            }
            stateInstance.setTime(Time.getCurrent());
        } else {
            stateInstance = state;
        }

        final long roundInstance;
        if (round == null) {
            roundInstance = Math.abs(random.nextLong());
        } else {
            roundInstance = round;
        }

        final Hash legacyRunningEventHashInstance;
        if (legacyRunningEventHash == null) {
            legacyRunningEventHashInstance = randomHash(random);
        } else {
            legacyRunningEventHashInstance = legacyRunningEventHash;
        }

        final Instant consensusTimestampInstance;
        if (consensusTimestamp == null) {
            consensusTimestampInstance = RandomUtils.randomInstant(random);
        } else {
            consensusTimestampInstance = consensusTimestamp;
        }

        final boolean freezeStateInstance;
        if (freezeState == null) {
            freezeStateInstance = random.nextBoolean();
        } else {
            freezeStateInstance = freezeState;
        }

        final int roundsNonAncientInstance;
        if (roundsNonAncient == null) {
            roundsNonAncientInstance = 26;
        } else {
            roundsNonAncientInstance = roundsNonAncient;
        }

        final ConsensusSnapshot consensusSnapshotInstance;
        if (consensusSnapshot == null) {
            consensusSnapshotInstance = new ConsensusSnapshot(
                    roundInstance,
                    Stream.generate(() -> randomHash(random)).limit(10).toList(),
                    IntStream.range(0, roundsNonAncientInstance)
                            .mapToObj(i -> new MinimumJudgeInfo(roundInstance - i, 0L))
                            .toList(),
                    roundInstance,
                    consensusTimestampInstance);
        } else {
            consensusSnapshotInstance = consensusSnapshot;
        }
        FAKE_MERKLE_STATE_LIFECYCLES.initPlatformState((MerkleStateRoot) stateInstance);
        final PlatformStateModifier platformState = stateInstance.getWritablePlatformState();

        platformState.bulkUpdate(v -> {
            v.setSnapshot(consensusSnapshotInstance);
            v.setLegacyRunningEventHash(legacyRunningEventHashInstance);
            v.setCreationSoftwareVersion(softwareVersionInstance);
            v.setRoundsNonAncient(roundsNonAncientInstance);
            v.setConsensusTimestamp(consensusTimestampInstance);
        });

        FAKE_MERKLE_STATE_LIFECYCLES.initRosterState((MerkleStateRoot) stateInstance);
        RosterUtils.setActiveRoster((State) stateInstance, rosterInstance, roundInstance);

        if (signatureVerifier == null) {
            signatureVerifier = SignatureVerificationTestUtils::verifySignature;
        }

        final Configuration configuration = new TestConfigBuilder()
                .withValue("state.stateHistoryEnabled", true)
                .withConfigDataType(StateConfig.class)
                .getOrCreateConfig();
        final PlatformContext platformContext = TestPlatformContextBuilder.create()
                .withConfiguration(configuration)
                .build();

        final SignedState signedState = new SignedState(
                configuration,
                signatureVerifier,
                stateInstance,
                "RandomSignedStateGenerator.build()",
                freezeStateInstance,
                deleteOnBackgroundThread,
                pcesRound);

        MerkleCryptoFactory.getInstance().digestTreeSync(stateInstance);
        if (stateHash != null) {
            stateInstance.setHash(stateHash);
        }

        final Map<NodeId, Signature> signaturesInstance;
        if (signatures == null) {
            final List<NodeId> signingNodeIdsInstance;
            if (signingNodeIds == null) {
                signingNodeIdsInstance = new LinkedList<>();
                if (!rosterInstance.rosterEntries().isEmpty()) {
                    for (int i = 0; i < rosterInstance.rosterEntries().size() / 3 + 1; i++) {
                        final RosterEntry node = rosterInstance.rosterEntries().get(i);
                        signingNodeIdsInstance.add(NodeId.of(node.nodeId()));
                    }
                }
            } else {
                signingNodeIdsInstance = signingNodeIds;
            }

            signaturesInstance = new HashMap<>();

            for (final NodeId nodeID : signingNodeIdsInstance) {
                signaturesInstance.put(nodeID, randomSignature(random));
            }
        } else {
            signaturesInstance = signatures;
        }

        for (final NodeId nodeId : signaturesInstance.keySet()) {
            signedState.getSigSet().addSignature(nodeId, signaturesInstance.get(nodeId));
        }

        builtSignedStates.get().add(signedState);
        return signedState;
    }

    /**
     * Build multiple states.
     *
     * @param count the number of states to build
     */
    public List<SignedState> build(final int count) {
        final List<SignedState> states = new ArrayList<>(count);

        for (int i = 0; i < count; i++) {
            states.add(build());
        }

        return states;
    }

    /**
     * Set if this state should be deleted on a background thread.
     * ({@link com.swirlds.platform.state.signed.StateGarbageCollector} must be wired up in order for this to happen)
     *
     * @param deleteOnBackgroundThread if true, delete on a background thread
     * @return this object
     */
    @NonNull
    public RandomSignedStateGenerator setDeleteOnBackgroundThread(final boolean deleteOnBackgroundThread) {
        this.deleteOnBackgroundThread = deleteOnBackgroundThread;
        return this;
    }

    /**
     * Set the state.
     *
     * @return this object
     */
    public RandomSignedStateGenerator setState(final PlatformMerkleStateRoot state) {
        this.state = state;
        return this;
    }

    /**
     * Set the round when the state was generated.
     *
     * @return this object
     */
    public RandomSignedStateGenerator setRound(final long round) {
        this.round = round;
        return this;
    }

    /**
     * Set the legacy running hash of all events that have been applied to this state since genesis.
     *
     * @return this object
     */
    public RandomSignedStateGenerator setLegacyRunningEventHash(final Hash legacyRunningEventHash) {
        this.legacyRunningEventHash = legacyRunningEventHash;
        return this;
    }

    /**
     * Set the roster.
     *
     * @return this object
     */
    public RandomSignedStateGenerator setRoster(final Roster roster) {
        this.roster = roster;
        return this;
    }

    /**
     * Set the timestamp associated with this state.
     *
     * @return this object
     */
    public RandomSignedStateGenerator setConsensusTimestamp(final Instant consensusTimestamp) {
        this.consensusTimestamp = consensusTimestamp;
        return this;
    }

    /**
     * Specify if this state was written to disk as a result of a freeze.
     *
     * @return this object
     */
    public RandomSignedStateGenerator setFreezeState(final boolean freezeState) {
        this.freezeState = freezeState;
        return this;
    }

    /**
     * Set the software version that was used to create this state.
     *
     * @return this object
     */
    public RandomSignedStateGenerator setSoftwareVersion(final SoftwareVersion softwareVersion) {
        this.softwareVersion = softwareVersion;
        return this;
    }

    /**
     * Specify which nodes have signed this signed state. Ignored if signatures are set.
     *
     * @param signingNodeIds a list of nodes that have signed this state
     * @return this object
     */
    @NonNull
    public RandomSignedStateGenerator setSigningNodeIds(@NonNull final List<NodeId> signingNodeIds) {
        Objects.requireNonNull(signingNodeIds, "signingNodeIds must not be null");
        this.signingNodeIds = signingNodeIds;
        return this;
    }

    /**
     * Provide signatures for the signed state.
     *
     * @return this object
     */
    @NonNull
    public RandomSignedStateGenerator setSignatures(@NonNull final Map<NodeId, Signature> signatures) {
        Objects.requireNonNull(signatures, "signatures must not be null");
        this.signatures = signatures;
        return this;
    }

    /**
     * Set the hash for the state. If unset the state is hashed like normal.
     *
     * @return this object
     */
    @NonNull
    public RandomSignedStateGenerator setStateHash(@NonNull final Hash stateHash) {
        Objects.requireNonNull(stateHash, "stateHash must not be null");
        this.stateHash = stateHash;
        return this;
    }

    /**
     * Set the number of non-ancient rounds.
     *
     * @return this object
     */
    public RandomSignedStateGenerator setRoundsNonAncient(final int roundsNonAncient) {
        this.roundsNonAncient = roundsNonAncient;
        return this;
    }

    /**
     * Set the epoch hash.
     *
     * @return this object
     */
    public RandomSignedStateGenerator setEpoch(Hash epoch) {
        this.epoch = epoch;
        return this;
    }

    @NonNull
    public RandomSignedStateGenerator setConsensusSnapshot(@NonNull final ConsensusSnapshot consensusSnapshot) {
        this.consensusSnapshot = consensusSnapshot;
        return this;
    }

    /**
     * Set the signature verifier.
     *
     * @return this object
     */
    @NonNull
    public RandomSignedStateGenerator setSignatureVerifier(@NonNull final SignatureVerifier signatureVerifier) {
        this.signatureVerifier = signatureVerifier;
        return this;
    }

    /**
     * Set if this state was generated during a PCES round.
     *
     * @param pcesRound true if this state was generated during a PCES round
     * @return this object
     */
    @NonNull
    public RandomSignedStateGenerator setPcesRound(final boolean pcesRound) {
        this.pcesRound = pcesRound;
        return this;
    }

    /**
     * Set if this state should use a {@link BlockingSwirldState} instead of a {@link MerkleStateRoot}.
     * This flag is fasle by default.
     *
     * @param useBlockingState true if this state should use {@link BlockingSwirldState}
     * @return this object
     */
    public RandomSignedStateGenerator setUseBlockingState(boolean useBlockingState) {
        this.useBlockingState = useBlockingState;
        return this;
    }

    /**
     * Keep calling release() on a given Reservable until it's completely released.
     * @param reservable a reservable to release
     */
    public static void releaseReservable(@NonNull final Reservable reservable) {
        while (reservable.getReservationCount() >= 0) {
            reservable.release();
        }
    }

    /**
     * Release all the SignedState objects built by this generator on the current thread,
     * and then clear the list of built states.
     */
    public static void releaseAllBuiltSignedStates() {
        builtSignedStates.get().forEach(signedState -> {
            releaseReservable(signedState.getState());
        });
        builtSignedStates.get().clear();
    }

    /**
     * Clear the list of states built on the current thread w/o releasing them.
     * There are tests that actually release the states on purpose, verifying the reserve/release behavior.
     * Some of these tests use mocks which fail if the state is released more than what the test expects.
     * For these few special cases, this method allows the test to "forget" about any states that it built
     * using this generator on the current thread. As long as the number of such special cases is low enough,
     * this shouldn't cause any serious resource leaks or OOMs in tests.
     */
    public static void forgetAllBuiltSignedStatesWithoutReleasing() {
        builtSignedStates.get().clear();
    }
}
