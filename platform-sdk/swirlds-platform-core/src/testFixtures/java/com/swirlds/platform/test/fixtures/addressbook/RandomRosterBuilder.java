// SPDX-License-Identifier: Apache-2.0
package com.swirlds.platform.test.fixtures.addressbook;

import static com.swirlds.platform.crypto.KeyCertPurpose.SIGNING;

import com.hedera.hapi.node.state.roster.Roster;
import com.swirlds.common.platform.NodeId;
import com.swirlds.platform.crypto.KeysAndCerts;
import com.swirlds.platform.crypto.PublicStores;
import com.swirlds.platform.crypto.SerializableX509Certificate;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.stream.IntStream;

/**
 * A utility for generating a random roster.
 */
public class RandomRosterBuilder {

    /**
     * All randomness comes from this.
     */
    private final Random random;

    /**
     * The number of roster entries to put into the roster.
     */
    private int size = 4;

    /**
     * Describes different ways that the random roster has its weight distributed if the custom strategy lambda is
     * unset.
     */
    public enum WeightDistributionStrategy {
        /**
         * All nodes have equal weight.
         */
        BALANCED,
        /**
         * Nodes are given weight with a gaussian distribution.
         */
        GAUSSIAN
    }

    /**
     * The weight distribution strategy.
     */
    private WeightDistributionStrategy weightDistributionStrategy = WeightDistributionStrategy.GAUSSIAN;

    /**
     * The average weight. Used directly if using {@link WeightDistributionStrategy#BALANCED}, used as mean if using
     * {@link WeightDistributionStrategy#GAUSSIAN}.
     */
    private long averageWeight = 1000;

    /**
     * The standard deviation of the weight, ignored if distribution strategy is not
     * {@link WeightDistributionStrategy#GAUSSIAN}.
     */
    private long weightStandardDeviation = 100;

    /**
     * The minimum weight to give to any particular address.
     */
    private long minimumWeight = 0;

    /**
     * The maximum weight to give to any particular address.
     */
    private Long maximumWeight;

    /**
     * the next available node id for new addresses.
     */
    private NodeId nextNodeId = NodeId.FIRST_NODE_ID;

    /**
     * If true then generate real cryptographic keys.
     */
    private boolean realKeys;

    /**
     * If we are using real keys, this map will hold the private keys for each address.
     */
    private final Map<NodeId, KeysAndCerts> privateKeys = new HashMap<>();

    /**
     * Create a new random roster generator.
     *
     * @param random a source of randomness
     * @return a new random roster generator
     */
    @NonNull
    public static RandomRosterBuilder create(@NonNull final Random random) {
        return new RandomRosterBuilder(random);
    }

    /**
     * Constructor.
     *
     * @param random a source of randomness
     */
    private RandomRosterBuilder(@NonNull final Random random) {
        this.random = Objects.requireNonNull(random);
    }

    /**
     * Build a random roster given the provided configuration.
     */
    @NonNull
    public Roster build() {
        final Roster.Builder builder = Roster.newBuilder();

        if (maximumWeight == null && size > 0) {
            // We don't want the total weight to overflow a long
            maximumWeight = Long.MAX_VALUE / size;
        }

        builder.rosterEntries(IntStream.range(0, size)
                .mapToObj(index -> {
                    final NodeId nodeId = getNextNodeId();
                    final RandomRosterEntryBuilder addressBuilder = RandomRosterEntryBuilder.create(random)
                            .withNodeId(nodeId.id())
                            .withWeight(getNextWeight());

                    generateKeys(nodeId, addressBuilder);
                    return addressBuilder.build();
                })
                .toList());

        return builder.build();
    }

    /**
     * Set the size of the roster.
     *
     * @return this object
     */
    @NonNull
    public RandomRosterBuilder withSize(final int size) {
        this.size = size;
        return this;
    }

    /**
     * Set the average weight for an address. If the weight distribution strategy is
     * {@link WeightDistributionStrategy#BALANCED}, all addresses will have this weight. If the weight distribution
     * strategy is {@link WeightDistributionStrategy#GAUSSIAN}, this will be the mean weight.
     *
     * @return this object
     */
    @NonNull
    public RandomRosterBuilder withAverageWeight(final long averageWeight) {
        this.averageWeight = averageWeight;
        return this;
    }

    /**
     * Set the standard deviation for the weight for an address. Ignored unless the weight distribution strategy is
     * {@link WeightDistributionStrategy#GAUSSIAN}.
     *
     * @return this object
     */
    @NonNull
    public RandomRosterBuilder withWeightStandardDeviation(final long weightStandardDeviation) {
        this.weightStandardDeviation = weightStandardDeviation;
        return this;
    }

    /**
     * Set the minimum weight for an address. Overrides the weight generation strategy.
     *
     * @return this object
     */
    @NonNull
    public RandomRosterBuilder withMinimumWeight(final long minimumWeight) {
        this.minimumWeight = minimumWeight;
        return this;
    }

    /**
     * Set the maximum weight for an address. Overrides the weight generation strategy.
     *
     * @return this object
     */
    @NonNull
    public RandomRosterBuilder withMaximumWeight(final long maximumWeight) {
        this.maximumWeight = maximumWeight;
        return this;
    }

    /**
     * Set the strategy used for deciding distribution of weight.
     *
     * @return this object
     */
    @NonNull
    public RandomRosterBuilder withWeightDistributionStrategy(
            @NonNull final WeightDistributionStrategy weightDistributionStrategy) {

        this.weightDistributionStrategy = weightDistributionStrategy;
        return this;
    }

    /**
     * Specify if real cryptographic keys should be generated (default false). Warning: generating real keys is very
     * time consuming.
     *
     * @param realKeysEnabled if true then generate real cryptographic keys
     * @return this object
     */
    @NonNull
    public RandomRosterBuilder withRealKeysEnabled(final boolean realKeysEnabled) {
        this.realKeys = realKeysEnabled;
        return this;
    }

    /**
     * Get the private keys for a node. Should only be called after the roster has been built and only if
     * {@link #withRealKeysEnabled(boolean)} was set to true.
     *
     * @param nodeId the node id
     * @return the private keys
     * @throws IllegalStateException if real keys are not being generated or the roster has not been built
     */
    @NonNull
    public KeysAndCerts getPrivateKeys(@NonNull final NodeId nodeId) {
        if (!realKeys) {
            throw new IllegalStateException("Real keys are not being generated");
        }
        if (!privateKeys.containsKey(nodeId)) {
            throw new IllegalStateException("Unknown node ID " + nodeId);
        }
        return privateKeys.get(nodeId);
    }

    /**
     * Generate the next node ID.
     */
    @NonNull
    private NodeId getNextNodeId() {
        final NodeId nextId = nextNodeId;
        // randomly advance between 1 and 3 steps
        final int randomAdvance = random.nextInt(3);
        nextNodeId = nextNodeId.getOffset(randomAdvance + 1L);
        return nextId;
    }

    /**
     * Generate the next weight for the next address.
     */
    private long getNextWeight() {
        final long unboundedWeight;
        switch (weightDistributionStrategy) {
            case BALANCED -> unboundedWeight = averageWeight;
            case GAUSSIAN -> unboundedWeight =
                    Math.max(0, (long) (averageWeight + random.nextGaussian() * weightStandardDeviation));
            default -> throw new IllegalStateException("Unexpected value: " + weightDistributionStrategy);
        }

        return Math.min(maximumWeight, Math.max(minimumWeight, unboundedWeight));
    }

    /**
     * Generate the cryptographic keys for a node.
     */
    private void generateKeys(@NonNull final NodeId nodeId, @NonNull final RandomRosterEntryBuilder addressBuilder) {
        if (realKeys) {
            try {
                final PublicStores publicStores = new PublicStores();

                final byte[] masterKey = new byte[64];
                random.nextBytes(masterKey);

                final KeysAndCerts keysAndCerts =
                        KeysAndCerts.generate(nodeId, new byte[] {}, masterKey, new byte[] {}, publicStores);
                privateKeys.put(nodeId, keysAndCerts);

                final SerializableX509Certificate sigCert =
                        new SerializableX509Certificate(publicStores.getCertificate(SIGNING, nodeId));

                addressBuilder.withSigCert(sigCert);

            } catch (final Exception e) {
                throw new RuntimeException();
            }
        }
    }
}
