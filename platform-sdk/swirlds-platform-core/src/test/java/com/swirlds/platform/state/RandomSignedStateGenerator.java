/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.state;

import static com.swirlds.common.test.fixtures.RandomUtils.getRandomPrintSeed;
import static com.swirlds.common.test.fixtures.RandomUtils.randomHash;
import static com.swirlds.common.test.fixtures.RandomUtils.randomSignature;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.Signature;
import com.swirlds.common.merkle.crypto.MerkleCryptoFactory;
import com.swirlds.common.system.BasicSoftwareVersion;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.SoftwareVersion;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.test.fixtures.RandomAddressBookGenerator;
import com.swirlds.common.test.fixtures.RandomUtils;
import com.swirlds.platform.consensus.ConsensusSnapshot;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.test.fixtures.state.DummySwirldState;
import com.swirlds.test.framework.context.TestPlatformContextBuilder;
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

    private State state;
    private Long round;
    private Hash hashEventsCons;
    private AddressBook addressBook;
    private Instant consensusTimestamp;
    private Boolean freezeState = false;
    private SoftwareVersion softwareVersion;
    private List<NodeId> signingNodeIds;
    private Map<NodeId, Signature> signatures;
    private boolean protectionEnabled = false;
    private Hash stateHash = null;
    private Integer roundsNonAncient = null;
    private Hash epoch = null;

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
            addressBookInstance = new RandomAddressBookGenerator(random)
                    .setWeightDistributionStrategy(RandomAddressBookGenerator.WeightDistributionStrategy.BALANCED)
                    .setHashStrategy(RandomAddressBookGenerator.HashStrategy.REAL_HASH)
                    .build();
        } else {
            addressBookInstance = addressBook;
        }

        final State stateInstance;
        if (state == null) {
            stateInstance = new State();
            final DummySwirldState swirldState = new DummySwirldState(addressBookInstance);
            stateInstance.setSwirldState(swirldState);
            PlatformState platformState = new PlatformState();
            final PlatformData platformData = new PlatformData();
            platformData.setEpochHash(epoch);
            platformState.setPlatformData(platformData);
            stateInstance.setPlatformState(platformState);
        } else {
            stateInstance = state;
        }

        final long roundInstance;
        if (round == null) {
            roundInstance = Math.abs(random.nextLong());
        } else {
            roundInstance = round;
        }

        final Hash hashEventsConsInstance;
        if (hashEventsCons == null) {
            hashEventsConsInstance = randomHash(random);
        } else {
            hashEventsConsInstance = hashEventsCons;
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

        final SoftwareVersion softwareVersionInstance;
        if (softwareVersion == null) {
            softwareVersionInstance = new BasicSoftwareVersion(Math.abs(random.nextLong()));
        } else {
            softwareVersionInstance = softwareVersion;
        }

        stateInstance.getPlatformState().setAddressBook(addressBookInstance);
        stateInstance
                .getPlatformState()
                .getPlatformData()
                .setRound(roundInstance)
                .setHashEventsCons(hashEventsConsInstance)
                .setConsensusTimestamp(consensusTimestampInstance)
                .setCreationSoftwareVersion(softwareVersionInstance)
                .setRoundsNonAncient(roundsNonAncientInstance)
                .setSnapshot(new ConsensusSnapshot(
                        roundInstance,
                        Stream.generate(() -> randomHash(random)).limit(10).toList(),
                        IntStream.range(0, roundsNonAncientInstance)
                                .mapToObj(i -> new MinGenInfo(roundInstance - i, 0L))
                                .toList(),
                        roundInstance,
                        consensusTimestampInstance));

        final SignedState signedState = new SignedState(
                TestPlatformContextBuilder.create().build(),
                stateInstance,
                "RandomSignedStateGenerator.build()",
                freezeStateInstance);

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
                final Signature signature = randomSignature(random);

                final Signature wrappedSignature = spy(signature);
                doAnswer(invocation -> {
                            final byte[] bytes = invocation.getArgument(0);
                            final Hash hash =
                                    new Hash(bytes, stateInstance.getHash().getDigestType());

                            return hash.equals(stateInstance.getHash());
                        })
                        .when(wrappedSignature)
                        .verifySignature(any(), any());

                signaturesInstance.put(nodeID, wrappedSignature);
            }
        } else {
            signaturesInstance = signatures;
        }

        for (final NodeId nodeId : signaturesInstance.keySet()) {
            signedState.getSigSet().addSignature(nodeId, signaturesInstance.get(nodeId));
        }

        if (protectionEnabled && stateInstance.getSwirldState() instanceof final DummySwirldState dummySwirldState) {
            dummySwirldState.disableDeletion();
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
     * Set the state.
     *
     * @return this object
     */
    public RandomSignedStateGenerator setState(final State state) {
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
     * Set the running hash of all events that have been applied to this state since genesis.
     *
     * @return this object
     */
    public RandomSignedStateGenerator setHashEventsCons(final Hash hashEventsCons) {
        this.hashEventsCons = hashEventsCons;
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
     * Default false. If true and a {@link DummySwirldState} is being used, then disable deletion on the state.
     *
     * @return this object
     */
    @NonNull
    public RandomSignedStateGenerator setProtectionEnabled(final boolean protectionEnabled) {
        this.protectionEnabled = protectionEnabled;
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
}
