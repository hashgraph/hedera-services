package com.hedera.services.txns.contract;

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
import com.hedera.services.legacy.core.jproto.JContractIDKey;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.store.AccountStore;
import com.hedera.services.store.models.Id;
import com.hedera.services.txns.TransitionLogic;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.MiscUtils;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;

import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

import static com.hedera.services.exceptions.ValidationUtils.validateFalse;
import static com.hedera.services.exceptions.ValidationUtils.validateTrue;
import static com.hedera.services.legacy.core.jproto.JContractIDKey.fromId;
import static com.hedera.services.sigs.utils.ImmutableKeyUtils.signalsKeyRemoval;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.AUTORENEW_DURATION_NOT_IN_RANGE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ADMIN_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_EXPIRATION_TIME;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_RENEWAL_PERIOD;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

public class ContractUpdateTransitionLogic implements TransitionLogic {
	private final TransactionContext txnCtx;
	private final OptionValidator validator;
	private final AccountStore accountStore;

	private final Function<TransactionBody, ResponseCodeEnum> SEMANTIC_CHECK = this::validate;

	public ContractUpdateTransitionLogic(
			TransactionContext txnCtx,
			OptionValidator validator,
			AccountStore accountStore
	) {
		this.txnCtx = txnCtx;
		this.validator = validator;
		this.accountStore = accountStore;
	}

	@Override
	public void doStateTransition() {
		/* --- Process gRPC --- */
		final var contractUpdateTxn = txnCtx.accessor().getTxn();
		final var op = contractUpdateTxn.getContractUpdateInstance();
		final var targetId = Id.fromGrpcContract(op.getContractID());

		/* --- Translate the account --- */
		final var target = accountStore.loadContract(targetId);

		/* --- Validate --- */
		validateFalse(op.hasExpirationTime() && !validator.isValidExpiry(op.getExpirationTime()), INVALID_EXPIRATION_TIME);

		/* --- Process admin key --- */
		JKey processedAdminKey = null;
		if (op.hasAdminKey()) {
			final var newAdminKey = op.getAdminKey();
			if (signalsKeyRemoval(newAdminKey)) {
				processedAdminKey = fromId(targetId);
			} else {
				var optionalJKey = MiscUtils.asUsableFcKey(newAdminKey);
				validateTrue(optionalJKey.isPresent() && !(optionalJKey.get() instanceof JContractIDKey), INVALID_ADMIN_KEY);
				processedAdminKey = optionalJKey.get();
			}
		}

		/* --- Process other candidates --- */
		final var newProxy = op.hasProxyAccountID() ? op.getProxyAccountID() : null;
		final var newAutoRenewPeriod = op.hasAutoRenewPeriod() ? op.getAutoRenewPeriod() : null;
		final var newExpirationTime = op.hasExpirationTime() ? op.getExpirationTime() : null;
		final var newMemo = op.hasMemoWrapper()
				? op.getMemoWrapper().getValue()
				: op.getMemo().length() > 0
				? op.getMemo()
				: null;

		/* --- Do the business logic --- */
		target.updateFromGrpcContract(
				Optional.ofNullable(processedAdminKey),
				Optional.ofNullable(newProxy),
				Optional.ofNullable(newAutoRenewPeriod),
				Optional.ofNullable(newExpirationTime),
				Optional.ofNullable(newMemo));

		/* --- Persist the changes --- */
		accountStore.persistAccount(target);
	}

	@Override
	public Predicate<TransactionBody> applicability() {
		return TransactionBody::hasContractUpdateInstance;
	}

	@Override
	public Function<TransactionBody, ResponseCodeEnum> semanticCheck() {
		return SEMANTIC_CHECK;
	}

	public ResponseCodeEnum validate(TransactionBody contractUpdateTxn) {
		var op = contractUpdateTxn.getContractUpdateInstance();

		var status = OK;

		if (op.hasAutoRenewPeriod()) {
			if (op.getAutoRenewPeriod().getSeconds() < 1) {
				return INVALID_RENEWAL_PERIOD;
			}
			if (!validator.isValidAutoRenewPeriod(op.getAutoRenewPeriod())) {
				return AUTORENEW_DURATION_NOT_IN_RANGE;
			}
		}

		var newMemoIfAny = op.hasMemoWrapper() ? op.getMemoWrapper().getValue() : op.getMemo();
		if ((status = validator.memoCheck(newMemoIfAny)) != OK) {
			return status;
		}

		return OK;
	}
}
