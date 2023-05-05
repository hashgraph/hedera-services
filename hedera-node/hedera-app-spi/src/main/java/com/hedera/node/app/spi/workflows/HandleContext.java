/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.spi.workflows;

import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.hapi.node.transaction.TransactionReceipt;
import com.hedera.node.app.spi.config.GlobalDynamicConfig;
import com.hedera.node.app.spi.signatures.SignatureVerification;
import com.hedera.node.app.spi.validation.AttributeValidator;
import com.hedera.node.app.spi.validation.ExpiryValidator;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;

/**
 * Bundles up the in-handle application context required by {@link TransactionHandler} implementations.
 *
 * <p>At present, only supplies the context needed for Consensus Service handlers in the
 * limited form described by https://github.com/hashgraph/hedera-services/issues/4945.
 */
public interface HandleContext {
    /**
     * Returns the current consensus time.
     *
     * @return the current consensus time
     */
    @NonNull
    Instant consensusNow();

    /**
     * Returns the {@link TransactionBody}
     *
     * @return the {@code TransactionBody}
     */
    @NonNull
    TransactionBody body();

    /**
     * Returns the {@link GlobalDynamicConfig} for the node.
     *
     * <p>This is a temporary solution. In the final version, {@link com.swirlds.config.api.Configuration} will be
     * provided.
     *
     * @return the {@code GlobalDynamicConfig}
     */
    @NonNull
    GlobalDynamicConfig config();

    /**
     * Returns the next entity number, for use by handlers that create entities.
     *
     * <p>If this method is called after a child transaction was dispatched, which is subsequently rolled back,
     * the counter will be rolled back, too. Consequently, the provided number must not be used anymore in this case,
     * because it will be reused.
     *
     * @return the next entity number
     */
    long newEntityNum();

    /**
     * Returns the validator for attributes of entities created or updated by handlers.
     *
     * @return the validator for attributes
     */
    @NonNull
    AttributeValidator attributeValidator();

    /**
     * Returns the validator for expiry metadata (both explicit expiration times and
     * auto-renew configuration) of entities created or updated by handlers.
     *
     * @return the validator for expiry metadata
     */
    @NonNull
    ExpiryValidator expiryValidator();

    /**
     * Gets the {@link SignatureVerification} for the given key. If this key was not provided during pre-handle, then
     * there will be no corresponding {@link SignatureVerification}. If the key was provided during pre-handle, then the
     * corresponding {@link SignatureVerification} will be returned with the result of that verification operation.
     *
     * @param key the key to get the verification for
     * @return the verification for the given key, or {@code null} if no such key was provided during pre-handle
     */
    @Nullable
    SignatureVerification verificationFor(@NonNull Key key);

    /**
     * Get a readable store given the store's interface. This gives read-only access to the store.
     *
     * @param storeInterface The store interface to find and create a store for
     * @return An implementation of the provided store interface
     * @param <T> Interface class for a Store
     * @throws IllegalArgumentException if the storeInterface class provided is unknown to the app
     * @throws NullPointerException if {@code storeInterface} is {@code null}
     */
    @NonNull
    <T> T readableStore(@NonNull Class<T> storeInterface);

    /**
     * Return a writable store given the store's interface. This gives write access to the store.
     *
     * <p>This method is limited to
     *
     * @param storeInterface The store interface to find and create a store for
     * @param <T> Interface class for a Store
     * @return An implementation of the provided store interface
     * @throws IllegalArgumentException if the storeInterface class provided is unknown to the app
     * @throws IllegalStateException if the store is not accessible from the current transaction
     * @throws NullPointerException if {@code storeInterface} is {@code null}
     */
    @NonNull
    <T> T writableStore(@NonNull Class<T> storeInterface);

    /**
     * Returns a record builder for the given record builder subtype.
     *
     * @param singleTransactionRecordBuilderClass the record type
     * @param <T> the record type
     * @return a builder for the given record type
     * @throws NullPointerException if {@code singleTransactionRecordBuilderClass} is {@code null}
     * @throws IllegalArgumentException if the record builder type is unknown to the app
     */
    @NonNull
    <T> T recordBuilder(@NonNull Class<T> singleTransactionRecordBuilderClass);

    /**
     * Dispatches an independent (top-level) transaction, that precedes the current transaction.
     *
     * <p>A top-level transaction is independent of any other transaction. If it is successful, the state changes are
     * automatically committed. If it fails, any eventual state changes are automatically rolled back.
     *
     * <p>This method can only be called as long as no state changes have been introduced by the current transaction
     * (either by storing state or by calling a child transaction).
     *
     * @param txBody the {@link TransactionBody} of the transaction to dispatch
     * @return the {@link TransactionResult} of the transaction, if successful
     * @throws NullPointerException if {@code txBody} is {@code null}
     * @throws HandleException if the transaction fails
     * @throws IllegalStateException if the current transaction has already introduced state changes
     */
    @NonNull
    TransactionResult dispatchPrecedingTransaction(@NonNull TransactionBody txBody) throws HandleException;

    /**
     * Dispatches a child transaction.
     *
     * <p>A child transaction depends on the current transaction. That means if the current transaction fails,
     * a child transaction is automatically rolled back. The state changes introduced by a child transaction
     * are automatically committed together with the parent transaction.
     *
     * <p>A child transaction will run with the current state. It will see all state changes introduced by the current
     * transaction or preceding child transactions. If successful, a new entry will be added to the
     * {@link TransactionStack}. This enables the current transaction to commit or roll back the state changes.
     * Please be aware that any state changes introduced by storing data in one of the stores after calling a child
     * transaction will also be rolled back if the child transaction is rolled back.
     *
     * @param txBody the {@link TransactionBody} of the child transaction to dispatch
     * @return the {@link TransactionResult} of the child transaction, if successful
     * @throws NullPointerException if {@code txBody} is {@code null}
     * @throws HandleException if the transaction fails
     */
    @NonNull
    TransactionResult dispatchChildTransaction(@NonNull TransactionBody txBody) throws HandleException;

    /**
     * Returns the current {@link TransactionStack}.
     *
     * @return the current {@code TransactionStack}
     */
    @NonNull
    TransactionStack transactionStack();

    /**
     * A stack of transactions.
     *
     * <p>Every time a child transaction is dispatched and executed successfully, a new entry is added to the
     * transaction stack. The transaction stack allows to rollback an arbitrary number of transactions. Please
     * be aware that rolling back a child transaction will also rollbacks all state changes that were introduced
     * afterward.
     */
    interface TransactionStack {
        /**
         * Returns the {@link TransactionResult} of the last child transaction.
         *
         * @return the {@code TransactionResult} of the last child transaction
         * @throws IllegalStateException if the transaction stack is empty
         */
        @NonNull
        TransactionResult peek();

        /**
         * Rolls back the last child transaction.
         *
         * @throws IllegalStateException if the transaction stack is empty
         */
        default void rollback() {
            rollback(1);
        }

        /**
         * Rolls back the last {@code depth} child transactions.
         *
         * @param depth the number of child transactions to roll back
         * @throws IllegalArgumentException if {@code depth} is less than {@code 1}
         * @throws IllegalStateException if the transaction stack contains fewer elements than {@code depth}
         */
        void rollback(int depth);
    }

    /**
     * The result of a dispatched and successfully executed transaction.
     */
    interface TransactionResult {
        /**
         * Returns the {@link TransactionBody} of the transaction.
         *
         * @return the {@code TransactionBody} of the transaction
         */
        @NonNull
        TransactionBody txBody();

        /**
         * Returns the consensus time of the transaction.
         *
         * @return the consensus time of the transaction
         */
        @NonNull
        Instant consensusTime();

        /**
         * Returns the {@link TransactionReceipt} of the transaction.
         *
         * @return the {@code TransactionReceipt} of the transaction
         */
        TransactionReceipt receipt();
    }
}
