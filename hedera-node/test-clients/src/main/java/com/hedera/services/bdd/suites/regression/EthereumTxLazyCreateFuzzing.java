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
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.*;
import static com.hedera.services.bdd.suites.leaky.LeakyCryptoTestsSuite.CONTRACTS_EVM_VERSION_PROP;
import static com.hedera.services.bdd.suites.leaky.LeakyCryptoTestsSuite.LAZY_CREATE_PROPERTY_NAME;
import static com.hedera.services.bdd.suites.regression.factories.EvmAddressFuzzingFactory.evmAddressFuzzing;
import static com.hedera.services.bdd.suites.regression.factories.EvmAddressFuzzingFactory.initOperations;

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

        return propertyPreservingHapiSpec("EthereumTransactionLazyCreateFuzzing")
                .preserving(CHAIN_ID_PROP, LAZY_CREATE_PROPERTY_NAME, CONTRACTS_EVM_VERSION_PROP)
                .given(initOperations())
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
