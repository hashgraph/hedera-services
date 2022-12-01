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
import edu.umd.cs.findbugs.annotations.Nullable;

import java.util.Optional;

/** An implementation of {@link Account}. FUTURE: Should be moved to token-service-impl module */
public record AccountImpl(
        long accountNumber,
        byte[] alias,
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
        return key == null;
    }

    @Override
    public Optional<HederaKey> getKey() {
        return Optional.ofNullable(key);
    }

    @Override
    public Optional<byte[]> getAlias() {
        return alias.length == 0 ? Optional.empty() : Optional.of(alias);
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
}
