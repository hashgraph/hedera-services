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
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getFileInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileDelete;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static java.util.stream.Collectors.toList;

import com.hedera.services.bdd.spec.HapiPropertySource;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.keys.KeyFactory;
import com.hedera.services.bdd.suites.HapiSuite;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Issue305Spec extends HapiSuite {
    private static final Logger log = LogManager.getLogger(Issue305Spec.class);

    public static void main(String... args) {
        new Issue305Spec().runSuiteSync();
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return IntStream.range(0, 5)
                .mapToObj(ignore -> createDeleteInSameRoundWorks())
                .collect(toList());
    }

    private HapiSpec createDeleteInSameRoundWorks() {
        AtomicReference<String> nextFileId = new AtomicReference<>();
        return defaultHapiSpec("CreateDeleteInSameRoundWorks")
                .given(
                        newKeyNamed("tbdKey").type(KeyFactory.KeyType.LIST),
                        fileCreate("marker").via("markerTxn"))
                .when(
                        withOpContext(
                                (spec, opLog) -> {
                                    var lookup = getTxnRecord("markerTxn");
                                    allRunFor(spec, lookup);
                                    var markerFid =
                                            lookup.getResponseRecord().getReceipt().getFileID();
                                    var nextFid =
                                            markerFid.toBuilder()
                                                    .setFileNum(markerFid.getFileNum() + 1)
                                                    .build();
                                    nextFileId.set(HapiPropertySource.asFileString(nextFid));
                                    opLog.info("Next file will be " + nextFileId.get());
                                }))
                .then(
                        fileCreate("tbd").key("tbdKey").deferStatusResolution(),
                        fileDelete(nextFileId::get).signedBy(GENESIS, "tbdKey").logged(),
                        getFileInfo(nextFileId::get).logged());
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
