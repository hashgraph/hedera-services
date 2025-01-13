/*
 * Copyright (C) 2024-2025 Hedera Hashgraph, LLC
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

import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Utility methods for migration testing tool transactions
 */
public class TransactionUtils {
    private static final byte APPLICATION_TRANSACTION_MARKER = 1;

    /**
     * Parse a {@link MigrationTestingToolTransaction} from a {@link Bytes}.
     */
    public static @NonNull MigrationTestingToolTransaction parseTransaction(@NonNull final Bytes bytes) {
        // Remove the first byte, which is marker added to distinguish application transactions from system ones in
        // TransactionGenerator
        final Bytes slicedBytes = bytes.slice(1, bytes.length() - 1);
        final SerializableDataInputStream in = new SerializableDataInputStream(slicedBytes.toInputStream());

        try {
            return in.readSerializable(false, MigrationTestingToolTransaction::new);
        } catch (final IOException e) {
            throw new UncheckedIOException("Could not parse transaction kind:%s".formatted(bytes.toHex()), e);
        }
    }

    public static boolean isSystemTransaction(@NonNull final Bytes bytes) {
        return bytes.getByte(0) != APPLICATION_TRANSACTION_MARKER;
    }
}
