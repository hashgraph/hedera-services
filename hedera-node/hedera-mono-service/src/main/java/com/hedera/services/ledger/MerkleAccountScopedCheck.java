/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
 *
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
 */
package com.hedera.services.ledger;

import static com.hedera.services.ledger.properties.AccountProperty.APPROVE_FOR_ALL_NFTS_ALLOWANCES;
import static com.hedera.services.ledger.properties.AccountProperty.BALANCE;
import static com.hedera.services.ledger.properties.AccountProperty.CRYPTO_ALLOWANCES;
import static com.hedera.services.ledger.properties.AccountProperty.EXPIRY;
import static com.hedera.services.ledger.properties.AccountProperty.FUNGIBLE_TOKEN_ALLOWANCES;
import static com.hedera.services.ledger.properties.AccountProperty.IS_DELETED;
import static com.hedera.services.ledger.properties.AccountProperty.IS_SMART_CONTRACT;
import static com.hedera.services.ledger.properties.NftProperty.SPENDER;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.*;

import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.ledger.properties.NftProperty;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.migration.UniqueTokenAdapter;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.FcTokenAllowanceId;
import com.hedera.services.store.models.NftId;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import javax.annotation.Nullable;

public class MerkleAccountScopedCheck implements LedgerCheck<MerkleAccount, AccountProperty> {
    private final OptionValidator validator;

    private BalanceChange balanceChange;
    private TransactionalLedger<NftId, NftProperty, UniqueTokenAdapter> nftsLedger;

    public MerkleAccountScopedCheck(
            final OptionValidator validator,
            final TransactionalLedger<NftId, NftProperty, UniqueTokenAdapter> nftsLedger) {
        this.validator = validator;
        this.nftsLedger = nftsLedger;
    }

    @Override
    public ResponseCodeEnum checkUsing(
            final Function<AccountProperty, Object> extantProps,
            final Map<AccountProperty, Object> changeSet) {
        return internalCheck(null, extantProps, changeSet);
    }

    @Override
    public ResponseCodeEnum checkUsing(
            final MerkleAccount account, final Map<AccountProperty, Object> changeSet) {
        return internalCheck(account, null, changeSet);
    }

    public MerkleAccountScopedCheck setBalanceChange(final BalanceChange balanceChange) {
        this.balanceChange = balanceChange;
        return this;
    }

    private ResponseCodeEnum internalCheck(
            @Nullable final MerkleAccount account,
            @Nullable final Function<AccountProperty, Object> extantProps,
            final Map<AccountProperty, Object> changeSet) {
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
            final Map<AccountProperty, Object> changeSet) {
        if (changeSet != null && changeSet.containsKey(prop)) {
            return changeSet.get(prop);
        }
        final var useExtantProps = extantProps != null;
        if (!useExtantProps) {
            assert account != null;
        }
        switch (prop) {
            case IS_SMART_CONTRACT:
                return useExtantProps
                        ? extantProps.apply(IS_SMART_CONTRACT)
                        : account.isSmartContract();
            case IS_DELETED:
                return useExtantProps ? extantProps.apply(IS_DELETED) : account.isDeleted();
            case BALANCE:
                return useExtantProps ? extantProps.apply(BALANCE) : account.getBalance();
            case EXPIRY:
                return useExtantProps ? extantProps.apply(EXPIRY) : account.getExpiry();
            case CRYPTO_ALLOWANCES:
                return useExtantProps
                        ? extantProps.apply(CRYPTO_ALLOWANCES)
                        : account.getCryptoAllowances();
            case FUNGIBLE_TOKEN_ALLOWANCES:
                return useExtantProps
                        ? extantProps.apply(FUNGIBLE_TOKEN_ALLOWANCES)
                        : account.getFungibleTokenAllowances();
            case APPROVE_FOR_ALL_NFTS_ALLOWANCES:
                return useExtantProps
                        ? extantProps.apply(APPROVE_FOR_ALL_NFTS_ALLOWANCES)
                        : account.getApproveForAllNfts();
            default:
                throw new IllegalArgumentException(
                        "Invalid Property " + prop + " cannot be validated in scoped check");
        }
    }

    private ResponseCodeEnum hbarCheck(
            @Nullable final MerkleAccount account,
            @Nullable final Function<AccountProperty, Object> extantProps,
            final Map<AccountProperty, Object> changeSet) {
        if ((boolean) getEffective(IS_DELETED, account, extantProps, changeSet)) {
            return ACCOUNT_DELETED;
        }

        final var expiry = (long) getEffective(EXPIRY, account, extantProps, changeSet);
        final var balance = (long) getEffective(BALANCE, account, extantProps, changeSet);
        final var isContract =
                (boolean) getEffective(IS_SMART_CONTRACT, account, extantProps, changeSet);
        final var expiryStatus = validator.expiryStatusGiven(balance, expiry, isContract);
        if (expiryStatus != OK) {
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

    @SuppressWarnings("unchecked")
    public ResponseCodeEnum validateNftAllowance(
            @Nullable final MerkleAccount account,
            @Nullable final Function<AccountProperty, Object> extantProps,
            final Map<AccountProperty, Object> changeSet) {
        if (balanceChange.isApprovedAllowance()) {
            final var approveForAllNftsAllowances =
                    (Set<FcTokenAllowanceId>)
                            getEffective(
                                    APPROVE_FOR_ALL_NFTS_ALLOWANCES,
                                    account,
                                    extantProps,
                                    changeSet);
            final var nftAllowanceId =
                    FcTokenAllowanceId.from(
                            balanceChange.getToken().asEntityNum(),
                            EntityNum.fromAccountId(balanceChange.getPayerID()));

            if (!approveForAllNftsAllowances.contains(nftAllowanceId)) {
                final var approvedSpender =
                        (EntityId) nftsLedger.get(balanceChange.nftId(), SPENDER);

                if (!approvedSpender.matches(balanceChange.getPayerID())) {
                    return SPENDER_DOES_NOT_HAVE_ALLOWANCE;
                }
            }
        }
        return OK;
    }

    @SuppressWarnings("unchecked")
    private ResponseCodeEnum validateHbarAllowance(
            @Nullable final MerkleAccount account,
            @Nullable final Function<AccountProperty, Object> extantProps,
            final Map<AccountProperty, Object> changeSet) {
        if (balanceChange.isApprovedAllowance()) {
            final var cryptoAllowances =
                    (Map<EntityNum, Long>)
                            getEffective(CRYPTO_ALLOWANCES, account, extantProps, changeSet);
            final var allowance =
                    cryptoAllowances.getOrDefault(
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

    @SuppressWarnings("unchecked")
    public ResponseCodeEnum validateFungibleTokenAllowance(
            @Nullable final MerkleAccount account,
            @Nullable final Function<AccountProperty, Object> extantProps,
            final Map<AccountProperty, Object> changeSet) {
        if (balanceChange.isApprovedAllowance()) {
            final var fungibleAllowances =
                    (Map<FcTokenAllowanceId, Long>)
                            getEffective(
                                    FUNGIBLE_TOKEN_ALLOWANCES, account, extantProps, changeSet);
            final var allowance =
                    fungibleAllowances.getOrDefault(
                            FcTokenAllowanceId.from(
                                    balanceChange.getToken().asEntityNum(),
                                    EntityNum.fromAccountId(balanceChange.getPayerID())),
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
