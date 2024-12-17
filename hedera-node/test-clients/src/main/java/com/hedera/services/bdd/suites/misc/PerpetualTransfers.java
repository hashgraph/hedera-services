/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
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

import static com.hedera.services.bdd.junit.TestTags.NOT_REPEATABLE;
import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.runWithProvider;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.SpecOperation;
import com.hedera.services.bdd.spec.infrastructure.OpProvider;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;

@Tag(NOT_REPEATABLE)
public class PerpetualTransfers {
    private AtomicLong duration = new AtomicLong(30);
    private AtomicReference<TimeUnit> unit = new AtomicReference<>(SECONDS);
    private AtomicInteger maxOpsPerSec = new AtomicInteger(500);

    @HapiTest
    final Stream<DynamicTest> canTransferBackAndForthForever() {
        return defaultHapiSpec("CanTransferBackAndForthForever")
                .given()
                .when()
                .then(runWithProvider(transfersFactory())
                        .lasting(duration::get, unit::get)
                        .maxOpsPerSec(maxOpsPerSec::get));
    }

    private Function<HapiSpec, OpProvider> transfersFactory() {
        AtomicBoolean fromAtoB = new AtomicBoolean(true);
        AtomicInteger transfersSoFar = new AtomicInteger(0);
        return spec -> new OpProvider() {
            @Override
            public List<SpecOperation> suggestedInitializers() {
                return List.of(cryptoCreate("A"), cryptoCreate("B"));
            }

            @Override
            public Optional<HapiSpecOperation> get() {
                var from = fromAtoB.get() ? "A" : "B";
                var to = fromAtoB.get() ? "B" : "A";
                fromAtoB.set(!fromAtoB.get());

                var op = cryptoTransfer(tinyBarsFromTo(from, to, 1))
                        .noLogging()
                        .memo("transfer #" + transfersSoFar.getAndIncrement())
                        .deferStatusResolution();

                return Optional.of(op);
            }
        };
    }
}
