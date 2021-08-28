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
import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.ledger.ids.EntityIdSource;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.merkle.MerkleTopic;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.RichInstant;
import com.hedera.services.txns.TransitionLogic;
import com.hedera.services.txns.validation.OptionValidator;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TopicID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.swirlds.fcmap.FCMap;
import org.apache.commons.codec.DecoderException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_EXPIRED_AND_PENDING_REMOVAL;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.AUTORENEW_ACCOUNT_NOT_ALLOWED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.AUTORENEW_DURATION_NOT_IN_RANGE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BAD_ENCODING;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_AUTORENEW_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_RENEWAL_PERIOD;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

/**
 * The syntax check pre-consensus validates the adminKey's structure as signature validation occurs before
 * doStateTransition().
 */
public class TopicCreateTransitionLogic implements TransitionLogic {
	private static final Logger log = LogManager.getLogger(TopicCreateTransitionLogic.class);

	private final Function<TransactionBody, ResponseCodeEnum> PRE_SIGNATURE_VALIDATION_SEMANTIC_CHECK =
			this::validatePreSignatureValidation;

	private final HederaLedger ledger;
	private final Supplier<FCMap<MerkleEntityId, MerkleAccount>> accounts;
	private final Supplier<FCMap<MerkleEntityId, MerkleTopic>> topics;
	private final EntityIdSource entityIdSource;
	private final OptionValidator validator;
	private final TransactionContext transactionContext;

	public TopicCreateTransitionLogic(
			Supplier<FCMap<MerkleEntityId, MerkleAccount>> accounts,
			Supplier<FCMap<MerkleEntityId, MerkleTopic>> topics,
			EntityIdSource entityIdSource,
			OptionValidator validator,
			TransactionContext transactionContext,
			HederaLedger ledger
	) {
		this.accounts = accounts;
		this.topics = topics;
		this.ledger = ledger;
		this.entityIdSource = entityIdSource;
		this.validator = validator;
		this.transactionContext = transactionContext;
	}

	@Override
	public void doStateTransition() {
		var postConsensusValidationResult = validatePreStateTransition();
		if (OK != postConsensusValidationResult) {
			transactionContext.setStatus(postConsensusValidationResult);
			return;
		}

		var transactionBody = transactionContext.accessor().getTxn();
		var payerAccountId = transactionBody.getTransactionID().getAccountID();
		var op = transactionBody.getConsensusCreateTopic();

		try {
			// expirationTime (currently un-enforced) is consensus timestamp of create plus the specified required
			// autoRenewPeriod->seconds.
			var expirationTime = transactionContext.consensusTime().plusSeconds(op.getAutoRenewPeriod().getSeconds());

			var topic = new MerkleTopic(op.getMemo(),
					op.hasAdminKey() ? JKey.mapKey(op.getAdminKey()) : null,
					op.hasSubmitKey() ? JKey.mapKey(op.getSubmitKey()) : null,
					op.getAutoRenewPeriod().getSeconds(),
					op.hasAutoRenewAccount() ? EntityId.fromGrpcAccountId(op.getAutoRenewAccount()) : null,
					new RichInstant(expirationTime.getEpochSecond(), expirationTime.getNano()));

			var newEntityId = entityIdSource.newAccountId(payerAccountId);
			var newTopicId = TopicID.newBuilder()
					.setShardNum(newEntityId.getShardNum())
					.setRealmNum(newEntityId.getRealmNum())
					.setTopicNum(newEntityId.getAccountNum())
					.build();

			topics.get().put(MerkleEntityId.fromTopicId(newTopicId), topic);
			transactionContext.setCreated(newTopicId);
			transactionContext.setStatus(SUCCESS);
		} catch (DecoderException e) {
			log.error("DecoderException should have been hit in validatePostConsensus().", e);
			// Should not hit this - validatePostConsensus() should fail first on hasGoodEncoding(key).
			transactionContext.setStatus(BAD_ENCODING);
		}
	}

	@Override
	public Predicate<TransactionBody> applicability() {
		return TransactionBody::hasConsensusCreateTopic;
	}

	@Override
	public Function<TransactionBody, ResponseCodeEnum> semanticCheck() {
		return PRE_SIGNATURE_VALIDATION_SEMANTIC_CHECK;
	}

	/**
	 * Pre-consensus (and post-consensus-pre-doStateTransition) validation validates the encoding of the optional
	 * adminKey; this check occurs before signature validation which occurs before doStateTransition.
	 * @param transactionBody
	 * @return the validity
	 */
	private ResponseCodeEnum validatePreSignatureValidation(TransactionBody transactionBody) {
		var op = transactionBody.getConsensusCreateTopic();

		if (op.hasAdminKey() && !validator.hasGoodEncoding(op.getAdminKey())) {
			return BAD_ENCODING;
		}

		return OK;
	}

	/**
	 * Validation of the post-consensus transaction just prior to state transition.
	 * @return the validity
	 */
	private ResponseCodeEnum validatePreStateTransition() {
		var op = transactionContext.accessor().getTxn().getConsensusCreateTopic();

		ResponseCodeEnum validationResult = validator.memoCheck(op.getMemo());

		if (op.hasSubmitKey() && !validator.hasGoodEncoding(op.getSubmitKey())) {
			validationResult = BAD_ENCODING;
		} else if (!op.hasAutoRenewPeriod()) {
			validationResult = INVALID_RENEWAL_PERIOD;
		} else if (!validator.isValidAutoRenewPeriod(op.getAutoRenewPeriod())) {
			validationResult = AUTORENEW_DURATION_NOT_IN_RANGE;
		} else if (op.hasAutoRenewAccount()) {
			final var reqAutoRenew = op.getAutoRenewAccount();
			final var sanityCheck = validator.queryableAccountStatus(reqAutoRenew, accounts.get());
			if (sanityCheck != OK) {
				return INVALID_AUTORENEW_ACCOUNT;
			}
			if (ledger.isDetached(reqAutoRenew)) {
				validationResult = ACCOUNT_EXPIRED_AND_PENDING_REMOVAL;
			} else if (!op.hasAdminKey()) {
				validationResult = AUTORENEW_ACCOUNT_NOT_ALLOWED;
			}
		}

		return validationResult;
	}
}
