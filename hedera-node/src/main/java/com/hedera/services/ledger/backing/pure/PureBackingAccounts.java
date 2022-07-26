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
package com.hedera.services.ledger.backing.pure;

import static com.hedera.services.utils.EntityNum.fromAccountId;
import static java.util.stream.Collectors.toSet;

import com.hedera.services.ledger.backing.BackingStore;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.AccountID;
import com.swirlds.merkle.map.MerkleMap;
import java.util.Set;
import java.util.function.Supplier;

public class PureBackingAccounts implements BackingStore<AccountID, MerkleAccount> {
    private final Supplier<MerkleMap<EntityNum, MerkleAccount>> delegate;

    public PureBackingAccounts(Supplier<MerkleMap<EntityNum, MerkleAccount>> delegate) {
        this.delegate = delegate;
    }

    @Override
    public MerkleAccount getRef(AccountID id) {
        return delegate.get().get(fromAccountId(id));
    }

    @Override
    public MerkleAccount getImmutableRef(AccountID id) {
        return delegate.get().get(fromAccountId(id));
    }

    @Override
    public void put(AccountID id, MerkleAccount account) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void remove(AccountID id) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean contains(AccountID id) {
        return delegate.get().containsKey(fromAccountId(id));
    }

    @Override
    public Set<AccountID> idSet() {
        return delegate.get().keySet().stream().map(EntityNum::toGrpcAccountId).collect(toSet());
    }

    @Override
    public long size() {
        return delegate.get().size();
    }
}
