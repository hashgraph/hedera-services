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

import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromAccountToAlias;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overridingThree;
import static com.hedera.services.bdd.suites.HapiSuite.CHAIN_ID_PROP;
import static com.hedera.services.bdd.suites.leaky.LeakyCryptoTestsSuite.*;
import static com.hedera.services.bdd.suites.regression.factories.RegressionProviderFactory.intPropOrElse;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.infrastructure.OpProvider;
import com.hedera.services.bdd.spec.infrastructure.providers.names.RegistrySourcedNameProvider;
import com.hedera.services.bdd.spec.infrastructure.providers.ops.BiasedDelegatingProvider;
import com.hedera.services.bdd.spec.infrastructure.providers.ops.crypto.TransferToRandomLazyCreate;
import com.hedera.services.bdd.spec.infrastructure.selectors.RandomSelector;
import com.hedera.services.bdd.spec.keys.SigControl;
import com.hederahashgraph.api.proto.java.Key;
import java.util.function.Function;
import java.util.stream.IntStream;

public class EvmAddressFuzzingFactory {
    private static final int NUM_DISTINCT_ECDSA_KEYS = 42;
    private static final String RANDOM_TRANSFER_BIAS = "randomTransfer.bias";

    private EvmAddressFuzzingFactory() {}

    public static HapiSpecOperation[] initOperations() {
        return new HapiSpecOperation[] {
            overridingThree(
                    CHAIN_ID_PROP, "298", LAZY_CREATE_PROPERTY_NAME, "true", CONTRACTS_EVM_VERSION_PROP, V_0_34),
            newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
            cryptoCreate(RELAYER).balance(6 * ONE_MILLION_HBARS).withRecharging(),
            cryptoTransfer(tinyBarsFromAccountToAlias(GENESIS, SECP_256K1_SOURCE_KEY, ONE_HUNDRED_HBARS))
                    .via(AUTO_ACCOUNT)
        };
    }

    public static Function<HapiSpec, OpProvider> evmAddressFuzzing(final String resource) {
        return spec -> {
            final var props = RegressionProviderFactory.propsFrom(resource);

            final var keys = new RegistrySourcedNameProvider<>(Key.class, spec.registry(), new RandomSelector());

            return new BiasedDelegatingProvider()
                    .shouldLogNormalFlow(true)
                    /* --- <inventory> --- */
                    .withInitialization(onlyEcdsaKeys())
                    /* ----- CRYPTO ----- */
                    .withOp(
                            new TransferToRandomLazyCreate(spec.registry(), keys),
                            intPropOrElse(RANDOM_TRANSFER_BIAS, 0, props));
        };
    }

    private static HapiSpecOperation[] onlyEcdsaKeys() {
        return IntStream.range(0, NUM_DISTINCT_ECDSA_KEYS)
                .mapToObj(i -> newKeyNamed("Fuzz#" + i).shape(SigControl.SECP256K1_ON))
                .toArray(HapiSpecOperation[]::new);
    }
}
