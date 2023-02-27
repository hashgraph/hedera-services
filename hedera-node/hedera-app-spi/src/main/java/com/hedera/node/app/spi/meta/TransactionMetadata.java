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

package com.hedera.node.app.spi.meta;

import static java.util.Objects.requireNonNull;

import com.hedera.node.app.spi.key.HederaKey;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.List;
import java.util.Set;

/**
 * Metadata collected when transactions are handled as part of "pre-handle". This happens with
 * multiple background threads. Any state read or computed as part of this pre-handle, including any
 * errors, are captured in the TransactionMetadata. This is then made available to the transaction
 * during the "handle" phase as part of the HandleContext.
 *
 * @param txnBody Transaction that is being pre-handled
 * @param payer payer for the transaction
 * @param status {@link ResponseCodeEnum} status of the transaction
 * @param payerKey payer key required to sign the transaction. It is null if payer is missing
 * @param requiredNonPayerKeys list of keys that are required to sign the transaction, in addition
 *     to payer key
 * @param readKeys the keys that were read during pre-handle
 */
public record TransactionMetadata(
        @Nullable TransactionBody txnBody,
        @Nullable AccountID payer,
        @NonNull ResponseCodeEnum status,
        @Nullable HederaKey payerKey,
        @NonNull List<HederaKey> requiredNonPayerKeys,
        @Nullable Object handlerMetadata,
        @NonNull List<ReadKeys> readKeys) {

    public TransactionMetadata {
        requireNonNull(status);
        requireNonNull(requiredNonPayerKeys);
        requireNonNull(readKeys);
    }

    public TransactionMetadata(@NonNull final PreHandleContext context, @NonNull final List<ReadKeys> readKeys) {
        this(
                requireNonNull(context).getTxn(),
                context.getPayer(),
                context.getStatus(),
                context.getPayerKey(),
                context.getRequiredNonPayerKeys(),
                context.getHandlerMetadata(),
                readKeys);
    }

    public TransactionMetadata(
            @Nullable final TransactionBody txBody,
            @Nullable final AccountID payerID,
            @NonNull final ResponseCodeEnum status) {
        this(txBody, payerID, status, null, List.of(), null, List.of());
    }

    /**
     * Checks the failure by validating the status is not {@link ResponseCodeEnum OK}
     *
     * @return returns true if status is not OK
     */
    public boolean failed() {
        return status != ResponseCodeEnum.OK;
    }

    /**
     * An entry of read keys for a single {@link com.hedera.node.app.spi.state.ReadableKVState}
     *
     * <p>Each entry in the list consists of the {@code statesKey} (which identifies the {@link
     * com.hedera.node.app.spi.state.ReadableStates}), the {@code stateKey} (which identifies the
     * {@link com.hedera.node.app.spi.state.ReadableKVState}, and the {@link Set} of keys, that were
     * read.
     *
     * @param statesKey index that identifies the {@link
     *     com.hedera.node.app.spi.state.ReadableStates}
     * @param stateKey index that identifies the {@link
     *     com.hedera.node.app.spi.state.ReadableKVState}
     * @param readKeys {@link Set} of all keys that were read
     */
    public record ReadKeys(
            @NonNull String statesKey, @NonNull String stateKey, @NonNull Set<? extends Comparable<?>> readKeys) {
        public ReadKeys {
            requireNonNull(statesKey);
            requireNonNull(stateKey);
            requireNonNull(readKeys);
        }
    }
}
