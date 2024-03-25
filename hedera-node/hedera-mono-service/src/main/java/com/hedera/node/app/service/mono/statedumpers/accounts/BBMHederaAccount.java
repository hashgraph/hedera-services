/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.mono.statedumpers.accounts;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.NftID;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.state.token.Account.StakedIdOneOfType;
import com.hedera.hapi.node.state.token.AccountApprovalForAllAllowance;
import com.hedera.hapi.node.state.token.AccountCryptoAllowance;
import com.hedera.hapi.node.state.token.AccountFungibleTokenAllowance;
import com.hedera.node.app.service.mono.pbj.PbjConverter;
import com.hedera.node.app.service.mono.state.submerkle.FcTokenAllowanceId;
import com.hedera.node.app.service.mono.state.virtual.entities.OnDiskAccount;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.node.app.service.mono.utils.EntityNumPair;
import com.hedera.pbj.runtime.OneOf;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record BBMHederaAccount(
        @Nullable AccountID accountId,
        @NonNull Bytes alias,
        @Nullable Key key,
        long expirationSecond,
        long tinybarBalance,
        String memo,
        boolean deleted,
        long stakedToMe,
        long stakePeriodStart,
        OneOf<StakedIdOneOfType> stakedId,
        boolean declineReward,
        boolean receiverSigRequired,
        @Nullable TokenID headTokenId,
        @Nullable NftID headNftId,
        long headNftSerialNumber,
        long numberOwnedNfts,
        int maxAutoAssociations,
        int usedAutoAssociations,
        int numberAssociations,
        boolean smartContract,
        int numberPositiveBalances,
        long ethereumNonce,
        long stakeAtStartOfLastRewardedPeriod,
        @Nullable AccountID autoRenewAccountId,
        long autoRenewSeconds,
        int contractKvPairsNumber,
        @Nullable List<AccountCryptoAllowance> cryptoAllowances,
        @Nullable List<AccountApprovalForAllAllowance> approveForAllNftAllowances,
        @Nullable List<AccountFungibleTokenAllowance> tokenAllowances,
        int numberTreasuryTitles,
        boolean expiredAndPendingRemoval,
        @NonNull Bytes firstContractStorageKey,
        boolean immutable,
        long stakedNodeAddressBookId) {

    private static final AccountID MISSING_ACCOUNT_ID = AccountID.DEFAULT;
    public static final long ONE_HBAR_IN_TINYBARS = 100_000_000L;
    public static final BBMHederaAccount DUMMY_ACCOUNT = new BBMHederaAccount(
            null, null, null, 0, 0, "", false, 0, 0, null, false, false, null, null, 0, 0, 0, 0, 0, false, 0, 0, 0,
            null, 0, 0, null, null, null, 0, false, null, false, 0);

    public static BBMHederaAccount fromMono(OnDiskAccount account) {
        return new BBMHederaAccount(
                AccountID.newBuilder().accountNum(account.getAccountNumber()).build(),
                Bytes.wrap(account.getAlias().toByteArray()),
                PbjConverter.asPbjKey(account.getKey()),
                account.getExpiry(),
                account.getBalance(),
                account.getMemo(),
                account.isDeleted(),
                account.getStakedToMe(),
                account.getStakePeriodStart(),
                account.getStakedId() < 0
                        ? new OneOf<>(StakedIdOneOfType.STAKED_NODE_ID, account.getStakedId())
                        : new OneOf<>(StakedIdOneOfType.STAKED_ACCOUNT_ID, account.getStakedId()),
                account.isDeclineReward(),
                account.isReceiverSigRequired(),
                new TokenID(0, 0, account.getHeadTokenId()),
                new NftID(new TokenID(0, 0, account.getHeadNftId()), account.getHeadNftSerialNum()),
                account.getHeadNftSerialNum(),
                account.getNftsOwned(),
                account.getMaxAutoAssociations(),
                account.getUsedAutoAssociations(),
                account.getNumAssociations(),
                account.isSmartContract(),
                account.getNumPositiveBalances(),
                account.getEthereumNonce(),
                account.getStakeAtStartOfLastRewardedPeriod(),
                account.getAutoRenewAccount() != null
                        ? account.getAutoRenewAccount().toPbjAccountId()
                        : null,
                account.getAutoRenewSecs(),
                account.getNumContractKvPairs(),
                toCryptoAllowance(account.getCryptoAllowances()),
                toApproveForAllNftAllowances(account.getApproveForAllNfts()),
                toAccountFungibleTokenAllowance(account.getFungibleTokenAllowances()),
                account.getNumTreasuryTitles(),
                account.isExpiredAndPendingRemoval(),
                account.getFirstContractStorageKey() == null
                        ? Bytes.EMPTY
                        : toBytes(account.getFirstContractStorageKey().getKey()),
                account.isImmutable(),
                account.getStakedId() >= 0 ? -1 : account.getStakedNodeAddressBookId());
    }

    @NonNull
    private static Bytes toBytes(@NonNull final int[] packed) {
        final var buf = ByteBuffer.allocate(32);
        buf.asIntBuffer().put(packed);
        return Bytes.wrap(buf.array());
    }

    private static List<AccountCryptoAllowance> toCryptoAllowance(Map<EntityNum, Long> cryptoAllowanceMap) {
        return cryptoAllowanceMap.entrySet().stream()
                .map(c -> new AccountCryptoAllowance(c.getKey().toEntityId().toPbjAccountId(), c.getValue()))
                .toList();
    }

    private static List<AccountApprovalForAllAllowance> toApproveForAllNftAllowances(
            Set<FcTokenAllowanceId> allowanceIds) {
        return allowanceIds.stream()
                .map(a -> new AccountApprovalForAllAllowance(
                        new TokenID(0, 0, a.getTokenNum().longValue()),
                        a.getSpenderNum().toEntityId().toPbjAccountId()))
                .toList();
    }

    private static List<AccountFungibleTokenAllowance> toAccountFungibleTokenAllowance(
            Map<FcTokenAllowanceId, Long> tokenAllowanceMap) {
        return tokenAllowanceMap.entrySet().stream()
                .map(t -> new AccountFungibleTokenAllowance(
                        new TokenID(0, 0, t.getKey().getTokenNum().longValue()),
                        t.getKey().getSpenderNum().toEntityId().toPbjAccountId(),
                        t.getValue()))
                .toList();
    }

    private static final AccountID immutableAccount1 =
            AccountID.newBuilder().accountNum(800).build();
    private static final AccountID immutableAccount2 =
            AccountID.newBuilder().accountNum(801).build();
    public static final List<AccountID> immutableAccounts = List.of(immutableAccount1, immutableAccount2);

    public boolean isImmutable() {
        return immutable;
    }

    public EntityNumPair getHeadNftKey() {
        if (headNftId() == null || headNftId().tokenId() == null) {
            return null;
        }

        return EntityNumPair.fromLongs(headNftId().tokenId().tokenNum(), headNftSerialNumber);
    }

    public EntityNumPair getLatestAssociation() {
        if (accountId == null || accountId.accountNum() == null || headTokenId() == null) {
            return null;
        }
        return EntityNumPair.fromLongs(accountId.accountNum(), headTokenId().tokenNum());
    }

    public long totalStake() {
        return tinybarBalance() + stakedToMe();
    }

    public AccountID getProxy() {
        return MISSING_ACCOUNT_ID;
    }

    public int[] getFirstUint256Key() {
        return toInts(firstContractStorageKey);
    }

    private static int[] toInts(Bytes bytes) {
        final var bytesArray = bytes.toByteArray();
        ByteBuffer buf = ByteBuffer.wrap(bytesArray);
        int[] ints = new int[bytesArray.length / 4];
        for (int i = 0; i < ints.length; i++) {
            ints[i] = buf.getInt();
        }
        return ints;
    }
}
