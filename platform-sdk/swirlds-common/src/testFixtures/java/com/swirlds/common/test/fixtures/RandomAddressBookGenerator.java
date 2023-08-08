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

package com.swirlds.common.test.fixtures;

import static com.swirlds.common.test.fixtures.RandomUtils.randomHash;

import com.swirlds.common.crypto.CryptographyHolder;
import com.swirlds.common.crypto.KeyType;
import com.swirlds.common.crypto.SerializablePublicKey;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.address.Address;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.test.fixtures.crypto.PreGeneratedPublicKeys;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;

/**
 * A utility for generating a random address book.
 */
public class RandomAddressBookGenerator {

    /**
     * All randomness comes from this.
     */
    private final Random random;

    private final Set<NodeId> nodeIds = new HashSet<>();

    /**
     * The number of addresses to put into the address book.
     */
    private int size = 4;

    /**
     * Describes different ways that the random address book is hashed.
     */
    public enum HashStrategy {
        NO_HASH,
        FAKE_HASH,
        REAL_HASH
    }

    /**
     * The strategy that should be used when hashing the address book.
     */
    private HashStrategy hashStrategy = HashStrategy.NO_HASH;

    /**
     * Describes different ways that the random address book has its weight distributed if the custom strategy lambda is
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
    private long minimumWeight = 1;

    /**
     * The maximum weight to give to any particular address.
     */
    private long maximumWeight = 100_000;

    /**
     * Used to determine weight for each node. Overrides all other behaviors if set.
     */
    private Function<NodeId, Long> customWeightGenerator;

    /** the next available node id for new addresses. */
    private NodeId nextNodeId = NodeId.FIRST_NODE_ID;

    /**
     * Create a new address book generator.
     */
    public RandomAddressBookGenerator() {
        this(new Random());
    }

    /**
     * Create a new address book generator with a source of randomness.
     *
     * @param random a source of randomness
     */
    public RandomAddressBookGenerator(final Random random) {
        this.random = random;
    }

    /**
     * Create a new address book generator with a seed.
     *
     * @param seed the seed for the random number generator
     */
    public RandomAddressBookGenerator(final long seed) {
        this(new Random(seed));
    }

    /**
     * Generate an address that has random data in the "unimportant" fields.
     *
     * @param random a source of randomness
     * @param id     the node ID
     * @param weight the weight
     */
    @NonNull
    public static Address addressWithRandomData(
            @NonNull final Random random, @NonNull final NodeId id, final long weight) {
        Objects.requireNonNull(random, "Random must not be null");
        Objects.requireNonNull(id, "NodeId must not be null");

        final SerializablePublicKey sigPublicKey = PreGeneratedPublicKeys.getPublicKey(KeyType.RSA, id.id());
        final SerializablePublicKey encPublicKey = PreGeneratedPublicKeys.getPublicKey(KeyType.EC, id.id());
        final SerializablePublicKey agreePublicKey = PreGeneratedPublicKeys.getPublicKey(KeyType.EC, id.id());

        final String nickname = NameUtils.getName(id.id());
        final String selfName = RandomUtils.randomString(random, 10);

        final int maxPort = 65535;
        final int minPort = 2000;
        final String addressInternalHostname;
        try {
            addressInternalHostname =
                    InetAddress.getByName(RandomUtils.randomIp(random)).getHostAddress();
        } catch (final UnknownHostException e) {
            throw new RuntimeException(e);
        }
        final int portInternalIpv4 = minPort + random.nextInt(maxPort - minPort);
        final String addressExternalHostname;
        try {
            addressExternalHostname =
                    InetAddress.getByName(RandomUtils.randomIp(random)).getHostAddress();
        } catch (final UnknownHostException e) {
            throw new RuntimeException(e);
        }
        final int portExternalIpv4 = minPort + random.nextInt(maxPort - minPort);

        final String memo = RandomUtils.randomString(random, 10);

        return new Address(
                id,
                nickname,
                selfName,
                weight,
                addressInternalHostname,
                portInternalIpv4,
                addressExternalHostname,
                portExternalIpv4,
                sigPublicKey,
                encPublicKey,
                agreePublicKey,
                memo);
    }

    /**
     * Generate the next node ID.
     */
    private NodeId getNextNodeId() {
        final NodeId nextId = this.nextNodeId;
        // randomly advance between 1 and 3 steps
        final int randomAdvance = random.nextInt(3);
        this.nextNodeId = this.nextNodeId.getOffset(randomAdvance + 1L);
        return nextId;
    }

    /**
     * Generate the next weight for the next address.
     */
    private long getNextWeight(@NonNull final NodeId nodeId) {
        Objects.requireNonNull(nodeId, "NodeId must not be null");

        if (customWeightGenerator != null) {
            return customWeightGenerator.apply(nodeId);
        }

        final long unboundedWeight;
        switch (weightDistributionStrategy) {
            case BALANCED -> unboundedWeight = averageWeight;
            case GAUSSIAN -> unboundedWeight = (long) (averageWeight + random.nextGaussian() * weightStandardDeviation);
            default -> throw new IllegalStateException("Unexpected value: " + weightDistributionStrategy);
        }

        return Math.min(maximumWeight, Math.max(minimumWeight, unboundedWeight));
    }

    /**
     * Build a random address book given the provided configuration.
     */
    public AddressBook build() {
        final AddressBook addressBook = new AddressBook();
        addressBook.setNextNodeId(this.nextNodeId);
        addressBook.setRound(Math.abs(random.nextLong()));

        addToAddressBook(addressBook);
        return addressBook;
    }

    /**
     * Add new addresses to an address book. The number of addresses is equal to the value specified by
     * {@link #setSize(int)}. The next candidate ID is set to be the address book's
     * {@link AddressBook#getNextNodeId()}.
     *
     * @param addressBook the address book to add new addresses to
     * @return the input address book after it has been expanded
     */
    public AddressBook addToAddressBook(final AddressBook addressBook) {
        setNextPossibleNodeId(addressBook.getNextNodeId());

        if (!nodeIds.isEmpty()) {
            nodeIds.stream().sorted().forEach(nodeId -> addressBook.add(buildNextAddress(nodeId)));
        } else {
            for (int index = 0; index < size; index++) {
                addressBook.add(buildNextAddress());
            }
        }

        if (hashStrategy == HashStrategy.FAKE_HASH) {
            addressBook.setHash(randomHash(random));
        } else if (hashStrategy == HashStrategy.REAL_HASH) {
            CryptographyHolder.get().digestSync(addressBook);
        }

        return addressBook;
    }

    /**
     * Remove a number of addresses from an address book.
     *
     * @param addressBook the address book to remove from
     * @param count       the number of addresses to remove, removes all addresses if count exceeds address book size
     * @return the input address book
     */
    @NonNull
    public AddressBook removeFromAddressBook(@NonNull final AddressBook addressBook, final int count) {
        Objects.requireNonNull(addressBook, "AddressBook must not be null");
        final List<NodeId> nodeIds = new ArrayList<>(addressBook.getSize());
        addressBook.forEach((final Address address) -> nodeIds.add(address.getNodeId()));
        Collections.shuffle(nodeIds, random);
        for (int i = 0; i < count && i < nodeIds.size(); i++) {
            addressBook.remove(nodeIds.get(i));
        }
        return addressBook;
    }

    /**
     * Build a random address using provided configuration. Address IS NOT automatically added to the address book.
     *
     * @return a random address
     */
    public Address buildNextAddress() {
        return buildNextAddress(null);
    }

    /**
     * Build a random address using provided configuration. Address IS NOT automatically added to the address book.
     *
     * @return a random address
     */
    @NonNull
    public Address buildNextAddress(@Nullable final NodeId suppliedNodeId) {
        final NodeId nodeId = suppliedNodeId == null ? getNextNodeId() : suppliedNodeId;
        return addressWithRandomData(random, nodeId, getNextWeight(nodeId));
    }

    /**
     * Build a random address with a specific node ID and take.
     */
    public Address buildNextAddress(final NodeId nodeId, final long weight) {
        this.nextNodeId = nodeId;
        return addressWithRandomData(random, getNextNodeId(), weight);
    }

    /**
     * Set the size of the address book.
     *
     * @return this object
     */
    public RandomAddressBookGenerator setSize(final int size) {
        this.size = size;
        return this;
    }

    /**
     * Set the node IDs of the address book.
     *
     * @return this object
     */
    public RandomAddressBookGenerator setNodeIds(@NonNull final Set<NodeId> nodeIds) {
        Objects.requireNonNull(nodeIds, "NodeIds must not be null");
        this.nodeIds.clear();
        this.nodeIds.addAll(nodeIds);
        return this;
    }

    /**
     * Set the desired hashing strategy for the address book.
     *
     * @return this object
     */
    public RandomAddressBookGenerator setHashStrategy(final HashStrategy hashStrategy) {
        this.hashStrategy = hashStrategy;
        return this;
    }

    /**
     * Set the average weight for an address.
     *
     * @return this object
     */
    public RandomAddressBookGenerator setAverageWeight(final long averageWeight) {
        this.averageWeight = averageWeight;
        return this;
    }

    /**
     * Set the standard deviation for the weight for an address.
     *
     * @return this object
     */
    public RandomAddressBookGenerator setWeightStandardDeviation(final long weightStandardDeviation) {
        this.weightStandardDeviation = weightStandardDeviation;
        return this;
    }

    /**
     * Set the minimum weight for an address.
     *
     * @return this object
     */
    public RandomAddressBookGenerator setMinimumWeight(final long minimumWeight) {
        this.minimumWeight = minimumWeight;
        return this;
    }

    /**
     * Set the maximum weight for an address.
     *
     * @return this object
     */
    public RandomAddressBookGenerator setMaximumWeight(final long maximumWeight) {
        this.maximumWeight = maximumWeight;
        return this;
    }

    /**
     * Provide a method that is used to determine the weight of each node. Overrides all other weight generation
     * settings if set.
     *
     * @return this object
     */
    public RandomAddressBookGenerator setCustomWeightGenerator(final Function<NodeId, Long> customWeightGenerator) {
        this.customWeightGenerator = customWeightGenerator;
        return this;
    }

    /**
     * Set the strategy used for deciding distribution of weight.
     *
     * @return this object
     */
    public RandomAddressBookGenerator setWeightDistributionStrategy(
            final WeightDistributionStrategy weightDistributionStrategy) {

        this.weightDistributionStrategy = weightDistributionStrategy;
        return this;
    }

    /**
     * Set the next node ID that may be used to generate a random address. This node ID may be skipped if gaps are
     * permitted.
     *
     * @param nodeId the next node ID that is considered when generating a random address
     * @return this object
     */
    public RandomAddressBookGenerator setNextPossibleNodeId(@NonNull final NodeId nodeId) {
        Objects.requireNonNull(nodeId, "NodeId must not be null");
        this.nextNodeId = nodeId;
        return this;
    }
}
