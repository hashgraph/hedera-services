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

package com.hedera.node.app.workflows.handle.record;

import static java.util.Collections.unmodifiableList;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.state.SingleTransactionRecord;
import com.hedera.node.config.data.ConsensusConfig;
import com.swirlds.config.api.Configuration;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

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
public final class RecordListBuilder {
    private static final String CONFIGURATION_MUST_NOT_BE_NULL = "configuration must not be null";
    /** The record builder for the user transaction. */
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
     * Creates a new instance with the given user transaction consensus timestamp.
     *
     * @param consensusTimestamp The consensus timestamp of the user transaction
     * @throws NullPointerException if {@code recordBuilder} is {@code null}
     */
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
     * @throws NullPointerException      if {@code consensusConfig} is {@code null}
     * @throws HandleException if no more preceding slots are available
     */
    public SingleTransactionRecordBuilderImpl addPreceding(@NonNull final Configuration configuration) {
        requireNonNull(configuration, CONFIGURATION_MUST_NOT_BE_NULL);

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
        if (precedingCount >= maxRecords) {
            // We do not have a MAX_PRECEDING_RECORDS_EXCEEDED error, so use this.
            throw new HandleException(ResponseCodeEnum.MAX_CHILD_RECORDS_EXCEEDED);
        }

        // The consensus timestamp of the first item in the preceding list is T-1, where T is the time of the
        // user transaction. The second item is T-2, and so on.
        final var parentConsensusTimestamp = userTxnRecordBuilder.consensusNow();
        final var consensusNow = parentConsensusTimestamp.minusNanos(precedingCount + 1L);
        final var recordBuilder =
                new SingleTransactionRecordBuilderImpl(consensusNow).exchangeRate(userTxnRecordBuilder.exchangeRate());
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
     * @throws HandleException      if no more child slots are available
     */
    public SingleTransactionRecordBuilderImpl addChild(@NonNull final Configuration configuration) {
        requireNonNull(configuration, CONFIGURATION_MUST_NOT_BE_NULL);
        return doAddChild(configuration, false);
    }

    /**
     * Adds a record builder for a child transaction that is removed when reverted.
     * <p>
     * If a parent transaction of this child transaction is rolled back, the record builder is removed entirely. This is
     * only needed in a very few special cases. Under normal circumstances,
     * {@link #addChild(Configuration)} should be used.
     *
     * @param configuration the current configuration
     * @return the record builder for the child transaction
     * @throws NullPointerException if {@code consensusConfig} is {@code null}
     * @throws HandleException      if no more child slots are available
     */
    public SingleTransactionRecordBuilderImpl addRemovableChild(@NonNull final Configuration configuration) {
        requireNonNull(configuration, CONFIGURATION_MUST_NOT_BE_NULL);
        return doAddChild(configuration, true);
    }

    private SingleTransactionRecordBuilderImpl doAddChild(
            @NonNull final Configuration configuration, final boolean removable) {
        // FUTURE: We should reuse the RecordListBuilder between handle calls, and we should reuse these lists, in
        // which case we will no longer have to create them lazily.
        if (childRecordBuilders == null) {
            childRecordBuilders = new ArrayList<>();
        }

        // Make sure we have not created so many that we have run out of slots.
        final var childCount = childRecordBuilders.size();
        final var consensusConfig = configuration.getConfigData(ConsensusConfig.class);
        if (childCount >= consensusConfig.handleMaxFollowingRecords()) {
            throw new HandleException(ResponseCodeEnum.MAX_CHILD_RECORDS_EXCEEDED);
        }

        // The consensus timestamp of the first item in the child list is T+1, where T is the time of the user tx
        final var parentConsensusTimestamp = userTxnRecordBuilder.consensusNow();
        final var prevConsensusNow = childRecordBuilders.isEmpty()
                ? userTxnRecordBuilder.consensusNow()
                : childRecordBuilders.get(childRecordBuilders.size() - 1).consensusNow();
        final var consensusNow = prevConsensusNow.plusNanos(1L);
        final var recordBuilder = new SingleTransactionRecordBuilderImpl(consensusNow, removable)
                .parentConsensus(parentConsensusTimestamp)
                .exchangeRate(userTxnRecordBuilder.exchangeRate());
        childRecordBuilders.add(recordBuilder);
        return recordBuilder;
    }

    /**
     * Reverts or removes all child transactions after the given one.
     *
     * <p>Suppose I have a list of 10 child record builders. If the given builder is the last of these 10, then nothing
     * happens, because there are no children after the last one.
     *
     * <p>If the given builder is the next to last of these 10, then if the last one was added by
     * {@link #addChild(Configuration)} it will still be in the list, but will have a status of
     * {@link ResponseCodeEnum#REVERTED_SUCCESS} (unless it had another failure mode already), otherwise it will
     * actually be removed from the list.
     *
     * <p>If the given builder is the 5th of these 10, then each builder from the 6th to the 10th will be removed from
     * the list if they were added by {@link #addRemovableChild(Configuration)}, otherwise they will have their status
     * set to {@link ResponseCodeEnum#REVERTED_SUCCESS} (unless it had another failure mode already).
     *
     * @param recordBuilder the record builder which children need to be reverted
     */
    public void revertChildrenOf(@NonNull final SingleTransactionRecordBuilderImpl recordBuilder) {
        requireNonNull(recordBuilder, "recordBuilder must not be null");
        if (childRecordBuilders == null) {
            childRecordBuilders = new ArrayList<>();
        }

        // Find the index into the list of records from which to revert. If the record builder is the user transaction,
        // then we start at index 0, which is the first child transaction after the user transaction. If the record
        // builder is not the user transaction AND cannot be found in the list of children, then we have an illegal
        // state -- this should never happen. Otherwise, we start from the index of this builder + 1.
        final int index;
        if (recordBuilder == userTxnRecordBuilder) {
            index = 0;
        } else {
            // Traverse from end to start, since we are most likely going to be reverting the most recent child,
            // or close to it.
            index = childRecordBuilders.lastIndexOf(recordBuilder) + 1;
            if (index == 0) {
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
            if (child.removable()) {
                // Remove it from the list by setting its location to null. Then, any subsequent children that are
                // kept will be moved into this position.
                childRecordBuilders.set(i, null);
            } else {
                if (child.status() == ResponseCodeEnum.OK) child.status(ResponseCodeEnum.REVERTED_SUCCESS);

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
        int count = precedingTxnRecordBuilders == null ? 0 : precedingTxnRecordBuilders.size();
        for (int i = count - 1; i >= 0; i--) {
            final var recordBuilder = precedingTxnRecordBuilders.get(i);
            records.add(
                    recordBuilder.transactionID(idBuilder.nonce(i + 1).build()).build());
        }

        records.add(userTxnRecord);

        int nextNonce = count + 1; // Initialize to be 1 more than the number of preceding items
        count = childRecordBuilders == null ? 0 : childRecordBuilders.size();
        for (int i = 0; i < count; i++) {
            final var recordBuilder = childRecordBuilders.get(i);
            records.add(recordBuilder
                    .transactionID(idBuilder.nonce(nextNonce++).build())
                    .build());
        }

        return new Result(userTxnRecord, unmodifiableList(records));
    }

    /*
     * This method is only used for testing. Unfortunately, building records does not work yet.
     * Added this method temporarily to check the content of this object.
     */
    Stream<SingleTransactionRecordBuilderImpl> builders() {
        Stream<SingleTransactionRecordBuilderImpl> recordBuilders = Stream.of(userTxnRecordBuilder);
        if (precedingTxnRecordBuilders != null) {
            recordBuilders = Stream.concat(precedingTxnRecordBuilders.stream(), recordBuilders);
        }
        if (childRecordBuilders != null) {
            recordBuilders = Stream.concat(recordBuilders, childRecordBuilders.stream());
        }
        return recordBuilders;
    }

    /**
     * The built result of records produced by a single user transaction.
     *
     * @param userTransactionRecord The record for the user transaction.
     * @param records An ordered list of all records, ordered by consensus timestamp. Preceding records come before
     *                the user transaction record, which comes before child records.
     */
    public record Result(
            @NonNull SingleTransactionRecord userTransactionRecord, @NonNull List<SingleTransactionRecord> records) {}
}
