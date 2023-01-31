/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.state.expiry.classification;

import com.hedera.services.state.migration.AccountStorageAdapter;
import com.hedera.services.state.migration.HederaAccount;
import com.hedera.services.utils.EntityNum;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class EntityLookup {
    private final Supplier<AccountStorageAdapter> accounts;

    @Inject
    public EntityLookup(final Supplier<AccountStorageAdapter> accounts) {
        this.accounts = accounts;
    }

    public HederaAccount getImmutableAccount(final EntityNum account) {
        return accounts.get().get(account);
    }

    public HederaAccount getMutableAccount(final EntityNum account) {
        return accounts.get().getForModify(account);
    }
}
