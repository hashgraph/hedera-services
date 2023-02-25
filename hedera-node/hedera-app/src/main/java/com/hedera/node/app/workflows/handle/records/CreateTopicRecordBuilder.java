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
import com.hedera.node.app.spi.records.ConsensusCreateTopicRecordBuilder;
import com.hederahashgraph.api.proto.java.TopicID;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;

public class CreateTopicRecordBuilder extends UniversalRecordBuilder<ConsensusCreateTopicRecordBuilder>
        implements ConsensusCreateTopicRecordBuilder {
    private long createdTopicNum = 0;

    /**
     * {@inheritDoc}
     */
    @Override
    protected CreateTopicRecordBuilder self() {
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public ConsensusCreateTopicRecordBuilder setCreatedTopic(final long num) {
        this.createdTopicNum = num;
        return this;
    }

    /**
     * A temporary method to expose the side-effects tracked in this builder to
     * the mono context.
     *
     * @param txnCtx the mono context
     */
    public void exposeSideEffectsToMono(@NonNull final TransactionContext txnCtx) {
        super.exposeSideEffectsToMono(txnCtx);
        if (createdTopicNum != 0) {
            Objects.requireNonNull(txnCtx)
                    .setCreated(
                            TopicID.newBuilder().setTopicNum(createdTopicNum).build());
        }
    }
}
