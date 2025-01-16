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

package com.swirlds.platform.util;

import com.hedera.hapi.platform.event.EventTransaction;
import com.hedera.hapi.platform.event.EventTransaction.TransactionOneOfType;
import com.hedera.pbj.runtime.OneOf;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Utility class for handling PJB transactions.
 * <p>
 * <b>IMPORTANT:</b> This class is subject to deletion in the future. It's only needed for the transition period
 * from old serialization to PBJ serialization.
 */
public final class TransactionUtils {
    private TransactionUtils() {}

    /**
     * Get the size of a transaction.<br>
     * This is a convenience method that delegates to {@link #getLegacyTransactionSize(OneOf)}.
     *
     * @param transaction the transaction to get the size of
     * @return the size of the transaction
     */
    //    public static int getLegacyTransactionSize(@NonNull final EventTransaction transaction) {
    //        return getLegacyTransactionSize(transaction.transaction());
    //    }

    /**
     * Get the size of a transaction.
     *
     * @param transaction the transaction to get the size of
     * @return the size of the transaction
     */
    public static int getLegacyTransactionSize(@NonNull final Bytes transaction) {
        return Integer.BYTES // add the the size of array length field
                + (int) transaction.length(); // add the size of the array
    }

    /**
     * Check if a transaction is a system transaction.<br>
     * This is a convenience method that delegates to {@link #isSystemTransaction(OneOf)}.
     *
     * @param transaction the transaction to check
     * @return {@code true} if the transaction is a system transaction, {@code false} otherwise
     */
    public static boolean isSystemTransaction(@NonNull final EventTransaction transaction) {
        return isSystemTransaction(transaction.transaction());
    }

    /**
     * Check if a transaction is a system transaction.
     *
     * @param transaction the transaction to check
     * @return {@code true} if the transaction is a system transaction, {@code false} otherwise
     */
    public static boolean isSystemTransaction(@NonNull final OneOf<TransactionOneOfType> transaction) {
        return !TransactionOneOfType.APPLICATION_TRANSACTION.equals(transaction.kind());
    }
}
