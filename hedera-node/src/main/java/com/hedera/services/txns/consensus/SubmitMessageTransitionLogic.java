package com.hedera.services.txns.consensus;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import com.hedera.services.context.TransactionContext;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.store.TopicStore;
import com.hedera.services.store.models.Id;
import com.hedera.services.txns.TransitionLogic;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.function.Function;
import java.util.function.Predicate;

import static com.hedera.services.exceptions.ValidationUtils.validateFalse;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_CHUNK_NUMBER;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_CHUNK_TRANSACTION_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOPIC_MESSAGE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MESSAGE_SIZE_TOO_LARGE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

@Singleton
public class SubmitMessageTransitionLogic implements TransitionLogic {
	private static final Logger log = LogManager.getLogger(SubmitMessageTransitionLogic.class);

	private static final Function<TransactionBody, ResponseCodeEnum> SEMANTIC_RUBBER_STAMP = ignore -> OK;

	private final TransactionContext transactionContext;
	private final GlobalDynamicProperties globalDynamicProperties;
	private final TopicStore topicStore;

	@Inject
	public SubmitMessageTransitionLogic(
			final TransactionContext transactionContext,
			final GlobalDynamicProperties globalDynamicProperties,
			final TopicStore topicStore) {
		this.topicStore = topicStore;
		this.transactionContext = transactionContext;
		this.globalDynamicProperties = globalDynamicProperties;
	}

	@Override
	public void doStateTransition() {
		var transactionBody = transactionContext.accessor().getTxn();
		var op = transactionBody.getConsensusSubmitMessage();
		/* --- Extract from gRPC ---*/
		final var message = op.getMessage();
		final var topicId = Id.fromGrpcTopic(op.getTopicID());
		final var payer = Id.fromGrpcAccount(transactionBody.getTransactionID().getAccountID());
		final var consensusTimestamp = transactionContext.consensusTime();
		
		/* --- Validate ---*/
		validateFalse(message.isEmpty(), INVALID_TOPIC_MESSAGE);
		validateFalse(message.size() > globalDynamicProperties.messageMaxBytesAllowed(), MESSAGE_SIZE_TOO_LARGE);
		/* --- load the model here, so we can maintain the order of validations --- */
		final var topic = topicStore.loadTopic(topicId);
		if (op.hasChunkInfo()) {
			var chunkInfo = op.getChunkInfo();
			validateFalse(!(1 <= chunkInfo.getNumber() && chunkInfo.getNumber() <= chunkInfo.getTotal()), INVALID_CHUNK_NUMBER);
			validateFalse(!chunkInfo.getInitialTransactionID().getAccountID().equals(
					transactionBody.getTransactionID().getAccountID()), INVALID_CHUNK_TRANSACTION_ID);
			validateFalse(1 == chunkInfo.getNumber() &&
					!chunkInfo.getInitialTransactionID().equals(transactionBody.getTransactionID()), INVALID_CHUNK_TRANSACTION_ID);
		}

		/* --- Do the business logic --- */
		final var messageBytes = message.toByteArray();
		topic.updateRunningHashAndSeqNo(
				payer,
				messageBytes,
				topicId,
				consensusTimestamp
		);
		/* --- Persist the updated model ---*/
		topicStore.persistTopic(topic);
	}

	@Override
	public Predicate<TransactionBody> applicability() {
		return TransactionBody::hasConsensusSubmitMessage;
	}

	@Override
	public Function<TransactionBody, ResponseCodeEnum> semanticCheck() {
		return SEMANTIC_RUBBER_STAMP;
	}
}
