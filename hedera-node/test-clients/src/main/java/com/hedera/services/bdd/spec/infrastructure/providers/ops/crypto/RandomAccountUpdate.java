/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.spec.infrastructure.providers.ops.crypto;

import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoUpdate;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static java.util.Collections.EMPTY_LIST;

import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.infrastructure.EntityNameProvider;
import com.hedera.services.bdd.spec.infrastructure.OpProvider;
import com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoUpdate;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.util.List;
import java.util.Optional;

public class RandomAccountUpdate implements OpProvider {
    private final EntityNameProvider<Key> keys;
    private final EntityNameProvider<AccountID> accounts;

    private final ResponseCodeEnum[] permissibleOutcomes = standardOutcomesAnd(ACCOUNT_DELETED, INVALID_ACCOUNT_ID);

    public RandomAccountUpdate(EntityNameProvider<Key> keys, EntityNameProvider<AccountID> accounts) {
        this.keys = keys;
        this.accounts = accounts;
    }

    @Override
    public List<HapiSpecOperation> suggestedInitializers() {
        return EMPTY_LIST;
    }

    @Override
    public Optional<HapiSpecOperation> get() {
        final var target = accounts.getQualifying();
        if (target.isEmpty()) {
            return Optional.empty();
        }
        final var newKey = keys.getQualifying();

        HapiCryptoUpdate op = cryptoUpdate(target.get())
                .key(newKey.get())
                .hasPrecheckFrom(STANDARD_PERMISSIBLE_PRECHECKS)
                .hasKnownStatusFrom(permissibleOutcomes);
        return Optional.of(op);
    }
}
