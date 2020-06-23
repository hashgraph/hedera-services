package com.hedera.services.txns.consensus;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
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
import com.hedera.services.context.domain.topic.Topic;
import com.hedera.services.txns.TransitionLogic;
import com.hedera.services.txns.validation.OptionValidator;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hedera.services.legacy.core.MapKey;
import com.swirlds.fcmap.FCMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.function.Function;
import java.util.function.Predicate;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.*;

public class SubmitMessageTransitionLogic implements TransitionLogic {
	protected static final Logger log = LogManager.getLogger(SubmitMessageTransitionLogic.class);

	private static final Function<TransactionBody, ResponseCodeEnum> SYNTAX_RUBBER_STAMP = ignore -> OK;

	private final FCMap<MapKey, Topic> topics;
	private final OptionValidator validator;
	private final TransactionContext transactionContext;

	public SubmitMessageTransitionLogic(FCMap<MapKey, Topic> topics, OptionValidator validator,
										TransactionContext transactionContext) {
		this.topics = topics;
		this.validator = validator;
		this.transactionContext = transactionContext;
	}

	@Override
	public void doStateTransition() {
		var transactionBody = transactionContext.accessor().getTxn();
		var op = transactionBody.getConsensusSubmitMessage();
		var topicId = op.getTopicID();

		if (op.getMessage().isEmpty()) {
			transactionContext.setStatus(INVALID_TOPIC_MESSAGE);
			return;
		}

		var topicStatus = validator.queryableTopicStatus(topicId, topics);
		if (OK != topicStatus) {
			transactionContext.setStatus(topicStatus);
			return;
		}

		if (op.hasChunkInfo()) {
			var chunkInfo = op.getChunkInfo();
			if (!(1 <= chunkInfo.getNumber() && chunkInfo.getNumber() <= chunkInfo.getTotal())) {
				transactionContext.setStatus(INVALID_CHUNK_NUMBER);
				return;
			}
			if (chunkInfo.getInitialTransactionID().getAccountID() !=
					transactionBody.getTransactionID().getAccountID()) {
				transactionContext.setStatus(INVALID_CHUNK_TRANSACTION_ID);
				return;
			}
		}

		var topicMapKey = MapKey.getMapKey(topicId);
		var topic = topics.get(topicMapKey);
		try {
			var updatedTopic = new Topic(topic);
			updatedTopic.updateRunningHashAndSequenceNumber(op.getMessage().toByteArray(), topicId,
					transactionContext.consensusTime());

			topics.put(topicMapKey, updatedTopic);
			transactionContext.setTopicRunningHash(updatedTopic.getRunningHash(), updatedTopic.getSequenceNumber());
			transactionContext.setStatus(SUCCESS);
		} catch (Exception e) {
			// Should not hit this - updateRunningHash should not throw due to NoSuchAlgorithmException (SHA384)
			log.error("Updating topic running hash failed.", e);
			transactionContext.setStatus(INVALID_TRANSACTION);
		}
	}

	@Override
	public Predicate<TransactionBody> applicability() {
		return TransactionBody::hasConsensusSubmitMessage;
	}

	@Override
	public Function<TransactionBody, ResponseCodeEnum> syntaxCheck() {
		return SYNTAX_RUBBER_STAMP;
	}
}
