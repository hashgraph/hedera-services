// SPDX-License-Identifier: Apache-2.0
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
            // Adding additional byte to differentiate application transactions from system ones
            out.write(1);
            out.writeSerializable(transaction, false);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }

        return byteOut.toByteArray();
    }
}
