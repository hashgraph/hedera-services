/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
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

import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.infrastructure.EntityNameProvider;
import com.hedera.services.bdd.spec.infrastructure.OpProvider;
import com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoUpdate;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.util.Optional;

public class RandomAccountUpdate implements OpProvider {
    protected final EntityNameProvider keys;
    protected final EntityNameProvider accounts;

    private final ResponseCodeEnum[] permissibleOutcomes = standardOutcomesAnd(ACCOUNT_DELETED, INVALID_ACCOUNT_ID);

    public RandomAccountUpdate(EntityNameProvider keys, EntityNameProvider accounts) {
        this.keys = keys;
        this.accounts = accounts;
    }

    @Override
    public Optional<HapiSpecOperation> get() {
        final var target = accounts.getQualifying();
        final var newKey = keys.getQualifying();
        if (target.isEmpty() || newKey.isEmpty()) {
            return Optional.empty();
        }

        HapiCryptoUpdate op = cryptoUpdate(target.get())
                .key(newKey.get())
                .payingWith(newKey.get())
                .signedBy(newKey.get())
                .hasPrecheckFrom(STANDARD_PERMISSIBLE_PRECHECKS)
                .hasKnownStatusFrom(permissibleOutcomes);
        return Optional.of(op);
    }
}
