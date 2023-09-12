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

package com.hedera.services.bdd.suites.streaming;

import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.inParallel;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.runWithProvider;
import static java.util.concurrent.TimeUnit.SECONDS;

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
import java.util.function.Function;
import java.util.stream.IntStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class RunTransfers extends HapiSuite {
    private static final Logger log = LogManager.getLogger(RunTransfers.class);

    private AtomicLong duration = new AtomicLong(900);
    private AtomicReference<TimeUnit> unit = new AtomicReference<>(SECONDS);
    private AtomicInteger maxOpsPerSec = new AtomicInteger(500);

    private final int TOTAL_ACCOUNTS = 10_000;
    private final int ACCOUNTS_IN_TRANSFER_ROTATION = 5;

    public static void main(String... args) {
        new RunTransfers().runSuiteSync();
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(new HapiSpec[] {
            runTransfers(),
        });
    }

    private HapiSpec runTransfers() {
        return defaultHapiSpec("RunTransfers")
                .given()
                .when()
                .then(runWithProvider(transfersProvider())
                        .lasting(duration::get, unit::get)
                        .maxOpsPerSec(maxOpsPerSec::get));
    }

    private Function<HapiSpec, OpProvider> transfersProvider() {
        final AtomicInteger donor = new AtomicInteger(0);

        return spec -> new OpProvider() {
            @Override
            public List<HapiSpecOperation> suggestedInitializers() {
                return List.of(inParallel(IntStream.range(0, TOTAL_ACCOUNTS)
                        .mapToObj(i -> cryptoCreate(String.format("account%d", i)))
                        .toArray(HapiSpecOperation[]::new)));
            }

            @Override
            public Optional<HapiSpecOperation> get() {
                var op = cryptoTransfer(tinyBarsFromTo(
                                "account" + donor.getAndIncrement(), "account" + donor.getAndIncrement(), 1))
                        .deferStatusResolution();
                if (donor.get() > ACCOUNTS_IN_TRANSFER_ROTATION) {
                    donor.set(0);
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
