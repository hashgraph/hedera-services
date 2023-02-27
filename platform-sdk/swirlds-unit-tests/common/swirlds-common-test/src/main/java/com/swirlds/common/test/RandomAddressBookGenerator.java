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

package com.swirlds.common.test;

import static com.swirlds.common.test.RandomUtils.randomHash;

import com.swirlds.common.crypto.CryptographyHolder;
import com.swirlds.common.crypto.KeyType;
import com.swirlds.common.crypto.SerializablePublicKey;
import com.swirlds.common.system.address.Address;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.test.crypto.PreGeneratedPublicKeys;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.function.LongUnaryOperator;

/**
 * A utility for generating a random address book.
 */
public class RandomAddressBookGenerator {

    /**
     * All randomness comes from this.
     */
    private final Random random;

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
     * If true then IDs will be generated 0, 1, 2, 3, etc. with no gaps. If false then there may be some gaps
     * and the first node ID may not be 0.
     */
    private boolean sequentialIds = false;

    /**
     * Describes different ways that the random address book has its stake distributed if the custom strategy
     * lambda is unset.
     */
    public enum StakeDistributionStrategy {
        /**
         * All nodes have equal stake.
         */
        BALANCED,
        /**
         * Nodes are given stake with a gaussian distribution.
         */
        GAUSSIAN
    }

    /**
     * The stake distribution strategy.
     */
    private StakeDistributionStrategy stakeDistributionStrategy = StakeDistributionStrategy.GAUSSIAN;

    /**
     * The average stake. Used directly if using {@link StakeDistributionStrategy#BALANCED}, used as mean if
     * using {@link StakeDistributionStrategy#GAUSSIAN}.
     */
    private long averageStake = 1000;

    /**
     * The standard deviation of the stake, ignored if distribution strategy is not
     * {@link StakeDistributionStrategy#GAUSSIAN}.
     */
    private long stakeStandardDeviation = 100;

    /**
     * The minimum stake to give to any particular address.
     */
    private long minimumStake = 1;

    /**
     * The maximum stake to give to any particular address.
     */
    private long maximumStake = 100_000;

    /**
     * Used to determine stake for each node. Overrides all other behaviors if set.
     */
    private LongUnaryOperator customStakeGenerator;

    private long previousNodeId = -1;

    /**
     * Create a new address book generator.
     */
    public RandomAddressBookGenerator() {
        this(new Random());
    }

    /**
     * Create a new address book generator with a source of randomness.
     *
     * @param random
     * 		a source of randomness
     */
    public RandomAddressBookGenerator(final Random random) {
        this.random = random;
    }

    /**
     * Create a new address book generator with a seed.
     *
     * @param seed
     * 		the seed for the random number generator
     */
    public RandomAddressBookGenerator(final long seed) {
        this(new Random(seed));
    }

    /**
     * Generate an address that has random data in the "unimportant" fields.
     *
     * @param random
     * 		a source of randomness
     * @param id
     * 		the node ID
     * @param stake
     * 		the stake
     */
    public static Address addressWithRandomData(final Random random, final long id, final long stake) {

        final SerializablePublicKey sigPublicKey = PreGeneratedPublicKeys.getPublicKey(KeyType.RSA, id);
        final SerializablePublicKey encPublicKey = PreGeneratedPublicKeys.getPublicKey(KeyType.EC, id);
        final SerializablePublicKey agreePublicKey = PreGeneratedPublicKeys.getPublicKey(KeyType.EC, id);

        final String nickname = RandomUtils.randomString(random, 10);
        final String selfName = RandomUtils.randomString(random, 10);

        final boolean ownHost = false;
        final int maxPort = 65535;
        final int minPort = 2000;
        final byte[] addressInternalIpv4;
        try {
            addressInternalIpv4 =
                    InetAddress.getByName(RandomUtils.randomIp(random)).getAddress();
        } catch (final UnknownHostException e) {
            throw new RuntimeException(e);
        }
        final int portInternalIpv4 = minPort + random.nextInt(maxPort - minPort);
        final byte[] addressExternalIpv4;
        try {
            addressExternalIpv4 =
                    InetAddress.getByName(RandomUtils.randomIp(random)).getAddress();
        } catch (final UnknownHostException e) {
            throw new RuntimeException(e);
        }
        final int portExternalIpv4 = minPort + random.nextInt(maxPort - minPort);
        final byte[] addressInternalIpv6 = null;
        final int portInternalIpv6 = -1;
        final byte[] addressExternalIpv6 = null;
        final int portExternalIpv6 = -1;

        final String memo = RandomUtils.randomString(random, 10);

        return new Address(
                id,
                nickname,
                selfName,
                stake,
                ownHost,
                addressInternalIpv4,
                portInternalIpv4,
                addressExternalIpv4,
                portExternalIpv4,
                addressInternalIpv6,
                portInternalIpv6,
                addressExternalIpv6,
                portExternalIpv6,
                sigPublicKey,
                encPublicKey,
                agreePublicKey,
                memo);
    }

    /**
     * Generate the next node ID.
     */
    private long getNextNodeId() {
        final long nextId;
        if (sequentialIds) {
            nextId = previousNodeId + 1;
        } else {
            // randomly advance between 1 and 3 steps
            nextId = previousNodeId + random.nextInt(3) + 1;
        }
        previousNodeId = nextId;
        return nextId;
    }

    /**
     * Generate the next stake for the next address.
     */
    private long getNextStake(final long nodeId) {

        if (customStakeGenerator != null) {
            return customStakeGenerator.applyAsLong(nodeId);
        }

        final long unboundedStake;
        switch (stakeDistributionStrategy) {
            case BALANCED -> unboundedStake = averageStake;
            case GAUSSIAN -> unboundedStake = (long) (averageStake + random.nextGaussian() * stakeStandardDeviation);
            default -> throw new IllegalStateException("Unexpected value: " + stakeDistributionStrategy);
        }

        return Math.min(maximumStake, Math.max(minimumStake, unboundedStake));
    }

    /**
     * Build a random address book given the provided configuration.
     */
    public AddressBook build() {
        final AddressBook addressBook = new AddressBook();
        addressBook.setNextNodeId(previousNodeId + 1);
        addressBook.setRound(Math.abs(random.nextLong()));

        addToAddressBook(addressBook);
        return addressBook;
    }

    /**
     * Add new addresses to an address book. The number of addresses is equal to the value specified by
     * {@link #setSize(int)}. The next candidate ID is set to be the address book's {@link AddressBook#getNextNodeId()}.
     *
     * @param addressBook
     * 		the address book to add new addresses to
     * @return the input address book after it has been expanded
     */
    public AddressBook addToAddressBook(final AddressBook addressBook) {
        setNextPossibleNodeId(addressBook.getNextNodeId());

        for (int index = 0; index < size; index++) {
            addressBook.add(buildNextAddress());
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
     * @param addressBook
     * 		the address book to remove from
     * @param count
     * 		the number of addresses to remove, removes all addresses if count exceeds address book size
     * @return the input address book
     */
    public AddressBook removeFromAddressBook(final AddressBook addressBook, final int count) {
        final List<Long> nodeIds = new ArrayList<>(addressBook.getSize());
        addressBook.forEach((final Address address) -> nodeIds.add(address.getId()));
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
        final long nodeId = getNextNodeId();
        return addressWithRandomData(random, nodeId, getNextStake(nodeId));
    }

    /**
     * Build a random address with a specific node ID and take.
     */
    public Address buildNextAddress(final long nodeId, final long stake) {
        try {
            return addressWithRandomData(random, nodeId, stake);
        } finally {
            previousNodeId = nodeId;
        }
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
     * Set the desired hashing strategy for the address book.
     *
     * @return this object
     */
    public RandomAddressBookGenerator setHashStrategy(final HashStrategy hashStrategy) {
        this.hashStrategy = hashStrategy;
        return this;
    }

    /**
     * <p>
     * Specify if sequential IDs should be used. If true then IDs will start at 0 and will not have any gaps.
     * If false then there may be some gaps between IDs.
     * </p>
     *
     * <p>
     * FUTURE WORK: eventually, most if not all tests should be resilient with respect to non-sequential node IDs.
     * When it is necessary to support non-sequential node IDs in production then we may want to remove this setter
     * and always generate non-sequentially.
     * </p>
     *
     * @return this object
     */
    public RandomAddressBookGenerator setSequentialIds(final boolean sequentialIds) {
        this.sequentialIds = sequentialIds;
        return this;
    }

    /**
     * Set the average stake for an address.
     *
     * @return this object
     */
    public RandomAddressBookGenerator setAverageStake(final long averageStake) {
        this.averageStake = averageStake;
        return this;
    }

    /**
     * Set the standard deviation for the stake for an address.
     *
     * @return this object
     */
    public RandomAddressBookGenerator setStakeStandardDeviation(final long stakeStandardDeviation) {
        this.stakeStandardDeviation = stakeStandardDeviation;
        return this;
    }

    /**
     * Set the minimum stake for an address.
     *
     * @return this object
     */
    public RandomAddressBookGenerator setMinimumStake(final long minimumStake) {
        this.minimumStake = minimumStake;
        return this;
    }

    /**
     * Set the maximum stake for an address.
     *
     * @return this object
     */
    public RandomAddressBookGenerator setMaximumStake(final long maximumStake) {
        this.maximumStake = maximumStake;
        return this;
    }

    /**
     * Provide a method that is used to determine the stake of each node. Overrides all other stake generation
     * settings if set.
     *
     * @return this object
     */
    public RandomAddressBookGenerator setCustomStakeGenerator(final LongUnaryOperator customStakeGenerator) {
        this.customStakeGenerator = customStakeGenerator;
        return this;
    }

    /**
     * Set the strategy used for deciding distribution of stake.
     *
     * @return this object
     */
    public RandomAddressBookGenerator setStakeDistributionStrategy(
            final StakeDistributionStrategy stakeDistributionStrategy) {

        this.stakeDistributionStrategy = stakeDistributionStrategy;
        return this;
    }

    /**
     * Set the next node ID that may be used to generate a random address. This node ID may be skipped if gaps are
     * permitted.
     *
     * @param nodeId
     * 		the next node ID that is considered when generating a random address
     * @return this object
     */
    public RandomAddressBookGenerator setNextPossibleNodeId(final long nodeId) {
        this.previousNodeId = nodeId - 1;
        return this;
    }
}
