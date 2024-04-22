package com.swirlds.demo.migration;

import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.platform.system.transaction.Transaction;
import java.io.IOException;
import java.io.UncheckedIOException;

public class TransactionUtils {
    /**
     * Parse a {@link MigrationTestingToolTransaction} from a {@link Bytes}.
     */
    public static MigrationTestingToolTransaction parseTransaction(final Bytes bytes) {
        final SerializableDataInputStream in = new SerializableDataInputStream(
                bytes.toInputStream());

        try {
            return in.readSerializable(false, MigrationTestingToolTransaction::new);
        } catch (final IOException e) {
            throw new UncheckedIOException(
                    "Could not parse transaction kind:%s".formatted(
                            bytes.toHex()),
                    e);
        }
    }
}
