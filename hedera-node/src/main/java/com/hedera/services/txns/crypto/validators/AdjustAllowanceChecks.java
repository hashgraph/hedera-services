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

package com.hedera.services.txns.crypto.validators;

import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.ledger.TransactionalLedger;
import com.hedera.services.ledger.properties.NftProperty;
import com.hedera.services.state.merkle.MerkleUniqueToken;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.FcTokenAllowanceId;
import com.hedera.services.store.TypedTokenStore;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.NftId;
import com.hedera.services.store.models.Token;
import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.CryptoAllowance;
import com.hederahashgraph.api.proto.java.NftAllowance;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenAllowance;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;

import static com.hedera.services.ledger.properties.NftProperty.OWNER;
import static com.hedera.services.txns.crypto.helpers.AllowanceHelpers.absolute;
import static com.hedera.services.txns.crypto.helpers.AllowanceHelpers.hasRepeatedId;
import static com.hedera.services.txns.crypto.helpers.AllowanceHelpers.hasRepeatedSerials;
import static com.hedera.services.txns.crypto.helpers.AllowanceHelpers.hasRepeatedSpender;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.AMOUNT_EXCEEDS_TOKEN_MAX_SUPPLY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.EMPTY_ALLOWANCES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FUNGIBLE_TOKEN_IN_NFT_ALLOWANCES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_NFT_SERIAL_NUMBER;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NEGATIVE_ALLOWANCE_AMOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NFT_IN_FUNGIBLE_TOKEN_ALLOWANCES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.REPEATED_SERIAL_NUMS_IN_NFT_ALLOWANCES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SENDER_DOES_NOT_OWN_NFT_SERIAL_NO;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SPENDER_ACCOUNT_REPEATED_IN_ALLOWANCES;

public class AdjustAllowanceChecks extends AllowanceChecks {
	@Inject
	public AdjustAllowanceChecks(
			final TransactionalLedger<NftId, NftProperty, MerkleUniqueToken> nftsLedger,
			final TypedTokenStore tokenStore,
			final GlobalDynamicProperties dynamicProperties) {
		super(nftsLedger, tokenStore, dynamicProperties);
	}

	@Override
	protected ResponseCodeEnum validateCryptoAllowances(final List<CryptoAllowance> cryptoAllowancesList,
			final Account ownerAccount) {
		if (cryptoAllowancesList.isEmpty()) {
			return OK;
		}

		if (hasRepeatedSpender(cryptoAllowancesList.stream().map(CryptoAllowance::getSpender).toList())) {
			return SPENDER_ACCOUNT_REPEATED_IN_ALLOWANCES;
		}

		final var existingAllowances = ownerAccount.getCryptoAllowances();
		for (final var allowance : cryptoAllowancesList) {
			final var spender = Id.fromGrpcAccount(allowance.getSpender());
			final var allowanceOwner = Id.fromGrpcAccount(allowance.getOwner());
			final var amount = allowance.getAmount();

			final var key = spender.asEntityNum();
			final var existingAmount = existingAllowances.containsKey(key) ? existingAllowances.get(key) : 0;

			// validate if the total allowance combined with existing allowance < 0
			var validity = validateAmount(amount, existingAmount);
			if (validity != OK) {
				return validity;
			}
			validity = validateCryptoAllowanceBasics(ownerAccount.getId(), allowanceOwner, spender);
			if (validity != OK) {
				return validity;
			}
		}
		return OK;
	}

	@Override
	ResponseCodeEnum validateFungibleTokenAllowances(final List<TokenAllowance> tokenAllowancesList,
			final Account ownerAccount) {
		if (tokenAllowancesList.isEmpty()) {
			return OK;
		}

		if (hasRepeatedId(
				tokenAllowancesList.stream().map(a -> FcTokenAllowanceId.from(EntityNum.fromTokenId(a.getTokenId()),
						EntityNum.fromAccountId(a.getSpender()))).toList())) {
			return SPENDER_ACCOUNT_REPEATED_IN_ALLOWANCES;
		}

		final var existingAllowances = ownerAccount.getFungibleTokenAllowances();

		for (final var allowance : tokenAllowancesList) {
			final var spenderAccountId = allowance.getSpender();
			final var owner = Id.fromGrpcAccount(allowance.getOwner());
			final var amount = allowance.getAmount();
			final var tokenId = allowance.getTokenId();
			final var token = tokenStore.loadPossiblyPausedToken(Id.fromGrpcToken(tokenId));
			final var spenderId = Id.fromGrpcAccount(spenderAccountId);

			final var key = FcTokenAllowanceId.from(token.getId().asEntityNum(), spenderId.asEntityNum());
			final var existingAllowance = existingAllowances.containsKey(key) ? existingAllowances.get(key) : 0;

			if (!token.isFungibleCommon()) {
				return NFT_IN_FUNGIBLE_TOKEN_ALLOWANCES;
			}

			var validity = validateTokenAmount(amount, existingAllowance, token);
			if (validity != OK) {
				return validity;
			}

			validity = validateTokenBasics(ownerAccount, spenderId, tokenId, owner);
			if (validity != OK) {
				return validity;
			}
		}
		return OK;
	}

	@Override
	ResponseCodeEnum validateNftAllowances(final List<NftAllowance> nftAllowancesList,
			final Account ownerAccount) {
		if (nftAllowancesList.isEmpty()) {
			return OK;
		}

		if (hasRepeatedId(
				nftAllowancesList.stream().map(a -> FcTokenAllowanceId.from(EntityNum.fromTokenId(a.getTokenId()),
						EntityNum.fromAccountId(a.getSpender()))).toList())) {
			return SPENDER_ACCOUNT_REPEATED_IN_ALLOWANCES;
		}
		final var existingAllowances = ownerAccount.getNftAllowances();

		for (final var allowance : nftAllowancesList) {
			final var spenderAccountId = allowance.getSpender();
			final var owner = Id.fromGrpcAccount(allowance.getOwner());
			final var tokenId = allowance.getTokenId();
			final var serialNums = allowance.getSerialNumbersList();
			final var token = tokenStore.loadPossiblyPausedToken(Id.fromGrpcToken(tokenId));
			final var spenderId = Id.fromGrpcAccount(spenderAccountId);
			final var approvedForAll = allowance.getApprovedForAll();
			final var key = FcTokenAllowanceId.from(token.getId().asEntityNum(), spenderId.asEntityNum());
			final var existingSerials = existingAllowances.containsKey(key) ?
					existingAllowances.get(key).getSerialNumbers()
					: new ArrayList<Long>();

			if (token.isFungibleCommon()) {
				return FUNGIBLE_TOKEN_IN_NFT_ALLOWANCES;
			}

			var validity = validateTokenBasics(ownerAccount, spenderId, tokenId, owner);
			if (validity != OK) {
				return validity;
			}

			validity = validateSerialNums(serialNums, ownerAccount, token, approvedForAll.getValue(), existingSerials);
			if (validity != OK) {
				return validity;
			}
		}
		return OK;
	}

	ResponseCodeEnum validateSerialNums(final List<Long> serialNums,
			final Account ownerAccount,
			final Token token,
			final boolean approvedForAll,
			List<Long> existingSerials) {
		if (hasRepeatedSerials(serialNums)) {
			return REPEATED_SERIAL_NUMS_IN_NFT_ALLOWANCES;
		}

		if (!approvedForAll && serialNums.isEmpty()) {
			return EMPTY_ALLOWANCES;
		}

		for (var serial : serialNums) {
			var absoluteSerial = absolute(serial);
			final var nftId = NftId.withDefaultShardRealm(token.getId().num(), absoluteSerial);

			if ((serial < 0 && !existingSerials.contains(absoluteSerial)) || absoluteSerial == 0) {
				return INVALID_TOKEN_NFT_SERIAL_NUMBER;
			}
			if (serial > 0 && existingSerials.contains(absoluteSerial)) {
				return REPEATED_SERIAL_NUMS_IN_NFT_ALLOWANCES;
			}
			if (!nftsLedger.exists(nftId)) {
				return INVALID_TOKEN_NFT_SERIAL_NUMBER;
			}

			final var owner = (EntityId) nftsLedger.get(nftId, OWNER);
			if (!ownerAccount.getId().asEntityId().equals(owner)) {
				return SENDER_DOES_NOT_OWN_NFT_SERIAL_NO;
			}
		}

		return OK;
	}

	ResponseCodeEnum validateAmount(final long amount, final long existingAmount) {
		if (amount + existingAmount < 0) {
			return NEGATIVE_ALLOWANCE_AMOUNT;
		}
		return OK;
	}

	ResponseCodeEnum validateTokenAmount(final long amount, final long existingAllowance,
			Token fungibleToken) {
		final long aggregatedAmount = amount + existingAllowance;
		if (aggregatedAmount < 0) {
			return NEGATIVE_ALLOWANCE_AMOUNT;
		}

		if (aggregatedAmount > fungibleToken.getMaxSupply()) {
			return AMOUNT_EXCEEDS_TOKEN_MAX_SUPPLY;
		}
		return OK;
	}
}
