/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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

import com.hedera.services.ledger.accounts.TestAccount;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class HashMapTestAccounts implements BackingStore<Long, TestAccount> {
    private Map<Long, TestAccount> testAccounts = new HashMap<>();

    @Override
    public TestAccount getRef(Long id) {
        return testAccounts.get(id);
    }

    @Override
    public TestAccount getImmutableRef(Long id) {
        return testAccounts.get(id);
    }

    @Override
    public void put(Long id, TestAccount testAccount) {
        testAccounts.put(id, testAccount);
    }

    @Override
    public void remove(Long id) {
        testAccounts.remove(id);
    }

    @Override
    public boolean contains(Long id) {
        return testAccounts.containsKey(id);
    }

    @Override
    public Set<Long> idSet() {
        return testAccounts.keySet();
    }

    @Override
    public long size() {
        return testAccounts.size();
    }
}
