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
import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.store.AccountStore;
import com.hedera.services.store.TypedTokenStore;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.txns.TransitionLogic;
import com.hederahashgraph.api.proto.java.CryptoDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

import static com.hedera.services.exceptions.ValidationUtils.validateFalse;
import static com.hedera.services.ledger.BalanceChange.hbarAdjust;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_ID_DOES_NOT_EXIST;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_IS_TREASURY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSFER_ACCOUNT_SAME_AS_DELETE_ACCOUNT;

/**
 * Implements the {@link TransitionLogic} for a HAPI CryptoDelete transaction,
 * and the conditions under which such logic is syntactically correct. (It is
 * possible that the <i>semantics</i> of the transaction will still be wrong;
 * for example, if the target account expired before this transaction reached
 * consensus.)
 */
@Singleton
public class CryptoDeleteTransitionLogic implements TransitionLogic {
	private final Function<TransactionBody, ResponseCodeEnum> SEMANTIC_CHECK = this::validate;

	private final TransactionContext txnCtx;
	private final HederaLedger ledger;
	private final AccountStore accountStore;
	private final TypedTokenStore tokenStore;

	@Inject
	public CryptoDeleteTransitionLogic(
			TransactionContext txnCtx,
			HederaLedger ledger,
			AccountStore accountStore,
			TypedTokenStore tokenStore
	) {
		this.txnCtx = txnCtx;
		this.ledger = ledger;
		this.accountStore = accountStore;
		this.tokenStore = tokenStore;
	}

	@Override
	public void doStateTransition() {
		/* --- Translate from gRPC types --- */
		final var op = txnCtx.accessor().getTxn().getCryptoDelete();

		final var targetId = Id.fromGrpcAccount(op.getDeleteAccountID());
		final var beneficiaryId = Id.fromGrpcAccount(op.getTransferAccountID());

		final var target = accountStore.loadAccount(targetId);
		final var beneficiary = accountStore.loadAccount(beneficiaryId);

		/* --- Validate --- */
		validateFalse(tokenStore.isKnownTreasury(target.getId().asGrpcAccount()), ACCOUNT_IS_TREASURY);
		validateNoTokenBalancesPresent(target);

		/* --- Do the business logic --- */
		final var balanceChanges = List.of(
				hbarAdjust(targetId, -1 * target.getBalance()),
				hbarAdjust(beneficiaryId, target.getBalance())
		);
		ledger.doZeroSum(balanceChanges);

		target.delete();

		/* --- Persist the changes --- */
		accountStore.persistAccount(target);
		accountStore.persistAccount(beneficiary);
	}

	private void validateNoTokenBalancesPresent(final Account target) {
		final var associatedTokens = target.getAssociatedTokens().getAsIds();

		for (var tokenId : associatedTokens) {
			final var token = tokenStore.loadToken(Id.fromGrpcToken(tokenId));
			if (token.isDeleted()) {
				continue;
			}
			final var tokenRelationship = tokenStore.loadTokenRelationship(token, target);
			validateFalse(tokenRelationship.getBalance() > 0L, TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES);
		}
	}

	@Override
	public Predicate<TransactionBody> applicability() {
		return TransactionBody::hasCryptoDelete;
	}

	@Override
	public Function<TransactionBody, ResponseCodeEnum> semanticCheck() {
		return SEMANTIC_CHECK;
	}

	private ResponseCodeEnum validate(TransactionBody cryptoDeleteTxn) {
		CryptoDeleteTransactionBody op = cryptoDeleteTxn.getCryptoDelete();

		if (!op.hasDeleteAccountID() || !op.hasTransferAccountID()) {
			return ACCOUNT_ID_DOES_NOT_EXIST;
		}

		if (op.getDeleteAccountID().equals(op.getTransferAccountID())) {
			return TRANSFER_ACCOUNT_SAME_AS_DELETE_ACCOUNT;
		}

		return OK;
	}
}