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

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.SubType;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.spi.authorization.SystemPrivilege;
import com.hedera.node.app.spi.fees.ExchangeRateInfo;
import com.hedera.node.app.spi.fees.FeeAccumulator;
import com.hedera.node.app.spi.fees.FeeCalculator;
import com.hedera.node.app.spi.info.NetworkInfo;
import com.hedera.node.app.spi.records.BlockRecordInfo;
import com.hedera.node.app.spi.records.RecordCache;
import com.hedera.node.app.spi.signatures.SignatureVerification;
import com.hedera.node.app.spi.validation.AttributeValidator;
import com.hedera.node.app.spi.validation.ExpiryValidator;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.config.api.Configuration;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;
import java.util.function.Predicate;

/**
 * Represents the context of a single {@code handle()}-call.
 * <p>
 * The information and functionality provided by a {@code HandleContext} is valid only for the duration of the call. It
 * can be grouped into five categories:
 * <ul>
 *     <li>Information about the transaction being handled, such as its consensus time, its body, and its category</li>
 *     <li>Configuration data and objects that depend on the current configuration</li>
 *     <li>Verification data, that has been assembled during pre-handle</li>
 *     <li>State related data and the possibility to rollback changes</li>
 *     <li>Data related to the record stream</li>
 *     <li>Functionality to dispatch preceding and child transactions</li>
 * </ul>
 */
@SuppressWarnings("UnusedReturnValue")
public interface HandleContext {

    /**
     * Category of the current transaction.
     */
    enum TransactionCategory {
        /** The original transaction submitted by a user. */
        USER,

        /** An independent, top-level transaction that is executed before the user transaction. */
        PRECEDING,

        /** A child transaction that is executed as part of a user transaction. */
        CHILD
    }

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
     * Gets the payer {@link AccountID}.
     *
     * @return the {@link AccountID} of the payer in this context
     */
    @NonNull
    AccountID payer();

    /**
     * Returns the current {@link Configuration} for the node.
     *
     * @return the {@code Configuration}
     */
    @NonNull
    Configuration configuration();

    /**
     * Returns information on current block and record file
     *
     * @return current BlockRecordInfo
     */
    @NonNull
    BlockRecordInfo blockRecordInfo();

    /**
     * Getter for the payer key
     *
     * @return the payer key
     */
    @Nullable
    Key payerKey();

    /**
     * Returns the Hedera resource prices (in thousandths of a tinycent) for the given {@link SubType} of
     * the given {@link HederaFunctionality}. The contract service needs this information to determine both the
     * gas price and the cost of storing logs (a function of the {@code rbh} price, which may itself vary by
     * contract operation type).
     *
     * @param functionality the {@link HederaFunctionality} of interest
     * @param subType the {@link SubType} of interest
     * @return the corresponding Hedera resource prices
     */
    @NonNull
    FunctionalityResourcePrices resourcePricesFor(
            @NonNull final HederaFunctionality functionality, @NonNull final SubType subType);

    /**
     * Get a calculator for calculating fees for the current transaction, and its {@link SubType}. Most transactions
     * just use {@link SubType#DEFAULT}, but some (such as crypto transfer) need to be more specific.
     *
     * @param subType The {@link SubType} of the transaction.
     * @return The {@link FeeCalculator} to use.
     */
    @NonNull
    FeeCalculator feeCalculator(@NonNull final SubType subType);

    /**
     * Gets a {@link FeeAccumulator} used for collecting fees for the current transaction.
     *
     * @return The {@link FeeAccumulator} to use.
     */
    @NonNull
    FeeAccumulator feeAccumulator();

    /**
     * Gets a {@link ExchangeRateInfo} which provides information about the current exchange rate.
     *
     * @return The {@link ExchangeRateInfo} .
     */
    @NonNull
    ExchangeRateInfo exchangeRateInfo();

    /**
     * Consumes and returns the next entity number, for use by handlers that create entities.
     *
     * <p>If this method is called after a child transaction was dispatched, which is subsequently rolled back,
     * the counter will be rolled back, too. Consequently, the provided number must not be used anymore in this case,
     * because it will be reused.
     *
     * @return the next entity number
     */
    long newEntityNum();

    /**
     * Peeks at the next entity number, for use by handlers that create entities.
     *
     * <p>If this method is called after a child transaction was dispatched, which is subsequently rolled back,
     * the counter will be rolled back, too. Consequently, the provided number must not be used anymore in this case,
     * because it will be reused.
     *
     * @return the next entity number
     */
    long peekAtNewEntityNum();

    /**
     * Returns the validator for attributes of entities created or updated by handlers.
     *
     * @return the validator for attributes
     */
    @NonNull
    AttributeValidator attributeValidator();

    /**
     * Returns the validator for expiry metadata (both explicit expiration times and auto-renew configuration) of
     * entities created or updated by handlers.
     *
     * @return the validator for expiry metadata
     */
    @NonNull
    ExpiryValidator expiryValidator();

    /**
     * Returns all (required and optional) keys of a nested transaction.
     *
     * @param nestedTxn the {@link TransactionBody} which keys are needed
     * @param payerForNested the payer for the nested transaction
     * @return the set of keys
     * @throws PreCheckException If there is a problem with the nested transaction
     */
    @NonNull
    TransactionKeys allKeysForTransaction(@NonNull TransactionBody nestedTxn, @NonNull AccountID payerForNested)
            throws PreCheckException;

    /**
     * Gets the {@link SignatureVerification} for the given key. If this key was not provided during pre-handle, then
     * there will be no corresponding {@link SignatureVerification}. If the key was provided during pre-handle, then the
     * corresponding {@link SignatureVerification} will be returned with the result of that verification operation.
     *
     * <p>The signatures of required keys are guaranteed to be verified. Optional signatures may still be in the
     * process of being verified (and therefore may time out). The timeout can be configured via the configuration
     * {@code hedera.workflow.verificationTimeoutMS}
     *
     * @param key the key to get the verification for
     * @return the verification for the given key
     * @throws NullPointerException if {@code key} is {@code null}
     */
    @NonNull
    SignatureVerification verificationFor(@NonNull Key key);

    /**
     * Gets the {@link SignatureVerification} for the given key. If this key was not provided during pre-handle, then
     * there will be no corresponding {@link SignatureVerification}. If the key was provided during pre-handle, then the
     * corresponding {@link SignatureVerification} will be returned with the result of that verification operation.
     * Additionally, the VerificationAssistant provided may modify the result for "primitive", "Contract ID", or
     * "Delegatable Contract ID" keys, and will be called to observe and reply for each such key as it is processed.
     *
     * <p>The signatures of required keys are guaranteed to be verified. Optional signatures may still be in the
     * process of being verified (and therefore may time out). The timeout can be configured via the configuration
     * {@code hedera.workflow.verificationTimeoutMS}
     *
     * @param key the key to get the verification for
     * @param callback a VerificationAssistant callback function that will observe each "primitive", "Contract ID", or
     * "Delegatable Contract ID" key and return a boolean indicating if the given key should be considered valid.
     * @return the verification for the given key
     */
    @NonNull
    SignatureVerification verificationFor(@NonNull Key key, @NonNull VerificationAssistant callback);

    /**
     * Gets the {@link SignatureVerification} for the given hollow account.
     *
     * <p>The signatures of required accounts are guaranteed to be verified. Optional accounts may still be in the
     * process of being verified (and therefore may time out). The timeout can be configured via the configuration
     * {@code hedera.workflow.verificationTimeoutMS}
     *
     * @param evmAlias The evm alias to lookup verification for.
     * @return the verification for the given hollow account.
     * @throws NullPointerException if {@code evmAlias} is {@code null}
     */
    @NonNull
    SignatureVerification verificationFor(@NonNull final Bytes evmAlias);

    /**
     * Checks whether the payer of the current transaction refers to a superuser.
     *
     * @return {@code true} if the payer is a superuser, otherwise {@code false
     */
    boolean isSuperUser();

    /**
     * Checks whether the current transaction is a privileged transaction and the payer has sufficient rights.
     *
     * @return the {@code SystemPrivilege} of the current transaction
     */
    SystemPrivilege hasPrivilegedAuthorization();

    /** Gets the {@link RecordCache}. */
    @NonNull
    RecordCache recordCache();

    /**
     * Get a readable store given the store's interface. This gives read-only access to the store.
     *
     * @param storeInterface The store interface to find and create a store for
     * @param <T> Interface class for a Store
     * @return An implementation of the provided store interface
     * @throws IllegalArgumentException if the storeInterface class provided is unknown to the app
     * @throws NullPointerException if {@code storeInterface} is {@code null}
     */
    @NonNull
    <T> T readableStore(@NonNull Class<T> storeInterface);

    /**
     * Return a writable store given the store's interface. This gives write access to the store.
     *
     * <p>This method is limited to stores that are part of the transaction's service.
     *
     * @param storeInterface The store interface to find and create a store for
     * @param <T> Interface class for a Store
     * @return An implementation of the provided store interface
     * @throws IllegalArgumentException if the storeInterface class provided is unknown to the app
     * @throws NullPointerException if {@code storeInterface} is {@code null}
     */
    @NonNull
    <T> T writableStore(@NonNull Class<T> storeInterface);

    /**
     * Return a service API given the API's interface. This permits use of another service
     * that doesn't have a corresponding HAPI {@link TransactionBody}.
     *
     * @param apiInterface The API interface to find and create an implementation of
     * @param <T> Interface class for an API
     * @return An implementation of the provided API interface
     * @throws IllegalArgumentException if the apiInterface class provided is unknown to the app
     * @throws NullPointerException if {@code apiInterface} is {@code null}
     */
    @NonNull
    <T> T serviceApi(@NonNull Class<T> apiInterface);

    /**
     * Returns the information about the network this transaction is being handled in.
     *
     * @return the network information
     */
    @NonNull
    NetworkInfo networkInfo();

    /**
     * Returns a record builder for the given record builder subtype.
     *
     * @param recordBuilderClass the record type
     * @param <T> the record type
     * @return a builder for the given record type
     * @throws NullPointerException if {@code recordBuilderClass} is {@code null}
     * @throws IllegalArgumentException if the record builder type is unknown to the app
     */
    @NonNull
    <T> T recordBuilder(@NonNull Class<T> recordBuilderClass);

    /**
     * Dispatches an independent (top-level) transaction, that precedes the current transaction.
     *
     * <p>A top-level transaction is independent of any other transaction. If it is successful, the state changes are
     * automatically committed. If it fails, any eventual state changes are automatically rolled back.
     *
     * <p>This method can only be called my a {@link TransactionCategory#USER}-transaction and only as long as no state
     * changes have been introduced by the user transaction (either by storing state or by calling a child
     * transaction).
     *
     * <p>The provided {@link Predicate} callback will be called to verify simple keys when the child transaction calls
     * any of the {@code verificationFor} methods.
     *
     * @param txBody             the {@link TransactionBody} of the transaction to dispatch
     * @param recordBuilderClass the record builder class of the transaction
     * @param verifier           a {@link Predicate} that will be used to validate primitive keys
     * @param syntheticPayer    the payer of the transaction
     * @return the record builder of the transaction
     * @throws NullPointerException     if {@code txBody} is {@code null}
     * @throws IllegalArgumentException if the transaction is not a {@link TransactionCategory#USER}-transaction or if
     *                                  the record builder type is unknown to the app
     * @throws IllegalStateException    if the current transaction has already introduced state changes
     */
    @NonNull
    <T> T dispatchPrecedingTransaction(
            @NonNull TransactionBody txBody,
            @NonNull Class<T> recordBuilderClass,
            @NonNull Predicate<Key> verifier,
            AccountID syntheticPayer);

    /**
     * Dispatches a preceding transaction that already has an ID.
     *
     * @param txBody            the {@link TransactionBody} of the transaction to dispatch
     * @param recordBuilderClass the record builder class of the transaction
     * @param verifier         a {@link Predicate} that will be used to validate primitive keys
     * @return the record builder of the transaction
     * @param <T> the record type
     * @throws IllegalArgumentException if the transaction body did not have an id
     */
    default <T> T dispatchPrecedingTransaction(
            @NonNull TransactionBody txBody, @NonNull Class<T> recordBuilderClass, @NonNull Predicate<Key> verifier) {
        throwIfMissingPayerId(txBody);
        return dispatchPrecedingTransaction(
                txBody,
                recordBuilderClass,
                verifier,
                txBody.transactionIDOrThrow().accountIDOrThrow());
    }

    /**
     * Dispatches a child transaction.
     *
     * <p>A child transaction depends on the current transaction. That means if the current transaction fails,
     * a child transaction is automatically rolled back. The state changes introduced by a child transaction are
     * automatically committed together with the parent transaction.
     *
     * <p>A child transaction will run with the current state. It will see all state changes introduced by the current
     * transaction or preceding child transactions. If successful, a new entry will be added to the
     * {@link SavepointStack}. This enables the current transaction to commit or roll back the state changes. Please be
     * aware that any state changes introduced by storing data in one of the stores after calling a child transaction
     * will also be rolled back if the child transaction is rolled back.
     *
     * <p>The provided {@link Predicate} callback will be called to verify simple keys when the child transaction calls
     * any of the {@code verificationFor} methods.
     *
     * <p>A {@link TransactionCategory#PRECEDING}-transaction must not dispatch a child transaction.
     *
     * @param txBody             the {@link TransactionBody} of the child transaction to dispatch
     * @param recordBuilderClass the record builder class of the child transaction
     * @param callback           a {@link Predicate} callback function that will observe each primitive key
     * @param syntheticPayer   the payer of the child transaction
     * @return the record builder of the child transaction
     * @throws NullPointerException     if any of the arguments is {@code null}
     * @throws IllegalArgumentException if the current transaction is a
     *                                  {@link TransactionCategory#PRECEDING}-transaction or if the record builder type is unknown to the app
     */
    @NonNull
    <T> T dispatchChildTransaction(
            @NonNull TransactionBody txBody,
            @NonNull Class<T> recordBuilderClass,
            @NonNull Predicate<Key> callback,
            @NonNull AccountID syntheticPayer);

    /**
     * Dispatches a child transaction that already has a transaction ID.
     *
     * @param txBody the {@link TransactionBody} of the child transaction to dispatch
     * @param recordBuilderClass the record builder class of the child transaction
     * @param callback a {@link Predicate} callback function that will observe each primitive key
     * @return the record builder of the child transaction
     * @param <T> the record type
     * @throws IllegalArgumentException if the transaction body did not have an id
     */
    @NonNull
    default <T> T dispatchChildTransaction(
            @NonNull TransactionBody txBody, @NonNull Class<T> recordBuilderClass, @NonNull Predicate<Key> callback) {
        throwIfMissingPayerId(txBody);
        return dispatchChildTransaction(
                txBody,
                recordBuilderClass,
                callback,
                txBody.transactionIDOrThrow().accountIDOrThrow());
    }

    /**
     * Dispatches a removable child transaction.
     *
     * <p>A removable child transaction depends on the current transaction. It behaves in almost all aspects like a
     * regular child transaction (see {@link #dispatchChildTransaction(TransactionBody, Class, Predicate, AccountID)}.
     * But unlike regular child transactions, the records of removable child transactions are removed and not reverted.
     *
     * <p>The provided {@link Predicate} callback will be called to verify simple keys when the child transaction calls
     * any of the {@code verificationFor} methods.
     *
     * <p>A {@link TransactionCategory#PRECEDING}-transaction must not dispatch a child transaction.
     *
     * @param txBody             the {@link TransactionBody} of the child transaction to dispatch
     * @param recordBuilderClass the record builder class of the child transaction
     * @param callback           a {@link Predicate} callback function that will observe each primitive key
     * @param payer
     * @return the record builder of the child transaction
     * @throws NullPointerException     if any of the arguments is {@code null}
     * @throws IllegalArgumentException if the current transaction is a
     *                                  {@link TransactionCategory#PRECEDING}-transaction or if the record builder type is unknown to the app
     */
    @NonNull
    <T> T dispatchRemovableChildTransaction(
            @NonNull TransactionBody txBody,
            @NonNull Class<T> recordBuilderClass,
            @NonNull Predicate<Key> callback,
            AccountID payer);

    /**
     * Dispatches a removable child transaction that already has a transaction ID.
     *
     * @param txBody          the {@link TransactionBody} of the child transaction to dispatch
     * @param recordBuilderClass the record builder class of the child transaction
     * @param callback      a {@link Predicate} callback function that will observe each primitive key
     * @return the record builder of the child transaction
     * @param <T> the record type
     * @throws IllegalArgumentException if the transaction body did not have an id
     */
    @NonNull
    default <T> T dispatchRemovableChildTransaction(
            @NonNull TransactionBody txBody, @NonNull Class<T> recordBuilderClass, @NonNull Predicate<Key> callback) {
        throwIfMissingPayerId(txBody);
        return dispatchRemovableChildTransaction(
                txBody,
                recordBuilderClass,
                callback,
                txBody.transactionIDOrThrow().accountIDOrThrow());
    }

    /**
     * Adds a child record builder to the list of record builders. If the current {@link HandleContext} (or any parent
     * context) is rolled back, all child record builders will be reverted.
     *
     * @param recordBuilderClass the record type
     * @return the new child record builder
     * @param <T> the record type
     * @throws NullPointerException if {@code recordBuilderClass} is {@code null}
     * @throws IllegalArgumentException if the record builder type is unknown to the app
     */
    @NonNull
    <T> T addChildRecordBuilder(@NonNull Class<T> recordBuilderClass);

    /**
     * Adds a preceding child record builder to the list of record builders. If the current {@link HandleContext} (or any parent
     * context) is rolled back, all child record builders will be reverted.
     *
     * @param recordBuilderClass the record type
     * @return the new child record builder
     * @param <T> the record type
     * @throws NullPointerException if {@code recordBuilderClass} is {@code null}
     * @throws IllegalArgumentException if the record builder type is unknown to the app
     */
    @NonNull
    <T> T addPrecedingChildRecordBuilder(@NonNull Class<T> recordBuilderClass);

    /**
     * Adds a removable child record builder to the list of record builders. Unlike a regular child record builder,
     * a removable child record builder is removed, if the current {@link HandleContext} (or any parent context) is
     * rolled back.
     *
     * @param recordBuilderClass the record type
     * @return the new child record builder
     * @param <T> the record type
     * @throws NullPointerException if {@code recordBuilderClass} is {@code null}
     * @throws IllegalArgumentException if the record builder type is unknown to the app
     */
    @NonNull
    <T> T addRemovableChildRecordBuilder(@NonNull Class<T> recordBuilderClass);

    /**
     * Returns the current {@link SavepointStack}.
     *
     * @return the current {@code TransactionStack}
     */
    @NonNull
    SavepointStack savepointStack();

    /**
     * A stack of savepoints.
     *
     * <p>A new savepoint can be created manually. In addition, a new entry is added to the savepoint stack every time
     * a child transaction is dispatched and executed successfully. The transaction stack allows to rollback an
     * arbitrary number of transactions. Please be aware that rolling back a child transaction will also rollbacks all
     * state changes that were introduced afterward.
     */
    interface SavepointStack {
        /**
         * Create a savepoint manually.
         * <p>
         * This method will add a new entry to the savepoint stack. A subsequent rollback will roll back all state
         * changes that were introduced after the savepoint was added.
         */
        void createSavepoint();

        /**
         * Commits all changes since the last savepoint.
         *
         * @throws IllegalStateException if the savepoint stack is empty
         */
        void commit();

        /**
         * Rolls back the changes up until the last savepoint.
         *
         * @throws IllegalStateException if the savepoint stack is empty
         */
        void rollback();

        /**
         * Returns the depth of the savepoint stack.
         *
         * @return the depth of the savepoint stack
         */
        int depth();
    }

    static void throwIfMissingPayerId(@NonNull final TransactionBody body) {
        if (!body.hasTransactionID() || !body.transactionIDOrThrow().hasAccountID()) {
            throw new IllegalArgumentException("Transaction id must be set if dispatching without an explicit payer");
        }
    }
}
