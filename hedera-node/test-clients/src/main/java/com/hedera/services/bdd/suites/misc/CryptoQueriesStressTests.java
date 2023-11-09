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
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountRecords;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.runWithProvider;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.HapiTestSuite;
import com.hedera.services.bdd.spec.HapiPropertySource;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.infrastructure.OpProvider;
import com.hedera.services.bdd.suites.HapiSuite;
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

@HapiTestSuite
public class CryptoQueriesStressTests extends HapiSuite {
    private static final Logger log = LogManager.getLogger(CryptoQueriesStressTests.class);

    private AtomicLong duration = new AtomicLong(30);
    private AtomicReference<TimeUnit> unit = new AtomicReference<>(SECONDS);
    private AtomicInteger maxOpsPerSec = new AtomicInteger(100);

    public static void main(String... args) {
        new CryptoQueriesStressTests().runSuiteSync();
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(new HapiSpec[] {
            getAccountInfoStress(), getAccountBalanceStress(),
        });
    }

    @HapiTest
    private HapiSpec getAccountBalanceStress() {
        return defaultHapiSpec("getAccountBalanceStress")
                .given()
                .when()
                .then(
                        withOpContext((spec, opLog) -> configureFromCi(spec)),
                        runWithProvider(getAccountBalanceFactory())
                                .lasting(duration::get, unit::get)
                                .maxOpsPerSec(maxOpsPerSec::get));
    }

    @HapiTest
    private HapiSpec getAccountInfoStress() {
        return defaultHapiSpec("getAccountInfoStress")
                .given()
                .when()
                .then(
                        withOpContext((spec, opLog) -> configureFromCi(spec)),
                        runWithProvider(getAccountInfoFactory())
                                .lasting(duration::get, unit::get)
                                .maxOpsPerSec(maxOpsPerSec::get));
    }

    @HapiTest
    private HapiSpec getAccountRecordsStress() {
        return defaultHapiSpec("getAccountRecordsStress")
                .given()
                .when()
                .then(
                        withOpContext((spec, opLog) -> configureFromCi(spec)),
                        runWithProvider(getAccountRecordsFactory())
                                .lasting(duration::get, unit::get)
                                .maxOpsPerSec(maxOpsPerSec::get));
    }

    private Function<HapiSpec, OpProvider> getAccountRecordsFactory() {
        return spec -> new OpProvider() {
            @Override
            public List<HapiSpecOperation> suggestedInitializers() {
                return List.of(
                        cryptoCreate("somebody").sendThreshold(1L),
                        cryptoTransfer(tinyBarsFromTo("somebody", FUNDING, 2L)).via("first"),
                        cryptoTransfer(tinyBarsFromTo("somebody", FUNDING, 3L)).via("second"),
                        cryptoTransfer(tinyBarsFromTo("somebody", FUNDING, 4L)).via("third"));
            }

            @Override
            public Optional<HapiSpecOperation> get() {
                return Optional.of(getAccountRecords("somebody")
                        .has(inOrder(
                                recordWith().txnId("first"),
                                recordWith().txnId("second"),
                                recordWith().txnId("third")))
                        .noLogging());
            }
        };
    }

    private Function<HapiSpec, OpProvider> getAccountBalanceFactory() {
        return spec -> new OpProvider() {
            @Override
            public List<HapiSpecOperation> suggestedInitializers() {
                return List.of(cryptoCreate("somebody"));
            }

            @Override
            public Optional<HapiSpecOperation> get() {
                return Optional.of(getAccountBalance("somebody").noLogging());
            }
        };
    }

    private Function<HapiSpec, OpProvider> getAccountInfoFactory() {
        return spec -> new OpProvider() {
            @Override
            public List<HapiSpecOperation> suggestedInitializers() {
                return List.of(cryptoCreate("somebody"));
            }

            @Override
            public Optional<HapiSpecOperation> get() {
                return Optional.of(getAccountInfo("somebody").noLogging());
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
            String name, Consumer<T> configurer, HapiPropertySource ciProps, Function<String, T> getter) {
        if (ciProps.has(name)) {
            configurer.accept(getter.apply(name));
        }
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
