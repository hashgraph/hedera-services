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

package com.hedera.node.app.statedumpers.accounts;

import static com.hedera.hapi.node.state.token.Account.StakedIdOneOfType.STAKED_ACCOUNT_ID;
import static com.hedera.hapi.node.state.token.Account.StakedIdOneOfType.STAKED_NODE_ID;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.NftID;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.state.token.Account.StakedIdOneOfType;
import com.hedera.hapi.node.state.token.AccountApprovalForAllAllowance;
import com.hedera.hapi.node.state.token.AccountCryptoAllowance;
import com.hedera.hapi.node.state.token.AccountFungibleTokenAllowance;
import com.hedera.node.app.statedumpers.legacy.EntityNumPair;
import com.hedera.pbj.runtime.OneOf;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.nio.ByteBuffer;
import java.util.List;

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
    public static final BBMHederaAccount DUMMY_ACCOUNT = new BBMHederaAccount(
            null, null, null, 0, 0, "", false, 0, 0, null, false, false, null, null, 0, 0, 0, 0, 0, false, 0, 0, 0,
            null, 0, 0, null, null, null, 0, false, null, false, 0);

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
        return firstContractStorageKey == Bytes.EMPTY ? null : toInts(firstContractStorageKey);
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

    public Long stakedIdLong() {
        var id = -1L;
        try {
            if (stakedId.kind() == STAKED_NODE_ID) {
                id = -((Long) stakedId.value()) - 1;
            } else if (stakedId.kind() == STAKED_ACCOUNT_ID) {
                id = ((AccountID) stakedId.value()).accountNum();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return id;
    }
}
