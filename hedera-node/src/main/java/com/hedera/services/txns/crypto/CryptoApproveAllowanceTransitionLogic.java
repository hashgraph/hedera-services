package com.hedera.services.txns.crypto;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2022 Hedera Hashgraph, LLC
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
import com.hedera.services.state.submerkle.FcTokenAllowance;
import com.hedera.services.state.submerkle.FcTokenAllowanceId;
import com.hedera.services.store.AccountStore;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.txns.TransitionLogic;
import com.hedera.services.txns.crypto.validators.ApproveAllowanceChecks;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.accessors.CryptoAllowanceAccessor;
import com.hedera.services.utils.accessors.PlatformTxnAccessor;
import com.hedera.services.utils.accessors.TxnAccessor;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoAllowance;
import com.hederahashgraph.api.proto.java.NftAllowance;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenAllowance;
import com.hederahashgraph.api.proto.java.TransactionBody;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import static com.hedera.services.exceptions.ValidationUtils.validateFalse;
import static com.hedera.services.txns.crypto.helpers.AllowanceHelpers.fetchOwnerAccount;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ALLOWANCE_SPENDER_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MAX_ALLOWANCES_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

public class CryptoApproveAllowanceTransitionLogic implements TransitionLogic {
	private final TransactionContext txnCtx;
	private final AccountStore accountStore;
	private final ApproveAllowanceChecks allowanceChecks;
	private final GlobalDynamicProperties dynamicProperties;
	private final Map<Long, Account> entitiesChanged;

	@Inject
	public CryptoApproveAllowanceTransitionLogic(
			final TransactionContext txnCtx,
			final AccountStore accountStore,
			final ApproveAllowanceChecks allowanceChecks,
			final GlobalDynamicProperties dynamicProperties) {
		this.txnCtx = txnCtx;
		this.accountStore = accountStore;
		this.allowanceChecks = allowanceChecks;
		this.dynamicProperties = dynamicProperties;
		this.entitiesChanged = new HashMap<>();
	}

	@Override
	public void doStateTransition() {
		/* --- Extract gRPC --- */
		final var platformAccessor = (PlatformTxnAccessor) txnCtx.accessor();
		final var approveAccessor = (CryptoAllowanceAccessor) platformAccessor.getDelegate();
		final AccountID payer = approveAccessor.getPayer();
		entitiesChanged.clear();

		/* --- Use models --- */
		final Id payerId = Id.fromGrpcAccount(payer);
		final var payerAccount = accountStore.loadAccount(payerId);

		/* --- Do the business logic --- */
		applyCryptoAllowances(approveAccessor.getCryptoAllowances(), payerAccount);
		applyFungibleTokenAllowances(approveAccessor.getTokenAllowances(), payerAccount);
		applyNftAllowances(approveAccessor.getNftAllowances(), payerAccount);

		/* --- Persist the payer account --- */
		for (final var entry : entitiesChanged.entrySet()) {
			accountStore.commitAccount(entry.getValue());
		}

		txnCtx.setStatus(SUCCESS);
	}

	@Override
	public Predicate<TransactionBody> applicability() {
		return TransactionBody::hasCryptoApproveAllowance;
	}

	@Override
	public ResponseCodeEnum validateSemantics(TxnAccessor accessor) {
		final var platformAccessor = (PlatformTxnAccessor) accessor;
		final var approveAccessor = (CryptoAllowanceAccessor) platformAccessor.getDelegate();
		final AccountID payer = approveAccessor.getPayer();
		final var payerAccount = accountStore.loadAccount(Id.fromGrpcAccount(payer));

		return allowanceChecks.allowancesValidation(approveAccessor.getCryptoAllowances(),
				approveAccessor.getTokenAllowances(), approveAccessor.getNftAllowances(), payerAccount,
				dynamicProperties.maxAllowanceLimitPerTransaction());
	}

	/**
	 * Applies all changes needed for Crypto allowances from the transaction
	 *
	 * @param cryptoAllowances
	 * @param payerAccount
	 */
	private void applyCryptoAllowances(final List<CryptoAllowance> cryptoAllowances, final Account payerAccount) {
		if (cryptoAllowances.isEmpty()) {
			return;
		}
		for (final var allowance : cryptoAllowances) {
			final var owner = allowance.getOwner();
			final var accountToApprove = fetchOwnerAccount(owner, payerAccount, accountStore, entitiesChanged);
			final var cryptoMap = accountToApprove.getMutableCryptoAllowances();

			final var spender = Id.fromGrpcAccount(allowance.getSpender());
			accountStore.loadAccountOrFailWith(spender, INVALID_ALLOWANCE_SPENDER_ID);

			final var amount = allowance.getAmount();
			if (cryptoMap.containsKey(spender.asEntityNum())) {
				if (amount == 0) {
					removeEntity(cryptoMap, spender, accountToApprove);
				}
				// No-Op need to submit adjustAllowance to adjust any allowances
				continue;
			}
			cryptoMap.put(spender.asEntityNum(), amount);
			validateFalse(exceedsAccountLimit(accountToApprove), MAX_ALLOWANCES_EXCEEDED);
			entitiesChanged.put(accountToApprove.getId().num(), accountToApprove);
		}
	}

	private void removeEntity(final Map<EntityNum, Long> cryptoMap,
			final Id spender,
			final Account accountToApprove) {
		cryptoMap.remove(spender.asEntityNum());
		accountToApprove.setCryptoAllowances(cryptoMap);
		entitiesChanged.put(accountToApprove.getId().num(), accountToApprove);
	}

	/**
	 * Applies all changes needed for NFT allowances from the transaction
	 *
	 * @param nftAllowances
	 * @param payerAccount
	 */
	private void applyNftAllowances(final List<NftAllowance> nftAllowances, final Account payerAccount) {
		if (nftAllowances.isEmpty()) {
			return;
		}
		for (var allowance : nftAllowances) {
			final var owner = allowance.getOwner();
			final var accountToApprove = fetchOwnerAccount(owner, payerAccount, accountStore, entitiesChanged);
			final var nftMap = accountToApprove.getMutableNftAllowances();

			final var spender = Id.fromGrpcAccount(allowance.getSpender());
			accountStore.loadAccountOrFailWith(spender, INVALID_ALLOWANCE_SPENDER_ID);

			final var approvedForAll = allowance.getApprovedForAll();
			final var serialNums = allowance.getSerialNumbersList();
			final var tokenId = allowance.getTokenId();

			final var key = FcTokenAllowanceId.from(EntityNum.fromTokenId(tokenId),
					spender.asEntityNum());
			if (nftMap.containsKey(key)) {
				// No-Op need to submit adjustAllowance to adjust any allowances
				continue;
			}
			final FcTokenAllowance value = approvedForAll.getValue() ? FcTokenAllowance.from(
					true) : FcTokenAllowance.from(serialNums);
			nftMap.put(key, value);

			validateFalse(exceedsAccountLimit(accountToApprove), MAX_ALLOWANCES_EXCEEDED);
			entitiesChanged.put(accountToApprove.getId().num(), accountToApprove);
		}
	}

	/**
	 * Applies all changes needed for fungible token allowances from the transaction
	 *
	 * @param tokenAllowances
	 * @param payerAccount
	 */
	private void applyFungibleTokenAllowances(final List<TokenAllowance> tokenAllowances, final Account payerAccount) {
		if (tokenAllowances.isEmpty()) {
			return;
		}
		for (var allowance : tokenAllowances) {
			final var owner = allowance.getOwner();
			final var accountToApprove = fetchOwnerAccount(owner, payerAccount, accountStore, entitiesChanged);
			final var tokensMap = accountToApprove.getMutableFungibleTokenAllowances();

			final var spender = Id.fromGrpcAccount(allowance.getSpender());
			accountStore.loadAccountOrFailWith(spender, INVALID_ALLOWANCE_SPENDER_ID);

			final var amount = allowance.getAmount();
			final var tokenId = allowance.getTokenId();

			final var key = FcTokenAllowanceId.from(EntityNum.fromTokenId(tokenId),
					spender.asEntityNum());
			if (tokensMap.containsKey(key)) {
				if (amount == 0) {
					removeTokenEntity(key, tokensMap, accountToApprove);
				}
				// No-Op need to submit adjustAllowance to adjust any allowances
				continue;
			}
			tokensMap.put(key, amount);

			validateFalse(exceedsAccountLimit(accountToApprove), MAX_ALLOWANCES_EXCEEDED);
			entitiesChanged.put(accountToApprove.getId().num(), accountToApprove);
		}
	}

	private void removeTokenEntity(final FcTokenAllowanceId key,
			final Map<FcTokenAllowanceId, Long> tokensMap,
			final Account accountToApprove) {
		tokensMap.remove(key);
		accountToApprove.setFungibleTokenAllowances(tokensMap);
		entitiesChanged.put(accountToApprove.getId().num(), accountToApprove);
	}

	/**
	 * Checks if the total allowances of an account will exceed the limit after applying this transaction
	 *
	 * @param ownerAccount
	 * @return
	 */
	private boolean exceedsAccountLimit(final Account ownerAccount) {
		return ownerAccount.getTotalAllowances() > dynamicProperties.maxAllowanceLimitPerAccount();
	}
}
