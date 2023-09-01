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

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.node.config.data.ConsensusConfig;
import com.swirlds.config.api.Configuration;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * This class manages all record builders that are used while a single user transaction is running.
 * <p>
 * It allows to add record builders for preceding transactions as well as child transactions. Record builders of child
 * transactions can be reverted (respectively in special cases removed). Finally, it can generate a list of all
 * records.
 * <p>
 * As all classes intended to be used within the handle-workflow, this class is <em>not</em> thread-safe.
 */
public class RecordListBuilder {

    private static final String CONFIGURATION_MUST_NOT_BE_NULL = "configuration must not be null";
    private final SingleTransactionRecordBuilderImpl userTxnRecordBuilder;

    private List<SingleTransactionRecordBuilderImpl> precedingTxnRecordBuilders;
    private List<SingleTransactionRecordBuilderImpl> childRecordBuilders;
    private Set<SingleTransactionRecordBuilderImpl> removableChildTxnRecordBuilders;

    /**
     * Creates a new instance with a single record builder for the user transaction.
     *
     * @param recordBuilder the record builder for the user transaction
     * @throws NullPointerException if {@code recordBuilder} is {@code null}
     */
    public RecordListBuilder(@NonNull final SingleTransactionRecordBuilderImpl recordBuilder) {
        this.userTxnRecordBuilder = requireNonNull(recordBuilder, "recordBuilder must not be null");
    }

    /**
     * Returns the main record builder
     *
     * @return the main record builder
     */
    public SingleTransactionRecordBuilderImpl userTransactionRecordBuilder() {
        return userTxnRecordBuilder;
    }

    /**
     * Returns an unmodifiable {@link List} of all preceding record builders.
     *
     * @return all preceding record builders
     */
    public List<SingleTransactionRecordBuilderImpl> precedingRecordBuilders() {
        return precedingTxnRecordBuilders != null
                ? Collections.unmodifiableList(precedingTxnRecordBuilders)
                : List.of();
    }

    /**
     * Returns an unmodifiable {@link List} of all child record builders.
     *
     * @return all child record builders
     */
    public List<SingleTransactionRecordBuilderImpl> childRecordBuilders() {
        return childRecordBuilders != null ? Collections.unmodifiableList(childRecordBuilders) : List.of();
    }

    /**
     * Adds a record builder for a preceding transaction.
     *
     * @param configuration the current configuration
     * @return the record builder for the preceding transaction
     * @throws NullPointerException      if {@code consensusConfig} is {@code null}
     * @throws IndexOutOfBoundsException if no more preceding slots are available
     */
    public SingleTransactionRecordBuilderImpl addPreceding(@NonNull final Configuration configuration) {
        requireNonNull(configuration, CONFIGURATION_MUST_NOT_BE_NULL);
        if (precedingTxnRecordBuilders == null) {
            precedingTxnRecordBuilders = new ArrayList<>();
        }
        final int precedingCount = precedingTxnRecordBuilders.size();
        final var consensusConfig = configuration.getConfigData(ConsensusConfig.class);
        final long maxRecords = consensusConfig.handleMaxPrecedingRecords();
        if (precedingCount >= maxRecords) {
            throw new IndexOutOfBoundsException("No more preceding slots available");
        }

        final var parentConsensusTimestamp = userTxnRecordBuilder.consensusNow();
        final var consensusNow = parentConsensusTimestamp.minusNanos(maxRecords - precedingCount);
        final var recordBuilder =
                new SingleTransactionRecordBuilderImpl(consensusNow).exchangeRate(userTxnRecordBuilder.exchangeRate());

        precedingTxnRecordBuilders.add(recordBuilder);
        return recordBuilder;
    }

    /**
     * Adds a record builder for a child transaction.
     * <p>
     * If a parent transaction of this child transaction is rolled back and the child transaction was successful, the
     * status is set to {@link com.hedera.hapi.node.base.ResponseCodeEnum#REVERTED_SUCCESS}.
     *
     * @param configuration the current configuration
     * @return the record builder for the child transaction
     * @throws NullPointerException      if {@code consensusConfig} is {@code null}
     * @throws IndexOutOfBoundsException if no more child slots are available
     */
    public SingleTransactionRecordBuilderImpl addChild(@NonNull final Configuration configuration) {
        requireNonNull(configuration, CONFIGURATION_MUST_NOT_BE_NULL);

        return doAddChild(configuration);
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
     * @throws NullPointerException      if {@code consensusConfig} is {@code null}
     * @throws IndexOutOfBoundsException if no more child slots are available
     */
    public SingleTransactionRecordBuilderImpl addRemovableChild(@NonNull final Configuration configuration) {
        requireNonNull(configuration, CONFIGURATION_MUST_NOT_BE_NULL);

        final var recordBuilder = doAddChild(configuration);

        if (removableChildTxnRecordBuilders == null) {
            removableChildTxnRecordBuilders = new HashSet<>();
        }
        removableChildTxnRecordBuilders.add(recordBuilder);
        return recordBuilder;
    }

    private SingleTransactionRecordBuilderImpl doAddChild(@NonNull final Configuration configuration) {
        if (childRecordBuilders == null) {
            childRecordBuilders = new ArrayList<>();
        }

        final var childCount = childRecordBuilders.size();
        final var consensusConfig = configuration.getConfigData(ConsensusConfig.class);
        if (childCount >= consensusConfig.handleMaxFollowingRecords()) {
            throw new IndexOutOfBoundsException("No more child slots available");
        }

        final var parentConsensusTimestamp = userTxnRecordBuilder.consensusNow();
        final var previousRecord = childCount == 0 ? userTxnRecordBuilder : childRecordBuilders.get(childCount - 1);
        final var consensusNow = previousRecord.consensusNow().plusNanos(1L);
        final var recordBuilder = new SingleTransactionRecordBuilderImpl(consensusNow)
                .parentConsensus(parentConsensusTimestamp)
                .exchangeRate(userTxnRecordBuilder.exchangeRate());
        childRecordBuilders.add(recordBuilder);
        return recordBuilder;
    }

    /**
     * Reverts all child record builders of the given record builder. Child record builders that have been added
     * with {@link #addRemovableChild(Configuration)} will be removed.
     *
     * @param recordBuilder the record builder which children need to be reverted
     */
    public void revertChildRecordBuilders(@NonNull final SingleTransactionRecordBuilderImpl recordBuilder) {
        requireNonNull(recordBuilder, "recordBuilder must not be null");
        if (childRecordBuilders == null) {
            childRecordBuilders = new ArrayList<>();
        }
        final int index;
        if (recordBuilder == userTxnRecordBuilder) {
            index = 0;
        } else {
            index = childRecordBuilders.indexOf(recordBuilder) + 1;
            if (index == 0) {
                throw new IllegalArgumentException("recordBuilder not found");
            }
        }
        final var children = childRecordBuilders.subList(index, childRecordBuilders.size());
        for (final var it = children.iterator(); it.hasNext(); ) {
            final SingleTransactionRecordBuilderImpl childRecordBuilder = it.next();
            if (removableChildTxnRecordBuilders != null
                    && removableChildTxnRecordBuilders.contains(childRecordBuilder)) {
                it.remove();
                removableChildTxnRecordBuilders.remove(childRecordBuilder);
            } else {
                if (childRecordBuilder.status() == ResponseCodeEnum.OK) {
                    childRecordBuilder.status(ResponseCodeEnum.REVERTED_SUCCESS);
                }
            }
        }
    }

    /**
     * Builds a stream of all records.
     *
     * @return the stream of all records
     */
    public Result build() {
        final var userTxnRecord = userTxnRecordBuilder.build();

        Stream<SingleTransactionRecord> recordStream = Stream.of(userTxnRecord);

        if (precedingTxnRecordBuilders != null) {
            prepareBuilders(precedingTxnRecordBuilders);
            recordStream = Stream.concat(
                    precedingTxnRecordBuilders.stream().map(SingleTransactionRecordBuilderImpl::build), recordStream);
        }

        if (childRecordBuilders != null) {
            prepareBuilders(childRecordBuilders);
            recordStream = Stream.concat(
                    recordStream, childRecordBuilders.stream().map(SingleTransactionRecordBuilderImpl::build));
        }

        return new Result(userTxnRecord, recordStream);
    }

    private void prepareBuilders(@NonNull List<SingleTransactionRecordBuilderImpl> recordBuilders) {
        int nextNonce = 0;
        for (final var recordBuilder : recordBuilders) {
            if (recordBuilder.transactionID() == null) {
                final var transactionID = userTxnRecordBuilder
                        .transactionID()
                        .copyBuilder()
                        .nonce(nextNonce++)
                        .build();
                recordBuilder.transactionID(transactionID);
            }
        }
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

    public record Result(
            @NonNull SingleTransactionRecord userTransactionRecord,
            @NonNull Stream<SingleTransactionRecord> recordStream) {}
}
