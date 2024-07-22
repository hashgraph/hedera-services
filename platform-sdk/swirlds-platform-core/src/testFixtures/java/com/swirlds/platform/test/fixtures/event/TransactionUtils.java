/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.test.fixtures.event;

import static com.hedera.hapi.platform.event.EventPayload.PayloadOneOfType.APPLICATION_PAYLOAD;
import static com.hedera.hapi.platform.event.EventPayload.PayloadOneOfType.STATE_SIGNATURE_PAYLOAD;
import static com.swirlds.common.test.fixtures.RandomUtils.randomHashBytes;
import static com.swirlds.common.test.fixtures.RandomUtils.randomSignatureBytes;

import com.hedera.hapi.platform.event.EventPayload.PayloadOneOfType;
import com.hedera.hapi.platform.event.StateSignaturePayload;
import com.hedera.pbj.runtime.OneOf;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.platform.system.transaction.PayloadWrapper;
import java.nio.ByteBuffer;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import java.util.random.RandomGenerator;

public class TransactionUtils {
    private static final int DEFAULT_TRANSACTION_MIN_SIZE = 10;
    private static final int DEFAULT_TRANSACTION_MAX_SIZE = 100;

    private static final AtomicLong nextLong = new AtomicLong(0);
    private static final Random random = new Random();

    public static PayloadWrapper[] randomSwirldTransactions(final long seed, final int number) {
        return randomSwirldTransactions(new Random(seed), number);
    }

    public static PayloadWrapper[] randomSwirldTransactions(final RandomGenerator random, final int number) {
        final PayloadWrapper[] transactions = new PayloadWrapper[number];
        for (int i = 0; i < transactions.length; i++) {
            transactions[i] = randomSwirldTransaction(random);
        }
        return transactions;
    }

    public static PayloadWrapper[] randomSwirldTransactions(
            final RandomGenerator random,
            final double transactionSizeAverage,
            final double transactionSizeStandardDeviation,
            final double transactionCountAverage,
            final double transactionCountStandardDeviation) {

        final int transactionCount =
                (int) Math.max(0, transactionCountAverage + random.nextGaussian() * transactionCountStandardDeviation);

        final PayloadWrapper[] transactions = new PayloadWrapper[transactionCount];

        for (int index = 0; index < transactionCount; index++) {
            transactions[index] =
                    randomSwirldTransaction(random, transactionSizeAverage, transactionSizeStandardDeviation);
        }

        return transactions;
    }

    public static PayloadWrapper randomSwirldTransaction(final RandomGenerator random) {
        return randomSwirldTransaction(random, DEFAULT_TRANSACTION_MIN_SIZE, DEFAULT_TRANSACTION_MAX_SIZE);
    }

    public static PayloadWrapper randomSwirldTransaction(
            final RandomGenerator random, final int minSize, final int maxSize) {
        final int size = minSize + random.nextInt(maxSize - minSize);
        final byte[] transBytes = new byte[size];
        random.nextBytes(transBytes);
        return new PayloadWrapper(new OneOf<>(APPLICATION_PAYLOAD, Bytes.wrap(transBytes)));
    }

    public static PayloadWrapper randomSwirldTransaction(
            final RandomGenerator random,
            final double transactionSizeAverage,
            final double transactionSizeStandardDeviation) {
        final int transactionSize =
                (int) Math.max(1, transactionSizeAverage + random.nextGaussian() * transactionSizeStandardDeviation);
        final byte[] transBytes = new byte[transactionSize];
        random.nextBytes(transBytes);
        return new PayloadWrapper(new OneOf<>(APPLICATION_PAYLOAD, Bytes.wrap(transBytes)));
    }

    public static OneOf<PayloadOneOfType> incrementingSwirldTransaction() {
        final ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
        buffer.putLong(nextLong.getAndIncrement());
        return new OneOf<>(APPLICATION_PAYLOAD, Bytes.wrap(buffer.array()));
    }

    public static OneOf<PayloadOneOfType> incrementingSystemTransaction() {
        return new OneOf<>(
                STATE_SIGNATURE_PAYLOAD,
                StateSignaturePayload.newBuilder()
                        .round(0)
                        .signature(randomSignatureBytes(random))
                        .hash(randomHashBytes(random))
                        .build());
    }

    public static PayloadWrapper randomStateSignatureTransaction(final RandomGenerator random) {
        final Random rand = new Random(random.nextLong());
        final Bytes signature = randomSignatureBytes(rand);
        final Bytes hash = randomHashBytes(rand);
        final StateSignaturePayload payload = StateSignaturePayload.newBuilder()
                .round(rand.nextLong())
                .signature(signature)
                .hash(hash)
                .build();
        return new PayloadWrapper(new OneOf<>(STATE_SIGNATURE_PAYLOAD, payload));
    }

    public static PayloadWrapper[] randomMixedTransactions(final RandomGenerator random, final int number) {
        final PayloadWrapper[] transactions = new PayloadWrapper[number];
        for (int i = 0; i < transactions.length; i++) {
            transactions[i] =
                    random.nextBoolean() ? randomSwirldTransaction(random) : randomStateSignatureTransaction(random);
        }
        return transactions;
    }
}
