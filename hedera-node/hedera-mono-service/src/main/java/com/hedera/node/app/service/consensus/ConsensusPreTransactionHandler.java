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

public interface ConsensusPreTransactionHandler extends PreTransactionHandler {
    /**
     * Create a topic to be used for consensus. If an autoRenewAccount is specified, that account
     * must also sign this transaction. If an adminKey is specified, the adminKey must sign the
     * transaction. On success, the resulting TransactionReceipt contains the newly created TopicId.
     * Request is [ConsensusCreateTopicTransactionBody](#proto.ConsensusCreateTopicTransactionBody)
     */
    TransactionMetadata preHandleCreateTopic(TransactionBody txn);

    /**
     * Update a topic. If there is no adminKey, the only authorized update (available to anyone) is
     * to extend the expirationTime. Otherwise transaction must be signed by the adminKey. If an
     * adminKey is updated, the transaction must be signed by the pre-update adminKey and
     * post-update adminKey. If a new autoRenewAccount is specified (not just being removed), that
     * account must also sign the transaction. Request is
     * [ConsensusUpdateTopicTransactionBody](#proto.ConsensusUpdateTopicTransactionBody)
     */
    TransactionMetadata preHandleUpdateTopic(TransactionBody txn);

    /**
     * Delete a topic. No more transactions or queries on the topic (via HAPI) will succeed. If an
     * adminKey is set, this transaction must be signed by that key. If there is no adminKey, this
     * transaction will fail UNAUTHORIZED. Request is
     * [ConsensusDeleteTopicTransactionBody](#proto.ConsensusDeleteTopicTransactionBody)
     */
    TransactionMetadata preHandleDeleteTopic(TransactionBody txn);

    /**
     * Submit a message for consensus. Valid and authorized messages on valid topics will be ordered
     * by the consensus service, gossipped to the mirror net, and published (in order) to all
     * subscribers (from the mirror net) on this topic. The submitKey (if any) must sign this
     * transaction. On success, the resulting TransactionReceipt contains the topic's updated
     * topicSequenceNumber and topicRunningHash. Request is
     * [ConsensusSubmitMessageTransactionBody](#proto.ConsensusSubmitMessageTransactionBody)
     */
    TransactionMetadata preHandleSubmitMessage(TransactionBody txn);
}
