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
package com.hedera.services.bdd.spec.infrastructure.providers.ops.hollow;

import static com.hedera.services.bdd.spec.infrastructure.providers.ops.hollow.RandomHollowAccount.ACCOUNT_SUFFIX;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BUSY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.PAYER_ACCOUNT_NOT_FOUND;

import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.infrastructure.HapiSpecRegistry;
import com.hedera.services.bdd.spec.infrastructure.OpProvider;
import com.hedera.services.bdd.spec.infrastructure.providers.names.RegistrySourcedNameProvider;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;

import java.util.Optional;

abstract class RandomOperationSignedByHollowAccount implements OpProvider {
    private final HapiSpecRegistry registry;

    private final RegistrySourcedNameProvider<AccountID> accounts;

    protected final ResponseCodeEnum[] permissiblePrechecks =
            standardPrechecksAnd(BUSY, PAYER_ACCOUNT_NOT_FOUND);
    protected final ResponseCodeEnum[] permissibleOutcomes = STANDARD_PERMISSIBLE_OUTCOMES;

    protected RandomOperationSignedByHollowAccount(
            HapiSpecRegistry registry, RegistrySourcedNameProvider<AccountID> accounts) {
        this.registry = registry;
        this.accounts = accounts;
    }

    @Override
    public Optional<HapiSpecOperation> get() {
        return randomHollowAccountKey().map(this::generateOpSignedBy);
    }

    protected Optional<String> randomHollowAccountKey() {
        return accounts.getQualifying()
                .filter(a -> a.endsWith(ACCOUNT_SUFFIX))
                .map(this::keyFromAccount);
    }

    private String keyFromAccount(String account) {
        final var key = account.replaceAll(ACCOUNT_SUFFIX + "$", "");
        final AccountID fromAccount = registry.getAccountID(account);
        registry.saveAccountId(key, fromAccount);
        return key;
    }

    protected abstract HapiSpecOperation generateOpSignedBy(String keyName);
}
