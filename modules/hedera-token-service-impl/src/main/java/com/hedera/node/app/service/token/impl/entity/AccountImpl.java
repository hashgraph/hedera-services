/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.node.app.service.token.impl.entity;

import com.hedera.node.app.service.token.entity.Account;
import com.hedera.node.app.service.token.entity.AccountBuilder;
import com.hedera.node.app.spi.key.HederaKey;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;
import java.util.Optional;

/** An implementation of {@link Account}. */
public record AccountImpl(
        long accountNumber,
        Optional<byte[]> alias,
        Optional<HederaKey> key,
        long expiry,
        long balance,
        String memo,
        boolean isDeleted,
        boolean isSmartContract,
        boolean isReceiverSigRequired,
        long proxyAccountNumber,
        long numberOfOwnedNfts,
        int maxAutoAssociations,
        int usedAutoAssociations,
        int numAssociations,
        int numPositiveBalances,
        long ethereumNonce,
        long stakedToMe,
        long stakePeriodStart,
        long stakedNum,
        boolean declineReward,
        long stakeAtStartOfLastRewardedPeriod,
        long autoRenewAccountNumber,
        long autoRenewSecs)
        implements Account {

    @Override
    public long shardNumber() {
        // FUTURE: Need to get this from config
        return 0;
    }

    @Override
    public long realmNumber() {
        // FUTURE: Need to get this from config
        return 0;
    }

    @Override
    public boolean isHollow() {
        return key.isEmpty();
    }

    @Override
    public long balanceInTinyBar() {
        return balance;
    }

    @Override
    @NonNull
    public AccountBuilder copy() {
        return new AccountBuilderImpl(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AccountImpl account = (AccountImpl) o;
        return accountNumber == account.accountNumber
                && expiry == account.expiry
                && balance == account.balance
                && isDeleted == account.isDeleted
                && isSmartContract == account.isSmartContract
                && isReceiverSigRequired == account.isReceiverSigRequired
                && proxyAccountNumber == account.proxyAccountNumber
                && numberOfOwnedNfts == account.numberOfOwnedNfts
                && maxAutoAssociations == account.maxAutoAssociations
                && usedAutoAssociations == account.usedAutoAssociations
                && numAssociations == account.numAssociations
                && numPositiveBalances == account.numPositiveBalances
                && ethereumNonce == account.ethereumNonce
                && stakedToMe == account.stakedToMe
                && stakePeriodStart == account.stakePeriodStart
                && stakedNum == account.stakedNum
                && declineReward == account.declineReward
                && stakeAtStartOfLastRewardedPeriod == account.stakeAtStartOfLastRewardedPeriod
                && autoRenewAccountNumber == account.autoRenewAccountNumber
                && alias.equals(account.alias)
                && key.equals(account.key)
                && memo.equals(account.memo)
                && autoRenewSecs == account.autoRenewSecs;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                accountNumber,
                alias,
                key,
                expiry,
                balance,
                memo,
                isDeleted,
                isSmartContract,
                isReceiverSigRequired,
                proxyAccountNumber,
                numberOfOwnedNfts,
                maxAutoAssociations,
                usedAutoAssociations,
                numAssociations,
                numPositiveBalances,
                ethereumNonce,
                stakedToMe,
                stakePeriodStart,
                stakedNum,
                declineReward,
                stakeAtStartOfLastRewardedPeriod,
                autoRenewAccountNumber,
                autoRenewSecs);
    }

    @Override
    public String toString() {
        return "AccountImpl{"
                + "accountNumber="
                + accountNumber
                + ", alias="
                + alias
                + ", key="
                + key
                + ", expiry="
                + expiry
                + ", balance="
                + balance
                + ", memo='"
                + memo
                + '\''
                + ", isDeleted="
                + isDeleted
                + ", isSmartContract="
                + isSmartContract
                + ", isReceiverSigRequired="
                + isReceiverSigRequired
                + ", proxyAccountNumber="
                + proxyAccountNumber
                + ", numberOfOwnedNfts="
                + numberOfOwnedNfts
                + ", maxAutoAssociations="
                + maxAutoAssociations
                + ", usedAutoAssociations="
                + usedAutoAssociations
                + ", numAssociations="
                + numAssociations
                + ", numPositiveBalances="
                + numPositiveBalances
                + ", ethereumNonce="
                + ethereumNonce
                + ", stakedToMe="
                + stakedToMe
                + ", stakePeriodStart="
                + stakePeriodStart
                + ", stakedNum="
                + stakedNum
                + ", declineReward="
                + declineReward
                + ", stakeAtStartOfLastRewardedPeriod="
                + stakeAtStartOfLastRewardedPeriod
                + ", autoRenewAccountNumber="
                + autoRenewAccountNumber
                + ", autoRenewSecs="
                + autoRenewSecs
                + '}';
    }
}
