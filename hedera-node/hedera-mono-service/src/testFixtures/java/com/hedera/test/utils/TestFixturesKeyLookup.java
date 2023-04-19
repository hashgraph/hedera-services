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
import com.hedera.hapi.node.state.token.Account;
import com.hedera.node.app.service.mono.state.virtual.EntityNumVirtualKey;
import com.hedera.node.app.spi.accounts.AccountAccess;
import com.hedera.node.app.spi.state.ReadableKVState;
import com.hedera.node.app.spi.state.ReadableStates;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

public class TestFixturesKeyLookup implements AccountAccess {
    private final ReadableKVState<String, Long> aliases;
    private final ReadableKVState<EntityNumVirtualKey, Account> accounts;

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

    private Account getNewAccount(long num, Bytes alias, Account account) {
        return account.copyBuilder().alias(alias).accountNumber(num).build();
    }
}
