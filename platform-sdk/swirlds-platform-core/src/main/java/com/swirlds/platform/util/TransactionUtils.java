/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

import static com.swirlds.common.io.streams.SerializableStreamConstants.BOOLEAN_BYTES;
import static com.swirlds.common.io.streams.SerializableStreamConstants.CLASS_ID_BYTES;
import static com.swirlds.common.io.streams.SerializableStreamConstants.VERSION_BYTES;

import com.hedera.hapi.platform.event.EventTransaction;
import com.hedera.hapi.platform.event.EventTransaction.TransactionOneOfType;
import com.hedera.hapi.platform.event.StateSignatureTransaction;
import com.hedera.pbj.runtime.OneOf;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;

/**
 * Utility class for handling PJB transactions.
 * <p>
 * <b>IMPORTANT:</b> This class is subject to deletion in the future. It's only needed for the transition period
 * from old serialization to PBJ serialization.
 */
public final class TransactionUtils {
    private TransactionUtils() {}

    /**
     * Get the size of a list of transactions.
     *
     * @param transactions the transactions to get the size of
     * @return the size of the transactions
     */
    public static int getLegacyObjectSize(@NonNull final List<EventTransaction> transactions) {
        int totalByteLength = Integer.BYTES; // length of array size
        if (transactions.isEmpty()) {
            return totalByteLength;
        }

        totalByteLength += BOOLEAN_BYTES;

        for (final EventTransaction transaction : transactions) {
            totalByteLength += CLASS_ID_BYTES;
            totalByteLength += VERSION_BYTES;
            totalByteLength += getLegacyTransactionSize(transaction.transaction());
        }

        return totalByteLength;
    }

    /**
     * Get the size of a transaction.<br>
     * This is a convenience method that delegates to {@link #getLegacyTransactionSize(OneOf)}.
     *
     * @param transaction the transaction to get the size of
     * @return the size of the transaction
     */
    public static int getLegacyTransactionSize(@NonNull final EventTransaction transaction) {
        return getLegacyTransactionSize(transaction.transaction());
    }

    /**
     * Get the size of a transaction.
     *
     * @param transaction the transaction to get the size of
     * @return the size of the transaction
     */
    public static int getLegacyTransactionSize(@NonNull final OneOf<TransactionOneOfType> transaction) {
        if (TransactionOneOfType.APPLICATION_TRANSACTION.equals(transaction.kind())) {
            return Integer.BYTES // add the the size of array length field
                    + (int) ((Bytes) transaction.as()).length(); // add the size of the array
        } else if (TransactionOneOfType.STATE_SIGNATURE_TRANSACTION.equals(transaction.kind())) {
            final StateSignatureTransaction stateSignatureTransaction = transaction.as();
            return Long.BYTES // round
                    + Integer.BYTES // signature array length
                    + (int) stateSignatureTransaction.signature().length()
                    + Integer.BYTES // hash array length
                    + (int) stateSignatureTransaction.hash().length()
                    + Integer.BYTES; // epochHash, always null, which is SerializableStreamConstants.NULL_VERSION
        } else {
            throw new IllegalArgumentException("Unknown transaction type: " + transaction.kind());
        }
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

    /**
     * Check if a transaction is a TssMessageTransaction.<br>
     * This is a convenience method that delegates to {@link #isTssMessageTransaction(OneOf)}.
     *
     * @param transaction the transaction to check
     * @return {@code true} if the transaction is a TssMessageTransaction, {@code false} otherwise
     */
    public static boolean isTssMessageTransaction(@NonNull final EventTransaction transaction) {
        return isTssMessageTransaction(transaction.transaction());
    }

    /**
     * Check if a transaction is a TssMessageTransaction.
     *
     * @param transaction the transaction to check
     * @return {@code true} if the transaction is a TssMessageTransaction, {@code false} otherwise
     */
    public static boolean isTssMessageTransaction(@NonNull final OneOf<TransactionOneOfType> transaction) {
        return transaction.kind().equals(TransactionOneOfType.TSS_MESSAGE_TRANSACTION);
    }

    /**
     * Check if a transaction is a TssVoteTransaction.<br>
     * This is a convenience method that delegates to {@link #isTssVoteTransaction(OneOf)}.
     *
     * @param transaction the transaction to check
     * @return {@code true} if the transaction is a TssVoteTransaction, {@code false} otherwise
     */
    public static boolean isTssVoteTransaction(@NonNull final EventTransaction transaction) {
        return isTssVoteTransaction(transaction.transaction());
    }

    /**
     * Check if a transaction is a TssVoteTransaction.
     *
     * @param transaction the transaction to check
     * @return {@code true} if the transaction is a TssVoteTransaction, {@code false} otherwise
     */
    public static boolean isTssVoteTransaction(@NonNull final OneOf<TransactionOneOfType> transaction) {
        return transaction.kind().equals(TransactionOneOfType.TSS_VOTE_TRANSACTION);
    }
}
