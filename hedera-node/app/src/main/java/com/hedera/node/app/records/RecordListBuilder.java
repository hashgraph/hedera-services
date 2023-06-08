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

package com.hedera.node.app.records;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.node.app.spi.records.SingleTransactionRecord;
import com.hedera.node.config.data.ConsensusConfig;
import com.swirlds.config.api.Configuration;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
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

    private final List<SingleTransactionRecordBuilder> recordBuilders = new ArrayList<>();

    private List<SingleTransactionRecordBuilder> precedingRecordBuilders;
    private Set<SingleTransactionRecordBuilder> removableChildRecordBuilders;

    /**
     * Creates a new instance with a single record builder for the user transaction.
     *
     * @param recordBuilder the record builder for the user transaction
     * @throws NullPointerException if {@code recordBuilder} is {@code null}
     */
    public RecordListBuilder(@NonNull final SingleTransactionRecordBuilder recordBuilder) {
        requireNonNull(recordBuilder, "recordBuilder must not be null");
        recordBuilders.add(recordBuilder);
    }

    /**
     * Adds a record builder for a preceding transaction.
     *
     * @param configuration the current configuration
     * @return the record builder for the preceding transaction
     * @throws NullPointerException if {@code consensusConfig} is {@code null}
     * @throws IndexOutOfBoundsException if no more preceding slots are available
     */
    public SingleTransactionRecordBuilder addPreceding(@NonNull final Configuration configuration) {
        requireNonNull(configuration, "configuration must not be null");
        if (precedingRecordBuilders == null) {
            precedingRecordBuilders = new ArrayList<>();
        }
        final int precedingCount = precedingRecordBuilders.size();
        final var consensusConfig = configuration.getConfigData(ConsensusConfig.class);
        final long maxRecords = consensusConfig.handleMaxPrecedingRecords();
        if (precedingCount >= maxRecords) {
            throw new IndexOutOfBoundsException("No more preceding slots available");
        }

        final var consensusNow = precedingCount == 0
                ? recordBuilders.get(0).consensusNow().minusNanos(maxRecords)
                : precedingRecordBuilders.get(precedingCount - 1).consensusNow().plusNanos(1L);
        final var recordBuilder = new SingleTransactionRecordBuilder(consensusNow);

        precedingRecordBuilders.add(recordBuilder);
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
     * @throws NullPointerException if {@code consensusConfig} is {@code null}
     * @throws IndexOutOfBoundsException if no more child slots are available
     */
    public SingleTransactionRecordBuilder addChild(@NonNull final Configuration configuration) {
        requireNonNull(configuration, "configuration must not be null");

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
     * @throws NullPointerException if {@code consensusConfig} is {@code null}
     * @throws IndexOutOfBoundsException if no more child slots are available
     */
    public SingleTransactionRecordBuilder addRemovableChild(@NonNull final Configuration configuration) {
        requireNonNull(configuration, "configuration must not be null");

        final var recordBuilder = doAddChild(configuration);

        if (removableChildRecordBuilders == null) {
            removableChildRecordBuilders = new HashSet<>();
        }
        removableChildRecordBuilders.add(recordBuilder);
        return recordBuilder;
    }

    private SingleTransactionRecordBuilder doAddChild(@NonNull final Configuration configuration) {
        final int childCount = recordBuilders.size();
        final var consensusConfig = configuration.getConfigData(ConsensusConfig.class);
        if (childCount > consensusConfig.handleMaxFollowingRecords()) {
            throw new IndexOutOfBoundsException("No more child slots available");
        }

        final var consensusNow =
                recordBuilders.get(childCount - 1).consensusNow().plusNanos(1L);
        final var recordBuilder = new SingleTransactionRecordBuilder(consensusNow);

        recordBuilders.add(recordBuilder);
        return recordBuilder;
    }

    /**
     * Reverts all child record builders of the given record builder. Child record builders that have been added
     * with {@link #addRemovableChild(Configuration)} will be removed.
     *
     * @param recordBuilder the record builder which children need to be reverted
     */
    public void revertChildRecordBuilders(@NonNull final SingleTransactionRecordBuilder recordBuilder) {
        requireNonNull(recordBuilder, "recordBuilder must not be null");
        final int index = recordBuilders.indexOf(recordBuilder);
        if (index < 0) {
            throw new IllegalArgumentException("recordBuilder not found");
        }
        final var children = recordBuilders.subList(index + 1, recordBuilders.size());
        for (final var it = children.iterator(); it.hasNext(); ) {
            final SingleTransactionRecordBuilder childRecordBuilder = it.next();
            if (removableChildRecordBuilders != null && removableChildRecordBuilders.contains(childRecordBuilder)) {
                it.remove();
                removableChildRecordBuilders.remove(childRecordBuilder);
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
    public Stream<SingleTransactionRecord> build() {
        final var stream = precedingRecordBuilders == null
                ? recordBuilders.stream()
                : Stream.concat(precedingRecordBuilders.stream(), recordBuilders.stream());
        return stream.map(SingleTransactionRecordBuilder::build);
    }

    /*
     * This method is only used for testing. Unfortunately, building records does not work yet.
     * Added this method temporarily to check the content of this object.
     */
    Stream<SingleTransactionRecordBuilder> builders() {
        return precedingRecordBuilders == null
                ? recordBuilders.stream()
                : Stream.concat(precedingRecordBuilders.stream(), recordBuilders.stream());
    }
}
