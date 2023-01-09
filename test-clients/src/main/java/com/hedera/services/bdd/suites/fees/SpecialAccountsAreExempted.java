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
package com.hedera.services.bdd.suites.fees;

import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.AccountInfoAsserts.changeFromSnapshot;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getFileContents;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileAppend;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.balanceSnapshot;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FEE_SCHEDULE_FILE_PART_UPLOADED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.utilops.UtilVerbs;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.nio.file.Path;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class SpecialAccountsAreExempted extends HapiSuite {
    private static final Logger log = LogManager.getLogger(SpecialAccountsAreExempted.class);

    public static void main(String... args) {
        new SpecialAccountsAreExempted().runSuiteAsync();
    }

    @Override
    public boolean canRunConcurrent() {
        return false;
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(
                new HapiSpec[] {
                    feeScheduleControlAccountIsntCharged(), exchangeRateControlAccountIsntCharged(),
                });
    }

    public static HapiSpec exchangeRateControlAccountIsntCharged() {
        return defaultHapiSpec("ExchangeRateControlAccountIsntCharged")
                .given(
                        cryptoTransfer(
                                tinyBarsFromTo(GENESIS, EXCHANGE_RATE_CONTROL, 1_000_000_000_000L)),
                        balanceSnapshot("pre", EXCHANGE_RATE_CONTROL),
                        getFileContents(EXCHANGE_RATES).saveTo("exchangeRates.bin"))
                .when(
                        fileUpdate(EXCHANGE_RATES)
                                .payingWith(EXCHANGE_RATE_CONTROL)
                                .path(Path.of("./", "exchangeRates.bin").toString()))
                .then(
                        UtilVerbs.sleepFor(1000L),
                        getAccountBalance(EXCHANGE_RATE_CONTROL)
                                .hasTinyBars(changeFromSnapshot("pre", 0)));
    }

    public static HapiSpec feeScheduleControlAccountIsntCharged() {
        ResponseCodeEnum[] acceptable = {SUCCESS, FEE_SCHEDULE_FILE_PART_UPLOADED};

        return defaultHapiSpec("FeeScheduleControlAccountIsntCharged")
                .given(
                        cryptoTransfer(
                                tinyBarsFromTo(GENESIS, FEE_SCHEDULE_CONTROL, 1_000_000_000_000L)),
                        balanceSnapshot("pre", FEE_SCHEDULE_CONTROL),
                        getFileContents(FEE_SCHEDULE).in4kChunks(true).saveTo("feeSchedule.bin"))
                .when(
                        fileUpdate(FEE_SCHEDULE)
                                .hasKnownStatusFrom(acceptable)
                                .payingWith(FEE_SCHEDULE_CONTROL)
                                .path(Path.of("./", "part0-feeSchedule.bin").toString()),
                        fileAppend(FEE_SCHEDULE)
                                .hasKnownStatusFrom(acceptable)
                                .payingWith(FEE_SCHEDULE_CONTROL)
                                .path(Path.of("./", "part1-feeSchedule.bin").toString()),
                        fileAppend(FEE_SCHEDULE)
                                .hasKnownStatusFrom(acceptable)
                                .payingWith(FEE_SCHEDULE_CONTROL)
                                .path(Path.of("./", "part2-feeSchedule.bin").toString()),
                        fileAppend(FEE_SCHEDULE)
                                .hasKnownStatusFrom(acceptable)
                                .payingWith(FEE_SCHEDULE_CONTROL)
                                .path(Path.of("./", "part3-feeSchedule.bin").toString()),
                        fileAppend(FEE_SCHEDULE)
                                .hasKnownStatusFrom(acceptable)
                                .payingWith(FEE_SCHEDULE_CONTROL)
                                .path(Path.of("./", "part4-feeSchedule.bin").toString()))
                .then(
                        getAccountBalance(FEE_SCHEDULE_CONTROL)
                                .hasTinyBars(changeFromSnapshot("pre", 0)));
    }

    private static String path(String prefix, String file) {
        return Path.of(prefix, file).toString();
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
