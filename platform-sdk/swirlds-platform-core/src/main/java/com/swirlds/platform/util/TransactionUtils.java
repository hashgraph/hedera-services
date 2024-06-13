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

import com.hedera.hapi.platform.event.EventPayload.PayloadOneOfType;
import com.hedera.hapi.platform.event.StateSignaturePayload;
import com.hedera.pbj.runtime.OneOf;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Utility class for handling PJB transactions.
 */
public final class TransactionUtils {
    private TransactionUtils() {}

    /**
     * Get the size of a transaction.
     *
     * @param transaction the transaction to get the size of
     * @return the size of the transaction
     */
    public static long getTransactionSize(@NonNull final OneOf<PayloadOneOfType> transaction) {
        if (PayloadOneOfType.APPLICATION_PAYLOAD.equals(transaction.kind())) {
            return Integer.BYTES // add the the size of array length field
                    + ((Bytes) transaction.as()).length(); // add the size of the array
        } else if (PayloadOneOfType.STATE_SIGNATURE_PAYLOAD.equals(transaction.kind())) {
            final StateSignaturePayload stateSignaturePayload = transaction.as();
            return Long.BYTES // round
                    + Integer.BYTES // signature array length
                    + stateSignaturePayload.signature().length()
                    + Integer.BYTES // hash array length
                    + stateSignaturePayload.hash().length()
                    + Integer.BYTES; // epochHash, always null, which is SerializableStreamConstants.NULL_VERSION
        } else {
            throw new IllegalArgumentException("Unknown transaction type: " + transaction.kind());
        }
    }

    /**
     * Check if a transaction is a system transaction.
     *
     * @param transaction the transaction to check
     * @return {@code true} if the transaction is a system transaction, {@code false} otherwise
     */
    public static boolean isSystemTransaction(@NonNull final OneOf<PayloadOneOfType> transaction) {
        return !PayloadOneOfType.APPLICATION_PAYLOAD.equals(transaction.kind());
    }
}
