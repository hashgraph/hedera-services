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

import static com.hedera.services.bdd.spec.infrastructure.OpProvider.UNIQUE_PAYER_ACCOUNT_INITIAL_BALANCE;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.suites.crypto.AutoAccountCreationSuite.CRYPTO_TRANSFER_RECEIVER;
import static com.hedera.services.bdd.suites.crypto.AutoAccountCreationSuite.LAZY_CREATE_SPONSOR;
import static com.hedera.services.bdd.suites.regression.factories.RegressionProviderFactory.intPropOrElse;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.infrastructure.OpProvider;
import com.hedera.services.bdd.spec.infrastructure.providers.names.RegistrySourcedNameProvider;
import com.hedera.services.bdd.spec.infrastructure.providers.ops.BiasedDelegatingProvider;
import com.hedera.services.bdd.spec.infrastructure.providers.ops.crypto.RandomAccount;
import com.hedera.services.bdd.spec.infrastructure.providers.ops.crypto.RandomAccountDeletion;
import com.hedera.services.bdd.spec.infrastructure.providers.ops.hollow.RandomHollowAccount;
import com.hedera.services.bdd.spec.infrastructure.providers.ops.hollow.RandomHollowContractCall;
import com.hedera.services.bdd.spec.infrastructure.providers.ops.hollow.RandomHollowContractCreate;
import com.hedera.services.bdd.spec.infrastructure.providers.ops.hollow.RandomHollowTokenAssociate;
import com.hedera.services.bdd.spec.infrastructure.providers.ops.hollow.RandomHollowTransfer;
import com.hedera.services.bdd.spec.infrastructure.providers.ops.hollow.RandomKey;
import com.hedera.services.bdd.spec.infrastructure.selectors.RandomSelector;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Key;
import java.util.function.Function;

public class AccountCompletionFuzzingFactory {
    public static final String CONTRACT = "PayReceivable";
    public static final String VANILLA_TOKEN = "TokenD";
    public static final String TOKEN_TREASURY = "treasury";

    private AccountCompletionFuzzingFactory() {
        throw new IllegalStateException("Static factory class");
    }

    public static HapiSpecOperation[] accountsCreation() {
        return new HapiSpecOperation[] {
            cryptoCreate(LAZY_CREATE_SPONSOR)
                    .balance(UNIQUE_PAYER_ACCOUNT_INITIAL_BALANCE)
                    .withRecharging(),
            cryptoCreate(CRYPTO_TRANSFER_RECEIVER)
                    .balance(UNIQUE_PAYER_ACCOUNT_INITIAL_BALANCE)
                    .withRecharging(),
            uploadInitCode(CONTRACT),
            cryptoCreate(TOKEN_TREASURY).balance(0L),
            tokenCreate(VANILLA_TOKEN).treasury(TOKEN_TREASURY),
            contractCreate(CONTRACT)
        };
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
                    .shouldLogNormalFlow(true)
                    .withOp(
                            new RandomKey(keys)
                                    .ceiling(
                                            intPropOrElse(
                                                    "randomAccount.ceilingNum",
                                                    RandomAccount.DEFAULT_CEILING_NUM,
                                                    props)),
                            intPropOrElse("randomKey.bias", 0, props))
                    .withOp(
                            new RandomHollowAccount(spec.registry(), keys, accounts)
                                    .ceiling(
                                            intPropOrElse(
                                                    "randomAccount.ceilingNum",
                                                    RandomAccount.DEFAULT_CEILING_NUM,
                                                    props)),
                            intPropOrElse("randomAccount.bias", 0, props))
                    .withOp(
                            new RandomHollowTransfer(spec.registry(), accounts),
                            intPropOrElse("randomTransfer.bias", 0, props))
                    .withOp(
                            new RandomHollowTokenAssociate(spec.registry(), accounts),
                            intPropOrElse("randomTokenAssociate.bias", 0, props))
                    .withOp(
                            new RandomHollowContractCreate(spec.registry(), accounts),
                            intPropOrElse("randomContractCreate.bias", 0, props))
                    .withOp(
                            new RandomHollowContractCall(spec.registry(), accounts),
                            intPropOrElse("randomContractCall.bias", 0, props))
                    .withOp(
                            new RandomAccountDeletion(accounts),
                            intPropOrElse("randomAccountDeletion.bias", 0, props));
        };
    }
}
