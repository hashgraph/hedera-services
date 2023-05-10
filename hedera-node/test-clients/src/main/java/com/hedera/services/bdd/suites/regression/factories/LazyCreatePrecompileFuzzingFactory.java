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
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.movingUnique;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overriding;
import static com.hedera.services.bdd.suites.HapiSuite.*;
import static com.hedera.services.bdd.suites.regression.LazyCreatePrecompileFuzzing.ATOMIC_CRYPTO_TRANSFER;
import static com.hedera.services.bdd.suites.regression.factories.RegressionProviderFactory.intPropOrElse;
import static com.hedera.services.bdd.suites.utils.ECDSAKeysUtils.onlyEcdsaKeys;

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.spec.HapiPropertySource;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.infrastructure.OpProvider;
import com.hedera.services.bdd.spec.infrastructure.providers.names.RegistrySourcedNameProvider;
import com.hedera.services.bdd.spec.infrastructure.providers.ops.BiasedDelegatingProvider;
import com.hedera.services.bdd.spec.infrastructure.providers.ops.precompile.*;
import com.hedera.services.bdd.spec.infrastructure.selectors.RandomSelector;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.TokenType;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Stream;

public class LazyCreatePrecompileFuzzingFactory {
    public static final long INITIAL_SUPPLY = 1_000_000_000L;
    public static final String FUNGIBLE_TOKEN = "fungibleToken";
    public static final String ERC_FUNGIBLE_TOKEN = "ercFungibleToken";
    public static final String NON_FUNGIBLE_TOKEN = "nonFungibleToken";
    public static final String MULTI_KEY = "purpose";
    public static final String OWNER = "owner";
    public static final String SENDER = "sender";
    public static final String TRANSFER_TO_ALIAS_PRECOMPILE_CONTRACT = "PrecompileAliasXfer";
    public static final String NESTED_LAZY_PRECOMPILE_CONTRACT = "LazyPrecompileTransfersAtomic";
    public static final String TOKEN_TREASURY = "treasury";
    public static final String TOKEN_TREASURY_ERC = "treasuryErc";
    public static final String ECDSA_KEY = "abcdECDSAkey";
    public static final String TRANSFER_TOKEN_TXN = "transferTokenTxn";
    public static final String TRANSFER_NFT_TXN = "transferNFTTxn";
    public static final String ERC_20_CONTRACT = "ERC20Contract";
    public static final String ERC_721_CONTRACT = "ERC721Contract";
    private static final String BASE_APPROVE_TXN = "baseApproveTxn";
    private static final String SPENDER = "spender";
    private static final int NUM_DISTINCT_ECDSA_KEYS = 42;

    private LazyCreatePrecompileFuzzingFactory() {}

    /**
     * Concatenated array of all initial operations used in .given()
     * @return HapiSpecOperation
     */
    public static HapiSpecOperation[] initOperations() {
        return Stream.of(
                        initOpCommon(),
                        initOpHbarTransfer(),
                        initOpFungibleTransfer(),
                        initOpERC20Transfer(),
                        initOpERC721Transfer())
                .flatMap(Stream::of)
                .toArray(HapiSpecOperation[]::new);
    }

    public static Function<HapiSpec, OpProvider> transferTokensFuzzingWith(final String resource) {
        return spec -> {
            final var props = RegressionProviderFactory.propsFrom(resource);

            final var keys = new RegistrySourcedNameProvider<>(Key.class, spec.registry(), new RandomSelector());

            return new BiasedDelegatingProvider()
                    .shouldLogNormalFlow(true)
                    .withInitialization(onlyEcdsaKeys(NUM_DISTINCT_ECDSA_KEYS))
                    .withOp(
                            new RandomHbarTransferLazyCreate(spec.registry(), keys),
                            intPropOrElse("randomHbar.bias", 0, props))
                    .withOp(
                            new RandomFungibleTransferLazyCreate(spec.registry(), keys),
                            intPropOrElse("randomFungibleTransfer.bias", 0, props))
                    .withOp(
                            new RandomERC20TransferLazyCreate(spec.registry(), keys),
                            intPropOrElse("randomERC20Transfer.bias", 0, props))
                    .withOp(
                            new RandomERC721TransferLazyCreate(spec.registry(), keys),
                            intPropOrElse("randomERC721Transfer.bias", 0, props));
        };
    }

    private static HapiSpecOperation[] initOpCommon() {
        return new HapiSpecOperation[] {
            /**
             * In order for hbar transfer to work properly through precmompile
             * contracts.precompile.atomicCryptoTransfer.enabled need to be set to TRUE
             */
            overriding(ATOMIC_CRYPTO_TRANSFER, "true"),

            // common init
            newKeyNamed(ECDSA_KEY).shape(SECP_256K1_SHAPE),
            newKeyNamed(MULTI_KEY),
            cryptoCreate(TOKEN_TREASURY),
            cryptoCreate(OWNER).balance(100 * ONE_HUNDRED_HBARS),
            cryptoCreate(SPENDER),
            cryptoCreate(UNIQUE_PAYER_ACCOUNT)
                    .balance(UNIQUE_PAYER_ACCOUNT_INITIAL_BALANCE)
                    .withRecharging()
        };
    }

    private static HapiSpecOperation[] initOpHbarTransfer() {
        return new HapiSpecOperation[] {
            cryptoCreate(SENDER).balance(INITIAL_SUPPLY).key(MULTI_KEY).maxAutomaticTokenAssociations(5),
            uploadInitCode(NESTED_LAZY_PRECOMPILE_CONTRACT),
            contractCreate(NESTED_LAZY_PRECOMPILE_CONTRACT),
        };
    }

    private static HapiSpecOperation[] initOpFungibleTransfer() {
        final AtomicReference<String> tokenAddr = new AtomicReference<>();

        return new HapiSpecOperation[] {
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

    private static HapiSpecOperation[] initOpERC20Transfer() {
        final AtomicReference<String> tokenAddr = new AtomicReference<>();

        return new HapiSpecOperation[] {
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

    private static HapiSpecOperation[] initOpERC721Transfer() {
        return new HapiSpecOperation[] {
            tokenCreate(NON_FUNGIBLE_TOKEN)
                    .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                    .initialSupply(0)
                    .treasury(TOKEN_TREASURY)
                    .adminKey(MULTI_KEY)
                    .supplyKey(MULTI_KEY),
            uploadInitCode(ERC_721_CONTRACT),
            contractCreate(ERC_721_CONTRACT),
            tokenAssociate(OWNER, NON_FUNGIBLE_TOKEN),
            tokenAssociate(SPENDER, NON_FUNGIBLE_TOKEN),
            tokenAssociate(ERC_721_CONTRACT, NON_FUNGIBLE_TOKEN),
            mintToken(NON_FUNGIBLE_TOKEN, erc721UniqueTokens()),
            cryptoTransfer(movingUnique(NON_FUNGIBLE_TOKEN, 1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L)
                    .between(TOKEN_TREASURY, OWNER)),
            cryptoApproveAllowance()
                    .payingWith(UNIQUE_PAYER_ACCOUNT)
                    .addNftAllowance(
                            OWNER,
                            NON_FUNGIBLE_TOKEN,
                            ERC_721_CONTRACT,
                            false,
                            List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L))
                    .via(BASE_APPROVE_TXN)
                    .logged()
                    .signedBy(UNIQUE_PAYER_ACCOUNT, OWNER)
                    .fee(ONE_HBAR)
        };
    }

    private static List<ByteString> erc721UniqueTokens() {
        return List.of(
                ByteString.copyFromUtf8("a"),
                ByteString.copyFromUtf8("b"),
                ByteString.copyFromUtf8("c"),
                ByteString.copyFromUtf8("d"),
                ByteString.copyFromUtf8("e"),
                ByteString.copyFromUtf8("f"),
                ByteString.copyFromUtf8("g"),
                ByteString.copyFromUtf8("h"),
                ByteString.copyFromUtf8("i"),
                ByteString.copyFromUtf8("j"));
    }
}
