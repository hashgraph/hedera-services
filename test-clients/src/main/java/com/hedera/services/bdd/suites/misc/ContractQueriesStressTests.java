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
package com.hedera.services.bdd.suites.misc;

import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.AssertUtils.inOrder;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.isLiteralResult;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.resultWith;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.contractCallLocal;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountRecords;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractBytecode;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.runWithProvider;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.contract.Utils.FunctionType.FUNCTION;
import static com.hedera.services.bdd.suites.contract.Utils.getABIFor;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.hedera.services.bdd.spec.HapiPropertySource;
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
import java.util.function.Consumer;
import java.util.function.Function;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ContractQueriesStressTests extends HapiSuite {
    private static final Logger log = LogManager.getLogger(ContractQueriesStressTests.class);

    private AtomicLong duration = new AtomicLong(30);
    private AtomicReference<TimeUnit> unit = new AtomicReference<>(SECONDS);
    private AtomicInteger maxOpsPerSec = new AtomicInteger(100);

    public static void main(String... args) {
        new ContractQueriesStressTests().runSuiteSync();
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(
                new HapiSpec[] {
                    contractCallLocalStress(),
                    getContractRecordsStress(),
                    getContractBytecodeStress(),
                    getContractInfoStress(),
                });
    }

    private HapiSpec getContractInfoStress() {
        return defaultHapiSpec("GetContractInfoStress")
                .given()
                .when()
                .then(
                        withOpContext((spec, opLog) -> configureFromCi(spec)),
                        runWithProvider(getContractInfoFactory())
                                .lasting(duration::get, unit::get)
                                .maxOpsPerSec(maxOpsPerSec::get));
    }

    private HapiSpec getContractBytecodeStress() {
        return defaultHapiSpec("GetAccountRecordsStress")
                .given()
                .when()
                .then(
                        withOpContext((spec, opLog) -> configureFromCi(spec)),
                        runWithProvider(getContractBytecodeFactory())
                                .lasting(duration::get, unit::get)
                                .maxOpsPerSec(maxOpsPerSec::get));
    }

    private HapiSpec contractCallLocalStress() {
        return defaultHapiSpec("ContractCallLocalStress")
                .given()
                .when()
                .then(
                        withOpContext((spec, opLog) -> configureFromCi(spec)),
                        runWithProvider(contractCallLocalFactory())
                                .lasting(duration::get, unit::get)
                                .maxOpsPerSec(maxOpsPerSec::get));
    }

    private HapiSpec getContractRecordsStress() {
        return defaultHapiSpec("GetContractRecordsStress")
                .given()
                .when()
                .then(
                        withOpContext((spec, opLog) -> configureFromCi(spec)),
                        runWithProvider(getContractRecordsFactory())
                                .lasting(duration::get, unit::get)
                                .maxOpsPerSec(maxOpsPerSec::get));
    }

    private Function<HapiSpec, OpProvider> getContractRecordsFactory() {
        return spec ->
                new OpProvider() {
                    @Override
                    public List<HapiSpecOperation> suggestedInitializers() {
                        return List.of(
                                uploadInitCode("ChildStorage"),
                                contractCreate("ChildStorage"),
                                contractCall("ChildStorage", "growChild", 0, 1, 1),
                                contractCall("ChildStorage", "growChild", 1, 1, 3),
                                contractCall("ChildStorage", "setZeroReadOne", 23).via("first"),
                                contractCall("ChildStorage", "setZeroReadOne", 23).via("second"),
                                contractCall("ChildStorage", "setZeroReadOne", 23).via("third"));
                    }

                    @Override
                    public Optional<HapiSpecOperation> get() {
                        return Optional.of(
                                getAccountRecords("somebody")
                                        .has(
                                                inOrder(
                                                        recordWith().txnId("first"),
                                                        recordWith().txnId("second"),
                                                        recordWith().txnId("third")))
                                        .noLogging());
                    }
                };
    }

    private Function<HapiSpec, OpProvider> getContractInfoFactory() {
        return spec ->
                new OpProvider() {
                    @Override
                    public List<HapiSpecOperation> suggestedInitializers() {
                        return List.of(
                                uploadInitCode("ChildStorage"), contractCreate("ChildStorage"));
                    }

                    @Override
                    public Optional<HapiSpecOperation> get() {
                        return Optional.of(getContractInfo("ChildStorage").noLogging());
                    }
                };
    }

    private Function<HapiSpec, OpProvider> getContractBytecodeFactory() {
        return spec ->
                new OpProvider() {
                    @Override
                    public List<HapiSpecOperation> suggestedInitializers() {
                        return List.of(
                                uploadInitCode("ChildStorage"), contractCreate("ChildStorage"));
                    }

                    @Override
                    public Optional<HapiSpecOperation> get() {
                        return Optional.of(getContractBytecode("ChildStorage").noLogging());
                    }
                };
    }

    private Function<HapiSpec, OpProvider> contractCallLocalFactory() {
        return spec ->
                new OpProvider() {
                    @Override
                    public List<HapiSpecOperation> suggestedInitializers() {
                        return List.of(
                                uploadInitCode("ChildStorage"), contractCreate("ChildStorage"));
                    }

                    @Override
                    public Optional<HapiSpecOperation> get() {
                        var op =
                                contractCallLocal(
                                                "ChildStorage",
                                                getABIFor(FUNCTION, "getMyValue", "ChildStorage"))
                                        .noLogging()
                                        .has(
                                                resultWith()
                                                        .resultThruAbi(
                                                                getABIFor(
                                                                        FUNCTION,
                                                                        "getMyValue",
                                                                        "ChildStorage"),
                                                                isLiteralResult(
                                                                        new Object[] {
                                                                            BigInteger.valueOf(73)
                                                                        })));
                        return Optional.of(op);
                    }
                };
    }

    private void configureFromCi(HapiSpec spec) {
        HapiPropertySource ciProps = spec.setup().ciPropertiesMap();
        configure("duration", duration::set, ciProps, ciProps::getLong);
        configure("unit", unit::set, ciProps, ciProps::getTimeUnit);
        configure("maxOpsPerSec", maxOpsPerSec::set, ciProps, ciProps::getInteger);
    }

    private <T> void configure(
            String name,
            Consumer<T> configurer,
            HapiPropertySource ciProps,
            Function<String, T> getter) {
        if (ciProps.has(name)) {
            configurer.accept(getter.apply(name));
        }
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
