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
import com.hedera.services.txns.crypto.validators.AdjustAllowanceChecks;
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
 * Implements the {@link TransitionLogic} for a HAPI CryptoAdjustAllowance transaction,
 * and the conditions under which such logic is syntactically correct.
 */
public class CryptoAdjustAllowanceTransitionLogic implements TransitionLogic {
	private final TransactionContext txnCtx;
	private final AccountStore accountStore;
	private final TypedTokenStore tokenStore;
	private final AdjustAllowanceChecks adjustAllowanceChecks;
	private final GlobalDynamicProperties dynamicProperties;
	private final SideEffectsTracker sideEffectsTracker;
	private final Map<Long, Account> entitiesChanged;
	private final StateView workingView;
	private final Map<NftId, UniqueToken> nftsTouched;

	@Inject
	public CryptoAdjustAllowanceTransitionLogic(
			final TransactionContext txnCtx,
			final AccountStore accountStore,
			final TypedTokenStore tokenStore,
			final AdjustAllowanceChecks allowanceChecks,
			final GlobalDynamicProperties dynamicProperties,
			final SideEffectsTracker sideEffectsTracker,
			final StateView workingView) {
		this.txnCtx = txnCtx;
		this.accountStore = accountStore;
		this.tokenStore = tokenStore;
		this.adjustAllowanceChecks = allowanceChecks;
		this.dynamicProperties = dynamicProperties;
		this.sideEffectsTracker = sideEffectsTracker;
		this.workingView = workingView;
		this.entitiesChanged = new HashMap<>();
		this.nftsTouched = new HashMap<>();
	}

	@Override
	public void doStateTransition() {
		/* --- Extract gRPC --- */
		final TransactionBody cryptoAdjustAllowanceTxn = txnCtx.accessor().getTxn();
		final AccountID payer = cryptoAdjustAllowanceTxn.getTransactionID().getAccountID();
		final var op = cryptoAdjustAllowanceTxn.getCryptoAdjustAllowance();
		entitiesChanged.clear();
		nftsTouched.clear();

		/* --- Use models --- */
		final Id payerId = Id.fromGrpcAccount(payer);
		final var payerAccount = accountStore.loadAccount(payerId);

		/* --- Do the business logic --- */
		adjustCryptoAllowances(op.getCryptoAllowancesList(), payerAccount);
		adjustFungibleTokenAllowances(op.getTokenAllowancesList(), payerAccount);
		adjustNftAllowances(op.getNftAllowancesList(), payerAccount);

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
		return TransactionBody::hasCryptoAdjustAllowance;
	}

	@Override
	public Function<TransactionBody, ResponseCodeEnum> semanticCheck() {
		return this::validate;
	}

	private ResponseCodeEnum validate(TransactionBody cryptoAllowanceTxn) {
		final AccountID payer = cryptoAllowanceTxn.getTransactionID().getAccountID();
		final var op = cryptoAllowanceTxn.getCryptoAdjustAllowance();
		final var payerAccount = accountStore.loadAccount(Id.fromGrpcAccount(payer));
		return adjustAllowanceChecks.allowancesValidation(op.getCryptoAllowancesList(),
				op.getTokenAllowancesList(), op.getNftAllowancesList(), payerAccount, workingView);
	}

	/**
	 * Adjust allowances based on all changes needed for CryptoAllowances from the transaction.
	 * If the spender doesn't exist in the allowances map, the cryptoAdjustAllowance transaction
	 * acts as approval for the allowance and inserts allowance to the map. If the aggregated allowance becomes zero
	 * after adding the amount to the existing spender's allowance, the spender's entry will be removed from the Map.
	 * reduced.
	 *
	 * @param cryptoAllowances
	 * 		newly given crypto allowances in the operation
	 * @param payerAccount
	 * 		account of the payer for this adjustAllowance txn
	 */
	private void adjustCryptoAllowances(final List<CryptoAllowance> cryptoAllowances, final Account payerAccount) {
		if (cryptoAllowances.isEmpty()) {
			return;
		}

		for (final var allowance : cryptoAllowances) {
			final var owner = allowance.getOwner();

			final var accountToAdjust = fetchOwnerAccount(owner, payerAccount, accountStore, entitiesChanged);
			final var cryptoMap = accountToAdjust.getMutableCryptoAllowances();

			final var spender = Id.fromGrpcAccount(allowance.getSpender());
			accountStore.loadAccountOrFailWith(spender, INVALID_ALLOWANCE_SPENDER_ID);

			final var amount = allowance.getAmount();

			// if there is no key in the map, adjust transaction acts as approve transaction
			if (!cryptoMap.containsKey(spender.asEntityNum())) {
				if (amount == 0) {
					//no-op transaction
					continue;
				}
				cryptoMap.put(spender.asEntityNum(), amount);
			} else {
				final var existingAmount = cryptoMap.get(spender.asEntityNum());
				final var aggregatedAmount = existingAmount + amount;
				if (aggregatedAmount == 0) {
					cryptoMap.remove(spender.asEntityNum());
				} else {
					cryptoMap.put(spender.asEntityNum(), aggregatedAmount);
				}
			}

			validateAllowanceLimitsOn(accountToAdjust, dynamicProperties.maxAllowanceLimitPerAccount());
			entitiesChanged.put(accountToAdjust.getId().num(), accountToAdjust);
			sideEffectsTracker.setCryptoAllowances(accountToAdjust.getId().asEntityNum(), cryptoMap);
		}
	}

	/**
	 * Adjusts all changes needed for NFT allowances from the transaction. If the key{tokenNum, spenderNum} doesn't
	 * exist in the map the allowance will be inserted. If the key exists, existing allowance values will be adjusted
	 * based on the new allowances given in operation
	 *
	 * @param nftAllowances
	 * 		newly given list of nft allowances
	 * @param payerAccount
	 * 		account of the payer for this adjustAllowance txn
	 */
	private void adjustNftAllowances(final List<NftAllowance> nftAllowances, final Account payerAccount) {
		if (nftAllowances.isEmpty()) {
			return;
		}

		for (var allowance : nftAllowances) {
			final var owner = allowance.getOwner();

			final var accountToAdjust = fetchOwnerAccount(owner, payerAccount, accountStore, entitiesChanged);
			final var mutableApprovedForAllNftsAllowances = accountToAdjust.getMutableApprovedForAllNftsAllowances();

			final var spenderAccount = allowance.getSpender();
			final var approvedForAll = allowance.getApprovedForAll();
			final var serialNums = allowance.getSerialNumbersList();
			final var tokenID = allowance.getTokenId();
			final var tokenId = Id.fromGrpcToken(tokenID);
			final var spender = Id.fromGrpcAccount(spenderAccount);
			accountStore.loadAccountOrFailWith(spender, INVALID_ALLOWANCE_SPENDER_ID);
			final var key = FcTokenAllowanceId.from(tokenId.asEntityNum(),
					spender.asEntityNum());

			if (approvedForAll.getValue()) {
				mutableApprovedForAllNftsAllowances.add(key);
			} else {
				mutableApprovedForAllNftsAllowances.remove(key);
			}

			validateAllowanceLimitsOn(accountToAdjust, dynamicProperties.maxAllowanceLimitPerAccount());

			final var nfts = updateSpender(tokenStore, accountToAdjust.getId(), spender, tokenId, serialNums);
			for (var nft : nfts) {
				nftsTouched.put(nft.getNftId(), nft);
			}
			entitiesChanged.put(accountToAdjust.getId().num(), accountToAdjust);
		}
	}

	/**
	 * Adjusts all changes needed for fungible token allowances from the transaction. If the key{tokenNum, spenderNum}
	 * doesn't exist in the map the allowance will be inserted. If the key exists, existing allowance will be adjusted
	 * based
	 * on the new allowances given in operation
	 *
	 * @param tokenAllowances
	 * 		newly given list of token allowances
	 * @param payerAccount
	 * 		account of the payer for this adjustAllowance txn
	 */
	private void adjustFungibleTokenAllowances(final List<TokenAllowance> tokenAllowances,
			final Account payerAccount) {
		if (tokenAllowances.isEmpty()) {
			return;
		}

		for (var allowance : tokenAllowances) {
			final var owner = allowance.getOwner();

			final var accountToAdjust = fetchOwnerAccount(owner, payerAccount, accountStore, entitiesChanged);
			final var tokenAllowancesMap = accountToAdjust.getMutableFungibleTokenAllowances();


			final var amount = allowance.getAmount();
			final var tokenId = allowance.getTokenId();
			final var spender = Id.fromGrpcAccount(allowance.getSpender());
			accountStore.loadAccountOrFailWith(spender, INVALID_ALLOWANCE_SPENDER_ID);

			final var key = FcTokenAllowanceId.from(EntityNum.fromTokenId(tokenId),
					spender.asEntityNum());
			if (!tokenAllowancesMap.containsKey(key)) {
				if (amount == 0) {
					continue;
				}
				tokenAllowancesMap.put(key, amount);
			} else {
				final var oldAmount = tokenAllowancesMap.get(key);
				final var aggregatedAmount = oldAmount + amount;
				if (aggregatedAmount == 0) {
					tokenAllowancesMap.remove(key);
				} else {
					tokenAllowancesMap.put(key, aggregatedAmount);
				}
			}

			validateAllowanceLimitsOn(accountToAdjust, dynamicProperties.maxAllowanceLimitPerAccount());
			entitiesChanged.put(accountToAdjust.getId().num(), accountToAdjust);
			sideEffectsTracker.setFungibleTokenAllowances(accountToAdjust.getId().asEntityNum(), tokenAllowancesMap);
		}
	}

}
