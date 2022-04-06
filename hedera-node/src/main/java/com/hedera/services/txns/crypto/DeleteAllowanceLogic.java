package com.hedera.services.txns.crypto;

/*-
 * ‌
 * Hedera Services Node
 *
 * Copyright (C) 2018 - 2022 Hedera Hashgraph, LLC
 *
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

import com.hedera.services.state.submerkle.FcTokenAllowanceId;
import com.hedera.services.store.AccountStore;
import com.hedera.services.store.TypedTokenStore;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.UniqueToken;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoRemoveAllowance;
import com.hederahashgraph.api.proto.java.NftRemoveAllowance;
import com.hederahashgraph.api.proto.java.TokenRemoveAllowance;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.hedera.services.exceptions.ValidationUtils.validateTrue;
import static com.hedera.services.txns.crypto.helpers.AllowanceHelpers.fetchOwnerAccount;
import static com.hedera.services.txns.crypto.helpers.AllowanceHelpers.validOwner;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SENDER_DOES_NOT_OWN_NFT_SERIAL_NO;

public class DeleteAllowanceLogic {
	private final AccountStore accountStore;
	private final TypedTokenStore tokenStore;
	private final Map<Long, Account> entitiesChanged;
	private final List<UniqueToken> nftsTouched;

	@Inject
	public DeleteAllowanceLogic(final AccountStore accountStore,
								final TypedTokenStore tokenStore) {
		this.accountStore = accountStore;
		this.tokenStore = tokenStore;
		this.entitiesChanged = new HashMap<>();
		this.nftsTouched = new ArrayList<>();
	}

	public void deleteAllowance(List<CryptoRemoveAllowance> cryptoAllowancesList,
								List<TokenRemoveAllowance> tokenAllowancesList,
								List<NftRemoveAllowance> nftAllowancesList,
								AccountID payer) {
		entitiesChanged.clear();
		nftsTouched.clear();

		/* --- Use models --- */
		final Id payerId = Id.fromGrpcAccount(payer);
		final var payerAccount = accountStore.loadAccount(payerId);

		/* --- Do the business logic --- */
		deleteCryptoAllowances(cryptoAllowancesList, payerAccount);
		deleteFungibleTokenAllowances(tokenAllowancesList, payerAccount);
		deleteNftSerials(nftAllowancesList, payerAccount);

		/* --- Persist the owner accounts and nfts --- */
		for (final var nft : nftsTouched) {
			tokenStore.persistNft(nft);
		}
		for (final var entry : entitiesChanged.entrySet()) {
			accountStore.commitAccount(entry.getValue());
		}
	}

	/**
	 * Clear spender on the provided nft serials. If the owner is not provided in any allowance,
	 * considers payer of the transaction as owner while checking if nft is owned by owner.
	 *
	 * @param nftAllowances
	 * 		given nftAllowances
	 * @param payerAccount payer for the transaction
	 */
	private void deleteNftSerials(final List<NftRemoveAllowance> nftAllowances, final Account payerAccount) {
		if (nftAllowances.isEmpty()) {
			return;
		}

		final var nfts = new ArrayList<UniqueToken>();
		for (var allowance : nftAllowances) {
			final var serialNums = allowance.getSerialNumbersList();
			final var tokenId = Id.fromGrpcToken(allowance.getTokenId());
			final var accountToWipe = fetchOwnerAccount(allowance.getOwner(), payerAccount, accountStore,
					entitiesChanged);
			final var token = tokenStore.loadPossiblyPausedToken(tokenId);

			for (var serial : serialNums) {
				final var nft = tokenStore.loadUniqueToken(tokenId, serial);
				validateTrue(validOwner(nft, accountToWipe.getId(), token), SENDER_DOES_NOT_OWN_NFT_SERIAL_NO);
				nft.clearSpender();
				nfts.add(nft);
			}
			nftsTouched.addAll(nfts);
			nfts.clear();
		}
	}

	/**
	 * For all given token allowance sto be deleted, deletes the token entry from the fungible token allowances
	 * map on the owner account.If the owner is not provided in any allowance, considers payer of the transaction as
	 * owner.
	 *
	 * @param tokenAllowances
	 * 		given token allowances
	 * @param payerAccount
	 * 		payer for the transaction
	 */
	private void deleteFungibleTokenAllowances(final List<TokenRemoveAllowance> tokenAllowances,
											   final Account payerAccount) {
		if (tokenAllowances.isEmpty()) {
			return;
		}
		for (var allowance : tokenAllowances) {
			final var owner = allowance.getOwner();
			final var tokenId = allowance.getTokenId();
			final var accountToWipe = fetchOwnerAccount(owner, payerAccount, accountStore, entitiesChanged);
			final var tokensMap = accountToWipe.getMutableFungibleTokenAllowances();

			for (Map.Entry<FcTokenAllowanceId, Long> e : tokensMap.entrySet()) {
				if (e.getKey().getTokenNum().longValue() == tokenId.getTokenNum()) {
					tokensMap.remove(e.getKey());
				}
			}
			entitiesChanged.put(accountToWipe.getId().num(), accountToWipe);
		}
	}

	/**
	 * Deletes all the crypto allowances on given owner. If the owner is not provided in any allowance,
	 * considers payer of the transaction as owner.
	 *
	 * @param cryptoAllowances given crypto allowances
	 * @param payerAccount payer for the transaction
	 */
	private void deleteCryptoAllowances(final List<CryptoRemoveAllowance> cryptoAllowances,
										final Account payerAccount) {
		if (cryptoAllowances.isEmpty()) {
			return;
		}
		for (final var allowance : cryptoAllowances) {
			final var owner = allowance.getOwner();
			final var accountToWipe = fetchOwnerAccount(owner, payerAccount, accountStore, entitiesChanged);
			accountToWipe.getMutableCryptoAllowances().clear();
			entitiesChanged.put(accountToWipe.getId().num(), accountToWipe);
		}
	}
}
