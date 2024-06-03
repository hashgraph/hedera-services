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

package com.hedera.test.utils;

import static com.hedera.hapi.node.base.AccountID.AccountOneOfType.ACCOUNT_NUM;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.state.primitives.ProtoBytes;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.state.spi.ReadableKVState;
import com.swirlds.state.spi.ReadableStates;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

public class TestFixturesKeyLookup implements ReadableAccountStore {
    private final ReadableKVState<ProtoBytes, AccountID> aliases;
    private final ReadableKVState<AccountID, Account> accounts;

    public TestFixturesKeyLookup(@NonNull final ReadableStates states) {
        this.accounts = states.get("ACCOUNTS");
        this.aliases = states.get("ALIASES");
    }

    @Nullable
    @Override
    public Account getAccountById(@NonNull AccountID accountID) {
        return accountID.account().kind() == ACCOUNT_NUM ? accounts.get(accountID) : null;
    }

    @Nullable
    @Override
    public Account getAliasedAccountById(@NonNull final AccountID accountID) {
        final var alias = accountID.alias();
        if (alias != null && alias.length() > 0) {
            final var num = aliases.get(new ProtoBytes(alias));
            if (num == null) {
                return null;
            } else {
                final var account = accounts.get(num);
                return account == null ? null : getNewAccount(num.accountNum(), alias, account);
            }
        } else if (!accountID.hasAccountNum()) {
            return null;
        } else {
            final long num = accountID.accountNumOrThrow();
            final var account =
                    accounts.get(AccountID.newBuilder().accountNum(num).build());
            return account == null ? null : getNewAccount(num, Bytes.EMPTY, account);
        }
    }

    @Override
    public long getNumberOfAccounts() {
        return accounts.size();
    }

    @Override
    public long sizeOfAccountState() {
        return accounts.size();
    }

    private static Account getNewAccount(long num, Bytes alias, Account account) {
        return account.copyBuilder()
                .alias(alias)
                .accountId(account.accountId().copyBuilder().accountNum(num))
                .build();
    }

    @Override
    public boolean containsAlias(@NonNull Bytes alias) {
        return aliases.contains(new ProtoBytes(alias));
    }

    @Override
    public boolean contains(@NonNull AccountID accountID) {
        return accounts.contains(accountID);
    }

    @Override
    @Nullable
    public AccountID getAccountIDByAlias(@NonNull final Bytes alias) {
        return aliases.get(new ProtoBytes(alias));
    }
}
