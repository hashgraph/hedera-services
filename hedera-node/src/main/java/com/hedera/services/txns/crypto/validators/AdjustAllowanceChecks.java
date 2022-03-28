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
import com.hedera.services.state.enums.TokenSupplyType;
import com.hedera.services.state.merkle.MerkleUniqueToken;
import com.hedera.services.state.submerkle.FcTokenAllowanceId;
import com.hedera.services.store.AccountStore;
import com.hedera.services.store.TypedTokenStore;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.Token;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.EntityNumPair;
import com.hederahashgraph.api.proto.java.CryptoAllowance;
import com.hederahashgraph.api.proto.java.NftAllowance;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenAllowance;
import com.swirlds.merkle.map.MerkleMap;
import org.apache.commons.lang3.tuple.Pair;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static com.hedera.services.txns.crypto.helpers.AllowanceHelpers.buildEntityNumPairFrom;
import static com.hedera.services.txns.crypto.helpers.AllowanceHelpers.buildTokenAllowanceKey;
import static com.hedera.services.txns.crypto.helpers.AllowanceHelpers.hasRepeatedId;
import static com.hedera.services.txns.crypto.helpers.AllowanceHelpers.hasRepeatedSpender;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.AMOUNT_EXCEEDS_TOKEN_MAX_SUPPLY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NEGATIVE_ALLOWANCE_AMOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NFT_IN_FUNGIBLE_TOKEN_ALLOWANCES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SPENDER_ACCOUNT_REPEATED_IN_ALLOWANCES;

@Singleton
public class AdjustAllowanceChecks implements AllowanceChecks {
	protected final AccountStore accountStore;
	protected final TypedTokenStore tokenStore;
	protected final Supplier<MerkleMap<EntityNumPair, MerkleUniqueToken>> nftsMap;
	protected final GlobalDynamicProperties dynamicProperties;

	@Inject
	public AdjustAllowanceChecks(
			final Supplier<MerkleMap<EntityNumPair, MerkleUniqueToken>> nftsMap,
			final TypedTokenStore tokenStore,
			final AccountStore accountStore,
			final GlobalDynamicProperties dynamicProperties) {
		this.accountStore = accountStore;
		this.tokenStore = tokenStore;
		this.nftsMap = nftsMap;
		this.dynamicProperties = dynamicProperties;
	}

	@Override
	public ResponseCodeEnum validateCryptoAllowances(final List<CryptoAllowance> cryptoAllowancesList,
			final Account payerAccount) {
		if (cryptoAllowancesList.isEmpty()) {
			return OK;
		}
		final List<EntityNumPair> cryptoKeys = new ArrayList<>();
		for (var allowance : cryptoAllowancesList) {
			cryptoKeys.add(buildEntityNumPairFrom(
					allowance.getOwner(),
					allowance.getSpender(),
					payerAccount.getId().asEntityNum()));
		}
		if (hasRepeatedSpender(cryptoKeys)) {
			return SPENDER_ACCOUNT_REPEATED_IN_ALLOWANCES;
		}

		for (final var allowance : cryptoAllowancesList) {
			final var owner = Id.fromGrpcAccount(allowance.getOwner());
			final var spender = Id.fromGrpcAccount(allowance.getSpender());
			final var amount = allowance.getAmount();
			final var fetchResult = fetchOwnerAccount(owner, payerAccount, accountStore);
			if (fetchResult.getRight() != OK) {
				return fetchResult.getRight();
			}
			final var ownerAccount = fetchResult.getLeft();
			final var existingAllowances = ownerAccount.getCryptoAllowances();
			final var key = spender.asEntityNum();
			final var existingAmount = existingAllowances.containsKey(key) ? existingAllowances.get(key) : 0;

			// validate if the total allowance combined with existing allowance < 0
			var validity = validateAmount(amount, existingAmount);
			if (validity != OK) {
				return validity;
			}
			validity = validateCryptoAllowanceBasics(ownerAccount.getId(), spender);
			if (validity != OK) {
				return validity;
			}
		}
		return OK;
	}

	@Override
	public ResponseCodeEnum validateFungibleTokenAllowances(final List<TokenAllowance> tokenAllowancesList,
			final Account payerAccount) {
		if (tokenAllowancesList.isEmpty()) {
			return OK;
		}
		final List<Pair<EntityNum, FcTokenAllowanceId>> tokenKeys = new ArrayList<>();
		for (var allowance : tokenAllowancesList) {
			tokenKeys.add(
					buildTokenAllowanceKey(allowance.getOwner(), allowance.getTokenId(), allowance.getSpender()));
		}
		if (hasRepeatedId(tokenKeys)) {
			return SPENDER_ACCOUNT_REPEATED_IN_ALLOWANCES;
		}

		for (final var allowance : tokenAllowancesList) {
			final var owner = Id.fromGrpcAccount(allowance.getOwner());
			final var spender = Id.fromGrpcAccount(allowance.getSpender());
			final var amount = allowance.getAmount();
			final var tokenId = allowance.getTokenId();
			final var token = tokenStore.loadPossiblyPausedToken(Id.fromGrpcToken(tokenId));
			final var fetchResult = fetchOwnerAccount(owner, payerAccount, accountStore);
			if (fetchResult.getRight() != OK) {
				return fetchResult.getRight();
			}
			final var ownerAccount = fetchResult.getLeft();
			final var existingAllowances = ownerAccount.getFungibleTokenAllowances();

			final var key = FcTokenAllowanceId.from(token.getId().asEntityNum(), spender.asEntityNum());
			final var existingAllowance = existingAllowances.containsKey(key) ? existingAllowances.get(key) : 0;

			if (!token.isFungibleCommon()) {
				return NFT_IN_FUNGIBLE_TOKEN_ALLOWANCES;
			}

			var validity = validateTokenAmount(amount, existingAllowance, token);
			if (validity != OK) {
				return validity;
			}

			validity = validateTokenBasics(ownerAccount, spender, tokenId);
			if (validity != OK) {
				return validity;
			}
		}
		return OK;
	}

	@Override
	public ResponseCodeEnum validateNftAllowances(final List<NftAllowance> nftAllowancesList,
			final Account payerAccount) {
		return validateNftAllowances(nftsMap, tokenStore, accountStore, nftAllowancesList, payerAccount);
	}

	@Override
	public boolean isEnabled() {
		return dynamicProperties.areAllowancesEnabled();
	}

	/**
	 * Validates if aggregated amount is less than zero
	 *
	 * @param amount
	 * 		given amount
	 * @param existingAmount
	 * 		existing allowance on owner account
	 * @return response code after validation
	 */
	ResponseCodeEnum validateAmount(final long amount, final long existingAmount) {
		if (amount + existingAmount < 0) {
			return NEGATIVE_ALLOWANCE_AMOUNT;
		}
		return OK;
	}

	/**
	 * Validates if the amount given is less tha zero for fungible token or if the
	 * amount exceeds token max supply
	 *
	 * @param amount
	 * 		given amount in the operation
	 * @param existingAllowance
	 * 		existing allowance for the fungible token on owner account
	 * @param fungibleToken
	 * 		fungible token for which allowance is related to
	 * @return response code after validation
	 */
	ResponseCodeEnum validateTokenAmount(final long amount,
			final long existingAllowance,
			Token fungibleToken) {
		final long aggregatedAmount = amount + existingAllowance;
		if (aggregatedAmount < 0) {
			return NEGATIVE_ALLOWANCE_AMOUNT;
		}

		if (fungibleToken.getSupplyType().equals(TokenSupplyType.FINITE) &&
				aggregatedAmount > fungibleToken.getMaxSupply()) {
			return AMOUNT_EXCEEDS_TOKEN_MAX_SUPPLY;
		}
		return OK;
	}
}
