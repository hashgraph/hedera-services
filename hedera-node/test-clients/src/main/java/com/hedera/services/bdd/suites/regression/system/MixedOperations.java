/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.suites.regression.system;

import static com.hedera.services.bdd.spec.transactions.TxnUtils.randomUtf8Bytes;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.submitMessageTo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.inParallel;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hedera.services.bdd.suites.HapiSuite.APP_PROPERTIES;
import static com.hedera.services.bdd.suites.HapiSuite.GENESIS;
import static com.hedera.services.bdd.suites.HapiSuite.THREE_MONTHS_IN_SECONDS;
import static com.hedera.services.bdd.suites.token.TokenTransactSpecs.SUPPLY_KEY;

import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hederahashgraph.api.proto.java.TokenSupplyType;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import org.apache.commons.lang3.ArrayUtils;

/**
 * Returns mixed operations that can be used for restart and reconnect tests.
 * These operations will be further extended in the future
 */
public class MixedOperations {

    static final String SUBMIT_KEY = "submitKey";
    static final String ADMIN_KEY = "adminKey";
    static final String TOKEN = "token";
    static final String SENDER = "sender";
    static final String RECEIVER = "receiver";
    static final String TOPIC = "topic";
    static final String TREASURY = "treasury";
    static final String PAYER = "payer";
    final int numSubmissions;

    public MixedOperations(int numSubmissions) {
        this.numSubmissions = numSubmissions;
    }

    Supplier<HapiSpecOperation[]> mixedOps(
            final AtomicInteger tokenId, final AtomicInteger scheduleId, final Random r) {
        return () -> new HapiSpecOperation[] {
            // Submit some mixed operations
            fileUpdate(APP_PROPERTIES).payingWith(GENESIS).overridingProps(Map.of("tokens.maxPerAccount", "10000000")),
            inParallel(IntStream.range(0, 2 * numSubmissions)
                    .mapToObj(ignore -> cryptoTransfer(tinyBarsFromTo(SENDER, RECEIVER, 1L))
                            .payingWith(PAYER)
                            .logging()
                            .signedBy(SENDER, PAYER))
                    .toArray(HapiSpecOperation[]::new)),
            sleepFor(10000),
            inParallel(IntStream.range(0, numSubmissions)
                    .mapToObj(ignore -> tokenCreate(TOKEN + tokenId.getAndIncrement())
                            .supplyType(TokenSupplyType.FINITE)
                            .treasury(TREASURY)
                            .autoRenewPeriod(THREE_MONTHS_IN_SECONDS)
                            .maxSupply(1000)
                            .initialSupply(500)
                            .decimals(1)
                            .adminKey(ADMIN_KEY)
                            .supplyKey(SUPPLY_KEY)
                            .payingWith(PAYER)
                            .logging())
                    .toArray(HapiSpecOperation[]::new)),
            sleepFor(10000),
            inParallel(IntStream.range(0, numSubmissions)
                    .mapToObj(i -> tokenAssociate(SENDER, TOKEN + i)
                            .payingWith(PAYER)
                            .logging()
                            .signedBy(SENDER, PAYER))
                    .toArray(HapiSpecOperation[]::new)),
            sleepFor(10000),
            submitMessageTo(TOPIC)
                    .message(ArrayUtils.addAll(
                            ByteBuffer.allocate(8)
                                    .putLong(Instant.now().toEpochMilli())
                                    .array(),
                            randomUtf8Bytes(1000)))
                    .payingWith(SENDER)
                    .signedBy(SENDER, SUBMIT_KEY),
            sleepFor(10000),
            inParallel(IntStream.range(0, 100)
                    .mapToObj(ignore -> scheduleCreate(
                                    "schedule" + scheduleId.incrementAndGet(),
                                    cryptoTransfer(tinyBarsFromTo(SENDER, RECEIVER, r.nextInt(100000000))))
                            .payingWith(PAYER)
                            .signedBy(SENDER, PAYER)
                            .adminKey(SENDER)
                            .logging())
                    .toArray(HapiSpecOperation[]::new)),
        };
    }
}
