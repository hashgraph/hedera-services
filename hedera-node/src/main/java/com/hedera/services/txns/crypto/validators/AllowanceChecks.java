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
import com.hedera.services.ledger.properties.TokenRelProperty;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.store.TypedTokenStore;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoAllowance;
import com.hederahashgraph.api.proto.java.NftAllowance;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenAllowance;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.apache.commons.lang3.tuple.Pair;

import javax.inject.Inject;
import java.util.List;

import static com.hedera.services.ledger.backing.BackingTokenRels.asTokenRel;
import static com.hedera.services.ledger.properties.TokenRelProperty.IS_FROZEN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_FROZEN_FOR_TOKEN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.AMOUNT_EXCEEDS_TOKEN_MAX_SUPPLY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CANNOT_APPROVE_FOR_ALL_FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NEGATIVE_ALLOWANCE_AMOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SPENDER_ACCOUNT_REPEATED_IN_ALLOWANCES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SPENDER_ACCOUNT_SAME_AS_OWNER;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;

public class AllowanceChecks {
	private final TransactionalLedger<Pair<AccountID, TokenID>, TokenRelProperty, MerkleTokenRelStatus> tokenRelsLedger;
	private final TypedTokenStore tokenStore;

	@Inject
	public AllowanceChecks(
			final TransactionalLedger<Pair<AccountID, TokenID>, TokenRelProperty, MerkleTokenRelStatus> tokenRelsLedger,
			final TypedTokenStore tokenStore) {
		this.tokenRelsLedger = tokenRelsLedger;
		this.tokenStore = tokenStore;
	}

	public ResponseCodeEnum allowancesValidation(final TransactionBody allowanceTxn, final Account ownerAccount) {
		final var allowances = allowanceTxn.getCryptoApproveAllowance();
		final var cryptoAllowances = allowances.getCryptoAllowancesList();
		final var tokenAllowances = allowances.getTokenAllowancesList();
		final var nftAllowances = allowances.getNftAllowancesList();

		var validity = validateCryptoAllowances(cryptoAllowances, ownerAccount);
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
			final var amount = allowance.getAmount();

			if (amount < 0) {
				return NEGATIVE_ALLOWANCE_AMOUNT;
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

		if (hasRepeatedSpender(tokenAllowancesList.stream().map(a -> a.getSpender()).toList())) {
			return SPENDER_ACCOUNT_REPEATED_IN_ALLOWANCES;
		}

		for (final var allowance : tokenAllowancesList) {
			final var spenderAccountId = allowance.getSpender();
			final var amount = allowance.getAmount();
			final var tokenId = allowance.getTokenId();
			final var token = tokenStore.loadToken(Id.fromGrpcToken(tokenId));
			if (amount < 0) {
				return NEGATIVE_ALLOWANCE_AMOUNT;
			}

			if (amount > token.getMaxSupply()) {
				return AMOUNT_EXCEEDS_TOKEN_MAX_SUPPLY;
			}

			final var validity = validateBasicTokenAllowances(ownerAccount, spenderAccountId, tokenId);
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

		if (hasRepeatedSpender(nftAllowancesList.stream().map(a -> a.getSpender()).toList())) {
			return SPENDER_ACCOUNT_REPEATED_IN_ALLOWANCES;
		}

		for (final var allowance : nftAllowancesList) {
			final var spenderAccountId = allowance.getSpender();
			final var approvedForAll = allowance.getApprovedForAll();
			final var tokenId = allowance.getTokenId();
			final var token = tokenStore.loadToken(Id.fromGrpcToken(tokenId));

			if (approvedForAll.getValue() & token.isFungibleCommon()) {
				return CANNOT_APPROVE_FOR_ALL_FUNGIBLE_COMMON;
			}

			final var validity = validateBasicTokenAllowances(ownerAccount, spenderAccountId, tokenId);
			if (validity != OK) {
				return validity;
			}
		}
		return OK;
	}

	private ResponseCodeEnum validateBasicTokenAllowances(
			final Account ownerAccount,
			final AccountID spenderAccountId,
			final TokenID tokenId) {
		final var spenderId = Id.fromGrpcAccount(spenderAccountId);
		if (ownerAccount.getId().equals(spenderId)) {
			return SPENDER_ACCOUNT_SAME_AS_OWNER;
		}
		if (!ownerAccount.isAssociatedWith(Id.fromGrpcToken(tokenId))) {
			return TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;
		}
		if (frozenAccounts(ownerAccount, spenderAccountId, tokenId)) {
			return ACCOUNT_FROZEN_FOR_TOKEN;
		}
		return OK;
	}

	boolean frozenAccounts(final Account ownerAccount, final AccountID spender,
			final TokenID tokenId) {
		final var ownerRelation = asTokenRel(ownerAccount.getId().asGrpcAccount(),
				tokenId);
		final var spenderRelation = asTokenRel(spender, tokenId);
		return ((boolean) tokenRelsLedger.get(ownerRelation, IS_FROZEN)) ||
				((boolean) tokenRelsLedger.get(spenderRelation, IS_FROZEN));
	}

	boolean hasRepeatedSpender(List<AccountID> spenders) {
		final int n = spenders.size();
		if (n < 2) {
			return false;
		}
		for (var i = 0; i < n - 1; i++) {
			for (var j = i + 1; j < n; j++) {
				if (spenders.get(i).equals(spenders.get(j))) {
					return true;
				}
			}
		}
		return false;
	}
}
