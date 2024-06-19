/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

import static com.hedera.node.app.spi.workflows.HandleContext.TransactionCategory.SCHEDULED;
import static com.hedera.node.app.spi.workflows.record.ExternalizedRecordCustomizer.NOOP_EXTERNALIZED_RECORD_CUSTOMIZER;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.SubType;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.spi.authorization.SystemPrivilege;
import com.hedera.node.app.spi.fees.ExchangeRateInfo;
import com.hedera.node.app.spi.fees.FeeAccumulator;
import com.hedera.node.app.spi.fees.FeeCalculator;
import com.hedera.node.app.spi.fees.Fees;
import com.hedera.node.app.spi.key.KeyVerifier;
import com.hedera.node.app.spi.records.BlockRecordInfo;
import com.hedera.node.app.spi.records.RecordCache;
import com.hedera.node.app.spi.validation.AttributeValidator;
import com.hedera.node.app.spi.validation.ExpiryValidator;
import com.hedera.node.app.spi.workflows.record.ExternalizedRecordCustomizer;
import com.hedera.node.app.spi.workflows.record.RecordListCheckPoint;
import com.swirlds.config.api.Configuration;
import com.swirlds.state.spi.info.NetworkInfo;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;
import java.util.Map;
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
        CHILD,
        /** A transaction executed via the schedule service. */
        SCHEDULED
    }

    /**
     * Enumerates the possible kinds of limits on preceding transaction records.
     */
    enum PrecedingTransactionCategory {
        /**
         * No limit on preceding transactions, true at genesis since there are no previous consensus
         * times to collide with.
         */
        UNLIMITED_CHILD_RECORDS,
        /**
         * The number of preceding transactions is limited by the number of nanoseconds between the
         * last-assigned consensus time and the current platform-assigned consensus time. (Or,
         * before block streams, even further artificially limited to three records for
         * backward compatibility.)
         */
        LIMITED_CHILD_RECORDS
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
     * Returns the {@link KeyVerifier} which can be used to verify signatures.
     *
     * @return the {@link KeyVerifier}
     */
    @NonNull
    KeyVerifier keyVerifier();

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
     * Dispatches the fee calculation for a child transaction (that might then be dispatched).
     *
     * <p>The override payer id still matters for this purpose, because a transaction can add
     * state whose lifetime is scoped to a payer account (the main current example is a
     * {@link HederaFunctionality#CRYPTO_APPROVE_ALLOWANCE} dispatch).
     *
     * @param txBody the {@link TransactionBody} of the child transaction to compute fees for
     * @param syntheticPayerId the child payer
     * @param computeDispatchFeesAsTopLevel for mono fidelity, whether to compute fees as a top-level transaction
     * @return the calculated fees
     */
    Fees dispatchComputeFees(
            @NonNull TransactionBody txBody,
            @NonNull AccountID syntheticPayerId,
            @NonNull ComputeDispatchFeesAsTopLevel computeDispatchFeesAsTopLevel);

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
     * <p>If non-null, the provided {@link Predicate} callback will be called to enforce signing requirements; or to
     * verify simple keys when the child transaction calls any of the {@code verificationFor} methods. If the callback
     * is null, no signing requirements will be enforced.
     *
     * @param txBody             the {@link TransactionBody} of the transaction to dispatch
     * @param recordBuilderClass the record builder class of the transaction
     * @param verifier           if signing requirements should be enforced, a {@link Predicate} that will be used to validate primitive keys
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
            @Nullable Predicate<Key> verifier,
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
    // Only used in tests
    default <T> T dispatchPrecedingTransaction(
            @NonNull final TransactionBody txBody,
            @NonNull final Class<T> recordBuilderClass,
            @NonNull final Predicate<Key> verifier) {
        throwIfMissingPayerId(txBody);
        return dispatchPrecedingTransaction(
                txBody,
                recordBuilderClass,
                verifier,
                txBody.transactionIDOrThrow().accountIDOrThrow());
    }

    /**
     * Dispatches preceding transaction that can be removed.
     *
     * <p>A removable preceding transaction depends on the current transaction. That means if the user transaction
     * fails, a removable preceding transaction is automatically removed and not exported. The state changes introduced by a
     * removable preceding transaction are automatically committed together with the parent transaction.
     *
     * <p>This method can only be called by a {@link TransactionCategory#USER}-transaction and only as long as no state
     * changes have been introduced by the user transaction (either by storing state or by calling a child
     * transaction).
     *
     * <p>The provided {@link Predicate} callback will be called to verify simple keys when the child transaction calls
     * any of the {@code verificationFor} methods.
     *
     * @param txBody             the {@link TransactionBody} of the transaction to dispatch
     * @param recordBuilderClass the record builder class of the transaction
     * @param verifier           if non-null, a {@link Predicate} that will be used to validate primitive keys
     * @param syntheticPayer    the payer of the transaction
     * @return the record builder of the transaction
     * @throws NullPointerException     if {@code txBody} is {@code null}
     * @throws IllegalArgumentException if the transaction is not a {@link TransactionCategory#USER}-transaction or if
     *                                  the record builder type is unknown to the app
     * @throws IllegalStateException    if the current transaction has already introduced state changes
     */
    @NonNull
    <T> T dispatchRemovablePrecedingTransaction(
            @NonNull TransactionBody txBody,
            @NonNull Class<T> recordBuilderClass,
            @Nullable Predicate<Key> verifier,
            AccountID syntheticPayer);

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
     * @param txBody the {@link TransactionBody} of the child transaction to dispatch
     * @param recordBuilderClass the record builder class of the child transaction
     * @param callback a {@link Predicate} callback function that will observe each primitive key
     * @param syntheticPayerId the payer of the child transaction
     * @param childCategory the category of the child transaction
     * @return the record builder of the child transaction
     * @throws NullPointerException if any of the arguments is {@code null}
     * @throws IllegalArgumentException if the current transaction is a
     * {@link TransactionCategory#PRECEDING}-transaction or if the record builder type is unknown to the app
     */
    @NonNull
    <T> T dispatchChildTransaction(
            @NonNull TransactionBody txBody,
            @NonNull Class<T> recordBuilderClass,
            @Nullable Predicate<Key> callback,
            @NonNull AccountID syntheticPayerId,
            @NonNull TransactionCategory childCategory);

    /**
     * Dispatches a child transaction that already has a transaction ID due to
     * its construction in the schedule service.
     *
     * @param txBody the {@link TransactionBody} of the child transaction to dispatch
     * @param recordBuilderClass the record builder class of the child transaction
     * @param callback a {@link Predicate} callback function that will observe each primitive key
     * @return the record builder of the child transaction
     * @param <T> the record type
     * @throws IllegalArgumentException if the transaction body did not have an id
     */
    @NonNull
    default <T> T dispatchScheduledChildTransaction(
            @NonNull final TransactionBody txBody,
            @NonNull final Class<T> recordBuilderClass,
            @NonNull final Predicate<Key> callback) {
        throwIfMissingPayerId(txBody);
        return dispatchChildTransaction(
                txBody,
                recordBuilderClass,
                callback,
                txBody.transactionIDOrThrow().accountIDOrThrow(),
                SCHEDULED);
    }

    /**
     * Dispatches a removable child transaction.
     *
     * <p>A removable child transaction depends on the current transaction. It behaves in almost all aspects like a
     * regular child transaction (see {@link #dispatchChildTransaction(TransactionBody, Class, Predicate, AccountID, TransactionCategory)}.
     * But unlike regular child transactions, the records of removable child transactions are removed and not reverted.
     *
     * <p>The provided {@link Predicate} callback will be called to verify simple keys when the child transaction calls
     * any of the {@code verificationFor} methods.
     *
     * <p>A {@link TransactionCategory#PRECEDING}-transaction must not dispatch a child transaction.
     *
     * @param txBody the {@link TransactionBody} of the child transaction to dispatch
     * @param recordBuilderClass the record builder class of the child transaction
     * @param callback a {@link Predicate} callback function that will observe each primitive key
     * @param syntheticPayerId the payer of the child transaction
     * @param customizer a final transformation to apply before externalizing if the returned value is non-null
     * @return the record builder of the child transaction
     * @throws NullPointerException if any of the arguments is {@code null}
     * @throws IllegalArgumentException if the current transaction is a
     * {@link TransactionCategory#PRECEDING}-transaction or if the record builder type is unknown to the app
     */
    @NonNull
    <T> T dispatchRemovableChildTransaction(
            @NonNull TransactionBody txBody,
            @NonNull Class<T> recordBuilderClass,
            @Nullable Predicate<Key> callback,
            @NonNull AccountID syntheticPayerId,
            @NonNull ExternalizedRecordCustomizer customizer);

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
            @NonNull final TransactionBody txBody,
            @NonNull final Class<T> recordBuilderClass,
            @NonNull final Predicate<Key> callback) {
        throwIfMissingPayerId(txBody);
        return dispatchRemovableChildTransaction(
                txBody,
                recordBuilderClass,
                callback,
                txBody.transactionIDOrThrow().accountIDOrThrow(),
                NOOP_EXTERNALIZED_RECORD_CUSTOMIZER);
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
     * Revert the childRecords from the checkpoint.
     */
    void revertRecordsFrom(@NonNull RecordListCheckPoint recordListCheckPoint);

    /**
     * Verifies if the throttle in this operation context has enough capacity to handle the given number of the
     * given function at the given time. (The time matters because we want to consider how much
     * will have leaked between now and that time.)
     *
     * @param n the number of the given function
     * @param function the function
     * @return true if the system should throttle the given number of the given function
     * at the instant for which throttling should be calculated
     */
    boolean shouldThrottleNOfUnscaled(int n, HederaFunctionality function);

    /**
     * For each following child transaction consumes the capacity
     * required for that child transaction in the consensus throttle buckets.
     *
     * @return true if all the child transactions were allowed through the throttle consideration, false otherwise.
     */
    boolean hasThrottleCapacityForChildTransactions();

    /**
     * Create a checkpoint for the current childRecords.
     *
     * @return the checkpoint for the current childRecords, containing the first preceding record and the last following
     * record.
     */
    @NonNull
    RecordListCheckPoint createRecordListCheckPoint();

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

    /**
     * Gets the pre-paid rewards for the current transaction. This can be non-empty for scheduled transactions.
     * Since we use the parent record finalizer to finalize schedule transactions, we need to deduct any paid staking rewards
     * already happened in the parent transaction.
     * @return the paid rewards
     */
    @NonNull
    Map<AccountID, Long> dispatchPaidRewards();
}
