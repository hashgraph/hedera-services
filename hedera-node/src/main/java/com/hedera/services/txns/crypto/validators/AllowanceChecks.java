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

import com.hedera.services.context.primitives.StateView;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.exceptions.InvalidTransactionException;
import com.hedera.services.state.submerkle.FcTokenAllowanceId;
import com.hedera.services.store.AccountStore;
import com.hedera.services.store.ReadOnlyTokenStore;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.Token;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.EntityNumPair;
import com.hederahashgraph.api.proto.java.CryptoAllowance;
import com.hederahashgraph.api.proto.java.NftAllowance;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenAllowance;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.List;

import static com.hedera.services.txns.crypto.helpers.AllowanceHelpers.aggregateNftAllowances;
import static com.hedera.services.txns.crypto.helpers.AllowanceHelpers.buildEntityNumPairFrom;
import static com.hedera.services.txns.crypto.helpers.AllowanceHelpers.buildTokenAllowanceKey;
import static com.hedera.services.txns.crypto.helpers.AllowanceHelpers.hasRepeatedId;
import static com.hedera.services.txns.crypto.helpers.AllowanceHelpers.hasRepeatedSerials;
import static com.hedera.services.txns.crypto.helpers.AllowanceHelpers.hasRepeatedSpender;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.DELEGATING_SPENDER_CANNOT_GRANT_APPROVE_FOR_ALL;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.DELEGATING_SPENDER_DOES_NOT_HAVE_APPROVE_FOR_ALL;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.EMPTY_ALLOWANCES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FUNGIBLE_TOKEN_IN_NFT_ALLOWANCES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ALLOWANCE_OWNER_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_NFT_SERIAL_NUMBER;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MAX_ALLOWANCES_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NFT_IN_FUNGIBLE_TOKEN_ALLOWANCES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.REPEATED_SERIAL_NUMS_IN_NFT_ALLOWANCES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SPENDER_ACCOUNT_REPEATED_IN_ALLOWANCES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SPENDER_ACCOUNT_SAME_AS_OWNER;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;

/**
 * Validations for {@link com.hederahashgraph.api.proto.java.CryptoApproveAllowance} and
 * {@link com.hederahashgraph.api.proto.java.CryptoAdjustAllowance} transaction allowances
 */
public abstract class AllowanceChecks {
	private final GlobalDynamicProperties dynamicProperties;
	private final OptionValidator validator;

	protected AllowanceChecks(final GlobalDynamicProperties dynamicProperties,
			final OptionValidator validator) {
		this.dynamicProperties = dynamicProperties;
		this.validator = validator;
	}

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
	 * @param payerAccount
	 * 		Account of the payer for the allowance approve/adjust txn.
	 * @param maxLimitPerTxn
	 * 		max allowance limit per transaction
	 * @param view
	 * 		working view
	 * @return response code after validation
	 */
	public ResponseCodeEnum allowancesValidation(final List<CryptoAllowance> cryptoAllowances,
			final List<TokenAllowance> tokenAllowances,
			final List<NftAllowance> nftAllowances,
			final Account payerAccount,
			final int maxLimitPerTxn,
			final StateView view) {

		// feature flag for allowances
		if (!isEnabled()) {
			return NOT_SUPPORTED;
		}

		var validity = commonChecks(cryptoAllowances, tokenAllowances, nftAllowances, maxLimitPerTxn);
		if (validity != OK) {
			return validity;
		}

		final var accountStore = new AccountStore(validator, dynamicProperties, view.asReadOnlyAccountStore());
		validity = validateCryptoAllowances(cryptoAllowances, payerAccount, accountStore);
		if (validity != OK) {
			return validity;
		}
		final var tokenStore = new ReadOnlyTokenStore(accountStore,
				view.asReadOnlyTokenStore(),
				view.asReadOnlyNftStore(),
				view.asReadOnlyAssociationStore());
		validity = validateFungibleTokenAllowances(tokenAllowances, payerAccount, tokenStore, accountStore);
		if (validity != OK) {
			return validity;
		}

		validity = validateNftAllowances(tokenStore, accountStore, nftAllowances, payerAccount);
		if (validity != OK) {
			return validity;
		}

		return OK;
	}


	/**
	 * Validates the CryptoAllowances given in {@link com.hederahashgraph.api.proto.java.CryptoApproveAllowance} or
	 * {@link com.hederahashgraph.api.proto.java.CryptoAdjustAllowance} transactions
	 *
	 * @param cryptoAllowances
	 * 		crypto allowances list
	 * @param payerAccount
	 * 		Account of the payer for the Allowance approve/adjust txn
	 * @param accountStore
	 * @return response code after validation
	 */
	ResponseCodeEnum validateCryptoAllowances(
			final List<CryptoAllowance> cryptoAllowances,
			final Account payerAccount,
			final AccountStore accountStore) {
		if (cryptoAllowances.isEmpty()) {
			return OK;
		}
		final List<EntityNumPair> entities = new ArrayList<>();
		for (var allowance : cryptoAllowances) {
			entities.add(buildEntityNumPairFrom(allowance.getOwner(), allowance.getSpender(),
					payerAccount.getId().asEntityNum()));
		}

		if (hasRepeatedSpender(entities)) {
			return SPENDER_ACCOUNT_REPEATED_IN_ALLOWANCES;
		}

		for (final var allowance : cryptoAllowances) {
			final var owner = Id.fromGrpcAccount(allowance.getOwner());
			final var spender = Id.fromGrpcAccount(allowance.getSpender());

			final var fetchResult = fetchOwnerAccount(owner, payerAccount, accountStore);
			if (fetchResult.getRight() != OK) {
				return fetchResult.getRight();
			}
			final var ownerAccount = fetchResult.getLeft();

			var validity = validateAmount(allowance.getAmount(), ownerAccount, spender);
			if (validity != OK) {
				return validity;
			}
			validity = validateSpender(ownerAccount.getId(), spender);
			if (validity != OK) {
				return validity;
			}
		}
		return OK;
	}

	/**
	 * Validate fungible token allowances list {@link com.hederahashgraph.api.proto.java.CryptoApproveAllowance} or
	 * {@link com.hederahashgraph.api.proto.java.CryptoAdjustAllowance} transactions
	 *
	 * @param tokenAllowances
	 * 		token allowances list
	 * @param payerAccount
	 * 		Account of the payer for the Allowance approve/adjust txn
	 * @param accountStore
	 * @return
	 */
	ResponseCodeEnum validateFungibleTokenAllowances(
			final List<TokenAllowance> tokenAllowances,
			final Account payerAccount,
			final ReadOnlyTokenStore tokenStore,
			final AccountStore accountStore) {
		if (tokenAllowances.isEmpty()) {
			return OK;
		}
		final List<Pair<EntityNum, FcTokenAllowanceId>> tokenKeys = new ArrayList<>();
		for (var allowance : tokenAllowances) {
			tokenKeys.add(
					buildTokenAllowanceKey(allowance.getOwner(), allowance.getTokenId(), allowance.getSpender()));
		}
		if (hasRepeatedId(tokenKeys)) {
			return SPENDER_ACCOUNT_REPEATED_IN_ALLOWANCES;
		}

		for (final var allowance : tokenAllowances) {
			final var owner = Id.fromGrpcAccount(allowance.getOwner());
			final var spender = Id.fromGrpcAccount(allowance.getSpender());
			final var tokenId = allowance.getTokenId();
			final var token = tokenStore.loadPossiblyPausedToken(Id.fromGrpcToken(tokenId));

			final var fetchResult = fetchOwnerAccount(owner, payerAccount, accountStore);
			if (fetchResult.getRight() != OK) {
				return fetchResult.getRight();
			}
			final var ownerAccount = fetchResult.getLeft();
			if (!token.isFungibleCommon()) {
				return NFT_IN_FUNGIBLE_TOKEN_ALLOWANCES;
			}

			var validity = validateTokenAmount(ownerAccount, allowance.getAmount(), token, spender);
			if (validity != OK) {
				return validity;
			}

			validity = validateTokenBasics(ownerAccount, spender, token, tokenStore);
			if (validity != OK) {
				return validity;
			}
		}
		return OK;
	}

	/**
	 * Validate nft allowances list {@link com.hederahashgraph.api.proto.java.CryptoApproveAllowance} or
	 * {@link com.hederahashgraph.api.proto.java.CryptoAdjustAllowance} transactions
	 *
	 * @param tokenStore
	 * @param accountStore
	 * @param nftAllowancesList
	 * @param payerAccount
	 * @return
	 */
	ResponseCodeEnum validateNftAllowances(
			final ReadOnlyTokenStore tokenStore,
			final AccountStore accountStore,
			final List<NftAllowance> nftAllowancesList,
			final Account payerAccount) {
		if (nftAllowancesList.isEmpty()) {
			return OK;
		}

		final List<Pair<EntityNum, FcTokenAllowanceId>> nftKeys = new ArrayList<>();
		for (var allowance : nftAllowancesList) {
			nftKeys.add(buildTokenAllowanceKey(allowance.getOwner(), allowance.getTokenId(), allowance.getSpender()));
		}
		if (hasRepeatedId(nftKeys)) {
			return SPENDER_ACCOUNT_REPEATED_IN_ALLOWANCES;
		}

		for (final var allowance : nftAllowancesList) {
			final var owner = Id.fromGrpcAccount(allowance.getOwner());
			final var spender = Id.fromGrpcAccount(allowance.getSpender());
			final var delegatingSpender = Id.fromGrpcAccount(allowance.getDelegatingSpender());
			final var tokenId = allowance.getTokenId();
			final var serialNums = allowance.getSerialNumbersList();
			final var token = tokenStore.loadPossiblyPausedToken(Id.fromGrpcToken(tokenId));
			final var approvedForAll = allowance.getApprovedForAll().getValue();

			if (token.isFungibleCommon()) {
				return FUNGIBLE_TOKEN_IN_NFT_ALLOWANCES;
			}

			final var fetchResult = fetchOwnerAccount(owner, payerAccount, accountStore);
			if (fetchResult.getRight() != OK) {
				return fetchResult.getRight();
			}
			final var ownerAccount = fetchResult.getLeft();

			var validity = validateTokenBasics(ownerAccount, spender, token, tokenStore);
			if (validity != OK) {
				return validity;
			}

			if (approvedForAll && !delegatingSpender.equals(Id.MISSING_ID)) {
				return DELEGATING_SPENDER_CANNOT_GRANT_APPROVE_FOR_ALL;
			} else if (!delegatingSpender.equals(Id.MISSING_ID)) {
				final var allowanceKey = FcTokenAllowanceId.from(
						EntityNum.fromTokenId(tokenId), delegatingSpender.asEntityNum());
				if (!ownerAccount.getApprovedForAllNftsAllowances().contains(allowanceKey)) {
					return DELEGATING_SPENDER_DOES_NOT_HAVE_APPROVE_FOR_ALL;
				}
			}

			validity = validateSerialNums(serialNums, token, tokenStore);
			if (validity != OK) {
				return validity;
			}
		}
		return OK;
	}

	/**
	 * Check if the allowance feature is enabled
	 *
	 * @return true if the feature is enabled in {@link com.hedera.services.context.properties.GlobalDynamicProperties}
	 */
	public boolean isEnabled() {
		return dynamicProperties.areAllowancesEnabled();
	}

	private ResponseCodeEnum validateSpender(final Id ownerId, final Id spender) {
		if (ownerId.equals(spender)) {
			return SPENDER_ACCOUNT_SAME_AS_OWNER;
		}
		return OK;
	}

	private ResponseCodeEnum validateTokenBasics(
			final Account ownerAccount,
			final Id spenderId,
			final Token token,
			final ReadOnlyTokenStore tokenStore) {
		if (ownerAccount.getId().equals(spenderId)) {
			return SPENDER_ACCOUNT_SAME_AS_OWNER;
		}
		if (!tokenStore.hasAssociation(token, ownerAccount)) {
			return TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;
		}
		return OK;
	}

	public ResponseCodeEnum commonChecks(final List<CryptoAllowance> cryptoAllowances,
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

	/**
	 * Validates serial numbers for {@link NftAllowance}
	 *
	 * @param serialNums
	 * 		given serial numbers in the {@link com.hederahashgraph.api.proto.java.CryptoApproveAllowance} operation
	 * @param token
	 * 		token for which allowance is related to
	 * @return response code after validation
	 */
	ResponseCodeEnum validateSerialNums(
			final List<Long> serialNums,
			final Token token,
			final ReadOnlyTokenStore tokenStore) {
		if (hasRepeatedSerials(serialNums)) {
			return REPEATED_SERIAL_NUMS_IN_NFT_ALLOWANCES;
		}

		for (var serial : serialNums) {
			if (serial <= 0) {
				return INVALID_TOKEN_NFT_SERIAL_NUMBER;
			}

			try {
				tokenStore.loadUniqueToken(token.getId(), serial);
			} catch (InvalidTransactionException ex) {
				return INVALID_TOKEN_NFT_SERIAL_NUMBER;
			}
		}

		return OK;
	}

	private boolean exceedsTxnLimit(final int totalAllowances, final int maxLimit) {
		return totalAllowances > maxLimit;
	}

	private boolean emptyAllowances(final int totalAllowances) {
		return totalAllowances == 0;
	}

	private Pair<Account, ResponseCodeEnum> fetchOwnerAccount(
			final Id owner,
			final Account payerAccount,
			final AccountStore accountStore) {
		if (owner.equals(Id.MISSING_ID) || owner.equals(payerAccount.getId())) {
			return Pair.of(payerAccount, OK);
		} else {
			try {
				return Pair.of(accountStore.loadAccount(owner), OK);
			} catch (InvalidTransactionException ex) {
				return Pair.of(payerAccount, INVALID_ALLOWANCE_OWNER_ID);
			}
		}
	}

	public abstract ResponseCodeEnum validateAmount(final long amount, final Account owner, final Id spender);

	public abstract ResponseCodeEnum validateTokenAmount(final Account ownerAccount, final long amount,
			final Token token,
			final Id spender);
}
