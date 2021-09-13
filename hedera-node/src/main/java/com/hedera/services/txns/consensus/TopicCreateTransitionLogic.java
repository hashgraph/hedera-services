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
import com.hedera.services.ledger.ids.EntityIdSource;
import com.hedera.services.store.AccountStore;
import com.hedera.services.store.TopicStore;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.Topic;
import com.hedera.services.txns.TransitionLogic;
import com.hedera.services.txns.validation.OptionValidator;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.function.Function;
import java.util.function.Predicate;

import static com.hedera.services.exceptions.ValidationUtils.validateFalse;
import static com.hedera.services.exceptions.ValidationUtils.validateTrue;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.AUTORENEW_ACCOUNT_NOT_ALLOWED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.AUTORENEW_DURATION_NOT_IN_RANGE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BAD_ENCODING;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_AUTORENEW_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_RENEWAL_PERIOD;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

/**
 * The syntax check pre-consensus validates the adminKey's structure as signature validation occurs before
 * doStateTransition().
 */
@Singleton
public class TopicCreateTransitionLogic implements TransitionLogic {
	private static final Logger log = LogManager.getLogger(TopicCreateTransitionLogic.class);

	private final AccountStore accountStore;
	private final TopicStore topicStore;
	private final EntityIdSource entityIdSource;
	private final OptionValidator validator;
	private final Function<TransactionBody, ResponseCodeEnum> PRE_SIGNATURE_VALIDATION_SEMANTIC_CHECK =
			this::validatePreSignatureValidation;
	private final TransactionContext transactionContext;

	@Inject
	public TopicCreateTransitionLogic(
			TopicStore topicStore,
			EntityIdSource entityIdSource,
			OptionValidator validator,
			TransactionContext transactionContext,
			final AccountStore accountStore) {
		this.accountStore = accountStore;
		this.topicStore = topicStore;
		this.entityIdSource = entityIdSource;
		this.validator = validator;
		this.transactionContext = transactionContext;
	}

	@Override
	public void doStateTransition() {
		/* --- pre-validation --- */
		validatePreStateTransition();
		/* --- extract gRPC --- */
		var transactionBody = transactionContext.accessor().getTxn();
		var payerAccountId = transactionBody.getTransactionID().getAccountID();
		var op = transactionBody.getConsensusCreateTopic();
		// expirationTime (currently un-enforced) is consensus timestamp of create plus the specified required
		// autoRenewPeriod->seconds.
		var expirationTime = transactionContext.consensusTime().plusSeconds(op.getAutoRenewPeriod().getSeconds());
		/* --- Do business logic --- */
		var topicId = entityIdSource.newAccountId(payerAccountId);
		var topic = Topic.fromGrpcTopicCreate(op, Id.fromGrpcAccount(topicId), expirationTime);
		/* --- persist the topic --- */
		topicStore.persistNew(topic);
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
	 *
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
	 *
	 * @return the validity
	 */
	private void validatePreStateTransition() {
		final var op = transactionContext.accessor().getTxn().getConsensusCreateTopic();
		final var validationResult = validator.memoCheck(op.getMemo());
		validateTrue(OK == validationResult, validationResult);
		validateFalse(op.hasSubmitKey() && !validator.hasGoodEncoding(op.getSubmitKey()), BAD_ENCODING);
		validateTrue(op.hasAutoRenewPeriod(), INVALID_RENEWAL_PERIOD);
		validateTrue(validator.isValidAutoRenewPeriod(op.getAutoRenewPeriod()), AUTORENEW_DURATION_NOT_IN_RANGE);
		if (op.hasAutoRenewAccount()) {
			final var autoRenew = accountStore.loadAccountOrFailWith(Id.fromGrpcAccount(op.getAutoRenewAccount()), INVALID_AUTORENEW_ACCOUNT);
			validateFalse(autoRenew.isSmartContract(), INVALID_AUTORENEW_ACCOUNT);
			validateTrue(op.hasAdminKey(), AUTORENEW_ACCOUNT_NOT_ALLOWED);
		}
	}
	
}
