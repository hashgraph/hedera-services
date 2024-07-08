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

import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoUpdate;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;

import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.infrastructure.EntityNameProvider;
import com.hedera.services.bdd.spec.infrastructure.providers.ops.crypto.RandomAccountUpdate;
import com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoUpdate;
import java.util.Optional;

public class RandomAccountUpdateHollowAccount extends RandomAccountUpdate {
    private final String[] signers;

    public RandomAccountUpdateHollowAccount(EntityNameProvider keys, EntityNameProvider accounts, String... signers) {
        super(keys, accounts);
        this.signers = signers;
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
                .hasKnownStatusFrom(INVALID_SIGNATURE)
                .signedBy(signers);

        return Optional.of(op);
    }
}
