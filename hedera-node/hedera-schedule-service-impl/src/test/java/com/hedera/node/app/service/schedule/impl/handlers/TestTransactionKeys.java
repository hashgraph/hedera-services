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

package com.hedera.node.app.service.schedule.impl.handlers;

import static java.util.Collections.emptySet;

import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.node.app.spi.workflows.TransactionKeys;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Set;

class TestTransactionKeys implements TransactionKeys {
    private final Key payerKey;
    private final Set<Key> optionalKeys;
    private final Set<Key> requiredKeys;

    TestTransactionKeys(final Key payer, final Set<Key> required, final Set<Key> optional) {
        payerKey = payer;
        requiredKeys = required;
        optionalKeys = optional;
    }

    @Override
    @NonNull
    public Key payerKey() {
        return payerKey;
    }

    @Override
    @NonNull
    public Set<Key> requiredNonPayerKeys() {
        return requiredKeys;
    }

    @Override
    public Set<Account> requiredHollowAccounts() {
        return emptySet();
    }

    @Override
    public Set<Key> optionalNonPayerKeys() {
        return optionalKeys;
    }

    @Override
    public Set<Account> optionalHollowAccounts() {
        return null;
    }
}
