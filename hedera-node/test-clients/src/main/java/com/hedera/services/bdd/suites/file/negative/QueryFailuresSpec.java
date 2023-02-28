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

package com.hedera.services.bdd.suites.file.negative;

import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getFileContents;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getFileInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileDelete;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_FILE_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.suites.HapiSuite;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class QueryFailuresSpec extends HapiSuite {
    private static final Logger log = LogManager.getLogger(QueryFailuresSpec.class);

    public static void main(String... args) {
        new QueryFailuresSpec().runSuiteSync();
    }

    @Override
    public boolean canRunConcurrent() {
        return true;
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(getsExpectedRejections());
    }

    private HapiSpec getsExpectedRejections() {
        return defaultHapiSpec("getsExpectedRejections")
                .given(fileCreate("tbd"), fileDelete("tbd"))
                .when()
                .then(
                        getFileInfo("1.2.3").nodePayment(1_234L).hasAnswerOnlyPrecheck(INVALID_FILE_ID),
                        getFileContents("1.2.3").nodePayment(1_234L).hasAnswerOnlyPrecheck(INVALID_FILE_ID),
                        getFileContents("tbd")
                                .nodePayment(1_234L)
                                .hasAnswerOnlyPrecheck(OK)
                                .logged(),
                        getFileInfo("tbd")
                                .nodePayment(1_234L)
                                .hasAnswerOnlyPrecheck(OK)
                                .hasDeleted(true)
                                .logged());
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
