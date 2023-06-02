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

package com.swirlds.platform.test.utils;

import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.system.BasicSoftwareVersion;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.events.BaseEventHashedData;
import com.swirlds.common.system.events.BaseEventUnhashedData;
import com.swirlds.common.system.events.ConsensusData;
import com.swirlds.common.system.transaction.internal.SwirldTransaction;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.internal.EventImpl;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Function;
import java.util.random.RandomGenerator;

public final class EqualsVerifier {

    // Do not instantiate
    private EqualsVerifier() {}

    private static EventImpl randomEventImpl(
            final RandomGenerator r, final EventImpl selfParent, final EventImpl otherParent) {
        return new EventImpl(randomGossipEvent(r), randomConsensusData(r), selfParent, otherParent);
    }

    public static EventImpl randomEventImpl(final RandomGenerator r) {
        return randomEventImpl(r, randomEventImpl(r, null, null), randomEventImpl(r, null, null));
    }

    public static GossipEvent randomGossipEvent(final RandomGenerator r) {
        final GossipEvent data = new GossipEvent(randomBaseEventHashedData(r), randomBaseEventUnhashedData(r));
        data.buildDescriptor();
        return data;
    }

    public static ConsensusData randomConsensusData(final RandomGenerator r) {
        final ConsensusData data = new ConsensusData();
        final long DELTA = 1L << 32;
        final long roundCreated = r.nextLong(Long.MAX_VALUE - DELTA);
        final long roundReceived = roundCreated + r.nextLong(DELTA);
        data.setRoundReceived(roundReceived);
        data.setConsensusOrder(r.nextLong(Long.MAX_VALUE));
        data.setConsensusTimestamp(randomInstant(r));
        return data;
    }

    public static BaseEventHashedData randomBaseEventHashedData(final RandomGenerator r) {
        final int NUM_TRANSACTIONS = 10;
        final SwirldTransaction[] transactions = new SwirldTransaction[NUM_TRANSACTIONS];
        for (int i = 0; i < transactions.length; ++i) {
            transactions[i] = randomSwirldTransaction(r);
        }

        final BaseEventHashedData data = new BaseEventHashedData(
                new BasicSoftwareVersion(1),
                new NodeId(r.nextLong(Long.MAX_VALUE)),
                r.nextLong(Long.MAX_VALUE),
                r.nextLong(Long.MAX_VALUE),
                randomHash(r),
                randomHash(r),
                randomInstant(r),
                transactions);
        data.setHash(randomHash(r));
        return data;
    }

    public static SwirldTransaction randomSwirldTransaction(final RandomGenerator r) {
        final int SIZE = 128;
        byte[] contents = new byte[SIZE];
        r.nextBytes(contents);
        return new SwirldTransaction(contents);
    }

    public static BaseEventUnhashedData randomBaseEventUnhashedData(final RandomGenerator r) {
        final int SIZE = 48;
        byte[] value = new byte[SIZE];
        r.nextBytes(value);
        return new BaseEventUnhashedData(new NodeId(r.nextLong(Long.MAX_VALUE)), value);
    }

    public static Hash randomHash(final RandomGenerator r) {
        final int SIZE = 48;
        byte[] value = new byte[SIZE];
        r.nextBytes(value);
        return new Hash(value, DigestType.SHA_384);
    }

    public static Instant randomInstant(final RandomGenerator r) {
        final long MAX_MILLIS = 1L << 42;
        return Instant.ofEpochMilli(r.nextLong(MAX_MILLIS));
    }

    private static final Random random = new Random();

    private static <R> List<R> generateObjects(final Function<RandomGenerator, R> supplier, final long[] seeds) {
        final ArrayList<R> objects = new ArrayList<>();
        for (final long seed : seeds) {
            random.setSeed(seed);
            objects.add(supplier.apply(random));
        }
        return objects;
    }

    /**
     * Verifies equals() and hashCode()
     *
     * @param original
     * 		an instance of type R
     * @param copy
     * 		an equal instance of type R
     * @param other
     * 		a different instance of type R
     * @param <R>
     * 		arbitrary type
     * @return true if all checks pass
     */
    private static <R> boolean verifyEqualsHashCode(final R original, final R copy, final R other) { // NOSONAR

        // Not equal null
        if (original.equals(null)) { // NOSONAR
            return false;
        }

        // Reflexive
        if (!original.equals(original)) { // NOSONAR
            return false;
        }

        // Symmetric
        if (!original.equals(copy)) {
            return false;
        }
        if (!copy.equals(original)) { // NOSONAR
            return false;
        }

        // Falsifiable
        if (original.equals(other)) {
            return false;
        }

        // Consistent
        if (copy.equals(other)) { // NOSONAR
            return false;
        }

        // Stable
        if (!original.equals(copy)) { // NOSONAR
            return false;
        }

        /*
         * hashCode() tests
         */

        // Consistent with equals()
        if (original.hashCode() != copy.hashCode()) {
            return false;
        }

        // Random instance hash codes shouldn't match
        if (original.hashCode() == other.hashCode()) { // NOSONAR
            return false;
        }

        return true;
    }

    /**
     * Verifies if equals()/hashCode() implemented by type R fulfill the contracts
     *
     * @param supplier
     * 		predefined supplier of random instances of type R
     * @param <R>
     * 		arbitrary type
     * @return true if all checks pass
     */
    public static <R> boolean verify(final Function<RandomGenerator, R> supplier) {
        final List<R> list = generateObjects(supplier, new long[] {1, 1, 2}); // NOSONAR
        return verifyEqualsHashCode(list.get(0), list.get(1), list.get(2)); // NOSONAR
    }

    /**
     * Verifies compareTo()
     *
     * @param original
     * 		an instance of type R
     * @param copy
     * 		an equal instance of type R
     * @param other
     * 		a different instance of type R
     * @param <R>
     * 		arbitrary type implementing Comparable
     * @return true if all checks pass
     */
    private static <R extends Comparable<R>> boolean verifyCompareTo(
            final R original, final R copy, final R other) { // NOSONAR
        // Reflexive
        if (original.compareTo(original) != 0) {
            return false;
        }

        // Consistent with equals()
        if (original.compareTo(copy) != 0) {
            return false;
        }

        // Symmetrical equality
        if (copy.compareTo(original) != 0) {
            return false;
        }

        // Asymmetrical inequality
        if (original.compareTo(other) * other.compareTo(original) >= 0) { // NOSONAR
            return false;
        }

        return true;
    }

    /**
     * Verifies if equals()/hashCode()/compareTo() implemented by type R fulfill the contracts
     *
     * @param supplier
     * 		predefined supplier of random instances of type R
     * @param <R>
     * 		arbitrary type implementing Comparable
     * @return true if all checks pass
     */
    public static <R extends Comparable<R>> boolean verifyComparable(final Function<RandomGenerator, R> supplier) {
        final List<R> list = generateObjects(supplier, new long[] {1, 1, 2}); // NOSONAR
        return verifyEqualsHashCode(list.get(0), list.get(1), list.get(2)) // NOSONAR
                && verifyCompareTo(list.get(0), list.get(1), list.get(2)); // NOSONAR
    }
}
