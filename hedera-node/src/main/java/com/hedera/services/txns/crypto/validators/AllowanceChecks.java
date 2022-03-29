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
import com.hedera.services.state.enums.TokenType;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.MerkleUniqueToken;
import com.hedera.services.state.submerkle.FcTokenAllowanceId;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.CryptoAllowance;
import com.hederahashgraph.api.proto.java.NftAllowance;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenAllowance;
import com.hederahashgraph.api.proto.java.TokenID;
import org.apache.commons.lang3.tuple.Pair;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.hedera.services.state.submerkle.EntityId.MISSING_ENTITY_ID;
import static com.hedera.services.store.AccountStore.loadMerkleAccount;
import static com.hedera.services.txns.crypto.helpers.AllowanceHelpers.aggregateNftAllowances;
import static com.hedera.services.txns.crypto.helpers.AllowanceHelpers.buildEntityNumPairFrom;
import static com.hedera.services.txns.crypto.helpers.AllowanceHelpers.buildTokenAllowanceKey;
import static com.hedera.services.txns.crypto.helpers.AllowanceHelpers.hasRepeatedId;
import static com.hedera.services.txns.crypto.helpers.AllowanceHelpers.hasRepeatedSpender;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.EMPTY_ALLOWANCES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FUNGIBLE_TOKEN_IN_NFT_ALLOWANCES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ALLOWANCE_OWNER_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MAX_ALLOWANCES_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NFT_IN_FUNGIBLE_TOKEN_ALLOWANCES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SPENDER_ACCOUNT_REPEATED_IN_ALLOWANCES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SPENDER_ACCOUNT_SAME_AS_OWNER;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;

/**
 * Validations for {@link com.hederahashgraph.api.proto.java.CryptoApproveAllowance} and
 * {@link com.hederahashgraph.api.proto.java.CryptoAdjustAllowance} transaction allowances
 */
@Singleton
public class AllowanceChecks {
	private final GlobalDynamicProperties dynamicProperties;
	private final OptionValidator validator;

	private static final String UNSUPPORTED_MSG = "Base Class, Implementation present in AdjustAllowanceChecks/ApproveAllowanceChecks";

	@Inject
	public AllowanceChecks(final GlobalDynamicProperties dynamicProperties,
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
	 * @param workingView
	 * @return response code after validation
	 */
	public ResponseCodeEnum allowancesValidation(final List<CryptoAllowance> cryptoAllowances,
			final List<TokenAllowance> tokenAllowances,
			final List<NftAllowance> nftAllowances,
			final Account payerAccount,
			final int maxLimitPerTxn,
			final StateView workingView) {

		// feature flag for allowances
		if (!isEnabled()) {
			return NOT_SUPPORTED;
		}
		var validity = commonChecks(cryptoAllowances, tokenAllowances, nftAllowances, maxLimitPerTxn);
		if (validity != OK) {
			return validity;
		}

		validity = validateCryptoAllowances(cryptoAllowances, payerAccount, workingView);
		if (validity != OK) {
			return validity;
		}

		validity = validateFungibleTokenAllowances(tokenAllowances, payerAccount, workingView);
		if (validity != OK) {
			return validity;
		}

		validity = validateNftAllowances(nftAllowances, payerAccount, workingView);
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
	 * @param view
	 * @return response code after validation
	 */
	ResponseCodeEnum validateCryptoAllowances(
			final List<CryptoAllowance> cryptoAllowances,
			final Account payerAccount,
			final StateView view) {
		if (cryptoAllowances.isEmpty()) {
			return OK;
		}
		final var entities = cryptoAllowances.stream()
				.map(allowance -> buildEntityNumPairFrom(allowance.getOwner(), allowance.getSpender(),
						payerAccount.getId().asEntityNum()))
				.toList();
		if (hasRepeatedSpender(entities)) {
			return SPENDER_ACCOUNT_REPEATED_IN_ALLOWANCES;
		}

		for (final var allowance : cryptoAllowances) {
			final var owner = Id.fromGrpcAccount(allowance.getOwner());
			final var spender = Id.fromGrpcAccount(allowance.getSpender());

			final var fetchResult = fetchOwnerAccount(owner, payerAccount, view);
			if (fetchResult.isEmpty()) {
				return INVALID_ALLOWANCE_OWNER_ID;
			}

			final var ownerAccount = fetchResult.get();
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
	 * @param tokenAllowancesList
	 * 		token allowances list
	 * @param payerAccount
	 * 		Account of the payer for the Allowance approve/adjust txn
	 * @param view
	 * @return
	 */
	ResponseCodeEnum validateFungibleTokenAllowances(final List<TokenAllowance> tokenAllowancesList,
			final Account payerAccount, final StateView view) {
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
			final var tokenId = allowance.getTokenId();
			final var merkleToken = view.loadToken(tokenId);
			final var token = Id.fromGrpcToken(tokenId);

			final var fetchResult = fetchOwnerAccount(owner, payerAccount, view);
			if (fetchResult.isEmpty()) {
				return INVALID_ALLOWANCE_OWNER_ID;
			}

			final var ownerAccount = fetchResult.get();
			if (!isFungibleCommon(merkleToken)) {
				return NFT_IN_FUNGIBLE_TOKEN_ALLOWANCES;
			}

			var validity = validateTokenAmount(ownerAccount, allowance.getAmount(), merkleToken, token, spender);
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

	/**
	 * Validate nft allowances list {@link com.hederahashgraph.api.proto.java.CryptoApproveAllowance} or
	 * {@link com.hederahashgraph.api.proto.java.CryptoAdjustAllowance} transactions
	 *
	 * @param nftAllowances
	 * 		nft allowances list
	 * @param payerAccount
	 * 		Account of the payer for the Allowance approve/adjust txn
	 * @param view
	 * @return
	 */
	ResponseCodeEnum validateNftAllowances(
			final List<NftAllowance> nftAllowances,
			final Account payerAccount,
			final StateView view) {
		if (nftAllowances.isEmpty()) {
			return OK;
		}

		final List<Pair<EntityNum, FcTokenAllowanceId>> nftKeys = new ArrayList<>();
		for (var allowance : nftAllowances) {
			nftKeys.add(buildTokenAllowanceKey(allowance.getOwner(), allowance.getTokenId(), allowance.getSpender()));
		}
		if (hasRepeatedId(nftKeys)) {
			return SPENDER_ACCOUNT_REPEATED_IN_ALLOWANCES;
		}

		for (final var allowance : nftAllowances) {
			final var spenderAccountId = allowance.getSpender();
			final var tokenId = allowance.getTokenId();
			final var serialNums = allowance.getSerialNumbersList();
			final var merkleToken = view.loadToken(tokenId);
			final var spenderId = Id.fromGrpcAccount(spenderAccountId);
			final var approvedForAll = allowance.getApprovedForAll().getValue();
			var owner = Id.fromGrpcAccount(allowance.getOwner());

			final var fetchResult = fetchOwnerAccount(owner, payerAccount, view);
			if (fetchResult.isEmpty()) {
				return INVALID_ALLOWANCE_OWNER_ID;
			}
			final var ownerAccount = fetchResult.get();

			if (isFungibleCommon(merkleToken)) {
				return FUNGIBLE_TOKEN_IN_NFT_ALLOWANCES;
			}
			var validity = validateTokenBasics(ownerAccount, spenderId, tokenId);
			if (validity != OK) {
				return validity;
			}

			if (!approvedForAll) {
				// if approvedForAll is true no need to validate all serial numbers, since they will not be stored in
				// state
				validity = validateSerialNums(serialNums, ownerAccount, merkleToken, view, Id.fromGrpcToken(tokenId),
						spenderId);
				if (validity != OK) {
					return validity;
				}
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
			final TokenID tokenId) {
		if (ownerAccount.getId().equals(spenderId)) {
			return SPENDER_ACCOUNT_SAME_AS_OWNER;
		}
		if (!ownerAccount.isAssociatedWith(Id.fromGrpcToken(tokenId))) {
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

	private boolean exceedsTxnLimit(final int totalAllowances, final int maxLimit) {
		return totalAllowances > maxLimit;
	}

	private boolean emptyAllowances(final int totalAllowances) {
		return totalAllowances == 0;
	}

	private Optional<Account> fetchOwnerAccount(
			final Id owner,
			final Account payerAccount,
			final StateView view) {
		if (owner.equals(Id.MISSING_ID) || owner.equals(payerAccount.getId())) {
			return Optional.of(payerAccount);
		} else {
			final var account = view.loadAccount(owner.asGrpcAccount());
			if (isInvalidOwner(account)) {
				return Optional.empty();
			} else {
				return Optional.of(loadMerkleAccount(account, owner));
			}
		}
	}

	boolean isInvalidOwner(final MerkleAccount account) {
		return account == null || account.isDeleted() || isExpired(account.getBalance(), account.getExpiry());
	}

	boolean isExpired(long balance, long expiry) {
		if (dynamicProperties.autoRenewEnabled() && balance == 0) {
			return !validator.isAfterConsensusSecond(expiry);
		} else {
			return false;
		}
	}

	boolean validOwner(final MerkleUniqueToken nft, final Account ownerAccount, final MerkleToken token) {
		final var listedOwner = nft.getOwner();
		return MISSING_ENTITY_ID.equals(listedOwner)
				? ownerAccount.getId().equals(token.treasury().asId())
				: listedOwner.equals(ownerAccount.getId().asEntityId());
	}

	private boolean isFungibleCommon(MerkleToken token) {
		return token.tokenType() == TokenType.FUNGIBLE_COMMON;
	}

	ResponseCodeEnum validateSerialNums(final List<Long> serialNums,
			final Account ownerAccount,
			final MerkleToken merkleToken,
			final StateView view,
			final Id token,
			final Id spender) {
		throw new UnsupportedOperationException(UNSUPPORTED_MSG);
	}

	ResponseCodeEnum validateAmount(final long amount, final Account owner, final Id spender) {
		throw new UnsupportedOperationException(UNSUPPORTED_MSG);
	}

	ResponseCodeEnum validateTokenAmount(final Account ownerAccount,
			final long l, final MerkleToken merkleToken, final Id token, final Id spender) {
		throw new UnsupportedOperationException(UNSUPPORTED_MSG);
	}
}
