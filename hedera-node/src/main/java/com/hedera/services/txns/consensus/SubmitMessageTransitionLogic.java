/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.txns.consensus;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_CHUNK_NUMBER;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_CHUNK_TRANSACTION_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOPIC_MESSAGE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MESSAGE_SIZE_TOO_LARGE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

import com.hedera.services.context.TransactionContext;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.state.merkle.MerkleTopic;
import com.hedera.services.txns.TransitionLogic;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.accessors.custom.SubmitMessageAccessor;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.swirlds.merkle.map.MerkleMap;
import java.io.IOException;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Singleton
public class SubmitMessageTransitionLogic implements TransitionLogic {
    private static final Logger log = LogManager.getLogger(SubmitMessageTransitionLogic.class);

    private final OptionValidator validator;
    private final TransactionContext txnCtx;
    private final Supplier<MerkleMap<EntityNum, MerkleTopic>> topics;
    private final GlobalDynamicProperties globalDynamicProperties;

    @Inject
    public SubmitMessageTransitionLogic(
            Supplier<MerkleMap<EntityNum, MerkleTopic>> topics,
            OptionValidator validator,
            TransactionContext txnCtx,
            GlobalDynamicProperties globalDynamicProperties) {
        this.topics = topics;
        this.validator = validator;
        this.txnCtx = txnCtx;
        this.globalDynamicProperties = globalDynamicProperties;
    }

    @Override
    public void doStateTransition() {
        final var accessor = (SubmitMessageAccessor) txnCtx.swirldsTxnAccessor().getDelegate();
        final var message = accessor.message();
        final var topic = accessor.topicId();
        final var hasChunkInfo = accessor.hasChunkInfo();
        final var chunkInfo = accessor.chunkInfo();
        final var payer = accessor.getPayer();
        final var txnId = accessor.getTxnId();

        // Simple validations depending on the txn body, should be moved to pre-check in future PR
        if (message.isEmpty()) {
            txnCtx.setStatus(INVALID_TOPIC_MESSAGE);
            return;
        }

        if (message.size() > globalDynamicProperties.messageMaxBytesAllowed()) {
            txnCtx.setStatus(MESSAGE_SIZE_TOO_LARGE);
            return;
        }

        var topicStatus = validator.queryableTopicStatus(topic, topics.get());
        if (OK != topicStatus) {
            txnCtx.setStatus(topicStatus);
            return;
        }

        if (hasChunkInfo) {
            if (!(1 <= chunkInfo.getNumber() && chunkInfo.getNumber() <= chunkInfo.getTotal())) {
                txnCtx.setStatus(INVALID_CHUNK_NUMBER);
                return;
            }
            // tbd : handle custom payer here
            if (!chunkInfo.getInitialTransactionID().getAccountID().equals(payer)) {
                txnCtx.setStatus(INVALID_CHUNK_TRANSACTION_ID);
                return;
            }
            if (1 == chunkInfo.getNumber() && !chunkInfo.getInitialTransactionID().equals(txnId)) {
                txnCtx.setStatus(INVALID_CHUNK_TRANSACTION_ID);
                return;
            }
        }

        var topicId = EntityNum.fromTopicId(topic);
        var mutableTopic = topics.get().getForModify(topicId);
        try {
            mutableTopic.updateRunningHashAndSequenceNumber(
                    // tbd : handle custom payer here
                    payer, message.toByteArray(), topic, txnCtx.consensusTime());
            txnCtx.setTopicRunningHash(
                    mutableTopic.getRunningHash(), mutableTopic.getSequenceNumber());
            txnCtx.setStatus(SUCCESS);
        } catch (IOException e) {
            log.error("Updating topic running hash failed.", e);
            txnCtx.setStatus(INVALID_TRANSACTION);
        }
    }

    @Override
    public Predicate<TransactionBody> applicability() {
        return TransactionBody::hasConsensusSubmitMessage;
    }
}
