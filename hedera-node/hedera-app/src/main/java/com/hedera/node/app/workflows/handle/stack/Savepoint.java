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

package com.hedera.node.app.workflows.handle.stack;

import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.record.ExternalizedRecordCustomizer;
import com.hedera.node.app.spi.workflows.record.SingleTransactionBuilder;
import com.hedera.node.app.spi.workflows.record.SingleTransactionBuilder.ReversingBehavior;
import com.swirlds.state.HederaState;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * A transactional unit that buffers all changes to a wrapped Hedera state, along with any stream item builders
 * whose lifecycle is tied to that of the state changes. May be committed or rolled back.
 */
public interface Savepoint extends BuilderSink {
    /**
     * The that this savepoint is buffering changes for, including all modifications made so far.
     *
     * @return the state
     */
    HederaState state();

    /**
     * Rolls back all changes made in this savepoint, making any necessary changes to the stream item builders
     * this savepoint is managing.
     * <p>
     * <b>Important:</b> Unlike transactional management of state changes, which simply discards everything,
     * management of stream item builders includes flushing builders not of type {@link ReversingBehavior#REMOVABLE}
     * to the parent sink of the savepoint.
     */
    void rollback();

    /**
     * Commits all changes made in this savepoint and flushes any stream item builders accumulated in this savepoint
     * to the parent sink of the savepoint.
     */
    void commit();

    /**
     * Creates and returns a new savepoint that represents a new transactional unit based on this savepoint's
     * modifications to state.
     * @return the new savepoint
     */
    Savepoint createFollowingSavepoint();

    /**
     * Creates and returns a new stream item builder whose lifecycle will be scoped to the state changes made
     * in this savepoint.
     *
     * @param reversingBehavior the reversing behavior to apply to the builder on rollback
     * @param txnCategory       the category of transaction initiating the new builder
     * @param customizer        the customizer to apply when externalizing the builder
     * @param isBaseBuilder     whether the builder is the base builder for a stack
     * @return the new builder
     */
    SingleTransactionBuilder createBuilder(
            @NonNull ReversingBehavior reversingBehavior,
            @NonNull HandleContext.TransactionCategory txnCategory,
            @NonNull ExternalizedRecordCustomizer customizer,
            boolean isBaseBuilder);
}
