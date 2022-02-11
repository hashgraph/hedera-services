package com.hedera.services.ledger;

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

import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.submerkle.FcTokenAllowance;
import com.hedera.services.state.submerkle.FcTokenAllowanceId;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.function.Function;

import static com.hedera.services.ledger.properties.AccountProperty.BALANCE;
import static com.hedera.services.ledger.properties.AccountProperty.CRYPTO_ALLOWANCES;
import static com.hedera.services.ledger.properties.AccountProperty.EXPIRY;
import static com.hedera.services.ledger.properties.AccountProperty.FUNGIBLE_TOKEN_ALLOWANCES;
import static com.hedera.services.ledger.properties.AccountProperty.IS_DELETED;
import static com.hedera.services.ledger.properties.AccountProperty.IS_SMART_CONTRACT;
import static com.hedera.services.ledger.properties.AccountProperty.NFT_ALLOWANCES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_EXPIRED_AND_PENDING_REMOVAL;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.AMOUNT_EXCEEDS_ALLOWANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SPENDER_DOES_NOT_HAVE_ALLOWANCE;

public class MerkleAccountScopedCheck implements LedgerCheck<MerkleAccount, AccountProperty> {
	private final GlobalDynamicProperties dynamicProperties;
	private final OptionValidator validator;

	private BalanceChange balanceChange;

	public MerkleAccountScopedCheck(final GlobalDynamicProperties dynamicProperties, final OptionValidator validator) {
		this.dynamicProperties = dynamicProperties;
		this.validator = validator;
	}

	@Override
	public ResponseCodeEnum checkUsing(
			final Function<AccountProperty, Object> extantProps,
			final Map<AccountProperty, Object> changeSet
	) {
		return internalCheck(null, extantProps, changeSet);
	}

	@Override
	public ResponseCodeEnum checkUsing(final MerkleAccount account, final Map<AccountProperty, Object> changeSet) {
		return internalCheck(account, null, changeSet);
	}

	public MerkleAccountScopedCheck setBalanceChange(final BalanceChange balanceChange) {
		this.balanceChange = balanceChange;
		return this;
	}

	private ResponseCodeEnum internalCheck(
			@Nullable final MerkleAccount account,
			@Nullable final Function<AccountProperty, Object> extantProps,
			final Map<AccountProperty, Object> changeSet
	) {
		if (balanceChange.isForHbar()) {
			return hbarCheck(account, extantProps, changeSet);
		} else if (balanceChange.isForFungibleToken()) {
			return validateFungibleTokenAllowance(account, extantProps, changeSet);
		} else {
			return validateNftAllowance(account, extantProps, changeSet);
		}
	}

	Object getEffective(
			final AccountProperty prop,
			@Nullable final MerkleAccount account,
			@Nullable final Function<AccountProperty, Object> extantProps,
			final Map<AccountProperty, Object> changeSet
	) {
		if (changeSet != null && changeSet.containsKey(prop)) {
			return changeSet.get(prop);
		}
		final var useExtantProps = extantProps != null;
		if (!useExtantProps) {
			assert account != null;
		}
		switch (prop) {
			case IS_SMART_CONTRACT:
				return useExtantProps ? extantProps.apply(IS_SMART_CONTRACT) : account.isSmartContract();
			case IS_DELETED:
				return useExtantProps ? extantProps.apply(IS_DELETED) : account.isDeleted();
			case BALANCE:
				return useExtantProps ? extantProps.apply(BALANCE) : account.getBalance();
			case EXPIRY:
				return useExtantProps ? extantProps.apply(EXPIRY) : account.getExpiry();
			case CRYPTO_ALLOWANCES:
				return useExtantProps ? extantProps.apply(CRYPTO_ALLOWANCES) : account.getCryptoAllowances();
			case FUNGIBLE_TOKEN_ALLOWANCES:
				return useExtantProps ? extantProps.apply(FUNGIBLE_TOKEN_ALLOWANCES) : account.getFungibleTokenAllowances();
			case NFT_ALLOWANCES:
				return useExtantProps ? extantProps.apply(NFT_ALLOWANCES) : account.getNftAllowances();
			default:
				throw new IllegalArgumentException("Invalid Property " + prop + " cannot be validated in scoped check");
		}
	}

	private ResponseCodeEnum hbarCheck(
			@Nullable final MerkleAccount account,
			@Nullable final Function<AccountProperty, Object> extantProps,
			final Map<AccountProperty, Object> changeSet) {
		if ((boolean) getEffective(IS_DELETED, account, extantProps, changeSet)) {
			return ResponseCodeEnum.ACCOUNT_DELETED;
		}

		final var balance = (long) getEffective(BALANCE, account, extantProps, changeSet);
		final var isDetached = dynamicProperties.autoRenewEnabled() &&
				balance == 0L &&
				!validator.isAfterConsensusSecond((long) getEffective(EXPIRY, account, extantProps, changeSet));
		if (isDetached) {
			return ACCOUNT_EXPIRED_AND_PENDING_REMOVAL;
		}

		final var validity = validateHbarAllowance(account, extantProps, changeSet);
		if (validity != OK) {
			return validity;
		}

		final var newBalance = balance + balanceChange.getAggregatedUnits();
		if (newBalance < 0L) {
			return balanceChange.codeForInsufficientBalance();
		}
		balanceChange.setNewBalance(newBalance);

		return OK;
	}

	public ResponseCodeEnum validateNftAllowance(
			@Nullable final MerkleAccount account,
			@Nullable final Function<AccountProperty, Object> extantProps,
			final Map<AccountProperty, Object> changeSet) {
		if (balanceChange.isApprovedAllowance()) {
			final var nftAllowances = (Map<FcTokenAllowanceId, FcTokenAllowance>) getEffective(
					NFT_ALLOWANCES, account, extantProps, changeSet);
			final var nftAllowance = nftAllowances.getOrDefault(
					FcTokenAllowanceId.from(
							balanceChange.getToken().asEntityNum(), EntityNum.fromAccountId(balanceChange.getPayerID())),
					null
			);

			if (nftAllowance == null) {
				return SPENDER_DOES_NOT_HAVE_ALLOWANCE;
			} else {
				final var allowAll = nftAllowance.isApprovedForAll();
				final var allowedSerialNums = nftAllowance.getSerialNumbers();

				if (allowAll) {
					return OK;
				} else {
					if (!allowedSerialNums.contains(balanceChange.serialNo())) {
						return SPENDER_DOES_NOT_HAVE_ALLOWANCE;
					}
				}
			}
		}
		return OK;
	}

	private ResponseCodeEnum validateHbarAllowance(
			@Nullable final MerkleAccount account,
			@Nullable final Function<AccountProperty, Object> extantProps,
			final Map<AccountProperty, Object> changeSet) {
		if (balanceChange.isApprovedAllowance()) {
			final var cryptoAllowances = (Map<EntityNum, Long>) getEffective(
					CRYPTO_ALLOWANCES, account, extantProps, changeSet);
			final var allowance = cryptoAllowances.getOrDefault(
					EntityNum.fromAccountId(balanceChange.getPayerID()), 0L);
			if (allowance == 0L) {
				return SPENDER_DOES_NOT_HAVE_ALLOWANCE;
			}
			final var newAllowance = allowance + balanceChange.getAllowanceUnits();
			if (newAllowance < 0L) {
				return AMOUNT_EXCEEDS_ALLOWANCE;
			}
		}
		return OK;
	}

	public ResponseCodeEnum validateFungibleTokenAllowance(
			@Nullable final MerkleAccount account,
			@Nullable final Function<AccountProperty, Object> extantProps,
			final Map<AccountProperty, Object> changeSet) {
		if (balanceChange.isApprovedAllowance()) {
			final var fungibleAllowances = (Map<FcTokenAllowanceId, Long>) getEffective(
					FUNGIBLE_TOKEN_ALLOWANCES, account, extantProps, changeSet);
			final var allowance = fungibleAllowances.getOrDefault(
					FcTokenAllowanceId.from(
							balanceChange.getToken().asEntityNum(), EntityNum.fromAccountId(balanceChange.getPayerID())),
					0L);
			if (allowance == 0L) {
				return SPENDER_DOES_NOT_HAVE_ALLOWANCE;
			}
			final var newAllowance = allowance + balanceChange.getAllowanceUnits();
			if (newAllowance < 0L) {
				return AMOUNT_EXCEEDS_ALLOWANCE;
			}
		}
		return OK;
	}
}
