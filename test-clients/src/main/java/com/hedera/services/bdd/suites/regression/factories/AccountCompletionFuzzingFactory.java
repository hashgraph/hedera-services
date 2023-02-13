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

import static com.hedera.node.app.service.evm.utils.EthSigsUtils.recoverAddressFromPubKey;
import static com.hedera.services.bdd.spec.infrastructure.OpProvider.UNIQUE_PAYER_ACCOUNT;
import static com.hedera.services.bdd.spec.infrastructure.OpProvider.UNIQUE_PAYER_ACCOUNT_INITIAL_BALANCE;
import static com.hedera.services.bdd.spec.keys.TrieSigMapGenerator.uniqueWithFullPrefixesFor;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.asId;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HBAR;
import static com.hedera.services.bdd.suites.HapiSuite.SECP_256K1_SHAPE;
import static com.hedera.services.bdd.suites.crypto.AutoAccountCreationSuite.CRYPTO_TRANSFER_RECEIVER;
import static com.hedera.services.bdd.suites.crypto.AutoAccountCreationSuite.LAZY_CREATE_SPONSOR;
import static com.hedera.services.bdd.suites.regression.factories.RegressionProviderFactory.intPropOrElse;

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.infrastructure.HapiSpecRegistry;
import com.hedera.services.bdd.spec.infrastructure.OpProvider;
import com.hedera.services.bdd.spec.infrastructure.meta.InitialAccountIdentifiers;
import com.hedera.services.bdd.spec.infrastructure.providers.names.RegistrySourcedNameProvider;
import com.hedera.services.bdd.spec.infrastructure.providers.ops.BiasedDelegatingProvider;
import com.hedera.services.bdd.spec.infrastructure.selectors.RandomSelector;
import com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoCreate;
import com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.IntStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class AccountCompletionFuzzingFactory {
    /**
     * How many different ECDSA keys we will re-use as we continually create, update, and delete
     * accounts with random {@link InitialAccountIdentifiers} based on this fixed set of keys.
     */
    private static final int NUM_DISTINCT_ECDSA_KEYS = 42;

    private static final Logger log = LogManager.getLogger(AccountCompletionFuzzingFactory.class);

    public static HapiSpecOperation[] accountCreation() {
        return createAccounts();
    }

    private static HapiCryptoCreate[] createSponsor() {
        return new HapiCryptoCreate[] {
            cryptoCreate(UNIQUE_PAYER_ACCOUNT)
                    .balance(UNIQUE_PAYER_ACCOUNT_INITIAL_BALANCE)
                    .withRecharging()
        };
    }

    private static HapiCryptoCreate[] createAccounts() {
        return List.of(
                        cryptoCreate(LAZY_CREATE_SPONSOR)
                                .balance(UNIQUE_PAYER_ACCOUNT_INITIAL_BALANCE)
                                .withRecharging(),
                        cryptoCreate(CRYPTO_TRANSFER_RECEIVER)
                                .balance(UNIQUE_PAYER_ACCOUNT_INITIAL_BALANCE)
                                .withRecharging())
                .toArray(HapiCryptoCreate[]::new);
    }

    public static Function<HapiSpec, OpProvider> accountCompletionFuzzingWith(
            final String resource) {
        return spec -> {
            final var props = RegressionProviderFactory.propsFrom(resource);

            final var accounts =
                    new RegistrySourcedNameProvider<>(
                            AccountID.class, spec.registry(), new RandomSelector());

            final var keys =
                    new RegistrySourcedNameProvider<>(
                            Key.class, spec.registry(), new RandomSelector());

            return new BiasedDelegatingProvider()
                    .withInitialization(onlyEcdsaKeys())
                    .shouldLogNormalFlow(true)
                    .withOp(
                            new OpProvider() {
                                @Override
                                public Optional<HapiSpecOperation> get() {
                                    final var key = keys.getQualifying();
                                    return key.filter(k -> !k.endsWith("#"))
                                            .filter(k -> !spec.registry().hasAccountId(k + "#"))
                                            .map(s -> generateHollowAccount(spec.registry(), s));
                                }
                            },
                            intPropOrElse("randomAccount.bias", 10, props))
                    .withOp(
                            new OpProvider() {
                                @Override
                                public Optional<HapiSpecOperation> get() {
                                    final var payer =
                                            accounts.getQualifying().filter(a -> a.endsWith("#"));

                                    if (payer.isEmpty()) {
                                        return Optional.empty();
                                    }
                                    final var from = payer.get();

                                    long amount = 1;

                                    final var key = from.substring(0, from.length() - 1);
                                    final AccountID fromAccount = asId(from, spec);
                                    spec.registry().saveAccountId(key, fromAccount);
                                    HapiCryptoTransfer op =
                                            cryptoTransfer(
                                                            tinyBarsFromTo(
                                                                    LAZY_CREATE_SPONSOR,
                                                                    CRYPTO_TRANSFER_RECEIVER,
                                                                    amount))
                                                    .payingWith(key)
                                                    // .signedBy(from)
                                                    .sigMapPrefixes(uniqueWithFullPrefixesFor(key))
                                                    .hasKnownStatus(ResponseCodeEnum.SUCCESS);
                                    return Optional.of(op);
                                }
                            },
                            intPropOrElse("randomTransfer.bias", 10, props));
        };
    }

    /*
                                    final var ecdsaKey =
                                           spec.registry()
                                                   .getKey(SECP_256K1_SOURCE_KEY)
                                                   .getECDSASecp256K1()
                                                   .toByteArray();
                                   final var evmAddress =
                                           ByteString.copyFrom(recoverAddressFromPubKey(ecdsaKey));
                                   final var op =
                                           cryptoTransfer(
                                                           tinyBarsFromTo(
                                                                   LAZY_CREATE_SPONSOR,
                                                                   evmAddress,
                                                                   ONE_HUNDRED_HBARS))
                                                   .hasKnownStatus(SUCCESS)
                                                   .via(TRANSFER_TXN);
    */

    private static HapiSpecOperation[] onlyEcdsaKeys() {
        return IntStream.range(0, NUM_DISTINCT_ECDSA_KEYS)
                .mapToObj(i -> newKeyNamed("Fuzz#" + i).shape(SECP_256K1_SHAPE))
                .toArray(HapiSpecOperation[]::new);
    }

    public static HapiSpecOperation[] generateHollowAccounts(HapiSpecRegistry registry) {
        return IntStream.range(0, NUM_DISTINCT_ECDSA_KEYS)
                .mapToObj(i -> generateHollowAccount(registry, "Fuzz#" + i))
                .toArray(HapiSpecOperation[]::new);
    }

    private static HapiSpecOperation generateHollowAccount(
            HapiSpecRegistry registry, String keyName) {
        final var ecdsaKey = registry.getKey(keyName).getECDSASecp256K1().toByteArray();
        final var evmAddress = ByteString.copyFrom(recoverAddressFromPubKey(ecdsaKey));
        // log.warn("Key name: " + keyName + ", Evm: " + evmAddress + "----------------");

        return cryptoCreate(keyName + "#")
                .hasAnyPrecheck()
                .hasAnyKnownStatus()
                .evmAddress(evmAddress)
                // .payingWith(LAZY_CREATE_SPONSOR)
                //  .key(keyName)
                .balance(100 * ONE_HBAR);
        // .via(keyName + "#tx");
    }
}
