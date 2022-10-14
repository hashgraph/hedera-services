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
package com.hedera.services.ledger.backing;

import static com.hedera.services.utils.EntityNum.fromAccountId;

import com.hedera.services.state.migration.AccountStorageAdapter;
import com.hedera.services.state.migration.HederaAccount;
import com.hedera.services.state.migration.RecordsStorageAdapter;
import com.hederahashgraph.api.proto.java.AccountID;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class BackingAccounts implements BackingStore<AccountID, HederaAccount> {
    private Set<AccountID> existingAccounts = new HashSet<>();
    private final Supplier<AccountStorageAdapter> delegate;
    private final Supplier<RecordsStorageAdapter> payerRecords;

    @Inject
    public BackingAccounts(
            final Supplier<AccountStorageAdapter> delegate,
            final Supplier<RecordsStorageAdapter> payerRecords) {
        this.delegate = delegate;
        this.payerRecords = payerRecords;
    }

    @Override
    public void rebuildFromSources() {
        existingAccounts.clear();
        delegate.get().forEach((num, account) -> existingAccounts.add(num.toGrpcAccountId()));
    }

    @Override
    public HederaAccount getRef(final AccountID id) {
        return delegate.get().getForModify(fromAccountId(id));
    }

    @Override
    public void put(final AccountID id, final HederaAccount account) {
        if (!existingAccounts.contains(id)) {
            final var num = fromAccountId(id);
            delegate.get().put(num, account);
            existingAccounts.add(id);
            payerRecords.get().prepForPayer(num);
        }
    }

    @Override
    public boolean contains(final AccountID id) {
        return existingAccounts.contains(id);
    }

    @Override
    public void remove(final AccountID id) {
        existingAccounts.remove(id);
        final var num = fromAccountId(id);
        delegate.get().remove(num);
        payerRecords.get().forgetPayer(num);
    }

    @Override
    public Set<AccountID> idSet() {
        return existingAccounts;
    }

    @Override
    public long size() {
        return delegate.get().size();
    }

    @Override
    public HederaAccount getImmutableRef(AccountID id) {
        return delegate.get().get(fromAccountId(id));
    }

    /* ---  Only used for unit tests --- */
    Set<AccountID> getExistingAccounts() {
        return existingAccounts;
    }

    public Supplier<AccountStorageAdapter> getDelegate() {
        return delegate;
    }
}
