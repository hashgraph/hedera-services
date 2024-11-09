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

package com.hedera.node.app.spi.workflows;

import static com.hedera.node.app.spi.workflows.record.StreamBuilder.TransactionCustomizer.NOOP_TRANSACTION_CUSTOMIZER;
import static java.util.Collections.emptySet;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.spi.workflows.HandleContext.ConsensusThrottling;
import com.hedera.node.app.spi.workflows.HandleContext.TransactionCategory;
import com.hedera.node.app.spi.workflows.record.StreamBuilder;
import com.hedera.node.app.spi.workflows.record.StreamBuilder.ReversingBehavior;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The options a {@link HandleContext} client can customize its dispatch with.
 */
public record DispatchOptions<T extends StreamBuilder>(
        @NonNull Commit commit,
        @NonNull AccountID payerId,
        @NonNull TransactionBody body,
        @NonNull Predicate<Key> keyVerifier,
        @NonNull Set<Key> authorizingKeys,
        @NonNull TransactionCategory category,
        @NonNull ConsensusThrottling throttling,
        @NonNull Class<T> streamBuilderType,
        @NonNull ReversingBehavior reversingBehavior,
        @NonNull StreamBuilder.TransactionCustomizer transactionCustomizer) {
    private static final Predicate<Key> PREAUTHORIZED_KEYS = k -> true;

    /**
     * The choice of when to commit the dispatched transaction's effects on state.
     */
    public enum Commit {
        /**
         * Wait to commit the dispatched transaction's effects with its parent; hence, only commit these effects
         * if the parent dispatch does not roll back.
         */
        WITH_PARENT,
        /**
         * Commit the transaction's effects immediately, along with any effects already accumulated by the parent
         * dispatch. (But note the parent dispatch will generally have no pending effects when this option makes
         * sense to use; i.e., to perform a preceding dispatch that sets up the parent dispatch state but does
         * not depend on the parent's success.)
         */
        IMMEDIATELY,
    }

    /**
     * Whether a dispatch can trigger staking rewards to involved accounts.
     */
    public enum StakingRewards {
        /**
         * The dispatch can trigger staking rewards.
         */
        ON,
        /**
         * The dispatch cannot trigger staking rewards.
         */
        OFF,
    }

    public DispatchOptions {
        requireNonNull(commit);
        requireNonNull(payerId);
        requireNonNull(body);
        requireNonNull(keyVerifier);
        requireNonNull(category);
        requireNonNull(throttling);
        requireNonNull(authorizingKeys);
        requireNonNull(streamBuilderType);
        requireNonNull(reversingBehavior);
        requireNonNull(transactionCustomizer);
    }

    /**
     * Returns the effective key verifier for the dispatch ({@code null} if all keys should be treated as authorized).
     */
    public @Nullable Predicate<Key> effectiveKeyVerifier() {
        return keyVerifier == PREAUTHORIZED_KEYS ? null : keyVerifier;
    }

    /**
     * Returns whether the dispatch should commit immediately.
     */
    public boolean commitImmediately() {
        return commit == Commit.IMMEDIATELY;
    }

    /**
     * Returns options for a dispatch that is logically independent of the parent dispatch. Use cases include,
     * <ul>
     *     <li>Completing a hollow account whose public key was provided in the signature map of
     *     a parent HAPI transaction.</li>
     *     <li>Doing post-upgrade setup before the first transaction handled after the upgrade.</li>
     * </ul>
     * <b>Important:</b> Since such dispatches are not logically part of the parent dispatch, they should
     * really be done as a separate top-level transaction. Prefer not introducing any further uses of this
     * {@link DispatchOptions} factory.
     *
     * @param payerId the account to pay for the dispatch
     * @param body the transaction to dispatch
     * @param streamBuilderType the type of stream builder to use for the dispatch
     * @return the options for the setup dispatch
     * @param <T> the type of stream builder to use for the dispatch
     */
    public static <T extends StreamBuilder> DispatchOptions<T> independentDispatch(
            @NonNull final AccountID payerId,
            @NonNull final TransactionBody body,
            @NonNull final Class<T> streamBuilderType) {
        return new DispatchOptions<>(
                Commit.IMMEDIATELY,
                payerId,
                body,
                PREAUTHORIZED_KEYS,
                emptySet(),
                TransactionCategory.PRECEDING,
                ConsensusThrottling.OFF,
                streamBuilderType,
                ReversingBehavior.IRREVERSIBLE,
                NOOP_TRANSACTION_CUSTOMIZER);
    }

    /**
     * Returns options for a dispatch that is part of the parent dispatch's transactional unit, but in a setup role
     * that logically precedes the parent transaction's effects. Use cases include,
     * <ul>
     *     <li>Externalizing creation of a "hollow" account prior to a successful EVM transaction that
     *     first sends value to its EVM address.</li>
     *     <li>Externalizing auto-creation of an aliased account that receives value in a parent
     *     {@link com.hedera.hapi.node.base.HederaFunctionality#CRYPTO_TRANSFER} transaction.</li>
     * </ul>
     * @param payerId the account to pay for the dispatch
     * @param body the transaction to dispatch
     * @param streamBuilderType the type of stream builder to use for the dispatch
     * @return the options for the setup dispatch
     * @param <T> the type of stream builder to use for the dispatch
     */
    public static <T extends StreamBuilder> DispatchOptions<T> setupDispatch(
            @NonNull final AccountID payerId,
            @NonNull final TransactionBody body,
            @NonNull final Class<T> streamBuilderType) {
        return new DispatchOptions<>(
                Commit.WITH_PARENT,
                payerId,
                body,
                PREAUTHORIZED_KEYS,
                emptySet(),
                TransactionCategory.PRECEDING,
                ConsensusThrottling.ON,
                streamBuilderType,
                ReversingBehavior.REMOVABLE,
                NOOP_TRANSACTION_CUSTOMIZER);
    }

    /**
     * Returns options for a dispatch that is part of the parent dispatch's transactional unit, as a sub-transaction
     * that forms a logical step explaining the entirety of the parent transaction's effects; no matter if the parent
     * transaction succeeds or fails. Use cases include,
     * <ul>
     *     <li>Triggering a scheduled transaction.</li>
     *     <li>Dispatching a native Hedera transaction from within the EVM via a system contract.</li>
     * </ul>
     *
     * @param <T> the type of stream builder to use for the dispatch
     * @param payerId the account to pay for the dispatch
     * @param body the transaction to dispatch
     * @param keyVerifier the key verifier to use for the dispatch
     * @param authorizingKeys the set of keys authorizing the dispatch
     * @param streamBuilderType the type of stream builder to use for the dispatch
     * @param stakingRewards whether the dispatch can trigger staking rewards
     * @return the options for the sub-dispatch
     */
    public static <T extends StreamBuilder> DispatchOptions<T> subDispatch(
            @NonNull final AccountID payerId,
            @NonNull final TransactionBody body,
            @NonNull final Predicate<Key> keyVerifier,
            @NonNull final Set<Key> authorizingKeys,
            @NonNull final Class<T> streamBuilderType,
            @NonNull final StakingRewards stakingRewards) {
        final var category =
                switch (requireNonNull(stakingRewards)) {
                    case ON -> TransactionCategory.SCHEDULED;
                    case OFF -> TransactionCategory.CHILD;
                };
        return new DispatchOptions<>(
                Commit.WITH_PARENT,
                payerId,
                body,
                keyVerifier,
                authorizingKeys,
                category,
                ConsensusThrottling.ON,
                streamBuilderType,
                ReversingBehavior.REVERSIBLE,
                NOOP_TRANSACTION_CUSTOMIZER);
    }

    /**
     * Returns options for a dispatch that is a step in the parent dispatch's business logic, but only appropriate
     * to externalize if the parent succeeds.
     * <ul>
     *     <li>Dispatching an internal contract creation in the EVM.</li>
     * </ul>
     *
     * @param payerId the account to pay for the dispatch
     * @param body the transaction to dispatch
     * @param streamBuilderType the type of stream builder to use for the dispatch
     * @param transactionCustomizer the customizer for the transaction
     * @return the options for the sub-dispatch
     * @param <T> the type of stream builder to use for the dispatch
     */
    public static <T extends StreamBuilder> DispatchOptions<T> stepDispatch(
            @NonNull final AccountID payerId,
            @NonNull final TransactionBody body,
            @NonNull final Class<T> streamBuilderType,
            @NonNull final StreamBuilder.TransactionCustomizer transactionCustomizer) {
        return new DispatchOptions<>(
                Commit.WITH_PARENT,
                payerId,
                body,
                PREAUTHORIZED_KEYS,
                emptySet(),
                TransactionCategory.CHILD,
                ConsensusThrottling.OFF,
                streamBuilderType,
                ReversingBehavior.REMOVABLE,
                transactionCustomizer);
    }
}
