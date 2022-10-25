/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.node.app.service.consensus;

import com.hedera.node.app.spi.PreTransactionHandler;
import com.hedera.node.app.spi.meta.TransactionMetadata;
import com.hederahashgraph.api.proto.java.TransactionBody;

/**
 * The pre-handler for the HAPI <a
 * href="https://github.com/hashgraph/hedera-protobufs/blob/main/services/consensus_service.proto">Consensus
 * Service</a>.
 */
public interface ConsensusPreTransactionHandler extends PreTransactionHandler {
    /**
     * Pre-handles a {@link
     * com.hederahashgraph.api.proto.java.HederaFunctionality#ConsensusCreateTopic} transaction,
     * returning the metadata required to, at minimum, validate the signatures of all required
     * signing keys.
     *
     * @param txn a transaction with a {@link
     *     com.hederahashgraph.api.proto.java.ConsensusCreateTopicTransactionBody}
     * @return the metadata for the topic creation
     */
    TransactionMetadata preHandleCreateTopic(TransactionBody txn);

    /**
     * Pre-handles a {@link
     * com.hederahashgraph.api.proto.java.HederaFunctionality#ConsensusUpdateTopic} transaction,
     * returning the metadata required to, at minimum, validate the signatures of all required
     * signing keys.
     *
     * @param txn a transaction with a {@link
     *     com.hederahashgraph.api.proto.java.ConsensusUpdateTopicTransactionBody}
     * @return the metadata for the topic update
     */
    TransactionMetadata preHandleUpdateTopic(TransactionBody txn);

    /**
     * Pre-handles a {@link
     * com.hederahashgraph.api.proto.java.HederaFunctionality#ConsensusDeleteTopic} transaction,
     * returning the metadata required to, at minimum, validate the signatures of all required
     * signing keys.
     *
     * @param txn a transaction with a {@link
     *     com.hederahashgraph.api.proto.java.ConsensusDeleteTopicTransactionBody}
     * @return the metadata for the topic delete
     */
    TransactionMetadata preHandleDeleteTopic(TransactionBody txn);

    /**
     * Pre-handles a {@link
     * com.hederahashgraph.api.proto.java.HederaFunctionality#ConsensusSubmitMessage} transaction,
     * returning the metadata required to, at minimum, validate the signatures of all required
     * signing keys.
     *
     * @param txn a transaction with a {@link
     *     com.hederahashgraph.api.proto.java.ConsensusSubmitMessageTransactionBody}
     * @return the metadata for the topic message submission
     */
    TransactionMetadata preHandleSubmitMessage(TransactionBody txn);
}
