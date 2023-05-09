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
import static com.hedera.services.bdd.suites.regression.factories.LazyCreatePrecompileFuzzingFactory.*;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.suites.HapiSuite;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class LazyCreatePrecompileFuzzing extends HapiSuite {
    private static final Logger log = LogManager.getLogger(LazyCreatePrecompileFuzzing.class);
    private static final String PROPERTIES = "lazycreate-precompile-fuzzing.properties";

    public static void main(String... args) {
        new LazyCreatePrecompileFuzzing().runSuiteSync();
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(transferTokensFuzzing());
    }

    private HapiSpec transferTokensFuzzing() {

        return propertyPreservingHapiSpec("TransferFungibleTokenLazyCreateFuzzing")
                .preserving("contracts.precompile.atomicCryptoTransfer.enabled")
                .given(initOperations())
                .when()
                .then(runWithProvider(transferTokensFuzzingWith(PROPERTIES)).lasting(10L, TimeUnit.SECONDS));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
