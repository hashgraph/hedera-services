/*
 * -
 *  * ‌
 *  * Hedera Services Node
 *  * ​
 *  * Copyright (C) 2018 - 2022 Hedera Hashgraph, LLC
 *  * ​
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *      http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *  * ‍
 *
 */

package com.hedera.services.txns.crypto;

import com.hedera.services.context.TransactionContext;
import com.hedera.services.exceptions.InsufficientFundsException;
import com.hedera.services.ledger.SigImpactHistorian;
import com.hedera.services.state.submerkle.FcTokenAllowance;
import com.hedera.services.state.submerkle.FcTokenAllowanceId;
import com.hedera.services.store.AccountStore;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.txns.TransitionLogic;
import com.hedera.services.txns.crypto.validators.AllowanceChecks;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.TxnAccessor;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoAllowance;
import com.hederahashgraph.api.proto.java.CryptoApproveAllowanceTransactionBody;
import com.hederahashgraph.api.proto.java.NftAllowance;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenAllowance;
import com.hederahashgraph.api.proto.java.TransactionBody;

import javax.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.EMPTY_ALLOWANCES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MAX_ALLOWANCES_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

public class CryptoApproveAllowanceTransitionLogic implements TransitionLogic {
	private final TransactionContext txnCtx;
	private final SigImpactHistorian sigImpactHistorian;
	private final AccountStore accountStore;
	private final AllowanceChecks allowanceChecks;

	public static final int ALLOWANCE_LIMIT_PER_TRANSACTION = 20;
	public static final int TOTAL_ALLOWANCE_LIMIT_PER_ACCOUNT = 100;

	@Inject
	public CryptoApproveAllowanceTransitionLogic(
			final TransactionContext txnCtx,
			final SigImpactHistorian sigImpactHistorian,
			final AccountStore accountStore,
			final AllowanceChecks allowanceChecks) {
		this.txnCtx = txnCtx;
		this.sigImpactHistorian = sigImpactHistorian;
		this.accountStore = accountStore;
		this.allowanceChecks = allowanceChecks;
	}

	@Override
	public void doStateTransition() {
		try {
			final TransactionBody cryptoApproveAllowanceTxn = txnCtx.accessor().getTxn();
			final AccountID owner = cryptoApproveAllowanceTxn.getTransactionID().getAccountID();
			final Id ownerId = Id.fromGrpcAccount(owner);
			final var ownerAccount = accountStore.loadAccount(ownerId);

			final var op = cryptoApproveAllowanceTxn.getCryptoApproveAllowance();


			if (willExceedLimit(op, ownerAccount)) {
				txnCtx.setStatus(MAX_ALLOWANCES_EXCEEDED);
				return;
			}

			applyCryptoAllowances(op.getCryptoAllowancesList(), ownerAccount);
			applyFungibleTokenAllowances(op.getTokenAllowancesList(), ownerAccount);
			applyNftAllowances(op.getNftAllowancesList(), ownerAccount);

			accountStore.commitAccount(ownerAccount);

			sigImpactHistorian.markEntityChanged(ownerId.num());

			txnCtx.setStatus(SUCCESS);
		} catch (InsufficientFundsException ife) {
			txnCtx.setStatus(INSUFFICIENT_PAYER_BALANCE);
		}
	}

	private boolean willExceedLimit(final CryptoApproveAllowanceTransactionBody op,
			final Account ownerAccount) {
		return ownerAccount.getTotalAllowances() +
				op.getCryptoAllowancesList().size() +
				op.getTokenAllowancesList().size() +
				op.getNftAllowancesList().size() > TOTAL_ALLOWANCE_LIMIT_PER_ACCOUNT;
	}

	@Override
	public Predicate<TransactionBody> applicability() {
		return TransactionBody::hasCryptoApproveAllowance;
	}

	@Override
	public Function<TransactionBody, ResponseCodeEnum> semanticCheck() {
		return this::validate;
	}

	@Override
	public ResponseCodeEnum validateSemantics(TxnAccessor accessor) {
		final var allowanceTxn = accessor.getTxn();
		final AccountID owner = allowanceTxn.getTransactionID().getAccountID();
		final Id ownerId = Id.fromGrpcAccount(owner);
		final var ownerAccount = accountStore.loadAccount(ownerId);
		return allowanceChecks.allowancesValidation(allowanceTxn, ownerAccount);
	}

	private ResponseCodeEnum validate(TransactionBody cryptoAllowanceTxn) {
		final var op = cryptoAllowanceTxn.getCryptoApproveAllowance();
		if (exceedsLimitCount(op)) {
			return MAX_ALLOWANCES_EXCEEDED;
		}
		if (emptyAllowances(op)) {
			return EMPTY_ALLOWANCES;
		}
		return OK;
	}

	private void applyCryptoAllowances(final List<CryptoAllowance> cryptoAllowances,
			final Account ownerAccount) {
		if (cryptoAllowances.isEmpty()) {
			return;
		}
		Map<EntityNum, Long> cryptoAllowancesMap = ownerAccount.getCryptoAllowances();
		for (final var allowance : cryptoAllowances) {
			final var spender = Id.fromGrpcAccount(allowance.getSpender());
			final var amount = allowance.getAmount();
			if (cryptoAllowancesMap.containsKey(spender.asEntityNum())) {
				// No-Op need to submit adjustAllowance to adjust any allowances
				return;
			}
			if (amount == 0) {
				cryptoAllowancesMap.remove(spender.asEntityNum());
				return;
			}
			if (ownerAccount.getTotalAllowances() > TOTAL_ALLOWANCE_LIMIT_PER_ACCOUNT) {
				txnCtx.setStatus(MAX_ALLOWANCES_EXCEEDED);
			}
			cryptoAllowancesMap.put(spender.asEntityNum(), amount);
			ownerAccount.setCryptoAllowances(cryptoAllowancesMap);
		}
	}

	private void applyNftAllowances(final List<NftAllowance> nftAllowances,
			final Account ownerAccount) {
		if (nftAllowances.isEmpty()) {
			return;
		}
		Map<FcTokenAllowanceId, FcTokenAllowance> nftAllowancesMap = ownerAccount.getNftAllowances();
		for (var allowance : nftAllowances) {
			final var spenderAccount = allowance.getSpender();
			final var approvedForAll = allowance.getApprovedForAll();
			final var serialNums = allowance.getSerialNumbersList();
			final var tokenId = allowance.getTokenId();
			final var spender = Id.fromGrpcAccount(spenderAccount);

			final var key = FcTokenAllowanceId.from(EntityNum.fromTokenId(tokenId),
					spender.asEntityNum());
			final var value = FcTokenAllowance.from(approvedForAll.getValue(), serialNums);
			if (nftAllowancesMap.containsKey(key)) {
				// No-Op need to submit adjustAllowance to adjust any allowances
				return;
			}
			nftAllowancesMap.put(key, value);
			ownerAccount.setNftAllowances(nftAllowancesMap);
		}

	}

	private void applyFungibleTokenAllowances(
			final List<TokenAllowance> tokenAllowances,
			final Account ownerAccount) {
		if (tokenAllowances.isEmpty()) {
			return;
		}
		Map<FcTokenAllowanceId, Long> tokenAllowancesMap = ownerAccount.getFungibleTokenAllowances();
		for (var allowance : tokenAllowances) {
			final var spenderAccount = allowance.getSpender();
			final var spender = Id.fromGrpcAccount(spenderAccount);
			final var amount = allowance.getAmount();
			final var tokenId = allowance.getTokenId();

			final var key = FcTokenAllowanceId.from(EntityNum.fromTokenId(tokenId),
					spender.asEntityNum());
			if (tokenAllowancesMap.containsKey(spender.asEntityNum())) {
				// No-Op need to submit adjustAllowance to adjust any allowances
				return;
			}
			if (amount == 0) {
				tokenAllowancesMap.remove(key);
				return;
			}
			tokenAllowancesMap.put(key, amount);
			ownerAccount.setFungibleTokenAllowances(tokenAllowancesMap);
		}

	}

	private boolean emptyAllowances(final CryptoApproveAllowanceTransactionBody op) {
		final var totalAllowances =
				op.getCryptoAllowancesCount() + op.getTokenAllowancesCount() + op.getNftAllowancesCount();
		return totalAllowances == 0;
	}

	private boolean exceedsLimitCount(final CryptoApproveAllowanceTransactionBody op) {
		final var totalAllowances =
				op.getCryptoAllowancesCount() + op.getTokenAllowancesCount() + op.getNftAllowancesCount();
		return totalAllowances > ALLOWANCE_LIMIT_PER_TRANSACTION;
	}
}
