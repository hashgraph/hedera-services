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

import static com.hedera.services.bdd.spec.transactions.TxnVerbs.*;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HUNDRED_HBARS;
import static com.hedera.services.bdd.suites.HapiSuite.SECP_256K1_SHAPE;
import static com.hedera.services.bdd.suites.regression.factories.RegressionProviderFactory.intPropOrElse;

import com.hedera.services.bdd.spec.HapiPropertySource;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.infrastructure.OpProvider;
import com.hedera.services.bdd.spec.infrastructure.providers.names.RegistrySourcedNameProvider;
import com.hedera.services.bdd.spec.infrastructure.providers.ops.BiasedDelegatingProvider;
import com.hedera.services.bdd.spec.infrastructure.providers.ops.precompile.RandomLazyCreateFungibleTransfer;
import com.hedera.services.bdd.spec.infrastructure.selectors.RandomSelector;
import com.hedera.services.bdd.spec.keys.SigControl;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.TokenType;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.IntStream;

public class LazyCreatePrecompileFuzzingFactory {
    public static final String FUNGIBLE_TOKEN = "fungibleToken";
    public static final String NON_FUNGIBLE_TOKEN = "nonFungibleToken";
    public static final String MULTI_KEY = "purpose";
    public static final String OWNER = "owner";
    public static final String TRANSFER_TO_ALIAS_PRECOMPILE_CONTRACT = "PrecompileAliasXfer";
    public static final String TOKEN_TREASURY = "treasury";
    public static final String ECDSA_KEY = "abcdECDSAkey";
    public static final String TRANSFER_TOKEN_TXN = "transferTokenTxn";
    private static final int NUM_DISTINCT_ECDSA_KEYS = 42;

    private LazyCreatePrecompileFuzzingFactory() {}

    public static HapiSpecOperation[] initOperationsTransferFungibleToken() {
        final AtomicReference<String> tokenAddr = new AtomicReference<>();

        return new HapiSpecOperation[] {
            newKeyNamed(ECDSA_KEY).shape(SECP_256K1_SHAPE),
            newKeyNamed(MULTI_KEY),
            cryptoCreate(TOKEN_TREASURY),
            cryptoCreate(OWNER).balance(100 * ONE_HUNDRED_HBARS),
            tokenCreate(FUNGIBLE_TOKEN)
                    .tokenType(TokenType.FUNGIBLE_COMMON)
                    .initialSupply(5)
                    .treasury(TOKEN_TREASURY)
                    .adminKey(MULTI_KEY)
                    .supplyKey(MULTI_KEY)
                    .exposingCreatedIdTo(id ->
                            tokenAddr.set(HapiPropertySource.asHexedSolidityAddress(HapiPropertySource.asToken(id)))),
            uploadInitCode(TRANSFER_TO_ALIAS_PRECOMPILE_CONTRACT),
            contractCreate(TRANSFER_TO_ALIAS_PRECOMPILE_CONTRACT),
            tokenAssociate(OWNER, List.of(FUNGIBLE_TOKEN)),
            cryptoTransfer(moving(5, FUNGIBLE_TOKEN).between(TOKEN_TREASURY, OWNER))
        };
    }

    public static Function<HapiSpec, OpProvider> transferFungibleTokenFuzzingWith(final String resource) {
        return spec -> {
            final var props = RegressionProviderFactory.propsFrom(resource);

            final var keys = new RegistrySourcedNameProvider<>(Key.class, spec.registry(), new RandomSelector());

            return new BiasedDelegatingProvider()
                    .shouldLogNormalFlow(true)
                    .withInitialization(onlyEcdsaKeys())
                    .withOp(
                            new RandomLazyCreateFungibleTransfer(spec.registry(), keys),
                            intPropOrElse("randomTransfer.bias", 0, props));
        };
    }

    private static HapiSpecOperation[] onlyEcdsaKeys() {
        return IntStream.range(0, NUM_DISTINCT_ECDSA_KEYS)
                .mapToObj(i -> newKeyNamed("Fuzz#" + i).shape(SigControl.SECP256K1_ON))
                .toArray(HapiSpecOperation[]::new);
    }
}
