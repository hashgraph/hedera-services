/*
 * Copyright (C) 2022-2025 Hedera Hashgraph, LLC
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

package com.swirlds.demo.stress;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Random;

/**
 * Provides pre-generated random transactions.
 */
final class TransactionPool {

    public static final byte APPLICATION_TRANSACTION_MARKER = 1;

    /**
     * the array of transactions
     */
    private final byte[][] transactions;

    /**
     * the standard psuedo-random number generator
     */
    private final Random random;

    /**
     * Constructs a TransactionPool instance with a fixed pool size, fixed transaction size.
     *
     * @param poolSize        the number of pre-generated transactions
     * @param transactionSize the size of randomly generated transaction
     * @throws IllegalArgumentException if the {@code poolSize} or the {@code transactionSize} parameters are less than
     *                                  one (1)
     */
    TransactionPool(final int poolSize, final int transactionSize) {
        if (poolSize < 1) {
            throw new IllegalArgumentException("poolSize");
        }

        if (transactionSize < 1) {
            throw new IllegalArgumentException("transactionSize");
        }

        this.random = new Random();

        // the fixed size of each transaction
        this.transactions = new byte[poolSize][];

        for (int i = 0; i < transactions.length; i++) {
            final byte[] data = new byte[transactionSize];
            random.nextBytes(data);
            // Add byte with value of 1 as a marker to indicate the start of an application transaction. This is used
            // to later differentiate between application transactions and system transactions.
            data[0] = APPLICATION_TRANSACTION_MARKER;
            transactions[i] = data;
        }
    }

    /**
     * Retrieves a random transaction from the pool of pre-generated transactions.
     *
     * @return a random transaction from the pool
     */
    @NonNull
    byte[] transaction() {
        return transactions[random.nextInt(transactions.length)];
    }
}
