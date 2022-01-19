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
import com.hedera.services.ledger.accounts.AliasManager;
import com.hedera.services.state.merkle.MerkleTopic;
import com.hedera.services.txns.TransitionLogic;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.swirlds.merkle.map.MerkleMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_CHUNK_NUMBER;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_CHUNK_TRANSACTION_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOPIC_MESSAGE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MESSAGE_SIZE_TOO_LARGE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

@Singleton
public class SubmitMessageTransitionLogic implements TransitionLogic {
	private static final Logger log = LogManager.getLogger(SubmitMessageTransitionLogic.class);

	private static final Function<TransactionBody, ResponseCodeEnum> SEMANTIC_RUBBER_STAMP = ignore -> OK;

	private final OptionValidator validator;
	private final TransactionContext transactionContext;
	private final Supplier<MerkleMap<EntityNum, MerkleTopic>> topics;
	private final GlobalDynamicProperties globalDynamicProperties;
	private final AliasManager aliasManager;

	@Inject
	public SubmitMessageTransitionLogic(
			final Supplier<MerkleMap<EntityNum, MerkleTopic>> topics,
			final OptionValidator validator,
			final TransactionContext transactionContext,
			final GlobalDynamicProperties globalDynamicProperties,
			final AliasManager aliasManager
	) {
		this.topics = topics;
		this.validator = validator;
		this.transactionContext = transactionContext;
		this.globalDynamicProperties = globalDynamicProperties;
		this.aliasManager = aliasManager;
	}

	@Override
	public void doStateTransition() {
		final var transactionBody = transactionContext.accessor().getTxn();
		final var op = transactionBody.getConsensusSubmitMessage();
		final var txnId = transactionBody.getTransactionID();
		final var payer = aliasManager.lookUpPayerAccountID(txnId.getAccountID()).resolvedId();

		if (op.getMessage().isEmpty()) {
			transactionContext.setStatus(INVALID_TOPIC_MESSAGE);
			return;
		}

		if (op.getMessage().size() > globalDynamicProperties.messageMaxBytesAllowed()) {
			transactionContext.setStatus(MESSAGE_SIZE_TOO_LARGE);
			return;
		}

		final var topicStatus = validator.queryableTopicStatus(op.getTopicID(), topics.get());
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

			final var initialTxnId = chunkInfo.getInitialTransactionID();
			final var chunkAccountIDLookup = aliasManager.lookUpPayerAccountID(initialTxnId.getAccountID());

			if (
					chunkAccountIDLookup.response() != OK ||
					!chunkAccountIDLookup.resolvedId().equals(payer) ||
					(1 == chunkInfo.getNumber() && !initialTxnId.equals(txnId))
			) {
				transactionContext.setStatus(INVALID_CHUNK_TRANSACTION_ID);
				return;
			}
		}

		var topicId = EntityNum.fromTopicId(op.getTopicID());
		var mutableTopic = topics.get().getForModify(topicId);
		try {
			mutableTopic.updateRunningHashAndSequenceNumber(
					payer,
					op.getMessage().toByteArray(),
					op.getTopicID(),
					transactionContext.consensusTime());
			transactionContext.setTopicRunningHash(mutableTopic.getRunningHash(), mutableTopic.getSequenceNumber());
			transactionContext.setStatus(SUCCESS);
		} catch (IOException e) {
			log.error("Updating topic running hash failed.", e);
			transactionContext.setStatus(INVALID_TRANSACTION);
		}
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
