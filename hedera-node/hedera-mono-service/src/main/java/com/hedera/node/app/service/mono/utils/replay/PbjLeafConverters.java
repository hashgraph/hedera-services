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

package com.hedera.node.app.service.mono.utils.replay;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.state.consensus.Topic;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.token.CryptoAllowance;
import com.hedera.hapi.node.token.NftAllowance;
import com.hedera.hapi.node.token.TokenAllowance;
import com.hedera.node.app.service.mono.pbj.PbjConverter;
import com.hedera.node.app.service.mono.state.merkle.MerkleAccount;
import com.hedera.node.app.service.mono.state.merkle.MerkleTopic;
import com.hedera.node.app.service.mono.state.submerkle.EntityId;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class PbjLeafConverters {
    private PbjLeafConverters() {
        throw new UnsupportedOperationException("Utility Class");
    }

    public static Topic topicFromMerkle(@NonNull final MerkleTopic topic) {
        final var builder = Topic.newBuilder()
                .topicNumber(topic.getKey().longValue())
                .sequenceNumber(topic.getSequenceNumber())
                .expiry(topic.getExpirationTimestamp().getSeconds())
                .autoRenewPeriod(topic.getAutoRenewDurationSeconds())
                .deleted(topic.isDeleted())
                .runningHash(Bytes.wrap(topic.getRunningHash()));
        if (topic.hasMemo()) {
            builder.memo(topic.getMemo());
        }
        if (topic.hasAdminKey()) {
            builder.adminKey(PbjConverter.toPbj(topic.getAdminKey()));
        }
        if (topic.hasSubmitKey()) {
            builder.submitKey(PbjConverter.toPbj(topic.getSubmitKey()));
        }
        return builder.build();
    }

    public static Account accountFromMerkle(@NonNull final MerkleAccount account) {
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
                .key(PbjConverter.toPbj(account.getAccountKey()))
                .autoRenewSecs(account.getAutoRenewSecs())
                .deleted(account.isDeleted())
                .expiry(account.getExpiry())
                .maxAutoAssociations(account.getMaxAutomaticAssociations())
                .usedAutoAssociations(account.getUsedAutoAssociations())
                .contractKvPairsNumber(account.getNumContractKvPairs())
                .cryptoAllowances(orderedHbarAllowancesFrom(account))
                .nftAllowances(orderedOperatorApprovalsFrom(account))
                .tokenAllowances(orderedFungibleAllowancesFrom(account))
                .declineReward(account.isDeclinedReward())
                .stakeAtStartOfLastRewardedPeriod(account.totalStakeAtStartOfLastRewardedPeriod())
                .stakedToMe(account.getStakedToMe())
                .stakePeriodStart(account.getStakePeriodStart())
                .stakedNumber(account.getStakedId())
                .autoRenewAccountNumber(Optional.ofNullable(account.getAutoRenewAccount()).map(EntityId::num).orElse(0L))
                .expiredAndPendingRemoval(account.isExpiredAndPendingRemoval())
                .build();
    }

    static List<NftAllowance> orderedOperatorApprovalsFrom(@NonNull final MerkleAccount account) {
        return orderedOperatorApprovals(
                account.getApproveForAllNfts().stream()
                        .map(a -> NftAllowance.newBuilder()
                                .tokenId(TokenID.newBuilder().tokenNum(a.getTokenNum().longValue()))
                                .build()
                        ).toList());
    }

    static List<CryptoAllowance> orderedHbarAllowancesFrom(@NonNull final MerkleAccount account) {
        return orderedHbarAllowances(
                account.getCryptoAllowances().entrySet().stream()
                        .map(e -> CryptoAllowance.newBuilder()
                                .owner(AccountID.newBuilder().accountNum(e.getKey().longValue()))
                                .amount(e.getValue())
                                .build()
                        ).toList());
    }

    static List<TokenAllowance> orderedFungibleAllowancesFrom(@NonNull final MerkleAccount account) {
        return orderedFungibleAllowances(
                account.getFungibleTokenAllowances().entrySet().stream()
                        .map(e -> TokenAllowance.newBuilder()
                                .tokenId(TokenID.newBuilder().tokenNum(e.getKey().getTokenNum().longValue()))
                                .amount(e.getValue())
                                .build()
                        ).toList());
    }

    static List<NftAllowance> orderedOperatorApprovals(final List<NftAllowance> approvals) {
        return approvals.stream()
                .sorted(Comparator.comparingLong(a -> a.tokenIdOrThrow().tokenNum()))
                .toList();
    }

    static List<CryptoAllowance> orderedHbarAllowances(final List<CryptoAllowance> allowances) {
        return allowances.stream()
                .sorted(Comparator.<CryptoAllowance>comparingLong(a -> a.ownerOrThrow().accountNumOrThrow())
                        .thenComparingLong(CryptoAllowance::amount))
                .toList();
    }

    static List<TokenAllowance> orderedFungibleAllowances(final List<TokenAllowance> allowances) {
        return allowances.stream()
                .sorted(Comparator.<TokenAllowance>comparingLong(a -> a.tokenIdOrThrow().tokenNum())
                        .thenComparingLong(TokenAllowance::amount))
                .toList();
    }
}
