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
import com.hedera.services.context.domain.haccount.HederaAccount;
import com.hedera.services.context.domain.topic.Topic;
import com.hedera.services.txns.TransitionLogic;
import com.hedera.services.txns.validation.OptionValidator;
import com.hederahashgraph.api.proto.java.ConsensusUpdateTopicTransactionBody;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hedera.services.legacy.core.MapKey;
import com.hedera.services.legacy.core.jproto.JAccountID;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.legacy.core.jproto.JTimestamp;
import com.swirlds.fcmap.FCMap;
import org.apache.commons.codec.DecoderException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.function.Function;
import java.util.function.Predicate;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.*;

public class TopicUpdateTransitionLogic implements TransitionLogic {
	protected static final Logger log = LogManager.getLogger(TopicUpdateTransitionLogic.class);
	private final Function<TransactionBody, ResponseCodeEnum> PRE_SIGNATURE_VALIDATION_SYNTAX_CHECK =
			this::validatePreSignatureValidation;

	private final FCMap<MapKey, HederaAccount> accounts;
	private final FCMap<MapKey, Topic> topics;
	private final OptionValidator validator;
	private final TransactionContext transactionContext;

	public TopicUpdateTransitionLogic(FCMap<MapKey, HederaAccount> accounts, FCMap<MapKey, Topic> topics,
									  OptionValidator validator, TransactionContext transactionContext) {
		this.accounts = accounts;
		this.topics = topics;
		this.validator = validator;
		this.transactionContext = transactionContext;
	}

	@Override
	public void doStateTransition() {
		var transactionBody = transactionContext.accessor().getTxn();
		var op = transactionBody.getConsensusUpdateTopic();
		var topicId = op.getTopicID();

		var topicStatus = validator.queryableTopicStatus(topicId, topics);
		if (OK != topicStatus) {
			// Should not get here as the adminKey lookup should have failed.
			transactionContext.setStatus(topicStatus);
			return;
		}

		var topicMapKey = MapKey.getMapKey(topicId);
		var updatedTopic = new Topic(topics.get(topicMapKey));

		if (!updatedTopic.hasAdminKey() &&
				(op.hasMemo() || op.hasAdminKey() || op.hasSubmitKey() || op.hasAutoRenewPeriod() ||
						op.hasAutoRenewAccount())) {
			// Topics without adminKeys can't be modified in this manner.
			transactionContext.setStatus(UNAUTHORIZED);
			return;
		}

		if (op.hasMemo()) {
			var newMemo = op.getMemo().getValue();
			if (!validator.isValidEntityMemo(newMemo)) {
				transactionContext.setStatus(MEMO_TOO_LONG);
				return;
			}
			updatedTopic.setMemo(newMemo);
		}

		if(!updateTopicWithNewKeys(op, updatedTopic)){
			return;
		}

		if (op.hasAutoRenewPeriod()) {
			var newAutoRenewPeriod = op.getAutoRenewPeriod();
			if (!validator.isValidAutoRenewPeriod(newAutoRenewPeriod)) {
				transactionContext.setStatus(AUTORENEW_DURATION_NOT_IN_RANGE);
				return;
			}
			updatedTopic.setAutoRenewDurationSeconds(newAutoRenewPeriod.getSeconds());
		}

		if (op.hasAutoRenewAccount() && !updateTopicWithNewAutoRenewAccount(op, updatedTopic)){
				return;
		}

		if (updatedTopic.hasAutoRenewAccountId() && !updatedTopic.hasAdminKey()) {
			transactionContext.setStatus(AUTORENEW_ACCOUNT_NOT_ALLOWED);
			return;
		}

		if (op.hasExpirationTime() && !updateTopicWithNewExpirationTime(op, updatedTopic)){
				return;
		}

		topics.put(topicMapKey, updatedTopic);
		transactionContext.setStatus(SUCCESS);
	}

	private boolean updateTopicWithNewExpirationTime(ConsensusUpdateTopicTransactionBody op, Topic updatedTopic) {
		var newExpiration = op.getExpirationTime();
		if (!validator.isValidExpiry(newExpiration)) {
			transactionContext.setStatus(INVALID_EXPIRATION_TIME);
			return false;
		}

		var newExpirationJTimestamp = JTimestamp.convert(newExpiration);
		// Not yet updated...
		if (updatedTopic.hasExpirationTimestamp() &&
				updatedTopic.getExpirationTimestamp().isAfter(newExpirationJTimestamp))
		{
			transactionContext.setStatus(EXPIRATION_REDUCTION_NOT_ALLOWED);
			return false;
		}

		updatedTopic.setExpirationTimestamp(newExpirationJTimestamp);
		return true;
	}

	private boolean updateTopicWithNewAutoRenewAccount(ConsensusUpdateTopicTransactionBody op, Topic updatedTopic) {
		var newAutoRenewAccountId = op.getAutoRenewAccount();
		if (newAutoRenewAccountId.getShardNum() == 0 && newAutoRenewAccountId.getRealmNum() == 0
				&& newAutoRenewAccountId.getAccountNum() == 0) {
			updatedTopic.setAutoRenewAccountId(null);
		} else {
			if (OK != validator.queryableAccountStatus(newAutoRenewAccountId, accounts)) {
				transactionContext.setStatus(INVALID_AUTORENEW_ACCOUNT);
				return false;
			}
		}
		updatedTopic.setAutoRenewAccountId(JAccountID.convert(newAutoRenewAccountId));
		return true;
	}

	private boolean updateTopicWithNewKeys(ConsensusUpdateTopicTransactionBody op, Topic updatedTopic) {
		var topicId = op.getTopicID();
		try {
			if (op.hasAdminKey()) {
				var newAdminKey = op.getAdminKey();
				if (!validator.hasGoodEncoding(newAdminKey)) {
					log.error("Update topic {} has invalid admin key specified, " +
							"which should have been caught during signature validation", topicId);
					transactionContext.setStatus(BAD_ENCODING);
					return false;
				}
				updatedTopic.setAdminKey(JKey.mapKey(newAdminKey));
			}

			if (op.hasSubmitKey()) {
				var newSubmitKey = op.getSubmitKey();
				if (!validator.hasGoodEncoding(newSubmitKey)) {
					transactionContext.setStatus(BAD_ENCODING);
					return false;
				}
				updatedTopic.setSubmitKey(JKey.mapKey(newSubmitKey));
			}
		} catch (DecoderException e) {
			log.error("Decoder exception updating topic {}. ", topicId, e);
			transactionContext.setStatus(BAD_ENCODING);
			return false;
		}
		return true;
	}

	@Override
	public Predicate<TransactionBody> applicability() {
		return TransactionBody::hasConsensusUpdateTopic;
	}

	@Override
	public Function<TransactionBody, ResponseCodeEnum> syntaxCheck() {
		return PRE_SIGNATURE_VALIDATION_SYNTAX_CHECK;
	}

	 /**
	 * Pre-consensus (and post-consensus-pre-doStateTransition) validation validates the encoding of the optional
	 * adminKey; this check occurs before signature validation which occurs before doStateTransition.
	 * @param transactionBody
	 * @return
	 */
	private ResponseCodeEnum validatePreSignatureValidation(TransactionBody transactionBody) {
		var op = transactionBody.getConsensusUpdateTopic();

		if (op.hasAdminKey() && !validator.hasGoodEncoding(op.getAdminKey())) {
			return BAD_ENCODING;
		}

		return OK;
	}
}
