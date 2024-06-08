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

package com.hedera.node.app.workflows.handle.record;

import static com.hedera.node.app.spi.workflows.record.ExternalizedRecordCustomizer.NOOP_EXTERNALIZED_RECORD_CUSTOMIZER;
import static com.hedera.node.app.workflows.handle.HandleContextImpl.PrecedingTransactionCategory.LIMITED_CHILD_RECORDS;
import static com.hedera.node.app.workflows.handle.HandleContextImpl.PrecedingTransactionCategory.UNLIMITED_CHILD_RECORDS;
import static java.util.Collections.unmodifiableList;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleContext.TransactionCategory;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.record.ExternalizedRecordCustomizer;
import com.hedera.node.app.spi.workflows.record.RecordListCheckPoint;
import com.hedera.node.app.state.SingleTransactionRecord;
import com.hedera.node.app.workflows.handle.HandleContextImpl;
import com.hedera.node.app.workflows.handle.flow.txn.UserTxnScope;
import com.hedera.node.app.workflows.handle.record.SingleTransactionRecordBuilderImpl.ReversingBehavior;
import com.hedera.node.config.data.ConsensusConfig;
import com.swirlds.config.api.Configuration;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import javax.inject.Inject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * This class manages all record builders that are used while a single user transaction is running.
 *
 * <p>As a transaction is handled, it will create a record and receipt tracking what happened when the transaction was
 * handled. During handling, it may be that more than one additional "synthetic" record must be created. For example,
 * a "crypto transfer" may need to auto-create a destination account that does not already exist. This must be done
 * <strong>before</strong> the crypto transfer so that someone reading the resulting record stream will see the creation
 * before the transfer. This is known as a "preceding" record. Preceding records are unrelated to the main user
 * transaction, other than having been triggered by it.
 *
 * <p>Likewise, some transactions cause "child" transactions to be created. For example, a "schedule sign" transaction
 * may complete all required signatures and "trigger" a child transaction for actually executing the transaction that
 * was signed. Or a smart contract may invoke the token service, causing one or more child transactions to be created.
 * These child transactions <strong>are</strong> related to the user transaction, and are linked by the "parent
 * consensus timestamp".
 *
 * <p>Some kinds of child transactions are "removable". This is only needed in a very few special cases. Normally
 * preceding and child transactions are used like a stack -- added to the end and removed from the end. But some
 * "removable" child transactions can be plucked out of the middle.
 *
 * <p>After transaction handling, this class will produce the list of all created records. They will be assigned
 * transaction IDs and consensus timestamps and parent consensus timestamps as appropriate.
 *
 * <p>As with all classes intended to be used within the handle-workflow, this class is <em>not</em> thread-safe.
 */
@UserTxnScope
public final class RecordListBuilder {
    private static final Logger logger = LogManager.getLogger(RecordListBuilder.class);

    private static final String CONFIGURATION_MUST_NOT_BE_NULL = "configuration must not be null";
    private static final EnumSet<ResponseCodeEnum> SUCCESSES = EnumSet.of(
            ResponseCodeEnum.OK,
            ResponseCodeEnum.SUCCESS,
            ResponseCodeEnum.FEE_SCHEDULE_FILE_PART_UPLOADED,
            ResponseCodeEnum.SUCCESS_BUT_MISSING_EXPECTED_OPERATION);
    /**
     * The record builder for the user transaction.
     */
    private final SingleTransactionRecordBuilderImpl userTxnRecordBuilder;
    /**
     * The list of record builders for preceding transactions. If the user transaction is at consensus time T, then
     * the first preceding transaction is at consensus time T-1, the second at T-2, etc.
     */
    private List<SingleTransactionRecordBuilderImpl> precedingTxnRecordBuilders;
    /**
     * The list of record builders for child transactions. If the user transaction is at consensus time T, then
     * the first child transaction is at consensus time T+1, the second at T+2, etc.
     */
    private List<SingleTransactionRecordBuilderImpl> childRecordBuilders;

    /**
     * Whether a following REMOVABLE child was removed. We need this to know whether to adjust consensus times
     * to eliminate gaps in following consensus times for mono-service fidelity.
     */
    private boolean followingChildRemoved = false;

    /**
     * Creates a new instance with the given user transaction consensus timestamp.
     *
     * @param consensusTimestamp The consensus timestamp of the user transaction
     * @throws NullPointerException if {@code recordBuilder} is {@code null}
     */
    @Inject
    public RecordListBuilder(@NonNull final Instant consensusTimestamp) {
        this.userTxnRecordBuilder = new SingleTransactionRecordBuilderImpl(
                requireNonNull(consensusTimestamp, "recordBuilder must not be null"));
    }

    /**
     * Gets the record builder for the user transaction.
     *
     * @return the main record builder
     */
    @NonNull
    public SingleTransactionRecordBuilderImpl userTransactionRecordBuilder() {
        return userTxnRecordBuilder;
    }

    /**
     * Returns an unmodifiable {@link List} of all preceding record builders. The first item in the list is the
     * record builder for the transaction immediately preceding the user transaction.
     *
     * @return all preceding record builders
     */
    @NonNull
    public List<SingleTransactionRecordBuilderImpl> precedingRecordBuilders() {
        return precedingTxnRecordBuilders != null ? unmodifiableList(precedingTxnRecordBuilders) : List.of();
    }

    /**
     * Returns an unmodifiable {@link List} of all child record builders. The first item in the list is the
     * record builder for the transaction immediately following the user transaction, and so forth.
     *
     * @return all child record builders
     */
    @NonNull
    public List<SingleTransactionRecordBuilderImpl> childRecordBuilders() {
        return childRecordBuilders != null ? unmodifiableList(childRecordBuilders) : List.of();
    }

    /**
     * Adds a record builder for a preceding transaction.
     *
     * @param configuration the current configuration
     * @return the record builder for the preceding transaction
     * @throws NullPointerException if {@code consensusConfig} is {@code null}
     * @throws HandleException if no more preceding slots are available
     */
    public SingleTransactionRecordBuilderImpl addPreceding(
            @NonNull final Configuration configuration,
            final HandleContextImpl.PrecedingTransactionCategory precedingTxnCategory) {
        requireNonNull(configuration, CONFIGURATION_MUST_NOT_BE_NULL);
        return doAddPreceding(configuration, ReversingBehavior.IRREVERSIBLE, precedingTxnCategory);
    }

    public SingleTransactionRecordBuilderImpl addReversiblePreceding(@NonNull final Configuration configuration) {
        requireNonNull(configuration, CONFIGURATION_MUST_NOT_BE_NULL);
        return doAddPreceding(configuration, ReversingBehavior.REVERSIBLE, LIMITED_CHILD_RECORDS);
    }

    public SingleTransactionRecordBuilderImpl addRemovablePreceding(@NonNull final Configuration configuration) {
        requireNonNull(configuration, CONFIGURATION_MUST_NOT_BE_NULL);
        return doAddPreceding(configuration, ReversingBehavior.REMOVABLE, LIMITED_CHILD_RECORDS);
    }

    public SingleTransactionRecordBuilderImpl doAddPreceding(
            @NonNull final Configuration configuration,
            @NonNull final ReversingBehavior reversingBehavior,
            @NonNull final HandleContextImpl.PrecedingTransactionCategory precedingTxnCategory) {
        // Lazily create. FUTURE: We should reuse the RecordListBuilder between handle calls, and we should
        // reuse these lists. Then we can omit this lazy create entirely and produce less garbage overall.
        if (precedingTxnRecordBuilders == null) {
            precedingTxnRecordBuilders = new ArrayList<>();
        }

        // Check whether the number of preceding records has been exceeded. FUTURE we could store in state the last
        // nanosecond of the last record from the previous handle transaction operation, and if there is room for more
        // preceding records, we grant them, even if we have passed `handleMaxPrecedingRecords`.
        final var consensusConfig = configuration.getConfigData(ConsensusConfig.class);
        final var precedingCount = precedingTxnRecordBuilders.size();
        final var maxRecords = consensusConfig.handleMaxPrecedingRecords();
        // On genesis start we create almost 700 preceding child records for creating system accounts.
        // Also, we should not be failing for stake update transaction records that happen every midnight.
        // In these two cases need to allow for this, but we don't want to allow for this on every handle call.
        if (precedingTxnRecordBuilders.size() >= maxRecords && (precedingTxnCategory != UNLIMITED_CHILD_RECORDS)) {
            // We do not have a MAX_PRECEDING_RECORDS_EXCEEDED error, so use this.
            throw new HandleException(ResponseCodeEnum.MAX_CHILD_RECORDS_EXCEEDED);
        }

        // The consensus timestamp of the first item in the preceding list is T-1, where T is the time of the
        // user transaction. The second item is T-2, and so on.
        final var parentConsensusTimestamp = userTxnRecordBuilder.consensusNow();
        final var consensusNow = parentConsensusTimestamp.minusNanos(precedingCount + 1L);
        final var recordBuilder = new SingleTransactionRecordBuilderImpl(consensusNow, reversingBehavior);
        precedingTxnRecordBuilders.add(recordBuilder);
        return recordBuilder;
    }

    /**
     * Adds a record builder for a child transaction.
     *
     * <p>If a parent transaction of this child transaction is rolled back and the child transaction was successful, the
     * status is set to {@link ResponseCodeEnum#REVERTED_SUCCESS}.
     *
     * @param configuration the current configuration
     * @return the record builder for the child transaction
     * @throws NullPointerException if {@code consensusConfig} is {@code null}
     * @throws HandleException if no more child slots are available
     */
    public SingleTransactionRecordBuilderImpl addChild(
            @NonNull final Configuration configuration,
            @NonNull final HandleContext.TransactionCategory childCategory) {
        requireNonNull(configuration, CONFIGURATION_MUST_NOT_BE_NULL);
        return doAddFollowingChild(
                configuration, ReversingBehavior.REVERSIBLE, NOOP_EXTERNALIZED_RECORD_CUSTOMIZER, childCategory);
    }

    /**
     * Adds a record builder for a child transaction that is removed when reverted.
     * <p>
     * If a parent transaction of this child transaction is rolled back, the record builder is removed entirely. This is
     * only needed in a very few special cases. Under normal circumstances,
     * {@link #addChild(Configuration, HandleContext.TransactionCategory)} should be used.
     *
     * @param configuration the current configuration
     * @return the record builder for the child transaction
     * @throws NullPointerException if {@code consensusConfig} is {@code null}
     * @throws HandleException if no more child slots are available
     */
    public SingleTransactionRecordBuilderImpl addRemovableChild(@NonNull final Configuration configuration) {
        requireNonNull(configuration, CONFIGURATION_MUST_NOT_BE_NULL);
        return doAddFollowingChild(
                configuration,
                ReversingBehavior.REMOVABLE,
                NOOP_EXTERNALIZED_RECORD_CUSTOMIZER,
                HandleContext.TransactionCategory.CHILD);
    }

    /**
     * Adds a record builder for a child transaction that is removed when reverted, and performs a custom
     * "finishing" operation on the transaction before externalizing it to the record stream.
     *
     * <p>We need this variant to let the contract service externalize some of its dispatched
     * {@code CryptoCreate} transactions as {@code ContractCreate} transactions.
     *
     * @param configuration the current configuration
     * @param customizer the custom finishing operation
     * @return the record builder for the child transaction
     * @throws NullPointerException if {@code consensusConfig} is {@code null}
     * @throws HandleException if no more child slots are available
     */
    public SingleTransactionRecordBuilderImpl addRemovableChildWithExternalizationCustomizer(
            @NonNull final Configuration configuration, @NonNull final ExternalizedRecordCustomizer customizer) {
        requireNonNull(configuration, CONFIGURATION_MUST_NOT_BE_NULL);
        requireNonNull(customizer, "customizer must not be null");
        return doAddFollowingChild(
                configuration, ReversingBehavior.REMOVABLE, customizer, HandleContext.TransactionCategory.CHILD);
    }

    private SingleTransactionRecordBuilderImpl doAddFollowingChild(
            @NonNull final Configuration configuration,
            final ReversingBehavior reversingBehavior,
            @NonNull final ExternalizedRecordCustomizer customizer,
            @NonNull final HandleContext.TransactionCategory childCategory) {
        // FUTURE: We should reuse the RecordListBuilder between handle calls, and we should reuse these lists, in
        // which case we will no longer have to create them lazily.
        if (childRecordBuilders == null) {
            childRecordBuilders = new ArrayList<>();
        }

        // Make sure we have not created so many that we have run out of slots.
        final int childCount = childRecordBuilders.size();
        final ConsensusConfig consensusConfig = configuration.getConfigData(ConsensusConfig.class);
        if (childCount >= consensusConfig.handleMaxFollowingRecords()) {
            throw new HandleException(ResponseCodeEnum.MAX_CHILD_RECORDS_EXCEEDED);
        }

        final Instant parentConsensusTimestamp = userTxnRecordBuilder.consensusNow();
        final Instant prevConsensusNow = childRecordBuilders.isEmpty()
                ? parentConsensusTimestamp
                : childRecordBuilders.get(childRecordBuilders.size() - 1).consensusNow();
        // The consensus timestamp of a SCHEDULED transaction in the child list is T+K (in nanoseconds),
        // where T is the time of the parent or preceding child tx and K is the maximum number of "preceding" records
        // defined for the current configuration.  This permits a SCHEDULED child to trigger additional preceding
        // transactions (e.g. auto create on CryptoTransfer) if necessary without creating records with the same
        // timestamp nanosecond value.
        // All other child transactions just offset by +1.
        final long nextRecordOffset =
                childCategory == TransactionCategory.SCHEDULED ? consensusConfig.handleMaxPrecedingRecords() + 1 : 1L;
        final Instant consensusNow = prevConsensusNow.plusNanos(nextRecordOffset);
        // Note we do not repeat exchange rates for child transactions
        final var recordBuilder = new SingleTransactionRecordBuilderImpl(consensusNow, reversingBehavior, customizer);
        // Only set parent consensus timestamp for child records if one is not provided
        if (!childCategory.equals(HandleContext.TransactionCategory.SCHEDULED)) {
            recordBuilder.parentConsensus(parentConsensusTimestamp);
        } else {
            recordBuilder.exchangeRate(userTxnRecordBuilder.exchangeRate());
        }
        if (!customizer.shouldSuppressRecord()) {
            childRecordBuilders.add(recordBuilder);
        }
        return recordBuilder;
    }

    /**
     * Reverts or removes all child transactions after the given one.
     *
     * <p>Suppose I have a list of 10 child record builders. If the given builder is the last of these 10, then nothing
     * happens, because there are no children after the last one.
     *
     * <p>If the given builder is the next to last of these 10, then if the last one was added by
     * {@link #addChild(Configuration, HandleContext.TransactionCategory)} it will still be in the list, but will have a status of
     * {@link ResponseCodeEnum#REVERTED_SUCCESS} (unless it had another failure mode already), otherwise it will
     * actually be removed from the list.
     *
     * <p>If the given builder is the 5th of these 10, then each builder from the 6th to the 10th will be removed from
     * the list if they were added by {@link #addRemovableChild(Configuration)} or
     * {@link #addRemovableChildWithExternalizationCustomizer(Configuration, ExternalizedRecordCustomizer)}, otherwise they will have their
     * status set to {@link ResponseCodeEnum#REVERTED_SUCCESS} (unless it had another failure mode already).
     *
     * @param recordBuilder the record builder which children need to be reverted
     */
    public void revertChildrenOf(@NonNull final SingleTransactionRecordBuilderImpl recordBuilder) {
        requireNonNull(recordBuilder, "recordBuilder must not be null");
        if (childRecordBuilders == null) {
            childRecordBuilders = new ArrayList<>();
        }
        if (precedingTxnRecordBuilders == null) {
            precedingTxnRecordBuilders = new ArrayList<>();
        }

        // Find the index into the list of records from which to revert. If the record builder is the user transaction,
        // then we start at index 0, which is the first child transaction after the user transaction. If the record
        // builder is not the user transaction AND cannot be found in the list of children, then we have an illegal
        // state -- this should never happen. Otherwise, we start from the index of this builder + 1.
        final int index;
        if (recordBuilder == userTxnRecordBuilder) {
            index = 0;

            // The user transaction fails and therefore we also have to revert preceding transactions
            if (!precedingTxnRecordBuilders.isEmpty()) {
                for (int i = 0; i < precedingTxnRecordBuilders.size(); i++) {
                    final var preceding = precedingTxnRecordBuilders.get(i);
                    if (preceding.reversingBehavior() == ReversingBehavior.REVERSIBLE
                            && SUCCESSES.contains(preceding.status())) {
                        preceding.status(ResponseCodeEnum.REVERTED_SUCCESS);
                    } else if (preceding.reversingBehavior() == ReversingBehavior.REMOVABLE) {
                        precedingTxnRecordBuilders.set(i, null);
                    }
                }
                // Any removable preceding children will come last in the list, so there's
                // no need to eliminate gaps in consensus times even if this returns true
                precedingTxnRecordBuilders.removeIf(Objects::isNull);
            }
        } else {
            // Traverse from end to start, since we are most likely going to be reverting the most recent child,
            // or close to it.
            index = childRecordBuilders.lastIndexOf(recordBuilder) + 1;
            if (index == 0) {
                if (precedingTxnRecordBuilders.contains(recordBuilder)) {
                    return;
                }
                logger.info(
                        " this {}, doesn't have child {} in list {}",
                        System.identityHashCode(this),
                        System.identityHashCode(recordBuilder),
                        childRecordBuilders.stream()
                                .map(System::identityHashCode)
                                .toList());
                throw new IllegalArgumentException("recordBuilder not found");
            }
        }

        // Now that we know from where to begin reverting, walk over all the children and revert or remove them.
        // If we do a remove, we need to shift elements around. This can be quite expensive since we use an array
        // list for our data structure. So we will shift elements as necessary as we walk through the list.
        final var count = childRecordBuilders.size();
        int into = index; // The position in the array into which we should put the next remaining child
        for (int i = index; i < count; i++) {
            final var child = childRecordBuilders.get(i);
            if (child.reversingBehavior() == ReversingBehavior.REMOVABLE) {
                // Remove it from the list by setting its location to null. Then, any subsequent children that are
                // kept will be moved into this position.
                childRecordBuilders.set(i, null);
                followingChildRemoved = true;
            } else {
                if (child.reversingBehavior() == ReversingBehavior.REVERSIBLE) {
                    child.nullOutSideEffectFields();
                    if (SUCCESSES.contains(child.status())) {
                        child.status(ResponseCodeEnum.REVERTED_SUCCESS);
                    }
                }

                if (into != i) {
                    childRecordBuilders.set(into, child);
                    childRecordBuilders.set(i, null);
                }
                into++;
            }
        }

        // I wish ArrayList had some kind of shortcut for this!
        //noinspection ListRemoveInLoop
        for (int i = count - 1; i >= into; i--) {
            childRecordBuilders.remove(i);
        }
    }

    /**
     * Reverts or removes all child transactions after the given checkpoint.
     * If there are no following records in the checkpoint, it means that the revert was executed on the user transaction.
     */
    public void revertChildrenFrom(@NonNull final RecordListCheckPoint checkPoint) {
        requireNonNull(checkPoint, "the record checkpoint must not be null");
        // The revert was executed on the user transaction
        if (checkPoint.lastFollowingRecord() == null) {
            revertChildrenOf(userTxnRecordBuilder);
            return;
        }

        // We get to here when the revert was executed on a child transaction
        // We need to revert all children that were added after the child transaction that was reverted
        revertChildrenOf((SingleTransactionRecordBuilderImpl) checkPoint.lastFollowingRecord());

        // We also need to revert all preceding transactions that were added after the first preceding transaction
        var firstPrecedingRecord = (SingleTransactionRecordBuilderImpl) checkPoint.firstPrecedingRecord();
        if (firstPrecedingRecord != null) {
            final var indexOf = precedingTxnRecordBuilders.indexOf(firstPrecedingRecord) + 1;
            if (indexOf == 0) {
                // This should never happen since the firstPrecedingRecord is not null
                throw new IllegalArgumentException("Preceding recordBuilder not found");
            }
            for (int i = indexOf; i < precedingTxnRecordBuilders.size(); i++) {
                final var preceding = precedingTxnRecordBuilders.get(i);
                if (preceding.reversingBehavior() == ReversingBehavior.REVERSIBLE
                        && SUCCESSES.contains(preceding.status())) {
                    preceding.status(ResponseCodeEnum.REVERTED_SUCCESS);
                } else if (preceding.reversingBehavior() == ReversingBehavior.REMOVABLE) {
                    precedingTxnRecordBuilders.set(i, null);
                }
            }
        }
    }

    /**
     * Builds a list of all records. Assigns transactions IDs as needed.
     *
     * @return A {@link Result} containing the user transaction record and the list of all records in proper order.
     */
    public Result build() {
        final var userTxnRecord = userTxnRecordBuilder.build();
        final var idBuilder = userTxnRecordBuilder.transactionID().copyBuilder();
        final var records = new ArrayList<SingleTransactionRecord>();

        // Add all preceding transactions. The last item in the list has the earliest consensus time, so it needs to
        // be added to "records" first. However, the first item should have a nonce of 1, and the last item should have
        // a nonce of N, where N is the number of preceding transactions.
        // precedingTxnRecordBuilders is not null. It will be 0 in case of null.
        @SuppressWarnings("java:S2259")
        int count = precedingTxnRecordBuilders == null ? 0 : precedingTxnRecordBuilders.size();
        for (int i = count - 1; i >= 0; i--) {
            final SingleTransactionRecordBuilderImpl recordBuilder = precedingTxnRecordBuilders.get(i);
            records.add(recordBuilder
                    .transactionID(idBuilder.nonce(i + 1).build())
                    .syncBodyIdFromRecordId()
                    .build());
        }
        records.add(userTxnRecord);

        int nextNonce = count + 1; // Initialize to be 1 more than the number of preceding items
        count = childRecordBuilders == null ? 0 : childRecordBuilders.size();
        // A dirty hack to match mono-service behavior of always assigning sequential consensus times
        // to contract service child transactions; no real reason this is necessary
        if (followingChildRemoved && count > 0) {
            ensureSequentialConsensusTimes(userTxnRecordBuilder.consensusNow(), childRecordBuilders);
        }
        for (int i = 0; i < count; i++) {
            final SingleTransactionRecordBuilderImpl recordBuilder = childRecordBuilders.get(i);
            // Only create a new transaction ID for child records if one is not provided
            if (recordBuilder.transactionID() == null || TransactionID.DEFAULT.equals(recordBuilder.transactionID())) {
                recordBuilder
                        .transactionID(idBuilder.nonce(nextNonce++).build())
                        .syncBodyIdFromRecordId();
            }
            records.add(recordBuilder.build());
        }
        return new Result(userTxnRecord, unmodifiableList(records));
    }

    private void ensureSequentialConsensusTimes(
            @NonNull final Instant parentConsensusTimestamp,
            @NonNull final List<SingleTransactionRecordBuilderImpl> recordBuilders) {
        for (int i = 0, n = recordBuilders.size(); i < n; i++) {
            recordBuilders.get(i).consensusTimestamp(parentConsensusTimestamp.plusNanos(i + 1L));
        }
    }

    /**
     * The built result of records produced by a single user transaction.
     *
     * @param userTransactionRecord The record for the user transaction.
     * @param records An ordered list of all records, ordered by consensus timestamp. Preceding records come before
     * the user transaction record, which comes before child records.
     */
    public record Result(
            @NonNull SingleTransactionRecord userTransactionRecord, @NonNull List<SingleTransactionRecord> records) {}
}
