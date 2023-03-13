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

package com.hedera.services.bdd.suites.compose;

import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.isLiteralResult;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.resultWith;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.contractCallLocal;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.runWithProvider;
import static com.hedera.services.bdd.suites.contract.Utils.FunctionType.FUNCTION;
import static com.hedera.services.bdd.suites.contract.Utils.getABIFor;
import static java.util.concurrent.TimeUnit.MINUTES;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.infrastructure.OpProvider;
import com.hedera.services.bdd.suites.HapiSuite;
import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class PerpetualLocalCalls extends HapiSuite {
    private static final Logger log = LogManager.getLogger(PerpetualLocalCalls.class);
    public static final String CHILD_STORAGE = "ChildStorage";

    private AtomicLong duration = new AtomicLong(Long.MAX_VALUE);
    private AtomicReference<TimeUnit> unit = new AtomicReference<>(MINUTES);
    private AtomicInteger maxOpsPerSec = new AtomicInteger(100);
    private AtomicInteger totalBeforeFailure = new AtomicInteger(0);

    public static void main(String... args) {
        new PerpetualLocalCalls().runSuiteSync();
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(new HapiSpec[] {
            localCallsForever(),
        });
    }

    private HapiSpec localCallsForever() {
        return defaultHapiSpec("LocalCallsForever")
                .given()
                .when()
                .then(runWithProvider(localCallsFactory())
                        .lasting(duration::get, unit::get)
                        .maxOpsPerSec(maxOpsPerSec::get));
    }

    private Function<HapiSpec, OpProvider> localCallsFactory() {
        return spec -> new OpProvider() {
            @Override
            public List<HapiSpecOperation> suggestedInitializers() {
                return List.of(uploadInitCode(CHILD_STORAGE), contractCreate(CHILD_STORAGE));
            }

            @Override
            public Optional<HapiSpecOperation> get() {
                var op = contractCallLocal(CHILD_STORAGE, "getMyValue")
                        .noLogging()
                        .has(resultWith()
                                .resultThruAbi(
                                        getABIFor(FUNCTION, "getMyValue", CHILD_STORAGE),
                                        isLiteralResult(new Object[] {BigInteger.valueOf(73)})));
                var soFar = totalBeforeFailure.getAndIncrement();
                if (soFar % 1000 == 0) {
                    log.info("--- " + soFar);
                }
                return Optional.of(op);
            }
        };
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
