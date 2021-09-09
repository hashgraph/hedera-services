package com.hedera.services.txns.crypto;

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
import com.hedera.services.ledger.ids.EntityIdSource;
import com.hedera.services.records.TransactionRecordService;
import com.hedera.services.store.AccountStore;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.txns.TransitionLogic;
import com.hedera.services.txns.validation.OptionValidator;
import com.hederahashgraph.api.proto.java.CryptoCreateTransactionBody;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.function.Function;
import java.util.function.Predicate;

import static com.hedera.services.exceptions.ValidationUtils.validateFalse;
import static com.hedera.services.utils.MiscUtils.asFcKeyUnchecked;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_EXPIRED_AND_PENDING_REMOVAL;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.AUTORENEW_DURATION_NOT_IN_RANGE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BAD_ENCODING;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_INITIAL_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_RECEIVE_RECORD_THRESHOLD;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_RENEWAL_PERIOD;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SEND_RECORD_THRESHOLD;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.KEY_REQUIRED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.REQUESTED_NUM_AUTOMATIC_ASSOCIATIONS_EXCEEDS_ASSOCIATION_LIMIT;

/**
 * Implements the {@link TransitionLogic} for a HAPI CryptoCreate transaction,
 * and the conditions under which such logic is syntactically correct. (It is
 * possible that the <i>semantics</i> of the transaction will still be wrong;
 * for example, if the sponsor account can no longer afford to fund the
 * initial balance of the new account.)
 */
@Singleton
public class CryptoCreateTransitionLogic implements TransitionLogic {

	private final Function<TransactionBody, ResponseCodeEnum> SEMANTIC_CHECK = this::validate;

	private final EntityIdSource ids;
	private final OptionValidator validator;
	private final TransactionContext txnCtx;
	private final AccountStore accountStore;
	private final GlobalDynamicProperties dynamicProperties;
	private final TransactionRecordService transactionRecordService;

	@Inject
	public CryptoCreateTransitionLogic(
			final OptionValidator validator,
			final TransactionContext txnCtx,
			final AccountStore accountStore,
			final GlobalDynamicProperties dynamicProperties,
			final EntityIdSource ids,
			final TransactionRecordService transactionRecordService
			) {
		this.txnCtx = txnCtx;
		this.validator = validator;
		this.accountStore = accountStore;
		this.dynamicProperties = dynamicProperties;
		this.ids = ids;
		this.transactionRecordService = transactionRecordService;
	}

	@Override
	public void doStateTransition() {
		/* --- Translate from gRPC types --- */
		final var txnBody = txnCtx.accessor().getTxn();
		final var op = txnBody.getCryptoCreateAccount();
		final var sponsorGrpc = txnBody.getTransactionID().getAccountID();
		final var sponsorId = Id.fromGrpcAccount(sponsorGrpc);

		/* --- Load the model objects --- */
		final var sponsor = accountStore.loadAccount(sponsorId);
		validateFalse(sponsor.isSmartContract(), ACCOUNT_EXPIRED_AND_PENDING_REMOVAL);

		/* --- Do the business logic --- */
		var createdIdGrpc = ids.newAccountId(sponsor.getId().asGrpcAccount());
		final var created = Account.createFromGrpc(Id.fromGrpcAccount(createdIdGrpc), op, txnCtx.consensusTime().getEpochSecond());
		final var balanceChanges = sponsor.transferHbar(created, op.getInitialBalance());

		/* --- Persist the models --- */
		this.accountStore.persistNew(created);
		this.accountStore.persistAccount(sponsor);

		/* --- Externalise the transaction effects in the record stream */
		transactionRecordService.includeChangesToAccount(created);
		transactionRecordService.includeHbarBalanceChanges(balanceChanges);
	}

	@Override
	public Predicate<TransactionBody> applicability() {
		return TransactionBody::hasCryptoCreateAccount;
	}

	@Override
	public Function<TransactionBody, ResponseCodeEnum> semanticCheck() {
		return SEMANTIC_CHECK;
	}

	public ResponseCodeEnum validate(TransactionBody cryptoCreateTxn) {
		CryptoCreateTransactionBody op = cryptoCreateTxn.getCryptoCreateAccount();

		var memoValidity = validator.memoCheck(op.getMemo());
		if (memoValidity != OK) {
			return memoValidity;
		}
		if (!op.hasKey()) {
			return KEY_REQUIRED;
		}
		if (!validator.hasGoodEncoding(op.getKey())) {
			return BAD_ENCODING;
		}
		var fcKey = asFcKeyUnchecked(op.getKey());
		if (fcKey.isEmpty()) {
			return KEY_REQUIRED;
		}
		if (!fcKey.isValid()) {
			return BAD_ENCODING;
		}
		if (op.getInitialBalance() < 0L) {
			return INVALID_INITIAL_BALANCE;
		}
		if (!op.hasAutoRenewPeriod()) {
			return INVALID_RENEWAL_PERIOD;
		}
		if (!validator.isValidAutoRenewPeriod(op.getAutoRenewPeriod())) {
			return AUTORENEW_DURATION_NOT_IN_RANGE;
		}
		if (op.getSendRecordThreshold() < 0L) {
			return INVALID_SEND_RECORD_THRESHOLD;
		}
		if (op.getReceiveRecordThreshold() < 0L) {
			return INVALID_RECEIVE_RECORD_THRESHOLD;
		}
		if (op.getMaxAutomaticTokenAssociations() > dynamicProperties.maxTokensPerAccount()) {
			return REQUESTED_NUM_AUTOMATIC_ASSOCIATIONS_EXCEEDS_ASSOCIATION_LIMIT;
		}
		return OK;
	}
}
