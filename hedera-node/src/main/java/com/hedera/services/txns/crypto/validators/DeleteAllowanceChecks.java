package com.hedera.services.txns.crypto.validators;

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

import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.exceptions.InvalidTransactionException;
import com.hedera.services.state.merkle.MerkleUniqueToken;
import com.hedera.services.store.AccountStore;
import com.hedera.services.store.TypedTokenStore;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.NftId;
import com.hedera.services.store.models.Token;
import com.hedera.services.utils.EntityNumPair;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoRemoveAllowance;
import com.hederahashgraph.api.proto.java.NftRemoveAllowance;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenRemoveAllowance;
import com.swirlds.merkle.map.MerkleMap;
import org.apache.commons.lang3.tuple.Pair;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.hedera.services.txns.crypto.helpers.AllowanceHelpers.hasRepeatedSerials;
import static com.hedera.services.txns.crypto.validators.AllowanceChecks.exceedsTxnLimit;
import static com.hedera.services.txns.crypto.validators.AllowanceChecks.fetchOwnerAccount;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.EMPTY_ALLOWANCES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FUNGIBLE_TOKEN_IN_NFT_ALLOWANCES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ALLOWANCE_OWNER_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_NFT_SERIAL_NUMBER;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MAX_ALLOWANCES_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NFT_IN_FUNGIBLE_TOKEN_ALLOWANCES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.REPEATED_ALLOWANCES_TO_DELETE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.REPEATED_SERIAL_NUMS_IN_NFT_ALLOWANCES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SENDER_DOES_NOT_OWN_NFT_SERIAL_NO;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;

/**
 * Semantic check validation for {@link com.hederahashgraph.api.proto.java.CryptoDeleteAllowance} transaction
 */
@Singleton
public class DeleteAllowanceChecks {
	protected final TypedTokenStore tokenStore;
	protected final AccountStore accountStore;
	protected final Supplier<MerkleMap<EntityNumPair, MerkleUniqueToken>> nftsMap;
	protected final GlobalDynamicProperties dynamicProperties;

	@Inject
	public DeleteAllowanceChecks(
			final Supplier<MerkleMap<EntityNumPair, MerkleUniqueToken>> nftsMap,
			final TypedTokenStore tokenStore,
			final AccountStore accountStore,
			final GlobalDynamicProperties dynamicProperties) {
		this.tokenStore = tokenStore;
		this.nftsMap = nftsMap;
		this.dynamicProperties = dynamicProperties;
		this.accountStore = accountStore;
	}

	/**
	 * Validates all allowances provided in
	 * {@link com.hederahashgraph.api.proto.java.CryptoDeleteAllowanceTransactionBody}
	 *
	 * @param cryptoAllowances
	 * 		given crypto allowances to remove
	 * @param tokenAllowances
	 * 		given fungible token allowances to remove
	 * @param nftAllowances
	 * 		given nft serials allowances to remove
	 * @param payerAccount
	 * 		payer for the transaction
	 * @return validation response
	 */
	public ResponseCodeEnum deleteAllowancesValidation(
			final List<CryptoRemoveAllowance> cryptoAllowances,
			final List<TokenRemoveAllowance> tokenAllowances,
			final List<NftRemoveAllowance> nftAllowances,
			final Account payerAccount) {
		// feature flag for allowances
		if (!isEnabled()) {
			return NOT_SUPPORTED;
		}

		var validity = commonChecks(cryptoAllowances, tokenAllowances, nftAllowances);
		if (validity != OK) {
			return validity;
		}

		validity = validateCryptoDeleteAllowances(cryptoAllowances, payerAccount);
		if (validity != OK) {
			return validity;
		}

		validity = validateTokenDeleteAllowances(tokenAllowances, payerAccount);
		if (validity != OK) {
			return validity;
		}

		validity = validateNftDeleteAllowances(nftAllowances, payerAccount);
		if (validity != OK) {
			return validity;
		}

		return OK;
	}

	/**
	 * Validates all the {@link CryptoRemoveAllowance}s in the
	 * {@link com.hederahashgraph.api.proto.java.CryptoDeleteAllowance}
	 * transaction
	 *
	 * @param cryptoAllowances
	 * 		crypto remove allowances list
	 * @param payerAccount
	 * 		payer for the transaction
	 * @return validation response
	 */
	ResponseCodeEnum validateCryptoDeleteAllowances(final List<CryptoRemoveAllowance> cryptoAllowances,
			final Account payerAccount) {
		final var distinctOwners = cryptoAllowances.stream()
				.map(CryptoRemoveAllowance::getOwner).distinct().count();
		if (cryptoAllowances.size() != distinctOwners) {
			return REPEATED_ALLOWANCES_TO_DELETE;
		}

		for (var allowance : cryptoAllowances) {
			final var owner = Id.fromGrpcAccount(allowance.getOwner());
			final var result = fetchOwnerAccount(owner, payerAccount, accountStore);
			if (result.getRight() != OK) {
				return result.getRight();
			}
		}
		return OK;
	}

	/**
	 * Validates all the {@link TokenRemoveAllowance}s in the
	 * {@link com.hederahashgraph.api.proto.java.CryptoDeleteAllowance}
	 * transaction
	 *
	 * @param tokenAllowances
	 * 		token remove allowances list
	 * @param payerAccount
	 * 		payer for the transaction
	 * @return validation response
	 */
	public ResponseCodeEnum validateTokenDeleteAllowances(final List<TokenRemoveAllowance> tokenAllowances,
			final Account payerAccount) {
		if (tokenAllowances.isEmpty()) {
			return OK;
		}

		final var distinctIds = tokenAllowances.stream().distinct().count();
		if (distinctIds != tokenAllowances.size()) {
			return REPEATED_ALLOWANCES_TO_DELETE;
		}

		for (final var allowance : tokenAllowances) {
			final var tokenId = allowance.getTokenId();
			var owner = Id.fromGrpcAccount(allowance.getOwner());

			final var token = tokenStore.loadPossiblyPausedToken(Id.fromGrpcToken(tokenId));
			final var fetchResult = fetchOwnerAccount(owner, payerAccount, accountStore);
			if (fetchResult.getRight() != OK) {
				return fetchResult.getRight();
			}

			final var ownerAccount = fetchResult.getLeft();
			if (!token.isFungibleCommon()) {
				return NFT_IN_FUNGIBLE_TOKEN_ALLOWANCES;
			}

			if (!ownerAccount.isAssociatedWith(Id.fromGrpcToken(tokenId))) {
				return TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;
			}
		}
		return OK;
	}

	/**
	 * Validates all the {@link NftRemoveAllowance}s in the
	 * {@link com.hederahashgraph.api.proto.java.CryptoDeleteAllowance}
	 * transaction
	 *
	 * @param nftAllowances
	 * 		nft remove allowances list
	 * @param payerAccount
	 * 		payer for the transaction
	 * @return validation response
	 */
	public ResponseCodeEnum validateNftDeleteAllowances(final List<NftRemoveAllowance> nftAllowances,
			final Account payerAccount) {
		if (nftAllowances.isEmpty()) {
			return OK;
		}
		if (repeatedAllowances(nftAllowances)) {
			return REPEATED_ALLOWANCES_TO_DELETE;
		}
		for (var allowance : nftAllowances) {
			final var owner = Id.fromGrpcAccount(allowance.getOwner());
			final var tokenId = allowance.getTokenId();
			final var serialNums = allowance.getSerialNumbersList();
			final var token = tokenStore.loadPossiblyPausedToken(Id.fromGrpcToken(tokenId));

			if (token.isFungibleCommon()) {
				return FUNGIBLE_TOKEN_IN_NFT_ALLOWANCES;
			}

			final var fetchResult = fetchOwnerAccount(owner, payerAccount, accountStore);
			if (fetchResult.getRight() != OK) {
				return fetchResult.getRight();
			}
			final var ownerAccount = fetchResult.getLeft();

			if (!ownerAccount.isAssociatedWith(Id.fromGrpcToken(tokenId))) {
				return TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;
			}
			final var validity = validateSerialNums(serialNums, ownerAccount, token);
			if (validity != OK) {
				return validity;
			}
		}

		return OK;
	}

	/**
	 * Checks if the nft allowances are repeated. Also validates if the serial numbers are repeated.
	 *
	 * @param nftAllowances
	 * @return
	 */
	boolean repeatedAllowances(final List<NftRemoveAllowance> nftAllowances) {
		Map<Pair<AccountID, TokenID>, List<Long>> seenNfts = new HashMap<>();
		for (var allowance : nftAllowances) {
			final var key = Pair.of(allowance.getOwner(), allowance.getTokenId());
			if (seenNfts.containsKey(key)) {
				if (serialsRepeated(seenNfts.get(key), allowance.getSerialNumbersList())) {
					return true;
				} else {
					final var list = new ArrayList<Long>();
					list.addAll(seenNfts.get(key));
					list.addAll(allowance.getSerialNumbersList());
					seenNfts.put(key, list);
				}
			} else {
				seenNfts.put(key, allowance.getSerialNumbersList());
			}
		}
		return false;
	}

	boolean serialsRepeated(final List<Long> existingSerials, final List<Long> newSerials) {
		for (var serial : newSerials) {
			if (existingSerials.contains(serial)) {
				return true;
			}
		}
		return false;
	}

	ResponseCodeEnum validateSerialNums(final List<Long> serialNums,
			final Account ownerAccount,
			final Token token) {
		if (hasRepeatedSerials(serialNums)) {
			return REPEATED_SERIAL_NUMS_IN_NFT_ALLOWANCES;
		}

		if (serialNums.isEmpty()) {
			return EMPTY_ALLOWANCES;
		}

		for (var serial : serialNums) {
			final var nftId = NftId.withDefaultShardRealm(token.getId().num(), serial);

			if (serial < 0 || serial == 0 || !nftsMap.get().containsKey(EntityNumPair.fromNftId(nftId))) {
				return INVALID_TOKEN_NFT_SERIAL_NUMBER;
			}

			final var nft = nftsMap.get().get(EntityNumPair.fromNftId(nftId));
			if (!AllowanceChecks.validOwner(nft, ownerAccount, token)) {
				return SENDER_DOES_NOT_OWN_NFT_SERIAL_NO;
			}
		}

		return OK;
	}

	ResponseCodeEnum commonChecks(
			final List<CryptoRemoveAllowance> cryptoAllowances,
			final List<TokenRemoveAllowance> tokenAllowances,
			final List<NftRemoveAllowance> nftAllowances) {
		// each serial number of an NFT is considered as an allowance.
		// So for Nft allowances aggregated amount is considered for limit calculation
		final var totalAllowances = cryptoAllowances.size() + tokenAllowances.size() +
				aggregateNftAllowances(nftAllowances);

		if (exceedsTxnLimit(totalAllowances, dynamicProperties.maxAllowanceLimitPerTransaction())) {
			return MAX_ALLOWANCES_EXCEEDED;
		}
		if (totalAllowances == 0) {
			return EMPTY_ALLOWANCES;
		}
		return OK;
	}

	/**
	 * Gets sum of number of serials in the nft allowances. Considers duplicate serial numbers as well.
	 *
	 * @param nftAllowances
	 * 		give nft allowances
	 * @return number of serials
	 */
	int aggregateNftAllowances(List<NftRemoveAllowance> nftAllowances) {
		return nftAllowances.stream().mapToInt(a -> a.getSerialNumbersCount()).sum();
	}

	/**
	 * Feature flag to check for allowances is enabled/disabled
	 *
	 * @return true if enabled, false otherwise
	 */
	private boolean isEnabled() {
		return dynamicProperties.areAllowancesEnabled();
	}
}
