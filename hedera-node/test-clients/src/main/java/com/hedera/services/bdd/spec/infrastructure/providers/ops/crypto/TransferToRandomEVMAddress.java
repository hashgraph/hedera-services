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

package com.hedera.services.bdd.spec.infrastructure.providers.ops.crypto;

import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromAccountToAlias;
import static com.hedera.services.bdd.suites.HapiSuite.GENESIS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.infrastructure.EntityNameProvider;
import com.hedera.services.bdd.spec.infrastructure.OpProvider;
import com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer;
import com.hederahashgraph.api.proto.java.Key;
import java.util.Optional;

public class TransferToRandomEVMAddress implements OpProvider {
    private final EntityNameProvider<Key> keys;

    public TransferToRandomEVMAddress(EntityNameProvider<Key> keys) {
        this.keys = keys;
    }

    @Override
    public Optional<HapiSpecOperation> get() {
        final var involvedKey = keys.getQualifying();
        if (involvedKey.isEmpty()) {
            return Optional.empty();
        }

        var to = involvedKey.get();
        HapiCryptoTransfer op = cryptoTransfer(tinyBarsFromAccountToAlias(GENESIS, to, 5, true))
                .hasKnownStatusFrom(SUCCESS)
                .payingWith(UNIQUE_PAYER_ACCOUNT);

        return Optional.of(op);
    }
}
