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

package com.hedera.services.bdd.suites.reconnect;

import static com.hedera.services.bdd.spec.HapiSpec.customHapiSpec;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withLiveNode;

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.suites.HapiSuite;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ValidateExchangeRateStateAfterReconnect extends HapiSuite {
    private static final Logger log = LogManager.getLogger(ValidateExchangeRateStateAfterReconnect.class);

    public static void main(String... args) {
        new ValidateExchangeRateStateAfterReconnect().runSuiteSync();
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(validateExchangeRateStateAfterReconnect());
    }

    private HapiSpec validateExchangeRateStateAfterReconnect() {
        final String transactionid = "authorizedTxn";
        final long oldFee = 13_299_075L;
        final long newFee = 159_588_904;
        return customHapiSpec("validateExchangeRateStateAfterReconnect")
                .withProperties(Map.of("txn.start.offset.secs", "-5"))
                .given(
                        sleepFor(Duration.ofSeconds(25).toMillis()),
                        getAccountBalance(GENESIS).setNode("0.0.6").unavailableNode())
                .when(
                        getAccountBalance(GENESIS).setNode("0.0.6").unavailableNode(),
                        fileUpdate(EXCHANGE_RATES)
                                .contents(spec -> {
                                    ByteString newRates = spec.ratesProvider()
                                            .rateSetWith(1, 1)
                                            .toByteString();
                                    spec.registry().saveBytes("newRates", newRates);
                                    return newRates;
                                })
                                .payingWith(SYSTEM_ADMIN),
                        getAccountBalance(GENESIS).setNode("0.0.6").unavailableNode())
                .then(
                        withLiveNode("0.0.6")
                                .within(180, TimeUnit.SECONDS)
                                .loggingAvailabilityEvery(30)
                                .sleepingBetweenRetriesFor(10),
                        cryptoCreate("civilian").via(transactionid).setNode("0.0.6"),
                        getTxnRecord(transactionid)
                                .setNode("0.0.6")
                                .hasPriority(recordWith().fee(newFee)));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
