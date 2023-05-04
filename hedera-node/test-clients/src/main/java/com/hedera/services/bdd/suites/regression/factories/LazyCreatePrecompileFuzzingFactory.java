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

import static com.hedera.services.bdd.spec.infrastructure.OpProvider.UNIQUE_PAYER_ACCOUNT;
import static com.hedera.services.bdd.spec.infrastructure.OpProvider.UNIQUE_PAYER_ACCOUNT_INITIAL_BALANCE;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.*;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.movingUnique;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HUNDRED_HBARS;
import static com.hedera.services.bdd.suites.HapiSuite.SECP_256K1_SHAPE;
import static com.hedera.services.bdd.suites.regression.factories.RegressionProviderFactory.intPropOrElse;
import static com.hedera.services.bdd.suites.utils.ECDSAKeysUtils.onlyEcdsaKeys;

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.spec.HapiPropertySource;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.infrastructure.OpProvider;
import com.hedera.services.bdd.spec.infrastructure.providers.names.RegistrySourcedNameProvider;
import com.hedera.services.bdd.spec.infrastructure.providers.ops.BiasedDelegatingProvider;
import com.hedera.services.bdd.spec.infrastructure.providers.ops.precompile.RandomLazyCreateERC20Transfer;
import com.hedera.services.bdd.spec.infrastructure.providers.ops.precompile.RandomLazyCreateFungibleTransfer;
import com.hedera.services.bdd.spec.infrastructure.providers.ops.precompile.RandomLazyCreateNonFungibleTransfer;
import com.hedera.services.bdd.spec.infrastructure.selectors.RandomSelector;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.TokenType;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

public class LazyCreatePrecompileFuzzingFactory {
    public static final String FUNGIBLE_TOKEN = "fungibleToken";
    public static final long INITIAL_SUPPLY = 1_000_000_000L;
    public static final String ERC_FUNGIBLE_TOKEN = "ercFungibleToken";
    public static final String NON_FUNGIBLE_TOKEN = "nonFungibleToken";
    public static final String MULTI_KEY = "purpose";
    public static final String OWNER = "owner";
    public static final String TRANSFER_TO_ALIAS_PRECOMPILE_CONTRACT = "PrecompileAliasXfer";
    public static final String TOKEN_TREASURY = "treasury";
    public static final String TOKEN_TREASURY_ERC = "treasuryErc";
    public static final String ECDSA_KEY = "abcdECDSAkey";
    public static final String TRANSFER_TOKEN_TXN = "transferTokenTxn";
    public static final String TRANSFER_NFT_TXN = "transferNFTTxn";
    public static final String ERC_20_CONTRACT = "ERC20Contract";
    private static final String SPENDER = "spender";
    private static final String FIRST = "FIRST";
    public static final ByteString FIRST_META = ByteString.copyFrom(FIRST.getBytes(StandardCharsets.UTF_8));
    public static final ByteString SECOND_META = ByteString.copyFrom(FIRST.getBytes(StandardCharsets.UTF_8));
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
                    .initialSupply(INITIAL_SUPPLY)
                    .treasury(TOKEN_TREASURY)
                    .adminKey(MULTI_KEY)
                    .supplyKey(MULTI_KEY)
                    .exposingCreatedIdTo(id ->
                            tokenAddr.set(HapiPropertySource.asHexedSolidityAddress(HapiPropertySource.asToken(id)))),
            uploadInitCode(TRANSFER_TO_ALIAS_PRECOMPILE_CONTRACT),
            contractCreate(TRANSFER_TO_ALIAS_PRECOMPILE_CONTRACT),
            tokenAssociate(OWNER, List.of(FUNGIBLE_TOKEN)),
            cryptoTransfer(moving(INITIAL_SUPPLY, FUNGIBLE_TOKEN).between(TOKEN_TREASURY, OWNER))
        };
    }

    public static HapiSpecOperation[] initOperationsTransferNonFungibleToken() {
        return new HapiSpecOperation[] {
            newKeyNamed(ECDSA_KEY).shape(SECP_256K1_SHAPE),
            newKeyNamed(MULTI_KEY),
            cryptoCreate(OWNER).balance(100 * ONE_HUNDRED_HBARS),
            cryptoCreate(SPENDER),
            cryptoCreate(TOKEN_TREASURY),
            tokenCreate(NON_FUNGIBLE_TOKEN)
                    .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                    .initialSupply(0)
                    .treasury(TOKEN_TREASURY)
                    .adminKey(MULTI_KEY)
                    .supplyKey(MULTI_KEY),
            uploadInitCode(TRANSFER_TO_ALIAS_PRECOMPILE_CONTRACT),
            contractCreate(TRANSFER_TO_ALIAS_PRECOMPILE_CONTRACT),
            tokenAssociate(OWNER, NON_FUNGIBLE_TOKEN),
            tokenAssociate(SPENDER, NON_FUNGIBLE_TOKEN),
            tokenAssociate(TRANSFER_TO_ALIAS_PRECOMPILE_CONTRACT, NON_FUNGIBLE_TOKEN),
            mintToken(NON_FUNGIBLE_TOKEN, List.of(FIRST_META, SECOND_META)),
            cryptoTransfer(movingUnique(NON_FUNGIBLE_TOKEN, 1L, 2L).between(TOKEN_TREASURY, OWNER))
        };
    }

    public static HapiSpecOperation[] initOperationsTransferERC20() {
        final AtomicReference<String> tokenAddr = new AtomicReference<>();
        return new HapiSpecOperation[] {
            newKeyNamed(ECDSA_KEY).shape(SECP_256K1_SHAPE),
            newKeyNamed(MULTI_KEY),
            cryptoCreate(TOKEN_TREASURY),
            tokenCreate(ERC_FUNGIBLE_TOKEN)
                    .tokenType(TokenType.FUNGIBLE_COMMON)
                    .initialSupply(INITIAL_SUPPLY)
                    .treasury(TOKEN_TREASURY)
                    .adminKey(MULTI_KEY)
                    .supplyKey(MULTI_KEY)
                    .exposingCreatedIdTo(id ->
                            tokenAddr.set(HapiPropertySource.asHexedSolidityAddress(HapiPropertySource.asToken(id)))),
            uploadInitCode(ERC_20_CONTRACT),
            contractCreate(ERC_20_CONTRACT),
            tokenAssociate(ERC_20_CONTRACT, List.of(ERC_FUNGIBLE_TOKEN)),
            cryptoTransfer(moving(INITIAL_SUPPLY, ERC_FUNGIBLE_TOKEN).between(TOKEN_TREASURY, ERC_20_CONTRACT)),
        };
    }

    public static Function<HapiSpec, OpProvider> transferFungibleTokenFuzzingWith(final String resource) {
        return spec -> {
            final var props = RegressionProviderFactory.propsFrom(resource);

            final var keys = new RegistrySourcedNameProvider<>(Key.class, spec.registry(), new RandomSelector());

            return new BiasedDelegatingProvider()
                    .shouldLogNormalFlow(true)
                    .withInitialization(onlyEcdsaKeys(NUM_DISTINCT_ECDSA_KEYS))
                    .withOp(
                            new RandomLazyCreateFungibleTransfer(spec.registry(), keys),
                            intPropOrElse("randomFungibleTransfer.bias", 0, props));
        };
    }

    public static Function<HapiSpec, OpProvider> transferNonFungibleTokenFuzzingWith(final String resource) {
        return spec -> {
            final var props = RegressionProviderFactory.propsFrom(resource);

            final var keys = new RegistrySourcedNameProvider<>(Key.class, spec.registry(), new RandomSelector());

            return new BiasedDelegatingProvider()
                    .shouldLogNormalFlow(true)
                    .withInitialization(onlyEcdsaKeys(NUM_DISTINCT_ECDSA_KEYS))
                    .withOp(
                            new RandomLazyCreateNonFungibleTransfer(spec.registry(), keys),
                            intPropOrElse("randomNonFungibleTransfer.bias", 0, props));
        };
    }

    public static Function<HapiSpec, OpProvider> transferERC20FuzzingWith(final String resource) {
        return spec -> {
            final var props = RegressionProviderFactory.propsFrom(resource);

            final var keys = new RegistrySourcedNameProvider<>(Key.class, spec.registry(), new RandomSelector());

            return new BiasedDelegatingProvider()
                    .shouldLogNormalFlow(true)
                    .withInitialization(onlyEcdsaKeys(NUM_DISTINCT_ECDSA_KEYS))
                    .withOp(
                            new RandomLazyCreateERC20Transfer(spec.registry(), keys),
                            intPropOrElse("randomERC20Transfer.bias", 0, props));
        };
    }

    public static HapiSpecOperation[] initOperations() {
        final AtomicReference<String> tokenAddr = new AtomicReference<>();

        return new HapiSpecOperation[] {
            // common init
            newKeyNamed(ECDSA_KEY).shape(SECP_256K1_SHAPE),
            newKeyNamed(MULTI_KEY),
            cryptoCreate(TOKEN_TREASURY),
            cryptoCreate(OWNER).balance(100 * ONE_HUNDRED_HBARS),
            cryptoCreate(SPENDER),
            cryptoCreate(UNIQUE_PAYER_ACCOUNT)
                    .balance(UNIQUE_PAYER_ACCOUNT_INITIAL_BALANCE)
                    .withRecharging(),

            // Fungible init
            tokenCreate(FUNGIBLE_TOKEN)
                    .tokenType(TokenType.FUNGIBLE_COMMON)
                    .initialSupply(INITIAL_SUPPLY)
                    .treasury(TOKEN_TREASURY)
                    .adminKey(MULTI_KEY)
                    .supplyKey(MULTI_KEY)
                    .exposingCreatedIdTo(id ->
                            tokenAddr.set(HapiPropertySource.asHexedSolidityAddress(HapiPropertySource.asToken(id)))),
            uploadInitCode(TRANSFER_TO_ALIAS_PRECOMPILE_CONTRACT),
            contractCreate(TRANSFER_TO_ALIAS_PRECOMPILE_CONTRACT),
            tokenAssociate(OWNER, List.of(FUNGIBLE_TOKEN)),
            cryptoTransfer(moving(INITIAL_SUPPLY, FUNGIBLE_TOKEN).between(TOKEN_TREASURY, OWNER)),

            // Non Fungible init

            // ERC 20 init
            cryptoCreate(TOKEN_TREASURY_ERC),
            tokenCreate(ERC_FUNGIBLE_TOKEN)
                    .tokenType(TokenType.FUNGIBLE_COMMON)
                    .initialSupply(INITIAL_SUPPLY)
                    .treasury(TOKEN_TREASURY_ERC)
                    .adminKey(MULTI_KEY)
                    .supplyKey(MULTI_KEY)
                    .exposingCreatedIdTo(id ->
                            tokenAddr.set(HapiPropertySource.asHexedSolidityAddress(HapiPropertySource.asToken(id)))),
            uploadInitCode(ERC_20_CONTRACT),
            contractCreate(ERC_20_CONTRACT),
            tokenAssociate(ERC_20_CONTRACT, List.of(ERC_FUNGIBLE_TOKEN)),
            cryptoTransfer(moving(INITIAL_SUPPLY, ERC_FUNGIBLE_TOKEN).between(TOKEN_TREASURY_ERC, ERC_20_CONTRACT))
        };
    }

    public static Function<HapiSpec, OpProvider> transferTokensFuzzingWith(final String resource) {
        return spec -> {
            final var props = RegressionProviderFactory.propsFrom(resource);

            final var keys = new RegistrySourcedNameProvider<>(Key.class, spec.registry(), new RandomSelector());

            return new BiasedDelegatingProvider()
                    .shouldLogNormalFlow(true)
                    .withInitialization(onlyEcdsaKeys(NUM_DISTINCT_ECDSA_KEYS))
                    .withOp(
                            new RandomLazyCreateFungibleTransfer(spec.registry(), keys),
                            intPropOrElse("randomFungibleTransfer.bias", 0, props))
                    .withOp(
                            new RandomLazyCreateERC20Transfer(spec.registry(), keys),
                            intPropOrElse("randomERC20Transfer.bias", 0, props));
        };
    }
}
