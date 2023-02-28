/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.consensus.impl.handlers;

import static java.util.Objects.requireNonNull;

import com.hedera.node.app.service.consensus.impl.ReadableTopicStore;
import com.hedera.node.app.service.consensus.impl.config.ConsensusServiceConfig;
import com.hedera.node.app.service.consensus.impl.records.ConsensusSubmitMessageRecordBuilder;
import com.hedera.node.app.service.consensus.impl.records.SubmitMessageRecordBuilder;
import com.hedera.node.app.spi.meta.HandleContext;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ConsensusSubmitMessageTransactionBody;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * This class contains all workflow-related functionality regarding {@link
 * com.hederahashgraph.api.proto.java.HederaFunctionality#ConsensusSubmitMessage}.
 */
@Singleton
public class ConsensusSubmitMessageHandler implements TransactionHandler {
    @Inject
    public ConsensusSubmitMessageHandler() {}

    /**
     * This method is called during the pre-handle workflow.
     *
     * <p>Determines signatures needed for submitting a new message to a consensus topic
     *
     * @param context the {@link PreHandleContext} which collects all information that will be
     *     passed to {@code handle()}
     * @param topicStore the {@link ReadableTopicStore} to use to resolve topic metadata
     * @throws NullPointerException if one of the arguments is {@code null}
     */
    public void preHandle(@NonNull final PreHandleContext context, @NonNull ReadableTopicStore topicStore) {
        requireNonNull(context);
        requireNonNull(topicStore);

        final var op = context.getTxn().getConsensusSubmitMessage();
        final var topicMeta = topicStore.getTopicMetadata(op.getTopicID());
        if (topicMeta.failed()) {
            context.status(ResponseCodeEnum.INVALID_TOPIC_ID);
            return;
        }

        final var submitKey = topicMeta.metadata().submitKey();
        if (submitKey.isEmpty()) {
            context.status(ResponseCodeEnum.UNAUTHORIZED);
            return;
        }
        context.addToReqNonPayerKeys(submitKey.get());
    }

    /**
     * Given the appropriate context, submits a message to a topic.
     *
     * TODO: Provide access to writable topic store.
     *
     * @param handleContext the {@link HandleContext} for the active transaction
     * @param submitMessage the {@link ConsensusSubmitMessageTransactionBody} of the active transaction
     * @param consensusServiceConfig the {@link ConsensusServiceConfig} for the active transaction
     * @param recordBuilder the {@link ConsensusSubmitMessageRecordBuilder} for the active transaction
     * @throws NullPointerException if one of the arguments is {@code null}
     */
    public void handle(
            @NonNull final HandleContext handleContext,
            @NonNull final ConsensusSubmitMessageTransactionBody submitMessage,
            @NonNull final ConsensusServiceConfig consensusServiceConfig,
            @NonNull final ConsensusSubmitMessageRecordBuilder recordBuilder) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public ConsensusSubmitMessageRecordBuilder newRecordBuilder() {
        return new SubmitMessageRecordBuilder();
    }
}
