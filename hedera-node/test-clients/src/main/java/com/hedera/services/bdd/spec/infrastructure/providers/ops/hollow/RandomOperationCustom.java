/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.PAYER_ACCOUNT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.PAYER_ACCOUNT_NOT_FOUND;

import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.infrastructure.HapiSpecRegistry;
import com.hedera.services.bdd.spec.infrastructure.OpProvider;
import com.hedera.services.bdd.spec.infrastructure.providers.names.RegistrySourcedNameProvider;
import com.hedera.services.bdd.spec.transactions.HapiTxnOp;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.util.Optional;

abstract class RandomOperationCustom<T extends HapiTxnOp<T>> implements OpProvider {

    protected final ResponseCodeEnum[] permissiblePrechecks =
            standardPrechecksAnd(PAYER_ACCOUNT_NOT_FOUND, ACCOUNT_DELETED, PAYER_ACCOUNT_DELETED);

    private final HapiSpecRegistry registry;

    private final RegistrySourcedNameProvider<AccountID> accounts;

    protected RandomOperationCustom(HapiSpecRegistry registry, RegistrySourcedNameProvider<AccountID> accounts) {
        this.registry = registry;
        this.accounts = accounts;
    }

    @Override
    public Optional<HapiSpecOperation> get() {
        return randomHollowAccountKey().map(this::generateOpSignedBy);
    }

    private Optional<String> randomHollowAccountKey() {
        return accounts.getQualifying();
    }

    protected abstract HapiSpecOperation generateOpSignedBy(String keyName);

    protected abstract HapiTxnOp<T> hapiTxnOp(String keyName);
}
