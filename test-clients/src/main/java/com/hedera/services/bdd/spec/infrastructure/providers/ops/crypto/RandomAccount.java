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

import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;

import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.infrastructure.EntityNameProvider;
import com.hedera.services.bdd.spec.infrastructure.OpProvider;
import com.hedera.services.bdd.spec.infrastructure.providers.names.RegistrySourcedNameProvider;
import com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoCreate;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

public class RandomAccount implements OpProvider {
    public static final int DEFAULT_CEILING_NUM = 100;
    public static final long INITIAL_BALANCE = 1_000_000_000L;
    static final long SEND_THRESHOLD = INITIAL_BALANCE / 50;

    private int ceilingNum = DEFAULT_CEILING_NUM;

    private final AtomicInteger opNo = new AtomicInteger();
    private final EntityNameProvider<Key> keys;
    private final RegistrySourcedNameProvider<AccountID> accounts;
    private final ResponseCodeEnum[] permissibleOutcomes = standardOutcomesAnd(INVALID_ACCOUNT_ID);

    public RandomAccount(
            EntityNameProvider<Key> keys, RegistrySourcedNameProvider<AccountID> accounts) {
        this.keys = keys;
        this.accounts = accounts;
    }

    public RandomAccount ceiling(int n) {
        ceilingNum = n;
        return this;
    }

    @Override
    public List<HapiSpecOperation> suggestedInitializers() {
        return List.of(newKeyNamed(my("simpleKey")));
    }

    @Override
    public Optional<HapiSpecOperation> get() {
        if (accounts.numPresent() >= ceilingNum) {
            return Optional.empty();
        }

        Optional<String> key = keys.getQualifying();
        if (key.isEmpty()) {
            return Optional.empty();
        }

        int id = opNo.getAndIncrement();
        HapiCryptoCreate op =
                cryptoCreate(my("account" + id))
                        .key(key.get())
                        .memo("randomlycreated" + id)
                        .balance(INITIAL_BALANCE)
                        .sendThreshold(SEND_THRESHOLD)
                        .hasPrecheckFrom(STANDARD_PERMISSIBLE_PRECHECKS)
                        .hasKnownStatusFrom(permissibleOutcomes);
        return Optional.of(op);
    }

    private String my(String opName) {
        return unique(opName, RandomAccount.class);
    }
}
