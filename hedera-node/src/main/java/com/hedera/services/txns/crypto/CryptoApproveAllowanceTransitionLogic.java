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
import com.hedera.services.ledger.TransactionalLedger;
import com.hedera.services.ledger.properties.TokenRelProperty;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.state.submerkle.FcTokenAllowance;
import com.hedera.services.state.submerkle.FcTokenAllowanceId;
import com.hedera.services.store.AccountStore;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.Token;
import com.hedera.services.txns.TransitionLogic;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoAllowance;
import com.hederahashgraph.api.proto.java.CryptoApproveAllowanceTransactionBody;
import com.hederahashgraph.api.proto.java.NftAllowance;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenAllowance;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.apache.commons.lang3.tuple.Pair;

import javax.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

import static com.hedera.services.ledger.backing.BackingTokenRels.asTokenRel;
import static com.hedera.services.ledger.properties.TokenRelProperty.IS_FROZEN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_FROZEN_FOR_TOKEN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.AMOUNT_EXCEEDS_TOKEN_MAX_SUPPLY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CANNOT_APPROVE_FOR_ALL_FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.EMPTY_ALLOWANCES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MAX_ALLOWANCES_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NEGATIVE_ALLOWANCE_AMOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SPENDER_ACCOUNT_SAME_AS_OWNER;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;

public class CryptoApproveAllowanceTransitionLogic implements TransitionLogic {
	private final TransactionContext txnCtx;
	private final OptionValidator validator;
	private final SigImpactHistorian sigImpactHistorian;
	private final AccountStore accountStore;
	private final TransactionalLedger<Pair<AccountID, TokenID>, TokenRelProperty, MerkleTokenRelStatus> tokenRelsLedger;

	private static final int ALLOWANCE_LIMIT_PER_TRANSACTION = 20;

	@Inject
	public CryptoApproveAllowanceTransitionLogic(
			final TransactionContext txnCtx,
			final OptionValidator validator,
			final SigImpactHistorian sigImpactHistorian,
			final AccountStore accountStore,
			final TransactionalLedger<Pair<AccountID, TokenID>, TokenRelProperty, MerkleTokenRelStatus> tokenRelsLedger) {
		this.txnCtx = txnCtx;
		this.validator = validator;
		this.sigImpactHistorian = sigImpactHistorian;
		this.accountStore = accountStore;
		this.tokenRelsLedger = tokenRelsLedger;
	}

	@Override
	public void doStateTransition() {
		try {
			final TransactionBody cryptoApproveAllowanceTxn = txnCtx.accessor().getTxn();
			final AccountID owner = cryptoApproveAllowanceTxn.getTransactionID().getAccountID();
			final Id ownerId = Id.fromGrpcAccount(owner);
			final var ownerAccount = accountStore.loadAccount(ownerId);

			final var op = cryptoApproveAllowanceTxn.getCryptoApproveAllowance();
			validateAndApplyCryptoAllowances(op.getCryptoAllowancesList(), ownerAccount);
			validateAndApplyFungibleTokenAllowances(op.getTokenAllowancesList(), ownerAccount);
			validateAndApplyNftAllowances(op.getNftAllowancesList(), ownerAccount);

			accountStore.commitAccount(ownerAccount);

			sigImpactHistorian.markEntityChanged(ownerId.num());

			txnCtx.setStatus(SUCCESS);
		} catch (InsufficientFundsException ife) {
			txnCtx.setStatus(INSUFFICIENT_PAYER_BALANCE);
		}
	}

	@Override
	public Predicate<TransactionBody> applicability() {
		return TransactionBody::hasCryptoApproveAllowance;
	}

	@Override
	public Function<TransactionBody, ResponseCodeEnum> semanticCheck() {
		return this::validate;
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

	private void validateAndApplyCryptoAllowances(final List<CryptoAllowance> cryptoAllowances,
			final Account ownerAccount) {
		if (cryptoAllowances.size() > 0) {
			Map<EntityNum, Long> cryptoAllowancesMap = ownerAccount.getCryptoAllowances();
			for (final var allowance : cryptoAllowances) {
				final var spender = Id.fromGrpcAccount(allowance.getSpender());
				final var amount = allowance.getAmount();
				if (ownerAccount.equals(spender)) {
					txnCtx.setStatus(SPENDER_ACCOUNT_SAME_AS_OWNER);
					return;
				}
				if (amount < 0) {
					txnCtx.setStatus(NEGATIVE_ALLOWANCE_AMOUNT);
					return;
				}
				if (cryptoAllowancesMap.containsKey(spender.asEntityNum())) {
					// Should this return SPENDER_HAS_ALLOWANCE?
				}
				if (amount == 0) {
					cryptoAllowancesMap.remove(spender.asEntityNum());
					return;
				}
				cryptoAllowancesMap.put(spender.asEntityNum(), amount);
				ownerAccount.setCryptoAllowances(cryptoAllowancesMap);
			}
		}
	}

	private void validateAndApplyNftAllowances(final List<NftAllowance> nftAllowances,
			final Account ownerAccount) {
		if (nftAllowances.size() > 0) {
			Map<FcTokenAllowanceId, FcTokenAllowance> nftAllowancesMap = ownerAccount.getNftAllowances();
			final var ownerId = ownerAccount.getId();
			for (var allowance : nftAllowances) {
				final var spenderAccount = allowance.getSpender();
				final var approvedForAll = allowance.getApprovedForAll();
				final var serialNums = allowance.getSerialNumbersList();
				final var tokenId = allowance.getTokenId();

				final var spender = Id.fromGrpcAccount(spenderAccount);
				final var token = new Token(Id.fromGrpcToken(tokenId));

				if (ownerId.equals(spender)) {
					txnCtx.setStatus(SPENDER_ACCOUNT_SAME_AS_OWNER);
					return;
				}
				if (frozenAccounts(ownerAccount, spenderAccount, tokenId)) {
					txnCtx.setStatus(ACCOUNT_FROZEN_FOR_TOKEN);
					return;
				}
				if (!ownerAccount.isAssociatedWith(token.getId())) {
					txnCtx.setStatus(TOKEN_NOT_ASSOCIATED_TO_ACCOUNT);
					return;
				}
				if (approvedForAll.getValue() & token.isFungibleCommon()) {
					txnCtx.setStatus(CANNOT_APPROVE_FOR_ALL_FUNGIBLE_COMMON);
					return;
				}

				final var key = FcTokenAllowanceId.from(EntityNum.fromTokenId(tokenId),
						spender.asEntityNum());
				final var value = FcTokenAllowance.from(approvedForAll.getValue(), serialNums);
				if (nftAllowancesMap.containsKey(key)) {
					// Should this return SPENDER_HAS_ALLOWANCE?
				}
				nftAllowancesMap.put(key, value);
				ownerAccount.setNftAllowances(nftAllowancesMap);
			}
		}
	}

	private void validateAndApplyFungibleTokenAllowances(
			final List<TokenAllowance> tokenAllowances,
			final Account ownerAccount) {
		if (tokenAllowances.size() > 0) {
			Map<FcTokenAllowanceId, Long> tokenAllowancesMap = ownerAccount.getFungibleTokenAllowances();
			final var ownerId = ownerAccount.getId();
			for (var allowance : tokenAllowances) {
				final var spenderAccount = allowance.getSpender();
				final var spender = Id.fromGrpcAccount(spenderAccount);
				final var amount = allowance.getAmount();
				final var tokenId = allowance.getTokenId();
				final var token = new Token(Id.fromGrpcToken(tokenId));

				if (ownerId.equals(spender)) {
					txnCtx.setStatus(SPENDER_ACCOUNT_SAME_AS_OWNER);
					return;
				}
				if (amount < 0) {
					txnCtx.setStatus(NEGATIVE_ALLOWANCE_AMOUNT);
					return;
				}

				if (!ownerAccount.isAssociatedWith(Id.fromGrpcToken(tokenId))) {
					txnCtx.setStatus(TOKEN_NOT_ASSOCIATED_TO_ACCOUNT);
					return;
				}
				if (frozenAccounts(ownerAccount, spenderAccount, tokenId)) {
					txnCtx.setStatus(ACCOUNT_FROZEN_FOR_TOKEN);
					return;
				}
				if (amount > token.getMaxSupply()) {
					txnCtx.setStatus(AMOUNT_EXCEEDS_TOKEN_MAX_SUPPLY);
					return;
				}

				final var key = FcTokenAllowanceId.from(EntityNum.fromTokenId(tokenId),
						spender.asEntityNum());
				if (tokenAllowancesMap.containsKey(spender.asEntityNum())) {
					// Should this return SPENDER_HAS_ALLOWANCE?
				}
				if (amount == 0) {
					tokenAllowancesMap.remove(key);
					return;
				}
				tokenAllowancesMap.put(key, amount);
				ownerAccount.setFungibleTokenAllowances(tokenAllowancesMap);
			}
		}
	}

	private boolean frozenAccounts(final Account ownerAccount, final AccountID spender,
			final TokenID tokenId) {
		final var ownerRelation = asTokenRel(ownerAccount.getId().asGrpcAccount(),
				tokenId);
		final var spenderRelation = asTokenRel(spender, tokenId);
		return ((boolean) tokenRelsLedger.get(ownerRelation, IS_FROZEN)) ||
				((boolean) tokenRelsLedger.get(spenderRelation, IS_FROZEN));
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
