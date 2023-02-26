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

package com.hedera.node.app.workflows.handle.records;

import com.hedera.node.app.service.mono.context.TransactionContext;
import com.hedera.node.app.spi.records.ConsensusSubmitMessageRecordBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Objects;

public class SubmitMessageRecordBuilder extends UniversalRecordBuilder<ConsensusSubmitMessageRecordBuilder>
        implements ConsensusSubmitMessageRecordBuilder {
    private long newSequenceNumber;

    @Nullable
    private byte[] newRunningHash = null;

    /**
     * {@inheritDoc}
     */
    @Override
    protected SubmitMessageRecordBuilder self() {
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public ConsensusSubmitMessageRecordBuilder setNewTopicMetadata(
            final @NonNull byte[] topicRunningHash,
            final long sequenceNumber,
            // The mono context will (correctly) assume the latest running hash version,
            // but it probably makes more sense to provide it here in the future
            final long runningHashVersion) {
        this.newRunningHash = topicRunningHash;
        this.newSequenceNumber = sequenceNumber;
        return this;
    }

    /**
     * A temporary method to expose the side-effects tracked in this builder to
     * the mono context.
     *
     * @param txnCtx the mono context
     */
    @Deprecated
    public void exposeSideEffectsToMono(@NonNull final TransactionContext txnCtx) {
        super.exposeSideEffectsToMono(txnCtx);
        if (newRunningHash != null) {
            Objects.requireNonNull(txnCtx).setTopicRunningHash(newRunningHash, newSequenceNumber);
        }
    }
}
