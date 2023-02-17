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

package com.hedera.services.bdd.suites.regression.factories;

import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.suites.regression.factories.RegressionProviderFactory.intPropOrElse;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.infrastructure.OpProvider;
import com.hedera.services.bdd.spec.infrastructure.meta.InitialAccountIdentifiers;
import com.hedera.services.bdd.spec.infrastructure.providers.names.RegistrySourcedNameProvider;
import com.hedera.services.bdd.spec.infrastructure.providers.ops.BiasedDelegatingProvider;
import com.hedera.services.bdd.spec.infrastructure.providers.ops.crypto.RandomAccount;
import com.hedera.services.bdd.spec.infrastructure.providers.ops.crypto.RandomAccountUpdate;
import com.hedera.services.bdd.spec.infrastructure.selectors.RandomSelector;
import com.hedera.services.bdd.spec.keys.SigControl;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Key;
import java.util.function.Function;
import java.util.stream.IntStream;

public class IdFuzzingProviderFactory {
    /**
     * How many different ECDSA keys we will re-use as we continually create, update, and delete
     * accounts with random {@link InitialAccountIdentifiers} based on this fixed set of keys.
     */
    private static final int NUM_DISTINCT_ECDSA_KEYS = 42;

    public static Function<HapiSpec, OpProvider> idFuzzingWith(final String resource) {
        return spec -> {
            final var props = RegressionProviderFactory.propsFrom(resource);

            final var keys = new RegistrySourcedNameProvider<>(Key.class, spec.registry(), new RandomSelector());
            final var accounts =
                    new RegistrySourcedNameProvider<>(AccountID.class, spec.registry(), new RandomSelector());

            return new BiasedDelegatingProvider()
                    /* --- <inventory> --- */
                    .withInitialization(onlyEcdsaKeys())
                    /* ----- CRYPTO ----- */
                    .withOp(
                            new RandomAccount(keys, accounts, true)
                                    .ceiling(intPropOrElse(
                                            "randomAccount.ceilingNum", RandomAccount.DEFAULT_CEILING_NUM, props)),
                            intPropOrElse("randomAccount.bias", 0, props))
                    .withOp(
                            new RandomAccountUpdate(keys, accounts),
                            intPropOrElse("randomAccountUpdate.bias", 0, props));
        };
    }

    private static HapiSpecOperation[] onlyEcdsaKeys() {
        return IntStream.range(0, NUM_DISTINCT_ECDSA_KEYS)
                .mapToObj(i -> newKeyNamed("Fuzz#" + i).shape(SigControl.SECP256K1_ON))
                .toArray(HapiSpecOperation[]::new);
    }
}
