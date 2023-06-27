/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.mono.state.migration;

import static java.util.Objects.requireNonNull;

import com.google.protobuf.ByteString;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.state.token.AccountApprovalForAllAllowance;
import com.hedera.hapi.node.state.token.AccountCryptoAllowance;
import com.hedera.hapi.node.state.token.AccountFungibleTokenAllowance;
import com.hedera.node.app.service.mono.legacy.core.jproto.JKey;
import com.hedera.node.app.service.mono.pbj.PbjConverter;
import com.hedera.node.app.service.mono.state.submerkle.EntityId;
import com.hedera.node.app.service.mono.state.submerkle.FcTokenAllowanceId;
import com.hedera.node.app.service.mono.state.virtual.ContractKey;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Translates between the legacy {@link com.hedera.node.app.service.mono.state.merkle.MerkleAccount} and the new {@link Account} both ways.
 */
public class AccountStateTranslator {

    @NonNull
    /**
     * Translates a {@link com.hedera.node.app.service.mono.state.merkle.MerkleAccount} to a {@link Account}.
     * @param account the {@link com.hedera.node.app.service.mono.state.merkle.MerkleAccount} to translate
     * @return the translated {@link Account}
     */
    public static Account accountFromMerkle(
            @NonNull final com.hedera.node.app.service.mono.state.merkle.MerkleAccount account) {

        final var firstContractStorageKey = account.getFirstContractStorageKey() == null
                ? null
                : Bytes.wrap(account.getFirstContractStorageKey()
                        .getKeyAsBigInteger()
                        .toByteArray());
        return Account.newBuilder()
                .accountNumber(account.getKey().longValue())
                .numberOwnedNfts(account.getNftsOwned())
                .numberTreasuryTitles(account.getNumTreasuryTitles())
                .memo(account.getMemo())
                .smartContract(account.isSmartContract())
                .alias(Bytes.wrap(account.getAlias().toByteArray()))
                .ethereumNonce(account.getEthereumNonce())
                .numberAssociations(account.getNumAssociations())
                .numberPositiveBalances(account.getNumPositiveBalances())
                .headTokenNumber(account.getHeadTokenId())
                .headNftSerialNumber(account.getHeadNftSerialNum())
                .tinybarBalance(account.getBalance())
                .receiverSigRequired(account.isReceiverSigRequired())
                .key(PbjConverter.asPbjKey(account.getAccountKey()))
                .autoRenewSecs(account.getAutoRenewSecs())
                .deleted(account.isDeleted())
                .expiry(account.getExpiry())
                .maxAutoAssociations(account.getMaxAutomaticAssociations())
                .usedAutoAssociations(account.getUsedAutoAssociations())
                .contractKvPairsNumber(account.getNumContractKvPairs())
                .cryptoAllowances(orderedHbarAllowancesFrom(account))
                .approveForAllNftAllowances(orderedOperatorApprovalsFrom(account))
                .tokenAllowances(orderedFungibleAllowancesFrom(account))
                .declineReward(account.isDeclinedReward())
                .stakeAtStartOfLastRewardedPeriod(account.totalStakeAtStartOfLastRewardedPeriod())
                .stakedToMe(account.getStakedToMe())
                .stakePeriodStart(account.getStakePeriodStart())
                .stakedNumber(account.getStakedId())
                .firstContractStorageKey(firstContractStorageKey)
                .headNftId(account.getHeadNftTokenNum())
                .headNftSerialNumber(account.getHeadNftSerialNum())
                .autoRenewAccountNumber(Optional.ofNullable(account.getAutoRenewAccount())
                        .map(EntityId::num)
                        .orElse(0L))
                .expiredAndPendingRemoval(account.isExpiredAndPendingRemoval())
                .build();
    }

    @Nullable
    static List<AccountApprovalForAllAllowance> orderedOperatorApprovalsFrom(
            @NonNull final com.hedera.node.app.service.mono.state.merkle.MerkleAccount account) {
        return orderedOperatorApprovals(account.getApproveForAllNfts().stream()
                .map(a -> AccountApprovalForAllAllowance.newBuilder()
                        .spenderNum(a.getSpenderNum().longValue())
                        .tokenNum(a.getTokenNum().longValue())
                        .build())
                .toList());
    }

    @Nullable
    static List<AccountCryptoAllowance> orderedHbarAllowancesFrom(
            @NonNull final com.hedera.node.app.service.mono.state.merkle.MerkleAccount account) {
        return orderedHbarAllowances(account.getCryptoAllowances().entrySet().stream()
                .map(e -> AccountCryptoAllowance.newBuilder()
                        .spenderNum(e.getKey().longValue())
                        .amount(e.getValue())
                        .build())
                .toList());
    }

    @Nullable
    static List<AccountFungibleTokenAllowance> orderedFungibleAllowancesFrom(
            @NonNull final com.hedera.node.app.service.mono.state.merkle.MerkleAccount account) {
        return orderedFungibleAllowances(account.getFungibleTokenAllowances().entrySet().stream()
                .map(e -> AccountFungibleTokenAllowance.newBuilder()
                        .tokenNum(e.getKey().getTokenNum().longValue())
                        .spenderNum(e.getKey().getSpenderNum().longValue())
                        .amount(e.getValue())
                        .build())
                .toList());
    }

    @Nullable
    static List<AccountApprovalForAllAllowance> orderedOperatorApprovals(
            @NonNull final List<AccountApprovalForAllAllowance> approvals) {
        return approvals.stream()
                .sorted(Comparator.comparingLong(AccountApprovalForAllAllowance::spenderNum)
                        .thenComparingLong(AccountApprovalForAllAllowance::tokenNum))
                .toList();
    }

    @Nullable
    static List<AccountCryptoAllowance> orderedHbarAllowances(final List<AccountCryptoAllowance> allowances) {
        return allowances.stream()
                .sorted(Comparator.comparingLong(AccountCryptoAllowance::amount))
                .toList();
    }

    @Nullable
    static List<AccountFungibleTokenAllowance> orderedFungibleAllowances(
            final List<AccountFungibleTokenAllowance> allowances) {
        return allowances.stream()
                .sorted(Comparator.comparingLong(AccountFungibleTokenAllowance::tokenNum)
                        .thenComparingLong(AccountFungibleTokenAllowance::amount))
                .toList();
    }

    @NonNull
    /***
     * Converts a {@link com.hedera.services.store.models.Account} to a {@link com.hedera.node.app.service.mono.state.merkle.MerkleAccount}
     * @param accountID the {@link AccountID} of the account to convert
     * @param readableAccountStore the {@link com.hedera.node.app.service.token.ReadableAccountStore} to use to retrieve the account
     * @return the {@link com.hedera.node.app.service.mono.state.merkle.MerkleAccount} corresponding to the accountID
     */
    public static com.hedera.node.app.service.mono.state.merkle.MerkleAccount merkleAccountFromAccount(
            @NonNull AccountID accountID, @NonNull ReadableAccountStore readableAccountStore) {
        requireNonNull(accountID);
        requireNonNull(readableAccountStore);
        final var optionalAccount = readableAccountStore.getAccountById(accountID);
        if (optionalAccount == null) {
            throw new IllegalArgumentException("Account not found");
        }
        return merkleAccountFromAccount(optionalAccount);
    }

    @NonNull
    /***
     * Converts a {@link Account} to a {@link com.hedera.node.app.service.mono.state.merkle.MerkleAccount}
     * @param account the {@link Account} to convert
     * @return the {@link com.hedera.node.app.service.mono.state.merkle.MerkleAccount} corresponding to the account
     */
    public static com.hedera.node.app.service.mono.state.merkle.MerkleAccount merkleAccountFromAccount(
            @NonNull Account account) {
        requireNonNull(account);
        com.hedera.node.app.service.mono.state.merkle.MerkleAccount merkleAccount =
                new com.hedera.node.app.service.mono.state.merkle.MerkleAccount();
        merkleAccount.setKey(EntityNum.fromLong(account.accountNumber()));
        merkleAccount.setNftsOwned(account.numberOwnedNfts());
        merkleAccount.setNumTreasuryTitles(account.numberTreasuryTitles());
        merkleAccount.setMemo(account.memo());
        merkleAccount.setSmartContract(account.smartContract());
        merkleAccount.setAlias(ByteString.copyFrom((account.alias().toByteArray())));
        merkleAccount.setEthereumNonce(account.ethereumNonce());
        merkleAccount.setNumAssociations(account.numberAssociations());
        merkleAccount.setNumPositiveBalances(account.numberPositiveBalances());
        merkleAccount.setHeadTokenId(account.headTokenNumber());
        merkleAccount.setHeadNftSerialNum(account.headNftSerialNumber());
        merkleAccount.setBalanceUnchecked(account.tinybarBalance());
        merkleAccount.setReceiverSigRequired(account.receiverSigRequired());
        merkleAccount.setAccountKey((JKey) PbjConverter.fromPbjKeyUnchecked(account.keyOrElse((Key.DEFAULT)))
                .orElse(null));
        merkleAccount.setAutoRenewSecs(account.autoRenewSecs());
        merkleAccount.setDeleted(account.deleted());
        merkleAccount.setExpiry(account.expiry());
        merkleAccount.setMaxAutomaticAssociations(account.maxAutoAssociations());
        merkleAccount.setUsedAutomaticAssociations(account.usedAutoAssociations());
        merkleAccount.setNumContractKvPairs(account.contractKvPairsNumber());
        merkleAccount.setCryptoAllowances(orderedHbarAllowancesFrom(account));
        merkleAccount.setApproveForAllNfts(orderedOperatorApprovalsFrom(account));
        merkleAccount.setFungibleTokenAllowances(orderedFungibleAllowancesFrom(account));
        merkleAccount.setDeclineReward(account.declineReward());
        merkleAccount.setStakeAtStartOfLastRewardedPeriod(account.stakeAtStartOfLastRewardedPeriod());
        merkleAccount.setStakedToMe(account.stakedToMe());
        merkleAccount.setStakePeriodStart(account.stakePeriodStart());
        merkleAccount.setStakedId(account.stakedNumber());
        merkleAccount.setAutoRenewAccount(new EntityId(0, 0, account.autoRenewAccountNumber()));
        merkleAccount.setExpiredAndPendingRemoval(account.expiredAndPendingRemoval());
        merkleAccount.setHeadNftId(account.headNftId());
        merkleAccount.setHeadNftSerialNum(account.headNftSerialNumber());
        if (account.firstContractStorageKey() != null)
            merkleAccount.setFirstUint256StorageKey(new ContractKey(
                            account.accountNumber(),
                            account.firstContractStorageKey().toByteArray())
                    .getKey());
        return merkleAccount;
    }

    @NonNull
    static SortedMap<EntityNum, Long> orderedHbarAllowancesFrom(@NonNull final Account account) {
        final SortedMap<EntityNum, Long> cryptoAllowances = new TreeMap<>();
        final var allowances = account.cryptoAllowances();
        if (allowances != null) {
            for (var allowance : allowances) {
                cryptoAllowances.put(EntityNum.fromLong(allowance.spenderNum()), allowance.amount());
            }
        }

        return cryptoAllowances;
    }

    @NonNull
    static Set<FcTokenAllowanceId> orderedOperatorApprovalsFrom(@NonNull final Account account) {
        final Set<FcTokenAllowanceId> fcTokenAllowanceIdSet = new TreeSet<>();
        final var allowances = account.approveForAllNftAllowances();
        if (allowances != null) {
            for (var allowance : allowances) {
                fcTokenAllowanceIdSet.add(new FcTokenAllowanceId(
                        EntityNum.fromLong(allowance.tokenNum()), EntityNum.fromLong(allowance.spenderNum())));
            }
        }
        return fcTokenAllowanceIdSet;
    }

    @NonNull
    static SortedMap<FcTokenAllowanceId, Long> orderedFungibleAllowancesFrom(@NonNull final Account account) {
        final SortedMap<FcTokenAllowanceId, Long> fungibleAllowances = new TreeMap<>();
        final var allowances = account.tokenAllowances();
        if (allowances != null) {
            for (var allowance : allowances) {
                fungibleAllowances.put(
                        new FcTokenAllowanceId(
                                EntityNum.fromLong(allowance.tokenNum()), EntityNum.fromLong(allowance.spenderNum())),
                        allowance.amount());
            }
        }
        return fungibleAllowances;
    }
}
