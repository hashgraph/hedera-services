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
import com.hedera.services.state.merkle.MerkleTopic;
import com.hedera.services.txns.TransitionLogic;
import com.hedera.services.txns.validation.OptionValidator;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.swirlds.fcmap.FCMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.*;

public class TopicDeleteTransitionLogic implements TransitionLogic {
	protected static final Logger log = LogManager.getLogger(TopicDeleteTransitionLogic.class);

	private static final Function<TransactionBody, ResponseCodeEnum> SYNTAX_RUBBER_STAMP = ignore -> OK;

	private final Supplier<FCMap<MerkleEntityId, MerkleTopic>> topics;
	private final OptionValidator validator;
	private final TransactionContext transactionContext;

	public TopicDeleteTransitionLogic(
			Supplier<FCMap<MerkleEntityId, MerkleTopic>> topics,
			OptionValidator validator,
			TransactionContext transactionContext
	) {
		this.topics = topics;
		this.validator = validator;
		this.transactionContext = transactionContext;
	}

	@Override
	public void doStateTransition() {
		var op = transactionContext.accessor().getTxn().getConsensusDeleteTopic();
		var topicId = op.getTopicID();

		var topicStatus = validator.queryableTopicStatus(topicId, topics.get());
		if (OK != topicStatus) {
			// Should not get here as the adminKey lookup should have failed.
			transactionContext.setStatus(topicStatus);
			return;
		}

		var topicMapKey = MerkleEntityId.fromTopicId(topicId);
		var topic = topics.get().get(topicMapKey);
		if (!topic.hasAdminKey()) {
			// Topics without adminKeys can't be deleted.
			transactionContext.setStatus(UNAUTHORIZED);
			return;
		}

		var mutableTopic = topics.get().getForModify(topicMapKey);
		mutableTopic.setDeleted(true);
		topics.get().put(topicMapKey, mutableTopic);

		transactionContext.setStatus(SUCCESS);
	}

	@Override
	public Predicate<TransactionBody> applicability() {
		return TransactionBody::hasConsensusDeleteTopic;
	}

	/**
	 * No transaction-specific pre-consensus checks.
	 */
	@Override
	public Function<TransactionBody, ResponseCodeEnum> syntaxCheck() {
		return SYNTAX_RUBBER_STAMP;
	}
}
