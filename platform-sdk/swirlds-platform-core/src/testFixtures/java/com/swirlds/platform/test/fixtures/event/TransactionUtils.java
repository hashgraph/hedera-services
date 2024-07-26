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

import static com.swirlds.platform.system.transaction.PayloadWrapperUtils.createAppPayloadWrapper;

import com.swirlds.platform.system.transaction.PayloadWrapper;
import java.util.random.RandomGenerator;

public class TransactionUtils {

    public static PayloadWrapper[] randomApplicationTransactions(
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
                    randomApplicationTransaction(random, transactionSizeAverage, transactionSizeStandardDeviation);
        }

        return transactions;
    }

    public static PayloadWrapper randomApplicationTransaction(
            final RandomGenerator random,
            final double transactionSizeAverage,
            final double transactionSizeStandardDeviation) {
        final int transactionSize =
                (int) Math.max(1, transactionSizeAverage + random.nextGaussian() * transactionSizeStandardDeviation);
        final byte[] transBytes = new byte[transactionSize];
        random.nextBytes(transBytes);
        return createAppPayloadWrapper(transBytes);
    }
}
