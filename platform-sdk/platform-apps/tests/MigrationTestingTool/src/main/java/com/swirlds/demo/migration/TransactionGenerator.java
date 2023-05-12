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

package com.swirlds.demo.migration;

import com.swirlds.common.io.streams.SerializableDataOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.security.SignatureException;
import java.util.Random;

/**
 * Generates and executes transactions for the migration testing tool.
 */
public class TransactionGenerator {

    private final Random random;

    /**
     * Create a new source of transactions.
     *
     * @param seed
     * 		used to seed the random number generator
     */
    public TransactionGenerator(final long seed) {
        this.random = new Random(seed);
    }

    /**
     * Generate a new transaction.
     */
    public byte[] generateTransaction() throws SignatureException {

        final double choice = random.nextDouble();
        final MigrationTestingToolTransaction.TransactionType type;
        if (choice < 0.5) {
            type = MigrationTestingToolTransaction.TransactionType.MERKLE_MAP;
        } else {
            type = MigrationTestingToolTransaction.TransactionType.VIRTUAL_MAP;
        }

        final MigrationTestingToolTransaction transaction =
                new MigrationTestingToolTransaction(type, random.nextLong());

        final ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
        final SerializableDataOutputStream out = new SerializableDataOutputStream(byteOut);
        try {
            out.writeSerializable(transaction, false);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }

        return byteOut.toByteArray();
    }
}
