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

import static com.hedera.node.app.spi.AppContext.*;
import static com.hedera.node.app.spi.workflows.HandleContext.TransactionCategory.SCHEDULED;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.spi.authorization.SystemPrivilege;
import com.hedera.node.app.spi.fees.ExchangeRateInfo;
import com.hedera.node.app.spi.fees.Fees;
import com.hedera.node.app.spi.fees.ResourcePriceCalculator;
import com.hedera.node.app.spi.ids.EntityNumGenerator;
import com.hedera.node.app.spi.key.KeyVerifier;
import com.hedera.node.app.spi.records.BlockRecordInfo;
import com.hedera.node.app.spi.store.StoreFactory;
import com.hedera.node.app.spi.throttle.ThrottleAdviser;
import com.hedera.node.app.spi.validation.AttributeValidator;
import com.hedera.node.app.spi.validation.ExpiryValidator;
import com.hedera.node.app.spi.workflows.record.ExternalizedRecordCustomizer;
import com.hedera.node.app.spi.workflows.record.StreamBuilder;
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
        /**
         * A transaction submitted by a user via HAPI or by a node via {@link com.hedera.node.app.spi.AppContext.Gossip}.
         * */
        USER,
        /**
         * An independent, top-level transaction that is executed before the user transaction.
         * */
        PRECEDING,
        /**
         * A child transaction that is executed as part of a user transaction.
         * */
        CHILD,
        /**
         * A transaction executed via the schedule service.
         * */
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
     * Attempts to charge the payer in this context the given amount of tinybar.
     * @param amount the amount to charge
     * @return true if the entire amount was successfully charged, false otherwise
     */
    boolean tryToChargePayer(long amount);

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
     * Returns a {@link ResourcePriceCalculator} that provides functionality to calculate fees for transactions.
     *
     * @return the {@link ResourcePriceCalculator}
     */
    @NonNull
    ResourcePriceCalculator resourcePriceCalculator();

    /**
     * Gets a {@link ExchangeRateInfo} which provides information about the current exchange rate.
     *
     * @return The {@link ExchangeRateInfo} .
     */
    @NonNull
    ExchangeRateInfo exchangeRateInfo();

    /**
     * Returns an {@link EntityNumGenerator} that can be used to generate entity numbers.
     *
     * @return the entity number generator
     */
    EntityNumGenerator entityNumGenerator();

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

    /**
     * Returns a {@link StoreFactory} that can create readable and writable stores as well as service APIs.
     *
     * @return the {@link StoreFactory}
     */
    @NonNull
    StoreFactory storeFactory();

    /**
     * Returns the information about the network this transaction is being handled in.
     *
     * @return the network information
     */
    @NonNull
    NetworkInfo networkInfo();

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
     * @throws HandleException if the base builder for the dispatch cannot be created
     */
    @NonNull
    <T> T dispatchPrecedingTransaction(
            @NonNull TransactionBody txBody,
            @NonNull Class<T> recordBuilderClass,
            @Nullable Predicate<Key> verifier,
            @NonNull AccountID syntheticPayer);

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
     * @param syntheticPayer     the payer of the transaction
     * @param consensusThrottling whether to throttle the child transaction at consensus
     * @return the record builder of the transaction
     * @throws NullPointerException     if {@code txBody} is {@code null}
     * @throws IllegalArgumentException if the transaction is not a {@link TransactionCategory#USER}-transaction or if
     *                                  the record builder type is unknown to the app
     * @throws IllegalStateException    if the current transaction has already introduced state changes
     * @throws HandleException          if the base builder for the dispatch cannot be created
     */
    @NonNull
    <T> T dispatchRemovablePrecedingTransaction(
            @NonNull TransactionBody txBody,
            @NonNull Class<T> recordBuilderClass,
            @Nullable Predicate<Key> verifier,
            AccountID syntheticPayer,
            @NonNull ConsensusThrottling consensusThrottling);

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
     * @param syntheticPayerId   the payer of the child transaction
     * @param childCategory      the category of the child transaction
     * @param consensusThrottling whether to throttle the child transaction at consensus
     * @return the record builder of the child transaction
     * @throws NullPointerException     if any of the arguments is {@code null}
     * @throws IllegalArgumentException if the current transaction is a
     * @throws HandleException          if the base builder for the dispatch cannot be created
     *                                  {@link TransactionCategory#PRECEDING}-transaction or if the record builder type is unknown to the app
     */
    @NonNull
    <T> T dispatchChildTransaction(
            @NonNull TransactionBody txBody,
            @NonNull Class<T> recordBuilderClass,
            @Nullable Predicate<Key> callback,
            @NonNull AccountID syntheticPayerId,
            @NonNull TransactionCategory childCategory,
            @NonNull ConsensusThrottling consensusThrottling);

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
     * @throws HandleException if the base builder for the dispatch cannot be created
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
                SCHEDULED,
                ConsensusThrottling.ON);
    }

    /**
     * Dispatches a removable child transaction.
     *
     * <p>A removable child transaction depends on the current transaction. It behaves in almost all aspects like a
     * regular child transaction (see {@link #dispatchChildTransaction(TransactionBody, Class, Predicate, AccountID, TransactionCategory, ConsensusThrottling)}.
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
     * @param syntheticPayerId   the payer of the child transaction
     * @param customizer         a final transformation to apply before externalizing if the returned value is non-null
     * @param consensusThrottling whether to throttle the child transaction at consensus
     * @return the record builder of the child transaction
     * @throws NullPointerException     if any of the arguments is {@code null}
     * @throws IllegalArgumentException if the current transaction is a
     * @throws HandleException          if the base builder for the dispatch cannot be created
     *                                  {@link TransactionCategory#PRECEDING}-transaction or if the record builder type is unknown to the app
     */
    @NonNull
    <T> T dispatchRemovableChildTransaction(
            @NonNull TransactionBody txBody,
            @NonNull Class<T> recordBuilderClass,
            @Nullable Predicate<Key> callback,
            @NonNull AccountID syntheticPayerId,
            @NonNull ExternalizedRecordCustomizer customizer,
            @NonNull ConsensusThrottling consensusThrottling);

    /**
     * Returns the current {@link SavepointStack}.
     *
     * @return the current {@code TransactionStack}
     */
    @NonNull
    SavepointStack savepointStack();

    /**
     * Returns the {@link ThrottleAdviser} for this transaction, which provides information about throttles.
     *
     * @return the {@link ThrottleAdviser} for this transaction
     */
    @NonNull
    ThrottleAdviser throttleAdviser();

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
        <T extends StreamBuilder> T getBaseBuilder(@NonNull Class<T> recordBuilderClass);

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

    /**
     * Whether a dispatch should be throttled at consensus. True for everything except certain dispatches
     * internal to the EVM which are only constrained by gas.
     */
    enum ConsensusThrottling {
        ON,
        OFF
    }
}
