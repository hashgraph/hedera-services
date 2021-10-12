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
import com.hedera.services.store.AccountStore;
import com.hedera.services.store.TopicStore;
import com.hedera.services.store.models.Id;
import com.hedera.services.txns.TransitionLogic;
import com.hedera.services.txns.validation.OptionValidator;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

import static com.hedera.services.exceptions.ValidationUtils.validateFalse;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.AUTORENEW_DURATION_NOT_IN_RANGE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BAD_ENCODING;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_EXPIRATION_TIME;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.UNAUTHORIZED;

@Singleton
public class TopicUpdateTransitionLogic implements TransitionLogic {
	private final Function<TransactionBody, ResponseCodeEnum> SEMANTIC_CHECK = this::validatePreSignatureValidation;
	private final OptionValidator validator;
	private final TransactionContext transactionContext;
	private final AccountStore accountStore;
	private final TopicStore topicStore;

	@Inject
	public TopicUpdateTransitionLogic(final OptionValidator validator,
									  final TransactionContext transactionContext,
									  final AccountStore accountStore,
									  final TopicStore topicStore) {
		this.validator = validator;
		this.transactionContext = transactionContext;
		this.accountStore = accountStore;
		this.topicStore = topicStore;
	}

	@Override
	public void doStateTransition() {
		/* --- Extract gRPC --- */
		final var transactionBody = transactionContext.accessor().getTxn();
		final var op = transactionBody.getConsensusUpdateTopic();
		final var topicId = op.getTopicID();
		final var newAdminKey = op.hasAdminKey() ? op.getAdminKey() : null;
		final var newSubmitKey = op.hasSubmitKey() ? op.getSubmitKey() : null;
		final var newMemo = op.hasMemo() ? op.getMemo().getValue() : null;
		final var newExpirationTime = op.hasExpirationTime() ? op.getExpirationTime() : null;
		final var newAutoRenewPeriod = op.hasAutoRenewPeriod() ? op.getAutoRenewPeriod() : null;
		final var topic = topicStore.loadTopic(Id.fromGrpcTopic(topicId));

		final var affectsExpiryOnly = !op.hasMemo()
				&& !op.hasAdminKey()
				&& !op.hasSubmitKey()
				&& !op.hasAutoRenewPeriod()
				&& !op.hasAutoRenewAccount();

		/* --- Validate --- */
		validateFalse(!topic.hasAdminKey() && !affectsExpiryOnly, UNAUTHORIZED);

		if (newSubmitKey != null) {
			validateFalse(!validator.hasGoodEncoding(op.getSubmitKey()), BAD_ENCODING);
		}
		if (newMemo != null) {
			final var memoValidity = validator.memoCheck(newMemo);
			validateFalse(memoValidity != OK, memoValidity);
		}
		if (newExpirationTime != null) {
			validateFalse(!validator.isValidExpiry(newExpirationTime), INVALID_EXPIRATION_TIME);
		}
		if (newAutoRenewPeriod != null) {
			validateFalse(!validator.isValidAutoRenewPeriod(newAutoRenewPeriod), AUTORENEW_DURATION_NOT_IN_RANGE);
		}
		if (topic.hasAutoRenewAccountId()) {
			accountStore.loadAccount(topic.getAutoRenewAccountId());
		}

		/* --- Translate the account --- */
		final var transactionRemovesAutoRenewAccount =
				op.getAutoRenewAccount().equals(AccountID.getDefaultInstance());

		final var newAutoRenewAccount = transactionRemovesAutoRenewAccount
				? null
				: accountStore.loadAccount(Id.fromGrpcAccount(op.getAutoRenewAccount()));

		/* --- Do the business logic --- */
		topic.update(
				Optional.ofNullable(newExpirationTime),
				Optional.ofNullable(newAdminKey),
				Optional.ofNullable(newSubmitKey),
				Optional.ofNullable(newMemo),
				Optional.ofNullable(newAutoRenewPeriod),
				Optional.ofNullable(newAutoRenewAccount),
				op.hasAutoRenewAccount(),
				transactionRemovesAutoRenewAccount
		);

		/* --- Persist the changes --- */
		topicStore.persistTopic(topic);
	}

	@Override
	public Predicate<TransactionBody> applicability() {
		return TransactionBody::hasConsensusUpdateTopic;
	}

	@Override
	public Function<TransactionBody, ResponseCodeEnum> semanticCheck() {
		return SEMANTIC_CHECK;
	}

	/**
	 * Pre-consensus (and post-consensus-pre-doStateTransition) validation validates the encoding of the optional
	 * adminKey; this check occurs before signature validation which occurs before doStateTransition.
	 *
	 * @param transactionBody
	 * @return
	 */
	private ResponseCodeEnum validatePreSignatureValidation(final TransactionBody transactionBody) {
		var op = transactionBody.getConsensusUpdateTopic();

		if (op.hasAdminKey() && !validator.hasGoodEncoding(op.getAdminKey())) {
			return BAD_ENCODING;
		}

		return OK;
	}
}

