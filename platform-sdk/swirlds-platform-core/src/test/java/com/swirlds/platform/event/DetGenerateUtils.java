/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.event;

import static com.swirlds.common.test.fixtures.RandomUtils.randomHash;
import static com.swirlds.common.test.fixtures.RandomUtils.randomSignature;

import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.Signature;
import com.swirlds.common.system.BasicSoftwareVersion;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.events.BaseEventHashedData;
import com.swirlds.common.system.events.BaseEventUnhashedData;
import com.swirlds.common.system.events.ConsensusData;
import com.swirlds.common.system.transaction.Transaction;
import com.swirlds.common.system.transaction.internal.ConsensusTransactionImpl;
import com.swirlds.common.system.transaction.internal.StateSignatureTransaction;
import com.swirlds.common.system.transaction.internal.SwirldTransaction;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * A class that has methods to deterministically generate objects
 */
public abstract class DetGenerateUtils {
    private static final DigestType DEFAULT_HASH_TYPE = DigestType.SHA_384;
    private static final long DEFAULT_MAX_EPOCH = 1583243881;
    private static final int DEFAULT_TRANSACTION_NUMBER = 10;
    private static final int DEFAULT_TRANSACTION_MAX_SIZE = 100;
    private static final int DEFAULT_SIGNATURE_SIZE = 384;

    public static BaseEventHashedData generateBaseEventHashedData(final Random random) {
        return new BaseEventHashedData(
                new BasicSoftwareVersion(1),
                new NodeId(nextLong(random, 0)), // creatorId, must be positive
                nextLong(random, 0), // selfParentGen, must be positive
                nextLong(random, 0), // otherParentGen, must be positive
                generateRandomHash(random, DEFAULT_HASH_TYPE), // selfParentHash
                generateRandomHash(random, DEFAULT_HASH_TYPE), // otherParentHash
                generateRandomInstant(random, DEFAULT_MAX_EPOCH), // timeCreated
                generateTransactions(DEFAULT_TRANSACTION_NUMBER, DEFAULT_TRANSACTION_MAX_SIZE, random)
                        .toArray(new ConsensusTransactionImpl[0])); // transactions
    }

    public static BaseEventUnhashedData generateBaseEventUnhashedData(final Random random) {
        return new BaseEventUnhashedData(
                new NodeId(nextLong(random, 0)), // otherId, must be positive
                generateRandomByteArray(random, DEFAULT_SIGNATURE_SIZE)); // signature
    }

    public static ConsensusData generateConsensusEventData(final Random random) {
        final ConsensusData data = new ConsensusData();

        // isWitness & isFamous are no longer part of ConsensusEvent. random.nextBoolean() have been left here so that
        // an event would be the same given the same seed.
        random.nextBoolean();
        random.nextBoolean();

        data.setConsensusTimestamp(generateRandomInstant(random, DEFAULT_MAX_EPOCH));
        data.setConsensusOrder(nextLong(random, 0));

        return data;
    }

    /**
     * Randomly generate a list of transaction object
     *
     * @param number
     * 		how many transaction to generate
     * @param maxSize
     * 		maxiumyum payload size a transaction could have
     * @param random
     * 		random seed generator
     * @return a list of transaction objects
     *
     * 		Note: This routine is copied directly in
     * 		./swirlds-unit-tests/common/swirlds-common-test/src/test/java/com/swirlds/common/test/TransactionTest.java.
     * 		Please keep the two versions in sync.
     */
    public static List<Transaction> generateTransactions(final int number, final int maxSize, final Random random) {
        final List<Transaction> list = new ArrayList<>(number);
        for (int i = 0; i < number; i++) {
            final int size = Math.max(1, random.nextInt(maxSize));
            final byte[] bytes = new byte[size];
            random.nextBytes(bytes);
            final boolean system = random.nextBoolean();
            if (system) {
                final Signature signature = randomSignature(random);
                final Hash hash = randomHash(random);
                list.add(new StateSignatureTransaction(random.nextLong(), signature, hash));
            } else {
                list.add(new SwirldTransaction(bytes));
            }
        }
        return list;
    }

    public static Hash generateRandomHash(final Random random, final DigestType type) {
        return new Hash(generateRandomByteArray(random, type.digestLength()), type);
    }

    public static Instant generateRandomInstant(final Random random, final long maxEpoch) {
        return Instant.ofEpochSecond(nextLong(random, 0, maxEpoch - 1), nextLong(random, 0, 1_000_000_000));
    }

    public static long nextLong(final Random random, final long min) {
        return nextLong(random, min, Long.MAX_VALUE);
    }

    public static long nextLong(final Random random, final long min, final long max) {
        return random.longs(1, min, max).findFirst().orElseThrow();
    }

    public static byte[] generateRandomByteArray(final Random random, final int size) {
        final byte[] bytes = new byte[size];
        random.nextBytes(bytes);
        return bytes;
    }
}
