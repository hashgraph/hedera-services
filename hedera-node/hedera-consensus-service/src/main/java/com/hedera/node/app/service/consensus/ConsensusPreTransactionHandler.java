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
package com.hedera.node.app.service.consensus;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.consensus.ConsensusCreateTopicTransactionBody;
import com.hedera.hapi.node.consensus.ConsensusDeleteTopicTransactionBody;
import com.hedera.hapi.node.consensus.ConsensusSubmitMessageTransactionBody;
import com.hedera.hapi.node.consensus.ConsensusUpdateTopicTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.spi.PreTransactionHandler;
import com.hedera.node.app.spi.meta.TransactionMetadata;

/**
 * The pre-handler for the HAPI <a
 * href="https://github.com/hashgraph/hedera-protobufs/blob/main/services/consensus_service.proto">Consensus
 * Service</a>.
 */
public interface ConsensusPreTransactionHandler extends PreTransactionHandler {
    /**
     * Pre-handles a {@link HederaFunctionality#CONSENSUS_CREATE_TOPIC} transaction, returning the
     * metadata required to, at minimum, validate the signatures of all required signing keys.
     *
     * @param txn a transaction with a {@link ConsensusCreateTopicTransactionBody}
     * @param payer payer of the transaction
     * @return the metadata for the topic creation
     */
    TransactionMetadata preHandleCreateTopic(TransactionBody txn, AccountID payer);

    /**
     * Pre-handles a {@link HederaFunctionality#CONSENSUS_UPDATE_TOPIC} transaction, returning the
     * metadata required to, at minimum, validate the signatures of all required signing keys.
     *
     * @param txn a transaction with a {@link ConsensusUpdateTopicTransactionBody}
     * @param payer payer of the transaction
     * @return the metadata for the topic update
     */
    TransactionMetadata preHandleUpdateTopic(TransactionBody txn, AccountID payer);

    /**
     * Pre-handles a {@link HederaFunctionality#CONSENSUS_DELETE_TOPIC} transaction, returning the
     * metadata required to, at minimum, validate the signatures of all required signing keys.
     *
     * @param txn a transaction with a {@link ConsensusDeleteTopicTransactionBody}
     * @param payer payer of the transaction
     * @return the metadata for the topic delete
     */
    TransactionMetadata preHandleDeleteTopic(TransactionBody txn, AccountID payer);

    /**
     * Pre-handles a {@link HederaFunctionality#CONSENSUS_SUBMIT_MESSAGE} transaction, returning the
     * metadata required to, at minimum, validate the signatures of all required signing keys.
     *
     * @param txn a transaction with a {@link ConsensusSubmitMessageTransactionBody}
     * @param payer payer of the transaction
     * @return the metadata for the topic message submission
     */
    TransactionMetadata preHandleSubmitMessage(TransactionBody txn, AccountID payer);
}
