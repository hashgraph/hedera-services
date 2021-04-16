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
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleTopic;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.txns.TransitionLogic;
import com.hedera.services.txns.validation.OptionValidator;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ConsensusUpdateTopicTransactionBody;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.legacy.core.jproto.JKey;
import com.swirlds.fcmap.FCMap;
import org.apache.commons.codec.DecoderException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static com.hedera.services.state.submerkle.RichInstant.fromGrpc;
import static com.hedera.services.utils.MiscUtils.asFcKeyUnchecked;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.*;

public class TopicUpdateTransitionLogic implements TransitionLogic {
	private static final Logger log = LogManager.getLogger(TopicUpdateTransitionLogic.class);

	private final Function<TransactionBody, ResponseCodeEnum> PRE_SIGNATURE_VALIDATION_SYNTAX_CHECK =
			this::validatePreSignatureValidation;

	private final Supplier<FCMap<MerkleEntityId, MerkleAccount>> accounts;
	private final Supplier<FCMap<MerkleEntityId, MerkleTopic>> topics;
	private final OptionValidator validator;
	private final TransactionContext transactionContext;

	public TopicUpdateTransitionLogic(
			Supplier<FCMap<MerkleEntityId, MerkleAccount>> accounts,
			Supplier<FCMap<MerkleEntityId, MerkleTopic>> topics,
			OptionValidator validator,
			TransactionContext transactionContext
	) {
		this.accounts = accounts;
		this.topics = topics;
		this.validator = validator;
		this.transactionContext = transactionContext;
	}

	@Override
	public void doStateTransition() {
		var transactionBody = transactionContext.accessor().getTxn();
		var op = transactionBody.getConsensusUpdateTopic();

		var topicStatus = validator.queryableTopicStatus(op.getTopicID(), topics.get());
		if (topicStatus != OK) {
			transactionContext.setStatus(topicStatus);
			return;
		}

		var topicId = MerkleEntityId.fromTopicId(op.getTopicID());
		var topic = topics.get().get(topicId);
		if (!topic.hasAdminKey() && wantsToMutateNonExpiryField(op)) {
			transactionContext.setStatus(UNAUTHORIZED);
			return;
		}
		if (!canApplyNewFields(op, topic)) {
			return;
		}

		var mutableTopic = topics.get().getForModify(topicId);
		applyNewFields(op, mutableTopic);
		topics.get().put(topicId, mutableTopic);
		transactionContext.setStatus(SUCCESS);
	}

	private boolean wantsToMutateNonExpiryField(ConsensusUpdateTopicTransactionBody op) {
		return op.hasMemo() ||
				op.hasAdminKey() || op.hasSubmitKey() ||
				op.hasAutoRenewPeriod() || op.hasAutoRenewAccount();
	}

	private boolean canApplyNewFields(ConsensusUpdateTopicTransactionBody op, MerkleTopic topic) {
		return canApplyNewKeys(op, topic) &&
				canApplyNewMemo(op) &&
				canApplyNewExpiry(op, topic) &&
				canApplyNewAutoRenewPeriod(op) &&
				canApplyNewAutoRenewAccount(op, topic);
	}

	private void applyNewFields(ConsensusUpdateTopicTransactionBody op, MerkleTopic topic) {
		applyNewKeys(op, topic);
		applyNewMemo(op, topic);
		applyNewExpiry(op, topic);
		applyNewAutoRenewPeriod(op, topic);
		applyNewAutoRenewAccount(op, topic);
	}

	private boolean canApplyNewAutoRenewPeriod(ConsensusUpdateTopicTransactionBody op) {
		if (!op.hasAutoRenewPeriod()) {
			return true;
		}
		var newAutoRenewPeriod = op.getAutoRenewPeriod();
		if (!validator.isValidAutoRenewPeriod(newAutoRenewPeriod)) {
			transactionContext.setStatus(AUTORENEW_DURATION_NOT_IN_RANGE);
			return false;
		}
		return true;
	}

	private boolean canApplyNewMemo(ConsensusUpdateTopicTransactionBody op) {
		if (!op.hasMemo()) {
			return true;
		}
		var memoValidity = validator.memoCheck(op.getMemo().getValue());
		if (memoValidity != OK) {
			transactionContext.setStatus(memoValidity);
			return false;
		}
		return true;
	}

	private void applyNewMemo(ConsensusUpdateTopicTransactionBody op, MerkleTopic topic) {
		if (op.hasMemo()) {
			topic.setMemo(op.getMemo().getValue());
		}
	}

	private boolean canApplyNewExpiry(ConsensusUpdateTopicTransactionBody op, MerkleTopic topic) {
		if (!op.hasExpirationTime()) {
			return true;
		}
		var newExpiry = op.getExpirationTime();
		if (!validator.isValidExpiry(newExpiry)) {
			transactionContext.setStatus(INVALID_EXPIRATION_TIME);
			return false;
		}

		var richNewExpiry = fromGrpc(newExpiry);
		if (topic.hasExpirationTimestamp() && topic.getExpirationTimestamp().isAfter(richNewExpiry)) {
			transactionContext.setStatus(EXPIRATION_REDUCTION_NOT_ALLOWED);
			return false;
		}

		return true;
	}

	private void applyNewExpiry(ConsensusUpdateTopicTransactionBody op, MerkleTopic topic) {
		if (op.hasExpirationTime()) {
			topic.setExpirationTimestamp(fromGrpc(op.getExpirationTime()));
		}
	}

	private boolean canApplyNewAutoRenewAccount(ConsensusUpdateTopicTransactionBody op, MerkleTopic topic) {
		if (!op.hasAutoRenewAccount()) {
			return true;
		}
		var newAutoRenewAccount = op.getAutoRenewAccount();
		if (designatesAccountRemoval(newAutoRenewAccount)) {
			return true;
		}
		if (!topic.hasAdminKey() || (op.hasAdminKey() && asFcKeyUnchecked(op.getAdminKey()).isEmpty())) {
			transactionContext.setStatus(AUTORENEW_ACCOUNT_NOT_ALLOWED);
			return false;
		}
		if (OK != validator.queryableAccountStatus(newAutoRenewAccount, accounts.get())) {
			transactionContext.setStatus(INVALID_AUTORENEW_ACCOUNT);
			return false;
		}
		return true;
	}

	private void applyNewAutoRenewAccount(ConsensusUpdateTopicTransactionBody op, MerkleTopic topic) {
		if (op.hasAutoRenewAccount()) {
			if (designatesAccountRemoval(op.getAutoRenewAccount())) {
				topic.setAutoRenewAccountId(null);
			} else {
				topic.setAutoRenewAccountId(EntityId.fromGrpcAccountId(op.getAutoRenewAccount()));
			}
		}
	}

	private void applyNewAutoRenewPeriod(ConsensusUpdateTopicTransactionBody op, MerkleTopic topic) {
		if (op.hasAutoRenewPeriod()) {
			topic.setAutoRenewDurationSeconds(op.getAutoRenewPeriod().getSeconds());
		}
	}

	private boolean designatesAccountRemoval(AccountID id) {
		return id.getShardNum() == 0 && id.getRealmNum() == 0 && id.getAccountNum() == 0;
	}

	private boolean canApplyNewKeys(ConsensusUpdateTopicTransactionBody op, MerkleTopic topic) {
		if (!op.hasAdminKey() && !op.hasSubmitKey()) {
			return true;
		}
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
				var fcKey = JKey.mapKey(newAdminKey);
				if (fcKey.isEmpty()) {
					boolean opRemovesAutoRenewId = op.hasAutoRenewAccount() &&
							designatesAccountRemoval(op.getAutoRenewAccount());
					if (topic.hasAutoRenewAccountId() && !opRemovesAutoRenewId) {
						transactionContext.setStatus(AUTORENEW_ACCOUNT_NOT_ALLOWED);
						return false;
					}
				}
			}

			if (op.hasSubmitKey()) {
				var newSubmitKey = op.getSubmitKey();
				if (!validator.hasGoodEncoding(newSubmitKey)) {
					transactionContext.setStatus(BAD_ENCODING);
					return false;
				}
				JKey.mapKey(newSubmitKey);
			}
		} catch (DecoderException e) {
			log.error("Decoder exception updating topic {}. ", topicId, e);
			transactionContext.setStatus(BAD_ENCODING);
			return false;
		}
		return true;
	}

	private void applyNewKeys(ConsensusUpdateTopicTransactionBody op, MerkleTopic topic) {
		if (op.hasAdminKey()) {
			topic.setAdminKey(asFcKeyUnchecked(op.getAdminKey()));
		}
		if (op.hasSubmitKey()) {
			topic.setSubmitKey(asFcKeyUnchecked(op.getSubmitKey()));
		}
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
	 *
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
