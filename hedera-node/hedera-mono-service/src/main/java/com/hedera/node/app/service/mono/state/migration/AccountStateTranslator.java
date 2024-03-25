/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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
import com.hedera.hapi.node.base.NftID;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.state.token.AccountApprovalForAllAllowance;
import com.hedera.hapi.node.state.token.AccountCryptoAllowance;
import com.hedera.hapi.node.state.token.AccountFungibleTokenAllowance;
import com.hedera.node.app.service.evm.contracts.execution.StaticProperties;
import com.hedera.node.app.service.mono.legacy.core.jproto.JKey;
import com.hedera.node.app.service.mono.pbj.PbjConverter;
import com.hedera.node.app.service.mono.state.submerkle.EntityId;
import com.hedera.node.app.service.mono.state.submerkle.FcTokenAllowanceId;
import com.hedera.node.app.service.mono.state.virtual.ContractKey;
import com.hedera.node.app.service.mono.state.virtual.entities.OnDiskAccount;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
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
                ? Bytes.EMPTY
                : Bytes.wrap(account.getFirstContractStorageKey()
                        .getKeyAsBigInteger()
                        .toByteArray());
        final var stakedAccountId = account.getStakedId() > 0
                ? AccountID.newBuilder().accountNum(account.getStakedId()).build()
                : null;
        final var stakedNodeId = account.getStakedId() < 0 ? -account.getStakedId() - 1 : -1;
        final var acntBuilder = Account.newBuilder()
                .accountId(AccountID.newBuilder()
                        .accountNum(account.getKey().longValue())
                        .realmNum(StaticProperties.getRealm())
                        .shardNum(StaticProperties.getShard()))
                .numberOwnedNfts(account.getNftsOwned())
                .numberTreasuryTitles(account.getNumTreasuryTitles())
                .memo(account.getMemo())
                .smartContract(account.isSmartContract())
                .alias(Bytes.wrap(account.getAlias().toByteArray()))
                .ethereumNonce(account.getEthereumNonce())
                .numberAssociations(account.getNumAssociations())
                .numberPositiveBalances(account.getNumPositiveBalances())
                .headTokenId(TokenID.newBuilder()
                        .tokenNum(account.getHeadTokenId())
                        .realmNum(StaticProperties.getRealm())
                        .shardNum(StaticProperties.getShard()))
                .headNftSerialNumber(account.getHeadNftSerialNum())
                .tinybarBalance(account.getBalance())
                .receiverSigRequired(account.isReceiverSigRequired())
                .key(PbjConverter.asPbjKey(account.getAccountKey()))
                .autoRenewSeconds(account.getAutoRenewSecs())
                .deleted(account.isDeleted())
                .expirationSecond(account.getExpiry())
                .maxAutoAssociations(account.getMaxAutomaticAssociations())
                .usedAutoAssociations(account.getUsedAutoAssociations())
                .contractKvPairsNumber(account.getNumContractKvPairs())
                .cryptoAllowances(orderedHbarAllowancesFrom(account.getCryptoAllowances()))
                .approveForAllNftAllowances(orderedOperatorApprovalsFrom(account.getApproveForAllNfts()))
                .tokenAllowances(orderedFungibleAllowancesFrom(account.getFungibleTokenAllowances()))
                .declineReward(account.isDeclinedReward())
                .stakeAtStartOfLastRewardedPeriod(account.totalStakeAtStartOfLastRewardedPeriod())
                .stakedToMe(account.getStakedToMe())
                .stakePeriodStart(account.getStakePeriodStart())
                .stakedAccountId(stakedAccountId)
                .stakedNodeId(stakedNodeId)
                .firstContractStorageKey(firstContractStorageKey)
                .headNftId(NftID.newBuilder()
                        .tokenId(TokenID.newBuilder()
                                .tokenNum(account.getHeadNftTokenNum())
                                .realmNum(StaticProperties.getRealm())
                                .shardNum(StaticProperties.getShard()))
                        .serialNumber(account.getHeadNftSerialNum()))
                .autoRenewAccountId(AccountID.newBuilder()
                        .accountNum(Optional.ofNullable(account.getAutoRenewAccount())
                                .map(EntityId::num)
                                .orElse(0L))
                        .realmNum(StaticProperties.getRealm())
                        .shardNum(StaticProperties.getShard()))
                .expiredAndPendingRemoval(account.isExpiredAndPendingRemoval());

        if (stakedAccountId != null) acntBuilder.stakedAccountId(stakedAccountId);
        else if (stakedNodeId != -1) acntBuilder.stakedNodeId(stakedNodeId);

        return acntBuilder.build();
    }

    public static Account accountFromOnDiskAccount(@NonNull final OnDiskAccount account) {
        final var firstContractStorageKey = account.getFirstContractStorageKey() == null
                ? Bytes.EMPTY
                : Bytes.wrap(account.getFirstContractStorageKey()
                        .getKeyAsBigInteger()
                        .toByteArray());
        final var stakedAccountId = account.getStakedId() > 0
                ? AccountID.newBuilder().accountNum(account.getStakedId()).build()
                : null;
        final var stakedNodeId = account.getStakedId() < 0 ? -account.getStakedId() - 1 : -1;
        final var acntBuilder = Account.newBuilder()
                .accountId(AccountID.newBuilder()
                        .accountNum(account.getAccountNumber())
                        .realmNum(StaticProperties.getRealm())
                        .shardNum(StaticProperties.getShard()))
                .numberOwnedNfts(account.getNftsOwned())
                .numberTreasuryTitles(account.getNumTreasuryTitles())
                .memo(account.getMemo())
                .smartContract(account.isSmartContract())
                .alias(Bytes.wrap(account.getAlias().toByteArray()))
                .ethereumNonce(account.getEthereumNonce())
                .numberAssociations(account.getNumAssociations())
                .numberPositiveBalances(account.getNumPositiveBalances())
                .headTokenId(TokenID.newBuilder()
                        .tokenNum(account.getHeadTokenId())
                        .realmNum(StaticProperties.getRealm())
                        .shardNum(StaticProperties.getShard()))
                .headNftSerialNumber(account.getHeadNftSerialNum())
                .tinybarBalance(account.getBalance())
                .receiverSigRequired(account.isReceiverSigRequired())
                .key(PbjConverter.asPbjKey(account.getAccountKey()))
                .autoRenewSeconds(account.getAutoRenewSecs())
                .deleted(account.isDeleted())
                .expirationSecond(account.getExpiry())
                .maxAutoAssociations(account.getMaxAutomaticAssociations())
                .usedAutoAssociations(account.getUsedAutoAssociations())
                .contractKvPairsNumber(account.getNumContractKvPairs())
                .cryptoAllowances(orderedHbarAllowancesFrom(account.getCryptoAllowances()))
                .approveForAllNftAllowances(orderedOperatorApprovalsFrom(account.getApproveForAllNfts()))
                .tokenAllowances(orderedFungibleAllowancesFrom(account.getFungibleTokenAllowances()))
                .declineReward(account.isDeclinedReward())
                .stakeAtStartOfLastRewardedPeriod(account.totalStakeAtStartOfLastRewardedPeriod())
                .stakedToMe(account.getStakedToMe())
                .stakePeriodStart(account.getStakePeriodStart())
                .firstContractStorageKey(firstContractStorageKey)
                .headNftId(NftID.newBuilder()
                        .tokenId(TokenID.newBuilder()
                                .tokenNum(account.getHeadNftTokenNum())
                                .realmNum(StaticProperties.getRealm())
                                .shardNum(StaticProperties.getShard()))
                        .serialNumber(account.getHeadNftSerialNum()))
                .autoRenewAccountId(
                        account.hasAutoRenewAccount()
                                ? AccountID.newBuilder()
                                        .realmNum(StaticProperties.getRealm())
                                        .shardNum(StaticProperties.getShard())
                                        .accountNum(
                                                account.getAutoRenewAccount().num())
                                        .build()
                                : null)
                .expiredAndPendingRemoval(account.isExpiredAndPendingRemoval());

        if (stakedAccountId != null) acntBuilder.stakedAccountId(stakedAccountId);
        else if (stakedNodeId != -1) acntBuilder.stakedNodeId(stakedNodeId);

        return acntBuilder.build();
    }

    @Nullable
    static List<AccountApprovalForAllAllowance> orderedOperatorApprovalsFrom(
            @NonNull final Set<FcTokenAllowanceId> approveForAllNfts) {
        return orderedOperatorApprovals(approveForAllNfts.stream()
                .map(a -> AccountApprovalForAllAllowance.newBuilder()
                        .spenderId(AccountID.newBuilder()
                                .accountNum(a.getSpenderNum().longValue())
                                .realmNum(StaticProperties.getRealm())
                                .shardNum(StaticProperties.getShard()))
                        .tokenId(TokenID.newBuilder()
                                .tokenNum(a.getTokenNum().longValue())
                                .realmNum(StaticProperties.getRealm())
                                .shardNum(StaticProperties.getShard()))
                        .build())
                .toList());
    }

    @Nullable
    static List<AccountCryptoAllowance> orderedHbarAllowancesFrom(
            @NonNull final Map<EntityNum, Long> cryptoAllowances) {
        return orderedHbarAllowances(cryptoAllowances.entrySet().stream()
                .map(e -> AccountCryptoAllowance.newBuilder()
                        .spenderId(AccountID.newBuilder()
                                .accountNum(e.getKey().longValue())
                                .realmNum(StaticProperties.getRealm())
                                .shardNum(StaticProperties.getShard()))
                        .amount(e.getValue())
                        .build())
                .toList());
    }

    @Nullable
    static List<AccountFungibleTokenAllowance> orderedFungibleAllowancesFrom(
            @NonNull final Map<FcTokenAllowanceId, Long> fungibleTokenAllowances) {
        return orderedFungibleAllowances(fungibleTokenAllowances.entrySet().stream()
                .map(e -> AccountFungibleTokenAllowance.newBuilder()
                        .tokenId(TokenID.newBuilder()
                                .tokenNum(e.getKey().getTokenNum().longValue())
                                .realmNum(StaticProperties.getRealm())
                                .shardNum(StaticProperties.getShard()))
                        .spenderId(AccountID.newBuilder()
                                .accountNum(e.getKey().getSpenderNum().longValue())
                                .realmNum(StaticProperties.getRealm())
                                .shardNum(StaticProperties.getShard()))
                        .amount(e.getValue())
                        .build())
                .toList());
    }

    @Nullable
    static List<AccountApprovalForAllAllowance> orderedOperatorApprovals(
            @NonNull final List<AccountApprovalForAllAllowance> approvals) {

        Comparator<AccountApprovalForAllAllowance> comp = Comparator.comparingLong((AccountApprovalForAllAllowance a) ->
                        a.spenderIdOrThrow().shardNum())
                .thenComparingLong(a -> a.spenderIdOrThrow().realmNum())
                .thenComparingLong(a -> a.spenderIdOrThrow().accountNum())
                .thenComparingLong(a -> a.tokenIdOrThrow().shardNum())
                .thenComparingLong(a -> a.tokenIdOrThrow().realmNum())
                .thenComparingLong(a -> a.tokenIdOrThrow().tokenNum());
        return approvals.stream().sorted(comp).toList();
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
        Comparator<AccountFungibleTokenAllowance> comp = Comparator.comparingLong(
                        (AccountFungibleTokenAllowance a) -> a.tokenIdOrThrow().shardNum())
                .thenComparingLong(a -> a.tokenIdOrThrow().realmNum())
                .thenComparingLong(a -> a.tokenIdOrThrow().tokenNum())
                .thenComparingLong(a -> a.amount());
        return allowances.stream().sorted(comp).toList();
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
        final var stakedId = (account.hasStakedNodeId() && account.stakedNodeId() != -1)
                ? -1 - account.stakedNodeId()
                : (account.hasStakedAccountId() ? account.stakedAccountId().accountNum() : 0);

        merkleAccount.setKey(
                EntityNum.fromLong(account.accountIdOrElse(AccountID.DEFAULT).accountNum()));
        merkleAccount.setNftsOwned(account.numberOwnedNfts());
        merkleAccount.setNumTreasuryTitles(account.numberTreasuryTitles());
        merkleAccount.setMemo(account.memo());
        merkleAccount.setSmartContract(account.smartContract());
        merkleAccount.setAlias(ByteString.copyFrom((account.alias().toByteArray())));
        merkleAccount.setEthereumNonce(account.ethereumNonce());
        merkleAccount.setNumAssociations(account.numberAssociations());
        merkleAccount.setNumPositiveBalances(account.numberPositiveBalances());
        merkleAccount.setHeadTokenId(account.headTokenIdOrElse(TokenID.DEFAULT).tokenNum());
        merkleAccount.setHeadNftSerialNum(account.headNftSerialNumber());
        merkleAccount.setBalanceUnchecked(account.tinybarBalance());
        merkleAccount.setReceiverSigRequired(account.receiverSigRequired());
        merkleAccount.setAccountKey((JKey) PbjConverter.fromPbjKeyUnchecked(account.keyOrElse((Key.DEFAULT)))
                .orElse(null));
        merkleAccount.setAutoRenewSecs(account.autoRenewSeconds());
        merkleAccount.setDeleted(account.deleted());
        merkleAccount.setExpiry(account.expirationSecond());
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
        merkleAccount.setStakedId(stakedId);
        merkleAccount.setAutoRenewAccount(new EntityId(
                0, 0, account.autoRenewAccountIdOrElse(AccountID.DEFAULT).accountNum()));
        merkleAccount.setExpiredAndPendingRemoval(account.expiredAndPendingRemoval());
        merkleAccount.setHeadNftId(account.headNftIdOrElse(NftID.DEFAULT)
                .tokenIdOrElse(TokenID.DEFAULT)
                .tokenNum());
        merkleAccount.setHeadNftSerialNum(account.headNftSerialNumber());
        if (account.firstContractStorageKey() != null
                && account.firstContractStorageKey().length() > 0)
            merkleAccount.setFirstUint256StorageKey(new ContractKey(
                            account.accountIdOrElse(AccountID.DEFAULT).accountNum(),
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
                cryptoAllowances.put(
                        EntityNum.fromLong(
                                allowance.spenderIdOrElse(AccountID.DEFAULT).accountNum()),
                        allowance.amount());
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
                        EntityNum.fromLong(
                                allowance.tokenIdOrElse(TokenID.DEFAULT).tokenNum()),
                        EntityNum.fromLong(
                                allowance.spenderIdOrElse(AccountID.DEFAULT).accountNum())));
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
                                EntityNum.fromLong(
                                        allowance.tokenIdOrElse(TokenID.DEFAULT).tokenNum()),
                                EntityNum.fromLong(allowance
                                        .spenderIdOrElse(AccountID.DEFAULT)
                                        .accountNum())),
                        allowance.amount());
            }
        }
        return fungibleAllowances;
    }
}
