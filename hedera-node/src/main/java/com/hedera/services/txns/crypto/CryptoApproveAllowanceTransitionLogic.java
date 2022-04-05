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

import com.hedera.services.context.SideEffectsTracker;
import com.hedera.services.context.TransactionContext;
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.state.submerkle.FcTokenAllowanceId;
import com.hedera.services.store.AccountStore;
import com.hedera.services.store.TypedTokenStore;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.NftId;
import com.hedera.services.store.models.UniqueToken;
import com.hedera.services.txns.TransitionLogic;
import com.hedera.services.txns.crypto.validators.ApproveAllowanceChecks;
import com.hedera.services.utils.EntityNum;
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
import java.util.function.Function;
import java.util.function.Predicate;

import static com.hedera.services.txns.crypto.helpers.AllowanceHelpers.fetchOwnerAccount;
import static com.hedera.services.txns.crypto.helpers.AllowanceHelpers.updateSpender;
import static com.hedera.services.txns.crypto.helpers.AllowanceHelpers.validateAllowanceLimitsOn;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ALLOWANCE_SPENDER_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

/**
 * Implements the {@link TransitionLogic} for a HAPI CryptoApproveAllowance transaction,
 * and the conditions under which such logic is syntactically correct.
 */
public class CryptoApproveAllowanceTransitionLogic implements TransitionLogic {
	private final TransactionContext txnCtx;
	private final AccountStore accountStore;
	private final TypedTokenStore tokenStore;
	private final ApproveAllowanceChecks allowanceChecks;
	private final GlobalDynamicProperties dynamicProperties;
	private final Map<Long, Account> entitiesChanged;
	private final StateView workingView;
	private final Map<NftId, UniqueToken> nftsTouched;
	private final SideEffectsTracker sideEffectsTracker;

	@Inject
	public CryptoApproveAllowanceTransitionLogic(
			final TransactionContext txnCtx,
			final AccountStore accountStore,
			final TypedTokenStore tokenStore,
			final ApproveAllowanceChecks allowanceChecks,
			final GlobalDynamicProperties dynamicProperties,
			final StateView workingView,
			final SideEffectsTracker sideEffectsTracker) {
		this.txnCtx = txnCtx;
		this.accountStore = accountStore;
		this.tokenStore = tokenStore;
		this.allowanceChecks = allowanceChecks;
		this.dynamicProperties = dynamicProperties;
		this.entitiesChanged = new HashMap<>();
		this.workingView = workingView;
		this.nftsTouched = new HashMap<>();
		this.sideEffectsTracker = sideEffectsTracker;
	}

	@Override
	public void doStateTransition() {
		/* --- Extract gRPC --- */
		final TransactionBody cryptoApproveAllowanceTxn = txnCtx.accessor().getTxn();
		final AccountID payer = cryptoApproveAllowanceTxn.getTransactionID().getAccountID();
		final var op = cryptoApproveAllowanceTxn.getCryptoApproveAllowance();
		entitiesChanged.clear();
		nftsTouched.clear();

		/* --- Use models --- */
		final Id payerId = Id.fromGrpcAccount(payer);
		final var payerAccount = accountStore.loadAccount(payerId);

		/* --- Do the business logic --- */
		applyCryptoAllowances(op.getCryptoAllowancesList(), payerAccount);
		applyFungibleTokenAllowances(op.getTokenAllowancesList(), payerAccount);
		applyNftAllowances(op.getNftAllowancesList(), payerAccount);

		/* --- Persist the entities --- */
		for (final var nft : nftsTouched.values()) {
			tokenStore.persistNft(nft);
		}
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
	public Function<TransactionBody, ResponseCodeEnum> semanticCheck() {
		return this::validate;
	}

	private ResponseCodeEnum validate(TransactionBody cryptoAllowanceTxn) {
		final AccountID payer = cryptoAllowanceTxn.getTransactionID().getAccountID();
		final var op = cryptoAllowanceTxn.getCryptoApproveAllowance();
		final var payerAccount = accountStore.loadAccount(Id.fromGrpcAccount(payer));

		return allowanceChecks.allowancesValidation(
				op.getCryptoAllowancesList(),
				op.getTokenAllowancesList(),
				op.getNftAllowancesList(),
				payerAccount,
				workingView);
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

			validateAllowanceLimitsOn(accountToApprove, dynamicProperties.maxAllowanceLimitPerAccount());
			entitiesChanged.put(accountToApprove.getId().num(), accountToApprove);
			sideEffectsTracker.setCryptoAllowances(accountToApprove.getId().asEntityNum(), cryptoMap);
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
	void applyNftAllowances(final List<NftAllowance> nftAllowances, final Account payerAccount) {
		if (nftAllowances.isEmpty()) {
			return;
		}
		for (var allowance : nftAllowances) {
			final var owner = allowance.getOwner();
			final var accountToApprove = fetchOwnerAccount(owner, payerAccount, accountStore, entitiesChanged);
			final var approveForAllNftsSet = accountToApprove.getMutableApprovedForAllNfts();

			final var spenderId = Id.fromGrpcAccount(allowance.getSpender());
			accountStore.loadAccountOrFailWith(spenderId, INVALID_ALLOWANCE_SPENDER_ID);

			final var tokenId = Id.fromGrpcToken(allowance.getTokenId());

			if (allowance.hasApprovedForAll() && allowance.getApprovedForAll().getValue()) {
				final var key = FcTokenAllowanceId.from(tokenId.asEntityNum(), spenderId.asEntityNum());
				approveForAllNftsSet.add(key);
			}

			validateAllowanceLimitsOn(accountToApprove, dynamicProperties.maxAllowanceLimitPerAccount());

			final var nfts = updateSpender(tokenStore, accountToApprove.getId(), spenderId, tokenId, allowance.getSerialNumbersList());
			for (var nft : nfts) {
				nftsTouched.put(nft.getNftId(), nft);
			}
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

			validateAllowanceLimitsOn(accountToApprove, dynamicProperties.maxAllowanceLimitPerAccount());
			entitiesChanged.put(accountToApprove.getId().num(), accountToApprove);
			sideEffectsTracker.setFungibleTokenAllowances(accountToApprove.getId().asEntityNum(), tokensMap);
		}
	}

	private void removeTokenEntity(final FcTokenAllowanceId key,
			final Map<FcTokenAllowanceId, Long> tokensMap,
			final Account accountToApprove) {
		tokensMap.remove(key);
		accountToApprove.setFungibleTokenAllowances(tokensMap);
		entitiesChanged.put(accountToApprove.getId().num(), accountToApprove);
	}
}
