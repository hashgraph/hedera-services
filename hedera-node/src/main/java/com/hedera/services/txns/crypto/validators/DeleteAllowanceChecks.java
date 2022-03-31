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

import com.hedera.services.context.primitives.StateView;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.store.AccountStore;
import com.hedera.services.store.ReadOnlyTokenStore;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.Token;
import com.hedera.services.txns.validation.OptionValidator;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoRemoveAllowance;
import com.hederahashgraph.api.proto.java.NftRemoveAllowance;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenRemoveAllowance;
import org.apache.commons.lang3.tuple.Pair;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.EMPTY_ALLOWANCES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FUNGIBLE_TOKEN_IN_NFT_ALLOWANCES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NFT_IN_FUNGIBLE_TOKEN_ALLOWANCES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.REPEATED_ALLOWANCES_TO_DELETE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;

/**
 * Semantic check validation for {@link com.hederahashgraph.api.proto.java.CryptoDeleteAllowance} transaction
 */
@Singleton
public class DeleteAllowanceChecks extends AllowanceChecks {
	private final GlobalDynamicProperties dynamicProperties;
	private final OptionValidator validator;

	@Inject
	public DeleteAllowanceChecks(
			final GlobalDynamicProperties dynamicProperties,
			final OptionValidator validator) {
		super(dynamicProperties, validator);
		this.dynamicProperties = dynamicProperties;
		this.validator = validator;
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
	 * @param view
	 * @return validation response
	 */
	public ResponseCodeEnum deleteAllowancesValidation(
			final List<CryptoRemoveAllowance> cryptoAllowances,
			final List<TokenRemoveAllowance> tokenAllowances,
			final List<NftRemoveAllowance> nftAllowances,
			final Account payerAccount,
			final StateView view) {
		// feature flag for allowances
		if (!isEnabled()) {
			return NOT_SUPPORTED;
		}

		var validity = commonDeleteChecks(cryptoAllowances, tokenAllowances, nftAllowances);
		if (validity != OK) {
			return validity;
		}
		final var accountStore = new AccountStore(validator, dynamicProperties, view.asReadOnlyAccountStore());
		validity = validateCryptoDeleteAllowances(cryptoAllowances, payerAccount, accountStore);
		if (validity != OK) {
			return validity;
		}

		final var tokenStore = new ReadOnlyTokenStore(accountStore,
				view.asReadOnlyTokenStore(),
				view.asReadOnlyNftStore(),
				view.asReadOnlyAssociationStore());
		validity = validateTokenDeleteAllowances(tokenAllowances, payerAccount, tokenStore, accountStore);
		if (validity != OK) {
			return validity;
		}

		validity = validateNftDeleteAllowances(nftAllowances, payerAccount, tokenStore, accountStore);
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
	 * @param accountStore
	 * @return validation response
	 */
	ResponseCodeEnum validateCryptoDeleteAllowances(
			final List<CryptoRemoveAllowance> cryptoAllowances,
			final Account payerAccount,
			final AccountStore accountStore) {
		final Set<AccountID> distinctOwners = new HashSet<>();
		for (var allowance : cryptoAllowances) {
			if (!distinctOwners.contains(allowance.getOwner())) {
				distinctOwners.add(allowance.getOwner());
			}
		}

		if (cryptoAllowances.size() != distinctOwners.size()) {
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
	public ResponseCodeEnum validateTokenDeleteAllowances(
			final List<TokenRemoveAllowance> tokenAllowances,
			final Account payerAccount,
			final ReadOnlyTokenStore tokenStore,
			final AccountStore accountStore) {
		if (tokenAllowances.isEmpty()) {
			return OK;
		}

		final Set<TokenRemoveAllowance> distinctIds = new HashSet<>(tokenAllowances);
		if (distinctIds.size() != tokenAllowances.size()) {
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

			if (!tokenStore.hasAssociation(token, ownerAccount)) {
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
	public ResponseCodeEnum validateNftDeleteAllowances(
			final List<NftRemoveAllowance> nftAllowances,
			final Account payerAccount,
			final ReadOnlyTokenStore tokenStore,
			final AccountStore accountStore) {
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

			if (!tokenStore.hasAssociation(token, ownerAccount)) {
				return TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;
			}
			final var validity = validateDeleteSerialNums(serialNums, token, tokenStore);
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
	 * 		given nft allowance
	 * @return true if repeated , false otherwise
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

	private boolean serialsRepeated(final List<Long> existingSerials, final List<Long> newSerials) {
		for (var serial : newSerials) {
			if (existingSerials.contains(serial)) {
				return true;
			}
		}
		return false;
	}

	ResponseCodeEnum validateDeleteSerialNums(
			final List<Long> serialNums,
			final Token token,
			final ReadOnlyTokenStore tokenStore) {

		if (serialNums.isEmpty()) {
			return EMPTY_ALLOWANCES;
		}

		return validateSerialNums(serialNums, token, tokenStore);
	}

	@Override
	public ResponseCodeEnum validateAmount(final long amount, final Account owner, final Id spender) {
		throw new UnsupportedOperationException("Delete allowance checks will not validate amounts");
	}

	@Override
	public ResponseCodeEnum validateTokenAmount(final Account ownerAccount, final long amount, final Token token,
			final Id spender) {
		throw new UnsupportedOperationException("Delete allowance checks will not validate token amounts");
	}

	ResponseCodeEnum commonDeleteChecks(
			final List<CryptoRemoveAllowance> cryptoAllowances,
			final List<TokenRemoveAllowance> tokenAllowances,
			final List<NftRemoveAllowance> nftAllowances) {
		// each serial number of an NFT is considered as an allowance.
		// So for Nft allowances aggregated amount is considered for transaction limit calculation.
		// Number of serials will not be counted for allowance on account.
		final var totalAllowances = cryptoAllowances.size() + tokenAllowances.size() +
				aggregateNftDeleteAllowances(nftAllowances);

		return validateTotalAllowances(totalAllowances);
	}

	/**
	 * Gets sum of number of serials in the nft allowances. Considers duplicate serial numbers as well.
	 *
	 * @param nftAllowances
	 * 		give nft allowances
	 * @return number of serials
	 */
	int aggregateNftDeleteAllowances(List<NftRemoveAllowance> nftAllowances) {
		int count = 0;
		for (var allowance : nftAllowances) {
			count += allowance.getSerialNumbersCount();
		}
		return count;
	}
}
