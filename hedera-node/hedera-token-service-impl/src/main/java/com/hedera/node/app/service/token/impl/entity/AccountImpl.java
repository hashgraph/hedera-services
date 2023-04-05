/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

import com.hedera.node.app.spi.accounts.Account;
import com.hedera.node.app.spi.accounts.AccountBuilder;
import com.hedera.node.app.spi.key.HederaKey;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Optional;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

/** An implementation of {@link Account} */
public record AccountImpl(
        long accountNumber,
        Optional<byte[]> alias,
        @Nullable HederaKey key,
        long expiry,
        long balance,
        String memo,
        boolean isDeleted,
        boolean isSmartContract,
        boolean isReceiverSigRequired,
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
    public boolean isHollow() {
        return key == null;
    }

    @Override
    public Optional<HederaKey> getKey() {
        return Optional.ofNullable(key);
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
        return new HashCodeBuilder()
                .append(accountNumber)
                .append(alias)
                .append(key)
                .append(expiry)
                .append(balance)
                .append(memo)
                .append(isDeleted)
                .append(isSmartContract)
                .append(isReceiverSigRequired)
                .append(numberOfOwnedNfts)
                .append(maxAutoAssociations)
                .append(usedAutoAssociations)
                .append(numAssociations)
                .append(numPositiveBalances)
                .append(ethereumNonce)
                .append(stakedToMe)
                .append(stakePeriodStart)
                .append(stakedNum)
                .append(declineReward)
                .append(stakeAtStartOfLastRewardedPeriod)
                .append(autoRenewAccountNumber)
                .append(autoRenewSecs)
                .hashCode();
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("accountNumber", accountNumber)
                .append("alias", alias)
                .append("key", key)
                .append("expiry", expiry)
                .append("balance", balance)
                .append("memo", memo)
                .append("isDeleted", isDeleted)
                .append("isSmartContract", isSmartContract)
                .append("isReceiverSigRequired", isReceiverSigRequired)
                .append("numberOfOwnedNfts", numberOfOwnedNfts)
                .append("maxAutoAssociations", maxAutoAssociations)
                .append("usedAutoAssociations", usedAutoAssociations)
                .append("numAssociations", numAssociations)
                .append("numPositiveBalances", numPositiveBalances)
                .append("ethereumNonce", ethereumNonce)
                .append("stakedToMe", stakedToMe)
                .append("stakePeriodStart", stakePeriodStart)
                .append("stakedNum", stakedNum)
                .append("declineReward", declineReward)
                .append("stakeAtStartOfLastRewardedPeriod", stakeAtStartOfLastRewardedPeriod)
                .append("autoRenewAccountNumber", autoRenewAccountNumber)
                .append("autoRenewSecs", autoRenewSecs)
                .build();
    }
}
