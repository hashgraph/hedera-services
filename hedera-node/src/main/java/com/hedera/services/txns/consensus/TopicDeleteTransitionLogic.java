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
import com.hedera.services.store.TopicStore;
import com.hedera.services.store.models.Id;
import com.hedera.services.txns.TransitionLogic;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.function.Function;
import java.util.function.Predicate;

import static com.hedera.services.exceptions.ValidationUtils.validateTrue;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.UNAUTHORIZED;

@Singleton
public class TopicDeleteTransitionLogic implements TransitionLogic {
	private static final Function<TransactionBody, ResponseCodeEnum> SEMANTIC_RUBBER_STAMP = ignore -> OK;

	private final TransactionContext transactionContext;
	private final TopicStore store;

	@Inject
	public TopicDeleteTransitionLogic(
			TopicStore store,
			TransactionContext transactionContext
	) {
		this.store = store;
		this.transactionContext = transactionContext;
	}

	@Override
	public void doStateTransition() {
		/* --- Extract gRPC --- */
		var op = transactionContext.accessor().getTxn().getConsensusDeleteTopic();

		/* --- Do the business logic --- */
		var topicId = Id.fromGrpcTopic(op.getTopicID());
		var topic = store.loadTopic(topicId);
		validateTrue(topic.getAdminKey() != null, UNAUTHORIZED);
		topic.setDeleted(true);

		/* --- Persist the changes --- */
		store.persistTopic(topic);
	}

	@Override
	public Predicate<TransactionBody> applicability() {
		return TransactionBody::hasConsensusDeleteTopic;
	}

	/**
	 * No transaction-specific pre-consensus checks.
	 */
	@Override
	public Function<TransactionBody, ResponseCodeEnum> semanticCheck() {
		return SEMANTIC_RUBBER_STAMP;
	}
}
