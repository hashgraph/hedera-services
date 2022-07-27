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

import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class HashMapBackingAccounts implements BackingStore<AccountID, MerkleAccount> {
    final AccountID GENESIS = IdUtils.asAccount("0.0.2");
    final long GENESIS_BALANCE = 50_000_000_000L;

    private Map<AccountID, MerkleAccount> accounts = new HashMap<>();

    {
        MerkleAccount genesisAccount = new MerkleAccount();
        try {
            genesisAccount.setBalance(GENESIS_BALANCE);
        } catch (Exception ignore) {
        }
        accounts.put(GENESIS, genesisAccount);
    }

    @Override
    public MerkleAccount getRef(AccountID id) {
        return accounts.get(id);
    }

    @Override
    public void put(AccountID id, MerkleAccount account) {
        accounts.put(id, account);
    }

    @Override
    public boolean contains(AccountID id) {
        return accounts.containsKey(id);
    }

    @Override
    public void remove(AccountID id) {
        accounts.remove(id);
    }

    @Override
    public Set<AccountID> idSet() {
        return accounts.keySet();
    }

    @Override
    public long size() {
        return accounts.size();
    }

    @Override
    public MerkleAccount getImmutableRef(AccountID id) {
        return accounts.get(id);
    }
}
