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

package com.hedera.services.bdd.suites.regression;

import static com.hedera.services.bdd.spec.HapiSpec.propertyPreservingHapiSpec;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromAccountToAlias;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.*;
import static com.hedera.services.bdd.suites.regression.factories.EvmAddressFuzzingFactory.evmAddressFuzzing;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.suites.HapiSuite;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class EthereumTxLazyCreateFuzzing extends HapiSuite {
    private static final Logger log = LogManager.getLogger(EthereumTxLazyCreateFuzzing.class);

    private static final String PROPERTIES = "ethereum-tx-lazy-create-fuzzing.properties";
    private static final String LAZY_CREATE_PROPERTY_NAME = "lazyCreation.enabled";
    public static final String CONTRACTS_EVM_VERSION_PROP = "contracts.evm.version";
    public static final String V_0_34 = "v0.34";
    public static final String AUTO_ACCOUNT = "autoAccount";
    private final AtomicInteger maxOpsPerSec = new AtomicInteger(1);
    private final AtomicInteger maxPendingOps = new AtomicInteger(Integer.MAX_VALUE);
    private final AtomicInteger backoffSleepSecs = new AtomicInteger(Integer.MAX_VALUE);

    public static void main(String... args) {
        new EthereumTxLazyCreateFuzzing().runSuiteSync();
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(ethereumTransactionLazyCreateFuzzing());
    }

    private HapiSpec ethereumTransactionLazyCreateFuzzing() {

        return propertyPreservingHapiSpec("EthereumTransactionLazyCreate")
                .preserving(CHAIN_ID_PROP, LAZY_CREATE_PROPERTY_NAME, CONTRACTS_EVM_VERSION_PROP)
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
                        .maxOpsPerSec(maxOpsPerSec::get)
                        .maxPendingOps(maxPendingOps::get)
                        .backoffSleepSecs(backoffSleepSecs::get));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
