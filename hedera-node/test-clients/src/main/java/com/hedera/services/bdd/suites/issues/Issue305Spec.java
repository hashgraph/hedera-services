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

package com.hedera.services.bdd.suites.issues;

import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.HapiSpec.propertyPreservingHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getFileInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overridingThree;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;

import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.HapiTestSuite;
import com.hedera.services.bdd.spec.HapiPropertySource;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.keys.KeyFactory;
import com.hedera.services.bdd.suites.HapiSuite;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Assertions;

@HapiTestSuite
public class Issue305Spec extends HapiSuite {
    private static final Logger log = LogManager.getLogger(Issue305Spec.class);
    private static final String KEY = "tbdKey";

    public static void main(String... args) {
        new Issue305Spec().runSuiteSync();
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        final var repeatedSpecs = new ArrayList<>(IntStream.range(0, 5)
                .mapToObj(ignore -> createDeleteInSameRoundWorks())
                .toList());
        repeatedSpecs.add(congestionMultipliersRefreshOnPropertyUpdate());
        return repeatedSpecs;
    }

    @HapiTest
    private HapiSpec createDeleteInSameRoundWorks() {
        AtomicReference<String> nextFileId = new AtomicReference<>();
        return defaultHapiSpec("CreateDeleteInSameRoundWorks")
                .given(
                        newKeyNamed(KEY).type(KeyFactory.KeyType.LIST),
                        fileCreate("marker").via("markerTxn"))
                .when(withOpContext((spec, opLog) -> {
                    var lookup = getTxnRecord("markerTxn");
                    allRunFor(spec, lookup);
                    var markerFid = lookup.getResponseRecord().getReceipt().getFileID();
                    var nextFid = markerFid.toBuilder()
                            .setFileNum(markerFid.getFileNum() + 1)
                            .build();
                    nextFileId.set(HapiPropertySource.asFileString(nextFid));
                    opLog.info("Next file will be {}", nextFileId.get());
                }))
                .then(
                        fileCreate("tbd").key(KEY).deferStatusResolution(),
                        fileDelete(nextFileId::get).signedBy(GENESIS, KEY),
                        getFileInfo(nextFileId::get).hasDeleted(true));
    }

    private HapiSpec congestionMultipliersRefreshOnPropertyUpdate() {
        final var civilian = "civilian";
        final var preCongestionTxn = "preCongestionTxn";
        final var postCongestionTxn = "postCongestionTxn";
        final var multipurposeContract = "Multipurpose";
        final var normalPrice = new AtomicLong();
        final var multipliedPrice = new AtomicLong();

        return propertyPreservingHapiSpec("CongestionMultipliersRefreshOnPropertyUpdate")
                .preserving("fees.percentCongestionMultipliers", "fees.minCongestionPeriod", "contracts.maxGasPerSec")
                .given(
                        cryptoCreate(civilian).balance(10 * ONE_HUNDRED_HBARS),
                        uploadInitCode(multipurposeContract),
                        contractCreate(multipurposeContract).payingWith(GENESIS).logging(),
                        contractCall(multipurposeContract)
                                .payingWith(civilian)
                                .fee(10 * ONE_HBAR)
                                .sending(ONE_HBAR)
                                .via(preCongestionTxn),
                        getTxnRecord(preCongestionTxn).providingFeeTo(normalPrice::set),
                        overridingThree(
                                "contracts.maxGasPerSec", "3_000_000",
                                "fees.percentCongestionMultipliers", "1,5x",
                                "fees.minCongestionPeriod", "1"))
                .when(withOpContext((spec, opLog) -> {
                    for (int i = 0; i < 25; i++) {
                        TimeUnit.MILLISECONDS.sleep(50);
                        allRunFor(
                                spec,
                                contractCall(multipurposeContract)
                                        .payingWith(civilian)
                                        .gas(200_000)
                                        .fee(10 * ONE_HBAR)
                                        .sending(ONE_HBAR)
                                        .deferStatusResolution());
                    }
                }))
                .then(
                        contractCall(multipurposeContract)
                                .payingWith(civilian)
                                .fee(10 * ONE_HBAR)
                                .sending(ONE_HBAR)
                                .via(postCongestionTxn),
                        getTxnRecord(postCongestionTxn).providingFeeTo(multipliedPrice::set),
                        withOpContext((spec, opLog) -> Assertions.assertEquals(
                                5.0,
                                (1.0 * multipliedPrice.get()) / normalPrice.get(),
                                0.1,
                                "~5x multiplier should be in affect!")));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
