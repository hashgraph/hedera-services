/*
 * Copyright (C) 2016-2024 Hedera Hashgraph, LLC
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
import static com.swirlds.platform.test.fixtures.state.FakeMerkleStateLifecycles.FAKE_MERKLE_STATE_LIFECYCLES;
import static com.swirlds.platform.test.fixtures.state.FakeMerkleStateLifecycles.registerMerkleStateRootClassIds;

import com.swirlds.base.time.Time;
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
import com.swirlds.platform.state.MerkleRoot;
import com.swirlds.platform.state.MerkleStateRoot;
import com.swirlds.platform.state.MinimumJudgeInfo;
import com.swirlds.platform.state.PlatformStateModifier;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.system.BasicSoftwareVersion;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.platform.test.fixtures.addressbook.RandomAddressBookBuilder;
import com.swirlds.platform.test.fixtures.state.manager.SignatureVerificationTestUtils;
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

    final Random random;

    private MerkleRoot state;
    private Long round;
    private Hash legacyRunningEventHash;
    private AddressBook addressBook;
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
        final AddressBook addressBookInstance;
        if (addressBook == null) {
            addressBookInstance = RandomAddressBookBuilder.create(random)
                    .withWeightDistributionStrategy(RandomAddressBookBuilder.WeightDistributionStrategy.BALANCED)
                    .build();
        } else {
            addressBookInstance = addressBook;
        }

        final SoftwareVersion softwareVersionInstance;
        if (softwareVersion == null) {
            softwareVersionInstance = new BasicSoftwareVersion(random.nextInt(1, 100));
        } else {
            softwareVersionInstance = softwareVersion;
        }

        final MerkleRoot stateInstance;
        registerMerkleStateRootClassIds();
        if (state == null) {
            if (useBlockingState) {
                stateInstance = new BlockingSwirldState();
            } else {
                stateInstance = new MerkleStateRoot(
                        FAKE_MERKLE_STATE_LIFECYCLES, version -> new BasicSoftwareVersion(version.major()));
            }
            ((MerkleStateRoot) stateInstance).setTime(Time.getCurrent());
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

        final PlatformStateModifier platformState = stateInstance.getWritablePlatformState();

        platformState.bulkUpdate(v -> {
            v.setSnapshot(consensusSnapshotInstance);
            v.setAddressBook(addressBookInstance);
            v.setLegacyRunningEventHash(legacyRunningEventHashInstance);
            v.setCreationSoftwareVersion(softwareVersionInstance);
            v.setRoundsNonAncient(roundsNonAncientInstance);
            v.setConsensusTimestamp(consensusTimestampInstance);
        });

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
                platformContext,
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
                if (addressBookInstance.getSize() > 0) {
                    for (int i = 0; i < addressBookInstance.getSize() / 3 + 1; i++) {
                        signingNodeIdsInstance.add(addressBookInstance.getNodeId(i));
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
    public RandomSignedStateGenerator setState(final MerkleStateRoot state) {
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
     * Set the address book.
     *
     * @return this object
     */
    public RandomSignedStateGenerator setAddressBook(final AddressBook addressBook) {
        this.addressBook = addressBook;
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
}
