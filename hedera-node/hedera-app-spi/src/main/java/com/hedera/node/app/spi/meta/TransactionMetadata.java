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
import java.util.Map;
import java.util.Set;

/**
 * Metadata collected when transactions are handled as part of "pre-handle". This happens with
 * multiple background threads. Any state read or computed as part of this pre-handle, including any
 * errors, are captured in the TransactionMetadata. This is then made available to the transaction
 * during the "handle" phase as part of the HandleContext.
 */
public interface TransactionMetadata {
    /**
     * Checks the failure by validating the status is not {@link ResponseCodeEnum OK}
     *
     * @return returns true if status is not OK
     */
    default boolean failed() {
        return !status().equals(ResponseCodeEnum.OK);
    }

    /**
     * Returns the status of the transaction.
     *
     * @return the status of the transaction.
     */
    @NonNull
    ResponseCodeEnum status();

    /**
     * Transaction that is being pre-handled
     *
     * @return transaction that is being pre-handled
     */
    @Nullable
    TransactionBody txnBody();

    /**
     * All the keys required for validation signing requirements in pre-handle. This list includes
     * payer key as well.
     *
     * @return keys needed for validating signing requirements
     */
    @NonNull
    List<HederaKey> requiredNonPayerKeys();

    /**
     * Payer for the transaction
     *
     * @return payer for the transaction
     */
    @Nullable
    AccountID payer();

    /**
     * Transaction payer's key
     *
     * @return payer key to sign the transaction
     */
    @Nullable
    HederaKey payerKey();

    /**
     * A {@link Map} of all read keys.
     *
     * <p>The map contains a {@code Map} for each {@link
     * com.hedera.node.app.spi.state.ReadableStates} that was created during pre-handle, indexed by
     * their {@code statesKey}.
     *
     * <p>The contained {@code Maps} keep a {@link Set} of all read keys for each {@link
     * com.hedera.node.app.spi.state.ReadableKVState}, indexed by the {@code stateKey}.
     *
     * @return a data structure with all keys that were read during pre-handle
     */
    @NonNull
    List<ReadKeys> readKeys();

    record ReadKeys(
            @NonNull String statesKey,
            @NonNull String stateKey,
            @NonNull Set<? extends Comparable<?>> readKeys) {
        public ReadKeys {
            requireNonNull(statesKey);
            requireNonNull(stateKey);
            requireNonNull(readKeys);
        }
    }
}
