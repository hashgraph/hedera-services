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

package com.hedera.test.utils;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.node.app.service.mono.state.migration.HederaAccount;
import com.hedera.node.app.service.mono.state.virtual.EntityNumVirtualKey;
import com.hedera.node.app.spi.accounts.Account;
import com.hedera.node.app.spi.accounts.AccountAccess;
import com.hedera.node.app.spi.accounts.AccountBuilder;
import com.hedera.node.app.spi.key.HederaKey;
import com.hedera.node.app.spi.state.ReadableKVState;
import com.hedera.node.app.spi.state.ReadableStates;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

public class TestFixturesKeyLookup implements AccountAccess {
    private final ReadableKVState<String, Long> aliases;
    private final ReadableKVState<EntityNumVirtualKey, HederaAccount> accounts;

    public TestFixturesKeyLookup(@NonNull final ReadableStates states) {
        this.accounts = states.get("ACCOUNTS");
        this.aliases = states.get("ALIASES");
    }

    @Nullable
    @Override
    public Account getAccountById(@NonNull AccountID accountID) {
        final var alias = accountID.alias();
        if (alias != null && alias.length() > 0) {
            final var num = aliases.get(alias.asUtf8String());
            if (num == null) {
                return null;
            } else {
                final var account = accounts.get(new EntityNumVirtualKey(num));
                return account == null ? null : new StubbedAccount(num, alias, account);
            }
        } else if (!accountID.hasAccountNum()) {
            return null;
        } else {
            final long num = accountID.accountNumOrThrow();
            final var account = accounts.get(new EntityNumVirtualKey(num));
            return account == null ? null : new StubbedAccount(num, Bytes.EMPTY, account);
        }
    }

    private static final class StubbedAccount implements Account {
        private final long num;
        private final Bytes alias;
        private final HederaAccount account;

        private StubbedAccount(long num, Bytes alias, HederaAccount account) {
            this.num = num;
            this.alias = alias;
            this.account = account;
        }

        @Override
        public long accountNumber() {
            return num;
        }

        @Nullable
        @Override
        public Bytes alias() {
            return alias;
        }

        @Override
        public boolean isHollow() {
            return false;
        }

        @Nullable
        @Override
        public HederaKey getKey() {
            return account.getAccountKey();
        }

        @Override
        public long expiry() {
            return account.getExpiry();
        }

        @Override
        public long balanceInTinyBar() {
            return account.getBalance();
        }

        @Override
        public long autoRenewSecs() {
            return account.getAutoRenewSecs();
        }

        @NonNull
        @Override
        public String memo() {
            return account.getMemo();
        }

        @Override
        public boolean isDeleted() {
            return account.isDeleted();
        }

        @Override
        public boolean isSmartContract() {
            return account.isSmartContract();
        }

        @Override
        public boolean isReceiverSigRequired() {
            return account.isReceiverSigRequired();
        }

        @Override
        public long numberOfOwnedNfts() {
            return account.getNftsOwned();
        }

        @Override
        public int maxAutoAssociations() {
            return account.getMaxAutomaticAssociations();
        }

        @Override
        public int usedAutoAssociations() {
            return account.getUsedAutoAssociations();
        }

        @Override
        public int numAssociations() {
            return account.getNumAssociations();
        }

        @Override
        public int numPositiveBalances() {
            return account.getNumPositiveBalances();
        }

        @Override
        public long ethereumNonce() {
            return account.getEthereumNonce();
        }

        @Override
        public long stakedToMe() {
            return account.getStakedToMe();
        }

        @Override
        public long stakePeriodStart() {
            return account.getStakePeriodStart();
        }

        @Override
        public long stakedNum() {
            return account.totalStake();
        }

        @Override
        public boolean declineReward() {
            return account.isDeclinedReward();
        }

        @Override
        public long stakeAtStartOfLastRewardedPeriod() {
            return account.totalStakeAtStartOfLastRewardedPeriod();
        }

        @Override
        public long autoRenewAccountNumber() {
            return account.getAutoRenewAccount().num();
        }

        @NonNull
        @Override
        public AccountBuilder copy() {
            throw new UnsupportedOperationException("copy not supported");
        }
    }
}
