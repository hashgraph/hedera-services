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

import static com.hedera.node.app.service.evm.utils.EthSigsUtils.recoverAddressFromPubKey;
import static com.hedera.services.bdd.spec.infrastructure.providers.ops.hollow.RandomKey.KEY_PREFIX;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ALIAS_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.infrastructure.HapiSpecRegistry;
import com.hedera.services.bdd.spec.infrastructure.OpProvider;
import com.hedera.services.bdd.spec.infrastructure.providers.names.RegistrySourcedNameProvider;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Key;
import java.util.Optional;

public class RandomHollowAccount implements OpProvider {
    public static final String ACCOUNT_SUFFIX = "#";
    public static final int DEFAULT_CEILING_NUM = 100;
    public static final long INITIAL_BALANCE = 1_000_000_000L;
    private int ceilingNum = DEFAULT_CEILING_NUM;
    private final HapiSpecRegistry registry;

    private final RegistrySourcedNameProvider<Key> keys;
    private final RegistrySourcedNameProvider<AccountID> accounts;

    public RandomHollowAccount(
            HapiSpecRegistry registry,
            RegistrySourcedNameProvider<Key> keys,
            RegistrySourcedNameProvider<AccountID> accounts) {
        this.registry = registry;
        this.keys = keys;
        this.accounts = accounts;
    }

    public RandomHollowAccount ceiling(int n) {
        ceilingNum = n;
        return this;
    }

    @Override
    public Optional<HapiSpecOperation> get() {
        if (accounts.numPresent() >= ceilingNum * 2) {
            return Optional.empty();
        }

        return randomKey().map(this::generateHollowAccount);
    }

    private Optional<String> randomKey() {
        return keys.getQualifying()
                .filter(k -> !k.endsWith(ACCOUNT_SUFFIX))
                .filter(k -> k.startsWith(KEY_PREFIX))
                .filter(k -> !registry.hasAccountId(k + ACCOUNT_SUFFIX));
    }

    private HapiSpecOperation generateHollowAccount(String keyName) {
        final ByteString evmAddress = getEvmAddress(keyName);

        return cryptoCreate(keyName + ACCOUNT_SUFFIX)
                .hasPrecheckFrom(OK, INVALID_ALIAS_KEY)
                .hasKnownStatusFrom(SUCCESS, INVALID_ALIAS_KEY)
                .evmAddress(evmAddress)
                .balance(INITIAL_BALANCE)
                .noLogging();
    }

    private ByteString getEvmAddress(String keyName) {
        final var ecdsaKey = this.registry.getKey(keyName).getECDSASecp256K1().toByteArray();

        return ByteString.copyFrom(recoverAddressFromPubKey(ecdsaKey));
    }
}
