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

package com.hedera.services.txns.crypto.validators;

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
import com.hederahashgraph.api.proto.java.CryptoApproveAllowanceTransactionBody;
import com.hederahashgraph.api.proto.java.NftAllowance;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenAllowance;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;

import javax.inject.Inject;
import java.util.List;

import static com.hedera.services.ledger.properties.NftProperty.OWNER;
import static com.hedera.services.txns.crypto.CryptoApproveAllowanceTransitionLogic.ALLOWANCE_LIMIT_PER_TRANSACTION;
import static com.hedera.services.txns.crypto.helpers.AllowanceHelpers.hasRepeatedId;
import static com.hedera.services.txns.crypto.helpers.AllowanceHelpers.hasRepeatedSerials;
import static com.hedera.services.txns.crypto.helpers.AllowanceHelpers.hasRepeatedSpender;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.AMOUNT_EXCEEDS_TOKEN_MAX_SUPPLY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.EMPTY_ALLOWANCES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FUNGIBLE_TOKEN_IN_NFT_ALLOWANCES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_NFT_SERIAL_NUMBER;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MAX_ALLOWANCES_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NEGATIVE_ALLOWANCE_AMOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NFT_IN_FUNGIBLE_TOKEN_ALLOWANCES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.PAYER_AND_OWNER_NOT_EQUAL;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.REPEATED_SERIAL_NUMS_IN_NFT_ALLOWANCES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SENDER_DOES_NOT_OWN_NFT_SERIAL_NO;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SPENDER_ACCOUNT_REPEATED_IN_ALLOWANCES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SPENDER_ACCOUNT_SAME_AS_OWNER;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;

public class AllowanceChecks {
	private final TypedTokenStore tokenStore;
	private final TransactionalLedger<NftId, NftProperty, MerkleUniqueToken> nftsLedger;

	@Inject
	public AllowanceChecks(
			final TransactionalLedger<NftId, NftProperty, MerkleUniqueToken> nftsLedger,
			final TypedTokenStore tokenStore) {
		this.tokenStore = tokenStore;
		this.nftsLedger = nftsLedger;
	}

	public ResponseCodeEnum allowancesValidation(final TransactionBody allowanceTxn, final Account ownerAccount) {
		final var allowances = allowanceTxn.getCryptoApproveAllowance();
		final var cryptoAllowances = allowances.getCryptoAllowancesList();
		final var tokenAllowances = allowances.getTokenAllowancesList();
		final var nftAllowances = allowances.getNftAllowancesList();

		var validity = commonChecks(allowances);
		if (validity != OK) {
			return validity;
		}

		validity = validateCryptoAllowances(cryptoAllowances, ownerAccount);
		if (validity != OK) {
			return validity;
		}

		validity = validateFungibleTokenAllowances(tokenAllowances, ownerAccount);
		if (validity != OK) {
			return validity;
		}

		validity = validateNftAllowances(nftAllowances, ownerAccount);
		if (validity != OK) {
			return validity;
		}

		return OK;
	}

	ResponseCodeEnum commonChecks(final CryptoApproveAllowanceTransactionBody op) {
		if (exceedsTxnLimit(op)) {
			return MAX_ALLOWANCES_EXCEEDED;
		}
		if (emptyAllowances(op)) {
			return EMPTY_ALLOWANCES;
		}
		return OK;
	}

	ResponseCodeEnum validateCryptoAllowances(final List<CryptoAllowance> cryptoAllowancesList,
			final Account ownerAccount) {
		if (cryptoAllowancesList.isEmpty()) {
			return OK;
		}

		if (hasRepeatedSpender(cryptoAllowancesList.stream().map(a -> a.getSpender()).toList())) {
			return SPENDER_ACCOUNT_REPEATED_IN_ALLOWANCES;
		}

		for (final var allowance : cryptoAllowancesList) {
			final var spender = Id.fromGrpcAccount(allowance.getSpender());
			final var allowanceOwner = Id.fromGrpcAccount(allowance.getOwner());
			final var amount = allowance.getAmount();

			final var validity = validateAmount(amount, null);
			if (validity != OK) {
				return validity;
			}
			if (!ownerAccount.getId().equals(allowanceOwner)) {
				return PAYER_AND_OWNER_NOT_EQUAL;
			}
			if (ownerAccount.getId().equals(spender)) {
				return SPENDER_ACCOUNT_SAME_AS_OWNER;
			}
		}
		return OK;
	}

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

		for (final var allowance : tokenAllowancesList) {
			final var spenderAccountId = allowance.getSpender();
			final var owner = Id.fromGrpcAccount(allowance.getOwner());
			final var amount = allowance.getAmount();
			final var tokenId = allowance.getTokenId();
			final var token = tokenStore.loadToken(Id.fromGrpcToken(tokenId));
			final var spenderId = Id.fromGrpcAccount(spenderAccountId);

			if (!token.isFungibleCommon()) {
				return NFT_IN_FUNGIBLE_TOKEN_ALLOWANCES;
			}

			var validity = validateAmount(amount, token);
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

		for (final var allowance : nftAllowancesList) {
			final var spenderAccountId = allowance.getSpender();
			final var owner = Id.fromGrpcAccount(allowance.getOwner());
			final var tokenId = allowance.getTokenId();
			final var serialNums = allowance.getSerialNumbersList();
			final var token = tokenStore.loadToken(Id.fromGrpcToken(tokenId));
			final var spenderId = Id.fromGrpcAccount(spenderAccountId);

			if (token.isFungibleCommon()) {
				return FUNGIBLE_TOKEN_IN_NFT_ALLOWANCES;
			}

			var validity = validateTokenBasics(ownerAccount, spenderId, tokenId, owner);
			if (validity != OK) {
				return validity;
			}

			validity = validateSerialNums(serialNums, ownerAccount, token);
			if (validity != OK) {
				return validity;
			}
		}
		return OK;
	}

	ResponseCodeEnum validateTokenBasics(
			final Account ownerAccount,
			final Id spenderId,
			final TokenID tokenId,
			final Id owner) {
		if (!ownerAccount.getId().equals(owner)) {
			return PAYER_AND_OWNER_NOT_EQUAL;
		}

		if (ownerAccount.getId().equals(spenderId)) {
			return SPENDER_ACCOUNT_SAME_AS_OWNER;
		}
		if (!ownerAccount.isAssociatedWith(Id.fromGrpcToken(tokenId))) {
			return TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;
		}
		return OK;
	}

	ResponseCodeEnum validateSerialNums(final List<Long> serialNums, final Account ownerAccount, final Token token) {
		for (var serial : serialNums) {
			final var nftId = NftId.withDefaultShardRealm(token.getId().num(), serial);
			if (serial <= 0 || !nftsLedger.exists(nftId)) {
				return INVALID_TOKEN_NFT_SERIAL_NUMBER;
			}

			final var owner = (EntityId) nftsLedger.get(nftId, OWNER);
			if (!ownerAccount.getId().asEntityId().equals(owner)) {
				return SENDER_DOES_NOT_OWN_NFT_SERIAL_NO;
			}

			if (hasRepeatedSerials(serialNums)) {
				return REPEATED_SERIAL_NUMS_IN_NFT_ALLOWANCES;
			}
		}
		return OK;
	}

	private ResponseCodeEnum validateAmount(final long amount, Token fungibleToken) {
		if (amount < 0) {
			return NEGATIVE_ALLOWANCE_AMOUNT;
		}

		if (fungibleToken != null && amount > fungibleToken.getMaxSupply()) {
			return AMOUNT_EXCEEDS_TOKEN_MAX_SUPPLY;
		}
		return OK;
	}

	/**
	 * Checks if the total allowances in the transaction exceeds the allowed limit
	 *
	 * @param op
	 * @return
	 */
	private boolean exceedsTxnLimit(final CryptoApproveAllowanceTransactionBody op) {
		final var totalAllowances =
				op.getCryptoAllowancesCount() + op.getTokenAllowancesCount() + op.getNftAllowancesCount();
		return totalAllowances > ALLOWANCE_LIMIT_PER_TRANSACTION;
	}

	/**
	 * Checks if the allowance lists are empty in the transaction
	 *
	 * @param op
	 * @return
	 */
	private boolean emptyAllowances(final CryptoApproveAllowanceTransactionBody op) {
		final var totalAllowances =
				op.getCryptoAllowancesCount() + op.getTokenAllowancesCount() + op.getNftAllowancesCount();
		return totalAllowances == 0;
	}
}
