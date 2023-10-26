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

package com.hedera.services.bdd.suites.file;

import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getFileContents;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.transactions.file.HapiFileUpdate;
import com.hedera.services.bdd.spec.utilops.UtilVerbs;
import com.hedera.services.bdd.suites.HapiSuite;
import java.text.ParseException;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

// The test in this suite was extracted from ExchangeRateControlSuite. It has to be run shortly
// before midnight, which is not practical. Either we add functionality to mock time in e2e-test
// or we move this test to the xtests.
//
// https://github.com/hashgraph/hedera-services/issues/8950
public class MidnightUpdateRateSuite extends HapiSuite {

    private static final Logger log = LogManager.getLogger(MidnightUpdateRateSuite.class);

    final HapiFileUpdate resetRatesOp = fileUpdate(EXCHANGE_RATES)
            .payingWith(EXCHANGE_RATE_CONTROL)
            .fee(ADEQUATE_FUNDS)
            .contents(spec -> spec.ratesProvider().rateSetWith(1, 12).toByteString());

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of();
    }

    private HapiSpec acct57UpdatesMidnightRateAtMidNight() throws ParseException {
        return defaultHapiSpec("Acct57UpdatesMidnightRateAtMidNight")
                .given(resetRatesOp, cryptoTransfer(tinyBarsFromTo(GENESIS, EXCHANGE_RATE_CONTROL, ADEQUATE_FUNDS)))
                .when(
                        // should be done just before midnight
                        UtilVerbs.waitUntil("23:58"),
                        fileUpdate(EXCHANGE_RATES)
                                .contents(spec -> {
                                    ByteString newRates = spec.ratesProvider()
                                            .rateSetWith(10, 147)
                                            .toByteString();
                                    spec.registry().saveBytes("midnightRate", newRates);
                                    return newRates;
                                })
                                .payingWith(EXCHANGE_RATE_CONTROL))
                .then(
                        // should be the first transaction after midnight
                        UtilVerbs.sleepFor(300_000),
                        fileUpdate(EXCHANGE_RATES)
                                .contents(spec -> {
                                    ByteString newRates = spec.ratesProvider()
                                            .rateSetWith(10, 183)
                                            .toByteString();
                                    spec.registry().saveBytes("newRates", newRates);
                                    return newRates;
                                })
                                .payingWith(EXCHANGE_RATE_CONTROL),
                        getFileContents(EXCHANGE_RATES)
                                .hasContents(spec -> spec.registry().getBytes("newRates")),
                        resetRatesOp);
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
