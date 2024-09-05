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
import static com.hedera.services.bdd.suites.HapiSuite.FEE_SCHEDULE;
import static com.hedera.services.bdd.suites.HapiSuite.FEE_SCHEDULE_CONTROL;
import static com.hedera.services.bdd.suites.HapiSuite.GENESIS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FEE_SCHEDULE_FILE_PART_UPLOADED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

import com.hedera.services.bdd.junit.ContextRequirement;
import com.hedera.services.bdd.junit.LeakyHapiTest;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;

public class FeeScheduleUpdateWaiverTest {
    @LeakyHapiTest(requirement = ContextRequirement.NO_CONCURRENT_CREATIONS)
    final Stream<DynamicTest> feeScheduleControlAccountIsntCharged() {
        ResponseCodeEnum[] acceptable = {SUCCESS, FEE_SCHEDULE_FILE_PART_UPLOADED};

        return defaultHapiSpec("FeeScheduleControlAccountIsntCharged")
                .given(
                        cryptoTransfer(tinyBarsFromTo(GENESIS, FEE_SCHEDULE_CONTROL, 1_000_000_000_000L)),
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
                .then(getAccountBalance(FEE_SCHEDULE_CONTROL).hasTinyBars(changeFromSnapshot("pre", 0)));
    }
}
