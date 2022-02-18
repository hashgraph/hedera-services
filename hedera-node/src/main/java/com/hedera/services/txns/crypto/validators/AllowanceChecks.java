package com.hedera.services.txns.crypto.validators;

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

import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hederahashgraph.api.proto.java.CryptoAllowance;
import com.hederahashgraph.api.proto.java.NftAllowance;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenAllowance;
import com.hederahashgraph.api.proto.java.TokenID;

import java.util.List;

import static com.hedera.services.txns.crypto.helpers.AllowanceHelpers.aggregateNftAllowances;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.EMPTY_ALLOWANCES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MAX_ALLOWANCES_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SPENDER_ACCOUNT_SAME_AS_OWNER;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;

/**
 * Validations for {@link com.hederahashgraph.api.proto.java.CryptoApproveAllowance} and
 * {@link com.hederahashgraph.api.proto.java.CryptoAdjustAllowance} transaction allowances
 */
public interface AllowanceChecks {
	/**
	 * Validates the CryptoAllowances given in {@link com.hederahashgraph.api.proto.java.CryptoApproveAllowance} or
	 * {@link com.hederahashgraph.api.proto.java.CryptoAdjustAllowance} transactions
	 *
	 * @param cryptoAllowancesList
	 * 		crypto allowances list
	 * @param ownerAccount
	 * 		owner account
	 * @return response code after validation
	 */
	ResponseCodeEnum validateCryptoAllowances(final List<CryptoAllowance> cryptoAllowancesList,
			final Account ownerAccount);

	/**
	 * Validate fungible token allowances list {@link com.hederahashgraph.api.proto.java.CryptoApproveAllowance} or
	 * {@link com.hederahashgraph.api.proto.java.CryptoAdjustAllowance} transactions
	 *
	 * @param tokenAllowancesList
	 * 		token allowances list
	 * @param ownerAccount
	 * 		owner account
	 * @return
	 */
	ResponseCodeEnum validateFungibleTokenAllowances(final List<TokenAllowance> tokenAllowancesList,
			final Account ownerAccount);

	/**
	 * Validate nft allowances list {@link com.hederahashgraph.api.proto.java.CryptoApproveAllowance} or
	 * {@link com.hederahashgraph.api.proto.java.CryptoAdjustAllowance} transactions
	 *
	 * @param nftAllowancesList
	 * 		nft allowances list
	 * @param ownerAccount
	 * 		owner account
	 * @return
	 */
	ResponseCodeEnum validateNftAllowances(final List<NftAllowance> nftAllowancesList,
			final Account ownerAccount);

	/**
	 * Validate all allowances in {@link com.hederahashgraph.api.proto.java.CryptoApproveAllowance} or
	 * {@link com.hederahashgraph.api.proto.java.CryptoAdjustAllowance} transactions
	 *
	 * @param cryptoAllowances
	 * 		crypto allowances list
	 * @param tokenAllowances
	 * 		fungible token allowances list
	 * @param nftAllowances
	 * 		nft allowances list
	 * @param ownerAccount
	 * 		owner account
	 * @param maxLimitPerTxn
	 * 		max allowance limit per transaction
	 * @return response code after validation
	 */
	default ResponseCodeEnum allowancesValidation(final List<CryptoAllowance> cryptoAllowances,
			final List<TokenAllowance> tokenAllowances,
			final List<NftAllowance> nftAllowances,
			final Account ownerAccount,
			final int maxLimitPerTxn) {
		var validity = commonChecks(cryptoAllowances, tokenAllowances, nftAllowances, maxLimitPerTxn);
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

	default ResponseCodeEnum validateCryptoAllowanceBasics(final Id ownerId, final Id spender) {
		if (ownerId.equals(spender)) {
			return SPENDER_ACCOUNT_SAME_AS_OWNER;
		}
		return OK;
	}

	default ResponseCodeEnum validateTokenBasics(
			final Account ownerAccount,
			final Id spenderId,
			final TokenID tokenId) {
		if (ownerAccount.getId().equals(spenderId)) {
			return SPENDER_ACCOUNT_SAME_AS_OWNER;
		}
		if (!ownerAccount.isAssociatedWith(Id.fromGrpcToken(tokenId))) {
			return TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;
		}
		return OK;
	}

	default ResponseCodeEnum commonChecks(final List<CryptoAllowance> cryptoAllowances,
			final List<TokenAllowance> tokenAllowances,
			final List<NftAllowance> nftAllowances,
			final int maxLimitPerTxn) {
		// each serial number of an NFT is considered as an allowance.
		// So for Nft allowances aggregated amount is considered for limit calculation.
		final var totalAllowances = cryptoAllowances.size() + tokenAllowances.size()
				+ aggregateNftAllowances(nftAllowances);

		if (exceedsTxnLimit(totalAllowances, maxLimitPerTxn)) {
			return MAX_ALLOWANCES_EXCEEDED;
		}
		if (emptyAllowances(totalAllowances)) {
			return EMPTY_ALLOWANCES;
		}
		return OK;
	}

	default boolean exceedsTxnLimit(final int totalAllowances, final int maxLimit) {
		return totalAllowances > maxLimit;
	}

	default boolean emptyAllowances(final int totalAllowances) {
		return totalAllowances == 0;
	}
}
