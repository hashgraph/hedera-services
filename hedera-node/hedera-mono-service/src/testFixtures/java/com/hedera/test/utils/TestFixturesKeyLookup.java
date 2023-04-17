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

import static com.hedera.node.app.service.mono.utils.MiscUtils.asPbjKeyUnchecked;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.node.app.service.mono.state.migration.HederaAccount;
import com.hedera.node.app.service.mono.state.virtual.EntityNumVirtualKey;
import com.hedera.node.app.spi.accounts.AccountAccess;
import com.hedera.node.app.spi.state.ReadableKVState;
import com.hedera.node.app.spi.state.ReadableStates;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Collections;

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
                return account == null ? null : getNewAccount(num, alias, account);
            }
        } else if (!accountID.hasAccountNum()) {
            return null;
        } else {
            final long num = accountID.accountNumOrThrow();
            final var account = accounts.get(new EntityNumVirtualKey(num));
            return account == null ? null : getNewAccount(num, Bytes.EMPTY, account);
        }
    }

    private Account getNewAccount(long num, Bytes alias, HederaAccount account) {
        return new Account(
                num,
                alias,
                asPbjKeyUnchecked(account.getAccountKey()),
                account.getExpiry(),
                account.getBalance(),
                account.getMemo(),
                account.isDeleted(),
                account.getStakedToMe(),
                account.getStakePeriodStart(),
                account.getStakedId(),
                account.isDeclinedReward(),
                account.isReceiverSigRequired(),
                account.getHeadTokenId(),
                account.getHeadNftTokenNum(),
                account.getHeadNftSerialNum(),
                account.getNftsOwned(),
                account.getMaxAutomaticAssociations(),
                account.getUsedAutoAssociations(),
                account.getNumAssociations(),
                account.isSmartContract(),
                account.getNumPositiveBalances(),
                account.getEthereumNonce(),
                account.totalStakeAtStartOfLastRewardedPeriod(),
                account.getAutoRenewAccount() != null
                        ? account.getAutoRenewAccount().num()
                        : 0,
                account.getAutoRenewSecs(),
                account.getNumContractKvPairs(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                account.getNumTreasuryTitles(),
                account.isExpiredAndPendingRemoval());
    }
}
