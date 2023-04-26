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

package com.hedera.services.bdd.suites.regression;

import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.HapiSpec.propertyPreservingHapiSpec;
import static com.hedera.services.bdd.spec.infrastructure.OpProvider.UNIQUE_PAYER_ACCOUNT;
import static com.hedera.services.bdd.spec.infrastructure.OpProvider.UNIQUE_PAYER_ACCOUNT_INITIAL_BALANCE;
import static com.hedera.services.bdd.spec.infrastructure.meta.InitialAccountIdentifiers.KEY_FOR_INCONGRUENT_ALIAS;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromAccountToAlias;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.*;
import static com.hedera.services.bdd.suites.leaky.LeakyCryptoTestsSuite.*;
import static com.hedera.services.bdd.suites.leaky.LeakyCryptoTestsSuite.AUTO_ACCOUNT;
import static com.hedera.services.bdd.suites.regression.factories.IdFuzzingProviderFactory.*;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.suites.HapiSuite;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * We want to make this suite exercise all forms of identity a Hedera account may have, under all
 * possible circumstances. (This could take us a while to do.)
 *
 * <p>See <a href="https://github.com/hashgraph/hedera-services/issues/4565">#4565</a> for details.
 */
public class AddressAliasIdFuzzing extends HapiSuite {
    private static final Logger log = LogManager.getLogger(AddressAliasIdFuzzing.class);

    private static final String PROPERTIES = "id-fuzzing.properties";
    private final AtomicInteger maxOpsPerSec = new AtomicInteger(1);

    public static void main(String... args) {
        new AddressAliasIdFuzzing().runSuiteSync();
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(addressAliasIdFuzzing(), transferToKeyFuzzing(), ethereumTransactionLazyCreateFuzzing());
    }

    private HapiSpec addressAliasIdFuzzing() {
        return defaultHapiSpec("AddressAliasIdFuzzing")
                .given(
                        newKeyNamed(KEY_FOR_INCONGRUENT_ALIAS).shape(SECP_256K1_SHAPE),
                        cryptoCreate(UNIQUE_PAYER_ACCOUNT)
                                .balance(UNIQUE_PAYER_ACCOUNT_INITIAL_BALANCE)
                                .withRecharging())
                .when()
                .then(runWithProvider(idFuzzingWith(PROPERTIES)).lasting(10L, TimeUnit.SECONDS));
    }

    private HapiSpec transferToKeyFuzzing() {
        return defaultHapiSpec("TransferToKeyFuzzing")
                .given(cryptoCreate(UNIQUE_PAYER_ACCOUNT)
                        .balance(UNIQUE_PAYER_ACCOUNT_INITIAL_BALANCE)
                        .withRecharging())
                .when()
                .then(runWithProvider(idTransferToRandomKeyWith(PROPERTIES)).lasting(10L, TimeUnit.SECONDS));
    }

    private HapiSpec ethereumTransactionLazyCreateFuzzing() {

        return propertyPreservingHapiSpec("EthereumTransactionLazyCreateFuzzing")
                .preserving(CHAIN_ID_PROP, LAZY_CREATE_PROPERTY_NAME, CONTRACTS_EVM_VERSION_PROP)
                /**
                 * Initialization operations:
                 * override Ethereum network config (CHAIN_ID_PROP, LAZY_CREATE_PROPERTY_NAME, CONTRACTS_EVM_VERSION_PROP, V_0_34)
                 * initiate key name for signing the Ethereum Transaction (SECP_256K1_SOURCE_KEY)
                 * create account for paying the Ethereum Transaction (RELAYER).
                 * transfer ONE_HUNDRED_HBARS to SECP_256K1_SOURCE_KEY for signing the transaction
                 */
                .given(
                        overridingThree(
                                CHAIN_ID_PROP,
                                "298",
                                LAZY_CREATE_PROPERTY_NAME,
                                "true",
                                CONTRACTS_EVM_VERSION_PROP,
                                V_0_34),
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        cryptoCreate(RELAYER).balance(6 * ONE_MILLION_HBARS).withRecharging(),
                        cryptoTransfer(tinyBarsFromAccountToAlias(GENESIS, SECP_256K1_SOURCE_KEY, ONE_HUNDRED_HBARS))
                                .via(AUTO_ACCOUNT))
                .when()
                .then(runWithProvider(evmAddressFuzzing(PROPERTIES))
                        .lasting(10L, TimeUnit.SECONDS)
                        .maxOpsPerSec(maxOpsPerSec::get));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
